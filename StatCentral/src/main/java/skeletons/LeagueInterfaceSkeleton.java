package skeletons;

import java.util.concurrent.CompletableFuture;

/**
 * LeagueInterface
 *
 * The interface through which a sports league interacts with the StatCentral system.
 * Provides methods for managing players and the league's hierarchical structure.
 * All methods asynchronously send HTTP requests to the GatewayServer and return
 * the result of the HTTP response back to the caller.
 */
public class LeagueInterfaceSkeleton {

    /**
     * Asynchronously sends a request to the GatewayServer to create a new player
     * profile in the system with the provided player details.
     *
     * @param playerData  An object containing all required fields for the new player
     *                    (e.g. name, position, team, conference, division)
     * @return            The response from the GatewayServer confirming success,
     *                    or an error message if the request failed (e.g. team not defined)
     */
    public CompletableFuture<String> createPlayer(PlayerDataSkeleton playerData) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to create a new conference
     * in the league structure.
     *
     * @param conferenceName  The name of the new conference to be created
     * @return                The response from the GatewayServer confirming the conference
     *                        was created, or an error message if a conference with that
     *                        name already exists
     */
    public CompletableFuture<String> createConference(String conferenceName) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to create a new division
     * within an existing conference in the league structure.
     *
     * @param divisionName    The name of the new division to be created
     * @param conferenceName  The name of the existing conference under which the
     *                        new division will be placed
     * @return                The response from the GatewayServer confirming the division
     *                        was created, or an error message if the specified conference
     *                        does not exist or a division with that name already exists within it
     */
    public CompletableFuture<String> createDivision(String divisionName, String conferenceName) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to create a new team
     * within an existing division in the league structure.
     *
     * @param teamName      The name of the new team to be created
     * @param divisionName  The name of the existing division under which the
     *                      new team will be placed
     * @return              The response from the GatewayServer confirming the team
     *                      was created, or an error message if the specified division
     *                      does not exist or a team with that name already exists within it
     */
    public CompletableFuture<String> createTeam(String teamName, String divisionName) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to delete an existing
     * conference from the league structure. Deleting a conference also permanently
     * deletes all divisions within that conference, all teams within those divisions,
     * and all player profiles belonging to those teams.
     *
     * @param conferenceName  The name of the conference to be deleted
     * @return                The response from the GatewayServer confirming the conference
     *                        and all of its contents were deleted, or an error message if
     *                        the conference does not exist
     */
    public CompletableFuture<String> deleteConference(String conferenceName) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to delete an existing
     * division from the league structure. Deleting a division also permanently
     * deletes all teams within that division and all player profiles belonging
     * to those teams.
     *
     * @param divisionName  The name of the division to be deleted
     * @return              The response from the GatewayServer confirming the division
     *                      and all of its contents were deleted, or an error message if
     *                      the division does not exist
     */
    public CompletableFuture<String> deleteDivision(String divisionName) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to delete an existing
     * team from the league structure. Deleting a team also permanently deletes
     * all player profiles belonging to that team.
     *
     * @param teamName  The name of the team to be deleted
     * @return          The response from the GatewayServer confirming the team
     *                  and all of its players were deleted, or an error message
     *                  if the team does not exist
     */
    public CompletableFuture<String> deleteTeam(String teamName) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to delete an existing
     * player profile from the system.
     *
     * @param playerId    The unique identifier of the player to be deleted
     * @return            The response from the GatewayServer confirming deletion,
     *                    or an error message if the player was not found
     */
    public CompletableFuture<String> deletePlayer(String playerId) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to move a player
     * from their current team to a new team within the league structure.
     *
     * @param playerId    The unique identifier of the player to be moved
     * @param newTeam     The name of the team to move the player to
     * @return            The response from the GatewayServer confirming the move,
     *                    or an error message if the player or team does not exist
     */

    public CompletableFuture<String> movePlayerToTeam(String playerId, String newTeam) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to move a team
     * from its current division to a new division within the league structure.
     *
     * @param teamName      The name of the team to be moved
     * @param newDivision   The name of the division to move the team to
     * @return              The response from the GatewayServer confirming the move,
     *                      or an error message if the team or division does not exist
     */
    public CompletableFuture<String> moveTeamToDivision(String teamName, String newDivision) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to move a division
     * from its current conference to a new conference within the league structure.
     *
     * @param divisionName    The name of the division to be moved
     * @param newConference   The name of the conference to move the division to
     * @return                The response from the GatewayServer confirming the move,
     *                        or an error message if the division or conference does not exist
     */
    public CompletableFuture<String> moveDivisionToConference(String divisionName, String newConference) {return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to update a specific
     * statistical value for a given player.
     *
     * @param playerId    The unique identifier of the player whose stat is being updated
     * @param statName    The name of the statistical category to update (e.g. "homeRuns")
     * @param value       The value to apply to the update (e.g. increment amount or set value)
     * @return            The response from the GatewayServer confirming the update,
     *                    or an error message if the player or stat category does not exist
     */
    public CompletableFuture<String> updatePlayerStat(String playerId, String statName, int value) {return null;}
}
