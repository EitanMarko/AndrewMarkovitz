package edu.yu.dbimpl.metadata;

/** Specifies the public API for the TableMgr implementation by requiring all
 * TableMgr implementations to extend this base class.
 *
 * Students MAY NOT modify this class in any way, they must suppport EXACTLY
 * the constructor signatures specified in the base class (and NO OTHER
 * signatures).
 * 
 * TableMgrBase constrains the TableMgr design by implicitly assuming an
 * implementation consisting of one table that stores meta-data about table
 * names, and another table that stores meta-data about field names.  Also:
 * the names of these tables and some of their field names are constrained to
 * match the specified public constants.  In general, PetiteDB tries to give
 * implementations more freedom, but hard to see how to define a testable
 * interface that doesn't use e.g., Java's reflection APIs to do the job.
 *
 * A TableMgr stores the meta-data about tables created by users, thus creating
 * and managing a catalog.  Implementations MUST SUPPORT client ability to
 * TableScan iterate over the TABLE_META_DATA_TABLE and FIELD_META_DATA_TABLE
 * catalog tables.  The TABLE_META_DATA_TABLE MUST STORE table name information
 * in a TABLE_NAME field.  Implementations MUST enforce a strict maximum length
 * for table and field names. If a client attempts to create a table or field
 * with a name exceeding MAX_LENGTH_PER_NAME, the implementation MUST throw an
 * IllegalArgumentException before any metadata is written to the catalog.
 *
 * The DBMS has exactly one TableMgr object (singleton pattern), which is
 * (conceptually) created during system startup, and in practice by a single
 * invocation of the constructor.
 *
 * IMPORTANT: table and field names supplied by the client MUST be stored
 * internally in lower-case and returned to clients as lower-cased.  The API
 * allows clients to supply parameters that have arbitrary case; the
 * implementation is responsible for doing case conversion as necessary.
 *
 * @author Avraham Leff
 */

import edu.yu.dbimpl.tx.TxBase;
import edu.yu.dbimpl.record.LayoutBase;
import edu.yu.dbimpl.record.SchemaBase;

public abstract class TableMgrBase {
  /** The max characters a tablename or fieldname can have.
   */
  public static final int MAX_LENGTH_PER_NAME = 32;

  /** Constants that define the tables and table fields used by the TableMgr
   */

  /** Name of the table storing meta-data about all tables created by the
   * system (not just user-created tables)
   */
  public static final String TABLE_META_DATA_TABLE = "tblcat";

  /** TABLE_META_DATA_TABLE MUST store table names in this attribute.
   */
  public static final String TABLE_NAME = "tblname";
  
  /** Name of the table storing field meta-data: implementations are
   * responsible for storing the requisite information to support the TableMgr
   * APIs, but the number and names of the fields in this table are
   * implementation dependent.
   */
  public static final String FIELD_META_DATA_TABLE = "fldcat";

  /** Constructor: create a new table (catalog) manager.
   *
   * A table manager MUST access the DBConfiguration singleton to determine if
   * it is required to manage a brand-new database or to use an existing
   * database.  If the latter, the table manager is responsible for loading
   * previously persisted catlog information before servicing client requests.
   * @param tx supplies the transactional scope for database operations used in
   * the constructor implementation.  The client is responsible for managing
   * the transaction's life-cycle, and to ensure that the tx is active when
   * passed to the table manager.
   */
  public TableMgrBase(TxBase tx) {
    // fill me in in the implementation class!
  }

  /** Retrieves the layout of the specified table.  If the table is not in the
   * catalog, return null.
   * 
   * @param tableName the name of the table whose meta-data is being requested
   * @param tx supplies the transactional scope for the method's implementation.
   * @return the meta-data for the specified table, null if no such table.
   */
  public abstract LayoutBase getLayout(String tableName, TxBase tx);

  /** Supplies the meta-data that should be persisted to the system catalog
   * about a new database table.
   *
   * NOTE: the table itself need not exist at the time that this method is
   * invoked (or even be created subsequently in the same tx).  It's OK if the
   * user is entering metadata about a table to be created later.
   * 
   * @param tableName the name of the new table.  Per the PetiteDB grammar, the
   * table name MUST be stored in lower-case.
   * @param schema the table's schema
   * @param tx supplies the transactional scope for the method's implementation
   * @return the layout that the DBMS has now associated with this table name.
   * @throws IllegalArgumentException if the catalog already contains an entry
   * for the specified table.
   * @see #replace
   */
  public abstract LayoutBase createTable(String tableName, SchemaBase schema, TxBase tx);

  /** Removes all metadata associated with the specified table from the system
   * catalog. This method performs a logical metadata deletion in the catalog.
   * The implementation is NOT responsible for deleting or modifying the
   * physical data blocks associated with this table.
   * 
   * @param tableName the name of the table whose metadata is being dropped.
   * @param tx supplies the transactional scope for the method's implementation
   * @return LayoutBase the metadata previously associated with the table.
   * @throws IllegalArgumentException if metadata for the table isn't currently
   * in the catalog.
   */
  public abstract LayoutBase dropTableMetadata(String tableName, TxBase tx);

  /** Overwrites the existing metadata for the specified table with a new
   * schema.  This method performs a logical metadata update in the catalog.
   * The implementation is NOT responsible for rewriting existing physical data
   * blocks to conform to the new schema.  Clients invoking this method on a
   * populated table accept the risk of rendering existing physical data
   * unreadable.
   *
   * @param tableName the name of the table.
   * @param schema the table's new schema. Must not be null.
   * @param tx supplies the transactional scope for the method's implementation
   * @return LayoutBase the metadata previously associated with the table.
   * @throws IllegalArgumentException if metadata for the table isn't currently
   * in the catalog, or if the provided schema is null.
   */
  public abstract LayoutBase updateTableMetadata(String tableName, SchemaBase schema, TxBase tx);  
}
