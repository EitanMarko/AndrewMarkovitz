package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DBConnectionManager
 *
 * A singleton class that manages two separate HikariCP connection pools:
 *   1. A WRITE pool — connects to PgBouncer's "statcentral_write" alias,
 *      which routes to the PostgreSQL PRIMARY node. Used by all microservices
 *      that modify data: PlayerHandler, LeagueStructureManager, StatUpdater.
 *
 *   2. A READ pool  — connects to PgBouncer's "statcentral_read" alias,
 *      which routes to the PostgreSQL REPLICA node. Used by all read-only
 *      microservices: PlayerStatQuerier, AggregateStatQuerier.
 *
 * WHY HIKARICP?
 *   HikariCP ("Hikari Connection Pool") is the industry-standard Java
 *   connection pooling library. It maintains a pool of already-open JDBC
 *   connections that microservices can borrow and return, avoiding the
 *   overhead of opening a new connection for every database operation.
 *
 *   Even though PgBouncer already pools connections at the network level,
 *   HikariCP provides pooling at the JVM level — reducing socket overhead
 *   and giving us fast connection acquisition within the same JVM process.
 *   Together, they form two tiers of connection efficiency.
 *
 * DEPENDENCY (add to pom.xml or build.gradle):
 *   Maven:  <dependency>
 *               <groupId>com.zaxxer</groupId>
 *               <artifactId>HikariCP</artifactId>
 *               <version>5.1.0</version>
 *           </dependency>
 *   Gradle: implementation 'com.zaxxer:HikariCP:5.1.0'
 *
 * SINGLETON PATTERN:
 *   We use a thread-safe "initialization-on-demand holder" pattern. The pools
 *   are created exactly once when first accessed, and the JVM's class loader
 *   guarantees thread safety without needing synchronized blocks.
 */
public class DBConnectionManager {

    // -------------------------------------------------------------------------
    // Configuration constants — in production these should be loaded from
    // environment variables or a secrets manager (e.g. AWS Secrets Manager,
    // Vault) rather than hardcoded here.
    // -------------------------------------------------------------------------

    // PgBouncer host and port. All connections go through PgBouncer, NEVER
    // directly to PostgreSQL. PgBouncer then routes based on the database name.
    private static final String PGBOUNCER_HOST = System.getenv().getOrDefault(
            "PGBOUNCER_HOST", "localhost");
    private static final int    PGBOUNCER_PORT = Integer.parseInt(System.getenv().getOrDefault(
            "PGBOUNCER_PORT", "6432"));

    // The two database aliases defined in pgbouncer.ini [databases] section.
    // PgBouncer maps these to the primary and replica PostgreSQL hosts respectively.
    private static final String WRITE_DB_NAME = "statcentral_write";
    private static final String READ_DB_NAME  = "statcentral_read";

    // Application credentials — must match an entry in PgBouncer's userlist.txt
    // and must also exist as a PostgreSQL role with appropriate privileges.
    private static final String DB_USER     = "appuser";
    private static final String DB_PASSWORD = "apppassword";

    // Pool sizing — tune these based on your server capacity.
    // The total connections opened to PgBouncer = POOL_SIZE * (number of WorkerServer JVMs).
    // Make sure this does not exceed PgBouncer's max_client_conn in pgbouncer.ini.
    private static final int POOL_SIZE     = 10;  // max connections per pool per JVM
    private static final int MIN_IDLE      = 2;   // connections kept warm when idle
    private static final int TIMEOUT_MS    = 3000; // max ms to wait for a connection

    // -------------------------------------------------------------------------
    // Holder classes — implement the thread-safe singleton pattern.
    // Each pool is created lazily on first access.
    // -------------------------------------------------------------------------

    /** Holds the singleton WRITE pool (primary node). */
    private static class WritePoolHolder {
        static final HikariDataSource INSTANCE = createPool(WRITE_DB_NAME, "WritePool");
    }

    /** Holds the singleton READ pool (replica node). */
    private static class ReadPoolHolder {
        static final HikariDataSource INSTANCE = createPool(READ_DB_NAME, "ReadPool");
    }

    // -------------------------------------------------------------------------
    // Public API — called by microservices
    // -------------------------------------------------------------------------

