package microservices;

import database.DBConnectionManager;
import java.sql.*;

/**
 * LeagueStructureManager
 *
 * A microservice object held by a WorkerServer. Responsible for managing the
 * hierarchical league structure (conferences → divisions → teams) stored in
 * the PostgreSQL database, and for moving entities within that structure.
 *
 * ── Connection Routing ───────────────────────────────────────────────────────
 * ALL operations here are writes (INSERT, UPDATE, DELETE), so every method
 * uses DBConnectionManager.getWriteConnection() — routing through PgBouncer
 * to the PRIMARY PostgreSQL node.
 *
 * ── CASCADE DELETES ──────────────────────────────────────────────────────────
 * The database schema defines ON DELETE CASCADE relationships:
 *   conferences → divisions → teams → players → player_stats
 *
 * This means a single DELETE on the conferences table automatically cascades
 * down through all child tables. We do NOT need to manually delete divisions,
 * teams, players, and stats in separate statements — PostgreSQL handles it.
 *
 * ── UNIQUENESS CONSTRAINTS ───────────────────────────────────────────────────
 * The schema enforces:
 *   - Conference names are globally unique.
 *   - Division names are unique within a conference (same name allowed in diff conferences).
 *   - Team names are globally unique.
 *
 * If any uniqueness constraint is violated, the DB returns SQL state "23505"
 * (unique_violation), which we catch and convert into a readable error message.
 */
public class LeagueStructureManager {

    // =========================================================================
    // CREATE operations — issue INSERT statements
    // =========================================================================

