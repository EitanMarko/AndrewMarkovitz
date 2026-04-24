package leagues;

import database.DBConnectionManager;
import microservices.PlayerData;
import microservices.StatUpdater;
import java.sql.*;
import java.time.Year;
import java.util.Set;

/**
 * NBAStatUpdater
 *
 * Concrete implementation of StatUpdater for the National Basketball Association.
 * Operates on the nba_player_stats table, which has NBA-specific columns.
 *
 * ── Supported Stats ──────────────────────────────────────────────────────────
 *   points               — total points scored
 *   rebounds             — total rebounds
 *   assists              — total assists
 *   steals               — total steals
 *   blocks               — total blocked shots
 *   turnovers            — total turnovers
 *   fieldGoalsMade       — field goals made
 *   fieldGoalsAttempted  — field goal attempts
 *   fieldGoalPercentage  — fieldGoalsMade / fieldGoalsAttempted (derived, like battingAverage)
 *
 * ── Field Goal Percentage: A Derived Stat ────────────────────────────────────
 *   - incrementStat("fieldGoalsMade")      → increments made AND attempted,
 *                                            recomputes fieldGoalPercentage
 *   - incrementStat("fieldGoalsAttempted") → increments attempted only (missed shot),
 *                                            recomputes fieldGoalPercentage
 *   - setStat("fieldGoalPercentage", v)    → directly sets the stored column
 *
 * ── Instantiation ────────────────────────────────────────────────────────────
 * Discovered at runtime via the naming convention "impl.[leagueId]StatUpdater".
 * No registration needed — having this class on the classpath is sufficient.
 */
public class NBAStatUpdater extends StatUpdater {

    private static final Set<String> SUPPORTED_STATS = Set.of(
            "points",
            "rebounds",
            "assists",
            "steals",
            "blocks",
            "turnovers",
            "fieldGoalsMade",
            "fieldGoalsAttempted",
            "fieldGoalPercentage"
    );

    @Override
    public Set<String> getSupportedStats() {
        return SUPPORTED_STATS;
    }

    @Override
    public String getStatsTable() {
        return "nba_player_stats";
    }

    @Override
    public String toColumnName(String camelCase) {
        return camelCase.replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    /**
     * Inserts an all-zero initial stats row for a newly created NBA player.
     * PlayerData does not have NBA-specific stat fields, so all columns start at 0.
     * Called by PlayerHandler within the createPlayer transaction.
     */
    @Override
    protected void doCreateInitialStats(Connection conn, int playerId,
                                         PlayerData playerData) throws SQLException {
        String sql =
            "INSERT INTO nba_player_stats " +
            "(player_id, year, points, rebounds, assists, steals, blocks, turnovers, " +
            " field_goals_made, field_goals_attempted, field_goal_percentage) " +
            "VALUES (?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 0.000)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, playerData.getYear());
            stmt.executeUpdate();
        }
    }

    // =========================================================================
    // doIncrementStat
    // =========================================================================

    @Override
    protected String doIncrementStat(String playerId, String statName) {
        if ("fieldGoalPercentage".equals(statName)) {
            return "Error: 'fieldGoalPercentage' cannot be incremented directly. "
                    + "Increment 'fieldGoalsMade' (made shot) or 'fieldGoalsAttempted' "
                    + "(missed shot) instead; the percentage is automatically recomputed.";
        }

        int year = Year.now().getValue();

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            if ("fieldGoalsMade".equals(statName)) {
                // A made field goal is always also an attempt.
                // Increment both columns and recompute the percentage atomically.
                String sql =
                    "UPDATE nba_player_stats " +
                    "SET field_goals_made      = field_goals_made + 1, " +
                    "    field_goals_attempted  = field_goals_attempted + 1, " +
                    "    field_goal_percentage  = CASE WHEN (field_goals_attempted + 1) > 0 " +
                    "                                  THEN ROUND((field_goals_made + 1.0) / (field_goals_attempted + 1), 3) " +
                    "                                  ELSE 0.000 END " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?";
                return executeUpdateAndCheck(conn, sql, playerId, year, statName);

            } else if ("fieldGoalsAttempted".equals(statName)) {
                // A missed shot — only the attempt count goes up.
                String sql =
                    "UPDATE nba_player_stats " +
                    "SET field_goals_attempted  = field_goals_attempted + 1, " +
                    "    field_goal_percentage  = CASE WHEN (field_goals_attempted + 1) > 0 " +
                    "                                  THEN ROUND(field_goals_made::NUMERIC / (field_goals_attempted + 1), 3) " +
                    "                                  ELSE 0.000 END " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?";
                return executeUpdateAndCheck(conn, sql, playerId, year, statName);

            } else {
                // All other stats: simple increment of the corresponding column.
                String col = toColumnName(statName);
                String sql = String.format(
                    "UPDATE nba_player_stats " +
                    "SET %s = %s + 1 " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?", col, col);
                return executeUpdateAndCheck(conn, sql, playerId, year, statName);
            }

        } catch (SQLException e) {
            System.err.println("[NBAStatUpdater.doIncrementStat] DB error: " + e.getMessage());
            return "Error: database operation failed while incrementing '"
                    + statName + "' for player '" + playerId + "'. Details: " + e.getMessage();
        }
    }

