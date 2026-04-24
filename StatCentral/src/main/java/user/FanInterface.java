package user;

/**
 * FanInterface
 *
 * The interface through which fans interact with the StatCentral system.
 * Provides methods for querying individual player statistics and performing
 * aggregate stat queries filtered by league structure and year.
 *
 * All methods send HTTP requests to the GatewayServer and block until the
 * HTTP response is received, returning the result as a String. The response
 * will be either a success payload (e.g. a formatted stat result or ranked
 * player list) or a descriptive error message if the request failed.
 *
 * NOTE ON RETURN TYPE:
 *   Methods return String directly (synchronous/blocking) rather than
 *   CompletableFuture<String> (asynchronous). The HTTP call is still made
 *   over the network; the caller simply blocks on the current thread until
 *   the response arrives. If non-blocking behavior is desired at the call
 *   site, callers can wrap any of these methods themselves:
 *     CompletableFuture.supplyAsync(() -> fanInterface.getPlayerStats(id))
 */
public interface FanInterface {

    /**
     * Sends a request to the GatewayServer to retrieve all statistics
     * for a specific player, across all available seasons, and returns
     * the result when the HTTP response is received.
     *
     * Corresponds to: PlayerStatQuerier.getAllPlayerStats(playerName)
     *
     * @param playerId    The unique identifier (name) of the player to query
     * @return            A formatted string containing all stats for the player
     *                    across all available seasons, or an error message if
     *                    the player does not exist in the system
     */
    String getPlayerStats(String playerId);

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
    String getPlayerStat(String playerId, String statName);

    /**
     * Sends a request to the GatewayServer to query the top N players
     * in a given statistical category, filtered by a specific level of the
     * league hierarchy and a specific year, and returns the result when the
     * HTTP response is received. Results are ranked in descending order.
     *
     * For example: query the top 10 players in the "AL" conference
     * in the "homeRuns" stat category in the year 2024.
     *
     * Corresponds to: AggregateStatQuerier.queryTopPlayersByStat(...)
     *
     * @param statName          The name of the statistical category to rank players by
     *                          (e.g. "homeRuns", "battingAverage")
     * @param leagueLevel       The level of the league hierarchy to filter by
     *                          (e.g. "conference", "division", "team")
     * @param leagueLevelValue  The specific name of the conference, division, or team
     *                          to filter within (e.g. "AL", "East", "Yankees")
     * @param topN              The number of top players to return
     * @param year              The year for which to query statistics
     * @return                  A ranked list of the top N players and their stat values,
     *                          or an error message if the stat name, league level value,
     *                          or year is not defined in the system
     */
    String queryTopPlayersByStat(String statName, String leagueLevel,
                                 String leagueLevelValue, int topN, int year);
}
