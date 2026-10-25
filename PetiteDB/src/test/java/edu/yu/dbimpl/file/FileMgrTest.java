package edu.yu.dbimpl.file;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import edu.yu.dbimpl.config.DBConfiguration;

/**
 * Tests for FileMgr, the disk I/O layer.
 *
 * What we care about here (from FileMgrBase Javadoc / lecture):
 * - Constructor honors DBConfiguration: new database wipes the directory,
 *   existing database keeps it; missing configuration is an error.
 * - Blocks written through a Page come back byte-for-byte via read.
 * - append/length grow files one block at a time; unknown files have length 0.
 * - Writing past the end creates intervening blocks; reading past the end
 *   creates nothing.
 * - Bad arguments are rejected with IllegalArgumentException.
 * - Data survives a "restart" (a new FileMgr on the same directory).
 * - Concurrent use from many threads is safe.
 *
 * Each test gets its own temp directory (lecture, slide 39) so tests can't
 * see each other's files.
 *
 * Portability: every FileMgr is created through openFileMgr() so that
 * tearDown() can close it.  Windows refuses to delete a file while a handle
 * to it is open, so without this JUnit couldn't remove the temp directory
 * and every test would error on cleanup.  macOS/Linux don't care, but the
 * tests shouldn't depend on that.
 */
class FileMgrTest {

  private static final int BLOCK = 400;

  @TempDir
  Path tempDir;

  private File dbDir;

  /** Every manager created by the running test, closed in tearDown(). */
  private final List<FileMgr> managers = new ArrayList<>();

  @BeforeEach
  void setUp(TestInfo info) {
    // A visible divider (with the test's name) so each test's log output is
    // easy to tell apart, even where the IDE test runner swallows blank lines.
    System.out.println("\n----------------------------------------");
    System.out.println("TEST: " + info.getTestMethod().map(m -> m.getName()).orElse("?"));
    System.out.println("----------------------------------------");
    dbDir = tempDir.resolve("db").toFile();
    configure(true);
  }

  /** Release every file handle before JUnit deletes the temp directory. */
  @AfterEach
  void tearDown() {
    for (FileMgr fm : managers) {
      fm.close();
    }
    managers.clear();
  }

  /** Create a FileMgr and remember it so tearDown() can close it. */
  private FileMgr openFileMgr(File dir, int blockSize) {
    FileMgr fm = new FileMgr(dir, blockSize);
    managers.add(fm);
    return fm;
  }

  /** Point the DBConfiguration singleton at a fresh or existing database. */
  private static void configure(boolean startup) {
    Properties props = new Properties();
    props.setProperty(DBConfiguration.DB_STARTUP, String.valueOf(startup));
    DBConfiguration.INSTANCE.get().setConfiguration(props);
  }

  // ------------------------------------------------------------ constructor

  /**
   * A new database on a missing directory creates it and reports the block
   * size it was given.
   */
  @Test
  void constructor_createsDirectoryAndKeepsBlockSize() {
    assertFalse(dbDir.exists());
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    assertTrue(dbDir.isDirectory());
    assertEquals(BLOCK, fm.blockSize());
  }

  /**
   * Null directory or non-positive block size is an IllegalArgumentException.
   */
  @Test
  void constructor_rejectsBadArguments() {
    assertThrows(IllegalArgumentException.class, () -> new FileMgr(null, BLOCK));
    assertThrows(IllegalArgumentException.class, () -> new FileMgr(dbDir, 0));
    assertThrows(IllegalArgumentException.class, () -> new FileMgr(dbDir, -1));
  }

  /**
   * FileMgrBase: IllegalStateException if DBConfiguration can't say whether
   * this is a new database.  Nothing may be created on disk in that case.
   */
  @Test
  void constructor_withoutStartupConfig_throwsAndCreatesNothing() {
    DBConfiguration.INSTANCE.get().setConfiguration(new Properties());
    assertThrows(IllegalStateException.class, () -> new FileMgr(dbDir, BLOCK));
    assertFalse(dbDir.exists());
  }

  /**
   * A relative directory resolves against the JVM's working directory.
   * This test can't use the temp directory (that would be absolute), so it
   * cleans up after itself.  The manager is closed first: on Windows the
   * file can't be deleted while its handle is open.
   */
  @Test
  void constructor_relativeDirectory_resolvesAgainstWorkingDirectory() throws Exception {
    File rel = new File("target", "filemgr-rel-test-" + System.nanoTime());
    try {
      FileMgr fm = openFileMgr(rel, BLOCK);
      fm.append("t");
      File expected = new File(new File(System.getProperty("user.dir"), rel.getPath()), "t");
      assertTrue(expected.isFile());
    } finally {
      tearDown();
      deleteRecursively(rel.toPath());
    }
  }

