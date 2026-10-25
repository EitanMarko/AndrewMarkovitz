package edu.yu.dbimpl.parse;

/** Defines an AST API for artifacts returned by a Parser's processing of an
 * update statement (see the PetiteDB grammar definition of Update)

 * Students MAY NOT modify this class in any way!.
 *
 * @author Avraham Leff
 */

import edu.yu.dbimpl.query.*;

public abstract class ParseUpdateBase {

  /** Constructor.
   *
   * @param tableName the name of the table to which the update will be
   * applied.
   * @param fieldName the name of the field being updated.
   * @param newValue the expression representing the value to which the target field
   * will be updated.
   * @param predicate the predicate that filters the tuples that will be
   * updated.
   */
  public ParseUpdateBase(String tableName, String fieldName, Expression newValue, Predicate predicate) {
    // no-op
  }
   
  /** Returns the name of the table to which the update will be applied.
   */
  public abstract String tableName();
   
  /** Returns the name of the field being updated.
   */
  public abstract String targetField();
   
  /** Returns the expression representing the value to which the target field
   * will be updated.
   */
  public abstract Expression newValue();
   
  /** Returns the predicate that filters the tuples that will be updated.
   */
  public abstract Predicate predicate();
}
