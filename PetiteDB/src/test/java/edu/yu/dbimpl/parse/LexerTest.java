package edu.yu.dbimpl.parse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import edu.yu.dbimpl.parse.LexerBase.Token;
import edu.yu.dbimpl.parse.LexerBase.TokenType;

/**
 * Unit tests for Lexer.
 *
 * The lexer turns a SQL string into a stream of Tokens per the PetiteDB EBNF /
 * LexerBase contract. These tests check:
 * - Constructor eagerly consumes the first token; firstTokenRetrieved() is stable.
 * - Keywords/ids are lowercased; string constants keep their case.
 * - Whitespace and "--" comments are discarded.
 * - Token classification (keyword vs id vs boolean vs number vs op/delimiter).
 * - Illegal input throws BadSyntaxException (fail-early, no recovery).
 */
class LexerTest {

  /**
   * Helper: drain the entire token stream including the constructor's first token
   * and a terminating EOF. Makes "expected full stream" assertions readable.
   */
  private static List<Token> allTokens(String sql) {
    Lexer lexer = new Lexer(sql);
    List<Token> tokens = new ArrayList<>();
    tokens.add(lexer.firstTokenRetrieved());
    Token t;
    do {
      t = lexer.nextToken();
      tokens.add(t);
    } while (t.type != TokenType.EOF);
    return tokens;
  }

  /** Shorthand for building an expected Token in assertions. */
  private static Token tok(TokenType type, String value) {
    return new Token(type, value);
  }

  /**
   * LexerBase requires: by the time the constructor finishes, one token has already
   * been consumed. firstTokenRetrieved() always returns that frozen token —
   * calling nextToken() must not change what firstTokenRetrieved returns.
   */
  @Test
  void constructor_extractsFirstToken_andFirstTokenRetrievedIsStable() {
    Lexer lexer = new Lexer("SELECT Name FROM T");
    Token first = lexer.firstTokenRetrieved();
    assertEquals(tok(TokenType.KEYWORD, "select"), first);

    assertEquals(tok(TokenType.ID, "name"), lexer.nextToken());
    // Re-invoking firstTokenRetrieved must NOT reset the scan pointer or change the value.
    assertEquals(first, lexer.firstTokenRetrieved());
    assertEquals(first, lexer.firstTokenRetrieved());
  }

  /**
   * Empty input → first token is EOF, and further nextToken() calls keep returning EOF
   * (LexerBase: once consumed, EOF is sticky).
   */
  @Test
  void emptyInput_isEof() {
    Lexer lexer = new Lexer("");
    assertEquals(LexerBase.EOF_Token, lexer.firstTokenRetrieved());
    assertEquals(LexerBase.EOF_Token, lexer.nextToken());
    assertEquals(LexerBase.EOF_Token, lexer.nextToken());
  }

  /**
   * Null is not a valid SQL string. LexerBase says the constructor throws
   * BadSyntaxException if a problem occurs reading the first token; we treat
   * null the same way (fail early) rather than inventing "null means empty".
   */
  @Test
  void nullInput_throwsBadSyntaxException() {
    assertThrows(BadSyntaxException.class, () -> new Lexer(null));
  }


  /* 
  // If it turns out null input is acceptable, use this test instead of nullInput_throwsBadSyntaxException()
  @Test
  void nullInput_treatedLikeEmpty() {
    Lexer lexer = new Lexer(null);
    assertEquals(LexerBase.EOF_Token, lexer.firstTokenRetrieved());
  }
  */

  /**
   * Mixed-case input is allowed, but keyword and id token values must be lowercased
   * (assignment note + LexerBase). So "SELECT Name" → keyword("select"), id("name").
   */
  @Test
  void keywords_andIds_areLowercased() {
    List<Token> tokens = allTokens("SELECT Name FROM Students");
    assertEquals(List.of(
        tok(TokenType.KEYWORD, "select"),
        tok(TokenType.ID, "name"),
        tok(TokenType.KEYWORD, "from"),
        tok(TokenType.ID, "students"),
        LexerBase.EOF_Token
    ), tokens);
  }

