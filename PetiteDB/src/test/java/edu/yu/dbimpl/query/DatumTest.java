package edu.yu.dbimpl.query;

import java.sql.Types;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Datum, the value-class implementation of DatumBase.
 *
 * What we care about here (from DatumBase Javadoc / course requirements):
 * - Each constructor wraps exactly one Java type and reports the matching SQL type.
 * - asX() returns the wrapped value or throws ClassCastException.
 * - Strings and byte[] are convertible to each other; other types are not.
 * - equals/hashCode compare wrapped values with no type coercion (e.g. 7 ≠ 7.0).
 * - compareTo only compares equal Java types; otherwise ClassCastException.
 */
class DatumTest {

  /**
   * INTEGER datum: asInt works, SQL type is INTEGER, and asDouble widens via
   * Integer.doubleValue() (DatumBase: asX() follows JDK Y.xValue()).  The
   * getters with no corresponding Integer.xValue() must fail.
   */
  @Test
  void integerDatum_roundTripsAndType() {
    Datum d = new Datum(42);
    assertEquals(42, d.asInt());
    assertEquals(42.0, d.asDouble());
    assertEquals(Types.INTEGER, d.getSQLType());
    assertThrows(ClassCastException.class, d::asBoolean);
    assertThrows(ClassCastException.class, d::asString);
    assertThrows(ClassCastException.class, d::asBinaryArray);
  }

  /**
   * VARCHAR datum: asString works; asBinaryArray is allowed via String.getBytes().
   * asInt must still fail — conversion is only between String and byte[].
   */
  @Test
  void stringDatum_roundTripsAndBinaryConversion() {
    Datum d = new Datum("Hello");
    assertEquals("Hello", d.asString());
    assertArrayEquals("Hello".getBytes(), d.asBinaryArray());
    assertEquals(Types.VARCHAR, d.getSQLType());
    assertThrows(ClassCastException.class, d::asInt);
    assertThrows(ClassCastException.class, d::asDouble);
  }

  /** BOOLEAN datum: true/false round-trip and SQL type is BOOLEAN. */
  @Test
  void booleanDatum_roundTrips() {
    Datum t = new Datum(true);
    Datum f = new Datum(false);
    assertTrue(t.asBoolean());
    assertFalse(f.asBoolean());
    assertEquals(Types.BOOLEAN, t.getSQLType());

    assertThrows(ClassCastException.class, t::asDouble);
    assertThrows(ClassCastException.class, t::asString);
    assertThrows(ClassCastException.class, t::asBinaryArray);

    assertThrows(ClassCastException.class, f::asDouble);
    assertThrows(ClassCastException.class, f::asString);
    assertThrows(ClassCastException.class, f::asBinaryArray);
  }

  /**
   * DOUBLE datum: asDouble round-trip, SQL type is DOUBLE, and asInt narrows via
   * Double.intValue(), which truncates toward zero rather than rounding.
   */
  @Test
  void doubleDatum_roundTrips() {
    Datum d = new Datum(3.14);
    assertEquals(3.14, d.asDouble());
    assertEquals(Types.DOUBLE, d.getSQLType());
    assertEquals(3, d.asInt());
    assertEquals(-3, new Datum(-3.9).asInt());
    assertThrows(ClassCastException.class, d::asBoolean);
    assertThrows(ClassCastException.class, d::asString);
    assertThrows(ClassCastException.class, d::asBinaryArray);
  }

  /**
   * VARBINARY datum: asBinaryArray round-trip; asString is allowed via new String(bytes).
   * SQL type must be VARBINARY.
   */
  @Test
  void binaryDatum_roundTripsAndStringConversion() {
    byte[] bytes = "abc".getBytes();
    Datum d = new Datum(bytes);
    assertArrayEquals(bytes, d.asBinaryArray());
    assertEquals(new String(bytes), d.asString());
    assertEquals(Types.VARBINARY, d.getSQLType());
  }

  /**
   * Two separately constructed Datums with the same wrapped value must be equal.
   * Includes byte[] — important because default Object.equals on arrays is reference equality.
   */
  @Test
  void equals_sameValueSameType() {
    assertEquals(new Datum(7), new Datum(7));
    assertEquals(new Datum("x"), new Datum("x"));
    assertEquals(new Datum(true), new Datum(true));
    assertEquals(new Datum(1.5), new Datum(1.5));
    assertEquals(new Datum(new byte[] {1, 2}), new Datum(new byte[] {1, 2}));
  }

  /**
   * equals must NOT do implicit type conversion (DatumBase: "no implicit type conversion").
   * Also null / non-Datum arguments must return false (not throw).
   */
  @Test
  void equals_differentValuesOrTypes() {
    assertNotEquals(new Datum(7), new Datum(8));
    assertNotEquals(new Datum(7), new Datum(7.0)); // Integer vs Double — different types
    assertNotEquals(new Datum("7"), new Datum(7));
    assertNotEquals(new Datum(true), null);
    assertNotEquals(new Datum(1), "not a datum");
  }

  /**
   * hashCode must agree with equals: equal Datums → equal hash codes.
   * Required by course policy for any value class that may land in HashMap/HashSet.
   */
  @Test
  void hashCode_consistentWithEquals() {
    assertEquals(new Datum(9).hashCode(), new Datum(9).hashCode());
    assertEquals(
        new Datum(new byte[] {9, 8}).hashCode(),
        new Datum(new byte[] {9, 8}).hashCode());
  }

  /**
   * compareTo uses the wrapped object's natural order when types match.
   * Covers every supported constructor type.
   */
  @Test
  void compareTo_ordersSameTypes() {
    assertTrue(new Datum(1).compareTo(new Datum(2)) < 0);
    assertTrue(new Datum(2).compareTo(new Datum(1)) > 0);
    assertEquals(0, new Datum(2).compareTo(new Datum(2)));

    assertTrue(new Datum("a").compareTo(new Datum("b")) < 0);
    assertTrue(new Datum(1.0).compareTo(new Datum(2.0)) < 0);
    assertTrue(new Datum(false).compareTo(new Datum(true)) < 0);
    assertTrue(
        new Datum(new byte[] {1}).compareTo(new Datum(new byte[] {2})) < 0);
  }

  /**
   * compareTo must throw ClassCastException on type mismatch — even if values
   * "look" similar (1 vs 1.0). Same rule as equals: no coercion.
   */
  @Test
  void compareTo_rejectsTypeMismatch() {
    assertThrows(ClassCastException.class,
        () -> new Datum(1).compareTo(new Datum(1.0)));
    assertThrows(ClassCastException.class,
        () -> new Datum("a").compareTo(new Datum(1)));
  }

  /**
   * byte[] equality must use Arrays.equals (content), not == (identity).
   * Different array instances with the same contents must still be equal Datums.
   */
  @Test
  void equals_usesArraysEqualsForBinary() {
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 3};
    assertTrue(Arrays.equals(a, b));
    assertEquals(new Datum(a), new Datum(b));
  }
}
