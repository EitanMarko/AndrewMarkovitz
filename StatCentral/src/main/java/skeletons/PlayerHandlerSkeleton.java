package skeletons;

/**
 * PlayerHandler
 *
 * A microservice object held by a WorkerServer. Responsible for handling all
 * player creation and deletion operations against the underlying database.
 * Validates that provided player data references a defined league structure
 * before writing to the database.
 */
public class PlayerHandlerSkeleton {

    /**
     * Creates a new player profile in the underlying database using the provided
     * player data. Validates that all referenced league structure fields (e.g. team,
     * division, conference) exist in the system before creating the profile.
     *
     * @param playerData    An object containing all required fields for the new player
     *                      (e.g. name, position, team, division, conference) as well
     *                      as any initial stat values
     * @return              A success message confirming the player was created,
     *                      or an error message if any referenced field (e.g. team name)
     *                      does not exist in the defined league structure
     */
    public String createPlayer(PlayerDataSkeleton playerData) {return null;}

    /**
     * Deletes an existing player profile from the underlying database
     * identified by the given player ID.
     *
     * @param playerName  The name of the player to be deleted
     * @return            A success message confirming the player was deleted,
     *                    or an error message if no player with the given ID exists
     */
    public String deletePlayer(String playerName) {return null;}
}