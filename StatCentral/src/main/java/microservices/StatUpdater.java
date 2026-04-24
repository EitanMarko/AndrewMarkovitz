package microservices;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * StatUpdater
 *
 * Abstract base class for all sport-specific stat updater microservice objects.
 * A StatUpdater is held by a WorkerServer and is responsible for updating player
 * statistics in the underlying database.
 *
 * ── Connection Routing ───────────────────────────────────────────────────────
 * All operations in StatUpdater subclasses are WRITES (UPDATE statements),
 * so they must use DBConnectionManager.getWriteConnection() — routing through
 * PgBouncer to the PRIMARY PostgreSQL node. This is enforced in the concrete
 * subclass implementations of doIncrementStat() and doSetStat().
 *
 * ── Design: Template Method Pattern ─────────────────────────────────────────
 * This class applies the Template Method pattern. The two public-facing methods
 * that the WorkerServer calls — incrementStat() and setStat() — are declared
 * final. They perform all shared validation logic (stat name recognition,
 * value constraint checking) before delegating to the protected abstract methods
 * doIncrementStat() and doSetStat(), which each concrete subclass implements
 * with sport-specific database logic.
 *
 * This guarantees:
 *   - Validation is enforced uniformly. No subclass can bypass it.
 *   - Subclasses only handle sport-specific database logic, not validation.
 *   - The WorkerServer's calling interface never changes regardless of sport.
 *
 * ── How to extend for a new sport ───────────────────────────────────────────
 * 1. Implement getSupportedStats() — return the Set of valid stat names.
 * 2. Implement doIncrementStat(playerId, statName) — issue the DB increment.
 * 3. Implement doSetStat(playerId, statName, value) — issue the DB set.
 *
 * See MLBStatUpdater for a complete reference implementation.
 */
public abstract class StatUpdater {

    // =========================================================================
    // Public API — called by WorkerServer. FINAL: cannot be overridden.
    // =========================================================================

    /**
     * Increments the specified statistical category by one for the given player.
     *
     * Validates that the stat name is recognized before delegating to the
     * sport-specific doIncrementStat() implementation.
     *
     * @param playerId  The unique identifier (name) of the player to update.
     * @param statName  The stat category to increment (e.g. "homeRuns", "hits").
     * @return          Success message, or error if stat is unknown or player not found.
     */
    public final String incrementStat(String playerId, String statName) {
        // Validate the stat name against the sport-specific whitelist.
        // If invalid, we return immediately without touching the database.
        if (!getSupportedStats().contains(statName)) {
            return "Error: stat category '" + statName
                    + "' is not defined for this sport. Supported stats: "
                    + getSupportedStats();
        }
        // Delegate to the sport-specific implementation.
        // At this point, statName is guaranteed to be valid.
        return doIncrementStat(playerId, statName);
    }

    /**
     * Sets the specified statistical category to the given value for the given player.
     *
     * Validates that the stat name is recognized and the value is non-negative
     * before delegating to the sport-specific doSetStat() implementation.
     *
     * @param playerId  The unique identifier (name) of the player to update.
     * @param statName  The stat category to set (e.g. "homeRuns", "hits").
     * @param value     The value to assign. Must be non-negative (≥ 0).
     * @return          Success message, or error if stat is unknown, value is negative,
     *                  or player is not found.
     */
    public final String setStat(String playerId, String statName, int value) {
        // Validate stat name
        if (!getSupportedStats().contains(statName)) {
            return "Error: stat category '" + statName
                    + "' is not defined for this sport. Supported stats: "
                    + getSupportedStats();
        }
        // Validate that the value is non-negative.
        // Statistics like home runs and strikeouts cannot be negative.
        if (value < 0) {
            return "Error: stat value cannot be negative (received " + value
                    + " for stat '" + statName + "').";
        }
        // Delegate to sport-specific implementation.
        // statName is guaranteed valid and value is guaranteed non-negative.
        return doSetStat(playerId, statName, value);
    }

    /**
     * Convenience check for whether a given stat name is valid for this sport.
     *
     * @param statName  The stat name to check.
     * @return          true if this sport's StatUpdater supports the stat.
     */
    public boolean supportsStat(String statName) {
        return getSupportedStats().contains(statName);
    }

    // =========================================================================
    // Abstract methods — implemented by each sport-specific subclass
    // =========================================================================

    /**
     * Returns the complete set of stat category names tracked for this sport.
     * Must return a constant, non-null, unmodifiable Set.
     * The strings here must exactly match what callers send in requests.
     *
     * @return  Unmodifiable Set of all valid stat name strings for this sport.
     */
    public abstract Set<String> getSupportedStats();

    /**
     * Returns the name of the database table that stores this league's stats.
     * Used by PlayerStatQuerier and AggregateStatQuerier to build league-agnostic
     * queries without hardcoding any table name outside the subclass.
     *
     * Examples:
     *   MLBStatUpdater → "player_stats"
     *   NBAStatUpdater → "nba_player_stats"
     *
     * @return  The SQL table name for this league's stats rows.
     */
    public abstract String getStatsTable();

    /**
     * Converts a camelCase stat name (as used in the API) to its snake_case
     * SQL column name counterpart (as used in this league's stats table).
     *
     * Used by PlayerStatQuerier and AggregateStatQuerier to map incoming stat
     * names to the correct column without hardcoding the mapping outside the subclass.
     *
     * Examples (MLB):
     *   "homeRuns"  → "home_runs"
     *   "atBats"    → "at_bats"
     *   "strikeouts"→ "strikeouts"
     *
     * @param camelCase  A stat name from getSupportedStats()
     * @return           The corresponding snake_case column name in the stats table
     */
    public abstract String toColumnName(String camelCase);

    /**
     * Inserts the initial stats row for a newly created player into this league's
     * stats table. Called by PlayerHandler.createPlayer() within the same transaction
     * as the player row INSERT, so both succeed or fail together.
     *
     * MLB uses PlayerData's stat fields (homeRuns, hits, etc.) as initial values.
     * Other leagues that don't have matching PlayerData fields insert all zeros.
     *
     * @param conn      An open connection with autoCommit=false (caller manages commit).
     * @param playerId  The newly assigned player ID from the players table.
     * @param playerData The PlayerData from the createPlayer request.
     * @throws SQLException if the INSERT fails.
     */
    protected abstract void doCreateInitialStats(Connection conn, int playerId,
                                                  PlayerData playerData) throws SQLException;

    /**
     * Sport-specific implementation: increment the given stat by 1 for the player.
     *
     * Called only after all validation in incrementStat() has passed.
     * statName is guaranteed to be in getSupportedStats(). No re-validation needed.
     *
     * @param playerId  Player name/ID. Guaranteed non-null.
     * @param statName  Stat to increment. Guaranteed in getSupportedStats().
     * @return          Success or error message (e.g. if player not found in DB).
     */
    protected abstract String doIncrementStat(String playerId, String statName);

    /**
     * Sport-specific implementation: set the given stat to the given value for the player.
     *
     * Called only after all validation in setStat() has passed.
     * statName is guaranteed to be in getSupportedStats(). value is guaranteed ≥ 0.
     *
     * @param playerId  Player name/ID. Guaranteed non-null.
     * @param statName  Stat to set. Guaranteed in getSupportedStats().
     * @param value     Value to assign. Guaranteed ≥ 0.
     * @return          Success or error message (e.g. if player not found in DB).
     */
    protected abstract String doSetStat(String playerId, String statName, int value);
}