    /**
     * Returns a JDBC Connection from the WRITE pool.
     * Use this for all INSERT, UPDATE, and DELETE operations.
     * Routes through PgBouncer → PostgreSQL PRIMARY node.
     *
     * IMPORTANT: Always call this inside a try-with-resources block:
     *
     *   try (Connection conn = DBConnectionManager.getWriteConnection()) {
     *       // use conn ...
     *   }
     *
     * The try-with-resources automatically returns the connection to the pool
     * when the block exits, even if an exception is thrown.
     *
     * @return a Connection from the write pool
     * @throws SQLException if the pool is exhausted or PgBouncer is unreachable
     */
    public static Connection getWriteConnection() throws SQLException {
        return WritePoolHolder.INSTANCE.getConnection();
    }

    /**
     * Returns a JDBC Connection from the READ pool.
     * Use this for all SELECT-only operations.
     * Routes through PgBouncer → PostgreSQL REPLICA node.
     *
     * Same try-with-resources usage as getWriteConnection().
     *
     * @return a Connection from the read pool
     * @throws SQLException if the pool is exhausted or PgBouncer is unreachable
     */
    public static Connection getReadConnection() throws SQLException {
        return ReadPoolHolder.INSTANCE.getConnection();
    }

    /**
     * Gracefully shuts down both connection pools.
     * Call this when the WorkerServer is shutting down to release all
     * database connections cleanly rather than letting them time out.
     */
    public static void shutdown() {
        if (!WritePoolHolder.INSTANCE.isClosed()) {
            WritePoolHolder.INSTANCE.close();
        }
        if (!ReadPoolHolder.INSTANCE.isClosed()) {
            ReadPoolHolder.INSTANCE.close();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Creates and configures a HikariCP DataSource pointing to PgBouncer
     * with the specified database alias.
     *
     * @param dbAlias    the PgBouncer database alias ("statcentral_write" or "statcentral_read")
     * @param poolName   a human-readable name shown in logs and monitoring dashboards
     * @return           a fully configured, ready-to-use HikariDataSource
     */
    private static HikariDataSource createPool(String dbAlias, String poolName) {
        HikariConfig config = new HikariConfig();

        // JDBC URL targets PgBouncer, not PostgreSQL directly.
        // PgBouncer resolves the database alias to the correct backend.
        config.setJdbcUrl(String.format(
                "jdbc:postgresql://%s:%d/%s", PGBOUNCER_HOST, PGBOUNCER_PORT, dbAlias));

        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setPoolName(poolName);

        // Pool sizing
        config.setMaximumPoolSize(POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);

        // How long to wait for a connection from the pool before throwing.
        // Should be shorter than your HTTP/TCP timeout to fail fast.
        config.setConnectionTimeout(TIMEOUT_MS);

        // How long a connection can sit idle in the pool before being evicted.
        // Prevents stale connections from accumulating.
        config.setIdleTimeout(600_000);  // 10 minutes

        // Maximum lifetime of a connection in the pool regardless of activity.
        // Should be less than PostgreSQL's idle_in_transaction_session_timeout.
        config.setMaxLifetime(1_800_000); // 30 minutes

        // Test query to validate connections before handing them out.
        // PgBouncer in transaction mode requires this to verify the connection
        // is still alive after being idle.
        config.setConnectionTestQuery("SELECT 1");

        // CRITICAL for PgBouncer in transaction pooling mode:
        // Disable auto-commit so that each getConnection() call starts a clean
        // transaction context. Microservices commit explicitly.
        config.setAutoCommit(false);
        return new HikariDataSource(config);
    }

    // Prevent instantiation — this is a pure static utility class.
    private DBConnectionManager() {}
}

// Run this in Powershell as admin to start pgbouncer:
    // docker run -d --name pgbouncer -p 6432:5432 -e DB_HOST=host.docker.internal -e DB_PORT=5432 -e DB_USER=postgres -e DB_PASSWORD=postgres -e DB_NAME=statcentral edoburu/pgbouncer

// Call this at 'PS C:\Program Files\PostgreSQL\18\bin>' to reach database:
    // .\psql -h localhost -p 5432 -U postgres

// Call this to attempt to reach pgbouncer from the same directory as above:
    // .\psql -h localhost -p 6432 -U postgres postgres