  /**
   * StrToken keeps original case and may contain spaces.
   * EBNF: "'" [a-zA-Z0-9_ ]+ "'"
   */
  @Test
  void stringConstant_preservesCase_andAllowsSpaces() {
    List<Token> tokens = allTokens("'Hello World'");
    assertEquals(tok(TokenType.STRING_CONSTANT, "Hello World"), tokens.get(0));
  }

  /**
   * true/false are BOOLEAN_CONSTANT tokens, not KEYWORD and not ID —
   * even when written in mixed/upper case (then lowercased in the token value).
   */
  @Test
  void booleans_areBooleanConstants_notKeywordsOrIds() {
    assertEquals(tok(TokenType.BOOLEAN_CONSTANT, "true"),
        new Lexer("TRUE").firstTokenRetrieved());
    assertEquals(tok(TokenType.BOOLEAN_CONSTANT, "false"),
        new Lexer("False").firstTokenRetrieved());
  }

  /**
   * Number forms from the EBNF:
   * - IntToken: digits only (e.g. 42) — NOT a double
   * - DoubleToken: must include a decimal point, e.g. 3.14, 3., or .5
   * Token.value stores the number as a String (LexerBase).
   */
  @Test
  void numbers_intAndDoubleForms() {
    assertEquals(tok(TokenType.INT_CONSTANT, "42"),
        new Lexer("42").firstTokenRetrieved());
    assertEquals(tok(TokenType.DOUBLE_CONSTANT, "3.14"),
        new Lexer("3.14").firstTokenRetrieved());
    assertEquals(tok(TokenType.DOUBLE_CONSTANT, "3."),
        new Lexer("3.").firstTokenRetrieved());
    assertEquals(tok(TokenType.DOUBLE_CONSTANT, ".5"),
        new Lexer(".5").firstTokenRetrieved());
  }

  /**
   * Comparison operators = > < and delimiters ( ) ,
   * are single-character tokens of the expected types.
   */
  @Test
  void comparisonOps_andDelimiters() {
    List<Token> tokens = allTokens("= > < ( ) ,");
    assertEquals(List.of(
        tok(TokenType.COMPARISON_OP, "="),
        tok(TokenType.COMPARISON_OP, ">"),
        tok(TokenType.COMPARISON_OP, "<"),
        tok(TokenType.DELIMITER, "("),
        tok(TokenType.DELIMITER, ")"),
        tok(TokenType.DELIMITER, ","),
        LexerBase.EOF_Token
    ), tokens);
  }

  /**
   * Whitespace (space/tab/CR/LF) and single-line "--" comments are consumed
   * and discarded — they must not appear in the token stream.
   */
  @Test
  void whitespace_andComments_areSkipped() {
    List<Token> tokens = allTokens(
        "select -- this is a comment\n  age\tfrom\r\nstudents");
    assertEquals(List.of(
        tok(TokenType.KEYWORD, "select"),
        tok(TokenType.ID, "age"),
        tok(TokenType.KEYWORD, "from"),
        tok(TokenType.ID, "students"),
        LexerBase.EOF_Token
    ), tokens);
  }

  /**
   * IdTok: starts with a letter, then letters/digits/underscores.
   * Value is lowercased.
   */
  @Test
  void identifiers_allowDigitsAndUnderscores() {
    assertEquals(tok(TokenType.ID, "col_1"),
        new Lexer("Col_1").firstTokenRetrieved());
  }

  /**
   * Every string in LexerBase.keywords, regardless of input case,
   * must tokenize as KEYWORD with the lowercased value.
   */
  @Test
  void allKeywords_classifiedAsKeywords() {
    for (String kw : LexerBase.keywords) {
      Token t = new Lexer(kw.toUpperCase()).firstTokenRetrieved();
      assertEquals(TokenType.KEYWORD, t.type, kw);
      assertEquals(kw, t.value);
    }
  }

