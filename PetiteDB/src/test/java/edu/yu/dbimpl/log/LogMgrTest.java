package edu.yu.dbimpl.log;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import edu.yu.dbimpl.config.DBConfiguration;
import edu.yu.dbimpl.file.BlockId;
import edu.yu.dbimpl.file.BlockIdBase;
import edu.yu.dbimpl.file.FileMgr;
import edu.yu.dbimpl.file.Page;
import edu.yu.dbimpl.file.PageBase;

/**
 * Tests for LogMgr, the write-ahead log layer.
 *
 * What we care about here (from LogMgrBase Javadoc / log_module lecture /
 * the milestone sheet):
 * - Constructor honors DBConfiguration, creates the log file with one empty
 *   block when it is missing, and rejects bad arguments.
 * - append hands out LSNs 0, 1, 2, ... and copies the client's bytes.  It
 *   must NOT touch the disk until the current page is full; then it writes
 *   that page once and continues in a new block.
 * - A record may be as large as the block size minus 8 bytes of metadata;
 *   anything larger than a block is an IllegalArgumentException.
 * - flush(lsn) puts that record (and its whole page) on disk, is a no-op
 *   when the record is already there, and rejects an LSN that was never
 *   issued.
 * - iterator() flushes, then walks every record newest to oldest, across
 *   block boundaries, returning the client's bytes exactly.
 * - A LogMgr opened on an existing database continues the LSN sequence and
 *   can still iterate the old records.
 * - Many threads may append and flush at once.  The professor's target is
 *   100,000 small records from 10 threads in a few hundred milliseconds.
 *
 * Disk traffic is observed through CountingFileMgr, a FileMgr that counts
 * its read/write/append calls.  The LogMgr only sees a FileMgrBase, so it
 * can't tell the difference, and the tests can assert exactly when the log
 * went to disk.
 *
 * Each test gets its own temp directory (file_module lecture, slide 39).
 * Note: FileMgr.close() is package-private to the file package, so these
 * tests can't release handles before JUnit deletes the temp directory.
 * macOS/Linux don't mind; on Windows the cleanup may complain.
 */
class LogMgrTest {

  private static final int BLOCK = 400;
  private static final String LOG = "petite.log";

  @TempDir
  Path tempDir;

  private File dbDir;

  @BeforeEach
  void setUp(TestInfo info) {
    System.out.println("\n----------------------------------------");
    System.out.println("TEST: " + info.getTestMethod().map(m -> m.getName()).orElse("?"));
    System.out.println("----------------------------------------");
    dbDir = tempDir.resolve("db").toFile();
    configure(true);
  }

  /** Point the DBConfiguration singleton at a fresh or existing database. */
  private static void configure(boolean startup) {
    Properties props = new Properties();
    props.setProperty(DBConfiguration.DB_STARTUP, String.valueOf(startup));
    DBConfiguration.INSTANCE.get().setConfiguration(props);
  }

  /** A FileMgr that counts how often the LogMgr asks it to touch the disk. */
  private static final class CountingFileMgr extends FileMgr {
    final AtomicInteger reads = new AtomicInteger();
    final AtomicInteger writes = new AtomicInteger();
    final AtomicInteger appends = new AtomicInteger();

    CountingFileMgr(File dir, int blockSize) {
      super(dir, blockSize);
    }

    @Override public void read(BlockIdBase blk, PageBase p) {
      reads.incrementAndGet();
      super.read(blk, p);
    }

    @Override public void write(BlockIdBase blk, PageBase p) {
      writes.incrementAndGet();
      super.write(blk, p);
    }

    @Override public BlockIdBase append(String filename) {
      appends.incrementAndGet();
      return super.append(filename);
    }

    void resetCounts() {
      reads.set(0);
      writes.set(0);
      appends.set(0);
    }

    /** Disk writes of any kind: a block written, or a block appended. */
    int diskWrites() {
      return writes.get() + appends.get();
    }
  }

  private CountingFileMgr newFileMgr(boolean startup) {
    configure(startup);
    return new CountingFileMgr(dbDir, BLOCK);
  }

  // ------------------------------------------------------------- records

  /**
   * A log record built the way real clients build them (lecture, slides
   * 38-40): an int and a String laid out by the Page API in a byte array of
   * exactly the right size.
   */
  private static byte[] record(int id, String tag) {
    byte[] bytes = new byte[Integer.BYTES + PageBase.maxLength(tag.length())];
    Page p = new Page(bytes);
    p.setInt(0, id);
    p.setString(Integer.BYTES, tag);
    return bytes;
  }

  private static int idOf(byte[] rec) {
    return new Page(rec).getInt(0);
  }

  private static String tagOf(byte[] rec) {
    return new Page(rec).getString(Integer.BYTES);
  }

  /** A record of exactly {@code size} bytes whose content depends on {@code seed}. */
  private static byte[] bytes(int size, int seed) {
    byte[] b = new byte[size];
    new Random(seed).nextBytes(b);
    return b;
  }

  /** Drain an iterator into a list, newest record first. */
  private static List<byte[]> drain(Iterator<byte[]> it) {
    List<byte[]> out = new ArrayList<>();
    while (it.hasNext()) {
      out.add(it.next());
    }
    return out;
  }

  // --------------------------------------------------------- constructor

  /**
   * LogMgrBase: "If the log file does not yet exist, it is created with an
   * empty first block."  So a brand-new log is one block long and has
   * nothing to iterate.
   */
  @Test
  void constructor_newDatabase_createsLogFileWithOneEmptyBlock() {
    CountingFileMgr fm = newFileMgr(true);
    assertEquals(0, fm.length(LOG), "test setup: no log file yet");
    LogMgr lm = new LogMgr(fm, LOG);
    assertEquals(1, fm.length(LOG), "log file should hold exactly one (empty) block");
    assertFalse(lm.iterator().hasNext(), "a new log has no records");
  }