  /**
   * FileMgrBase: "If no such directory exists when the constructor is
   * invoked, the implementation must create it" -- including any missing
   * parent directories.
   */
  @Test
  void constructor_createsMissingParentDirectories() {
    File nested = tempDir.resolve("a").resolve("b").resolve("c").toFile();
    assertFalse(nested.getParentFile().exists());
    FileMgr fm = openFileMgr(nested, BLOCK);
    assertTrue(nested.isDirectory());
    assertEquals(0, fm.append("t").number());
  }

  /**
   * The same rule applies to an EXISTING database (db.startup=false) whose
   * directory isn't there yet: it is created, empty, and usable.
   */
  @Test
  void constructor_existingDatabaseOnMissingDirectory_createsIt() {
    configure(false);
    assertFalse(dbDir.exists());
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    assertTrue(dbDir.isDirectory());
    assertEquals(0, fm.length("t"));
    assertEquals(0, fm.append("t").number());
  }

  /**
   * A regular file sitting where the database directory should be can't be
   * used, in either startup mode, and must not be destroyed.
   */
  @Test
  void constructor_pathIsRegularFile_throwsAndKeepsFile() throws Exception {
    Files.write(dbDir.toPath(), new byte[] {1, 2, 3});
    assertThrows(RuntimeException.class, () -> new FileMgr(dbDir, BLOCK));
    configure(false);
    assertThrows(RuntimeException.class, () -> new FileMgr(dbDir, BLOCK));
    assertTrue(dbDir.isFile());
    assertEquals(3L, dbDir.length());
  }

  /**
   * A brand-new database starts from an empty directory even if a previous
   * occupant left files and nested subdirectories behind.
   */
  @Test
  void constructor_newDatabase_wipesFilesAndSubdirectories() throws Exception {
    Path deep = dbDir.toPath().resolve("sub").resolve("deeper");
    Files.createDirectories(deep);
    Files.write(dbDir.toPath().resolve("old.tbl"), new byte[BLOCK]);
    Files.write(deep.resolve("stale"), new byte[] {1});

    FileMgr fm = openFileMgr(dbDir, BLOCK);
    assertTrue(dbDir.isDirectory());
    assertEquals(0, dbDir.list().length);
    assertEquals(0, fm.length("old.tbl"));
  }

  /**
   * A db.startup value that is neither "true" nor "false" means the
   * configuration can't say which mode to use.  DBConfiguration reports that
   * with an unchecked exception; whichever one it is, it must escape the
   * constructor and nothing may be created or deleted on disk.
   */
  @Test
  void constructor_malformedStartupConfig_throwsAndTouchesNothing() {
    Properties props = new Properties();
    props.setProperty(DBConfiguration.DB_STARTUP, "yes");
    DBConfiguration.INSTANCE.get().setConfiguration(props);
    assertThrows(RuntimeException.class, () -> new FileMgr(dbDir, BLOCK));
    assertFalse(dbDir.exists());
  }

  // --------------------------------------------------------- read / write

  /**
   * Lecture slide 3: "Your implementation has to handle all byte size
   * specifications correctly".  Small and odd block sizes -- including ones
   * too small to hold an int -- must still give exact block boundaries:
   * every byte of every block comes back, and the file is exactly
   * blocks * size bytes long.  Booleans are used because they are the one
   * type that fits in any block.
   */
  @Test
  void oddAndTinyBlockSizes_keepExactBlockBoundaries() {
    for (int size : new int[] {1, 2, 3, 7, 13, 127, 1000, 4097}) {
      File dir = tempDir.resolve("bs" + size).toFile();
      FileMgr fm = openFileMgr(dir, size);
      assertEquals(size, fm.blockSize());
      final int blocks = 5;
      for (int b = 0; b < blocks; b++) {
        Page out = new Page(size);
        for (int j = 0; j < size; j++) {
          out.setBoolean(j, pattern(b, j));
        }
        fm.write(new BlockId("f", b), out);
      }
      assertEquals(blocks, fm.length("f"), "block size " + size);
      assertEquals((long) blocks * size, new File(dir, "f").length(), "block size " + size);
      // Read in reverse so a pass can't be an accident of sequential order.
      for (int b = blocks - 1; b >= 0; b--) {
        Page in = new Page(size);
        fm.read(new BlockId("f", b), in);
        for (int j = 0; j < size; j++) {
          assertEquals(pattern(b, j), in.getBoolean(j), "size " + size + " block " + b + " byte " + j);
        }
      }
      assertEquals(blocks, fm.append("f").number(), "block size " + size);
      assertEquals((long) (blocks + 1) * size, new File(dir, "f").length(), "block size " + size);
    }
  }

  /** A per-block, per-byte pattern that differs between neighboring blocks. */
  private static boolean pattern(int block, int index) {
    return (block * 31 + index * 7) % 3 == 0;
  }

