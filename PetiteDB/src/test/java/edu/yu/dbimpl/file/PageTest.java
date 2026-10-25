package edu.yu.dbimpl.file;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Page, the main-memory block image.
 *
 * What we care about here (from PageBase Javadoc / lecture):
 * - Every type round-trips through its setter/getter at any offset.
 * - Sizes are exactly Integer.BYTES, Double.BYTES, 1, and 4 + n for blobs
 *   and strings.
 * - Nothing may cross the end of the page: IllegalArgumentException, and the
 *   page is left untouched.
 * - Page(byte[]) shares the caller's array; Page(int) owns its own.
 * - Concurrent use is safe.
 */
class PageTest {

  private static final int SIZE = 64;

  /**
   * Ints round-trip, including the extreme values.
   */
  @Test
  void int_roundTrips() {
    Page p = new Page(SIZE);
    p.setInt(0, 42);
    p.setInt(4, -1);
    p.setInt(8, Integer.MAX_VALUE);
    p.setInt(12, Integer.MIN_VALUE);
    assertEquals(42, p.getInt(0));
    assertEquals(-1, p.getInt(4));
    assertEquals(Integer.MAX_VALUE, p.getInt(8));
    assertEquals(Integer.MIN_VALUE, p.getInt(12));
  }

  /**
   * Doubles round-trip bit-for-bit, including the special values.
   */
  @Test
  void double_roundTrips() {
    Page p = new Page(SIZE);
    p.setDouble(0, 3.25);
    p.setDouble(8, -0.0);
    p.setDouble(16, Double.NaN);
    p.setDouble(24, Double.POSITIVE_INFINITY);
    assertEquals(3.25, p.getDouble(0));
    assertEquals(Double.doubleToLongBits(-0.0), Double.doubleToLongBits(p.getDouble(8)));
    assertTrue(Double.isNaN(p.getDouble(16)));
    assertEquals(Double.POSITIVE_INFINITY, p.getDouble(24));
  }

  /**
   * Booleans round-trip and occupy a single byte: two of them at adjacent
   * offsets don't clobber each other.
   */
  @Test
  void boolean_roundTripsInOneByte() {
    Page p = new Page(SIZE);
    p.setBoolean(0, true);
    p.setBoolean(1, false);
    p.setBoolean(2, true);
    assertTrue(p.getBoolean(0));
    assertFalse(p.getBoolean(1));
    assertTrue(p.getBoolean(2));
  }

  /**
   * Blobs round-trip, including the empty blob.
   */
  @Test
  void bytes_roundTrips() {
    Page p = new Page(SIZE);
    byte[] data = {1, 2, 3, (byte) 0xFF};
    p.setBytes(0, data);
    p.setBytes(10, new byte[0]);
    assertArrayEquals(data, p.getBytes(0));
    assertArrayEquals(new byte[0], p.getBytes(10));
  }

  /**
   * getBytes returns a copy: changing it must not change the page.
   */
  @Test
  void getBytes_returnsCopy() {
    Page p = new Page(SIZE);
    p.setBytes(0, new byte[] {7, 8, 9});
    byte[] out = p.getBytes(0);
    out[0] = 99;
    assertArrayEquals(new byte[] {7, 8, 9}, p.getBytes(0));
  }

  /**
   * setBytes copies the caller's array into the page: the caller still owns
   * the array, so changing it afterward must not change the page.
   */
  @Test
  void setBytes_copiesCallersArray() {
    Page p = new Page(SIZE);
    byte[] mine = {7, 8, 9};
    p.setBytes(0, mine);
    mine[0] = 99;
    mine[2] = 99;
    assertArrayEquals(new byte[] {7, 8, 9}, p.getBytes(0));
  }

  /**
   * setBytes only reads the caller's array, never writes to it (general
   * policy: don't modify input parameters) -- whether the call succeeds or
   * is rejected for not fitting.
   */
  @Test
  void setBytes_leavesCallersArrayUnmodified() {
    Page p = new Page(SIZE);
    byte[] mine = {1, 2, 3, 4, 5};
    p.setBytes(10, mine);
    assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, mine);

