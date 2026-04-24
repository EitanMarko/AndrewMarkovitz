package user;

import cluster.GatewayConfig;
import cluster.GatewayServer;
import microservices.PlayerData;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LeagueInterfaceImpl
 *
 * Concrete implementation of LeagueInterface. This is the class that league
 * administrators (or the application code acting on their behalf) actually
 * instantiate and call.
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
 * per parameter. The GatewayServer reads "operationType" to route the request
 * through the LeaderServer → WorkerServer → microservice pipeline.
 *
 * For example, a createTeam request body looks like:
 *   {
 *     "operationType": "createTeam",
 *     "teamName": "New York Yankees",
 *     "divisionName": "East"
 *   }
 *
 * ── League ID ────────────────────────────────────────────────────────────────
 * A "leagueId" field is included in every request body. The WorkerServer uses
 * this field when handling stat update requests to look up the correct
 * StatUpdater instance from its internal registry (e.g. "MLB" → MLBStatUpdater).
 * For structural requests it is ignored but still included for consistency.
 *
 * ── HTTP Client ──────────────────────────────────────────────────────────────
 * We use Java 11's built-in java.net.http.HttpClient, created once at
 * construction and reused across all requests.
 *
 * ── Error Handling ───────────────────────────────────────────────────────────
 * Network failures, timeouts, and interrupted threads are all caught and
 * returned as error strings — consistent with the String return type contract
 * defined in LeagueInterface.
 *
 * ── Configuration ────────────────────────────────────────────────────────────
 * The GatewayServer host, port, and leagueId are passed at construction time.
 */
public class LeagueInterfaceImpl implements LeagueInterface {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** URL path on the GatewayServer that accepts all incoming requests. */
    private static final String REQUEST_PATH = "/request";

    /**
     * Timeout for the entire HTTP request cycle (connect + send + receive).
     * League write operations may take slightly longer than fan reads because
     * they involve DB writes and potentially index updates, so we allow 15s.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    /** Base URL of the GatewayServer, e.g. "http://gateway-host:8080". */
    private final String gatewayBaseUrl;

    /**
     * The league identifier for this instance. Included in every request so
     * the WorkerServer can resolve the correct sport-specific StatUpdater.
     * Example values: "MLB", "NFL", "NBA".
     */
    private final String leagueId;

    /** Shared HTTP client, reused across all requests from this instance. */
    private final HttpClient httpClient;

    /**
     * Optional gateway restart configuration.  When non-null, an IOException
     * from sendRequest() triggers automatic GatewayServer recovery instead of
     * returning an error string to the caller.
     */
    private final GatewayConfig gatewayConfig;

    /**
     * Guards the recovery section so only one thread restarts the gateway at a
     * time.  Other threads that detect the same failure wait on recoveryLock
     * until the winner finishes, then retry their requests normally.
     */
    private final AtomicBoolean recovering = new AtomicBoolean(false);
    private final Object recoveryLock = new Object();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new LeagueInterfaceImpl that sends all requests to the specified
     * GatewayServer and identifies itself with the given leagueId.
     *
     * @param gatewayHost  The hostname or IP of the GatewayServer (e.g. "localhost")
     * @param gatewayPort  The port the GatewayServer listens on (e.g. 8080)
     * @param leagueId     The identifier for this sports league (e.g. "MLB").
     *                     Must match a key registered in each WorkerServer's
     *                     StatUpdater registry.
     */
    public LeagueInterfaceImpl(String gatewayHost, int gatewayPort, String leagueId) {
        this.gatewayBaseUrl = "http://" + gatewayHost + ":" + gatewayPort;
        this.leagueId       = leagueId;
        this.httpClient     = HttpClient.newHttpClient();
        this.gatewayConfig  = null;
    }

    /**
     * Recovery-enabled constructor.  Pass a {@link GatewayConfig} to allow the
     * interface to automatically restart the GatewayServer when it detects a
     * connection failure.
     *
     * @param gatewayHost  Hostname of the GatewayServer
     * @param gatewayPort  HTTP port of the GatewayServer
     * @param leagueId     Sport league identifier (e.g. "MLB")
     * @param gatewayConfig All parameters needed to reconstruct the gateway
     */
    public LeagueInterfaceImpl(String gatewayHost, int gatewayPort, String leagueId,
                               GatewayConfig gatewayConfig) {
        this.gatewayBaseUrl = "http://" + gatewayHost + ":" + gatewayPort;
        this.leagueId       = leagueId;
        this.httpClient     = HttpClient.newHttpClient();
        this.gatewayConfig  = gatewayConfig;
    }