  /**
   * Every PetiteDB type written into a page and stored in a block comes back
   * identical after a read into a different page.
   */
  @Test
  void writeThenRead_roundTripsAllTypes() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    BlockId blk = new BlockId("data", 3);

    Page out = new Page(BLOCK);
    out.setInt(0, -77);
    out.setDouble(4, 2.718);
    out.setBoolean(12, true);
    out.setString(13, "petite");
    out.setBytes(40, new byte[] {9, 8, 7});
    fm.write(blk, out);

    Page in = new Page(BLOCK);
    fm.read(blk, in);
    assertEquals(-77, in.getInt(0));
    assertEquals(2.718, in.getDouble(4));
    assertTrue(in.getBoolean(12));
    assertEquals("petite", in.getString(13));
    assertArrayEquals(new byte[] {9, 8, 7}, in.getBytes(40));
  }

  /**
   * Blocks are independent: writing one doesn't disturb its neighbors, in
   * the same file or in another file.
   */
  @Test
  void blocksAndFiles_areIndependent() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    for (int i = 0; i < 5; i++) {
      Page p = new Page(BLOCK);
      p.setInt(0, i);
      p.setString(4, "f1-" + i);
      fm.write(new BlockId("f1", i), p);
      Page q = new Page(BLOCK);
      q.setInt(0, 100 + i);
      fm.write(new BlockId("f2", i), q);
    }
    // Rewrite block 2 of f1; others must be untouched.
    Page r = new Page(BLOCK);
    r.setInt(0, 222);
    r.setString(4, "f1-rewritten");
    fm.write(new BlockId("f1", 2), r);

    for (int i = 0; i < 5; i++) {
      Page p = new Page(BLOCK);
      fm.read(new BlockId("f1", i), p);
      if (i == 2) {
        assertEquals(222, p.getInt(0));
        assertEquals("f1-rewritten", p.getString(4));
      } else {
        assertEquals(i, p.getInt(0));
        assertEquals("f1-" + i, p.getString(4));
      }
      Page q = new Page(BLOCK);
      fm.read(new BlockId("f2", i), q);
      assertEquals(100 + i, q.getInt(0));
    }
    assertEquals(5, fm.length("f1"));
    assertEquals(5, fm.length("f2"));
  }

  /**
   * A page that was filled to the last byte survives the trip to disk.
   */
  @Test
  void fullPage_roundTrips() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    String s = "x".repeat(BLOCK - Integer.BYTES);
    Page out = new Page(BLOCK);
    out.setString(0, s);
    fm.write(new BlockId("full", 0), out);
    Page in = new Page(BLOCK);
    fm.read(new BlockId("full", 0), in);
    assertEquals(s, in.getString(0));
  }

  /**
   * A Page built on a client byte[] (the log manager's kind) is also usable
   * for I/O when the array is exactly one block long.  This exercises the
   * heap-buffer path through FileChannel, which Page(int)'s direct buffer
   * never does.  After the read, the client's own array holds the block.
   */
  @Test
  void heapPage_roundTripsThroughFileMgr() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    byte[] outArr = new byte[BLOCK];
    Page out = new Page(outArr);
    out.setInt(0, 31);
    out.setString(4, "from a byte[]");
    fm.write(new BlockId("heap", 2), out);

    byte[] inArr = new byte[BLOCK];
    Page in = new Page(inArr);
    fm.read(new BlockId("heap", 2), in);
    assertEquals(31, in.getInt(0));
    assertEquals("from a byte[]", in.getString(4));
    assertArrayEquals(outArr, inArr);
  }

  // ------------------------------------------------------- append / length

  /**
   * A file that was never written has length 0, and naming it doesn't give
   * it any blocks.
   */
  @Test
  void length_ofUnknownFile_isZero() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    assertEquals(0, fm.length("nothing"));
    assertEquals(0, fm.length("nothing"));
  }

  /**
   * append hands out consecutive block numbers, each call grows the file by
   * exactly one block on disk, and the new block is readable and zeroed.
   */
  @Test
  void append_growsFileOneBlockAtATime() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    for (int i = 0; i < 4; i++) {
      BlockIdBase blk = fm.append("log");
      assertEquals("log", blk.fileName());
      assertEquals(i, blk.number());
      assertEquals(i + 1, fm.length("log"));
      assertEquals((long) (i + 1) * BLOCK, new File(dbDir, "log").length());
    }
    Page p = new Page(BLOCK);
    p.setInt(0, 12345); // dirty the page first so zeros really come from disk
    fm.read(new BlockId("log", 3), p);
    assertEquals(0, p.getInt(0));
  }

  /**
   * Lecture slide 49: writing block 10 of an empty file creates blocks 0-10;
   * the earlier ones read back as zeros.
   */
  @Test
  void write_pastEnd_createsPrecedingBlocks() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    Page p = new Page(BLOCK);
    p.setString(0, "tenth");
    fm.write(new BlockId("sparse", 10), p);
    assertEquals(11, fm.length("sparse"));
    assertEquals(11L * BLOCK, new File(dbDir, "sparse").length());

    Page q = new Page(BLOCK);
    q.setInt(0, 99);
    fm.read(new BlockId("sparse", 4), q);
    assertEquals(0, q.getInt(0));
    fm.read(new BlockId("sparse", 10), q);
    assertEquals("tenth", q.getString(0));
    // The next append lands after block 10.
    assertEquals(11, fm.append("sparse").number());
  }

  /**
   * Lecture slide 49: reading a block that doesn't exist is allowed, throws
   * nothing, and must not create the block.
   */
  @Test
  void read_pastEnd_createsNothing() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    fm.append("short");
    Page p = new Page(BLOCK);
    fm.read(new BlockId("short", 7), p);
    assertEquals(1, fm.length("short"));
    assertEquals((long) BLOCK, new File(dbDir, "short").length());
  }

  // ------------------------------------------------------------ bad input

  /**
   * Null arguments, a page of the wrong size, and a foreign PageBase
   * implementation are all rejected before any I/O happens.
   */
  @Test
  void readWrite_rejectBadArguments() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    BlockId blk = new BlockId("f", 0);
    Page good = new Page(BLOCK);
    Page wrongSize = new Page(BLOCK + 1);
    PageBase foreign = new ForeignPage();

    assertThrows(IllegalArgumentException.class, () -> fm.read(null, good));
    assertThrows(IllegalArgumentException.class, () -> fm.write(null, good));
    assertThrows(IllegalArgumentException.class, () -> fm.read(blk, null));
    assertThrows(IllegalArgumentException.class, () -> fm.write(blk, null));
    assertThrows(IllegalArgumentException.class, () -> fm.read(blk, wrongSize));
    assertThrows(IllegalArgumentException.class, () -> fm.write(blk, wrongSize));
    assertThrows(IllegalArgumentException.class, () -> fm.read(blk, foreign));
    assertThrows(IllegalArgumentException.class, () -> fm.write(blk, foreign));
    assertThrows(IllegalArgumentException.class, () -> fm.append(null));
    assertThrows(IllegalArgumentException.class, () -> fm.append("  "));
    assertThrows(IllegalArgumentException.class, () -> fm.length(null));
    assertThrows(IllegalArgumentException.class, () -> fm.length(""));
    assertEquals(0, fm.length("f"));
  }

  // -------------------------------------------------------------- restart

  /**
   * The grading item "running a previously created database works": data
   * written by one FileMgr is visible to a new FileMgr opened on the same
   * directory with db.startup=false, and wiped by one opened with true.
   */
  @Test
  void restart_keepsDataWhenNotStartup_andWipesWhenStartup() {
    FileMgr first = openFileMgr(dbDir, BLOCK);
    Page p = new Page(BLOCK);
    p.setString(0, "persisted");
    first.write(new BlockId("t", 0), p);
    first.append("t");
    assertEquals(2, first.length("t"));
    // A real restart is a new JVM, so the old manager's handles are gone.
    // Simulate that by closing; on Windows the wipe below would otherwise
    // fail because "t" is still open.
    first.close();

    configure(false);
    FileMgr reopened = openFileMgr(dbDir, BLOCK);
    assertEquals(2, reopened.length("t"));
    Page q = new Page(BLOCK);
    reopened.read(new BlockId("t", 0), q);
    assertEquals("persisted", q.getString(0));
    reopened.close();

    configure(true);
    FileMgr fresh = openFileMgr(dbDir, BLOCK);
    assertTrue(dbDir.isDirectory());
    // The wipe happened in the constructor, so the directory is empty at this
    // point.  length("t") then re-creates "t" on demand (as an empty file),
    // which is why the final check tolerates exactly that one name.
    assertEquals(0, fresh.length("t"));
    assertEquals(0, dbDir.list((d, n) -> !n.equals("t")).length);
  }

  /**
   * A fuller restart: several files, every PetiteDB type, blocks other than
   * block 0, and a gap left by writing past the end.  All of it must be
   * there, unchanged, when the database is reopened, and the reopened
   * manager must carry on from where the first one stopped.
   */
  @Test
  void restart_preservesAllTypesAcrossMultipleFilesAndBlocks() {
    FileMgr first = openFileMgr(dbDir, BLOCK);
    Page p = new Page(BLOCK);
    p.setInt(0, Integer.MIN_VALUE);
    p.setDouble(4, -1.5e300);
    p.setBoolean(12, true);
    p.setString(13, "alpha");
    p.setBytes(60, new byte[] {-1, 0, 1});
    first.write(new BlockId("a.tbl", 0), p);

    Page q = new Page(BLOCK);
    q.setString(0, "a-block-5");
    q.setBoolean(BLOCK - 1, true); // last byte of the block
    first.write(new BlockId("a.tbl", 5), q);

    Page r = new Page(BLOCK);
    r.setInt(100, 424242);
    first.write(new BlockId("b.tbl", 2), r);
    first.append("c.log");
    first.close();

    configure(false);
    FileMgr reopened = openFileMgr(dbDir, BLOCK);
    assertEquals(6, reopened.length("a.tbl"));
    assertEquals(3, reopened.length("b.tbl"));
    assertEquals(1, reopened.length("c.log"));
    assertEquals(0, reopened.length("never-created"));

    Page in = new Page(BLOCK);
    reopened.read(new BlockId("a.tbl", 0), in);
    assertEquals(Integer.MIN_VALUE, in.getInt(0));
    assertEquals(-1.5e300, in.getDouble(4));
    assertTrue(in.getBoolean(12));
    assertEquals("alpha", in.getString(13));
    assertArrayEquals(new byte[] {-1, 0, 1}, in.getBytes(60));

    reopened.read(new BlockId("a.tbl", 5), in);
    assertEquals("a-block-5", in.getString(0));
    assertTrue(in.getBoolean(BLOCK - 1));

    reopened.read(new BlockId("a.tbl", 3), in); // inside the gap
    assertEquals(0, in.getInt(0));

    reopened.read(new BlockId("b.tbl", 2), in);
    assertEquals(424242, in.getInt(100));

    // Carries on from the persisted end, without disturbing what's there.
    assertEquals(6, reopened.append("a.tbl").number());
    reopened.read(new BlockId("a.tbl", 5), in);
    assertEquals("a-block-5", in.getString(0));
  }

  // ---------------------------------------------------------- concurrency

  /**
   * Many threads writing and reading the SAME block.  Each thread writes a
   * block filled with its own letter; every read must return one complete
   * block from some writer, never a mixture of two.
   *
   * This holds on every platform because FileMgr locks each block for the
   * duration of a transfer; it does not rely on the operating system making
   * file reads and writes atomic with respect to each other.
   */
  @Test
  void concurrentWritesAndReadsOfSameBlock_neverTear() throws InterruptedException {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int threads = 8;
    final int chars = BLOCK - Integer.BYTES;
    final BlockId blk = new BlockId("hot", 0);
    final Set<String> valid = new HashSet<>();
    for (int t = 0; t < threads; t++) {
      valid.add(letters(t, chars));
    }
    Page init = new Page(BLOCK);
    init.setString(0, letters(0, chars));
    fm.write(blk, init); // so the very first read is valid

    final List<Throwable> failures = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final String mine = letters(t, chars);
      workers.add(new Thread(() -> {
        try {
          Page out = new Page(BLOCK);
          out.setString(0, mine);
          Page in = new Page(BLOCK);
          for (int i = 0; i < 500; i++) {
            fm.write(blk, out);
            fm.read(blk, in);
            if (!valid.contains(in.getString(0))) {
              throw new AssertionError("torn block read");
            }
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    runAll(workers);
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);
    assertEquals(1, fm.length("hot"));
  }

  /**
   * Many threads asking for the same brand-new file at the same instant:
   * the file must be opened exactly once, so every append lands in the one
   * file and gets its own block number.  Repeated over several files to
   * give the race many chances to happen.
   */
  @Test
  void concurrentFirstUseOfSameFile_opensItOnce() throws InterruptedException {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int threads = 8;
    for (int round = 0; round < 25; round++) {
      final String name = "race" + round;
      final CountDownLatch start = new CountDownLatch(1);
      final Set<Integer> numbers = new HashSet<>();
      final List<Throwable> failures = new ArrayList<>();
      List<Thread> workers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        workers.add(new Thread(() -> {
          try {
            start.await();
            int n = fm.append(name).number();
            synchronized (numbers) { numbers.add(n); }
          } catch (Throwable e) {
            synchronized (failures) { failures.add(e); }
          }
        }));
      }
      for (Thread w : workers) {
        w.start();
      }
      start.countDown(); // release everyone together
      for (Thread w : workers) {
        w.join();
      }
      assertTrue(failures.isEmpty(), () -> "failures: " + failures);
      assertEquals(threads, numbers.size(), name + ": duplicate block numbers");
      assertEquals(threads, fm.length(name));
      assertEquals((long) threads * BLOCK, new File(dbDir, name).length());
    }
  }

  /**
   * Threads working on different files at the same time don't see each
   * other's data, and every file ends up with exactly its own blocks.
   */
  @Test
  void concurrentUseOfDifferentFiles_isIndependent() throws InterruptedException {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int threads = 8;
    final int perThread = 40;
    final List<Throwable> failures = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final String name = "file" + t;
      workers.add(new Thread(() -> {
        try {
          Page out = new Page(BLOCK);
          Page in = new Page(BLOCK);
          for (int i = 0; i < perThread; i++) {
            out.setString(0, name + "-" + i);
            fm.write(new BlockId(name, i), out);
          }
          for (int i = perThread - 1; i >= 0; i--) {
            fm.read(new BlockId(name, i), in);
            if (!(name + "-" + i).equals(in.getString(0))) {
              throw new AssertionError(name + " block " + i + " holds " + in.getString(0));
            }
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    runAll(workers);
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);
    for (int t = 0; t < threads; t++) {
      assertEquals(perThread, fm.length("file" + t));
    }
  }

  /**
   * One Page shared between threads: some keep changing it through the
   * setters while others flush it to disk.  A flush must capture the page
   * at one moment -- a complete value from one setter call -- never a page
   * that changed halfway through the transfer.  Each flusher uses its own
   * block and its own page for reading back, so the only thing shared is
   * the page under test.
   */
  @Test
  void concurrentSettersAndFlushesOfSharedPage_neverTear() throws InterruptedException {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int setters = 4;
    final int flushers = 4;
    final int chars = BLOCK - Integer.BYTES;
    final Page shared = new Page(BLOCK);
    final Set<String> valid = new HashSet<>();
    for (int t = 0; t < setters; t++) {
      valid.add(letters(t, chars));
    }
    shared.setString(0, letters(0, chars));

    final List<Throwable> failures = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < setters; t++) {
      final String mine = letters(t, chars);
      workers.add(new Thread(() -> {
        try {
          for (int i = 0; i < 5_000; i++) {
            shared.setString(0, mine);
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    for (int t = 0; t < flushers; t++) {
      final BlockId blk = new BlockId("flush", t);
      workers.add(new Thread(() -> {
        try {
          Page in = new Page(BLOCK);
          for (int i = 0; i < 500; i++) {
            fm.write(blk, shared);
            fm.read(blk, in);
            if (!valid.contains(in.getString(0))) {
              throw new AssertionError("page changed while being written to " + blk);
            }
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    runAll(workers);
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);
  }

  /** A string of {@code chars} copies of the t-th letter of the alphabet. */
  private static String letters(int t, int chars) {
    return String.valueOf((char) ('a' + t)).repeat(chars);
  }

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
   * Many threads appending to one file and writing/reading their own blocks:
   * every append must return a distinct block, every block must read back
   * exactly what its owner wrote, and the file must end up with one block
   * per append.
   */
  @Test
  void concurrentAppendWriteRead_isSafe() throws InterruptedException {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int threads = 8;
    final int perThread = 50;
    final List<Throwable> failures = new ArrayList<>();
    final boolean[] seen = new boolean[threads * perThread];
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final int id = t;
      Thread w = new Thread(() -> {
        try {
          Page out = new Page(BLOCK);
          Page in = new Page(BLOCK);
          for (int i = 0; i < perThread; i++) {
            BlockIdBase blk = fm.append("shared");
            synchronized (seen) {
              if (seen[blk.number()]) {
                throw new AssertionError("block " + blk.number() + " handed out twice");
              }
              seen[blk.number()] = true;
            }
            out.setInt(0, id);
            out.setInt(4, i);
            out.setString(8, "thread-" + id + "-iter-" + i);
            fm.write(blk, out);
            fm.read(blk, in);
            if (in.getInt(0) != id || in.getInt(4) != i
                || !("thread-" + id + "-iter-" + i).equals(in.getString(8))) {
              throw new AssertionError("torn read at " + blk);
            }
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      });
      workers.add(w);
      w.start();
    }
    for (Thread w : workers) {
      w.join();
    }
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);
    assertEquals(threads * perThread, fm.length("shared"));
  }

  // ----------------------------------------------------------- file cache

  /*
   * The operating system lends a process only so many open files at once
   * (about 10,000 on macOS), so the FileMgr keeps at most MAX_OPEN_FILES
   * channels open and closes the least-recently-used one when it needs
   * room.  Closing a file must be invisible to clients: its data and length
   * are unchanged, and naming it again simply reopens it.  These tests use
   * the package-private openFileCount() / isOpen() hooks to watch the cache.
   */

  /** One block of recognisable content for file number {@code i}. */
  private static void writeTag(FileMgr fm, String file, int i) {
    Page out = new Page(BLOCK);
    out.setInt(0, i);
    out.setString(Integer.BYTES, "file-" + i);
    fm.write(new BlockId(file, 0), out);
  }

  /** Check that file number {@code i} still holds what writeTag() put there. */
  private static void assertTag(FileMgr fm, String file, int i) {
    Page in = new Page(BLOCK);
    fm.read(new BlockId(file, 0), in);
    assertEquals(i, in.getInt(0), "int in " + file);
    assertEquals("file-" + i, in.getString(Integer.BYTES), "string in " + file);
  }

  /**
   * Three times more files than the cache can hold: the cache must never
   * grow past its cap, and every file must read back intact after being
   * closed and reopened behind the scenes.
   */
  @Test
  void fileCache_neverExceedsCap_andEvictedFilesReadBackIntact() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int files = 3 * FileMgr.MAX_OPEN_FILES;
    for (int i = 0; i < files; i++) {
      writeTag(fm, "f" + i, i);
      assertTrue(fm.openFileCount() <= FileMgr.MAX_OPEN_FILES,
          "cache grew to " + fm.openFileCount() + " after " + (i + 1) + " files");
    }
    assertEquals(FileMgr.MAX_OPEN_FILES, fm.openFileCount());
    for (int i = 0; i < files; i++) {
      assertTag(fm, "f" + i, i);
      assertEquals(1, fm.length("f" + i));
    }
    assertEquals(FileMgr.MAX_OPEN_FILES, fm.openFileCount());
  }

  /**
   * The file used longest ago is the one that goes.  Fill the cache, then
   * touch the oldest file again: it must survive the next eviction while
   * the file that is now oldest does not.
   */
  @Test
  void fileCache_evictsLeastRecentlyUsedFile() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int cap = FileMgr.MAX_OPEN_FILES;
    for (int i = 0; i < cap; i++) {
      writeTag(fm, "f" + i, i);
    }
    assertEquals(cap, fm.openFileCount());
    assertTrue(fm.isOpen("f0"));
    assertTrue(fm.isOpen("f1"));

    assertTag(fm, "f0", 0); // f0 is now the most recently used; f1 is the oldest
    writeTag(fm, "extra", -1); // needs room: exactly one file must go
    assertEquals(cap, fm.openFileCount());
    assertTrue(fm.isOpen("f0"), "recently used file was evicted");
    assertFalse(fm.isOpen("f1"), "least recently used file was kept");
    assertTrue(fm.isOpen("extra"));

    assertTag(fm, "f1", 1); // reopens it, evicting the next oldest (f2)
    assertTrue(fm.isOpen("f1"));
    assertFalse(fm.isOpen("f2"));
    assertEquals(cap, fm.openFileCount());
  }

  /**
   * A file's length and block numbering must survive eviction: append after
   * the file was closed and reopened must continue where it left off, and
   * blocks written before the eviction must still be there.
   */
  @Test
  void fileCache_appendAndLengthSurviveEviction() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    Page out = new Page(BLOCK);
    for (int b = 0; b < 3; b++) {
      out.setInt(0, 100 + b);
      fm.write(new BlockId("victim", b), out);
    }
    assertEquals(3, fm.append("victim").number());
    assertEquals(4, fm.length("victim"));

    for (int i = 0; i < FileMgr.MAX_OPEN_FILES; i++) { // push "victim" out of the cache
      writeTag(fm, "filler" + i, i);
    }
    assertFalse(fm.isOpen("victim"), "test setup: victim should have been evicted");

    assertEquals(4, fm.length("victim"));
    assertEquals(4, fm.append("victim").number());
    assertEquals(5, fm.length("victim"));
    Page in = new Page(BLOCK);
    for (int b = 0; b < 3; b++) {
      fm.read(new BlockId("victim", b), in);
      assertEquals(100 + b, in.getInt(0), "block " + b + " after reopen");
    }
    assertTrue(fm.isOpen("victim"));
  }

  /**
   * close() empties the cache; the next use of any file reopens it with its
   * data intact.
   */
  @Test
  void fileCache_closeEmptiesCache_andUseReopens() {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    writeTag(fm, "a", 1);
    writeTag(fm, "b", 2);
    assertEquals(2, fm.openFileCount());
    fm.close();
    assertEquals(0, fm.openFileCount());
    assertFalse(fm.isOpen("a"));
    assertTag(fm, "a", 1);
    assertEquals(1, fm.openFileCount());
    assertTrue(fm.isOpen("a"));
    assertFalse(fm.isOpen("b"));
  }

  /**
   * The professor's stress scenario in miniature: many threads, far more
   * files than the cache holds, each file written then read back.  Every
   * file must come back intact, nothing may fail, and the cache must still
   * be within its cap when the dust settles.
   */
  @Test
  void fileCache_manyThreadsManyFiles_allReadBackIntact() throws InterruptedException {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int threads = 16;
    final int perThread = 200; // 3,200 files, 12x the cache
    final List<Throwable> failures = new ArrayList<>();
    final CountDownLatch go = new CountDownLatch(1);
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final int id = t;
      workers.add(new Thread(() -> {
        try {
          go.await();
          for (int i = 0; i < perThread; i++) {
            int n = id * perThread + i;
            writeTag(fm, "many" + n, n);
          }
          for (int i = 0; i < perThread; i++) {
            int n = id * perThread + i;
            assertTag(fm, "many" + n, n);
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    for (Thread w : workers) {
      w.start();
    }
    go.countDown();
    for (Thread w : workers) {
      w.join();
    }
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);
    assertTrue(fm.openFileCount() <= FileMgr.MAX_OPEN_FILES,
        "cache holds " + fm.openFileCount());
    for (int n = 0; n < threads * perThread; n++) {
      assertEquals(1, fm.length("many" + n));
    }
  }

  /**
   * Eviction must never close a file that another thread is in the middle
   * of using.  Some threads churn through brand-new files, forcing an
   * eviction on nearly every call, while others keep reading and writing a
   * fixed set of files that are constantly being pushed out and reopened.
   * No transfer may fail (a closed channel would surface as a
   * RuntimeException) and the fixed files must always read back correctly.
   */
  @Test
  void fileCache_churnWhileOthersTransfer_neverClosesFileInUse() throws InterruptedException {
    FileMgr fm = openFileMgr(dbDir, BLOCK);
    final int hotFiles = 2 * FileMgr.MAX_OPEN_FILES; // more than fit, so they keep cycling
    final int churners = 4;
    final int hammerers = 4;
    for (int i = 0; i < hotFiles; i++) {
      writeTag(fm, "hot" + i, i);
    }
    final List<Throwable> failures = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < churners; t++) {
      final int id = t;
      workers.add(new Thread(() -> {
        try {
          for (int i = 0; i < 1_000; i++) {
            writeTag(fm, "churn-" + id + "-" + i, i);
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    for (int t = 0; t < hammerers; t++) {
      final int id = t;
      workers.add(new Thread(() -> {
        try {
          for (int i = 0; i < 1_000; i++) {
            int n = (id * 7919 + i * 13) % hotFiles; // bounce around the hot set
            assertTag(fm, "hot" + n, n);
            writeTag(fm, "hot" + n, n); // same content, so readers can't tell writers apart
            assertEquals(1, fm.length("hot" + n));
          }
        } catch (Throwable e) {
          synchronized (failures) { failures.add(e); }
        }
      }));
    }
    runAll(workers);
    assertTrue(failures.isEmpty(), () -> "failures: " + failures);
    assertTrue(fm.openFileCount() <= FileMgr.MAX_OPEN_FILES);
  }

  // ------------------------------------------------------------ benchmark

  /**
   * The spec's reference workload: 400 blocks of 4096 bytes, each filled to
   * the brim with a String, written then read back.  The elapsed time is
   * printed for comparison with the ~2.1 s reference; correctness is asserted.
   */
  @Test
  void benchmark_400BlocksOf4096() {
    final int size = 4096;
    final int blocks = 400;
    FileMgr fm = openFileMgr(dbDir, size);
    final int chars = size - Integer.BYTES;

    long start = System.nanoTime();
    Page out = new Page(size);
    for (int i = 0; i < blocks; i++) {
      out.setString(0, payload(i, chars));
      fm.write(new BlockId("bench", i), out);
    }
    long written = System.nanoTime();
    Page in = new Page(size);
    for (int i = 0; i < blocks; i++) {
      fm.read(new BlockId("bench", i), in);
      assertEquals(payload(i, chars), in.getString(0));
    }
    long end = System.nanoTime();
    System.out.printf("benchmark: %d blocks x %d bytes: write %.3f s, read %.3f s, total %.3f s%n",
        blocks, size, (written - start) / 1e9, (end - written) / 1e9, (end - start) / 1e9);
    assertEquals(blocks, fm.length("bench"));
  }

  /** A block-filling string that differs per block. */
  private static String payload(int i, int chars) {
    String head = "block-" + i + "-";
    return head + "y".repeat(chars - head.length());
  }

  // -------------------------------------------------------------- helpers

  private static void deleteRecursively(Path root) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (var walk = Files.walk(root)) {
      for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.delete(p);
      }
    }
  }

  /** A PageBase that isn't our Page: the FileMgr can't reach its bytes. */
  private static final class ForeignPage extends PageBase {
    ForeignPage() { super(BLOCK); }
    @Override public int getInt(int offset) { return 0; }
    @Override public void setInt(int offset, int n) { }
    @Override public double getDouble(int offset) { return 0; }
    @Override public void setDouble(int offset, double d) { }
    @Override public boolean getBoolean(int offset) { return false; }
    @Override public void setBoolean(int offset, boolean d) { }
    @Override public byte[] getBytes(int offset) { return new byte[0]; }
    @Override public void setBytes(int offset, byte[] b) { }
    @Override public String getString(int offset) { return ""; }
    @Override public void setString(int offset, String s) { }
  }
}
