package skeletons;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * WorkerServer
 *
 * Receives routed requests from the LeaderServer and delegates them to the
 * appropriate microservice object it holds. Each microservice is an independent
 * object responsible for a specific category of operations against the underlying
 * database. Returns results back to the LeaderServer via TCP.
 *
 * Because StatCentral supports multiple leagues simultaneously — each with its
 * own sport-specific statistical categories — the WorkerServer maintains a registry
 * of StatUpdater instances keyed by league identifier (e.g. "MLB", "NFL", "NBA")
 * rather than holding a single StatUpdater. When an updatePlayerStat request
 * arrives, the WorkerServer extracts the leagueId from the Request object and uses
 * it to look up the correct StatUpdater subclass from the registry.
 *
 * All other microservices (PlayerHandler, LeagueStructureManager, PlayerStatQuerier,
 * AggregateStatQuerier) are shared across leagues, as league structure and player
 * record management follow the same logic regardless of sport. Those microservices
 * use the leagueId carried in the Request to scope their database queries to the
 * correct league's data where applicable.
 */
public class WorkerServerSkeleton {

    // Microservice instances shared across all leagues
    private PlayerHandlerSkeleton playerHandler;
    private LeagueStructureManagerSkeleton leagueStructureManager;
    private PlayerStatQuerierSkeleton playerStatQuerier;
    private AggregateStatQuerierSkeleton aggregateStatQuerier;

    /**
     * Registry of sport-specific StatUpdater instances, keyed by league identifier
     * (e.g. "MLB" → MLBStatUpdater instance, "NFL" → NFLStatUpdater instance).
     * Populated at WorkerServer startup via registerStatUpdater(). When an
     * updatePlayerStat request is received, the WorkerServer extracts the leagueId
     * from the Request object and uses it to look up the appropriate StatUpdater.
     */
    private Map<String, StatUpdaterSkeleton> statUpdaters;

    /**
     * Registers a sport-specific StatUpdater instance for a given league. Called
     * at WorkerServer startup for each league the platform supports. If a StatUpdater
     * is already registered for the given leagueId, it is replaced.
     *
     * @param leagueId      The unique identifier of the league (e.g. "MLB", "NFL").
     *                      Must match the leagueId carried in Request objects
     *                      originating from that league's interfaces.
     * @param statUpdater   The concrete StatUpdater subclass instance to register
     *                      for this league (e.g. an MLBStatUpdater for "MLB")
     */
    public void registerStatUpdater(String leagueId, StatUpdaterSkeleton statUpdater) {}

    /**
     * Receives an incoming request from the LeaderServer and determines which
     * microservice object should handle it based on the operation type. For
     * updatePlayerStat requests, also uses the leagueId field on the Request
     * object to select the correct StatUpdater from the registry.
     *
     * Returns an error message if the operation type is unrecognized, if the
     * leagueId on a stat update request does not match any registered StatUpdater,
     * or if the delegated microservice returns an error.
     *
     * @param request    The request object forwarded from the LeaderServer,
     *                   containing the operation type, leagueId, and associated
     *                   parameters
     * @return           The response retrieved from the appropriate microservice,
     *                   to be passed back up to the LeaderServer
     */
    public CompletableFuture<String> handleRequest(RequestSkeleton request) { return null; }

    /**
     * Asynchronously returns the result of a processed request back to the
     * LeaderServer via TCP.
     *
     * @param result    The result string retrieved from the delegated microservice,
     *                  either a success payload or an error message
     * @return          A future that completes when the result has been sent
     *                  back to the LeaderServer
     */
    public CompletableFuture<Void> returnResult(String result) { return null; }
}