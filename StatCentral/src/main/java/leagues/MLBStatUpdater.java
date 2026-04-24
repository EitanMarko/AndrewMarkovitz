package leagues;

import database.DBConnectionManager;
import microservices.PlayerData;
import microservices.StatUpdater;
import java.sql.*;
import java.time.Year;
import java.util.Set;

/**
 * MLBStatUpdater
 *
 * Concrete implementation of StatUpdater for Major League Baseball.
 * Handles all stat increment and set operations for MLB players.
 *
 * ── Supported Stats ──────────────────────────────────────────────────────────
 * The stat names in SUPPORTED_STATS must exactly match:
 *   (a) The column names in the mlb_player_stats table in schema.sql
 *       (after converting camelCase → snake_case for SQL), AND
 *   (b) The strings the LeagueInterface sends in updatePlayerStat requests.
 *
 * ── Batting Average: A Derived Stat ─────────────────────────────────────────
 * Batting average = hits / at_bats. It is a computed value, not something
 * you "directly" increment. However, we include "battingAverage" in the
 * supported stats set to allow direct setting (useful for historical data loads
 * via setStat). For increment operations, "battingAverage" is intentionally
 * not incremented directly — the league should instead increment "hits" or
 * "atBats", and this class automatically recomputes batting_average.
 *
 * Specifically:
 *   - incrementStat("hits")    → increments hits AND at_bats, recomputes batting_average
 *   - incrementStat("atBats")  → increments at_bats only, recomputes batting_average
 *   - setStat("battingAverage", value) → directly sets the stored batting_average column
 *
 * ── Connection Routing ───────────────────────────────────────────────────────
 * All operations are UPDATEs → write pool → PgBouncer → PostgreSQL primary.
 *
 * ── SQL Injection Safety ─────────────────────────────────────────────────────
 * The stat name is used as a COLUMN NAME in SQL queries, which cannot be
 * parameterized with PreparedStatement's '?' placeholder. Column names must be
 * embedded directly into the SQL string. We prevent injection by:
 *   1. The parent class (StatUpdater) already validates statName against
 *      SUPPORTED_STATS before calling these methods.
 *   2. SUPPORTED_STATS is a hardcoded constant Set — it contains no user input.
 *   3. toColumnName() maps camelCase stat names to their snake_case SQL equivalents,
 *      producing only known-safe column name strings.
 * This means statName is NEVER raw user input by the time we use it in SQL.
 */
public class MLBStatUpdater extends StatUpdater {

    /**
     * The complete set of stat categories tracked for MLB.
     * These strings are the canonical stat identifiers used in LeagueInterface calls.
     *
     * Set.of() returns an immutable Set — no one can accidentally modify it.
     */
    private static final Set<String> SUPPORTED_STATS = Set.of(
            "homeRuns",       // total home runs
            "hits",           // total hits (singles, doubles, triples, HRs)
            "atBats",         // total at-bat appearances
            "battingAverage", // hits / atBats (computed; see class-level comment)
            "strikeouts",     // times struck out
            "walks",          // base-on-balls
            "stolenBases",    // successful stolen base attempts
            "runsBattedIn"    // RBI — runners driven home
    );

    @Override
    public Set<String> getSupportedStats() {
        return SUPPORTED_STATS;
    }

    // =========================================================================
    // doIncrementStat — called by StatUpdater.incrementStat() after validation
    // =========================================================================

