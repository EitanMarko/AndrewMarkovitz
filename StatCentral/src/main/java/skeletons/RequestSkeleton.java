package skeletons;

/**
 * Request
 *
 * A data transfer object used to encapsulate a request as it is passed between
 * internal system components (GatewayServer → LeaderServer → WorkerServer) via TCP.
 * Wraps a raw String representation of the request payload, and carries the league
 * identifier as a first-class field so that every component in the pipeline —
 * particularly the WorkerServer — can determine which league (and therefore which
 * sport-specific microservice configuration) the request belongs to, without having
 * to parse the request body string.
 *
 * The leagueId is set by the GatewayServer when it first receives and parses the
 * incoming HTTP request from a LeagueInterface or FanInterface, and is preserved
 * unchanged throughout the entire request lifecycle.
 */
public class RequestSkeleton {

    private String requestBody;

    /**
     * The unique identifier of the league this request belongs to (e.g. "MLB", "NFL",
     * "NBA"). Used by the WorkerServer to select the correct sport-specific microservice
     * instance (e.g. the appropriate StatUpdater subclass) from its internal registry.
     * Must match the league identifier used when registering microservice instances on
     * the WorkerServer at startup.
     */
    private String leagueId;

    /**
     * Returns the raw String payload of this request.
     *
     * @return    The String representation of the request body
     */
    public String getRequestBody() { return null; }

    /**
     * Sets the raw String payload of this request.
     *
     * @param requestBody    The String representation of the request body to set
     */
    public void setRequestBody(String requestBody) {}

    /**
     * Returns the league identifier associated with this request.
     * Used by the WorkerServer to look up the correct sport-specific
     * microservice instance for the league that originated this request.
     *
     * @return    The league identifier string (e.g. "MLB", "NFL", "NBA")
     */
    public String getLeagueId() { return null; }

    /**
     * Sets the league identifier for this request. Called by the GatewayServer
     * when parsing the incoming HTTP request before forwarding to the LeaderServer.
     *
     * @param leagueId    The league identifier string (e.g. "MLB", "NFL", "NBA")
     */
    public void setLeagueId(String leagueId) {}
}