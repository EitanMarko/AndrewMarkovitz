package edu.yu.dbimpl.file;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for BlockId, the value-class implementation of BlockIdBase.
 *
 * What we care about here (from BlockIdBase Javadoc):
 * - The constructor rejects a null/blank file name and a negative block number.
 * - fileName() and number() return what the constructor was given.
 * - equals/hashCode compare by value, so BlockIds work as hash map keys.
 */
class BlockIdTest {

  /**
   * The getters return exactly the constructor arguments.
   */
  @Test
  void getters_returnConstructorArguments() {
    BlockIdBase blk = new BlockId("students.tbl", 7);
    assertEquals("students.tbl", blk.fileName());
    assertEquals(7, blk.number());
  }

  /**
   * Block 0 is the first block of a file, so 0 is a legal block number.
   */
  @Test
  void blockNumberZero_isAllowed() {
    assertEquals(0, new BlockId("f", 0).number());
  }

  /**
   * The largest int is still a non-negative block number.
   */
  @Test
  void blockNumberMaxInt_isAllowed() {
    assertEquals(Integer.MAX_VALUE, new BlockId("f", Integer.MAX_VALUE).number());
  }

  /**
   * A negative block number can't address anything.
   */
  @Test
  void negativeBlockNumber_throws() {
    assertThrows(IllegalArgumentException.class, () -> new BlockId("f", -1));
    assertThrows(IllegalArgumentException.class, () -> new BlockId("f", Integer.MIN_VALUE));
  }

  /**
   * Null must surface as IllegalArgumentException, not NullPointerException.
   */
  @Test
  void nullFileName_throws() {
    assertThrows(IllegalArgumentException.class, () -> new BlockId(null, 0));
  }

  /**
   * "Trimmed length greater than 0": empty and whitespace-only names fail.
   */
  @Test
  void emptyOrBlankFileName_throws() {
    assertThrows(IllegalArgumentException.class, () -> new BlockId("", 0));
    assertThrows(IllegalArgumentException.class, () -> new BlockId("   ", 0));
    assertThrows(IllegalArgumentException.class, () -> new BlockId("\t\n", 0));
  }

  /**
   * Surrounding whitespace is legal as long as something remains after
   * trimming, and the name is stored exactly as supplied.
   */
  @Test
  void fileNameWithSurroundingWhitespace_isStoredAsGiven() {
    assertEquals(" f ", new BlockId(" f ", 0).fileName());
  }

  /**
   * Two separately constructed BlockIds naming the same block are equal and
   * share a hash code.
   */
  @Test
  void equalsAndHashCode_areValueBased() {
    BlockId a = new BlockId("f", 3);
    BlockId b = new BlockId("f", 3);
    assertEquals(a, b);
    assertEquals(b, a);
    assertEquals(a, a);
    assertEquals(a.hashCode(), b.hashCode());
  }

  /**
   * A different file, a different block number, null and unrelated types are
   * all unequal.
   */
  @Test
  void equals_distinguishesDifferentBlocks() {
    BlockId a = new BlockId("f", 3);
    assertNotEquals(a, new BlockId("f", 4));
    assertNotEquals(a, new BlockId("g", 3));
    assertNotEquals(a, null);
    assertNotEquals(a, "f");
  }

  /**
   * The use case that matters to the buffer manager and lock table: looking a
   * block up with a different-but-equal BlockId instance must succeed.
   */
  @Test
  void worksAsHashMapKey() {
    Map<BlockIdBase, String> map = new HashMap<>();
    map.put(new BlockId("f", 1), "one");
    assertEquals("one", map.get(new BlockId("f", 1)));
    assertFalse(map.containsKey(new BlockId("f", 2)));

    Set<BlockIdBase> set = new HashSet<>();
    set.add(new BlockId("f", 1));
    set.add(new BlockId("f", 1));
    assertEquals(1, set.size());
  }

  /**
   * toString names both the file and the block, for readable log output.
   */
  @Test
  void toString_mentionsFileAndBlock() {
    String s = new BlockId("students.tbl", 7).toString();
    assertTrue(s.contains("students.tbl"));
    assertTrue(s.contains("7"));
  }
}
