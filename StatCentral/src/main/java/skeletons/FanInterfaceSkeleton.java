package skeletons;

import java.util.concurrent.CompletableFuture;

/**
 * FanInterface
 *
 * The interface through which fans interact with the StatCentral system.
 * Provides methods for querying individual player statistics and performing
 * aggregate stat queries filtered by league structure and year.
 * All methods asynchronously send HTTP requests to the GatewayServer and return
 * the result of the HTTP response back to the caller.
 */
public class FanInterfaceSkeleton {

    /**
     * Asynchronously sends a request to the GatewayServer to retrieve
     * the statistics for a specific player.
     *
     * @param playerId    The unique identifier of the player whose stats are being requested
     * @return            The response from the GatewayServer containing the player's stats,
     *                    or an error message if the player does not exist
     */
    public CompletableFuture<String> getPlayerStats(String playerId) {return null;}

    /**
     * Sends a request to the GatewayServer to retrieve the value of one
     * specific statistical category for a given player (most recent season),
     * and returns the result when the HTTP response is received.
     *
     * Corresponds to: PlayerStatQuerier.getPlayerStat(playerName, statName)
     *
     * For example: retrieve Aaron Judge's "homeRuns" value for the current season.
     *
     * @param playerId    The unique identifier (name) of the player to query
     * @param statName    The name of the statistical category to retrieve
     *                    (e.g. "homeRuns", "battingAverage"). Must be a stat
     *                    category defined in the system for this sport.
     * @return            The value of the requested stat for the given player,
     *                    or an error message if the player does not exist or
     *                    the stat category has not been defined in the system
     */
    public CompletableFuture<String> getPlayerStat(String playerId, String statName){return null;}

    /**
     * Asynchronously sends a request to the GatewayServer to query the top N players
     * in a given statistical category, filtered by a specific level of the league
     * hierarchy (e.g. conference, division, or team) and a specific year.
     *
     * For example: query the top 10 players in the "AL" conference
     * in the "homeRuns" stat category in the year 2024.
     *
     * @param statName          The name of the statistical category to rank players by
     *                          (e.g. "homeRuns", "battingAverage")
     * @param leagueLevel       The level of the league hierarchy to filter by
     *                          (e.g. "conference", "division", "team")
     * @param leagueLevelValue  The specific name of the conference, division, or team
     *                          to filter within (e.g. "AL", "East", "Yankees")
     * @param topN              The number of top players to return
     * @param year              The year for which to query statistics
     * @return                  The response from the GatewayServer containing the ranked
     *                          list of players, or an error message if the stat, league
     *                          level, or year is not defined in the system
     */
    public CompletableFuture<String> queryTopPlayersByStat(String statName, String leagueLevel,
                                                            String leagueLevelValue, int topN,
                                                            int year) {return null;}
}