  /**
   * An existing database whose log file is missing (nothing was ever logged)
   * gets the same empty first block.
   */
  @Test
  void constructor_existingDatabaseWithoutLogFile_createsIt() {
    newFileMgr(true); // creates the directory
    CountingFileMgr fm = newFileMgr(false);
    LogMgr lm = new LogMgr(fm, LOG);
    assertEquals(1, fm.length(LOG));
    assertEquals(0, lm.append(record(0, "first")), "LSN must start at 0");
  }

  /** Null file manager, or a null/blank log file name, is an IllegalArgumentException. */
  @Test
  void constructor_rejectsBadArguments() {
    CountingFileMgr fm = newFileMgr(true);
    assertThrows(IllegalArgumentException.class, () -> new LogMgr(null, LOG));
    assertThrows(IllegalArgumentException.class, () -> new LogMgr(fm, null));
    assertThrows(IllegalArgumentException.class, () -> new LogMgr(fm, ""));
    assertThrows(IllegalArgumentException.class, () -> new LogMgr(fm, "   "));
  }

  /**
   * LogMgrBase: the log manager MUST consult DBConfiguration.  With no
   * startup setting, DBConfiguration throws IllegalStateException, and that
   * must escape the constructor (the FileMgr is built first, while the
   * configuration is still valid).
   */
  @Test
  void constructor_withoutStartupConfig_throwsIllegalState() {
    CountingFileMgr fm = newFileMgr(true);
    DBConfiguration.INSTANCE.get().setConfiguration(new Properties());
    assertThrows(IllegalStateException.class, () -> new LogMgr(fm, LOG));
  }

  /**
   * The layout needs 4 bytes per block (boundary) and 4 per record (length),
   * so a block of 8 bytes can't hold even an empty record and the
   * constructor must refuse it.  A 9-byte block holds exactly one 1-byte
   * record per block, which exercises the tightest possible fit.
   */
  @Test
  void constructor_rejectsBlockSizeWithNoRoomForARecord() {
    configure(true);
    FileMgr eight = new FileMgr(dbDir, 8);
    assertThrows(IllegalArgumentException.class, () -> new LogMgr(eight, LOG));

    configure(true);
    FileMgr nine = new FileMgr(dbDir, 9);
    LogMgr lm = new LogMgr(nine, LOG);
    assertEquals(0, lm.append(new byte[1]), "a 1-byte record is the largest that fits in a 9-byte block");
    assertThrows(IllegalArgumentException.class, () -> lm.append(new byte[2]));
    assertEquals(1, lm.append(new byte[0]), "the empty record needs 4 bytes, so it starts a new block");
    assertEquals(2, nine.length(LOG));
    List<byte[]> all = drain(lm.iterator());
    assertEquals(2, all.size());
    assertEquals(0, all.get(0).length);
    assertEquals(1, all.get(1).length);
  }

  /**
   * A log block the LogMgr could not have produced (boundary outside the
   * block, or a record length running past the end) must be reported as
   * IllegalStateException at startup rather than silently miscounted.
   * The damage is done through the FileMgr, the only way to touch the file.
   */
  @Test
  void constructor_onCorruptLog_throwsIllegalState() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    lm.append(record(0, "a"));
    lm.flush(0);
    configure(false);

    Page bad = new Page(BLOCK);
    bad.setInt(0, BLOCK + 1); // boundary past the end of the block
    fm.write(new BlockId(LOG, 0), bad);
    assertThrows(IllegalStateException.class, () -> new LogMgr(fm, LOG));

    bad.setInt(0, 2); // boundary inside the header
    fm.write(new BlockId(LOG, 0), bad);
    assertThrows(IllegalStateException.class, () -> new LogMgr(fm, LOG));

    bad.setInt(0, 100); // plausible boundary, but the record there claims to be a whole block long
    bad.setInt(100, BLOCK);
    fm.write(new BlockId(LOG, 0), bad);
    assertThrows(IllegalStateException.class, () -> new LogMgr(fm, LOG));

