package edu.yu.dbimpl.parse;

import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import edu.yu.dbimpl.query.Datum;
import edu.yu.dbimpl.query.Expression;
import edu.yu.dbimpl.query.Predicate;
import edu.yu.dbimpl.query.Term;

/**
 * Unit tests for Parser — the syntax-only recursive-descent parser.
 *
 * Design notes from ParserBase that drive these tests:
 * - Results-oriented API: each method parses one construct from the
 *   current position (not necessarily a whole statement).
 * - Success may leave leftover tokens; problems show up only if a later call fails.
 * - On BadSyntaxException, fail early — no recovery expected.
 * - Constructor must throw if the Lexer would fail extracting the first token.
 *
 * Tests are grouped: building blocks → query → insert → delete → update → misc.
 */
class ParserTest {

  // =========================================================================
  // Building blocks (field, keyword, constant, expression, term, predicate)
  // =========================================================================

  /**
   * atEOF reflects whether the current token is EOF.
   * Empty input → EOF immediately; a keyword still pending → not EOF.
   */
  @Test
  void atEOF_trueOnEmpty_falseWhenTokensRemain() {
    assertTrue(new Parser("").atEOF());
    assertFalse(new Parser("select").atEOF());
  }

  /**
   * field() consumes an ID token (lowercased by the lexer) and advances.
   * Two ids in a row should both succeed, then atEOF.
   */
  @Test
  void field_readsId_andAdvances() {
    Parser p = new Parser("Age Name");
    assertEquals("age", p.field());
    assertEquals("name", p.field());
    assertTrue(p.atEOF());
  }

  /** field() must reject keywords and constants — only IdTok is a Field. */
  @Test
  void field_rejectsNonId() {
    assertThrows(BadSyntaxException.class, () -> new Parser("select").field());   // keyword
    assertThrows(BadSyntaxException.class, () -> new Parser("18").field()); // constant
  }

  /** keyword() consumes a KEYWORD token and returns its (lowercased) value. */
  @Test
  void keyword_readsKeyword() {
    Parser p = new Parser("SELECT from");
    assertEquals("select", p.keyword());
    assertEquals("from", p.keyword());
    assertTrue(p.atEOF());
  }

  /** An identifier is not a keyword. */
  @Test
  void keyword_rejectsNonKeyword() {
    assertThrows(BadSyntaxException.class, () -> new Parser("age").keyword());  // field
  }

  /**
   * constant() maps each literal token kind onto a Datum with the right Java/SQL type.
   * Mixed-case boolean text is fine (lexer lowercases before we parse).
   */
  @Test
  void constant_allLiteralKinds() {
    assertEquals(18, new Parser("18").constant().asInt());
    assertEquals(3.5, new Parser("3.5").constant().asDouble());
    assertTrue(new Parser("true").constant().asBoolean());
    assertFalse(new Parser("FALSE").constant().asBoolean());
    assertEquals("Hi There", new Parser("'Hi There'").constant().asString());
    assertEquals(Types.VARCHAR, new Parser("'x'").constant().getSQLType());
  }

  /** Ids and keywords are not DatumBase literals. */
  @Test
  void constant_rejectsNonLiteral() {
    assertThrows(BadSyntaxException.class, () -> new Parser("age").constant());   // field
    assertThrows(BadSyntaxException.class, () -> new Parser("select").constant());   // keyword
  }

  /**
   * Expression ::= Field | DatumBase.
   * ID → field-name Expression; literal → constant Expression.
   */
  @Test
  void expression_fieldOrConstant() {
    Expression fieldExpr = new Parser("Age").expression();
    assertTrue(fieldExpr.isFieldName());  
    assertEquals("age", fieldExpr.asFieldName());

    Expression constExpr = new Parser("42").expression();
    assertFalse(constExpr.isFieldName());
    assertEquals(new Datum(42), constExpr.asConstant());
  }

  /**
   * Term ::= Expression ComparisonOp Expression.
   * Covers '=', '>', '<' and both field/constant sides.
   */
  @Test
  void term_parsesComparison() {
    Term t = new Parser("Age = 18").term();
    assertEquals(new Term(new Expression("age"), '=', new Expression(new Datum(18))), t);

    Term gt = new Parser("x > y").term();
    assertEquals(new Term(new Expression("x"), '>', new Expression("y")), gt);

    Term lt = new Parser("a < 'b'").term();
    assertEquals(new Term(new Expression("a"), '<', new Expression(new Datum("b"))), lt);
  }

  /** Two expressions with no comparison operator between them is not a Term. */
  @Test
  void term_requiresOperator() {
    assertThrows(BadSyntaxException.class, () -> new Parser("age 18").term());
  }

