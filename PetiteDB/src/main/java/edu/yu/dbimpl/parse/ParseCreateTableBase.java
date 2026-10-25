package edu.yu.dbimpl.parse;

/** Defines an AST API for artifacts returned by a Parser's processing of an
 * create table statement (see the PetiteDB grammar definition)

 * Students MAY NOT modify this class in any way!.
 *
 * @author Avraham Leff
 */

import edu.yu.dbimpl.query.*;

import edu.yu.dbimpl.record.SchemaBase;

public abstract class ParseCreateTableBase {

  /** Constructor.
   *
   * @param tableName name of the new table
   * @param schema the logical record schema of the new table
   */
  public ParseCreateTableBase(String tblname, SchemaBase schema) {
    // no-op
  }

  public abstract String tableName();

  public abstract SchemaBase schema();
}
