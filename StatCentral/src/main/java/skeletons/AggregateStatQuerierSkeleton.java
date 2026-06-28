package skeletons;
/**
 * AggregateStatQuerier
 *
 * A microservice object held by a WorkerServer. Responsible for performing
 * aggregate statistical queries across multiple players, filtered by a level
 * of the league hierarchy and a specific year, returning ranked results.
 */
public class AggregateStatQuerierSkeleton {

    /**
     * Queries the underlying database for the top N players in a given statistical
     * category, filtered to a specific level of the league hierarchy (e.g. conference,
     * division, or team) and a specific year. Results are ranked in descending order
     * by the specified stat.
     *
     * For example: retrieve the top 10 players in the "AL" conference ranked by
     * "homeRuns" for the year 2024.
     *
     * @param statName          The name of the statistical category to rank players by
     *                          (e.g. "homeRuns", "battingAverage")
     * @param leagueLevel       The level of the league hierarchy to filter by
     *                          (e.g. "conference", "division", "team")
     * @param leagueLevelValue  The specific name of the conference, division, or team
     *                          to filter within (e.g. "AL", "East", "Yankees")
     * @param topN              The number of top players to return in the result
     * @param year              The year for which to retrieve and rank statistics
     * @return                  A ranked list of the top N players and their stat values
     *                          matching the given filters, or an error message if the
     *                          stat category, league level value, or year is not defined
     *                          in the system
     */
    public String queryTopPlayersByStat(String statName, String leagueLevel,
                                         String leagueLevelValue, int topN, int year) {return null;}
}
