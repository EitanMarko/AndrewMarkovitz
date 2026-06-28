package microservices;

import database.DBConnectionManager;
import java.sql.*;

/**
 * PlayerHandler
 *
 * A microservice object held by a WorkerServer. Responsible for creating and
 * deleting player records in the PostgreSQL database.
 *
 * ── Connection Routing ───────────────────────────────────────────────────────
 * Both operations here are WRITES (INSERT, DELETE), so this microservice
 * always uses DBConnectionManager.getWriteConnection() — which routes through
 * PgBouncer to the PRIMARY PostgreSQL node.
 *
 * ── createPlayer: Two-Step Read-Then-Write ───────────────────────────────────
 * Before inserting a player, we verify that their team exists. This is a
 * "read-then-write" pattern. There is a small TOCTOU (Time-Of-Check-Time-Of-Use)
 * race condition: if the team is deleted between our check and our INSERT, the
 * INSERT will fail with a foreign key violation. We catch that exception and
 * return an appropriate error message.
 *
 * ── deletePlayer ─────────────────────────────────────────────────────────────
 * A simple DELETE by player name. The player_stats table has ON DELETE CASCADE
 * in the schema, so all stat rows for the deleted player are automatically
 * removed without needing a separate DELETE statement.
 */
public class PlayerHandler {

    /**
     * Creates a new player profile in the database.
     *
     * Steps:
     *  1. Validate that required fields in playerData are non-null/non-empty.
     *  2. Check that the referenced team exists in the teams table.
     *  3. INSERT the player into the players table.
     *  4. INSERT an initial stats row into player_stats for the given year.
     *  5. Commit the transaction (steps 3+4 are atomic).
     *
     * @param playerData  All fields needed to create the player record.
     * @return            Success message, or a descriptive error message.
     */
    public String createPlayer(PlayerData playerData, StatUpdater statUpdater) {

        // --- Step 1: Validate required fields ---
        // We check these here in the microservice layer as a last line of defense,
        // even though the GatewayServer should have caught empty fields earlier.
        if (playerData == null) {
            return "Error: playerData cannot be null.";
        }
        if (playerData.getName() == null || playerData.getName().isBlank()) {
            return "Error: player name is required.";
        }
        if (playerData.getTeamName() == null || playerData.getTeamName().isBlank()) {
            return "Error: team name is required.";
        }
        if (playerData.getPosition() == null || playerData.getPosition().isBlank()) {
            return "Error: position is required.";
        }

        // All writes use the write connection (→ PgBouncer → PostgreSQL primary)
        // The try-with-resources block automatically closes (returns to pool) the
        // connection when we exit the block, even if an exception is thrown.
        try (Connection conn = DBConnectionManager.getWriteConnection()) {

            // Wrap both INSERT statements in a single transaction so that either
            // both succeed or neither does. We don't want a player row without
            // a corresponding stats row, or vice versa.
            conn.setAutoCommit(false);

            // --- Step 2: Verify the team exists ---
            // We use a PreparedStatement with a '?' placeholder to safely pass
            // the team name. This prevents SQL injection attacks, where a
            // malicious team name like "'; DROP TABLE teams; --" could
            // otherwise corrupt or destroy the database.
            int teamId;
            String checkTeamSQL = "SELECT id FROM teams WHERE name = ?";
            try (PreparedStatement checkTeam = conn.prepareStatement(checkTeamSQL)) {
                checkTeam.setString(1, playerData.getTeamName());
                try (ResultSet rs = checkTeam.executeQuery()) {
                    if (!rs.next()) {
                        // Team not found — roll back and return an error.
                        conn.rollback();
                        return "Error: team '" + playerData.getTeamName()
                                + "' does not exist in the league structure. "
                                + "Please create the team before adding players to it.";
                    }
                    teamId = rs.getInt("id");
                }
            }

            // --- Step 3: Insert the player record ---
            // We use RETURNING id to get the newly assigned player ID in a single
            // round-trip, rather than doing a separate SELECT after the insert.
            // league_id is stored so fan queries can route to the correct stats table.
            String insertPlayerSQL =
                    "INSERT INTO players (name, position, team_id, league_id) VALUES (?, ?, ?, ?) RETURNING id";

            int newPlayerId;
            try (PreparedStatement insertPlayer = conn.prepareStatement(insertPlayerSQL)) {
                insertPlayer.setString(1, playerData.getName());
                insertPlayer.setString(2, playerData.getPosition());
                insertPlayer.setInt(3, teamId);
                insertPlayer.setString(4, playerData.getLeagueId());

                try (ResultSet rs = insertPlayer.executeQuery()) {
                    rs.next();
                    newPlayerId = rs.getInt(1);
                }
            }

            // --- Step 4: Insert the initial stats row ---
            // Delegates to the league-specific StatUpdater so PlayerHandler never
            // needs to know which stats table or columns the league uses.
            // Both inserts (player + stats) are in the same transaction.
            statUpdater.doCreateInitialStats(conn, newPlayerId, playerData);

            // --- Step 5: Commit the transaction ---
            // Both inserts succeed atomically. The player row and their stats row
            // are now visible to other connections.
            conn.commit();

            return "Success: player '" + playerData.getName() + "' created successfully "
                    + "on team '" + playerData.getTeamName() + "'.";

        } catch (SQLException e) {
            // Check the SQL state to identify specific error types.
            // PostgreSQL error code "23505" = unique_violation (duplicate key).
            if ("23505".equals(e.getSQLState())) {
                return "Error: a player named '" + playerData.getName()
                        + "' already exists in the system.";
            }
            // PostgreSQL error code "23503" = foreign_key_violation.
            // This catches the TOCTOU race condition where the team was deleted
            // between our check (Step 2) and our INSERT (Step 3).
            if ("23503".equals(e.getSQLState())) {
                return "Error: the team '" + playerData.getTeamName()
                        + "' was removed from the system during this operation. "
                        + "Please try again.";
            }
            // Unexpected database error — log and propagate a generic message.
            System.err.println("[PlayerHandler.createPlayer] DB error: " + e.getMessage());
            return "Error: database operation failed while creating player '"
                    + playerData.getName() + "'. Details: " + e.getMessage();
        }
    }

    /**
     * Deletes an existing player record from the database.
     *
     * The player_stats table has ON DELETE CASCADE in the schema, so all
     * associated stat rows are automatically deleted. No additional DELETE
     * statements are needed.
     *
     * @param playerName  The name (unique identifier) of the player to delete.
     * @return            Success message, or a descriptive error message.
     */
    public String deletePlayer(String playerName) {

        // --- Validate input ---
        // We check at the WorkerServer level too, but validate here as a safeguard.
        if (playerName == null || playerName.isBlank()) {
            return "Error: player name cannot be null or empty.";
        }

        String deleteSQL = "DELETE FROM players WHERE name = ?";

        try (Connection conn = DBConnectionManager.getWriteConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {

            stmt.setString(1, playerName);

            // executeUpdate() returns the number of rows affected.
            // For a DELETE by unique name, this is either 1 (deleted) or 0 (not found).
            int rowsAffected = stmt.executeUpdate();
            conn.commit();

            if (rowsAffected == 0) {
                // No row matched the name — the player doesn't exist.
                return "Error: player '" + playerName + "' does not exist in the system.";
            }

            return "Success: player '" + playerName
                    + "' and all associated statistics have been deleted.";

        } catch (SQLException e) {
            System.err.println("[PlayerHandler.deletePlayer] DB error: " + e.getMessage());
            return "Error: database operation failed while deleting player '"
                    + playerName + "'. Details: " + e.getMessage();
        }
    }
}
