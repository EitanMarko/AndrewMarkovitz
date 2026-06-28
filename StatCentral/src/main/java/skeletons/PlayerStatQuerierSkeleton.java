package skeletons;
/**
 * PlayerStatQuerier
 *
 * A microservice object held by a WorkerServer. Responsible for retrieving
 * statistics for individual players from the underlying database.
 */
public class PlayerStatQuerierSkeleton {

    /**
     * Retrieves the value of a specific statistical category for a given player
     * from the underlying database.
     *
     * @param playerName    The name of the player to query
     * @param statName    The name of the statistical category to retrieve
     *                    (e.g. "homeRuns", "battingAverage")
     * @return            The value of the requested stat for the given player,
     *                    or an error message if the player does not exist or the
     *                    stat category has not been defined in the system
     */
    public String getPlayerStat(String playerName, String statName) {return null;}

    /**
     * Retrieves all statistical values for a given player from the underlying database.
     *
     * @param playerName    The name of the player to query
     * @return            A formatted result containing all stats for the given player,
     *                    or an error message if the player does not exist
     */
    public String getAllPlayerStats(String playerName) {return null;}
}
