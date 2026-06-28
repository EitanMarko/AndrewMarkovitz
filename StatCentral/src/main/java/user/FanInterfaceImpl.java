package user;

import cluster.GatewayConfig;
import cluster.GatewayServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FanInterfaceImpl
 *
 * Concrete implementation of FanInterface. This is the class that fans (or the
 * application code acting on behalf of fans) actually instantiate and call.
 *
 * ── What this class does ─────────────────────────────────────────────────────
 * Each method in this class:
 *   1. Builds a JSON request body encoding the operation type and parameters.
 *   2. Sends that body as an HTTP POST to the GatewayServer.
 *   3. Blocks until the HTTP response arrives.
 *   4. Returns the response body string to the caller.
 *
 * ── Request Format ───────────────────────────────────────────────────────────
 * All requests are sent as HTTP POST to:
 *   http://<gatewayHost>:<gatewayPort>/request
 *
 * The request body is a JSON object with an "operationType" field and one field
 * per parameter. For example, a getPlayerStats request looks like:
 *   {
 *     "operationType": "getPlayerStats",
 *     "playerId": "Aaron Judge"
 *   }
 *
 * The GatewayServer reads the "operationType" field to route the request through
 * the LeaderServer → WorkerServer → appropriate microservice pipeline.
 *
 * ── HTTP Client ──────────────────────────────────────────────────────────────
 * We use Java 11's built-in java.net.http.HttpClient. No third-party HTTP
 * library is required. The client is created once at construction time and
 * reused across all requests for efficiency (connection pooling is handled
 * internally by HttpClient).
 *
 * ── Error Handling ───────────────────────────────────────────────────────────
 * If the HTTP request itself fails (network error, timeout, GatewayServer
 * unreachable), the method catches the exception and returns a formatted error
 * string — consistent with the error-string contract defined in FanInterface.
 * The caller never needs to catch exceptions from these methods.
 *
 * ── Configuration ────────────────────────────────────────────────────────────
 * The GatewayServer host and port are passed in at construction time, making
 * this class easy to configure for different environments (local dev, staging,
 * production) without code changes.
 */
public class FanInterfaceImpl implements FanInterface {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /**
     * The URL path on the GatewayServer that accepts all incoming requests.
     * The GatewayServer uses the "operationType" field in the JSON body —
     * not the URL path — to distinguish between operation types.
     */
    private static final String REQUEST_PATH = "/request";

    /**
     * Timeout for the entire HTTP request (connect + send + receive).
     * If the GatewayServer does not respond within this duration, the request
     * fails and an error string is returned to the caller.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    /**
     * The base URL of the GatewayServer, constructed from host and port.
     * Example: "http://localhost:8080"
     * All request URLs are built by appending REQUEST_PATH to this base.
     */
    private final String gatewayBaseUrl;

    /**
     * The league this fan is watching (e.g. "MLB", "NBA").
     * Included in every request so WorkerServer can route to the correct
     * StatUpdater — and therefore the correct stats table and column set.
     */
    private final String leagueId;

    /**
     * The shared HTTP client. Created once at construction and reused for all
     * requests. HttpClient internally manages connection pooling and thread safety.
     */
    private final HttpClient httpClient;

    /** Optional gateway restart configuration; null disables auto-recovery. */
    private final GatewayConfig gatewayConfig;

    private final AtomicBoolean recovering = new AtomicBoolean(false);
    private final Object recoveryLock = new Object();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new FanInterfaceImpl that sends all requests to the specified
     * GatewayServer host and port.
     *
     * @param gatewayHost  The hostname or IP of the GatewayServer (e.g. "localhost")
     * @param gatewayPort  The port the GatewayServer listens on (e.g. 8080)
     */
    public FanInterfaceImpl(String gatewayHost, int gatewayPort, String leagueId) {
        this.gatewayBaseUrl = "http://" + gatewayHost + ":" + gatewayPort;
        this.leagueId       = leagueId;
        this.httpClient     = HttpClient.newHttpClient();
        this.gatewayConfig  = null;
    }

