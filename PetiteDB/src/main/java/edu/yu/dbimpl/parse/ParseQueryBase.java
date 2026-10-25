package edu.yu.dbimpl.parse;

/** Defines an AST API for artifacts returned by a Parser's processing of a
 * select statement (see the PetiteDB grammar definition of Query)

 * Students MAY NOT modify this class in any way!.
 *
 * @author Avraham Leff
 */

import java.util.*;
import edu.yu.dbimpl.query.Predicate;

public abstract class ParseQueryBase {

  /** Constructor.
   *
   * @param fields the fields specified in the select statement in the order
   * that they were extracted from the parse input.
   * @param tables the tables specified in the select statement in the order
   * that they were extracted from the parse input.
   * @param predicate the predicate specified in the select statement
   * @throws IllegalArgumentException if any of the input parameters are
   * syntactically invalid.
   */
  public ParseQueryBase
    (List<String> fields, List<String> tables, Predicate predicate)
  {
    // no-op
  }
   
  /** Returns the fields supplied to the constructor.
   *
   * @return a list of field names
   */
  public abstract List<String> fields();
   
  /** Returns the tables supplied to the constructor.
   *      
   * @return a list of table names
   */
  public abstract List<String> tables();
   
  /** Returns the predicate supplied to the constructor.
   *
   * @return the query predicate
   */
  public abstract Predicate predicate();
} // ParseQueryBase