    // =========================================================================
    // Player Management
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "createPlayer",
     *     "leagueId": "<leagueId>",
     *     "name": "<name>",
     *     "position": "<position>",
     *     "teamName": "<teamName>",
     *     "year": <year>,
     *     "homeRuns": <homeRuns>,
     *     "hits": <hits>,
     *     "atBats": <atBats>,
     *     "battingAverage": <battingAverage>,
     *     "strikeouts": <strikeouts>,
     *     "walks": <walks>,
     *     "stolenBases": <stolenBases>,
     *     "runsBattedIn": <runsBattedIn>
     *   }
     *
     * The WorkerServer deserializes this JSON back into a PlayerData object and
     * passes it to PlayerHandler.createPlayer(playerData).
     *
     * All stats fields are included even if zero, so the GatewayServer and
     * WorkerServer always receive a complete, unambiguous payload.
     */
    @Override
    public String createPlayer(PlayerData playerData) {
        if (playerData == null) {
            return "Error: playerData cannot be null.";
        }
        String body = "{"
                + "\"operationType\": \"createPlayer\","
                + "\"leagueId\": \""        + escapeJson(leagueId)                           + "\","
                + "\"name\": \""            + escapeJson(playerData.getName())                + "\","
                + "\"position\": \""        + escapeJson(playerData.getPosition())            + "\","
                + "\"teamName\": \""        + escapeJson(playerData.getTeamName())            + "\","
                + "\"year\": "              + playerData.getYear()                            + ","
                + "\"homeRuns\": "          + playerData.getHomeRuns()                        + ","
                + "\"hits\": "              + playerData.getHits()                            + ","
                + "\"atBats\": "            + playerData.getAtBats()                          + ","
                + "\"battingAverage\": "    + playerData.getBattingAverage()                  + ","
                + "\"strikeouts\": "        + playerData.getStrikeouts()                      + ","
                + "\"walks\": "             + playerData.getWalks()                           + ","
                + "\"stolenBases\": "       + playerData.getStolenBases()                     + ","
                + "\"runsBattedIn\": "      + playerData.getRunsBattedIn()
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   { "operationType": "deletePlayer", "leagueId": "<leagueId>", "playerId": "<playerId>" }
     *
     * The WorkerServer delegates to PlayerHandler.deletePlayer(playerName).
     */
    @Override
    public String deletePlayer(String playerId) {
        String body = "{"
                + "\"operationType\": \"deletePlayer\","
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
     *     "operationType": "updatePlayerStat",
     *     "leagueId": "<leagueId>",
     *     "playerId": "<playerId>",
     *     "statName": "<statName>",
     *     "value": <value>
     *   }
     *
     * The WorkerServer uses leagueId to resolve the correct StatUpdater subclass,
     * then calls either incrementStat() or setStat() depending on the value.
     * The value field is an integer and written without quotes (JSON number type).
     */
    @Override
    public String updatePlayerStat(String playerId, String statName, int value) {
        String body = "{"
                + "\"operationType\": \"updatePlayerStat\","
                + "\"leagueId\": \""  + escapeJson(leagueId) + "\","
                + "\"playerId\": \"" + escapeJson(playerId)  + "\","
                + "\"statName\": \"" + escapeJson(statName)  + "\","
                + "\"value\": "      + value                  // int — no escaping needed
                + "}";
        return sendRequest(body);
    }

    // =========================================================================
    // League Structure — Create
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "createConference",
     *     "leagueId": "<leagueId>",
     *     "conferenceName": "<conferenceName>"
     *   }
     *
     * The WorkerServer delegates to LeagueStructureManager.createConference(name).
     */
    @Override
    public String createConference(String conferenceName) {
        String body = "{"
                + "\"operationType\": \"createConference\","
                + "\"leagueId\": \""        + escapeJson(leagueId)       + "\","
                + "\"conferenceName\": \"" + escapeJson(conferenceName)  + "\""
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "createDivision",
     *     "leagueId": "<leagueId>",
     *     "divisionName": "<divisionName>",
     *     "conferenceName": "<conferenceName>"
     *   }
     *
     * The WorkerServer delegates to LeagueStructureManager.createDivision(div, conf).
     */
    @Override
    public String createDivision(String divisionName, String conferenceName) {
        String body = "{"
                + "\"operationType\": \"createDivision\","
                + "\"leagueId\": \""        + escapeJson(leagueId)       + "\","
                + "\"divisionName\": \""   + escapeJson(divisionName)    + "\","
                + "\"conferenceName\": \"" + escapeJson(conferenceName)  + "\""
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "createTeam",
     *     "leagueId": "<leagueId>",
     *     "teamName": "<teamName>",
     *     "divisionName": "<divisionName>"
     *   }
     *
     * The WorkerServer delegates to LeagueStructureManager.createTeam(team, div).
     */
    @Override
    public String createTeam(String teamName, String divisionName) {
        String body = "{"
                + "\"operationType\": \"createTeam\","
                + "\"leagueId\": \""      + escapeJson(leagueId)     + "\","
                + "\"teamName\": \""      + escapeJson(teamName)      + "\","
                + "\"divisionName\": \""  + escapeJson(divisionName)  + "\""
                + "}";
        return sendRequest(body);
    }

    // =========================================================================
    // League Structure — Delete
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "deleteConference",
     *     "leagueId": "<leagueId>",
     *     "conferenceName": "<conferenceName>"
     *   }
     *
     * The WorkerServer delegates to LeagueStructureManager.deleteConference(name).
     */
    @Override
    public String deleteConference(String conferenceName) {
        String body = "{"
                + "\"operationType\": \"deleteConference\","
                + "\"leagueId\": \""        + escapeJson(leagueId)       + "\","
                + "\"conferenceName\": \"" + escapeJson(conferenceName)  + "\""
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "deleteDivision",
     *     "leagueId": "<leagueId>",
     *     "divisionName": "<divisionName>"
     *   }
     *
     * The WorkerServer delegates to LeagueStructureManager.deleteDivision(name).
     */
    @Override
    public String deleteDivision(String divisionName) {
        String body = "{"
                + "\"operationType\": \"deleteDivision\","
                + "\"leagueId\": \""       + escapeJson(leagueId)      + "\","
                + "\"divisionName\": \""  + escapeJson(divisionName)   + "\""
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "deleteTeam",
     *     "leagueId": "<leagueId>",
     *     "teamName": "<teamName>"
     *   }
     *
     * The WorkerServer delegates to LeagueStructureManager.deleteTeam(name).
     */
    @Override
    public String deleteTeam(String teamName) {
        String body = "{"
                + "\"operationType\": \"deleteTeam\","
                + "\"leagueId\": \""  + escapeJson(leagueId)  + "\","
                + "\"teamName\": \"" + escapeJson(teamName)   + "\""
                + "}";
        return sendRequest(body);
    }

    // =========================================================================
    // League Structure — Move
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "movePlayerToTeam",
     *     "leagueId": "<leagueId>",
     *     "playerId": "<playerId>",
     *     "newTeam": "<newTeam>"
     *   }
     *
     * The WorkerServer delegates to LeagueStructureManager.movePlayerToTeam(id, team).
     */
    @Override
    public String movePlayerToTeam(String playerId, String newTeam) {
        String body = "{"
                + "\"operationType\": \"movePlayerToTeam\","
                + "\"leagueId\": \""  + escapeJson(leagueId) + "\","
                + "\"playerId\": \"" + escapeJson(playerId)  + "\","
                + "\"newTeam\": \""  + escapeJson(newTeam)   + "\""
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "moveTeamToDivision",
     *     "leagueId": "<leagueId>",
     *     "teamName": "<teamName>",
     *     "newDivision": "<newDivision>"
     *   }
     *
     * The WorkerServer delegates to LeagueStructureManager.moveTeamToDivision(team, div).
     */
    @Override
    public String moveTeamToDivision(String teamName, String newDivision) {
        String body = "{"
                + "\"operationType\": \"moveTeamToDivision\","
                + "\"leagueId\": \""      + escapeJson(leagueId)    + "\","
                + "\"teamName\": \""      + escapeJson(teamName)     + "\","
                + "\"newDivision\": \""   + escapeJson(newDivision)  + "\""
                + "}";
        return sendRequest(body);
    }

    /**
     * {@inheritDoc}
     *
     * Sends an HTTP POST to the GatewayServer with:
     *   {
     *     "operationType": "moveDivisionToConference",
     *     "leagueId": "<leagueId>",
     *     "divisionName": "<divisionName>",
     *     "newConference": "<newConference>"
     *   }
     *
     * The WorkerServer delegates to
     * LeagueStructureManager.moveDivisionToConference(div, conf).
     */
    @Override
    public String moveDivisionToConference(String divisionName, String newConference) {
        String body = "{"
                + "\"operationType\": \"moveDivisionToConference\","
                + "\"leagueId\": \""        + escapeJson(leagueId)       + "\","
                + "\"divisionName\": \""   + escapeJson(divisionName)    + "\","
                + "\"newConference\": \""  + escapeJson(newConference)   + "\""
                + "}";
        return sendRequest(body);
    }

    // =========================================================================
    // Private helpers — identical to FanInterfaceImpl by design
    // =========================================================================

    /**
     * Sends an HTTP POST request to the GatewayServer with the given JSON body,
     * waits for the response, and returns the response body as a String.
     *
     * This is the single point through which all HTTP communication flows for
     * the LeagueInterface. Centralizing the send logic here means all methods
     * automatically share the same timeout, Content-Type header, and error handling.
     *
     * @param jsonBody  The JSON-encoded request body string
     * @return          The HTTP response body string, or an error message string
     */
    private String sendRequest(String jsonBody) {
        System.out.println("Sending request to gateway at " + gatewayBaseUrl + REQUEST_PATH);
        try {
            return doSend(jsonBody);
        } catch (java.net.http.HttpTimeoutException e) {
            return "Error: request to GatewayServer timed out after "
                    + REQUEST_TIMEOUT.getSeconds() + " seconds. "
                    + "The system may be temporarily unavailable. Please try again.";
        } catch (IOException e) {
            // Connection refused or reset — the GatewayServer is likely dead.
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

    /**
     * Raw HTTP send — throws rather than catches so callers can decide how to
     * handle each exception type.
     */
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
     * original request once.
     *
     * Only one thread performs the restart; concurrent callers that detect the
     * same failure wait on {@code recoveryLock} until the winner finishes, then
     * retry their requests directly.
     *
     * @param jsonBody The request body that triggered the failure; retried after
     *                 the new gateway is ready.
     * @return The response from the retried request, or an error string.
     */
    private String attemptGatewayRecovery(String jsonBody) {
        if (recovering.compareAndSet(false, true)) {
            // ── This thread wins the recovery race ────────────────────────────
            try {
                System.out.println("[LeagueInterfaceImpl] GatewayServer unreachable — starting recovery.");

                // 1. Shut down the dead gateway (frees the HTTP port for the new one).
                GatewayServer old = gatewayConfig.activeGateway;
                if (old != null) {
                    try { old.shutdown(); } catch (Exception ignored) {}
                }

                // 2. Create and start a replacement GatewayServer with the same config.
                //    A fresh ConcurrentHashMap copy is passed so PeerServerImpl's
                //    constructor-time remove(serverID) doesn't mutate the stored map.
                GatewayServer replacement = new GatewayServer(
                        gatewayConfig.httpPort,
                        gatewayConfig.peerPort,
                        gatewayConfig.peerEpoch,
                        gatewayConfig.serverID,
                        new ConcurrentHashMap<>(gatewayConfig.peerIDtoAddress),
                        gatewayConfig.numberOfObservers);
                replacement.start();
                gatewayConfig.activeGateway = replacement;
                System.out.println("[LeagueInterfaceImpl] Replacement GatewayServer started — waiting for leader election.");

                // 3. Wait for the new gateway's peer server to identify the current leader.
                //    The election completes via the existing gossip/election protocol.
                long deadline = System.currentTimeMillis() + 20_000;
                while (replacement.getPeerServer().getCurrentLeader() == null
                        && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200);
                }
                if (replacement.getPeerServer().getCurrentLeader() == null) {
                    return "Error: replacement GatewayServer could not identify a cluster leader within 20 s.";
                }
                System.out.println("[LeagueInterfaceImpl] New GatewayServer has a leader — resuming requests.");

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return "Error: interrupted during GatewayServer recovery.";
            } catch (Exception e) {
                return "Error: GatewayServer recovery failed: " + e.getMessage();
            } finally {
                // Always release the recovery lock so waiting threads can proceed.
                synchronized (recoveryLock) {
                    recovering.set(false);
                    recoveryLock.notifyAll();
                }
            }
        } else {
            // ── Another thread is already recovering — wait for it ─────────────
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

        // Recovery complete (by this thread or another) — retry the original request once.
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
     * See FanInterfaceImpl.escapeJson() for full documentation.
     * This method is duplicated here rather than shared via a utility class to
     * keep each implementation self-contained. In a production codebase, extract
     * this to a shared JsonUtils helper or use a proper JSON library.
     *
     * @param value  The raw string value to embed in JSON
     * @return       The escaped string safe for use inside JSON double-quotes
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
