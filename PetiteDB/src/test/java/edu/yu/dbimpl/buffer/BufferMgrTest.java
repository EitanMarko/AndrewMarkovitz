package edu.yu.dbimpl.buffer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import edu.yu.dbimpl.buffer.BufferMgrBase.EvictionPolicy;
import edu.yu.dbimpl.config.DBConfiguration;
import edu.yu.dbimpl.file.BlockId;
import edu.yu.dbimpl.file.BlockIdBase;
import edu.yu.dbimpl.file.FileMgr;
import edu.yu.dbimpl.file.Page;
import edu.yu.dbimpl.file.PageBase;
import edu.yu.dbimpl.log.LogMgr;

/**
 * Tests for Buffer and BufferMgr, the buffer module.
 *
 * What we care about here (from BufferBase/BufferMgrBase Javadoc, the
 * buffer_module lecture, and the milestone sheet):
 * - Constructors validate their arguments, default to NAIVE, and access
 *   DBConfiguration.
 * - pin() follows the four scenarios of lecture slides 9-12: a resident
 *   block (pinned or not) is re-pinned with no disk access and the SAME
 *   Buffer is returned; a non-resident block replaces an unpinned buffer
 *   (writing it first only if dirty); with nothing unpinned the client
 *   waits, is woken by an unpin, and gets BufferAbortException after
 *   maxWaitTime.
 * - Disk access only when necessary (slides 14-16, 53): unpin never writes;
 *   a write happens only when a dirty page is replaced or at flushAll, and
 *   the log record is on disk before the page (write-ahead).
 * - flushAll(tx) writes exactly that transaction's dirty pages, once.
 * - NAIVE and CLOCK choose victims as in the lecture's worked example
 *   (slides 22-40), and CLOCK keeps a hot block that NAIVE keeps evicting.
 * - The whole thing is thread-safe, and holds up under the professor's
 *   graded load: 20 buffers, 50 threads, 50,000 4K blocks, 2 ms per pin.
 *
 * Disk traffic is observed through CountingFileMgr, a FileMgr that records
 * every read and write.  Clients of a BufferMgr can't see when the disk is
 * touched (slide 53), but the tests can.
 *
 * Each test gets its own temp directory.  As in LogMgrTest, FileMgr.close()
 * is package-private so handles are released only when the JVM exits.
 */
class BufferMgrTest {

  private static final int BLOCK = 400;
  private static final String DATA = "data";
  private static final String LOG = "petite.log";

  @TempDir
  Path tempDir;

  private File dbDir;
  private CountingFileMgr fm;
  private LogMgr lm;

  @BeforeEach
  void setUp(TestInfo info) {
    System.out.println("\n----------------------------------------");
    System.out.println("TEST: " + info.getTestMethod().map(m -> m.getName()).orElse("?"));
    System.out.println("----------------------------------------");
    dbDir = tempDir.resolve("db").toFile();
    start(true, BLOCK);
  }

  /** Point the DBConfiguration singleton at a fresh or existing database. */
  private static void configure(boolean startup) {
    Properties props = new Properties();
    props.setProperty(DBConfiguration.DB_STARTUP, String.valueOf(startup));
    DBConfiguration.INSTANCE.get().setConfiguration(props);
  }

  /** (Re)create the file and log managers the buffer manager sits on. */
  private void start(boolean startup, int blockSize) {
    configure(startup);
    fm = new CountingFileMgr(dbDir, blockSize);
    lm = new LogMgr(fm, LOG);
  }

  /** A FileMgr that records how often, and in what order, the disk is touched. */
  private static final class CountingFileMgr extends FileMgr {
    final AtomicInteger reads = new AtomicInteger();
    final AtomicInteger writes = new AtomicInteger();
    /** "file:block" of every write, in order. */
    final List<String> writeLog = Collections.synchronizedList(new ArrayList<>());

    CountingFileMgr(File dir, int blockSize) {
      super(dir, blockSize);
    }

    @Override public void read(BlockIdBase blk, PageBase p) {
      reads.incrementAndGet();
      super.read(blk, p);
    }

    @Override public void write(BlockIdBase blk, PageBase p) {
      writes.incrementAndGet();
      writeLog.add(blk.fileName() + ":" + blk.number());
      super.write(blk, p);
    }

    void resetCounts() {
      reads.set(0);
      writes.set(0);
      writeLog.clear();
    }

    /** Writes to the data file only (ignoring the log). */
    long dataWrites() {
      synchronized (writeLog) {
        return writeLog.stream().filter(s -> s.startsWith(DATA + ":")).count();
      }
    }
  }

  // ------------------------------------------------------------- helpers

  private static BlockId blk(int n) {
    return new BlockId(DATA, n);
  }

  /** Put blocks 0..count-1 of the data file on disk, block n holding the int 1000+n at offset 0. */
  private void seedBlocks(int count) {
    Page p = new Page(fm.blockSize());
    for (int n = 0; n < count; n++) {
      fm.append(DATA);
      p.setInt(0, 1000 + n);
      fm.write(blk(n), p);
    }
    fm.resetCounts();
  }

  private BufferMgr newMgr(int nBuffers, int maxWaitMs) {
    return new BufferMgr(fm, lm, nBuffers, maxWaitMs);
  }

  private BufferMgr newMgr(int nBuffers, int maxWaitMs, EvictionPolicy policy) {
    return new BufferMgr(fm, lm, nBuffers, maxWaitMs, policy);
  }

