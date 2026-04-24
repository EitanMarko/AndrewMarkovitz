package microservices;

import database.DBConnectionManager;
import java.sql.*;
import java.util.Set;

/**
 * AggregateStatQuerier
 *
 * A microservice object held by a WorkerServer. Responsible for performing
 * aggregate statistical queries across multiple players, filtered by a level
 * of the league hierarchy and a specific year, returning ranked results.
 *
 * Example query: "Top 10 players in the AL ranked by homeRuns in 2024."
 *
 * ── Connection Routing ───────────────────────────────────────────────────────
 * READ-ONLY queries. Uses DBConnectionManager.getReadConnection() — routing
 * through PgBouncer to the REPLICA PostgreSQL node.
 *
 * ── League-Agnostic Design ───────────────────────────────────────────────────
 * queryTopPlayersByStat() accepts a StatUpdater parameter that supplies all
 * league-specific knowledge: table name, column name mapping, and stat whitelist.
 * No sport-specific code lives here.
 *
 * ── SQL Injection Protection ─────────────────────────────────────────────────
 * statName is validated against statUpdater.getSupportedStats() before use.
 * The column name comes from statUpdater.toColumnName() (whitelisted input).
 * The table name comes from statUpdater.getStatsTable() (hardcoded in subclass).
 * leagueLevel is validated against VALID_LEAGUE_LEVELS; the WHERE expression is
 * resolved from a hardcoded switch, never from user input.
 * leagueLevelValue is safely parameterized with '?'.
 */
public class AggregateStatQuerier {

    private static final Set<String> VALID_LEAGUE_LEVELS = Set.of(
            "conference", "division", "team"
    );

    /**
     * Queries the database for the top N players in a given stat category,
     * filtered to a specific level of the league hierarchy and a specific year.
     * Results are ranked in descending order (highest value first).
     *
     * @param statName          The camelCase stat to rank by (e.g. "homeRuns", "points").
     * @param leagueLevel       The hierarchy level: "conference", "division", or "team".
     * @param leagueLevelValue  The specific name at that level (e.g. "AL", "East").
     * @param topN              How many players to return.
     * @param year              The season year to query.
     * @param statUpdater       The league-specific StatUpdater — supplies table name,
     *                          column mapping, and the valid stat whitelist.
     * @return                  A ranked list string, or an error message.
     */
    public String queryTopPlayersByStat(String statName, String leagueLevel,
                                         String leagueLevelValue, int topN, int year,
                                         StatUpdater statUpdater) {

        if (statName == null || statName.isBlank()) {
            return "Error: stat name cannot be null or empty.";
        }
        if (leagueLevel == null || leagueLevel.isBlank()) {
            return "Error: league level cannot be null or empty.";
        }
        if (leagueLevelValue == null || leagueLevelValue.isBlank()) {
            return "Error: league level value cannot be null or empty.";
        }
        if (topN <= 0) {
            return "Error: topN must be a positive integer (received " + topN + ").";
        }
        if (year <= 0) {
            return "Error: year must be a positive integer (received " + year + ").";
        }

        // Validate stat name against the league's whitelist.
        if (!statUpdater.getSupportedStats().contains(statName)) {
            return "Error: stat category '" + statName
                    + "' is not defined for this league. Valid stats: "
                    + statUpdater.getSupportedStats();
        }

        // Validate leagueLevel and resolve the WHERE clause table.column.
        String levelLower = leagueLevel.toLowerCase();
        if (!VALID_LEAGUE_LEVELS.contains(levelLower)) {
            return "Error: invalid league level '" + leagueLevel
                    + "'. Must be one of: " + VALID_LEAGUE_LEVELS;
        }
        String whereClause = resolveWhereClause(levelLower);

        // colName and tableName come from whitelisted/hardcoded sources — safe to embed.
        String colName  = statUpdater.toColumnName(statName);
        String table    = statUpdater.getStatsTable();

        String sql = String.format(
                "SELECT p.name, ps.%s AS stat_value, ps.year, " +
                "       t.name AS team, d.name AS division, c.name AS conference " +
                "FROM %s ps " +
                "JOIN players     p ON ps.player_id    = p.id " +
                "JOIN teams       t ON p.team_id        = t.id " +
                "JOIN divisions   d ON t.division_id    = d.id " +
                "JOIN conferences c ON d.conference_id  = c.id " +
                "WHERE ps.year = ? " +
                "  AND %s = ? " +
                "ORDER BY ps.%s DESC " +
                "LIMIT ?",
                colName, table, whereClause, colName);

        try (Connection conn = DBConnectionManager.getReadConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, year);
            stmt.setString(2, leagueLevelValue);
            stmt.setInt(3, topN);

            try (ResultSet rs = stmt.executeQuery()) {
                StringBuilder result = new StringBuilder();
                int rank = 1;
                boolean hasResults = false;

                while (rs.next()) {
                    if (!hasResults) {
                        result.append(String.format(
                                "=== Top %d players by '%s' in %s '%s' (%d) ===\n",
                                topN, statName, leagueLevel, leagueLevelValue, year));
                        hasResults = true;
                    }
                    result.append(String.format(
                            "  #%d  %-25s  %s: %s  (Team: %s, Division: %s, Conference: %s)\n",
                            rank++,
                            rs.getString("name"),
                            statName,
                            rs.getString("stat_value"),
                            rs.getString("team"),
                            rs.getString("division"),
                            rs.getString("conference")
                    ));
                }

                if (!hasResults) {
                    return diagnoseEmptyResult(leagueLevel, leagueLevelValue, year, statName);
                }

                if (rank - 1 < topN) {
                    result.append(String.format(
                            "  (Note: only %d player(s) found — fewer than the requested top %d)\n",
                            rank - 1, topN));
                }

                return result.toString().trim();
            }

        } catch (SQLException e) {
            System.err.println("[AggregateStatQuerier.queryTopPlayersByStat] DB error: "
                    + e.getMessage());
            return "Error: database query failed. Details: " + e.getMessage();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String resolveWhereClause(String level) {
        switch (level) {
            case "conference": return "c.name";
            case "division":   return "d.name";
            case "team":       return "t.name";
            default:           return null;
        }
    }

    private String diagnoseEmptyResult(String leagueLevel, String leagueLevelValue,
                                        int year, String statName) {
        String tableName;
        switch (leagueLevel.toLowerCase()) {
            case "conference": tableName = "conferences"; break;
            case "division":   tableName = "divisions";   break;
            case "team":       tableName = "teams";       break;
            default:           return "Error: no results found.";
        }

        String checkExistsSQL = String.format("SELECT 1 FROM %s WHERE name = ?", tableName);

        try (Connection conn = DBConnectionManager.getReadConnection();
             PreparedStatement stmt = conn.prepareStatement(checkExistsSQL)) {

            stmt.setString(1, leagueLevelValue);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return "Error: " + leagueLevel + " '" + leagueLevelValue
                            + "' does not exist in the system.";
                }
            }
            return "Error: no statistical data found for " + leagueLevel
                    + " '" + leagueLevelValue + "', stat '" + statName
                    + "' in year " + year + ".";

        } catch (SQLException e) {
            return "Error: no results found and a follow-up check failed. Details: "
                    + e.getMessage();
        }
    }
}