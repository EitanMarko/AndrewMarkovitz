package edu.yu.dbimpl.parse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import edu.yu.dbimpl.query.Datum;
import edu.yu.dbimpl.query.DatumBase;
import edu.yu.dbimpl.query.Expression;
import edu.yu.dbimpl.query.Predicate;
import edu.yu.dbimpl.query.Term;

/**
 * Unit tests for the AST "value" classes:
 * ParseQuery, ParseInsert, ParseDelete, ParseUpdate.
 *
 * These classes do NOT parse SQL. They only store the pieces the parser already
 * extracted and expose them via getters. Tests focus on:
 * - Correct storage / return of constructor arguments (order preserved for lists).
 * - Validation (null/blank/empty → IllegalArgumentException).
 *
 * Per course guidance: assume the client does not mutate lists/predicates after
 * handing them to the AST, so we store references (no defensive copies).
 */
class ParseAstTest {

  // -------------------------------------------------------------------------
  // ParseQuery
  // -------------------------------------------------------------------------

  /**
   * Happy path: fields, tables, and predicate are stored and returned as given.
   * Field/table order matters (select-list / from-list order from the grammar).
   * We keep the same list/predicate references (no defensive copy).
   */
  @Test
  void parseQuery_storesFieldsTablesPredicate_inOrder() {
    List<String> fields = List.of("name", "age");
    List<String> tables = List.of("students");
    Predicate pred = new Predicate(new Term(
        new Expression("age"), '=', new Expression(new Datum(18))));
    ParseQuery q = new ParseQuery(fields, tables, pred);

    assertSame(fields, q.fields());
    assertSame(tables, q.tables());
    assertSame(pred, q.predicate());
  }

  /**
   * Base Javadoc: IllegalArgumentException if any input is syntactically invalid.
   * We treat null / empty field or table lists, and null predicate, as invalid.
   * (Empty predicate object is OK — it means "no WHERE" / match-all.)
   */
  @Test
  void parseQuery_rejectsInvalidArgs() {
    Predicate p = new Predicate();
    assertThrows(IllegalArgumentException.class,
        () -> new ParseQuery(null, List.of("t"), p));
    assertThrows(IllegalArgumentException.class,
        () -> new ParseQuery(List.of(), List.of("t"), p));
    assertThrows(IllegalArgumentException.class,
        () -> new ParseQuery(List.of("a"), null, p));
    assertThrows(IllegalArgumentException.class,
        () -> new ParseQuery(List.of("a"), List.of(), p));
    assertThrows(IllegalArgumentException.class,
        () -> new ParseQuery(List.of("a"), List.of("t"), null));
  }

  // -------------------------------------------------------------------------
  // ParseInsert
  // -------------------------------------------------------------------------

  /**
   * INSERT AST: table name, field list, and value list; ith value pairs with ith field.
   * Lists are stored by reference (no defensive copy).
   */
  @Test
  void parseInsert_storesAlignedFieldsAndValues() {
    List<String> fields = List.of("name", "age");
    List<DatumBase> values = List.of(new Datum("Ann"), new Datum(20));
    ParseInsert ins = new ParseInsert("students", fields, values);

    assertEquals("students", ins.tableName());
    assertSame(fields, ins.fields());
    assertSame(values, ins.values());
  }

  /**
   * Rejects null/blank table, null/empty lists, and mismatched fields/values sizes
   * (the ith-value ↔ ith-field contract cannot hold if lengths differ).
   */
  @Test
  void parseInsert_rejectsInvalidArgs() {
    List<String> fields = List.of("a");
    List<DatumBase> values = List.of(new Datum(1));

    assertThrows(IllegalArgumentException.class,
        () -> new ParseInsert(null, fields, values)); // null table name
    assertThrows(IllegalArgumentException.class,
        () -> new ParseInsert("  ", fields, values)); // blank table name
    assertThrows(IllegalArgumentException.class,
        () -> new ParseInsert("t", null, values)); // null fields list  
    assertThrows(IllegalArgumentException.class,
        () -> new ParseInsert("t", List.of(), values)); // empty fields list    
    assertThrows(IllegalArgumentException.class,
        () -> new ParseInsert("t", fields, null)); // null values list  
    assertThrows(IllegalArgumentException.class,
        () -> new ParseInsert("t", fields, List.of())); // empty values list  
    assertThrows(IllegalArgumentException.class,
        () -> new ParseInsert("t", List.of("a", "b"), values)); // mismatched fields/values sizes
  }

  // -------------------------------------------------------------------------
  // ParseDelete
  // -------------------------------------------------------------------------

  /**
   * DELETE only needs table name + predicate.
   * Empty Predicate means "delete all rows" (no WHERE clause).
   */
  @Test
  void parseDelete_storesTableAndPredicate() {
    Predicate pred = new Predicate();       // empty predicate means "delete all rows" (no WHERE clause)
    ParseDelete del = new ParseDelete("students", pred);    // students is the table name
    assertEquals("students", del.tableName());  
    assertSame(pred, del.predicate());   // empty predicate means "delete all rows" (no WHERE clause)
  }

  /** Null/blank table or null predicate are invalid. */
  @Test
  void parseDelete_rejectsInvalidArgs() {
    assertThrows(IllegalArgumentException.class,
        () -> new ParseDelete(null, new Predicate()));
    assertThrows(IllegalArgumentException.class,
        () -> new ParseDelete("", new Predicate()));
    assertThrows(IllegalArgumentException.class,
        () -> new ParseDelete("t", null));
  }

  // -------------------------------------------------------------------------
  // ParseUpdate
  // -------------------------------------------------------------------------

  /**
   * UPDATE stores table, target field (SET column), RHS Expression, and predicate.
   * Note: getter is targetField(), not fieldName().
   */
  @Test
  void parseUpdate_storesAllPieces() {
    Expression neu = new Expression(new Datum(19));   // new value for age field
    Predicate pred = new Predicate();
    ParseUpdate upd = new ParseUpdate("students", "age", neu, pred);

    assertEquals("students", upd.tableName());
    assertEquals("age", upd.targetField());
    assertEquals(neu, upd.newValue());
    assertSame(pred, upd.predicate());
  }

  /** Null/blank table or field, or null expression/predicate, must be rejected. */
  @Test
  void parseUpdate_rejectsInvalidArgs() {
    Expression neu = new Expression(new Datum(1));
    Predicate pred = new Predicate();

    assertThrows(IllegalArgumentException.class,
        () -> new ParseUpdate(null, "age", neu, pred)); // null table name
    assertThrows(IllegalArgumentException.class,
        () -> new ParseUpdate("t", null, neu, pred)); // null field name
    assertThrows(IllegalArgumentException.class,
        () -> new ParseUpdate("t", " ", neu, pred)); // blank field name    
    assertThrows(IllegalArgumentException.class,
        () -> new ParseUpdate("t", "age", null, pred)); // null expression  
    assertThrows(IllegalArgumentException.class,
        () -> new ParseUpdate("t", "age", neu, null)); // null predicate  
  }
}
