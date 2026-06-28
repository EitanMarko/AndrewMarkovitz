package user;
import microservices.PlayerData;

/**
 * LeagueInterface
 *
 * The interface through which a sports league interacts with the StatCentral system.
 * Provides methods for managing players, the league's hierarchical structure
 * (conferences, divisions, teams), and player statistics.
 *
 * All methods send HTTP requests to the GatewayServer and block until the
 * HTTP response is received, returning the result as a String. The response
 * will be either a success confirmation message or a descriptive error message
 * if the request could not be fulfilled.
 *
 * NOTE ON RETURN TYPE:
 *   Methods return String directly (synchronous/blocking) rather than
 *   CompletableFuture<String> (asynchronous). The HTTP call is still made
 *   over the network; the caller simply blocks on the current thread until
 *   the response arrives. If non-blocking behavior is desired at the call
 *   site, callers can wrap any of these methods themselves:
 *     CompletableFuture.supplyAsync(() -> leagueInterface.createTeam(name, div))
 */
public interface LeagueInterface {

    // =========================================================================
    // Player Management
    // =========================================================================

    /**
     * Sends a request to the GatewayServer to create a new player profile
     * in the system with the provided player details, and returns the result
     * when the HTTP response is received.
     *
     * The team referenced in playerData must already exist in the league
     * structure before this call is made; the system will reject the creation
     * with an error message if it does not.
     *
     * @param playerData  An object containing all required fields for the new player
     *                    (e.g. name, position, team) as well as any initial stat values
     * @return            A success message confirming the player was created,
     *                    or an error message if the request failed (e.g. team not defined,
     *                    player already exists)
     */
    String createPlayer(PlayerData playerData);

    /**
     * Sends a request to the GatewayServer to delete an existing player profile
     * from the system, and returns the result when the HTTP response is received.
     *
     * @param playerId    The unique identifier (name) of the player to be deleted
     * @return            A success message confirming the player was deleted,
     *                    or an error message if the player was not found
     */
    String deletePlayer(String playerId);

    /**
     * Sends a request to the GatewayServer to update a specific statistical
     * value for a given player, and returns the result when the HTTP response
     * is received.
     *
     * @param playerId    The unique identifier (name) of the player whose stat is being updated
     * @param statName    The name of the statistical category to update (e.g. "homeRuns").
     *                    Must be a stat category defined in the system for this sport.
     * @param value       The integer value to apply to the update (used as a direct set value
     *                    or as an increment amount, depending on the operation mode)
     * @return            A success message confirming the update was applied,
     *                    or an error message if the player or stat category does not exist,
     *                    or the value violates a constraint (e.g. negative)
     */
    String updatePlayerStat(String playerId, String statName, int value);

    // =========================================================================
    // League Structure — Create
    // =========================================================================

    /**
     * Sends a request to the GatewayServer to create a new conference in the
     * league structure, and returns the result when the HTTP response is received.
     *
     * @param conferenceName  The name of the new conference to be created
     * @return                A success message confirming the conference was created,
     *                        or an error message if a conference with that name already exists
     */
    String createConference(String conferenceName);

    /**
     * Sends a request to the GatewayServer to create a new division within an
     * existing conference in the league structure, and returns the result when
     * the HTTP response is received.
     *
     * @param divisionName    The name of the new division to be created
     * @param conferenceName  The name of the existing conference under which the
     *                        new division will be placed
     * @return                A success message confirming the division was created,
     *                        or an error message if the specified conference does not exist
     *                        or a division with that name already exists within it
     */
    String createDivision(String divisionName, String conferenceName);

    /**
     * Sends a request to the GatewayServer to create a new team within an existing
     * division in the league structure, and returns the result when the HTTP
     * response is received.
     *
     * @param teamName      The name of the new team to be created
     * @param divisionName  The name of the existing division under which the
     *                      new team will be placed
     * @return              A success message confirming the team was created,
     *                      or an error message if the specified division does not exist
     *                      or a team with that name already exists
     */
    String createTeam(String teamName, String divisionName);

    // =========================================================================
    // League Structure — Delete
    // =========================================================================

    /**
     * Sends a request to the GatewayServer to delete an existing conference from
     * the league structure, and returns the result when the HTTP response is received.
     *
     * Deleting a conference also permanently deletes all divisions within it,
     * all teams within those divisions, and all player profiles on those teams.
     *
     * @param conferenceName  The name of the conference to be deleted
     * @return                A success message confirming the conference and all of its
     *                        cascaded contents were deleted, or an error message if
     *                        the conference does not exist
     */
    String deleteConference(String conferenceName);

    /**
     * Sends a request to the GatewayServer to delete an existing division from
     * the league structure, and returns the result when the HTTP response is received.
     *
     * Deleting a division also permanently deletes all teams within it and all
     * player profiles belonging to those teams.
     *
     * @param divisionName  The name of the division to be deleted
     * @return              A success message confirming the division and all of its
     *                      cascaded contents were deleted, or an error message if
     *                      the division does not exist
     */
    String deleteDivision(String divisionName);

    /**
     * Sends a request to the GatewayServer to delete an existing team from the
     * league structure, and returns the result when the HTTP response is received.
     *
     * Deleting a team also permanently deletes all player profiles on that team.
     *
     * @param teamName  The name of the team to be deleted
     * @return          A success message confirming the team and all of its players
     *                  were deleted, or an error message if the team does not exist
     */
    String deleteTeam(String teamName);

    // =========================================================================
    // League Structure — Move
    // =========================================================================

    /**
     * Sends a request to the GatewayServer to move a player from their current
     * team to a different existing team, and returns the result when the HTTP
     * response is received.
     *
     * @param playerId    The unique identifier (name) of the player to be moved
     * @param newTeam     The name of the team to move the player to
     * @return            A success message confirming the player was moved,
     *                    or an error message if the player or target team does not exist
     */
    String movePlayerToTeam(String playerId, String newTeam);

    /**
     * Sends a request to the GatewayServer to move a team from its current
     * division to a different existing division, and returns the result when
     * the HTTP response is received.
     *
     * @param teamName      The name of the team to be moved
     * @param newDivision   The name of the division to move the team to
     * @return              A success message confirming the team was moved,
     *                      or an error message if the team or target division does not exist
     */
    String moveTeamToDivision(String teamName, String newDivision);

    /**
     * Sends a request to the GatewayServer to move a division from its current
     * conference to a different existing conference, and returns the result when
     * the HTTP response is received.
     *
     * @param divisionName    The name of the division to be moved
     * @param newConference   The name of the conference to move the division to
     * @return                A success message confirming the division was moved,
     *                        or an error message if the division or target conference
     *                        does not exist
     */
    String moveDivisionToConference(String divisionName, String newConference);
}