    byte[] tooBig = new byte[SIZE];
    java.util.Arrays.fill(tooBig, (byte) 42);
    assertThrows(IllegalArgumentException.class, () -> p.setBytes(0, tooBig));
    for (byte b : tooBig) {
      assertEquals(42, b);
    }
  }

  /**
   * Strings round-trip, including the empty string, and use exactly
   * maxLength(s.length()) bytes so the next value can start right after.
   */
  @Test
  void string_roundTripsAndUsesMaxLength() {
    Page p = new Page(SIZE);
    String s = "hello";
    p.setString(0, s);
    int next = PageBase.maxLength(s.length());
    assertEquals(Integer.BYTES + 5, next);
    p.setInt(next, 123);
    p.setString(next + Integer.BYTES, "");
    assertEquals("hello", p.getString(0));
    assertEquals(123, p.getInt(next));
    assertEquals("", p.getString(next + Integer.BYTES));
  }

  /**
   * A string is stored as the blob of its ASCII bytes, so the two accessors
   * agree with each other.
   */
  @Test
  void string_isStoredAsBlobOfCharsetBytes() {
    Page p = new Page(SIZE);
    p.setString(0, "abc");
    assertArrayEquals("abc".getBytes(PageBase.CHARSET), p.getBytes(0));
    p.setBytes(16, "xyz".getBytes(PageBase.CHARSET));
    assertEquals("xyz", p.getString(16));
  }

  /**
   * Values of different types can sit side by side; each reads back from its
   * own offset.
   */
  @Test
  void mixedTypes_coexist() {
    Page p = new Page(SIZE);
    p.setInt(0, 1);
    p.setDouble(4, 2.5);
    p.setBoolean(12, true);
    p.setString(13, "db");
    assertEquals(1, p.getInt(0));
    assertEquals(2.5, p.getDouble(4));
    assertTrue(p.getBoolean(12));
    assertEquals("db", p.getString(13));
  }

  /**
   * Fixed-length values may end exactly at the page boundary but not one
   * byte past it.
   */
  @Test
  void fixedLength_boundaryExact() {
    Page p = new Page(SIZE);
    p.setInt(SIZE - Integer.BYTES, 5);
    assertEquals(5, p.getInt(SIZE - Integer.BYTES));
    p.setDouble(SIZE - Double.BYTES, 5.0);
    assertEquals(5.0, p.getDouble(SIZE - Double.BYTES));
    p.setBoolean(SIZE - 1, true);
    assertTrue(p.getBoolean(SIZE - 1));

    assertThrows(IllegalArgumentException.class, () -> p.setInt(SIZE - Integer.BYTES + 1, 5));
    assertThrows(IllegalArgumentException.class, () -> p.getInt(SIZE - Integer.BYTES + 1));
    assertThrows(IllegalArgumentException.class, () -> p.setDouble(SIZE - Double.BYTES + 1, 5.0));
    assertThrows(IllegalArgumentException.class, () -> p.getDouble(SIZE - Double.BYTES + 1));
    assertThrows(IllegalArgumentException.class, () -> p.setBoolean(SIZE, true));
    assertThrows(IllegalArgumentException.class, () -> p.getBoolean(SIZE));
  }

  /**
   * A string that exactly fills the page is fine; one more character is not.
   */
  @Test
  void string_boundaryExact() {
    Page p = new Page(SIZE);
    String fits = "x".repeat(SIZE - Integer.BYTES);
    p.setString(0, fits);
    assertEquals(fits, p.getString(0));

    String tooBig = fits + "x";
    assertThrows(IllegalArgumentException.class, () -> p.setString(0, tooBig));
    assertThrows(IllegalArgumentException.class, () -> p.setString(1, fits));
    assertThrows(IllegalArgumentException.class, () -> p.setBytes(0, new byte[SIZE - Integer.BYTES + 1]));
  }

  /**
   * A value larger than the whole page is rejected even at offset 0.
   */
  @Test
  void valueLargerThanPage_throws() {
    Page p = new Page(SIZE);
    assertThrows(IllegalArgumentException.class, () -> p.setString(0, "x".repeat(SIZE * 2)));
    assertThrows(IllegalArgumentException.class, () -> p.setBytes(0, new byte[SIZE * 2]));
  }

  /**
   * Negative offsets are never valid.
   */
  @Test
  void negativeOffset_throws() {
    Page p = new Page(SIZE);
    assertThrows(IllegalArgumentException.class, () -> p.getInt(-1));
    assertThrows(IllegalArgumentException.class, () -> p.setInt(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> p.getDouble(-1));
    assertThrows(IllegalArgumentException.class, () -> p.setDouble(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> p.getBoolean(-1));
    assertThrows(IllegalArgumentException.class, () -> p.setBoolean(-1, true));
    assertThrows(IllegalArgumentException.class, () -> p.getBytes(-1));
    assertThrows(IllegalArgumentException.class, () -> p.setBytes(-1, new byte[0]));
    assertThrows(IllegalArgumentException.class, () -> p.getString(-1));
    assertThrows(IllegalArgumentException.class, () -> p.setString(-1, ""));
  }

  /**
   * Offsets far beyond the page are rejected cleanly.  With an offset near
   * Integer.MAX_VALUE, "offset + length" overflows int and wraps negative,
   * so a naive bounds check would let the call through; it must still be an
   * IllegalArgumentException, for every getter and setter.
   */
  @Test
  void hugeOffset_throwsInsteadOfOverflowing() {
    Page p = new Page(SIZE);
    for (int offset : new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE - 3,
                                 Integer.MAX_VALUE - 7, Integer.MAX_VALUE / 2, SIZE * 1000}) {
      assertThrows(IllegalArgumentException.class, () -> p.getInt(offset));
      assertThrows(IllegalArgumentException.class, () -> p.setInt(offset, 1));
      assertThrows(IllegalArgumentException.class, () -> p.getDouble(offset));
      assertThrows(IllegalArgumentException.class, () -> p.setDouble(offset, 1.0));
      assertThrows(IllegalArgumentException.class, () -> p.getBoolean(offset));
      assertThrows(IllegalArgumentException.class, () -> p.setBoolean(offset, true));
      assertThrows(IllegalArgumentException.class, () -> p.getBytes(offset));
      assertThrows(IllegalArgumentException.class, () -> p.setBytes(offset, new byte[10]));
      assertThrows(IllegalArgumentException.class, () -> p.setBytes(offset, new byte[0]));
      assertThrows(IllegalArgumentException.class, () -> p.getString(offset));
      assertThrows(IllegalArgumentException.class, () -> p.setString(offset, "abc"));
    }
    assertThrows(IllegalArgumentException.class, () -> p.getInt(Integer.MIN_VALUE));
    assertThrows(IllegalArgumentException.class, () -> p.setBytes(Integer.MIN_VALUE, new byte[1]));
    // None of that touched the page.
    for (int i = 0; i + Integer.BYTES <= SIZE; i += Integer.BYTES) {
      assertEquals(0, p.getInt(i));
    }
  }

  /**
   * A page smaller than an int is legal (block size is the client's choice):
   * booleans still work, and anything needing 4 or more bytes is rejected.
   */
  @Test
  void pageSmallerThanAnInt_onlyHoldsBooleans() {
    Page p = new Page(3);
    p.setBoolean(0, true);
    p.setBoolean(2, true);
    assertTrue(p.getBoolean(0));
    assertFalse(p.getBoolean(1));
    assertTrue(p.getBoolean(2));
    assertThrows(IllegalArgumentException.class, () -> p.setBoolean(3, true));
    assertThrows(IllegalArgumentException.class, () -> p.setInt(0, 1));
    assertThrows(IllegalArgumentException.class, () -> p.getInt(0));
    assertThrows(IllegalArgumentException.class, () -> p.setDouble(0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> p.setBytes(0, new byte[0]));
    assertThrows(IllegalArgumentException.class, () -> p.getBytes(0));
    assertThrows(IllegalArgumentException.class, () -> p.setString(0, ""));
    assertThrows(IllegalArgumentException.class, () -> p.getString(0));
  }

  /**
   * Null values are rejected with IllegalArgumentException.
   */
  @Test
  void nullValue_throws() {
    Page p = new Page(SIZE);
    assertThrows(IllegalArgumentException.class, () -> p.setBytes(0, null));
    assertThrows(IllegalArgumentException.class, () -> p.setString(0, null));
  }

  /**
   * A rejected write must not have modified anything: the check happens
   * before the first byte is written.
   */
  @Test
  void failedWrite_leavesPageUntouched() {
    Page p = new Page(SIZE);
    p.setInt(SIZE - 8, 0xCAFE);
    p.setInt(SIZE - 4, 0xBABE);
    // Would overwrite the length prefix and spill past the end.
    assertThrows(IllegalArgumentException.class, () -> p.setBytes(SIZE - 8, new byte[8]));
    assertThrows(IllegalArgumentException.class, () -> p.setString(SIZE - 8, "12345"));
    assertEquals(0xCAFE, p.getInt(SIZE - 8));
    assertEquals(0xBABE, p.getInt(SIZE - 4));
  }

  /**
   * Reading a blob from an offset that doesn't hold one must not trust the
   * garbage length prefix: huge or negative lengths throw instead of
   * overflowing or allocating gigabytes.
   */
  @Test
  void getBytes_withCorruptLengthPrefix_throws() {
    Page p = new Page(SIZE);
    p.setInt(0, Integer.MAX_VALUE);
    assertThrows(IllegalArgumentException.class, () -> p.getBytes(0));
    assertThrows(IllegalArgumentException.class, () -> p.getString(0));
    p.setInt(0, -5);
    assertThrows(IllegalArgumentException.class, () -> p.getBytes(0));
    p.setInt(0, SIZE); // claims more bytes than remain after the prefix
    assertThrows(IllegalArgumentException.class, () -> p.getBytes(0));
    // Reading the prefix itself past the end is also rejected.
    assertThrows(IllegalArgumentException.class, () -> p.getBytes(SIZE - 2));
  }

  /**
   * Page(byte[]) wraps the array: writes through the page appear in the array
   * and writes to the array appear through the page.
   */
  @Test
  void byteArrayConstructor_sharesArray() {
    byte[] raw = new byte[SIZE];
    Page p = new Page(raw);
    p.setInt(0, 0x01020304);
    assertArrayEquals(new byte[] {1, 2, 3, 4}, new byte[] {raw[0], raw[1], raw[2], raw[3]});

    raw[4] = 1;
    assertTrue(p.getBoolean(4));

    p.setString(8, "shared");
    assertEquals("shared", new Page(raw).getString(8));
  }

  /**
   * The wrapped array's length is the page size for overflow purposes.
   */
  @Test
  void byteArrayConstructor_boundsAreArrayLength() {
    Page p = new Page(new byte[10]);
    p.setString(0, "abcdef"); // 4 + 6 = 10
    assertThrows(IllegalArgumentException.class, () -> p.setString(0, "abcdefg"));
    assertThrows(IllegalArgumentException.class, () -> p.setInt(7, 0));
  }

  /**
   * Page(int) allocates fresh, zeroed memory that no one else holds.
   */
  @Test
  void blocksizeConstructor_startsZeroed() {
    Page p = new Page(SIZE);
    for (int i = 0; i + Integer.BYTES <= SIZE; i += Integer.BYTES) {
      assertEquals(0, p.getInt(i));
    }
  }

  /**
   * Invalid constructor arguments are rejected.
   */
  @Test
  void badConstructorArguments_throw() {
    assertThrows(IllegalArgumentException.class, () -> new Page(0));
    assertThrows(IllegalArgumentException.class, () -> new Page(-4096));
    assertThrows(IllegalArgumentException.class, () -> new Page((byte[]) null));
  }

  /**
   * Many threads hammering disjoint regions of one page, each repeatedly
   * writing and verifying its own slot, must not disturb each other.
   *
   * Note what this does NOT test: each thread only ever reads its own slot,
   * so it can't see a half-written value from another thread.  The test
   * below covers that.
   */
  @Test
  void concurrentDisjointSlots_dontInterfere() throws InterruptedException {
    final int threads = 8;
    final int slot = 8; // bytes per thread: enough for a 4-char string
    final Page p = new Page(threads * slot);
    final List<Throwable> failures = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final int offset = t * slot;
      final String mine = "t" + t + "xy";
      Thread w = new Thread(() -> {
        try {
          for (int i = 0; i < 20_000; i++) {
            p.setString(offset, mine);
            String back = p.getString(offset);
            if (!mine.equals(back)) {
              throw new AssertionError("thread " + mine + " read " + back);
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
  }

  /**
   * The case synchronization actually exists for: many threads writing
   * different values to the SAME offset.  A setString is two steps (length
   * prefix, then bytes) and the bytes themselves are a multi-byte copy, so
   * without mutual exclusion a reader could see a mix of two writers'
   * values.  Every value is a single repeated letter, so any mixture like
   * "aaaabbbb" is detectably invalid.  Every read must return one of the
   * values some thread writes, and nothing else.
   */
  @Test
  void concurrentSameOffset_neverTearsValues() throws InterruptedException {
    final int threads = 8;
    final int len = 24;
    final Page p = new Page(SIZE);
    final Set<String> valid = new HashSet<>();
    for (int t = 0; t < threads; t++) {
      valid.add(String.valueOf((char) ('a' + t)).repeat(len));
    }
    p.setString(0, valid.iterator().next()); // so the very first read is valid
    final List<Throwable> failures = new ArrayList<>();
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final String mine = String.valueOf((char) ('a' + t)).repeat(len);
      Thread w = new Thread(() -> {
        try {
          for (int i = 0; i < 20_000; i++) {
            p.setString(0, mine);
            String back = p.getString(0);
            if (!valid.contains(back)) {
              throw new AssertionError("torn value read: \"" + back + "\"");
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
  }
}