    /**
     * Issues a SQL UPDATE to increment the given stat by 1 for the given player
     * in the current year's stats row.
     *
     * Special case for "hits": incrementing a hit also increments at_bats
     * (a hit is always an at-bat), and then recomputes batting_average.
     *
     * Special case for "atBats": recomputes batting_average after incrementing.
     *
     * Special case for "battingAverage": cannot be meaningfully incremented
     * (it's a ratio). We return an informative error directing the caller to
     * use hits/atBats instead.
     *
     * @param playerId  Player name. Guaranteed non-null by parent class.
     * @param statName  Stat to increment. Guaranteed in SUPPORTED_STATS by parent class.
     * @return          Success or error message.
     */
    @Override
    protected String doIncrementStat(String playerId, String statName) {

        // battingAverage cannot be directly incremented — it's a computed ratio.
        if ("battingAverage".equals(statName)) {
            return "Error: 'battingAverage' cannot be incremented directly. "
                    + "Increment 'hits' or 'atBats' instead; batting average "
                    + "is automatically recomputed.";
        }

        int currentYear = Year.now().getValue();

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            if ("hits".equals(statName)) {
                // A hit is simultaneously a hit AND an at-bat.
                // We increment both columns and recompute batting average
                // in a single UPDATE statement for atomicity.
                String sql =
                    "UPDATE mlb_player_stats " +
                    "SET hits    = hits + 1, " +
                    "    at_bats = at_bats + 1, " +
                    // CASE guard: avoid division by zero if at_bats somehow reaches 0
                    "    batting_average = CASE WHEN (at_bats + 1) > 0 " +
                    "                          THEN ROUND((hits + 1.0) / (at_bats + 1), 3) " +
                    "                          ELSE 0.000 END " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?";

                return executeUpdateAndCheck(conn, sql, playerId, currentYear, statName);

            } else if ("atBats".equals(statName)) {
                // An at-bat without a hit (e.g. strikeout, groundout).
                // Recompute batting average with unchanged hit count.
                String sql =
                    "UPDATE mlb_player_stats " +
                    "SET at_bats = at_bats + 1, " +
                    "    batting_average = CASE WHEN (at_bats + 1) > 0 " +
                    "                          THEN ROUND(hits::NUMERIC / (at_bats + 1), 3) " +
                    "                          ELSE 0.000 END " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?";

                return executeUpdateAndCheck(conn, sql, playerId, currentYear, statName);

            } else {
                // All other stats (homeRuns, strikeouts, walks, etc.):
                // a simple increment of the corresponding column.
                //
                // toColumnName() converts camelCase stat name to snake_case SQL column.
                // The stat name has already been validated against SUPPORTED_STATS by
                // the parent class, so this string is always a known-safe column name.
                String col = toColumnName(statName);
                String sql = String.format(
                    "UPDATE mlb_player_stats " +
                    "SET %s = %s + 1 " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?", col, col);

                return executeUpdateAndCheck(conn, sql, playerId, currentYear, statName);
            }

        } catch (SQLException e) {
            System.err.println("[MLBStatUpdater.doIncrementStat] DB error: " + e.getMessage());
            return "Error: database operation failed while incrementing '"
                    + statName + "' for player '" + playerId + "'. Details: " + e.getMessage();
        }
    }

    // =========================================================================
    // doSetStat — called by StatUpdater.setStat() after validation
    // =========================================================================