  /** Pin, modify the first int, log it, mark modified, and return the buffer still pinned. */
  private BufferBase pinAndDirty(BufferMgr bm, int block, int value, int txnum) {
    BufferBase buf = bm.pin(blk(block));
    buf.contents().setInt(0, value);
    int lsn = lm.append(("tx " + txnum + " set block " + block + " to " + value).getBytes());
    buf.setModified(txnum, lsn);
    return buf;
  }

  private int intOnDisk(int block) {
    Page p = new Page(fm.blockSize());
    fm.read(blk(block), p);
    return p.getInt(0);
  }

  // --------------------------------------------------------- constructor

  /** "nBuffers ... must be greater than 0." */
  @Test
  void constructor_rejectsNonPositiveNBuffers() {
    assertThrows(IllegalArgumentException.class, () -> newMgr(0, 100));
    assertThrows(IllegalArgumentException.class, () -> newMgr(-1, 100));
    assertThrows(IllegalArgumentException.class, () -> newMgr(0, 100, EvictionPolicy.CLOCK));
  }

  /** "maxWaitTime ... Must be greater than 0." */
  @Test
  void constructor_rejectsNonPositiveMaxWaitTime() {
    assertThrows(IllegalArgumentException.class, () -> newMgr(3, 0));
    assertThrows(IllegalArgumentException.class, () -> newMgr(3, -5));
    assertThrows(IllegalArgumentException.class, () -> newMgr(3, 0, EvictionPolicy.NAIVE));
  }

  /** "policy ... can't be null." */
  @Test
  void constructor_rejectsNullPolicy() {
    assertThrows(IllegalArgumentException.class, () -> newMgr(3, 100, null));
  }

  /** A buffer manager without a file or log manager can't do its job. */
  @Test
  void constructor_rejectsNullManagers() {
    assertThrows(IllegalArgumentException.class, () -> new BufferMgr(null, lm, 3, 100));
    assertThrows(IllegalArgumentException.class, () -> new BufferMgr(fm, null, 3, 100));
    assertThrows(IllegalArgumentException.class, () -> new Buffer(null, lm));
    assertThrows(IllegalArgumentException.class, () -> new Buffer(fm, null));
  }

  /** The 4-arg constructor "use[s] the EvictionPolicy.NAIVE"; the 5-arg one uses what it's given. */
  @Test
  void constructor_policyDefaultsToNaiveAndHonoursExplicitChoice() {
    assertEquals(EvictionPolicy.NAIVE, newMgr(3, 100).getEvictionPolicy());
    assertEquals(EvictionPolicy.NAIVE, newMgr(3, 100, EvictionPolicy.NAIVE).getEvictionPolicy());
    assertEquals(EvictionPolicy.CLOCK, newMgr(3, 100, EvictionPolicy.CLOCK).getEvictionPolicy());
  }

  /** "The buffer manager MUST access the DBConfiguration singleton": an unset one is an error. */
  @Test
  void constructor_requiresDBConfiguration() {
    DBConfiguration.INSTANCE.get().setConfiguration(new Properties());
    assertThrows(IllegalStateException.class, () -> newMgr(3, 100));
    configure(true);
    assertNotNull(newMgr(3, 100));
    configure(false);
    assertNotNull(newMgr(3, 100), "an existing database is fine too");
  }

  /** Every buffer starts unpinned. */
  @Test
  void available_startsAtNBuffers() {
    assertEquals(1, newMgr(1, 100).available());
    assertEquals(7, newMgr(7, 100).available());
    assertEquals(7, newMgr(7, 100, EvictionPolicy.CLOCK).available());
  }

  // ------------------------------------------------------------- pin/unpin