    // =========================================================================
    // doSetStat
    // =========================================================================

    @Override
    protected String doSetStat(String playerId, String statName, int value) {
        int year = Year.now().getValue();

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            if ("fieldGoalsMade".equals(statName) || "fieldGoalsAttempted".equals(statName)) {
                // When either component is directly set, recompute the percentage
                // in the same UPDATE for atomicity.
                String col      = toColumnName(statName);
                String madeCol  = "field_goals_made";
                String attCol   = "field_goals_attempted";

                String simpleSQL = String.format(
                    "UPDATE nba_player_stats " +
                    "SET %s = ?, " +
                    "    field_goal_percentage = " +
                    "        CASE WHEN (CASE WHEN '%s' = 'field_goals_made'     THEN ? ELSE field_goals_made END + " +
                    "                   CASE WHEN '%s' = 'field_goals_attempted' THEN ? ELSE field_goals_attempted END) > 0 " +
                    "             THEN ROUND(" +
                    "                  CASE WHEN '%s' = 'field_goals_made' THEN ? ELSE field_goals_made END::NUMERIC / " +
                    "                  GREATEST(CASE WHEN '%s' = 'field_goals_attempted' THEN ? ELSE field_goals_attempted END, 1)" +
                    "                  , 3) " +
                    "             ELSE 0.000 END " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?",
                    col, col, col, col, col, col
                );

                try (PreparedStatement stmt = conn.prepareStatement(simpleSQL)) {
                    stmt.setInt(1, value);
                    stmt.setInt(2, value);
                    stmt.setInt(3, value);
                    stmt.setInt(4, value);
                    stmt.setInt(5, value);
                    stmt.setString(6, playerId);
                    stmt.setInt(7, year);

                    int rows = stmt.executeUpdate();
                    conn.commit();
                    if (rows == 0) {
                        return "Error: player '" + playerId + "' does not exist "
                                + "or has no stats row for year " + year + ".";
                    }
                    return "Success: '" + statName + "' set to " + value
                            + " for player '" + playerId + "' (field goal percentage recomputed).";
                }

            } else {
                // Direct set for all other stats.
                String col = toColumnName(statName);
                String sql = String.format(
                    "UPDATE nba_player_stats SET %s = ? " +
                    "WHERE player_id = (SELECT id FROM players WHERE name = ?) " +
                    "  AND year = ?", col);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, value);
                    stmt.setString(2, playerId);
                    stmt.setInt(3, year);

                    int rows = stmt.executeUpdate();
                    conn.commit();
                    if (rows == 0) {
                        return "Error: player '" + playerId + "' does not exist "
                                + "or has no stats row for year " + year + ".";
                    }
                    return "Success: '" + statName + "' set to " + value
                            + " for player '" + playerId + "'.";
                }
            }

        } catch (SQLException e) {
            System.err.println("[NBAStatUpdater.doSetStat] DB error: " + e.getMessage());
            return "Error: database operation failed while setting '"
                    + statName + "' for player '" + playerId + "'. Details: " + e.getMessage();
        }
    }

    // =========================================================================
    // Private helper
    // =========================================================================

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