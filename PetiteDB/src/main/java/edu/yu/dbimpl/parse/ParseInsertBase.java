package edu.yu.dbimpl.parse;

/** Defines an AST API for artifacts returned by a Parser's processing of an
 * insert statement (see the PetiteDB grammar definition of Insert)

 * Students MAY NOT modify this class in any way!.
 *
 * @author Avraham Leff
 */

import java.util.List;
import edu.yu.dbimpl.query.DatumBase;

public abstract class ParseInsertBase {

  /** Constructor.
   *
   * @param tableName the table into which the new tuple will be inserted
   * @param fields names the fields (column names) of the tuple to be inserted
   * @param values supplies values for the fields (column names) of the tuple
   * to be inserted: the ith value element is associated with the ith field
   * element.
   * @throws IllegalArgumentException if any of the input parameters are
   * syntactically invalid.
   */
  public ParseInsertBase
    (String tableName, List<String> fields, List<DatumBase> values) {
    // no op
  }
   
  /** Returns the fields supplied to the constructor.
   *
   * @return a list of field names
   */
  public abstract List<String> fields();

  /** Returns the values supplied to the constructor.
   *
   * @return a list of values
   */
  public abstract List<DatumBase> values();

  
  /** Returns the table name supplied to the constructor.
   *      
   * @return a list of table names
   */
  public abstract String tableName();
   
} // ParseInsertBase