    /**
     * Recovery-enabled constructor. Pass a {@link GatewayConfig} so the interface
     * can automatically restart the GatewayServer on connection failure.
     */
    public FanInterfaceImpl(String gatewayHost, int gatewayPort, String leagueId,
                            GatewayConfig gatewayConfig) {
        this.gatewayBaseUrl = "http://" + gatewayHost + ":" + gatewayPort;
        this.leagueId       = leagueId;
        this.httpClient     = HttpClient.newHttpClient();
        this.gatewayConfig  = gatewayConfig;
    }

    // -------------------------------------------------------------------------
    // FanInterface implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   { "operationType": "getPlayerStats", "playerId": "<playerId>" }
     *
     * The GatewayServer routes this to the WorkerServer, which delegates to
     * PlayerStatQuerier.getAllPlayerStats(playerName).
     */
    @Override
    public String getPlayerStats(String playerId) {
        // Build the JSON request body.
        // We use manual JSON construction here to avoid requiring a JSON library
        // dependency. For a production system, use Jackson or Gson instead.
        // escapeJson() sanitizes the value to prevent JSON injection
        // (e.g. a player name containing a '"' character breaking the JSON structure).
        String body = "{"
                + "\"operationType\": \"getPlayerStats\","
                + "\"leagueId\": \""  + escapeJson(leagueId) + "\","
                + "\"playerId\": \"" + escapeJson(playerId)  + "\""
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "getPlayerStat",
     *     "playerId": "<playerId>",
     *     "statName": "<statName>"
     *   }
     *
     * The GatewayServer routes this to the WorkerServer, which delegates to
     * PlayerStatQuerier.getPlayerStat(playerName, statName).
     */
    @Override
    public String getPlayerStat(String playerId, String statName) {
        String body = "{"
                + "\"operationType\": \"getPlayerStat\","
                + "\"leagueId\": \""  + escapeJson(leagueId) + "\","
                + "\"playerId\": \"" + escapeJson(playerId)  + "\","
                + "\"statName\": \"" + escapeJson(statName)  + "\""
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "queryTopPlayersByStat",
     *     "statName": "<statName>",
     *     "leagueLevel": "<leagueLevel>",
     *     "leagueLevelValue": "<leagueLevelValue>",
     *     "topN": <topN>,
     *     "year": <year>
     *   }
     *
     * The GatewayServer routes this to the WorkerServer, which delegates to
     * AggregateStatQuerier.queryTopPlayersByStat(statName, leagueLevel,
     *   leagueLevelValue, topN, year).
     *
     * Note: topN and year are integers and are written directly into the JSON
     * without quotes, matching the JSON number type. They do not need escaping.
     */
    @Override
    public String queryTopPlayersByStat(String statName, String leagueLevel,
                                        String leagueLevelValue, int topN, int year) {
        String body = "{"
                + "\"operationType\": \"queryTopPlayersByStat\","
                + "\"leagueId\": \""         + escapeJson(leagueId)         + "\","
                + "\"statName\": \""         + escapeJson(statName)         + "\","
                + "\"leagueLevel\": \""      + escapeJson(leagueLevel)      + "\","
                + "\"leagueLevelValue\": \"" + escapeJson(leagueLevelValue) + "\","
                + "\"topN\": "               + topN                         + ","
                + "\"year\": "               + year
                + "}";
        return sendRequest(body);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sends an HTTP POST request to the GatewayServer with the given JSON body,
     * waits for the response, and returns the response body as a String.
     *
     * This is the single method through which all HTTP communication flows.
     * Centralizing the send logic here means all methods automatically share
     * the same timeout, Content-Type header, error handling, and URL construction.
     *
     * If an IOException or InterruptedException occurs (e.g. the GatewayServer
     * is unreachable, the request times out, or the thread is interrupted),
     * this method catches the exception and returns a formatted error string
     * so the caller always receives a String result, never an exception.
     *
     * @param jsonBody  The JSON-encoded request body string
     * @return          The HTTP response body string, or an error message string
     */
    private String sendRequest(String jsonBody) {
        try {
            return doSend(jsonBody);
        } catch (java.net.http.HttpTimeoutException e) {
            return "Error: request to GatewayServer timed out after "
                    + REQUEST_TIMEOUT.getSeconds() + " seconds. "
                    + "The system may be temporarily unavailable. Please try again.";
        } catch (IOException e) {
            if (gatewayConfig != null) {
                return attemptGatewayRecovery(jsonBody);
            }
            return "Error: could not reach GatewayServer at " + gatewayBaseUrl
                    + ". Details: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: request was interrupted before a response was received.";
        }
    }

    /** Raw HTTP send — throws rather than catches so callers can decide handling. */
    private String doSend(String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayBaseUrl + REQUEST_PATH))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    /**
     * Restarts the GatewayServer after a detected failure, then retries the
     * original request once.  Mirrors LeagueInterfaceImpl.attemptGatewayRecovery().
     */
    private String attemptGatewayRecovery(String jsonBody) {
        if (recovering.compareAndSet(false, true)) {
            try {
                System.out.println("[FanInterfaceImpl] GatewayServer unreachable — starting recovery.");

                GatewayServer old = gatewayConfig.activeGateway;
                if (old != null) {
                    try { old.shutdown(); } catch (Exception ignored) {}
                }

                GatewayServer replacement = new GatewayServer(
                        gatewayConfig.httpPort,
                        gatewayConfig.peerPort,
                        gatewayConfig.peerEpoch,
                        gatewayConfig.serverID,
                        new ConcurrentHashMap<>(gatewayConfig.peerIDtoAddress),
                        gatewayConfig.numberOfObservers);
                replacement.start();
                gatewayConfig.activeGateway = replacement;
                System.out.println("[FanInterfaceImpl] Replacement GatewayServer started — waiting for leader election.");

                long deadline = System.currentTimeMillis() + 20_000;
                while (replacement.getPeerServer().getCurrentLeader() == null
                        && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                if (replacement.getPeerServer().getCurrentLeader() == null) {
                    return "Error: replacement GatewayServer could not identify a cluster leader within 20 s.";
                }
                System.out.println("[FanInterfaceImpl] New GatewayServer has a leader — resuming requests.");

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return "Error: interrupted during GatewayServer recovery.";
            } catch (Exception e) {
                return "Error: GatewayServer recovery failed: " + e.getMessage();
            } finally {
                synchronized (recoveryLock) {
                    recovering.set(false);
                    recoveryLock.notifyAll();
                }
            }
        } else {
            synchronized (recoveryLock) {
                while (recovering.get()) {
                    try {
                        recoveryLock.wait(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "Error: interrupted while waiting for GatewayServer recovery.";
                    }
                }
            }
        }

        try {
            return doSend(jsonBody);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "Error: request retry after GatewayServer recovery failed: " + e.getMessage();
        }
    }

    /**
     * Escapes characters in a string that have special meaning inside a JSON
     * string value, preventing JSON injection.
     *
     * Characters escaped:
     *   \  →  \\   (backslash must be escaped first, before others)
     *   "  →  \"   (unescaped quote would end the JSON string prematurely)
     *   newline → \n, tab → \t (control characters that break JSON parsers)
     *
     * Example: a player name of  Aaron "The Judge" Judge
     *   becomes: Aaron \"The Judge\" Judge
     * ...which is safe to embed inside a JSON string value.
     *
     * NOTE: For a production system, replace this with a proper JSON library
     * (Jackson's ObjectMapper or Gson's JsonObject) which handles all edge cases.
     *
     * @param value  The raw string value to embed in JSON
     * @return       The escaped version safe for use inside JSON double-quotes
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")  // must be first — escape backslashes before others
                .replace("\"", "\\\"")  // escape double quotes
                .replace("\n", "\\n")   // escape newlines
                .replace("\r", "\\r")   // escape carriage returns
                .replace("\t", "\\t");  // escape tabs
    }
}