    bad.setInt(100, -1); // negative length
    fm.write(new BlockId(LOG, 0), bad);
    assertThrows(IllegalStateException.class, () -> new LogMgr(fm, LOG));
  }

  // -------------------------------------------------------------- append

  /** append returns 0, 1, 2, ... : the value BEFORE the increment. */
  @Test
  void append_returnsConsecutiveLsnsFromZero() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    for (int i = 0; i < 50; i++) {
      assertEquals(i, lm.append(record(i, "r" + i)), "LSN of append #" + i);
    }
  }

  /**
   * LogMgrBase: "appending a record to the log MUST NOT write to disk UNLESS
   * the current log page/block doesn't have sufficient room".  Records of 40
   * bytes with at most 8 bytes of metadata each (and at most 8 for the
   * block) mean at least 8 of them fit in a 400-byte block, so the first 8
   * appends may not touch the disk.  Ten 40-byte records can't fit under any
   * layout, so by the 11th append the page must have been written.
   */
  @Test
  void append_doesNotTouchDiskUntilPageIsFull() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();
    for (int i = 0; i < 8; i++) {
      lm.append(bytes(40, i));
      assertEquals(0, fm.diskWrites(), "disk written after append #" + (i + 1));
      assertEquals(0, fm.reads.get(), "disk read after append #" + (i + 1));
    }
    for (int i = 8; i < 11; i++) {
      lm.append(bytes(40, i));
    }
    assertTrue(fm.writes.get() >= 1, "full page was never written to disk");
    assertEquals(2, fm.length(LOG), "records past the first block should live in a second block");
  }

  /**
   * Filling a page costs ONE page write (plus possibly one append to create
   * the next block), not one write per record.  Over a few hundred small
   * records the number of disk writes must stay close to the number of
   * blocks the log occupies.
   */
  @Test
  void append_writesEachFullPageOnce() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();
    final int records = 500;
    for (int i = 0; i < records; i++) {
      lm.append(bytes(20, i));
    }
    int blocks = fm.length(LOG);
    assertTrue(blocks > 5, "test setup: expected many blocks, got " + blocks);
    // Every block but the current one was written exactly once when it
    // filled; the current one hasn't been written yet.  Allow an append per
    // block for implementations that pre-create blocks.
    assertTrue(fm.writes.get() <= blocks,
        "writes=" + fm.writes.get() + " for " + blocks + " blocks");
    assertTrue(fm.writes.get() >= blocks - 1,
        "writes=" + fm.writes.get() + " for " + blocks + " blocks");
    assertTrue(fm.diskWrites() <= 2 * blocks,
        "disk writes (" + fm.diskWrites() + ") should be at most two per block (" + blocks + " blocks)");
  }

  /**
   * Null, or anything larger than a block, is an IllegalArgumentException,
   * and a rejected append must not consume an LSN.
   */
  @Test
  void append_rejectsNullAndOversizedRecords() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    assertEquals(0, lm.append(record(0, "ok")));
    assertThrows(IllegalArgumentException.class, () -> lm.append(null));
    assertThrows(IllegalArgumentException.class, () -> lm.append(new byte[BLOCK]),
        "a record of exactly the block size leaves no room for any metadata");
    assertThrows(IllegalArgumentException.class, () -> lm.append(new byte[BLOCK + 1]));
    // Implementation limit: 4 bytes of block header + 4 bytes of length prefix,
    // so block size - 7 is one byte too many.  Allowed by LogMgrBase ("reasonable
    // amounts of meta-data"); asserted here so a layout change is noticed.
    assertThrows(IllegalArgumentException.class, () -> lm.append(new byte[BLOCK - 7]));
    assertThrows(IllegalArgumentException.class, () -> lm.append(new byte[2 * BLOCK]));
    assertEquals(1, lm.append(record(1, "still ok")), "a failed append must not consume an LSN");
    List<byte[]> all = drain(lm.iterator());
    assertEquals(2, all.size(), "rejected records must not appear in the log");
  }

  /**
   * LogMgrBase: metadata beyond 8 bytes is "unreasonable", so a record of
   * (block size - 8) bytes MUST be accepted.  Two in a row can't share a
   * block, so the second one also exercises the page-full path with a
   * maximal record.
   */
  @Test
  void append_acceptsRecordOfBlockSizeMinusEightBytes() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    byte[] big1 = bytes(BLOCK - 8, 1);
    byte[] big2 = bytes(BLOCK - 8, 2);
    assertEquals(0, lm.append(big1));
    assertEquals(1, lm.append(big2));
    List<byte[]> all = drain(lm.iterator());
    assertEquals(2, all.size());
    assertArrayEquals(big2, all.get(0));
    assertArrayEquals(big1, all.get(1));
    assertEquals(2, fm.length(LOG), "two maximal records need two blocks");
  }

  /** A zero-length record is a legal (if useless) record and round-trips. */
  @Test
  void append_acceptsEmptyRecord() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    lm.append(record(7, "before"));
    lm.append(new byte[0]);
    lm.append(record(9, "after"));
    List<byte[]> all = drain(lm.iterator());
    assertEquals(3, all.size());
    assertEquals(9, idOf(all.get(0)));
    assertEquals(0, all.get(1).length);
    assertEquals(7, idOf(all.get(2)));
  }

  /**
   * Arbitrary bytes (including negative values and zeros) come back exactly,
   * newest first.
   */
  @Test
  void append_recordsRoundTripByteForByte() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    List<byte[]> written = new ArrayList<>();
    Random sizes = new Random(99);
    for (int i = 0; i < 300; i++) {
      byte[] rec = bytes(1 + sizes.nextInt(60), i);
      written.add(rec);
      lm.append(rec);
    }
    List<byte[]> read = drain(lm.iterator());
    assertEquals(written.size(), read.size());
    for (int i = 0; i < written.size(); i++) {
      assertArrayEquals(written.get(written.size() - 1 - i), read.get(i), "record " + i + " from the end");
    }
  }

  /**
   * The log must copy the client's bytes: changing the array after append
   * must not change what the log returns.
   */
  @Test
  void append_copiesClientArray() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    byte[] rec = record(42, "original");
    byte[] snapshot = rec.clone();
    lm.append(rec);
    Arrays.fill(rec, (byte) 0x55);
    assertArrayEquals(snapshot, lm.iterator().next());
  }

  /**
   * Records of varying size pack into blocks differently; a record that
   * doesn't fit must go whole into the next block, never be split.
   */
  @Test
  void append_neverSplitsARecordAcrossBlocks() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    List<byte[]> written = new ArrayList<>();
    // Sizes chosen so that the last record on each page rarely fits exactly.
    int[] sizes = {37, 101, 59, 211, 13, 150, 97, 44, 300, 5, 77, 188, 29, 65, 120};
    for (int i = 0; i < 200; i++) {
      byte[] rec = bytes(sizes[i % sizes.length], i);
      written.add(rec);
      lm.append(rec);
    }
    assertTrue(fm.length(LOG) > 20, "test setup: expected many blocks");
    List<byte[]> read = drain(lm.iterator());
    assertEquals(written.size(), read.size());
    for (int i = 0; i < written.size(); i++) {
      assertArrayEquals(written.get(written.size() - 1 - i), read.get(i));
    }
  }

  // --------------------------------------------------------------- flush

  /**
   * flush writes the current page.  Observable two ways: the counting
   * FileMgr sees one write, and a second LogMgr opened on the same file
   * (existing database) can read the records.
   */
  @Test
  void flush_writesCurrentPageToDisk() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();
    lm.append(record(0, "a"));
    lm.append(record(1, "b"));
    lm.append(record(2, "c"));
    assertEquals(0, fm.diskWrites(), "append must not write");
    lm.flush(2);
    assertEquals(1, fm.writes.get(), "flush should write the page exactly once");

    LogMgr reopened = new LogMgr(newFileMgr(false), LOG);
    List<byte[]> all = drain(reopened.iterator());
    assertEquals(3, all.size());
    assertEquals("c", tagOf(all.get(0)));
    assertEquals("b", tagOf(all.get(1)));
    assertEquals("a", tagOf(all.get(2)));
  }

  /**
   * The milestone sheet's explicit point: flush of an LSN that is already on
   * disk MUST be a no-op.  The counting FileMgr proves no write happened.
   */
  @Test
  void flush_isNoOpWhenRecordAlreadyOnDisk() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();
    lm.append(record(0, "a"));
    lm.append(record(1, "b"));
    lm.append(record(2, "c"));
    lm.flush(1); // flushes the whole page: 0, 1 and 2 are now on disk
    assertEquals(1, fm.writes.get());

    lm.flush(0);
    lm.flush(1);
    lm.flush(2);
    lm.flush(2);
    assertEquals(1, fm.writes.get(), "flushing records already on disk must not write");

    lm.append(record(3, "d")); // LSN 3, in memory only
    lm.flush(2);
    assertEquals(1, fm.writes.get(), "LSN 2 is on disk; flush(2) must still be a no-op");
    lm.flush(3);
    assertEquals(2, fm.writes.get(), "LSN 3 was not on disk; flush(3) must write");
    lm.flush(3);
    assertEquals(2, fm.writes.get());
  }

  /**
   * A record that went to disk because its page filled up counts as flushed:
   * flushing it later must not write again.
   */
  @Test
  void flush_ofRecordWrittenWhenPageFilled_isNoOp() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();
    int lsn = 0;
    while (fm.length(LOG) < 3) { // keep going until the log spans three blocks
      lsn = lm.append(bytes(30, lsn));
    }
    int writesSoFar = fm.writes.get();
    assertTrue(writesSoFar >= 2, "test setup: two pages should have been written when they filled");
    lm.flush(0);
    lm.flush(5);
    assertEquals(writesSoFar, fm.writes.get(), "records in earlier blocks are already on disk");
    lm.flush(lsn);
    assertEquals(writesSoFar + 1, fm.writes.get(), "the newest record was in memory and needed one write");
  }

  /**
   * LogMgrBase: IllegalArgumentException if the LSN is strictly greater than
   * the highest LSN issued so far.  On an empty log nothing has been issued,
   * so every non-negative LSN is too high.
   */
  @Test
  void flush_rejectsLsnNeverIssued() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    assertThrows(IllegalArgumentException.class, () -> lm.flush(0));
    lm.append(record(0, "a"));
    lm.append(record(1, "b"));
    lm.flush(1); // fine: 1 was issued
    assertThrows(IllegalArgumentException.class, () -> lm.flush(2));
    assertThrows(IllegalArgumentException.class, () -> lm.flush(Integer.MAX_VALUE));
  }

  /**
   * A negative LSN names no record (LSNs start at 0), so "flush everything
   * with LSN <= lsn" has nothing to do: no exception, no disk write, on an
   * empty log and on one holding unflushed records alike.  The buffer
   * manager passes -1 for a page it changed without logging, so this must
   * never throw.
   */
  @Test
  void flush_negativeLsn_isNoOp() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();
    lm.flush(-1);
    lm.flush(Integer.MIN_VALUE);
    assertEquals(0, fm.diskWrites(), "nothing to flush on an empty log");

    lm.append(record(0, "a"));
    lm.append(record(1, "b"));
    lm.flush(-1);
    lm.flush(-100);
    assertEquals(0, fm.diskWrites(), "a negative LSN must not flush the records that are in memory");
    lm.flush(1);
    assertEquals(1, fm.writes.get(), "a real LSN still flushes");
    lm.flush(-1);
    assertEquals(1, fm.writes.get());
  }

  /** A flush must not start a new block: later appends continue in the same page. */
  @Test
  void flush_doesNotStartNewBlock() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    lm.append(record(0, "a"));
    lm.flush(0);
    assertEquals(1, fm.length(LOG));
    lm.append(record(1, "b"));
    lm.flush(1);
    assertEquals(1, fm.length(LOG), "two small records belong in the first block even with a flush between them");
    List<byte[]> all = drain(lm.iterator());
    assertEquals(2, all.size());
    assertEquals("b", tagOf(all.get(0)));
    assertEquals("a", tagOf(all.get(1)));
  }

  /**
   * When a page fills, append writes it out before starting a new block,
   * but NOT if flush already put every record on that page on disk: the
   * block on disk is already identical to the page.  So append-then-flush,
   * repeated across many blocks, costs exactly one write per record and no
   * extra write per block.  (The buffer manager flushes the log before every
   * dirty page write, so this pattern is common.)
   */
  @Test
  void append_doesNotRewritePageAlreadyFlushed() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();
    final int records = 1000;
    for (int i = 0; i < records; i++) {
      lm.flush(lm.append(bytes(30, i)));
    }
    int blocks = fm.length(LOG);
    assertTrue(blocks > 50, "test setup: expected many blocks, got " + blocks);
    assertEquals(records, fm.writes.get(),
        "one write per flush and none when a fully-flushed page fills (" + blocks + " blocks)");
    assertEquals(blocks - 1, fm.appends.get(), "one append per block after the first");
    // Nothing is left in memory, so the iterator writes nothing and sees everything.
    List<byte[]> all = drain(lm.iterator());
    assertEquals(records, fm.writes.get(), "iterator must not write when all records are on disk");
    assertEquals(records, all.size());
    for (int i = 0; i < records; i++) {
      assertArrayEquals(bytes(30, records - 1 - i), all.get(i), "record at position " + i);
    }
  }

  // ----------------------------------------------------------- latestLsn

  /**
   * latestLsn() (not in LogMgrBase; added for Buffer.setModified) is the LSN
   * of the most recent successful append: -1 on an empty log, unchanged by a
   * rejected append, and restored on restart.
   */
  @Test
  void latestLsn_tracksAppendsAndSurvivesRestart() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    assertEquals(-1, lm.latestLsn(), "empty log");
    assertEquals(0, lm.append(record(0, "a")));
    assertEquals(0, lm.latestLsn());
    assertThrows(IllegalArgumentException.class, () -> lm.append(new byte[BLOCK + 1]));
    assertEquals(0, lm.latestLsn(), "a rejected append issues no LSN");
    for (int i = 1; i < 25; i++) {
      lm.append(record(i, "r" + i));
    }
    assertEquals(24, lm.latestLsn());
    assertEquals(24, lm.latestLsn(), "a getter has no side effects");
    lm.flush(24);

    LogMgr reopened = new LogMgr(newFileMgr(false), LOG);
    assertEquals(24, reopened.latestLsn(), "restart continues from the persisted log");
    assertEquals(25, reopened.append(record(25, "z")));
    assertEquals(25, reopened.latestLsn());
  }

  // ------------------------------------------------------------ iterator

  /** Empty log: hasNext is false, next throws (standard Iterator contract). */
  @Test
  void iterator_onEmptyLog_hasNothing() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    Iterator<byte[]> it = lm.iterator();
    assertFalse(it.hasNext());
    assertThrows(NoSuchElementException.class, it::next);
  }

  /** Exhausted iterator: hasNext false, next throws. */
  @Test
  void iterator_afterLastRecord_throwsOnNext() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    lm.append(record(0, "only"));
    Iterator<byte[]> it = lm.iterator();
    assertTrue(it.hasNext());
    assertEquals("only", tagOf(it.next()));
    assertFalse(it.hasNext());
    assertThrows(NoSuchElementException.class, it::next);
  }

  /**
   * LogMgrBase: iterator() "First flushes the log to disk".  Records that
   * were only in memory must be on disk once an iterator exists, which a
   * second LogMgr on the same file can confirm.
   */
  @Test
  void iterator_flushesFirst() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();
    lm.append(record(0, "a"));
    lm.append(record(1, "b"));
    assertEquals(0, fm.diskWrites());
    lm.iterator();
    assertEquals(1, fm.writes.get(), "iterator() must flush the in-memory page");

    LogMgr reopened = new LogMgr(newFileMgr(false), LOG);
    assertEquals(2, drain(reopened.iterator()).size());
  }

  /**
   * The main contract: every record, newest to oldest, across many blocks.
   * Record i carries i, so order and completeness are both checked.
   */
  @Test
  void iterator_returnsEveryRecordNewestFirst_acrossManyBlocks() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    final int n = 2_000;
    for (int i = 0; i < n; i++) {
      lm.append(record(i, "rec-" + i));
    }
    assertTrue(fm.length(LOG) > 50, "test setup: expected many blocks, got " + fm.length(LOG));
    Iterator<byte[]> it = lm.iterator();
    for (int expected = n - 1; expected >= 0; expected--) {
      assertTrue(it.hasNext(), "ran out of records before reaching record " + expected);
      byte[] rec = it.next();
      assertEquals(expected, idOf(rec));
      assertEquals("rec-" + expected, tagOf(rec));
    }
    assertFalse(it.hasNext(), "iterator returned more records than were appended");
  }

  /** A record that exactly fills a block (max size) sits alone in it and still iterates correctly. */
  @Test
  void iterator_handlesBlocksHoldingOneMaximalRecord() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    lm.append(record(0, "small"));
    lm.append(bytes(BLOCK - 8, 1)); // forces a new block, fills it completely
    lm.append(record(2, "small again")); // another new block
    List<byte[]> all = drain(lm.iterator());
    assertEquals(3, all.size());
    assertEquals(2, idOf(all.get(0)));
    assertArrayEquals(bytes(BLOCK - 8, 1), all.get(1));
    assertEquals(0, idOf(all.get(2)));
  }

  /** Each call to iterator() starts over from the newest record and sees records added since the last one. */
  @Test
  void iterator_eachCallStartsFromNewestRecord() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    lm.append(record(0, "a"));
    lm.append(record(1, "b"));
    assertEquals(2, drain(lm.iterator()).size());
    lm.append(record(2, "c"));
    List<byte[]> all = drain(lm.iterator());
    assertEquals(3, all.size());
    assertEquals("c", tagOf(all.get(0)));
    assertEquals(3, drain(lm.iterator()).size(), "a fresh iterator must start over");
  }

  /**
   * The iterator reads blocks, not records, from disk: walking a log of
   * many blocks costs about one read per block, not one per record.
   */
  @Test
  void iterator_readsEachBlockAboutOnce() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr lm = new LogMgr(fm, LOG);
    final int n = 1_000;
    for (int i = 0; i < n; i++) {
      lm.append(bytes(20, i));
    }
    int blocks = fm.length(LOG);
    fm.resetCounts();
    assertEquals(n, drain(lm.iterator()).size());
    assertTrue(fm.reads.get() <= blocks + 1,
        "reads=" + fm.reads.get() + " for " + blocks + " blocks: the iterator should read a block at a time");
  }

  /**
   * The lecture's end-to-end flow (slides 38-40): a client lays out a String
   * and an int with the byte[] Page constructor, appends the bytes, and
   * later unpacks what the iterator returns with the same constructor.
   */
  @Test
  void iterator_recordsBuiltWithPageApi_unpackWithPageApi() {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    String[] names = {"alice", "bob", "carol", "dave"};
    for (int i = 0; i < names.length; i++) {
      byte[] rec = new byte[PageBase.maxLength(names[i].length()) + Integer.BYTES + Double.BYTES + 1];
      Page p = new Page(rec);
      p.setString(0, names[i]);
      int off = PageBase.maxLength(names[i].length());
      p.setInt(off, i * 10);
      p.setDouble(off + Integer.BYTES, i + 0.5);
      p.setBoolean(off + Integer.BYTES + Double.BYTES, i % 2 == 0);
      lm.append(rec);
    }
    Iterator<byte[]> it = lm.iterator();
    for (int i = names.length - 1; i >= 0; i--) {
      Page p = new Page(it.next());
      assertEquals(names[i], p.getString(0));
      int off = PageBase.maxLength(names[i].length());
      assertEquals(i * 10, p.getInt(off));
      assertEquals(i + 0.5, p.getDouble(off + Integer.BYTES));
      assertEquals(i % 2 == 0, p.getBoolean(off + Integer.BYTES + Double.BYTES));
    }
    assertFalse(it.hasNext());
  }

  // -------------------------------------------------------------- restart

  /**
   * LogMgrBase: on a later instantiation the LSN "MUST be initialized to the
   * latest LSN value of the previously instantiated DBMS" and "all
   * previously persisted log records must be available".  Here the first
   * manager flushes explicitly before the second one is created.
   */
  @Test
  void restart_continuesLsnAndKeepsRecords() {
    LogMgr first = new LogMgr(newFileMgr(true), LOG);
    final int n = 500; // spans many 400-byte blocks
    int last = -1;
    for (int i = 0; i < n; i++) {
      last = first.append(record(i, "gen1-" + i));
    }
    first.flush(last);

    LogMgr second = new LogMgr(newFileMgr(false), LOG);
    assertEquals(n, second.append(record(n, "gen2-" + n)), "LSN must continue where the previous DBMS left off");
    List<byte[]> all = drain(second.iterator());
    assertEquals(n + 1, all.size());
    assertEquals("gen2-" + n, tagOf(all.get(0)));
    for (int i = 1; i < all.size(); i++) {
      assertEquals(n - i, idOf(all.get(i)));
    }
  }

  /** The iterator's implicit flush is enough to make records survive a restart. */
  @Test
  void restart_afterIteratorWithoutExplicitFlush_keepsRecords() {
    LogMgr first = new LogMgr(newFileMgr(true), LOG);
    for (int i = 0; i < 20; i++) {
      first.append(record(i, "r" + i));
    }
    assertEquals(20, drain(first.iterator()).size());

    LogMgr second = new LogMgr(newFileMgr(false), LOG);
    assertEquals(20, drain(second.iterator()).size());
    assertEquals(20, second.append(record(20, "r20")));
  }

  /** Three generations in a row: each continues the numbering and sees everything persisted before it. */
  @Test
  void restart_threeGenerations() {
    int lsn = 0;
    for (int gen = 0; gen < 3; gen++) {
      LogMgr lm = new LogMgr(newFileMgr(gen == 0), LOG);
      for (int i = 0; i < 100; i++) {
        assertEquals(lsn, lm.append(record(lsn, "g" + gen)), "generation " + gen + ", record " + i);
        lsn++;
      }
      lm.flush(lsn - 1);
      assertEquals(lsn, drain(lm.iterator()).size());
    }
  }

  /** db.startup=true means a fresh database: the FileMgr wipes the directory and the log starts at LSN 0 again. */
  @Test
  void restart_withStartupTrue_startsEmptyLogAtLsnZero() {
    LogMgr first = new LogMgr(newFileMgr(true), LOG);
    for (int i = 0; i < 30; i++) {
      first.append(record(i, "old"));
    }
    first.flush(29);

    LogMgr fresh = new LogMgr(newFileMgr(true), LOG);
    assertFalse(fresh.iterator().hasNext(), "a brand-new database has no log records");
    assertEquals(0, fresh.append(record(0, "new")));
  }

  /**
   * Whatever a restarted manager can iterate is exactly what it numbers
   * from: after restart, the next LSN equals the number of records it can
   * read back.  (Records appended but never flushed may or may not survive;
   * the Javadoc only promises persisted records.  Either way the two must
   * agree, or LSNs would collide or skip.)
   */
  @Test
  void restart_lsnMatchesRecordsItCanRead() {
    LogMgr first = new LogMgr(newFileMgr(true), LOG);
    for (int i = 0; i < 300; i++) {
      first.append(bytes(25, i));
    }
    first.flush(150); // part of the log is certainly on disk, the tail may not be

    LogMgr second = new LogMgr(newFileMgr(false), LOG);
    int readable = drain(second.iterator()).size();
    assertTrue(readable >= 151, "at least the flushed records must be back: " + readable);
    assertEquals(readable, second.append(bytes(25, 999)), "next LSN must equal the number of persisted records");
  }

  /** A log file and its records survive a restart even when the last block is completely full. */
  @Test
  void restart_whenLastBlockIsExactlyFull() {
    LogMgr first = new LogMgr(newFileMgr(true), LOG);
    first.append(record(0, "a"));
    first.append(bytes(BLOCK - 8, 1)); // fills its block to the byte
    first.flush(1);

    LogMgr second = new LogMgr(newFileMgr(false), LOG);
    assertEquals(2, second.append(record(2, "c")));
    List<byte[]> all = drain(second.iterator());
    assertEquals(3, all.size());
    assertEquals("c", tagOf(all.get(0)));
    assertArrayEquals(bytes(BLOCK - 8, 1), all.get(1));
    assertEquals("a", tagOf(all.get(2)));
  }

  /** A database that was created and never logged to restarts at LSN 0 with nothing to iterate. */
  @Test
  void restart_withNoRecords_startsAtLsnZero() {
    new LogMgr(newFileMgr(true), LOG); // creates the empty first block only
    LogMgr second = new LogMgr(newFileMgr(false), LOG);
    assertFalse(second.iterator().hasNext(), "nothing was ever logged");
    assertEquals(0, second.append(record(0, "first")), "LSN must start at 0");
    LogMgr third = new LogMgr(newFileMgr(false), LOG);
    assertEquals(0, third.append(record(0, "first again")),
        "the previous record was never flushed, so the restarted LSN is still 0");
  }

  /**
   * Crash between "append a new block" and "write the page into it": the
   * file ends with a zero-filled block.  Simulated by calling FileMgr.append
   * on the log file after a flush.  A restarted manager must treat that
   * block as empty: the iterator skips it, the LSN continues from the
   * persisted records, and new records fill the block and are read back.
   */
  @Test
  void restart_afterCrashLeavingZeroFilledLastBlock() {
    CountingFileMgr fm = newFileMgr(true);
    LogMgr first = new LogMgr(fm, LOG);
    final int n = 60;
    for (int i = 0; i < n; i++) {
      first.append(record(i, "before-" + i));
    }
    first.flush(n - 1);
    int blocksBefore = fm.length(LOG);
    assertTrue(blocksBefore > 1, "test setup: expected several blocks");
    fm.append(LOG); // the crash: the block is on disk, all zeros
    assertEquals(blocksBefore + 1, fm.length(LOG));

    CountingFileMgr fm2 = newFileMgr(false);
    LogMgr second = new LogMgr(fm2, LOG);
    List<byte[]> all = drain(second.iterator());
    assertEquals(n, all.size(), "the zero-filled block must contribute no records");
    assertEquals(n - 1, idOf(all.get(0)));
    assertEquals(0, idOf(all.get(n - 1)));

    fm2.resetCounts();
    assertEquals(n, second.append(record(n, "after")), "LSN continues from the persisted records");
    assertEquals(0, fm2.diskWrites(), "the first record after restart goes into the empty block in memory");
    final int more = 40; // enough to fill that block and move past it
    for (int i = n + 1; i < n + more; i++) {
      second.append(record(i, "after"));
    }
    assertTrue(fm2.length(LOG) > blocksBefore + 1, "test setup: the recovered block should have filled");
    all = drain(second.iterator());
    assertEquals(n + more, all.size());
    for (int i = 0; i < all.size(); i++) {
      assertEquals(n + more - 1 - i, idOf(all.get(i)), "record " + i + " from the end");
    }
  }

  /**
   * Startup cost on an existing log: one read per block and no writes.
   * Afterwards the manager works from memory: an append that fits in the
   * last block costs nothing, a flush of an old LSN is a no-op, and a flush
   * of the new record is one write.
   */
  @Test
  void restart_readsEachBlockOnceAndWritesNothing() {
    LogMgr first = new LogMgr(newFileMgr(true), LOG);
    final int n = 300;
    for (int i = 0; i < n; i++) {
      first.append(bytes(20, i));
    }
    first.flush(n - 1);

    CountingFileMgr fm = newFileMgr(false);
    int blocks = fm.length(LOG);
    assertTrue(blocks > 10, "test setup: expected many blocks, got " + blocks);
    fm.resetCounts();
    LogMgr second = new LogMgr(fm, LOG);
    assertEquals(blocks, fm.reads.get(), "startup should read each block exactly once");
    assertEquals(0, fm.diskWrites(), "startup on an existing log must not write");

    second.flush(n - 1);
    assertEquals(0, fm.diskWrites(), "every persisted record is already on disk");
    assertEquals(n, second.append(bytes(20, n)));
    assertEquals(0, fm.diskWrites(), "the record fits in the last block, so no write yet");
    second.flush(n);
    assertEquals(1, fm.writes.get());
  }

  // ---------------------------------------------------------- concurrency

  /** Start every thread, then wait for all of them to finish. */
  private static void runAll(List<Thread> workers) throws InterruptedException {
    for (Thread w : workers) {
      w.start();
    }
    for (Thread w : workers) {
      w.join();
    }
  }

  /**
   * The professor's benchmark: 100,000 small records from 10 threads (his
   * Mac Pro: 221 ms).  Beyond timing, every LSN must be handed out exactly
   * once, every record must be in the log, and each thread's own records
   * must come back in the order it appended them.  The counting FileMgr
   * also proves the log went to disk about once per block, not per record.
   */
  @Test
  void benchmark_100kRecordsFrom10Threads() throws InterruptedException {
    final int blockSize = 4096;
    final int threads = 10;
    final int perThread = 10_000;
    configure(true);
    CountingFileMgr fm = new CountingFileMgr(dbDir, blockSize);
    LogMgr lm = new LogMgr(fm, LOG);
    fm.resetCounts();

    final int[][] lsns = new int[threads][perThread];
    final List<Throwable> failures = new ArrayList<>();
    final CountDownLatch go = new CountDownLatch(1);
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final int id = t;
      workers.add(new Thread(() -> {
        try {
          go.await();
          for (int i = 0; i < perThread; i++) {
            lsns[id][i] = lm.append(record(id * perThread + i, "t" + id));
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    long start = System.nanoTime();
    for (Thread w : workers) {
      w.start();
    }
    go.countDown();
    for (Thread w : workers) {
      w.join();
    }
    long appended = System.nanoTime();
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);

    // Every LSN exactly once, and increasing within each thread.
    Set<Integer> seen = new HashSet<>();
    for (int t = 0; t < threads; t++) {
      for (int i = 0; i < perThread; i++) {
        assertTrue(seen.add(lsns[t][i]), "LSN " + lsns[t][i] + " handed out twice");
        if (i > 0) {
          assertTrue(lsns[t][i] > lsns[t][i - 1], "thread " + t + " got LSNs out of order");
        }
      }
    }
    assertEquals(threads * perThread, seen.size());
    assertEquals(threads * perThread - 1, seen.stream().mapToInt(Integer::intValue).max().orElse(-1));

    // Every record is in the log; per thread, newest first.
    int[] nextExpected = new int[threads];
    Arrays.fill(nextExpected, perThread - 1);
    int count = 0;
    Iterator<byte[]> it = lm.iterator();
    while (it.hasNext()) {
      byte[] rec = it.next();
      int id = idOf(rec);
      int t = id / perThread;
      int i = id % perThread;
      assertEquals("t" + t, tagOf(rec));
      assertEquals(nextExpected[t], i, "thread " + t + "'s records came back out of order");
      nextExpected[t]--;
      count++;
    }
    long verified = System.nanoTime();
    assertEquals(threads * perThread, count, "iterator must return every record");

    int blocks = fm.length(LOG);
    System.out.printf("benchmark: %d records from %d threads: append %.3f s, verify %.3f s, "
        + "blocks=%d, page writes=%d, block appends=%d%n",
        threads * perThread, threads, (appended - start) / 1e9, (verified - appended) / 1e9,
        blocks, fm.writes.get(), fm.appends.get());
    assertTrue(fm.diskWrites() <= 2 * blocks + 2,
        "disk writes (" + fm.diskWrites() + ") should be about one per block (" + blocks + ")");
    assertTrue((appended - start) / 1e9 < 3.0,
        "appending 100,000 records took longer than 3 s; the professor's reference is 0.221 s");
  }

  /**
   * Threads that append and flush their own records while others keep
   * appending: nothing may fail, a flush of an issued LSN must never throw,
   * and afterwards every record is in the log.
   */
  @Test
  void concurrentAppendsAndFlushes_areSafe() throws InterruptedException {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    final int threads = 8;
    final int perThread = 2_000;
    final List<Throwable> failures = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final int id = t;
      workers.add(new Thread(() -> {
        try {
          for (int i = 0; i < perThread; i++) {
            int lsn = lm.append(record(id * perThread + i, "f" + id));
            if (i % 50 == 0) {
              lm.flush(lsn);
            }
            if (i % 333 == 0) {
              lm.flush(Math.max(0, lsn - 100)); // an older record: on disk already, or flushed now
            }
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    runAll(workers);
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);

    boolean[] present = new boolean[threads * perThread];
    for (byte[] rec : drain(lm.iterator())) {
      int id = idOf(rec);
      assertFalse(present[id], "record " + id + " returned twice");
      present[id] = true;
    }
    for (int id = 0; id < present.length; id++) {
      assertTrue(present[id], "record " + id + " missing");
    }
  }

  /**
   * Iterating while other threads keep appending must not fail or return
   * garbage: every record the iterator returns must be a well-formed record
   * that some thread appended.
   */
  @Test
  void iteratorWhileOthersAppend_returnsOnlyWholeRecords() throws InterruptedException {
    LogMgr lm = new LogMgr(newFileMgr(true), LOG);
    for (int i = 0; i < 500; i++) {
      lm.append(record(i, "seed"));
    }
    final List<Throwable> failures = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < 4; t++) {
      final int id = t;
      workers.add(new Thread(() -> {
        try {
          for (int i = 0; i < 3_000; i++) {
            lm.append(record(1_000_000 + id * 10_000 + i, "live"));
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    for (int r = 0; r < 2; r++) {
      workers.add(new Thread(() -> {
        try {
          for (int pass = 0; pass < 5; pass++) {
            int seen = 0;
            for (byte[] rec : drain(lm.iterator())) {
              String tag = tagOf(rec);
              if (!"seed".equals(tag) && !"live".equals(tag)) {
                throw new AssertionError("corrupt record with tag " + tag);
              }
              seen++;
            }
            if (seen < 500) {
              throw new AssertionError("iterator lost the seed records: saw " + seen);
            }
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    runAll(workers);
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);
    assertEquals(500 + 4 * 3_000, drain(lm.iterator()).size());
  }

  // ------------------------------------------------------------ benchmark

  /**
   * Single-threaded throughput, for comparison with the concurrent run: the
   * elapsed time is printed; correctness is asserted.
   */
  @Test
  void benchmark_100kRecordsSingleThread() {
    final int blockSize = 4096;
    configure(true);
    CountingFileMgr fm = new CountingFileMgr(dbDir, blockSize);
    LogMgr lm = new LogMgr(fm, LOG);
    final int n = 100_000;
    long start = System.nanoTime();
    for (int i = 0; i < n; i++) {
      assertEquals(i, lm.append(record(i, "s")));
    }
    long appended = System.nanoTime();
    int count = 0;
    Iterator<byte[]> it = lm.iterator();
    for (int expected = n - 1; it.hasNext(); expected--) {
      assertEquals(expected, idOf(it.next()));
      count++;
    }
    long verified = System.nanoTime();
    assertEquals(n, count);
    System.out.printf("benchmark: %d records single-threaded: append %.3f s, verify %.3f s, blocks=%d%n",
        n, (appended - start) / 1e9, (verified - appended) / 1e9, fm.length(LOG));
  }
}
