package cluster;

import helperFiles.LoggingServer;
import helperFiles.Message;
import microservices.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static helperFiles.PeerServer.ServerState.LOOKING;

/**
 * WorkerServer
 *
 * Responsible for:
 *   1. Accepting incoming TCP connections from the LeaderServer.
 *   2. Reading the request payload from the socket.
 *   3. Parsing the JSON request body to determine the operation type.
 *   4. Dispatching the request to the appropriate microservice method.
 *   5. Sending the microservice's result string back to the LeaderServer
 *      over the same socket connection.
 *
 * ── Microservices held by this class ─────────────────────────────────────────
 *   - PlayerHandler          handles createPlayer, deletePlayer
 *   - LeagueStructureManager handles all create/delete/move structure operations
 *   - StatUpdater (via registry) handles updatePlayerStat; the correct subclass
 *                            is resolved using the "leagueId" field in the request
 *   - PlayerStatQuerier      handles getPlayerStats, getPlayerStat
 *   - AggregateStatQuerier   handles queryTopPlayersByStat
 *
 * ── JSON parsing ─────────────────────────────────────────────────────────────
 * JSON parsing is handled by the private parseString() and parseInt() /
 * parseDouble() helpers at the bottom of this class. These are intentionally
 * lightweight and avoid the need for a third-party JSON library dependency.
 * They are sufficient for the flat, well-structured JSON payloads produced by
 * LeagueInterfaceImpl and FanInterfaceImpl. If the system is later extended
 * with nested JSON, replace these with Jackson or Gson.
 */
public class WorkerServer extends Thread implements LoggingServer {

    private final LinkedBlockingQueue<Message> outgoingMessages;
    private final LinkedBlockingQueue<Message> incomingMessages;
    private final PeerServerImpl workerServer;
    private final InetSocketAddress leaderAddress;
    private Logger logger;
    private ServerSocket serverSocket;
    private final int failInterval;

    /**
     * Cache of completed work that could not be sent back to the leader because
     * the leader failed mid-flight. Maps requestNumber → result string.
     * When a new leader is elected and re-sends the same requestNumber, this
     * class returns the cached result rather than re-executing the operation.
     */
    private final Map<Integer, String> completedWork;

    // -------------------------------------------------------------------------
    // NEW: Microservice instances
    // Each microservice is instantiated once and reused across all requests.
    // They are stateless with respect to request data (all state is in the DB),
    // so sharing a single instance across requests is safe.
    // -------------------------------------------------------------------------

    /** Handles createPlayer and deletePlayer requests. */
    private final PlayerHandler playerHandler;

    /** Handles all league structure create, delete, and move requests. */
    private final LeagueStructureManager leagueStructureManager;

    /** Handles getPlayerStat and getAllPlayerStats requests. */
    private final PlayerStatQuerier playerStatQuerier;

    /** Handles queryTopPlayersByStat requests. */
    private final AggregateStatQuerier aggregateStatQuerier;

