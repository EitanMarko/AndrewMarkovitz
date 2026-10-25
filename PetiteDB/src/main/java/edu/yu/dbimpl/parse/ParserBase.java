package edu.yu.dbimpl.parse;

/** Defines an Parser API for the small subset of SQL used in PetiteDB (be sure
 * to see the BNF definition and railroad diagram).
 * 
 * Design note: the design assumes a lexer class is doing the tokenization, and
 * that the parser is providing only "syntax-only parser" function.  The parser
 * is thus responsible for creating an Abstract Syntax Tree (AST) from the
 * input and returning that to clients.  Alternatively: the parser DOES NOT do
 * any downstream execution.
 *
 * Clients can use this class to (only) parse sub-clauses in the PetiteDB
 * grammar (e.g., a Term or Expression).  If a client's request can be
 * satisfied from the current parser position, the parse MAY NOT throw an
 * exception, must satisfy the request, even if the remainder of the input is
 * not recognized by the grammar.  The problems with the input will only
 * manifest if the client continues to request PetiteDB non-terminals that
 * can't be satisfied subsequently.  
 *
 * Students MAY NOT modify this class in any way!.
 *
 * Design note: this class does NOT define the interface to the PetiteDB
 * parser.  I've deliberately not specified such an API in order to give
 * complete implementation freedom.  Instead, this class defines a "results
 * oriented" API: given a String to parse, does your parser construct the
 * correct return type (or, if necessary, throw BadSyntaxException)
 *
 * Design note: as a "results oriented" API, this class isn't responsible for
 * determining whether the overall input is valid.  Rather: given the current
 * state of the parsing in the input, determine whether the specified SQL
 * constructed can be parsed from the remaining portion of the input.
 *
 * Design note: the parser and lexer MUST fail-early-and-fatally after throwing
 * a BadSyntaxException.  Do not try to recover from the error, let the client
 * fix the input and try again.  The behavior of subsequent API invocations on
 * the input by this parser or lexer instance is undefined.
 *
 * NOTE: This iteration doesn't currently require that the entire BNF be
 * supported.
 * 
 * @author Avraham Leff
 */

import edu.yu.dbimpl.query.DatumBase;
import edu.yu.dbimpl.query.Expression;
import edu.yu.dbimpl.query.Predicate;
import edu.yu.dbimpl.query.Term;

public abstract class ParserBase {

  /** Constructor: stores the supplied string so as to be able to parse later
   * per the client's explicit subsequent "typing" invocations.  The
   * constructor MUST throw a BadSyntaxException if a PetiteDB Lexer would
   * throw the BSE when trying to extract the first token (see the LexerBase
   * constructor Javadoc).
   *
   * @param s the string to be parsed
   * @throws BadSyntaxException if the Lexer constructor would throw an
   * exception on this input.
   */      
  public ParserBase (String s) {
    // no-op
  }

  /** Returns true iff current state of parser with respect to the constructor
   * parameter is "no more tokens to extract".
   *
   * @return true iff no more tokens to extract, false otherwise.
   */
  public abstract boolean atEOF();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the parsed field if valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract String field();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the parsed keyword if valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract String keyword();
  
  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the parsed constant (DatumBase) if valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract DatumBase constant();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the parsed expression if valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract Expression expression();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the parsed term if valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract Term term();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the parsed predicate if valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract Predicate predicate();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the corresponding parsed query instance if input is valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract ParseQueryBase query();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the corresponding parsed insert instance if input is valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract ParseInsertBase insert();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the corresponding parsed delete instance if input is valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract ParseDeleteBase delete();
  
  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the corresponding parsed update instance if input is valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract ParseUpdateBase update();

  /** Attempts to extract the specified non-terminal from the parser's current
   * position in the the constructor parameter
   *
   * @return the corresponding parsed "create table" instance if input is valid
   * @throws BadSyntaxException if the parser (given its current state relative
   * to the input) can't extract the specified grammar construct.
   */
  public abstract ParseCreateTableBase createTable();
}