  /**
   * "double" and "boolean" must also tokenize as KEYWORD, even though
   * LexerBase.keywords omits them: the grammar needs all four type names for
   * "create table".  Deriving expectations from LexerBase.keywords alone (as
   * the test above does) cannot catch a word missing from that list.
   */
  @Test
  void typeNamesMissingFromLexerBase_areStillKeywords() {
    for (String kw : List.of("double", "boolean", "DOUBLE", "Boolean")) {
      Token t = new Lexer(kw).firstTokenRetrieved();
      assertEquals(TokenType.KEYWORD, t.type, kw);
      assertEquals(kw.toLowerCase(), t.value, kw);
    }
  }

  /** "boolean" is a type keyword, but true/false remain BOOLEAN_CONSTANTs. */
  @Test
  void booleanKeyword_doesNotCaptureBooleanConstants() {
    assertEquals(tok(TokenType.KEYWORD, "boolean"),
        new Lexer("boolean").firstTokenRetrieved());
    assertEquals(tok(TokenType.BOOLEAN_CONSTANT, "true"),
        new Lexer("true").firstTokenRetrieved());
    assertEquals(tok(TokenType.BOOLEAN_CONSTANT, "false"),
        new Lexer("FALSE").firstTokenRetrieved());
  }

  /** EBNF StrToken uses "+" (one or more) inside quotes — empty '' is illegal. */
  @Test
  void emptyStringConstant_throws() {
    assertThrows(BadSyntaxException.class, () -> new Lexer("''"));
  }

  /** Characters outside [a-zA-Z0-9_ ] inside a string constant are illegal (e.g. '!'). */
  @Test
  void illegalCharInString_throws() {
    assertThrows(BadSyntaxException.class, () -> new Lexer("'hi!'"));
  }

  /** Missing closing quote must fail when reading the first token. */
  @Test
  void unterminatedString_throws() {
    assertThrows(BadSyntaxException.class, () -> new Lexer("'hello"));
  }

  /**
   * Characters that aren't part of any token production throw BadSyntaxException.
   *
   * Note the second case: constructor only reads the first token ("select"), so
   * new Lexer("select #") succeeds; the failure happens on nextToken() when '#'
   * is scanned.
   */
  @Test
  void unexpectedCharacter_throws() {
    assertThrows(BadSyntaxException.class, () -> new Lexer("@"));

    Lexer lexer = new Lexer("select #");
    assertEquals(TokenType.KEYWORD, lexer.firstTokenRetrieved().type);
    assertThrows(BadSyntaxException.class, lexer::nextToken);
  }

  /**
   * End-to-end token stream for a realistic INSERT statement —
   * keywords, ids, delimiters, int constant, and string constant in the expected order.
   */
  @Test
  void insertStatement_tokenStream() {
    List<Token> tokens = allTokens(
        "INSERT INTO T (A, B) VALUES (1, 'x')");
    assertEquals(List.of(
        tok(TokenType.KEYWORD, "insert"),
        tok(TokenType.KEYWORD, "into"),
        tok(TokenType.ID, "t"),
        tok(TokenType.DELIMITER, "("),
        tok(TokenType.ID, "a"),
        tok(TokenType.DELIMITER, ","),
        tok(TokenType.ID, "b"),
        tok(TokenType.DELIMITER, ")"),
        tok(TokenType.KEYWORD, "values"),
        tok(TokenType.DELIMITER, "("),
        tok(TokenType.INT_CONSTANT, "1"),
        tok(TokenType.DELIMITER, ","),
        tok(TokenType.STRING_CONSTANT, "x"),
        tok(TokenType.DELIMITER, ")"),
        LexerBase.EOF_Token
    ), tokens);
  }
}