    /**
     * Registry mapping leagueId strings (e.g. "MLB", "NFL") to their
     * corresponding sport-specific StatUpdater subclass instance.
     *
     * The WorkerServer passes this map in at construction time, populated
     * with whatever leagues are configured for this deployment. When an
     * "updatePlayerStat" request arrives, we look up the correct StatUpdater
     * by the "leagueId" field in the JSON body.
     *
     * If a request arrives with a leagueId that has no registered StatUpdater,
     * dispatchRequest() returns an error string rather than throwing.
     */
    private final Map<String, StatUpdater> statUpdaterRegistry;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new WorkerServer ready to accept requests from the leader.
     *
     * @param workerServer        The PeerServerImpl this follower belongs to.
     *                            Used to check server state (LOOKING vs not) and
     *                            retrieve TCP port and server ID.
     * @param leaderAddress       The address of the current leader. Used for
     *                            logging and reference; actual communication is
     *                            inbound (leader connects to us).
     * @param outgoingMessages    Queue for outbound messages to the leader cluster.
     *                            Preserved from original; not used in dispatch logic.
     * @param incomingMessages    Queue for inbound messages from the leader cluster.
     *                            Preserved from original; not used in dispatch logic.
     * @param logger              Parent logger passed in from PeerServerImpl.
     *                            Used as fallback if this class's logger init fails.
     * @param failInterval        Preserved from original; governs failure simulation.
     * @param statUpdaterRegistry Map of leagueId → StatUpdater subclass instance.
     *                            Must contain at least one entry for stat update
     *                            requests to be handled successfully.
     */
    public WorkerServer(
            PeerServerImpl workerServer,
            InetSocketAddress leaderAddress,
            LinkedBlockingQueue<Message> outgoingMessages,
            LinkedBlockingQueue<Message> incomingMessages,
            Logger logger,
            int failInterval,
            Map<String, StatUpdater> statUpdaterRegistry) throws IOException {

        this.outgoingMessages      = outgoingMessages;
        this.incomingMessages      = incomingMessages;
        this.workerServer          = workerServer;
        this.leaderAddress         = leaderAddress;
        this.serverSocket          = new ServerSocket(workerServer.getTcpPort());
        this.failInterval          = failInterval;
        this.completedWork         = new HashMap<>();
        this.statUpdaterRegistry   = statUpdaterRegistry;

        // Instantiate microservices — each connects to the DB via DBConnectionManager
        // using the appropriate read or write pool automatically.
        this.playerHandler          = new PlayerHandler();
        this.leagueStructureManager = new LeagueStructureManager();
        this.playerStatQuerier      = new PlayerStatQuerier();
        this.aggregateStatQuerier   = new AggregateStatQuerier();

        // Initialize this follower's own logger. If that fails, fall back to the
        // parent logger passed in from PeerServerImpl.
        try {
            this.logger = initializeLogging(
                    WorkerServer.class.getCanonicalName()
                    + "-on-server_" + workerServer.getServerId()
                    + "-on-tcpPort-" + workerServer.getTcpPort());
        } catch (IOException e) {
            logger.severe("Couldn't initialize WorkerServer's logger");
            this.logger = logger;
        }
    }

    // -------------------------------------------------------------------------
    // Shutdown — preserved exactly from original
    // -------------------------------------------------------------------------