  /**
   * Predicate ::= Term ("AND" Predicate)?
   * Implemented as one or more terms joined by AND (conjunction only).
   */
  @Test
  void predicate_singleTerm_andAndChain() {
    Predicate one = new Parser("Age = 18").predicate();  // single term
    assertEquals(1, one.terms().size());  
    assertEquals(
        new Term(new Expression("age"), '=', new Expression(new Datum(18))),
        one.terms().get(0));  

    Predicate many = new Parser("Age = 18 AND Name = 'Bob' AND Active = true").predicate();
    assertEquals(3, many.terms().size());
    assertEquals(
        new Term(new Expression("name"), '=', new Expression(new Datum("Bob"))),
        many.terms().get(1));
  }

  /** "AND" is case-insensitive because the lexer lowercases keywords. */
  @Test
  void mixedCase_and_keywordInPredicate() {
    Predicate p = new Parser("Age = 1 AnD Name = 'x'").predicate();
    assertEquals(2, p.terms().size());
  }

  // =========================================================================
  // query()
  // =========================================================================

  /**
   * Full SELECT with WHERE: fields, tables, single-term predicate, and EOF after parse.
   */
  @Test
  void query_basicWithWhere() {
    Parser p = new Parser(
        "SELECT Name, Age FROM Students WHERE Age = 18");
    ParseQueryBase q = p.query();

    assertEquals(List.of("name", "age"), q.fields());
    assertEquals(List.of("students"), q.tables());
    assertEquals(1, q.predicate().terms().size());
    assertTrue(p.atEOF());
  }

  /**
   * WHERE is optional. No WHERE → empty Predicate (truthy / match-all), not null.
   * ParseQuery rejects a null predicate, so the parser must pass an empty one.
   */
  @Test
  void query_withoutWhere_emptyPredicate() {
    ParseQueryBase q = new Parser("select a from t").query();
    assertEquals(List.of("a"), q.fields());
    assertEquals(List.of("t"), q.tables());
    assertTrue(q.predicate().terms().isEmpty());
  }

  /** Comma-separated select lists, table lists, and AND-chains in WHERE. */
  @Test
  void query_multipleTables_andAndPredicate() {
    ParseQueryBase q = new Parser(
        "SELECT a, b FROM t1, t2 WHERE a = 1 AND b = 2").query();
    assertEquals(List.of("a", "b"), q.fields());
    assertEquals(List.of("t1", "t2"), q.tables());
    assertEquals(2, q.predicate().terms().size());
  }

  /** Keywords may be mixed-case; stored ids/keywords are still lowercased. */
  @Test
  void query_mixedCase() {
    ParseQueryBase q = new Parser("SeLeCt Name FrOm Students").query();
    assertEquals(List.of("name"), q.fields());
    assertEquals(List.of("students"), q.tables());
  }

  /**
   * Results-oriented API: after a successful query(), leftover tokens are OK
   * as long as they were successfully lexed. Here "insert" remains and can be
   * read by a later keyword() call.
   *
   * Note: leftover characters that are not valid tokens would fail earlier,
   * when the lexer tries to advance past the last consumed token of the query.
   */
  @Test
  void query_allowsTrailingTokens_perResultsOrientedApi() {
    Parser p = new Parser("SELECT a FROM t insert");
    ParseQueryBase q = p.query();
    assertEquals(List.of("a"), q.fields());
    assertFalse(p.atEOF());
    assertEquals("insert", p.keyword());
  }

