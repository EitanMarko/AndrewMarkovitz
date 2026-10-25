package edu.yu.dbimpl.execute;

/** A ResultSet encapsulates the result of invoking a relational algebra
 * (query) operation.
 *
 * Clients must be aware that a ResultSet implementation instance need not be
 * thread-safe.
 * 
 * @author Avraham Leff
 */

import edu.yu.dbimpl.query.Scan;
import edu.yu.dbimpl.record.SchemaBase;

public interface ResultSet {

  /** Opens a scan on the result of the relational algebra operation.
   *
   * @return a Scan on the result.
   */
  public Scan open();

  /** Returns the schema associated with the result of the relational algebra
   * operation.
   *
   * @return the query's schema
   */
  public SchemaBase schema();
} // interface