  @Test
  void pin_rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> newMgr(3, 100).pin(null));
  }

  /** A block that isn't resident costs exactly one read and hands back the disk bytes. */
  @Test
  void pin_nonResidentBlock_readsItOnce() {
    seedBlocks(5);
    BufferMgr bm = newMgr(3, 100);
    BufferBase buf = bm.pin(blk(2));
    assertEquals(1, fm.reads.get());
    assertEquals(0, fm.writes.get());
    assertEquals(1002, buf.contents().getInt(0));
    assertEquals(blk(2), buf.block());
    assertTrue(buf.isPinned());
    assertEquals(2, bm.available());
  }

  /**
   * Scenario I (slide 9): the block is already pinned.  The second client
   * gets the SAME Buffer, the pin count goes up, nothing is read.
   */
  @Test
  void pin_alreadyPinnedBlock_returnsSameBufferWithoutRead() {
    seedBlocks(5);
    BufferMgr bm = newMgr(3, 100);
    BufferBase first = bm.pin(blk(1));
    BufferBase second = bm.pin(blk(1));
    assertSame(first, second);
    assertEquals(1, fm.reads.get());
    assertEquals(2, bm.available(), "one buffer in use, however many pins it has");
    bm.unpin(first);
    assertTrue(second.isPinned(), "still pinned by the other client");
    assertEquals(2, bm.available());
    bm.unpin(second);
    assertFalse(second.isPinned());
    assertEquals(3, bm.available());
  }

  /**
   * Scenario II (slide 10): the block was unpinned but nobody has reused
   * its buffer.  Re-pin it; the earlier read is not wasted.
   */
  @Test
  void pin_unpinnedResidentBlock_isRepinnedWithoutRead() {
    seedBlocks(5);
    BufferMgr bm = newMgr(3, 100);
    BufferBase first = bm.pin(blk(1));
    bm.unpin(first);
    assertEquals(3, bm.available());
    BufferBase again = bm.pin(blk(1));
    assertSame(first, again);
    assertEquals(1, fm.reads.get());
    assertEquals(0, fm.writes.get());
    assertEquals(2, bm.available());
  }

  /**
   * Scenario III (slide 11): the block isn't resident, and an unpinned
   * buffer exists.  That buffer is reused: one read, and no write because
   * the old page was clean.  The old block is then gone from memory.
   */
  @Test
  void pin_nonResidentBlock_reusesUnpinnedBuffer() {
    seedBlocks(5);
    BufferMgr bm = newMgr(2, 100);
    BufferBase a = bm.pin(blk(0));
    BufferBase b = bm.pin(blk(1));
    assertEquals(0, bm.available());
    bm.unpin(a);
    fm.resetCounts();
    BufferBase c = bm.pin(blk(2));
    assertSame(a, c, "the unpinned buffer was reused");
    assertNotSame(b, c);
    assertEquals(1, fm.reads.get());
    assertEquals(0, fm.writes.get(), "a clean page is not written when replaced");
    assertEquals(1002, c.contents().getInt(0));
    assertEquals(blk(2), c.block());
    assertEquals(0, bm.available());
    // block 0 is no longer resident: pinning it again must read
    bm.unpin(b);
    fm.resetCounts();
    bm.pin(blk(0));
    assertEquals(1, fm.reads.get());
  }

  /** A hit on a resident block never needs a free buffer, even with the pool full. */
  @Test
  void pin_residentBlock_succeedsEvenWhenPoolIsFull() {
    seedBlocks(5);
    BufferMgr bm = newMgr(2, 100);
    BufferBase a = bm.pin(blk(0));
    bm.pin(blk(1));
    assertEquals(0, bm.available());
    assertSame(a, bm.pin(blk(0)), "no waiting, no exception");
    assertThrows(BufferAbortException.class, () -> bm.pin(blk(2)), "but a miss has nowhere to go");
  }

  /**
   * Scenario IV (slide 12), the sad path: all buffers pinned, nobody
   * unpins, so after maxWaitTime the client gets BufferAbortException.
   */
  @Test
  void pin_whenAllPinned_timesOutWithBufferAbortException() {
    seedBlocks(5);
    final int maxWait = 300;
    BufferMgr bm = newMgr(1, maxWait);
    bm.pin(blk(0));
    fm.resetCounts();
    long t0 = System.nanoTime();
    assertThrows(BufferAbortException.class, () -> bm.pin(blk(1)));
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    System.out.println("timed out after " + elapsed + " ms (maxWaitTime " + maxWait + ")");
    assertTrue(elapsed >= maxWait - 20, "gave up too early: " + elapsed + " ms");
    assertTrue(elapsed < 5 * maxWait, "waited far too long: " + elapsed + " ms");
    assertEquals(0, bm.available(), "the failed pin changed nothing");
    assertEquals(0, fm.reads.get() + fm.writes.get(), "and touched no disk");
  }

  /**
   * Scenario IV, the happy path: the client blocks, another client unpins,
   * the blocked client wakes and gets its block.
   */
  @Test
  void pin_whenAllPinned_waitsUntilAnUnpin() throws Exception {
    seedBlocks(5);
    BufferMgr bm = newMgr(1, 5000);
    BufferBase held = bm.pin(blk(0));
    AtomicReference<BufferBase> got = new AtomicReference<>();
    Thread waiter = new Thread(() -> got.set(bm.pin(blk(1))));
    waiter.start();
    Thread.sleep(200);
    assertTrue(waiter.isAlive(), "waiter must still be blocked");
    assertNull(got.get());
    long t0 = System.nanoTime();
    bm.unpin(held);
    waiter.join(5000);
    long woke = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    assertFalse(waiter.isAlive(), "waiter never finished");
    assertNotNull(got.get());
    assertEquals(1001, got.get().contents().getInt(0));
    assertSame(held, got.get(), "the freed buffer is the one reused");
    System.out.println("waiter resumed " + woke + " ms after the unpin");
    assertTrue(woke < 1000, "wake-up took " + woke + " ms");
  }

  /**
   * "Must inform ALL clients currently blocked invoking pin()."  Two
   * clients wait for the same block on a one-buffer pool.  After the single
   * unpin, one of them reads the block and the other must find it resident
   * (scenario I) rather than sleeping on until it times out.
   */
  @Test
  void unpin_wakesEveryWaitingClient() throws Exception {
    seedBlocks(5);
    BufferMgr bm = newMgr(1, 3000);
    BufferBase held = bm.pin(blk(0));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<BufferBase> w1 = pool.submit(() -> bm.pin(blk(1)));
      Future<BufferBase> w2 = pool.submit(() -> bm.pin(blk(1)));
      Thread.sleep(200);
      assertFalse(w1.isDone() || w2.isDone(), "both must be blocked");
      fm.resetCounts();
      bm.unpin(held);
      BufferBase b1 = w1.get(3, TimeUnit.SECONDS);
      BufferBase b2 = w2.get(3, TimeUnit.SECONDS);
      assertSame(b1, b2, "both pinned the same resident block");
      assertEquals(1, fm.reads.get(), "block 1 was read once, by whichever woke first");
      assertEquals(0, bm.available());
      bm.unpin(b1);
      assertEquals(0, bm.available(), "still pinned by the other waiter");
      bm.unpin(b2);
      assertEquals(1, bm.available());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void unpin_rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> newMgr(3, 100).unpin(null));
  }

  /** "IllegalArgumentException if buffer isn't currently pinned." */
  @Test
  void unpin_rejectsBufferThatIsNotPinned() {
    seedBlocks(5);
    BufferMgr bm = newMgr(3, 100);
    BufferBase buf = bm.pin(blk(0));
    bm.unpin(buf);
    assertThrows(IllegalArgumentException.class, () -> bm.unpin(buf));
    assertEquals(3, bm.available(), "the rejected unpin changed nothing");
    assertThrows(IllegalArgumentException.class, () -> bm.unpin(new Buffer(fm, lm)),
        "a buffer that was never pinned");
  }

  /**
   * A block past the end of its file has no bytes on disk.  It is pinned
   * without a read, reads as all zeros, and once modified and flushed it
   * exists on disk.  This is how new blocks come into being through the
   * buffer manager.
   */
  @Test
  void pin_blockPastEndOfFile_isZeroedAndNotRead() {
    BufferMgr bm = newMgr(2, 100);
    assertEquals(0, fm.length("fresh"));
    BufferBase buf = bm.pin(new BlockId("fresh", 0));
    assertEquals(0, fm.reads.get());
    assertEquals(0, buf.contents().getInt(0));
    assertEquals(0, buf.contents().getInt(BLOCK - Integer.BYTES));
    buf.contents().setString(0, "hello");
    buf.setModified(1);
    bm.unpin(buf);
    assertEquals(0, fm.length("fresh"), "nothing written yet: unpin does not write");
    bm.flushAll(1);
    assertEquals(1, fm.length("fresh"));
    Page p = new Page(BLOCK);
    fm.read(new BlockId("fresh", 0), p);
    assertEquals("hello", p.getString(0));
  }

  /** A page replaced in a buffer must not leak into a later past-EOF block. */
  @Test
  void pin_blockPastEndOfFile_doesNotShowPreviousBlocksBytes() {
    seedBlocks(3);
    BufferMgr bm = newMgr(1, 100);
    BufferBase buf = bm.pin(blk(2));
    assertEquals(1002, buf.contents().getInt(0));
    bm.unpin(buf);
    BufferBase far = bm.pin(blk(50));
    assertEquals(0, far.contents().getInt(0), "a brand-new block is zeros, not block 2's bytes");
  }

  // ---------------------------------------------------- dirty pages and WAL

  /** Slides 14-15: unpinning a modified buffer writes nothing. */
  @Test
  void unpin_ofDirtyBuffer_writesNothing() {
    seedBlocks(5);
    BufferMgr bm = newMgr(2, 100);
    BufferBase buf = pinAndDirty(bm, 0, 42, 1);
    bm.unpin(buf);
    assertEquals(0, fm.writes.get());
    assertEquals(1000, intOnDisk(0), "disk still has the old value");
  }

  /**
   * Slide 15: a dirty, unpinned page re-pinned by another client is served
   * from memory, modifications included, with no disk access at all.
   */
  @Test
  void pin_dirtyUnpinnedBlock_isServedFromMemory() {
    seedBlocks(5);
    BufferMgr bm = newMgr(2, 100);
    BufferBase buf = pinAndDirty(bm, 0, 42, 1);
    bm.unpin(buf);
    fm.resetCounts();
    BufferBase again = bm.pin(blk(0));
    assertSame(buf, again);
    assertEquals(42, again.contents().getInt(0));
    assertEquals(0, fm.reads.get() + fm.writes.get());
  }

  /**
   * Slide 11: replacing a dirty page writes it, and "the buffer manager
   * must first reach out to the log manager": the log record describing
   * the change reaches disk BEFORE the page does.
   */
  @Test
  void pin_replacingDirtyBuffer_flushesLogThenWritesPageThenReads() {
    seedBlocks(5);
    BufferMgr bm = newMgr(1, 100);
    BufferBase buf = pinAndDirty(bm, 0, 42, 1);
    bm.unpin(buf);
    fm.resetCounts();
    BufferBase next = bm.pin(blk(1));
    assertSame(buf, next);
    assertEquals(List.of(LOG + ":0", DATA + ":0"), fm.writeLog, "log block first, then the data page");
    assertEquals(1, fm.reads.get());
    assertEquals(42, intOnDisk(0), "the modified page is on disk");
    assertEquals(1001, next.contents().getInt(0));
  }

  /** The log record is already on disk: replacing the page writes just the page. */
  @Test
  void pin_replacingDirtyBuffer_doesNotRewriteFlushedLog() {
    seedBlocks(5);
    BufferMgr bm = newMgr(1, 100);
    BufferBase buf = pinAndDirty(bm, 0, 42, 1);
    lm.flush(lm.latestLsn());
    bm.unpin(buf);
    fm.resetCounts();
    bm.pin(blk(1));
    assertEquals(List.of(DATA + ":0"), fm.writeLog);
  }

  /** setModified(txnum) without an LSN: still dirty, still written on replacement, no log write. */
  @Test
  void pin_replacingBufferModifiedWithoutLogRecord_writesPageOnly() {
    seedBlocks(5);
    BufferMgr bm = newMgr(1, 100);
    BufferBase buf = bm.pin(blk(0));
    buf.contents().setInt(0, 7);
    buf.setModified(3);
    bm.unpin(buf);
    fm.resetCounts();
    bm.pin(blk(1));
    assertEquals(List.of(DATA + ":0"), fm.writeLog);
    assertEquals(7, intOnDisk(0));
  }

  /** Once a dirty page has been written, replacing it again costs no write. */
  @Test
  void pin_replacingBufferAfterItsPageWasWritten_writesNothing() {
    seedBlocks(5);
    BufferMgr bm = newMgr(1, 100);
    BufferBase buf = pinAndDirty(bm, 0, 42, 1);
    bm.unpin(buf);
    bm.unpin(bm.pin(blk(1))); // writes block 0
    fm.resetCounts();
    bm.unpin(bm.pin(blk(2)));
    assertEquals(0, fm.writes.get(), "block 1 was never modified");
  }

  // ------------------------------------------------------------- flushAll

  @Test
  void flushAll_rejectsNegativeTxnum() {
    assertThrows(IllegalArgumentException.class, () -> newMgr(3, 100).flushAll(-1));
  }

  /** Only the named transaction's dirty pages are written, log first. */
  @Test
  void flushAll_writesExactlyThatTransactionsDirtyPages_logFirst() {
    seedBlocks(10);
    BufferMgr bm = newMgr(5, 100);
    BufferBase t1a = pinAndDirty(bm, 1, 11, 1);
    BufferBase t2 = pinAndDirty(bm, 2, 22, 2);
    BufferBase t1b = pinAndDirty(bm, 3, 33, 1);
    BufferBase clean = bm.pin(blk(4));
    fm.resetCounts();
    bm.flushAll(1);
    assertEquals(LOG + ":0", fm.writeLog.get(0), "log flushed before any page");
    assertEquals(2, fm.dataWrites());
    assertTrue(fm.writeLog.containsAll(List.of(DATA + ":1", DATA + ":3")), fm.writeLog.toString());
    assertFalse(fm.writeLog.contains(DATA + ":2"), "tx 2's page must not be written");
    assertFalse(fm.writeLog.contains(DATA + ":4"), "a clean page must not be written");
    assertEquals(11, intOnDisk(1));
    assertEquals(33, intOnDisk(3));
    assertEquals(1002, intOnDisk(2), "tx 2's change is still only in memory");
    assertTrue(t1a.isPinned() && t2.isPinned() && t1b.isPinned() && clean.isPinned(), "flushAll leaves pins alone");
    assertEquals(1, bm.available());
  }

  /** "Any association between the transaction and its buffers is removed": a repeat writes nothing. */
  @Test
  void flushAll_secondCallWritesNothing() {
    seedBlocks(5);
    BufferMgr bm = newMgr(3, 100);
    pinAndDirty(bm, 0, 1, 7);
    pinAndDirty(bm, 1, 2, 7);
    bm.flushAll(7);
    fm.resetCounts();
    bm.flushAll(7);
    assertEquals(0, fm.writes.get());
  }

  /** A transaction that modified nothing has nothing to flush. */
  @Test
  void flushAll_unknownTransaction_writesNothing() {
    seedBlocks(5);
    BufferMgr bm = newMgr(3, 100);
    pinAndDirty(bm, 0, 1, 7);
    fm.resetCounts();
    bm.flushAll(8);
    bm.flushAll(0);
    assertEquals(0, fm.writes.get());
    assertEquals(1000, intOnDisk(0));
  }

  /** After flushAll a page is clean: replacing its buffer costs no write. */
  @Test
  void flushAll_makesPagesClean() {
    seedBlocks(5);
    BufferMgr bm = newMgr(1, 100);
    BufferBase buf = pinAndDirty(bm, 0, 42, 1);
    bm.flushAll(1);
    bm.unpin(buf);
    fm.resetCounts();
    bm.pin(blk(1));
    assertEquals(0, fm.writes.get());
  }

  /** Modified again after a flush: the new change is flushed by the next flushAll. */
  @Test
  void flushAll_afterFurtherModification_writesAgain() {
    seedBlocks(5);
    BufferMgr bm = newMgr(2, 100);
    BufferBase buf = pinAndDirty(bm, 0, 1, 1);
    bm.flushAll(1);
    buf.contents().setInt(0, 2);
    buf.setModified(1, lm.append(new byte[]{2}));
    fm.resetCounts();
    bm.flushAll(1);
    assertEquals(1, fm.dataWrites());
    assertEquals(2, intOnDisk(0));
  }

  // ------------------------------------------------------------- Buffer API

  /** BufferBase.setModified(int, int): negative txnum, negative lsn, or an lsn never issued. */
  @Test
  void setModified_rejectsBadTxnumOrLsn() {
    seedBlocks(5);
    BufferMgr bm = newMgr(2, 100);
    BufferBase buf = bm.pin(blk(0));
    assertThrows(IllegalArgumentException.class, () -> buf.setModified(1, 0), "no log record exists yet");
    int lsn = lm.append(new byte[]{1});
    assertEquals(0, lsn);
    assertThrows(IllegalArgumentException.class, () -> buf.setModified(-1, lsn));
    assertThrows(IllegalArgumentException.class, () -> buf.setModified(1, -1));
    assertThrows(IllegalArgumentException.class, () -> buf.setModified(1, lsn + 1));
    assertThrows(IllegalArgumentException.class, () -> buf.setModified(-1));
    buf.setModified(1, lsn); // the real one is fine
    buf.setModified(0, lsn); // txnum 0 is not negative
    buf.setModified(0);
    bm.unpin(buf);
    fm.resetCounts();
    bm.flushAll(1);
    assertEquals(0, fm.writes.get(), "the rejected calls marked nothing, and tx 0 is the last modifier");
    bm.flushAll(0);
    assertEquals(1, fm.dataWrites());
  }

  /**
   * setModified(txnum) after setModified(txnum, lsn) keeps the LSN: the
   * earlier log record must still reach disk before the page does.
   */
  @Test
  void setModified_withoutLsn_keepsEarlierLsnForWriteAhead() {
    seedBlocks(5);
    BufferMgr bm = newMgr(1, 100);
    BufferBase buf = pinAndDirty(bm, 0, 1, 1);
    buf.contents().setInt(0, 2);
    buf.setModified(1);
    bm.unpin(buf);
    fm.resetCounts();
    bm.pin(blk(1));
    assertEquals(List.of(LOG + ":0", DATA + ":0"), fm.writeLog, "log still flushed before the page");
    assertEquals(2, intOnDisk(0));
  }

  /** block() and contents() describe what the buffer holds; isPinned() tracks the pin count. */
  @Test
  void buffer_gettersReflectState() {
    seedBlocks(5);
    BufferMgr bm = newMgr(2, 100);
    Buffer fresh = new Buffer(fm, lm);
    assertNull(fresh.block(), "never assigned");
    assertFalse(fresh.isPinned());
    assertNotNull(fresh.contents());
    BufferBase buf = bm.pin(blk(3));
    assertEquals(blk(3), buf.block());
    assertEquals(1003, buf.contents().getInt(0));
    assertTrue(buf.isPinned());
    bm.unpin(buf);
    assertFalse(buf.isPinned());
    assertEquals(blk(3), buf.block(), "still holds the block while unpinned");
  }

  // ----------------------------------------------------- eviction policies

  /**
   * The lecture's worked example (slides 22-28): pin 10, 20, 30, 40; unpin
   * 20; pin 50 (replaces 20); unpin all; then pin 60, 70.  NAIVE scans from
   * buffer 0 each time: 60 -> buffer 0, 70 -> buffer 1, pool [60, 70, 30, 40].
   */
  @Test
  void naive_lectureExample_fillsFromBufferZero() {
    seedBlocks(80);
    BufferMgr bm = newMgr(4, 100, EvictionPolicy.NAIVE);
    BufferBase b10 = bm.pin(blk(10)), b20 = bm.pin(blk(20)), b30 = bm.pin(blk(30)), b40 = bm.pin(blk(40));
    bm.unpin(b20);
    BufferBase b50 = bm.pin(blk(50));
    assertSame(b20, b50, "50 took the only unpinned buffer");
    bm.unpin(b40); bm.unpin(b10); bm.unpin(b30); bm.unpin(b50);
    assertEquals(4, bm.available());
    fm.resetCounts();
    BufferBase b60 = bm.pin(blk(60));
    BufferBase b70 = bm.pin(blk(70));
    assertSame(b10, b60, "60 -> buffer 0");
    assertSame(b20, b70, "70 -> buffer 1");
    assertEquals(2, fm.reads.get());
    // 30 and 40 are still resident; 10 and 50 are gone
    assertSame(b30, bm.pin(blk(30)));
    assertSame(b40, bm.pin(blk(40)));
    assertEquals(2, fm.reads.get(), "hits");
  }

  /**
   * Same sequence under CLOCK (slides 38-41).  The hand sits just past
   * buffer 1 (where 50 replaced 20).  Every buffer's bit is set, so the
   * sweep clears 2, 3, 0, 1 and comes back to 2: 60 -> buffer 2; the hand
   * moves to 3, whose bit is now clear: 70 -> buffer 3.  Pool [10, 50, 60, 70].
   */
  @Test
  void clock_lectureExample_startsAfterLastReplacement() {
    seedBlocks(80);
    BufferMgr bm = newMgr(4, 100, EvictionPolicy.CLOCK);
    BufferBase b10 = bm.pin(blk(10)), b20 = bm.pin(blk(20)), b30 = bm.pin(blk(30)), b40 = bm.pin(blk(40));
    bm.unpin(b20);
    BufferBase b50 = bm.pin(blk(50));
    assertSame(b20, b50, "50 took the only unpinned buffer");
    bm.unpin(b40); bm.unpin(b10); bm.unpin(b30); bm.unpin(b50);
    fm.resetCounts();
    BufferBase b60 = bm.pin(blk(60));
    BufferBase b70 = bm.pin(blk(70));
    assertSame(b30, b60, "60 -> buffer 2");
    assertSame(b40, b70, "70 -> buffer 3");
    assertEquals(2, fm.reads.get());
    // 10 and 50 are still resident; 30 and 40 are gone
    assertSame(b10, bm.pin(blk(10)));
    assertSame(b50, bm.pin(blk(50)));
    assertEquals(2, fm.reads.get(), "hits");
  }

  /**
   * Slide 30: NAIVE reuses buffer 0 for everything when a single client pins
   * and unpins, so alternating between two blocks reads from disk every time
   * even though the pool has room for both.
   */
  @Test
  void naive_alternatingTwoBlocks_readsEveryTime() {
    seedBlocks(5);
    BufferMgr bm = newMgr(4, 100, EvictionPolicy.NAIVE);
    for (int i = 0; i < 10; i++) {
      bm.unpin(bm.pin(blk(0)));
      bm.unpin(bm.pin(blk(1)));
    }
    assertEquals(20, fm.reads.get(), "every pin was a miss");
  }

  /** The same pattern under CLOCK: the hand keeps moving, both blocks stay resident. */
  @Test
  void clock_alternatingTwoBlocks_readsEachOnce() {
    seedBlocks(5);
    BufferMgr bm = newMgr(4, 100, EvictionPolicy.CLOCK);
    for (int i = 0; i < 10; i++) {
      bm.unpin(bm.pin(blk(0)));
      bm.unpin(bm.pin(blk(1)));
    }
    assertEquals(2, fm.reads.get(), "each block read once");
  }

  /**
   * Slide 41, the second chance: a block pinned again before the hand comes
   * round has its bit set, so the sweep clears the bit and spares it.  A
   * hot block mixed with a stream of cold ones is evicted at most once
   * (when every bit happens to be set) and then kept; NAIVE evicts it on
   * every round.
   */
  @Test
  void clock_hotBlockSurvivesColdStream_naiveDoesNot() {
    seedBlocks(60);
    final int rounds = 38;
    BufferMgr clock = newMgr(3, 100, EvictionPolicy.CLOCK);
    bm_hotColdRounds(clock, rounds);
    int clockReads = fm.reads.get();
    fm.resetCounts();
    BufferMgr naive = newMgr(3, 100, EvictionPolicy.NAIVE);
    bm_hotColdRounds(naive, rounds);
    int naiveReads = fm.reads.get();
    System.out.println("reads: CLOCK " + clockReads + ", NAIVE " + naiveReads + " over " + rounds + " cold misses");
    assertEquals(1 + rounds + 1, clockReads, "hot block read at start, once more after the all-bits-set sweep, then kept");
    assertEquals(1 + 2 * rounds - 1, naiveReads, "NAIVE re-reads the hot block every round after the first");
  }

  private void bm_hotColdRounds(BufferMgr bm, int rounds) {
    bm.unpin(bm.pin(blk(1))); // the hot block
    for (int n = 2; n < 2 + rounds; n++) {
      bm.unpin(bm.pin(blk(1)));
      bm.unpin(bm.pin(blk(n)));
    }
  }

  /** Both policies skip pinned buffers: with one free slot, every miss lands there. */
  @Test
  void eviction_neverTakesAPinnedBuffer() {
    seedBlocks(20);
    for (EvictionPolicy policy : EvictionPolicy.values()) {
      BufferMgr bm = newMgr(3, 100, policy);
      BufferBase p0 = bm.pin(blk(0));
      BufferBase p1 = bm.pin(blk(1));
      BufferBase free = bm.pin(blk(2));
      bm.unpin(free);
      for (int n = 3; n < 15; n++) {
        BufferBase got = bm.pin(blk(n));
        assertSame(free, got, policy + ": the one unpinned buffer");
        bm.unpin(got);
      }
      assertEquals(1000, p0.contents().getInt(0), policy + ": pinned pages untouched");
      assertEquals(1001, p1.contents().getInt(0));
      assertEquals(blk(0), p0.block());
      assertEquals(blk(1), p1.block());
    }
  }

  // --------------------------------------------------------------- restart

  /** Data written through the buffer manager survives a restart of the whole stack. */
  @Test
  void restart_dataWrittenThroughBuffersIsReadBack() {
    seedBlocks(5);
    BufferMgr bm = newMgr(2, 100);
    BufferBase flushed = pinAndDirty(bm, 0, 111, 1);
    bm.flushAll(1);
    bm.unpin(flushed);
    BufferBase evicted = pinAndDirty(bm, 1, 222, 2);
    bm.unpin(evicted);
    bm.unpin(bm.pin(blk(2)));
    bm.unpin(bm.pin(blk(3))); // evicts block 1
    assertEquals(222, intOnDisk(1), "test setup: block 1 was evicted");

    start(false, BLOCK);
    BufferMgr reopened = newMgr(2, 100);
    assertEquals(111, reopened.pin(blk(0)).contents().getInt(0));
    assertEquals(222, reopened.pin(blk(1)).contents().getInt(0));
  }

  // -------------------------------------------------------- thread safety

  /**
   * Many threads pin, modify, flush, unpin and read back with a pool far
   * smaller than the working set.  Every value written must be the value
   * read back, every buffer must end unpinned, and no disk access may be
   * attributed to a block nobody asked for.
   */
  @Test
  void concurrency_pinsFromManyThreads_keepDataIntact() throws Exception {
    final int threads = 50, perThread = 100, nBuffers = 20;
    BufferMgr bm = newMgr(nBuffers, 2000, EvictionPolicy.CLOCK);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<?>> writers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        final int tx = t;
        writers.add(pool.submit(() -> {
          go.await();
          for (int i = 0; i < perThread; i++) {
            int n = tx * perThread + i;
            BufferBase buf = bm.pin(blk(n));
            buf.contents().setInt(0, n * 7);
            buf.contents().setString(Integer.BYTES, "t" + tx + "-" + i);
            buf.setModified(tx, lm.append(("w " + n).getBytes()));
            bm.unpin(buf);
          }
          bm.flushAll(tx);
          return null;
        }));
      }
      go.countDown();
      for (Future<?> f : writers) {
        f.get(60, TimeUnit.SECONDS);
      }
      assertEquals(nBuffers, bm.available(), "every buffer unpinned after the writers");

      List<Future<?>> readers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        final int seed = t;
        readers.add(pool.submit(() -> {
          Random rnd = new Random(seed);
          for (int i = 0; i < perThread; i++) {
            int n = rnd.nextInt(threads * perThread);
            BufferBase buf = bm.pin(blk(n));
            try {
              assertEquals(n * 7, buf.contents().getInt(0), "block " + n);
              assertEquals("t" + (n / perThread) + "-" + (n % perThread), buf.contents().getString(Integer.BYTES));
            } finally {
              bm.unpin(buf);
            }
          }
          return null;
        }));
      }
      for (Future<?> f : readers) {
        f.get(60, TimeUnit.SECONDS);
      }
      assertEquals(nBuffers, bm.available(), "every buffer unpinned after the readers");
    } finally {
      pool.shutdownNow();
    }
  }

  // --------------------------------- replicas of the professor's graded tests

  /**
   * Milestone sheet: 20 buffers, 500 ms maximum wait, 50,000 blocks of 4096
   * bytes created by pinning and writing a small String and int into each,
   * then read back randomly at a concurrency level of 50, each "transaction"
   * sleeping 2 ms before unpinning.  The professor's Mac Pro does this in
   * 6300 ms.  Correctness is asserted; the time is printed.
   *
   * Arithmetic: 20 buffers each held 2 ms allow at most 10,000 pins per
   * second, so 50,000 reads need at least 5 s; the sleep, not the buffer
   * manager, sets the floor.
   */
  @Test
  void professorReplica_50kBlocks_50Threads_20Buffers() throws Exception {
    start(true, 4096);
    final int nBlocks = 50_000, threads = 50, perThread = nBlocks / threads;
    BufferMgr bm = newMgr(20, 500);
    // Longest any single pin() took.  With 50 clients and 20 buffers, 30
    // clients are always waiting; a fair hand-off keeps this far below the
    // 500 ms limit (an unfair one let a waiter starve past it).
    AtomicLong longestPinNanos = new AtomicLong();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      long t0 = System.nanoTime();
      List<Future<?>> writers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        final int tx = t;
        writers.add(pool.submit(() -> {
          for (int i = 0; i < perThread; i++) {
            int n = tx * perThread + i;
            long p0 = System.nanoTime();
            BufferBase buf = bm.pin(blk(n));
            longestPinNanos.accumulateAndGet(System.nanoTime() - p0, Math::max);
            buf.contents().setString(0, "block-" + n);
            buf.contents().setInt(100, n);
            buf.setModified(tx, lm.append(("w " + n).getBytes()));
            Thread.sleep(2);
            bm.unpin(buf);
          }
          bm.flushAll(tx);
          return null;
        }));
      }
      for (Future<?> f : writers) {
        f.get(120, TimeUnit.SECONDS);
      }
      long writeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
      assertEquals(nBlocks, fm.length(DATA), "every block exists on disk");

      long t1 = System.nanoTime();
      AtomicInteger verified = new AtomicInteger();
      List<Future<?>> readers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        final int seed = t;
        readers.add(pool.submit(() -> {
          Random rnd = new Random(seed);
          for (int i = 0; i < perThread; i++) {
            int n = rnd.nextInt(nBlocks);
            long p0 = System.nanoTime();
            BufferBase buf = bm.pin(blk(n));
            longestPinNanos.accumulateAndGet(System.nanoTime() - p0, Math::max);
            try {
              assertEquals("block-" + n, buf.contents().getString(0));
              assertEquals(n, buf.contents().getInt(100));
              verified.incrementAndGet();
              Thread.sleep(2);
            } finally {
              bm.unpin(buf);
            }
          }
          return null;
        }));
      }
      for (Future<?> f : readers) {
        f.get(120, TimeUnit.SECONDS);
      }
      long readMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1);
      assertEquals(nBlocks, verified.get());
      assertEquals(20, bm.available());
      System.out.printf("professor replica: write phase %d ms, read phase %d ms, total %d ms (professor: 6300 ms); "
          + "longest single pin() %d ms (limit 500)%n",
          writeMs, readMs, writeMs + readMs, TimeUnit.NANOSECONDS.toMillis(longestPinNanos.get()));
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Milestone sheet: CLOCK must beat NAIVE "when data-access patterns imply
   * that the hot set can be cached in memory".  4096-byte blocks, 20
   * buffers; after creating the blocks, 80% of accesses go to 15 hot
   * blocks.  NAIVE keeps reusing buffer 0 (the others are frozen on cold
   * blocks from the creation phase), so almost every access misses; CLOCK
   * keeps the hot set resident.  Misses are asserted; times are printed.
   */
  @Test
  void professorReplica_clockBeatsNaiveOnHotSet() {
    start(true, 4096);
    final int nBlocks = 5_000, accesses = 100_000, hotSize = 15;
    long naiveMisses = -1, clockMisses = -1, naiveMs = -1, clockMs = -1;
    for (EvictionPolicy policy : new EvictionPolicy[] {EvictionPolicy.NAIVE, EvictionPolicy.CLOCK}) {
      BufferMgr bm = newMgr(20, 500, policy);
      for (int n = 0; n < nBlocks; n++) { // creation phase
        BufferBase buf = bm.pin(blk(n));
        buf.contents().setInt(0, n);
        buf.setModified(1);
        bm.unpin(buf);
      }
      bm.flushAll(1);
      Random rnd = new Random(42);
      int[] hot = new int[hotSize];
      for (int i = 0; i < hotSize; i++) {
        hot[i] = rnd.nextInt(nBlocks);
      }
      fm.resetCounts();
      long t0 = System.nanoTime();
      for (int i = 0; i < accesses; i++) {
        int n = rnd.nextInt(100) < 80 ? hot[rnd.nextInt(hotSize)] : rnd.nextInt(nBlocks);
        BufferBase buf = bm.pin(blk(n));
        assertEquals(n, buf.contents().getInt(0));
        bm.unpin(buf);
      }
      long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
      if (policy == EvictionPolicy.NAIVE) {
        naiveMisses = fm.reads.get();
        naiveMs = ms;
      } else {
        clockMisses = fm.reads.get();
        clockMs = ms;
      }
    }
    System.out.printf("hot set: NAIVE %d misses in %d ms; CLOCK %d misses in %d ms (%d accesses, 20 buffers)%n",
        naiveMisses, naiveMs, clockMisses, clockMs, accesses);
    assertTrue(clockMisses < naiveMisses / 2,
        "CLOCK misses " + clockMisses + " should be well under NAIVE's " + naiveMisses);
    assertTrue(clockMisses < accesses * 0.45, "CLOCK should serve most of the hot set from memory: " + clockMisses);
  }
}