  /** SELECT without FROM is not a valid Query. */
  @Test
  void query_missingFrom_throws() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("SELECT a WHERE a = 1").query());
  }

  /** SELECT must be followed by at least one field before FROM. */
  @Test
  void query_missingSelectList_throws() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("SELECT FROM t").query());
  }

  /**
   * Calling the wrong top-level method fails immediately:
   * a SELECT statement does not start with INSERT.
   */
  @Test
  void query_wrongMethod_throws() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("SELECT a FROM t").insert());
  }

  // =========================================================================
  // insert()
  // =========================================================================

  /**
   * Full INSERT: table, parenthesized field list, VALUES, parenthesized constants.
   * Values keep their literal types/case (string case preserved).
   */
  @Test
  void insert_fullStatement() {
    Parser p = new Parser(
        "INSERT INTO Students (Name, Age) VALUES ('Ann', 20)");
    ParseInsertBase ins = p.insert();

    assertEquals("students", ins.tableName());
    assertEquals(List.of("name", "age"), ins.fields());
    assertEquals(2, ins.values().size());
    assertEquals("Ann", ins.values().get(0).asString());
    assertEquals(20, ins.values().get(1).asInt());
    assertTrue(p.atEOF());
  }

  /** VALUES list may contain int, double, boolean, and string constants. */
  @Test
  void insert_allConstantTypes() {
    ParseInsertBase ins = new Parser(
        "INSERT INTO t (a,b,c,d) VALUES (1, 2.5, true, 'Hi')").insert();
    assertEquals(1, ins.values().get(0).asInt());
    assertEquals(2.5, ins.values().get(1).asDouble());
    assertTrue(ins.values().get(2).asBoolean());
    assertEquals("Hi", ins.values().get(3).asString());
  }

  /** Truncated INSERT (no VALUES clause) must throw. */
  @Test
  void insert_missingValues_throws() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("INSERT INTO t (a)").insert());
  }

  /** Parentheses around field/value lists are required by the EBNF. */
  @Test
  void insert_missingParens_throws() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("INSERT INTO t a VALUES 1").insert());
  }

  // =========================================================================
  // delete()
  // =========================================================================

  /**
   * DELETE with WHERE builds a predicate; without WHERE uses an empty Predicate.
   */
  @Test
  void delete_withAndWithoutWhere() {
    ParseDeleteBase with = new Parser(
        "DELETE FROM Students WHERE Age > 21").delete();
    assertEquals("students", with.tableName());
    assertEquals(1, with.predicate().terms().size());
    assertEquals(
        new Term(new Expression("age"), '>', new Expression(new Datum(21))),
        with.predicate().terms().get(0));

    ParseDeleteBase all = new Parser("delete from students").delete();
    assertEquals("students", all.tableName());
    assertTrue(all.predicate().terms().isEmpty());
  }

  /** "DELETE Students" (missing FROM) is invalid. */
  @Test
  void delete_missingFrom_throws() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("DELETE Students").delete());
  }

  // =========================================================================
  // update()
  // =========================================================================

  /**
   * UPDATE ... SET field = constant [WHERE ...].
   * The '=' in SET is fixed syntax (not stored); RHS becomes newValue().
   */
  @Test
  void update_withConstantRhs_andWhere() {
    ParseUpdateBase u = new Parser(
        "UPDATE Students SET Age = 19 WHERE Name = 'Ann'").update();
    assertEquals("students", u.tableName());
    assertEquals("age", u.targetField());
    assertEquals(new Datum(19), u.newValue().asConstant());
    assertEquals(1, u.predicate().terms().size());
  }

  /**
   * RHS of SET may be a field Expression (not only a constant).
   * WHERE is optional → empty predicate.
   */
  @Test
  void update_withFieldRhs_noWhere() {
    ParseUpdateBase u = new Parser("update t set a = b").update();
    assertEquals("t", u.tableName());
    assertEquals("a", u.targetField());
    assertTrue(u.newValue().isFieldName());
    assertEquals("b", u.newValue().asFieldName());
    assertTrue(u.predicate().terms().isEmpty());
  }

  /**
   * SET requires '=' specifically. '>' is a valid comparison op for Terms,
   * but not for the UPDATE assignment.
   */
  @Test
  void update_requiresEqualsNotOtherOp() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("UPDATE t SET a > 1").update());
  }

  /** Missing SET keyword is a syntax error. */
  @Test
  void update_missingSet_throws() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("UPDATE t Age = 1").update());
  }

  // =========================================================================
  // createTable / constructor / misc
  // =========================================================================

  /**
   * ParseCreateTable is deferred this milestone (needs Schema), but ParserBase
   * still declares createTable(). Our implementation throws BadSyntaxException.
   */
  @Test
  void createTable_throwsUnsupported() {
    assertThrows(BadSyntaxException.class,
        () -> new Parser("create table t (a int)").createTable());
  }

  /**
   * Parser constructor must throw if Lexer would fail on the first token
   * (unterminated string, illegal character, etc.).
   */
  @Test
  void constructor_propagatesLexerFailureOnFirstToken() {
    assertThrows(BadSyntaxException.class, () -> new Parser("'unterminated"));
    assertThrows(BadSyntaxException.class, () -> new Parser("@bad"));
  }

  /**
   * Comments/whitespace are stripped by the lexer, so they must not affect parsing.
   */
  @Test
  void commentsAndWhitespace_doNotAffectParsing() {
    ParseQueryBase q = new Parser(
        "SELECT -- cols\n name FROM -- tbl\n students").query();
    assertEquals(List.of("name"), q.fields());
    assertEquals(List.of("students"), q.tables());
  }

  /**
   * Demonstrates the results-oriented API on sub-clauses:
   * client can call term(), then keyword(), then term() on the same Parser
   * and walk a predicate piece by piece from the current position.
   */
  @Test
  void sequentialSubclauseParsing_fromSameInput() {
    Parser p = new Parser("age = 18 AND name = 'x'");
    Term first = p.term();
    assertEquals("and", p.keyword());
    Term second = p.term();
    assertEquals(
        new Term(new Expression("age"), '=', new Expression(new Datum(18))),
        first);
    assertEquals(
        new Term(new Expression("name"), '=', new Expression(new Datum("x"))),
        second);
    assertTrue(p.atEOF());
  }
}