    /**
     * Creates a new conference with the given name.
     *
     * @param conferenceName  The name for the new conference (must be unique).
     * @return                Success or error message.
     */
    public String createConference(String conferenceName) {
        if (conferenceName == null || conferenceName.isBlank()) {
            return "Error: conference name cannot be null or empty.";
        }

        String sql = "INSERT INTO conferences (name) VALUES (?)";

        try (Connection conn = DBConnectionManager.getWriteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conferenceName);
            stmt.executeUpdate();
            conn.commit();

            return "Success: conference '" + conferenceName + "' has been created.";

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                // unique_violation — a conference with this name already exists
                return "Error: a conference named '" + conferenceName + "' already exists.";
            }
            System.err.println("[LeagueStructureManager.createConference] DB error: " + e.getMessage());
            return "Error: failed to create conference '" + conferenceName + "'. Details: " + e.getMessage();
        }
    }

    /**
     * Creates a new division within an existing conference.
     *
     * @param divisionName    The name for the new division.
     * @param conferenceName  The name of the parent conference (must already exist).
     * @return                Success or error message.
     */
    public String createDivision(String divisionName, String conferenceName) {
        if (divisionName == null || divisionName.isBlank()) {
            return "Error: division name cannot be null or empty.";
        }
        if (conferenceName == null || conferenceName.isBlank()) {
            return "Error: conference name cannot be null or empty.";
        }

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            // Step 1: Resolve the conference's ID.
            // We must look up the ID rather than using the name as a foreign key,
            // because the divisions table references conferences by integer ID.
            int confId = lookupId(conn, "conferences", conferenceName);
            if (confId == -1) {
                conn.rollback();
                return "Error: conference '" + conferenceName + "' does not exist.";
            }

            // Step 2: Insert the division.
            String sql = "INSERT INTO divisions (name, conference_id) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, divisionName);
                stmt.setInt(2, confId);
                stmt.executeUpdate();
            }

            conn.commit();
            return "Success: division '" + divisionName + "' created in conference '"
                    + conferenceName + "'.";

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                return "Error: a division named '" + divisionName
                        + "' already exists in conference '" + conferenceName + "'.";
            }
            System.err.println("[LeagueStructureManager.createDivision] DB error: " + e.getMessage());
            return "Error: failed to create division '" + divisionName + "'. Details: " + e.getMessage();
        }
    }

    /**
     * Creates a new team within an existing division.
     *
     * @param teamName      The name for the new team (must be globally unique).
     * @param divisionName  The name of the parent division (must already exist).
     * @return              Success or error message.
     */
    public String createTeam(String teamName, String divisionName) {
        if (teamName == null || teamName.isBlank()) {
            return "Error: team name cannot be null or empty.";
        }
        if (divisionName == null || divisionName.isBlank()) {
            return "Error: division name cannot be null or empty.";
        }

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            // Resolve the division's ID
            int divId = lookupId(conn, "divisions", divisionName);
            if (divId == -1) {
                conn.rollback();
                return "Error: division '" + divisionName + "' does not exist.";
            }

            String sql = "INSERT INTO teams (name, division_id) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, teamName);
                stmt.setInt(2, divId);
                stmt.executeUpdate();
            }

            conn.commit();
            return "Success: team '" + teamName + "' created in division '" + divisionName + "'.";

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                return "Error: a team named '" + teamName + "' already exists in the system.";
            }
            System.err.println("[LeagueStructureManager.createTeam] DB error: " + e.getMessage());
            return "Error: failed to create team '" + teamName + "'. Details: " + e.getMessage();
        }
    }

    // =========================================================================
    // DELETE operations — issue DELETE statements (cascade handled by schema)
    // =========================================================================

    /**
     * Deletes a conference and all of its contents.
     *
     * Thanks to ON DELETE CASCADE in the schema, a single DELETE on the
     * conferences table automatically removes:
     *   - All divisions in the conference
     *   - All teams in those divisions
     *   - All players on those teams
     *   - All stat rows for those players
     *
     * @param conferenceName  The name of the conference to delete.
     * @return                Success or error message.
     */
    public String deleteConference(String conferenceName) {
        return deleteByName("conferences", conferenceName,
                "conference '" + conferenceName + "' and all of its divisions, teams, "
                        + "players, and statistics have been deleted.");
    }

    /**
     * Deletes a division and all of its contents (teams, players, stats).
     *
     * @param divisionName  The name of the division to delete.
     * @return              Success or error message.
     */
    public String deleteDivision(String divisionName) {
        return deleteByName("divisions", divisionName,
                "division '" + divisionName + "' and all of its teams, players, "
                        + "and statistics have been deleted.");
    }

    /**
     * Deletes a team and all of its players (and their stats).
     *
     * @param teamName  The name of the team to delete.
     * @return          Success or error message.
     */
    public String deleteTeam(String teamName) {
        return deleteByName("teams", teamName,
                "team '" + teamName + "' and all of its players and statistics have been deleted.");
    }

    // =========================================================================
    // MOVE operations — issue UPDATE statements to change parent references
    // =========================================================================

    /**
     * Moves a player to a different team by updating their team_id foreign key.
     *
     * @param playerId  The name (unique ID) of the player to move.
     * @param newTeam   The name of the destination team (must exist).
     * @return          Success or error message.
     */
    public String movePlayerToTeam(String playerId, String newTeam) {
        if (playerId == null || playerId.isBlank()) {
            return "Error: player ID cannot be null or empty.";
        }
        if (newTeam == null || newTeam.isBlank()) {
            return "Error: target team name cannot be null or empty.";
        }

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            // Resolve the destination team's ID
            int teamId = lookupId(conn, "teams", newTeam);
            if (teamId == -1) {
                conn.rollback();
                return "Error: team '" + newTeam + "' does not exist.";
            }

            // UPDATE the player's team_id to point to the new team.
            // We match players by name (which serves as their unique identifier).
            String sql = "UPDATE players SET team_id = ? WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, teamId);
                stmt.setString(2, playerId);
                int rows = stmt.executeUpdate();

                if (rows == 0) {
                    conn.rollback();
                    return "Error: player '" + playerId + "' does not exist.";
                }
            }

            conn.commit();
            return "Success: player '" + playerId + "' has been moved to team '" + newTeam + "'.";

        } catch (SQLException e) {
            System.err.println("[LeagueStructureManager.movePlayerToTeam] DB error: " + e.getMessage());
            return "Error: failed to move player '" + playerId + "'. Details: " + e.getMessage();
        }
    }

    /**
     * Moves a team to a different division by updating its division_id foreign key.
     *
     * @param teamName     The name of the team to move.
     * @param newDivision  The name of the destination division (must exist).
     * @return             Success or error message.
     */
    public String moveTeamToDivision(String teamName, String newDivision) {
        if (teamName == null || teamName.isBlank()) {
            return "Error: team name cannot be null or empty.";
        }
        if (newDivision == null || newDivision.isBlank()) {
            return "Error: target division name cannot be null or empty.";
        }

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            int divId = lookupId(conn, "divisions", newDivision);
            if (divId == -1) {
                conn.rollback();
                return "Error: division '" + newDivision + "' does not exist.";
            }

            String sql = "UPDATE teams SET division_id = ? WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, divId);
                stmt.setString(2, teamName);
                int rows = stmt.executeUpdate();

                if (rows == 0) {
                    conn.rollback();
                    return "Error: team '" + teamName + "' does not exist.";
                }
            }

            conn.commit();
            return "Success: team '" + teamName + "' has been moved to division '" + newDivision + "'.";

        } catch (SQLException e) {
            System.err.println("[LeagueStructureManager.moveTeamToDivision] DB error: " + e.getMessage());
            return "Error: failed to move team '" + teamName + "'. Details: " + e.getMessage();
        }
    }

    /**
     * Moves a division to a different conference by updating its conference_id foreign key.
     *
     * @param divisionName   The name of the division to move.
     * @param newConference  The name of the destination conference (must exist).
     * @return               Success or error message.
     */
    public String moveDivisionToConference(String divisionName, String newConference) {
        if (divisionName == null || divisionName.isBlank()) {
            return "Error: division name cannot be null or empty.";
        }
        if (newConference == null || newConference.isBlank()) {
            return "Error: target conference name cannot be null or empty.";
        }

        try (Connection conn = DBConnectionManager.getWriteConnection()) {
            conn.setAutoCommit(false);

            int confId = lookupId(conn, "conferences", newConference);
            if (confId == -1) {
                conn.rollback();
                return "Error: conference '" + newConference + "' does not exist.";
            }

            String sql = "UPDATE divisions SET conference_id = ? WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, confId);
                stmt.setString(2, divisionName);
                int rows = stmt.executeUpdate();

                if (rows == 0) {
                    conn.rollback();
                    return "Error: division '" + divisionName + "' does not exist.";
                }
            }

            conn.commit();
            return "Success: division '" + divisionName
                    + "' has been moved to conference '" + newConference + "'.";

        } catch (SQLException e) {
            System.err.println("[LeagueStructureManager.moveDivisionToConference] DB error: "
                    + e.getMessage());
            return "Error: failed to move division '" + divisionName + "'. Details: " + e.getMessage();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Shared helper for deleteConference, deleteDivision, deleteTeam.
     * Issues a DELETE by name against the specified table and returns a
     * success or "not found" error message.
     *
     * IMPORTANT: This only works because conference names, team names, and
     * division names are all stored in a column named 'name' in their respective
     * tables. This is a deliberate schema consistency choice.
     *
     * @param tableName      The table to delete from ("conferences", "divisions", "teams")
     * @param entityName     The value of the 'name' column to match
     * @param successMessage The message to return on success
     * @return               Success or error message
     */
    private String deleteByName(String tableName, String entityName, String successMessage) {
        if (entityName == null || entityName.isBlank()) {
            return "Error: " + tableName.substring(0, tableName.length() - 1)
                    + " name cannot be null or empty.";
        }

        // SAFE to use String.format here for the table name because tableName
        // is NEVER user-supplied — it is always one of the three hardcoded strings
        // passed from the public methods above. Column/table names cannot be
        // parameterized with PreparedStatement, so we must use string concatenation,
        // but we must ensure the table name is not user-controlled to prevent injection.
        String sql = String.format("DELETE FROM %s WHERE name = ?", tableName);

        try (Connection conn = DBConnectionManager.getWriteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, entityName);
            int rows = stmt.executeUpdate();
            conn.commit();

            if (rows == 0) {
                return "Error: " + tableName.substring(0, tableName.length() - 1)
                        + " '" + entityName + "' does not exist.";
            }

            return "Success: " + successMessage;

        } catch (SQLException e) {
            System.err.println("[LeagueStructureManager.deleteByName] DB error: " + e.getMessage());
            return "Error: failed to delete '" + entityName + "' from " + tableName
                    + ". Details: " + e.getMessage();
        }
    }

    /**
     * Looks up the integer primary key (id) of a row identified by its name.
     * Returns -1 if no matching row is found.
     *
     * Used by create and move operations that need to resolve a name to an ID
     * before issuing an INSERT or UPDATE with a foreign key reference.
     *
     * @param conn        An open database connection (reused from the caller's transaction)
     * @param tableName   The table to query ("conferences", "divisions", or "teams")
     * @param entityName  The value of the 'name' column to look up
     * @return            The integer ID if found, or -1 if not found
     */
    private int lookupId(Connection conn, String tableName, String entityName) throws SQLException {
        // tableName is always a hardcoded value from within this class — safe to format.
        String sql = String.format("SELECT id FROM %s WHERE name = ?", tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entityName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("id") : -1;
            }
        }
    }
}
