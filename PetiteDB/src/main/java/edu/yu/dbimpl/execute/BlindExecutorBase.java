package edu.yu.dbimpl.execute;

/** Specifies the public API for the BlindExecutor implementation by requiring
 * all implementations to extend this base class.
 *
 * Students MAY NOT modify this class in any way, they must suppport EXACTLY
 * the constructor signatures specified in the base class (and NO OTHER
 * signatures).
 *
 * An Executor is the top-level API used by clients to have PetiteDB execute
 * their query and mutation (C/U/D) commands.  A BlindExecutor is a minimal,
 * bare-bones, SQL evaluation engine that only provides "Parse & Execute"
 * function.  Given SQL it determines whether a read operation is being invoked
 * or whether a mutation operation is being invoked.  The SQL is executed
 * exactly as written, in the exact order it was written, completely blind to
 * the underlying data volume or distribution.
 *
 * Design note: a BlindExecutor doesn't do "planning" (constructing logically
 * equivalent executions, evaluating their cost, and picking the cheapest).  A
 * BlindExecutor doesn't even do optimizations such as "when joining two
 * tables, drive the join from the smaller table".  Instead: a BlindExecutor
 * parses the SQL into an AST and then walks through the AST "as is" and apply
 * the command against the database.
 *
 * Design note: clients must be aware that a BlindExecutor instance need not be
 * thread-safe.
 * 
 * Implementation guide: for this iteration, you need not provide sophisticated
 * implementations of the relational operators discussed in class.  Thus, a
 * JOIN becomes a standard Nested Loop Join, and a WHERE clause triggers a full
 * sequential scan.
 *
 * Implementation suggestion: consider implementing SelectScanBase,
 * ProjectScanBase, ProductScanBase.  (These classes reside in dbimpl.query).
 * Having Scan implementations for the basic relational algebra operators may
 * simplify a pipelining design in which all methods EXCEPT next() are
 * delegated "as is" to the encapsulated scans.  See lecture discussion about
 * this approach.
 *
 * @author Avraham Leff
 */

import edu.yu.dbimpl.metadata.TableMgrBase;
import edu.yu.dbimpl.tx.TxBase;

public abstract class BlindExecutorBase {

  /** Constructor: provides important PetiteDB (conceptually) singleton
   * classes.
   *
   * @param tableMgr a valid (singleton) Table Manager contains metadata
   * (catalog) information.
   */
  public BlindExecutorBase(final TableMgrBase tableMgr) {
    // no-op implementation
  }

  /** Drives a SQL "select" command to the database and return the result.
   *
   * @param query An SQL query that that's valid per the PetiteDB "subset SQL"
   * grammar.
   * @param tx Provides the transactional scope under which the query will
   * execute.
   * @return the query results
   * @throws BadSemanticsException if the query's semantics aren't valid e.g.,
   * because of a mismatch between the query and the schema associated with the
   * specified tables.  
   * @throws BadSyntaxException if the query can't be parsed as a valid
   * PetiteDB SQL query.
   * @see BadSemanticsException.java
   * @see https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Statement.html#executeQuery(java.lang.String)
   */
  public abstract ResultSet executeQuery(String query, TxBase tx);

  /** Drives a SQL "insert/delete/update" command to the database and return
   * the number of affected rows.
   *
   * @param query An SQL query that that's valid per the PetiteDB "subset SQL"
   * grammar.
   * @param tx Provides the transactional scope under which cmd will execute.
   * @return the number of rows of affected by the command.
   * @throws BadSemanticsException if the query's semantics aren't valid e.g.,
   * because of a mismatch between the query and the schema associated with the
   * specified tables.  
   * @throws BadSyntaxException if the query can't be parsed as a valid
   * PetiteDB SQL query.
   * @see BadSemanticsException.java
   * https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Statement.html#executeUpdate(java.lang.String)
   */
  public abstract int executeUpdate(String cmd, TxBase tx);


} // abstract class
