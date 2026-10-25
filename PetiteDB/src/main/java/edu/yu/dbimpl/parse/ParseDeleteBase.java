package edu.yu.dbimpl.parse;

/** Defines an AST API for artifacts returned by a Parser's processing of an
 * delete statement (see the PetiteDB grammar definition of Delete)

 * Students MAY NOT modify this class in any way!.
 *
 * @author Avraham Leff
 */

import edu.yu.dbimpl.query.*;

public abstract class ParseDeleteBase {

  /** Constructor.
   *
   * @param tableName the table from which tuple(s) will be removed.
   * @param predicate the predicate that filters tuples to be deleted.
   * @throws IllegalArgumentException if any of the input parameters are
   * syntactically invalid.
   */
   public ParseDeleteBase(String tableName, Predicate predicate) {
     // no-op
   }
   
  /** Returns the table name passed to the constructor.
   */
  public abstract String tableName();
   
  /** Returns the predicate that filters tuples to be deleted.
   */
  public abstract Predicate predicate();
} // abstract class