    /**
     * Issues a SQL UPDATE to set the given stat to the given value for the
     * given player in the current year's stats row.
     *
     * Special case for "hits" and "atBats": recomputes batting_average after
     * setting the new value.
     *
     * @param playerId  Player name. Guaranteed non-null by parent class.
     * @param statName  Stat to set. Guaranteed in SUPPORTED_STATS by parent class.
     * @param value     Value to assign. Guaranteed ≥ 0 by parent class.
     * @return          Success or error message.
     */
    @Override
    protected String doSetStat(String playerId, String statName, int value) {

        int currentYear = Year.now().getValue();

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            if ("hits".equals(statName)) {
                // Set hits; recompute batting_average only when at_bats > 0.
                // Using GREATEST(at_bats, 1) would produce values like 110/1 = 110
                // when at_bats is 0, overflowing NUMERIC(5,3).
                // Only recompute batting_average when at_bats >= new hits value.
                // If at_bats is 0 or smaller than hits (e.g. from a partial data load),
                // dividing would produce a value > 1 or divide-by-zero, overflowing NUMERIC(5,3).
                String sql =
                    "UPDATE mlb_player_stats " +
                    "SET hits = ?, " +
                    "    batting_average = CASE WHEN at_bats >= ? " +
                    "                          THEN ROUND(? ::NUMERIC / at_bats, 3) " +
                    "                          ELSE 0.000 END " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, value);       // SET hits = ?
                    stmt.setInt(2, value);       // WHEN at_bats >= ?
                    stmt.setInt(3, value);       // numerator in ROUND
                    stmt.setString(4, playerId);
                    stmt.setInt(5, currentYear);

                    int rows = stmt.executeUpdate();
                    conn.commit();

                    if (rows == 0) {
                        return "Error: player '" + playerId + "' does not exist "
                                + "or has no stats row for year " + currentYear + ".";
                    }
                    return "Success: 'hits' set to " + value
                            + " for player '" + playerId + "' (batting average recomputed).";
                }

            } else if ("atBats".equals(statName)) {
                // Set at_bats; recompute batting_average only when the new value > 0.
                String sql =
                    "UPDATE mlb_player_stats " +
                    "SET at_bats = ?, " +
                    "    batting_average = CASE WHEN ? > 0 " +
                    "                          THEN ROUND(hits::NUMERIC / ?, 3) " +
                    "                          ELSE 0.000 END " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, value);       // SET at_bats = ?
                    stmt.setInt(2, value);       // WHEN ? > 0
                    stmt.setInt(3, value);       // denominator in ROUND
                    stmt.setString(4, playerId);
                    stmt.setInt(5, currentYear);

                    int rows = stmt.executeUpdate();
                    conn.commit();

                    if (rows == 0) {
                        return "Error: player '" + playerId + "' does not exist "
                                + "or has no stats row for year " + currentYear + ".";
                    }
                    return "Success: 'atBats' set to " + value
                            + " for player '" + playerId + "' (batting average recomputed).";
                }

            } else {
                // For all other stats, a simple direct set.
                String col = toColumnName(statName);
                String sql = String.format(
                    "UPDATE mlb_player_stats SET %s = ? " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?", col);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, value);
                    stmt.setString(2, playerId);
                    stmt.setInt(3, currentYear);

                    int rows = stmt.executeUpdate();
                    conn.commit();

                    if (rows == 0) {
                        return "Error: player '" + playerId + "' does not exist "
                                + "or has no stats row for year " + currentYear + ".";
                    }
                    return "Success: '" + statName + "' set to " + value
                            + " for player '" + playerId + "'.";
                }
            }

        } catch (SQLException e) {
            System.err.println("[MLBStatUpdater.doSetStat] DB error: " + e.getMessage());
            return "Error: database operation failed while setting '"
                    + statName + "' for player '" + playerId + "'. Details: " + e.getMessage();
        }
    }

    // =========================================================================
    // StatUpdater abstract method implementations
    // =========================================================================

    /**
     * Returns the name of the MLB stats table in the database.
     * Used by PlayerStatQuerier and AggregateStatQuerier to build generic queries.
     */
    @Override
    public String getStatsTable() {
        return "mlb_player_stats";
    }

    /**
     * Converts a camelCase stat name to its snake_case SQL column name.
     * Promoted from private to public so PlayerStatQuerier and AggregateStatQuerier
     * can use it without duplicating the conversion logic.
     *
     * Examples:
     *   "homeRuns"      → "home_runs"
     *   "atBats"        → "at_bats"
     *   "stolenBases"   → "stolen_bases"
     *   "runsBattedIn"  → "runs_batted_in"
     *   "strikeouts"    → "strikeouts"
     */
    @Override
    public String toColumnName(String camelCase) {
        return camelCase.replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    /**
     * Inserts the initial MLB stats row for a newly created player.
     * Uses the stat values from PlayerData (all default to 0 if not set).
     * Called by PlayerHandler within the createPlayer transaction.
     */
    @Override
    protected void doCreateInitialStats(Connection conn, int playerId,
                                         PlayerData playerData) throws SQLException {
        String sql =
            "INSERT INTO mlb_player_stats " +
            "(player_id, year, home_runs, hits, at_bats, batting_average, " +
            " strikeouts, walks, stolen_bases, runs_batted_in) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, playerData.getYear());
            stmt.setInt(3, playerData.getHomeRuns());
            stmt.setInt(4, playerData.getHits());
            stmt.setInt(5, playerData.getAtBats());
            stmt.setDouble(6, playerData.getBattingAverage());
            stmt.setInt(7, playerData.getStrikeouts());
            stmt.setInt(8, playerData.getWalks());
            stmt.setInt(9, playerData.getStolenBases());
            stmt.setInt(10, playerData.getRunsBattedIn());
            stmt.executeUpdate();
        }
    }

    /**
     * Executes a PreparedStatement with (playerName, year) parameters and
     * checks whether any rows were affected.
     *
     * @param conn       Open connection (within an active transaction)
     * @param sql        The UPDATE SQL string (with two '?' for player name and year)
     * @param playerId   The player's name
     * @param year       The stat year
     * @param statName   Used only in the success/error message
     * @return           Success or "player not found" error message
     */
    private String executeUpdateAndCheck(Connection conn, String sql,
                                          String playerId, int year, String statName)
            throws SQLException {

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId);
            stmt.setInt(2, year);
            int rows = stmt.executeUpdate();
            conn.commit();

            if (rows == 0) {
                return "Error: player '" + playerId + "' does not exist "
                        + "or has no stats row for year " + year + ".";
            }
            return "Success: '" + statName + "' incremented for player '"
                    + playerId + "' (year " + year + ").";
        }
    }
}