    public void shutdown() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Suppress — we're shutting down anyway
        }
        interrupt();
    }

    // -------------------------------------------------------------------------
    // run() — socket lifecycle preserved; JavaRunner replaced with dispatch
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        this.logger.log(Level.FINE,
                "\n\n\n-------------------------------------------------\n"
                + "WORK AHEAD - FOLLOWER (" + workerServer.getServerId()
                + ", TCP Port: " + workerServer.getTcpPort() + ")\n"
                + "-------------------------------------------------\n");

        boolean firstReq = true;

        while (!this.isInterrupted()) {

            // -----------------------------------------------------------------
            // Step 1: Accept a TCP connection from the leader and read the
            // request payload. This block is preserved exactly from the original.
            // -----------------------------------------------------------------
            byte[] requestBytes;
            OutputStream out;
            Socket leaderSocket;

            try {
                serverSocket.setSoTimeout(100);

                // Wait until an election has completed before accepting work
                while (true) {
                    if (workerServer.getCurrentLeader() != null) {
                        break;
                    }
                }

                leaderSocket = serverSocket.accept();

                if (firstReq) {
                    this.logger.fine("First connection accepted on WorkerServer.run() "
                            + "on server " + workerServer.getServerId() + "\n");
                    int sleep = Math.max(10000, workerServer.getNonObservers().size() * 1000);
                    serverSocket.setSoTimeout(sleep);
                    firstReq = false;
                }

                logger.fine("Accepted leader socket connection\n");
                InputStream in = leaderSocket.getInputStream();
                out = leaderSocket.getOutputStream();

                // Read the 4-byte length header, then read that many bytes of payload
                byte[] lenBytes = in.readNBytes(4);
                int len = ByteBuffer.wrap(lenBytes).getInt();
                requestBytes = in.readNBytes(len);

            } catch (IOException e) {
                continue; // Socket timed out or failed — loop and retry
            }

            // -----------------------------------------------------------------
            // Step 2: Strip the request number prefix from the payload.
            //
            // The leader prepends "[<requestNum>]" to the raw request body before
            // sending, matching the format from the original implementation.
            // We extract the request number and the clean JSON body separately.
            //
            // Example full payload:  [42]{"operationType": "createPlayer", ...}
            // After stripping:       requestNum = 42
            //                        requestJson = {"operationType": "createPlayer", ...}
            // -----------------------------------------------------------------
            String fullPayload = new String(requestBytes);

            // Extract the bracketed request number
            int bracketOpen  = fullPayload.indexOf('[');
            int bracketClose = fullPayload.indexOf(']');
            int requestNum   = Integer.parseInt(
                    fullPayload.substring(bracketOpen + 1, bracketClose));

            // Everything after the closing bracket is the JSON request body
            String requestJson = fullPayload.substring(bracketClose + 1).trim();

            // -----------------------------------------------------------------
            // Step 3: Produce the result string.
            //
            // If we have already completed this request (cached because the
            // previous leader died before we could send the result), return the
            // cached result immediately without re-executing the operation.
            // This prevents double-writes to the database on leader failover.
            //
            // Otherwise, dispatch the JSON request to the correct microservice.
            // -----------------------------------------------------------------
            String output;

            if (completedWork.containsKey(requestNum)) {
                // Return the cached result for this request number
                output = completedWork.get(requestNum);
                completedWork.remove(requestNum); // evict from cache now that it's been sent
                logger.fine("Returned cached result for request #" + requestNum
                        + " (re-requested by new leader)\n");
            } else {
                // NEW: Dispatch the JSON request to the correct microservice.
                // dispatchRequest() never throws — it catches all exceptions
                // internally and returns an error string.
                output = dispatchRequest(requestJson);
                logger.fine("Dispatched request #" + requestNum
                        + ", operationType=" + parseString(requestJson, "operationType") + "\n");
            }

            // -----------------------------------------------------------------
            // Step 4: Send the result back to the leader on the same socket.
            // This block is preserved exactly from the original.
            // -----------------------------------------------------------------
            try {
                if (workerServer.getPeerState() != LOOKING) {
                    // Normal case: we are still a follower, leader is alive.
                    // Send a length-prefixed response with a requestID header.
                    String header = "{requestID: #" + requestNum + "}";
                    byte[] responseBytes = (header + output).getBytes();
                    byte[] lengthHeaderBuffer = ByteBuffer.allocate(4)
                            .putInt(responseBytes.length).array();

                    logger.fine("Attempting to write response back to leader at ["
                            + System.nanoTime() + "]\n");
                    out.write(lengthHeaderBuffer);
                    out.write(responseBytes);
                    out.flush();
                    leaderSocket.close();
                    logger.fine("Sent response back to leader and closed socket\n"
                            + "--------------------------------------------------------\n");

                } else {
                    // The server has detected a leader failure (state is now LOOKING).
                    // Cache the completed work so the new leader can retrieve it.
                    completedWork.put(requestNum, output);
                    logger.fine("Leader failed — caching completed request #" + requestNum + "\n");
                }

            } catch (IOException e) {
                this.logger.log(Level.FINE,
                        "IOException when sending response to leader in WorkerServer");
                // Cache the result so the new leader can re-request and receive it
                completedWork.put(requestNum, output);
                logger.fine("IOException — caching completed request #" + requestNum + "\n");
                return;
            }
        } // end while loop

        // Clean up server socket on thread interrupt (shutdown signal)
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                this.logger.log(Level.FINE,
                        "IOException when closing socket in WorkerServer");
            }
        }
        this.logger.fine("Exiting WorkerServer.run() on server "
                + workerServer.getServerId());
    }

    // -------------------------------------------------------------------------
    // Preserved from original
    // -------------------------------------------------------------------------

    /**
     * Returns all completed work that could not be sent to the previous leader.
     * Called by the WorkerServer when a new leader takes over, so the new leader
     * can re-request any in-flight items and receive their cached results.
     *
     * @return Map of requestNumber → result string for all un-sent completed work
     */
    public Map<Integer, String> getCompletedWork() {
        return completedWork;
    }

    // =========================================================================
    // NEW: Request dispatch
    // =========================================================================

    /**
     * Parses the "operationType" field from the JSON request body and dispatches
     * the request to the correct microservice method, returning the result string.
     *
     * This method is the replacement for the JavaRunner.compileAndRun() call in
     * the original implementation. It never throws — all exceptions are caught
     * and returned as formatted error strings, maintaining the same contract the
     * rest of the class expects (always a String result, never an exception).
     *
     * ── Dispatch table ───────────────────────────────────────────────────────
     *
     * operationType              → microservice.method(params)
     * ─────────────────────────────────────────────────────────────────────────
     * "createPlayer"             → PlayerHandler.createPlayer(PlayerData)
     * "deletePlayer"             → PlayerHandler.deletePlayer(playerId)
     * "createConference"         → LeagueStructureManager.createConference(name)
     * "createDivision"           → LeagueStructureManager.createDivision(div, conf)
     * "createTeam"               → LeagueStructureManager.createTeam(team, div)
     * "deleteConference"         → LeagueStructureManager.deleteConference(name)
     * "deleteDivision"           → LeagueStructureManager.deleteDivision(name)
     * "deleteTeam"               → LeagueStructureManager.deleteTeam(name)
     * "movePlayerToTeam"         → LeagueStructureManager.movePlayerToTeam(id, team)
     * "moveTeamToDivision"       → LeagueStructureManager.moveTeamToDivision(team, div)
     * "moveDivisionToConference" → LeagueStructureManager.moveDivisionToConference(div, conf)
     * "updatePlayerStat"         → StatUpdater.incrementStat(id, stat)
     *                              or StatUpdater.setStat(id, stat, value)
     *                              (resolved from statUpdaterRegistry by leagueId)
     * "getPlayerStats"           → PlayerStatQuerier.getAllPlayerStats(name)
     * "getPlayerStat"            → PlayerStatQuerier.getPlayerStat(name, stat)
     * "queryTopPlayersByStat"    → AggregateStatQuerier.queryTopPlayersByStat(...)
     *
     * @param requestJson  The raw JSON body string (with the "[requestNum]" prefix
     *                     already stripped off by the caller)
     * @return             The result string from the microservice, or an error
     *                     string if the operationType is unrecognized or a
     *                     parameter is malformed
     */
    private String dispatchRequest(String requestJson) {
        try {
            String operationType = parseString(requestJson, "operationType");

            if (operationType == null || operationType.isEmpty()) {
                return "Error: request is missing required 'operationType' field.";
            }

            logger.fine("Dispatching operationType='" + operationType + "'\n");

            switch (operationType) {

                // ---------------------------------------------------------
                // Player Management — delegated to PlayerHandler
                // ---------------------------------------------------------

                case "createPlayer": {
                    // Build a PlayerData object from the JSON fields.
                    PlayerData pd = new PlayerData();
                    pd.setName(           parseString(requestJson, "name"));
                    pd.setPosition(       parseString(requestJson, "position"));
                    pd.setTeamName(       parseString(requestJson, "teamName"));
                    pd.setLeagueId(       parseString(requestJson, "leagueId"));
                    pd.setYear(           parseInt(   requestJson, "year"));
                    pd.setHomeRuns(       parseInt(   requestJson, "homeRuns"));
                    pd.setHits(           parseInt(   requestJson, "hits"));
                    pd.setAtBats(         parseInt(   requestJson, "atBats"));
                    pd.setBattingAverage( parseDouble(requestJson, "battingAverage"));
                    pd.setStrikeouts(     parseInt(   requestJson, "strikeouts"));
                    pd.setWalks(          parseInt(   requestJson, "walks"));
                    pd.setStolenBases(    parseInt(   requestJson, "stolenBases"));
                    pd.setRunsBattedIn(   parseInt(   requestJson, "runsBattedIn"));

                    // Look up (or lazily create) the StatUpdater for this league.
                    // It handles the league-specific initial stats INSERT inside the transaction.
                    String createLeagueId = pd.getLeagueId();
                    StatUpdater createUpdater = statUpdaterRegistry.computeIfAbsent(
                            createLeagueId, WorkerServer::createStatUpdater);
                    if (createUpdater == null) {
                        return "Error: unsupported leagueId '" + createLeagueId
                                + "'. No StatUpdater implementation exists for this league.";
                    }
                    return playerHandler.createPlayer(pd, createUpdater);
                }

                case "deletePlayer": {
                    String playerId = parseString(requestJson, "playerId");
                    return playerHandler.deletePlayer(playerId);
                }

                // ---------------------------------------------------------
                // League Structure — Create, delegated to LeagueStructureManager
                // ---------------------------------------------------------

                case "createConference": {
                    String conferenceName = parseString(requestJson, "conferenceName");
                    return leagueStructureManager.createConference(conferenceName);
                }

                case "createDivision": {
                    String divisionName   = parseString(requestJson, "divisionName");
                    String conferenceName = parseString(requestJson, "conferenceName");
                    return leagueStructureManager.createDivision(divisionName, conferenceName);
                }

                case "createTeam": {
                    String teamName     = parseString(requestJson, "teamName");
                    String divisionName = parseString(requestJson, "divisionName");
                    return leagueStructureManager.createTeam(teamName, divisionName);
                }

                // ---------------------------------------------------------
                // League Structure — Delete, delegated to LeagueStructureManager
                // ---------------------------------------------------------

                case "deleteConference": {
                    String conferenceName = parseString(requestJson, "conferenceName");
                    return leagueStructureManager.deleteConference(conferenceName);
                }

                case "deleteDivision": {
                    String divisionName = parseString(requestJson, "divisionName");
                    return leagueStructureManager.deleteDivision(divisionName);
                }

                case "deleteTeam": {
                    String teamName = parseString(requestJson, "teamName");
                    return leagueStructureManager.deleteTeam(teamName);
                }

                // ---------------------------------------------------------
                // League Structure — Move, delegated to LeagueStructureManager
                // ---------------------------------------------------------

                case "movePlayerToTeam": {
                    String playerId = parseString(requestJson, "playerId");
                    String newTeam  = parseString(requestJson, "newTeam");
                    return leagueStructureManager.movePlayerToTeam(playerId, newTeam);
                }

                case "moveTeamToDivision": {
                    String teamName     = parseString(requestJson, "teamName");
                    String newDivision  = parseString(requestJson, "newDivision");
                    return leagueStructureManager.moveTeamToDivision(teamName, newDivision);
                }

                case "moveDivisionToConference": {
                    String divisionName  = parseString(requestJson, "divisionName");
                    String newConference = parseString(requestJson, "newConference");
                    return leagueStructureManager.moveDivisionToConference(
                            divisionName, newConference);
                }

                // ---------------------------------------------------------
                // Stat Updates — delegated to the correct StatUpdater subclass,
                // resolved from statUpdaterRegistry using the "leagueId" field.
                // ---------------------------------------------------------

                case "updatePlayerStat": {
                    String leagueId = parseString(requestJson, "leagueId");
                    String playerId = parseString(requestJson, "playerId");
                    String statName = parseString(requestJson, "statName");
                    int    value    = parseInt(   requestJson, "value");

                    // Lazy factory: look up the StatUpdater for this leagueId.
                    // If it isn't cached yet, createStatUpdater() instantiates the
                    // correct subclass and the result is stored for future requests.
                    // If the leagueId has no implementation, null is returned.
                    StatUpdater statUpdater = statUpdaterRegistry.computeIfAbsent(
                            leagueId, WorkerServer::createStatUpdater);
                    if (statUpdater == null) {
                        return "Error: unsupported leagueId '" + leagueId
                                + "'. No StatUpdater implementation exists for this league.";
                    }

                    // A value of 0 means "increment by 1" (e.g. recording a new hit
                    // during a live game). Any positive value means "set directly"
                    // (e.g. bulk-loading a historical stat value).
                    if (value == 0) {
                        return statUpdater.incrementStat(playerId, statName);
                    } else {
                        return statUpdater.setStat(playerId, statName, value);
                    }
                }

                // ---------------------------------------------------------
                // Fan Queries — delegated to PlayerStatQuerier
                // ---------------------------------------------------------

                case "getPlayerStats": {
                    String playerId    = parseString(requestJson, "playerId");
                    String fanLeagueId = parseString(requestJson, "leagueId");
                    StatUpdater fanUpdater = statUpdaterRegistry.computeIfAbsent(
                            fanLeagueId, WorkerServer::createStatUpdater);
                    if (fanUpdater == null) {
                        return "Error: unsupported leagueId '" + fanLeagueId + "'.";
                    }
                    return playerStatQuerier.getAllPlayerStats(playerId, fanUpdater);
                }

                case "getPlayerStat": {
                    String playerId    = parseString(requestJson, "playerId");
                    String statName    = parseString(requestJson, "statName");
                    String fanLeagueId = parseString(requestJson, "leagueId");
                    StatUpdater fanUpdater = statUpdaterRegistry.computeIfAbsent(
                            fanLeagueId, WorkerServer::createStatUpdater);
                    if (fanUpdater == null) {
                        return "Error: unsupported leagueId '" + fanLeagueId + "'.";
                    }
                    return playerStatQuerier.getPlayerStat(playerId, statName, fanUpdater);
                }

                // ---------------------------------------------------------
                // Fan Queries — delegated to AggregateStatQuerier
                // ---------------------------------------------------------

                case "queryTopPlayersByStat": {
                    String statName         = parseString(requestJson, "statName");
                    String leagueLevel      = parseString(requestJson, "leagueLevel");
                    String leagueLevelValue = parseString(requestJson, "leagueLevelValue");
                    int    topN             = parseInt(   requestJson, "topN");
                    int    year             = parseInt(   requestJson, "year");
                    String aggLeagueId      = parseString(requestJson, "leagueId");
                    StatUpdater aggUpdater  = statUpdaterRegistry.computeIfAbsent(
                            aggLeagueId, WorkerServer::createStatUpdater);
                    if (aggUpdater == null) {
                        return "Error: unsupported leagueId '" + aggLeagueId + "'.";
                    }
                    return aggregateStatQuerier.queryTopPlayersByStat(
                            statName, leagueLevel, leagueLevelValue, topN, year, aggUpdater);
                }

                // ---------------------------------------------------------
                // Unknown operationType
                // ---------------------------------------------------------

                default:
                    // Return an error string — the WorkerServer will propagate this
                    // back up through LeaderServer → GatewayServer → client.
                    logger.warning("Unrecognized operationType: '" + operationType + "'\n");
                    return "Error: unrecognized operationType '" + operationType
                            + "'. The request could not be routed to any microservice.";
            }

        } catch (Exception e) {
            // Catch-all for any unexpected exception during dispatch or parsing.
            // We log the full stack trace server-side and return a safe error
            // string to the client — never letting an exception propagate up
            // into the socket-handling code in run().
            logger.log(Level.SEVERE,
                    "Unexpected exception in dispatchRequest(): " + e.getMessage(), e);
            return "Error: an unexpected server-side error occurred while processing "
                    + "the request. Details: " + e.getMessage();
        }
    }

    // =========================================================================
    // NEW: StatUpdater factory
    //
    // Maps a leagueId string to the appropriate StatUpdater subclass instance.
    // Called lazily by computeIfAbsent() in the updatePlayerStat dispatch case.
    // To support a new sport, add a case here — no other code needs to change.
    // Returns null for unrecognised leagueIds so the caller can return an error.
    // =========================================================================

    private static StatUpdater createStatUpdater(String leagueId) {
        String className = "leagues." + leagueId + "StatUpdater";
        try {
            Class<?> clazz = Class.forName(className);
            return (StatUpdater) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            return null; // no StatUpdater implementation exists for this leagueId
        } catch (ReflectiveOperationException e) {
            return null; // class found but could not be instantiated
        }
    }

    // =========================================================================
    // NEW: Lightweight JSON field extractors
    //
    // These methods extract scalar values from the flat JSON payloads produced
    // by LeagueInterfaceImpl and FanInterfaceImpl. They are intentionally simple
    // and do not support nested objects or arrays, which our request format
    // does not use. Replace with Jackson or Gson if the format grows in complexity.
    // =========================================================================

    /**
     * Extracts the string value associated with the given key from a flat JSON object.
     *
     * Looks for the pattern:  "key": "value"
     * Handles values that contain escaped quotes (\") by stopping at the first
     * unescaped closing quote.
     *
     * Example:
     *   parseString({"name": "Aaron Judge", "pos": "RF"}, "name") → "Aaron Judge"
     *
     * @param json  The flat JSON object string
     * @param key   The field name to look up (without quotes)
     * @return      The field's string value (without surrounding quotes),
     *              or an empty string if the field is not found or has a null value
     */
    private String parseString(String json, String key) {
        // Search for the quoted key followed by colon and optional whitespace
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return ""; // key not present in JSON
        }

        // Find the colon separator after the key
        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) {
            return "";
        }

        // Skip whitespace after the colon to find the start of the value
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        // If the value is not a quoted string (e.g. it's a number or null),
        // return empty — the caller should use parseInt/parseDouble instead.
        if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
            return "";
        }

        // Walk forward from the opening quote to find the closing quote,
        // skipping over any escaped quotes (\") inside the value.
        StringBuilder value = new StringBuilder();
        int i = valueStart + 1; // start after the opening quote
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                // Escaped character — include the unescaped form
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"':  value.append('"');  break;
                    case '\\': value.append('\\'); break;
                    case 'n':  value.append('\n'); break;
                    case 'r':  value.append('\r'); break;
                    case 't':  value.append('\t'); break;
                    default:   value.append(next); break;
                }
                i += 2; // skip the backslash AND the escaped character
            } else if (c == '"') {
                break; // closing quote found — stop
            } else {
                value.append(c);
                i++;
            }
        }
        return value.toString();
    }

    /**
     * Extracts the integer value associated with the given key from a flat JSON object.
     *
     * Looks for the pattern:  "key": <integer>
     * The value must be an unquoted JSON number. Returns 0 if the field is not
     * found or cannot be parsed as an integer.
     *
     * Example:
     *   parseInt({"year": 2024, "topN": 10}, "topN") → 10
     *
     * @param json  The flat JSON object string
     * @param key   The field name to look up (without quotes)
     * @return      The field's integer value, or 0 if not found / unparseable
     */
    private int parseInt(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return 0;
        }
        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) {
            return 0;
        }

        // Skip whitespace after the colon
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        // Collect digits (and optional leading minus sign for negative numbers)
        StringBuilder digits = new StringBuilder();
        int i = valueStart;
        if (i < json.length() && json.charAt(i) == '-') {
            digits.append('-');
            i++;
        }
        while (i < json.length() && Character.isDigit(json.charAt(i))) {
            digits.append(json.charAt(i));
            i++;
        }

        if (digits.length() == 0 || digits.toString().equals("-")) {
            return 0;
        }

        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            logger.warning("Failed to parse integer for key '" + key + "' in JSON\n");
            return 0;
        }
    }

    /**
     * Extracts the double value associated with the given key from a flat JSON object.
     *
     * Looks for the pattern:  "key": <number>
     * Handles both integer (e.g. 0) and decimal (e.g. 0.322) JSON number values.
     * Returns 0.0 if the field is not found or cannot be parsed.
     *
     * Example:
     *   parseDouble({"battingAverage": 0.322}, "battingAverage") → 0.322
     *
     * @param json  The flat JSON object string
     * @param key   The field name to look up (without quotes)
     * @return      The field's double value, or 0.0 if not found / unparseable
     */
    private double parseDouble(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return 0.0;
        }
        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) {
            return 0.0;
        }

        // Skip whitespace after the colon
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        // Collect the number characters: digits, decimal point, minus sign, exponent
        StringBuilder number = new StringBuilder();
        int i = valueStart;
        if (i < json.length() && json.charAt(i) == '-') {
            number.append('-');
            i++;
        }
        while (i < json.length()
                && (Character.isDigit(json.charAt(i))
                    || json.charAt(i) == '.'
                    || json.charAt(i) == 'e'
                    || json.charAt(i) == 'E'
                    || json.charAt(i) == '+'
                    || (json.charAt(i) == '-' && number.length() > 0))) {
            number.append(json.charAt(i));
            i++;
        }

        if (number.length() == 0) {
            return 0.0;
        }

        try {
            return Double.parseDouble(number.toString());
        } catch (NumberFormatException e) {
            logger.warning("Failed to parse double for key '" + key + "' in JSON\n");
            return 0.0;
        }
    }
}
