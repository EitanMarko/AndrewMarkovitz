package edu.yu.dbimpl.parse;

/** Defines the API for extracting tokens from an input string, and defines the
 * set of token categories that must be supported by a PetiteDB lexer.  The
 * lexemes correspond to a (very) small subsert of the SQL language.  This
 * language is formally defined via BNF (remind me to post it, and the
 * associated railroad diagram, if I forget to do so).
 *
 * Students MAY NOT modify this class in any way!.
 *
 * Design note: the lexer API is deliberately very unconstrained to allow for
 * maximum implementation freedom subject to the constraint of testability.
 * Implementors should consider adding "consume specific token type" APIs on
 * behalf of the parser.
 *
 * Design note: the parser and lexer should fail-early-and-fatally after
 * throwing a BadSyntaxException.  Do not try to recover from the error, let
 * the client fix it and try again.  The behavior of subsequent API invocations
 * on the input by this parser or lexer instance is undefined.
 *
 * @author Avraham Leff
 */

import java.util.*;

public abstract class LexerBase {

  /** Define an enum that specifies the set of valid token types.
   *
   * IMPORTANT: the PetiteDB grammar requires that all keyword and id token
   * matches be "lower-cased", so that an e.g., "SELECT X from Y" statement is
   * processed as "select x from y".  This implies that PetiteDB store all
   * keyword and id token input that's supplied by clients in "lower-case" as
   * well.
   */
  public enum TokenType {
    DELIMITER, COMPARISON_OP, EOF, ID, INT_CONSTANT, DOUBLE_CONSTANT, BOOLEAN_CONSTANT,
    KEYWORD, STRING_CONSTANT 
  };

  /** An immutable holder class: because immutable, clients get direct analysis
   * to encapsulated state.
   *
   * IMPORTANT: even if the token type doesn't correspond to a String (e.g., an
   * NUMERIC_CONSTANT), Token.value contains the String version of that number.
   * 
   *  Note to self: review immutability design if profiling shows performance
   *  problems as large number of Tokens are created during parsing.
   */
  public static class Token {
    /** Constructor
     *
     * @param TokenType type
     * @param String value
     */
    public Token(final TokenType type, final String value) {
      assert type != null: "type can't be null";
      assert value != null : "value can't be null";
      this.type = type;
      this.value = value;
    }

    @Override
    public String toString() {
      return "{type="+type.toString()+", value="+value.toString()+"}";
    }

    @Override
    public int hashCode() { return Objects.hash(type, value); }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      
      if (!(o instanceof Token)) {
        return false;
      }
      
      final Token that = (Token) o;
      return this.type == that.type && this.value.equals(that.value);
    }

    public final TokenType type;
    public final String value;
  } // static inner class

  /** Implementations MUST return THIS variable to inform clients that the
   * input has been consumed
   */
  public static Token EOF_Token =
    new Token(TokenType.EOF, String.valueOf(TokenType.EOF));

  /** Constructor: creates a new lexical analyzer to be used for parsing a
   * PetiteDB SQL statement.  Upon completion of the constructor invocation,
   * the lexer MUST HAVE consumed one token, and is able to return that token
   * via firstTokenRetrieved().
   *
   * @param s the statement to be lex'd
   * @throws BadSyntaxException if a problem occurs when reading the input for
   * the first token.
   * @see firstTokenRetrieved
   */
  public LexerBase(String s) {
    // fill me in in your implementation class!
  }

  /** Returns the first token that the constructor extracted from the input.
   * Clients extract subsequent tokens via calls to nextToken(); this method
   * will return the first token (extracted by the constructor), and doesn't
   * reset the "pointer" if reinvoked.
   *
   * @return Token first token in the input
   */
  abstract public Token firstTokenRetrieved();

  /** Extracts the next token from the input.  When all input has been
   * consumed, the token type of that last token must be EOF.  Subsequent
   * invocations of nextToken() will continue to return the EOF token type.
   *
   * @throws BadSyntaxException if the lexer runs into trouble processing the
   * input
   */
  public abstract Token nextToken() throws BadSyntaxException;

  /** Defines the set of valid PetiteDB comparison operators as an unmodifable list
   */
  public final static List<Character> comparisonOperators = List.of
    ('=', '>', '<');

  /** Defines the set of valid PetiteDB logical operators as an unmodifiable
   * list.  Defined in lower-case because the lexer is supposed to lower-case
   * everything but string constants.
   */
  public final static List<String> logicalOperators = List.of("and");

  /** Defines the set of valid PetiteDB keywords as an unmodifiable List.
   * Defined in lower-case because the lexer is supposed to lower-case
   * everything but string constants.
   */
  public final static List<String> keywords = List.
    of("insert", "into", "values", "delete",
       "int", "varchar", "update", "set", "select", "from", "where", "and",
       "index", "on", "create", "table");
}
