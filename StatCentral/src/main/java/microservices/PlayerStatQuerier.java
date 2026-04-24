package microservices;

import database.DBConnectionManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PlayerStatQuerier
 *
 * A microservice object held by a WorkerServer. Responsible for retrieving
 * statistics for individual players from the database.
 *
 * ── Connection Routing ───────────────────────────────────────────────────────
 * Both methods here are READ-ONLY (SELECT statements). This microservice
 * uses DBConnectionManager.getReadConnection() — routing through PgBouncer
 * to the REPLICA PostgreSQL node.
 *
 * ── League-Agnostic Design ───────────────────────────────────────────────────
 * Both methods accept a StatUpdater parameter that supplies all league-specific
 * knowledge: which table to query, which columns exist, and what stat names are
 * valid. This means PlayerStatQuerier contains zero MLB-specific (or any other
 * sport-specific) code — the same instance handles queries for any league.
 *
 * ── SQL Injection Protection ─────────────────────────────────────────────────
 * statName is validated against statUpdater.getSupportedStats() (a hardcoded,
 * immutable Set in each StatUpdater subclass) before use. The column name is
 * then derived via statUpdater.toColumnName() — a deterministic transform of a
 * whitelisted input — and embedded in SQL. The table name comes from
 * statUpdater.getStatsTable(), also a hardcoded constant. No user input ever
 * reaches SQL as a column name or table name.
 */
public class PlayerStatQuerier {

    /**
     * Retrieves the value of one specific stat for a given player.
     * Returns the most recent year's value (latest season) for that stat.
     *
     * @param playerName   The name/ID of the player to query.
     * @param statName     The camelCase stat name (e.g. "homeRuns", "points").
     * @param statUpdater  The league-specific StatUpdater — supplies table name,
     *                     column name, and the valid stat whitelist.
     * @return             The stat value as a string, or an error message.
     */
    public String getPlayerStat(String playerName, String statName, StatUpdater statUpdater) {

        if (playerName == null || playerName.isBlank()) {
            return "Error: player name cannot be null or empty.";
        }
        if (statName == null || statName.isBlank()) {
            return "Error: stat name cannot be null or empty.";
        }

        // Validate against the league's whitelist of camelCase stat names.
        if (!statUpdater.getSupportedStats().contains(statName)) {
            return "Error: stat category '" + statName
                    + "' is not defined for this league. Valid stats: "
                    + statUpdater.getSupportedStats();
        }

        // Convert to the snake_case column name used in the DB table.
        // Safe to embed in SQL: derived from a whitelisted, hardcoded value.
        String colName = statUpdater.toColumnName(statName);
        String table   = statUpdater.getStatsTable();

        String sql = String.format(
                "SELECT ps.%s, ps.year " +
                "FROM %s ps " +
                "JOIN players p ON ps.player_id = p.id " +
                "WHERE p.name = ? " +
                "ORDER BY ps.year DESC " +
                "LIMIT 1",
                colName, table);

        try (Connection conn = DBConnectionManager.getReadConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return "Error: player '" + playerName + "' does not exist in the system.";
                }
                return String.format("Player: %s | Stat: %s | Value: %s | Year: %d",
                        playerName,
                        statName,
                        rs.getString(colName),
                        rs.getInt("year"));
            }

        } catch (SQLException e) {
            System.err.println("[PlayerStatQuerier.getPlayerStat] DB error: " + e.getMessage());
            return "Error: database query failed for player '" + playerName
                    + "', stat '" + statName + "'. Details: " + e.getMessage();
        }
    }

    /**
     * Retrieves ALL statistics for a given player, across all available years.
     * Results are ordered by year (most recent first).
     *
     * The SELECT column list is built dynamically from the StatUpdater's
     * supported stats, so this method works for any league without modification.
     *
     * @param playerName   The name/ID of the player to query.
     * @param statUpdater  The league-specific StatUpdater.
     * @return             A formatted multi-line string of all stats and years,
     *                     or an error message if the player doesn't exist.
     */
    public String getAllPlayerStats(String playerName, StatUpdater statUpdater) {

        if (playerName == null || playerName.isBlank()) {
            return "Error: player name cannot be null or empty.";
        }

        String table = statUpdater.getStatsTable();

        // Build the column list from the league's supported stats, sorted
        // alphabetically for consistent output order.
        List<String> sortedStats = new ArrayList<>(statUpdater.getSupportedStats());
        Collections.sort(sortedStats);

        StringBuilder colList = new StringBuilder();
        for (String stat : sortedStats) {
            colList.append("ps.").append(statUpdater.toColumnName(stat)).append(", ");
        }
        // Remove trailing ", "
        colList.setLength(colList.length() - 2);

        String sql = String.format(
                "SELECT ps.year, %s, p.name, p.position " +
                "FROM %s ps " +
                "JOIN players p ON ps.player_id = p.id " +
                "WHERE p.name = ? " +
                "ORDER BY ps.year DESC",
                colList, table);

        try (Connection conn = DBConnectionManager.getReadConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerName);

            try (ResultSet rs = stmt.executeQuery()) {
                StringBuilder result = new StringBuilder();
                boolean found = false;

                while (rs.next()) {
                    if (!found) {
                        result.append("=== Stats for ")
                              .append(rs.getString("name"))
                              .append(" (").append(rs.getString("position")).append(") ===\n");
                        found = true;
                    }

                    result.append(String.format("  Year: %d |", rs.getInt("year")));
                    for (String stat : sortedStats) {
                        result.append(" ").append(stat)
                              .append("=").append(rs.getString(statUpdater.toColumnName(stat)))
                              .append(",");
                    }
                    // Remove trailing comma
                    result.setLength(result.length() - 1);
                    result.append("\n");
                }

                if (!found) {
                    return "Error: player '" + playerName + "' does not exist in the system.";
                }

                return result.toString().trim();
            }

        } catch (SQLException e) {
            System.err.println("[PlayerStatQuerier.getAllPlayerStats] DB error: " + e.getMessage());
            return "Error: database query failed for player '" + playerName
                    + "'. Details: " + e.getMessage();
        }
    }
}