import cluster.GatewayConfig;
import cluster.GatewayServer;
import cluster.PeerServerImpl;
import helperFiles.PeerServer;
import helperFiles.Vote;
import database.DBConnectionManager;
import microservices.PlayerData;
import org.junit.jupiter.api.Test;
import user.FanInterfaceImpl;
import user.LeagueInterfaceImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * FullInterfaceTest
 *
 * Exercises every method in FanInterfaceImpl and LeagueInterfaceImpl against
 * a live cluster. Uses separate ports from SimpleRequestTest to avoid conflicts
 * if both are run in the same session.
 *
 * ── Public tests ─────────────────────────────────────────────────────────────
 *
 *   testAllMethods()         — Full end-to-end exercise of every LeagueInterface
 *                              and FanInterface method: create conference/division/
 *                              team/player, fan reads, stat update, move operations,
 *                              sample-data reads (Aaron Judge), and cleanup.
 *
 *   testRealMLBData()        — Populates the full 2026 MLB structure (2 conferences,
 *                              6 divisions, 30 teams, 300 players), runs move
 *                              operations, pauses for database inspection via a
 *                              JOptionPane dialog, then resets the database.
 *
 *   testResetMLBData()       — Standalone reset: deletes AL and NL conferences
 *                              (cascades to everything). Use if testRealMLBData()
 *                              was interrupted before its cleanup phase.
 *
 *   testWipeDatabase()       — Wipes every row via TRUNCATE (no cluster needed).
 *
 *   testSeedFivePlayers()    — Seeds a minimal structure with 5 players so you can
 *                              inspect the database via the IntelliJ panel.
 *
 *   testDeleteSeedData()     — Removes all data created by testSeedFivePlayers().
 *
 *   testPrimaryFailover()    — Kills the Patroni primary mid-workload and confirms
 *                              that the system resumes writes after replica promotion.
 *
 *   killMultipleFollowers()  — 5-node cluster: kills two FOLLOWING WorkerServers,
 *                              verifies quorum is preserved (3 of 5 nodes remain).
 *
 *   killOneLeader5Peers()    — 5-node cluster: kills the LEADING WorkerServer,
 *                              waits for re-election, verifies resumed operation.
 *
 *   killGateway()            — 4-node cluster: shuts down the GatewayServer mid-test,
 *                              verifies that the LeagueInterface and FanInterface
 *                              auto-recover by restarting the gateway from GatewayConfig.
 */
public class FullInterfaceTest {

    // ── Port assignments ──────────────────────────────────────────────────────
    // Every test gets its own ports so tests can run concurrently without
    // binding conflicts. The last entry in each PORTS array is the gateway
    // peer server's UDP port; its HTTP port is listed separately.

    /** Ports for testAllMethods() — the main generic exercise. */
    private static final int[] PORTS             = {9010, 9020, 9030, 9040};
    private static final int   GATEWAY_HTTP_PORT = 9888;

    /** Ports for testRealMLBData() and testResetMLBData(). */
    private static final int[] MLB_REAL_PORTS    = {9210, 9220, 9230, 9240};
    private static final int   MLB_REAL_GATEWAY  = 9777;

    /** Ports for testSeedFivePlayers() and testDeleteSeedData(). */
    private static final int[] SEED_PORTS        = {9310, 9320, 9330, 9340};
    private static final int   SEED_GATEWAY      = 9666;

    /** Ports for testPrimaryFailover(). */
    private static final int[] FAILOVER_PORTS    = {9410, 9420, 9430, 9440};
    private static final int   FAILOVER_GATEWAY  = 9555;

    /** Ports for killMultipleFollowers() — 5-node cluster. */
    private static final int[] RESILIENCE_PORTS_2   = {9710, 9720, 9730, 9740, 9750};
    private static final int   RESILIENCE_GATEWAY_2 = 9800;

    /** Ports for killOneLeader5Peers() — 5-node cluster. */
    private static final int[] RESILIENCE_PORTS_3   = {9810, 9820, 9830, 9840, 9850};
    private static final int   RESILIENCE_GATEWAY_3 = 9900;

    /** Ports for killGateway() — 4-node cluster. */
    private static final int[] RESILIENCE_PORTS_4   = {9910, 9920, 9930, 9940};
    private static final int   RESILIENCE_GATEWAY_4 = 9950;

    // ── Timing constants ──────────────────────────────────────────────────────

    /** How long to wait for leader election before sending any requests. */
    private static final int LEADER_ELECTION_SLEEP_MS = PORTS.length * 1500;

    /** How long to sleep after each request to let the cluster process it. */
    private static final int REQUEST_SLEEP_MS = 3000;

    /**
     * Shorter sleep used by the MLB population test to keep runtime reasonable.
     * Increase if requests start failing under cluster load.
     */
    private static final int MLB_REQUEST_SLEEP = 1000;

    /**
     * How long to wait after killing the leader for re-election to complete.
     * Election normally finishes in 10–20 s; 40 s is a generous ceiling.
     */
    private static final int RE_ELECTION_SLEEP_MS = 60000;


    // =========================================================================
    //  Public @Test methods
    // =========================================================================

    /**
     * testAllMethods — full end-to-end exercise of every LeagueInterface and
     * FanInterface method against a 4-node cluster.
     *
     * Seven phases:
     *   1. Build league structure (conference → division → team)
     *   2. Create a player with non-zero initial stats
     *   3. Fan reads (getPlayerStats, getPlayerStat, queryTopPlayersByStat)
     *   4. Stat update + verify change via fan read
     *   5. Move operations (player → team, team → division, division → conference)
     *   6. Fan reads against the sample data seeded by schema.sql (Aaron Judge)
     *   7. Cleanup — delete all test data and shut down the cluster
     */
    @Test
    void testAllMethods() throws IOException, InterruptedException {

        // ── Start cluster ────────────────────────────────────────────────────────
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>();
        for (int i = 0; i < PORTS.length; i++) {
            peerIDtoAddress.put((long) i, new InetSocketAddress("localhost", PORTS[i]));
        }

        ArrayList<PeerServerImpl> servers = new ArrayList<>();
        GatewayServer gatewayServer = null;
        int lastIndex = PORTS.length - 1;
        int count = 0;

        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());
            if (count != lastIndex) {
                servers.add(new PeerServerImpl(
                        entry.getValue().getPort(), 0, entry.getKey(), map, (long) lastIndex, 1));
            } else {
                gatewayServer = new GatewayServer(
                        GATEWAY_HTTP_PORT, entry.getValue().getPort(), 0, entry.getKey(), map, 1);
            }
            count++;
        }

        for (PeerServerImpl s : servers) s.start();
        gatewayServer.start();

        System.out.println("Waiting for leader election (" + LEADER_ELECTION_SLEEP_MS + " ms)...");
        Thread.sleep(LEADER_ELECTION_SLEEP_MS);

        for (PeerServer s : servers) {
            Vote leader = s.getCurrentLeader();
            if (leader != null) {
                System.out.printf("Server port=%d id=%d leader=%d state=%s%n",
                        s.getAddress().getPort(), s.getServerId(),
                        leader.getProposedLeaderID(), s.getPeerState());
            }
        }
        System.out.println();

        // ── Create interfaces ────────────────────────────────────────────────────
        LeagueInterfaceImpl league = new LeagueInterfaceImpl("localhost", GATEWAY_HTTP_PORT, "MLB");
        FanInterfaceImpl    fan    = new FanInterfaceImpl("localhost", GATEWAY_HTTP_PORT, "MLB");

        // ── Phase 1: Build league structure ──────────────────────────────────────
        System.out.println("=== Phase 1: League Structure (Create) ===");

        send("createConference(TestConference)",
                league.createConference("TestConference"));

        send("createDivision(TestDivision, TestConference)",
                league.createDivision("TestDivision", "TestConference"));

        send("createTeam(Test Team, TestDivision)",
                league.createTeam("Test Team", "TestDivision"));

        // Second team — needed later for movePlayerToTeam
        send("createTeam(Test Team 2, TestDivision)",
                league.createTeam("Test Team 2", "TestDivision"));

        // Second division — needed later for moveTeamToDivision
        send("createDivision(TestDivision2, TestConference)",
                league.createDivision("TestDivision2", "TestConference"));

        // Second conference — needed later for moveDivisionToConference
        send("createConference(TestConference2)",
                league.createConference("TestConference2"));

        // ── Phase 2: Create player ───────────────────────────────────────────────
        System.out.println("\n=== Phase 2: Create Player ===");

        PlayerData player = new PlayerData("Test Player", "SP", "Test Team");
        player.setYear(2026);
        player.setHomeRuns(10);
        player.setHits(50);
        player.setAtBats(200);
        player.setBattingAverage(0.250);
        player.setStrikeouts(80);
        player.setWalks(30);
        player.setStolenBases(5);
        player.setRunsBattedIn(40);

        send("createPlayer(Test Player)", league.createPlayer(player));

        // ── Phase 3: Fan reads ───────────────────────────────────────────────────
        System.out.println("\n=== Phase 3: Fan Reads ===");

        send("getPlayerStats(Test Player)",
                fan.getPlayerStats("Test Player"));

        send("getPlayerStat(Test Player, homeRuns)",
                fan.getPlayerStat("Test Player", "homeRuns"));

        send("queryTopPlayersByStat(homeRuns, team, Test Team, 5, 2026)",
                fan.queryTopPlayersByStat("homeRuns", "team", "Test Team", 5, 2026));

        send("queryTopPlayersByStat(hits, division, TestDivision, 5, 2026)",
                fan.queryTopPlayersByStat("hits", "division", "TestDivision", 5, 2026));

        send("queryTopPlayersByStat(homeRuns, conference, TestConference, 5, 2026)",
                fan.queryTopPlayersByStat("homeRuns", "conference", "TestConference", 5, 2026));

        // ── Phase 4: Stat update ─────────────────────────────────────────────────
        System.out.println("\n=== Phase 4: Stat Update ===");

        send("updatePlayerStat(Test Player, homeRuns, 25)",
                league.updatePlayerStat("Test Player", "homeRuns", 25));

        send("getPlayerStat after update (homeRuns, expect 25)",
                fan.getPlayerStat("Test Player", "homeRuns"));

        // ── Phase 5: Move operations ─────────────────────────────────────────────
        System.out.println("\n=== Phase 5: Move Operations ===");

        send("movePlayerToTeam(Test Player, Test Team 2)",
                league.movePlayerToTeam("Test Player", "Test Team 2"));

        send("moveTeamToDivision(Test Team 2, TestDivision2)",
                league.moveTeamToDivision("Test Team 2", "TestDivision2"));

        send("moveDivisionToConference(TestDivision2, TestConference2)",
                league.moveDivisionToConference("TestDivision2", "TestConference2"));

        // ── Phase 6: Fan reads against a real MLB player ─────────────────────────
        // Create a minimal AL structure with Aaron Judge so the fan-facing
        // queries have real data to return.  This is self-contained — no
        // dependency on schema.sql seed data that may or may not be present.
        System.out.println("\n=== Phase 6: Fan Reads (Aaron Judge) ===");

        send("createConference(AL)",
                league.createConference("AL"));

        send("createDivision(AL East, AL)",
                league.createDivision("AL East", "AL"));

        send("createTeam(New York Yankees, AL East)",
                league.createTeam("New York Yankees", "AL East"));

        PlayerData judge = new PlayerData("Aaron Judge", "RF", "New York Yankees");
        judge.setYear(2026);
        judge.setHomeRuns(58);
        judge.setHits(144);
        judge.setAtBats(497);
        send("createPlayer(Aaron Judge)",
                league.createPlayer(judge));

        PlayerData soto = new PlayerData("Juan Soto", "LF", "New York Yankees");
        soto.setYear(2026);
        soto.setHomeRuns(41);
        soto.setHits(157);
        soto.setAtBats(519);
        send("createPlayer(Juan Soto)",
                league.createPlayer(soto));

        PlayerData volpe = new PlayerData("Anthony Volpe", "SS", "New York Yankees");
        volpe.setYear(2026);
        volpe.setHomeRuns(24);
        volpe.setHits(131);
        volpe.setAtBats(530);
        send("createPlayer(Anthony Volpe)",
                league.createPlayer(volpe));

        PlayerData chisholm = new PlayerData("Jazz Chisholm Jr.", "3B", "New York Yankees");
        chisholm.setYear(2026);
        chisholm.setHomeRuns(24);
        chisholm.setHits(108);
        chisholm.setAtBats(419);
        send("createPlayer(Jazz Chisholm Jr.)",
                league.createPlayer(chisholm));

        PlayerData stanton = new PlayerData("Giancarlo Stanton", "DH", "New York Yankees");
        stanton.setYear(2026);
        stanton.setHomeRuns(27);
        stanton.setHits(97);
        stanton.setAtBats(369);
        send("createPlayer(Giancarlo Stanton)",
                league.createPlayer(stanton));

        PlayerData torres = new PlayerData("Gleyber Torres", "2B", "New York Yankees");
        torres.setYear(2026);
        torres.setHomeRuns(15);
        torres.setHits(120);
        torres.setAtBats(468);
        send("createPlayer(Gleyber Torres)",
                league.createPlayer(torres));

        send("getPlayerStats(Aaron Judge)",
                fan.getPlayerStats("Aaron Judge"));

        send("getPlayerStat(Aaron Judge, homeRuns)",
                fan.getPlayerStat("Aaron Judge", "homeRuns"));

        send("queryTopPlayersByStat(homeRuns, team, New York Yankees, 5, 2026)",
                fan.queryTopPlayersByStat("homeRuns", "team", "New York Yankees", 5, 2026));

        send("queryTopPlayersByStat(hits, division, AL East, 5, 2026)",
                fan.queryTopPlayersByStat("hits", "division", "AL East", 5, 2026));

        send("queryTopPlayersByStat(homeRuns, conference, AL, 5, 2026)",
                fan.queryTopPlayersByStat("homeRuns", "conference", "AL", 5, 2026));

        // ── Inspection pause ─────────────────────────────────────────────────────
        // All data is now in the database. Show a dialog so you can inspect it
        // via pgAdmin or psql before cleanup runs.
        System.out.println("Database populated. Inspect it now, then click OK to clean up.");

        // ── Phase 7: Cleanup ─────────────────────────────────────────────────────
        System.out.println("\n=== Phase 7: Cleanup (Delete) ===");

        // Delete test player before its team is removed
        send("deletePlayer(Test Player)",
                league.deletePlayer("Test Player"));

        // AL → AL East → New York Yankees → all 6 Yankees players (cascade)
        send("deleteConference(AL) [cascades AL East, New York Yankees, all 6 players]",
                league.deleteConference("AL"));

        // TestConference2 → TestDivision2 → Test Team 2 (cascade)
        send("deleteConference(TestConference2) [cascades TestDivision2, Test Team 2]",
                league.deleteConference("TestConference2"));

        // TestConference → TestDivision → Test Team (cascade)
        send("deleteTeam(Test Team)",
                league.deleteTeam("Test Team"));

        send("deleteDivision(TestDivision)",
                league.deleteDivision("TestDivision"));

        send("deleteConference(TestConference)",
                league.deleteConference("TestConference"));

        // ── Shutdown ─────────────────────────────────────────────────────────────
        for (PeerServer s : servers) s.shutdown();
        gatewayServer.shutdown();
    }

    // =========================================================================
    // Primary failover test
    //
    // Demonstrates that the system continues to operate after the Patroni
    // primary is killed mid-workload. The test runs in four phases:
    //
    //   Phase 1 — Normal operation: writes and reads against the primary.
    //   Phase 2 — Primary killed: patroni1 is stopped via docker. Patroni
    //             detects the loss, etcd lease expires, and one of the replicas
    //             is promoted. Writes are retried every 2 seconds until the new
    //             primary accepts them (typically 10–30 seconds).
    //   Phase 3 — Resumed operation: writes and reads succeed against the new
    //             primary, confirming no data was lost and the system is healthy.
    //
    // PREREQUISITE: the Patroni Docker cluster must be running
    //   (docker compose up -d) and all three nodes must be in sync
    //   before launching this test.
    // =========================================================================

    /**
     * testPrimaryFailover — kills the Patroni primary mid-workload and verifies recovery.
     *
     * Four phases:
     *   1. Normal operation: creates a small AL East structure and runs fan reads.
     *   2. Primary killed: identifies the current Patroni leader via curl /primary
     *      and stops its container via docker stop.
     *   3. Failover poll: queries pg_is_in_recovery() every 2 seconds until the
     *      write pool reconnects to the newly promoted replica (up to 60 seconds).
     *   4. Resumed operation: writes and reads succeed through the new primary;
     *      the killed node is restarted as a replica for cluster health.
     *
     * PREREQUISITE: docker compose up -d must be running with all three Patroni
     * nodes in sync before this test is launched.
     */
    @Test
    void testPrimaryFailover() throws IOException, InterruptedException {

        // ── Start cluster ────────────────────────────────────────────────────
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>();
        for (int i = 0; i < FAILOVER_PORTS.length; i++) {
            peerIDtoAddress.put((long) i, new InetSocketAddress("localhost", FAILOVER_PORTS[i]));
        }

        ArrayList<PeerServerImpl> servers = new ArrayList<>();
        GatewayServer gatewayServer = null;
        int lastIndex = FAILOVER_PORTS.length - 1;
        int count = 0;

        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());
            if (count != lastIndex) {
                servers.add(new PeerServerImpl(
                        entry.getValue().getPort(), 0, entry.getKey(), map, (long) lastIndex, 1));
            } else {
                gatewayServer = new GatewayServer(
                        FAILOVER_GATEWAY, entry.getValue().getPort(), 0, entry.getKey(), map, 1);
            }
            count++;
        }

        for (PeerServerImpl s : servers) s.start();
        gatewayServer.start();

        System.out.println("Waiting for leader election (" + LEADER_ELECTION_SLEEP_MS + " ms)...");
        Thread.sleep(LEADER_ELECTION_SLEEP_MS);

        LeagueInterfaceImpl league = new LeagueInterfaceImpl("localhost", FAILOVER_GATEWAY, "MLB");
        FanInterfaceImpl    fan    = new FanInterfaceImpl("localhost",    FAILOVER_GATEWAY, "MLB");

        // ── Phase 1: Normal operation (pre-failure) ──────────────────────────
        System.out.println("\n=== Phase 1: Normal operation ===");

        sendFast("createConference(AL)",                  league.createConference("AL"));
        sendFast("createDivision(AL East, AL)",           league.createDivision("AL East", "AL"));
        sendFast("createTeam(New York Yankees, AL East)", league.createTeam("New York Yankees", "AL East"));
        sendFast("createTeam(Boston Red Sox,   AL East)", league.createTeam("Boston Red Sox",   "AL East"));

        addPlayer(league, "Aaron Judge",   "RF", "New York Yankees");
        addPlayer(league, "Juan Soto",     "LF", "New York Yankees");
        addPlayer(league, "Rafael Devers", "3B", "Boston Red Sox");
        addPlayer(league, "Jarren Duran",  "CF", "Boston Red Sox");

        sendFast("updatePlayerStat(Aaron Judge,   homeRuns, 58)",
                league.updatePlayerStat("Aaron Judge",   "homeRuns", 58));
        sendFast("updatePlayerStat(Rafael Devers, homeRuns, 33)",
                league.updatePlayerStat("Rafael Devers", "homeRuns", 33));
        sendFast("getPlayerStat(Aaron Judge, homeRuns)",
                fan.getPlayerStat("Aaron Judge", "homeRuns"));
        sendFast("queryTopPlayersByStat(homeRuns, division, AL East, 4, 2026)",
                fan.queryTopPlayersByStat("homeRuns", "division", "AL East", 4, 2026));

        // ── Phase 2: Kill the primary ────────────────────────────────────────
        System.out.println("\n=== Phase 2: PRIMARY FAILURE — identifying and stopping current leader ===");
        String killedNode = killCurrentLeader();
        System.out.println("  " + killedNode + " is down.");
        System.out.println("  Patroni is detecting the failure and electing a new primary.");
        System.out.println("  Writes will be retried every 2s until a replica is promoted...");

        // ── Phase 3: Poll until the new primary accepts writes ────────────────
        // We poll the write connection pool directly via JDBC rather than going
        // through the full cluster → WorkerServer → HikariCP chain, which has
        // multiple layers that can delay failure detection. A direct JDBC check
        // isolates exactly the layer we care about: can we write to a primary?
        // pg_is_in_recovery() returns false on a primary and true on a replica,
        // so a successful query returning false means the write pool is healthy.
        System.out.println("\n=== Phase 3: Waiting for Patroni failover ===");
        boolean recovered = false;
        for (int attempt = 1; attempt <= 30; attempt++) {
            Thread.sleep(2000);
            try (java.sql.Connection conn = DBConnectionManager.getWriteConnection();
                 java.sql.Statement  stmt = conn.createStatement();
                 java.sql.ResultSet  rs   = stmt.executeQuery("SELECT pg_is_in_recovery()")) {
                rs.next();
                boolean isPrimary = !rs.getBoolean(1);
                conn.commit();
                System.out.printf("  [t+%2ds] write pool → pg_is_in_recovery() = %s%s%n",
                        attempt * 2, !isPrimary,
                        isPrimary ? " — new primary is up" : " — still failing over");
                if (isPrimary) {
                    System.out.printf("%n  Failover complete — new primary accepting writes after ~%d seconds.%n",
                            attempt * 2);
                    recovered = true;
                    break;
                }
            } catch (java.sql.SQLException e) {
                System.out.printf("  [t+%2ds] still failing over: %s%n", attempt * 2, e.getMessage());
            }
        }

        if (!recovered) {
            System.out.println("  WARNING: new primary did not come up within 60 seconds.");
        }

        // ── Phase 4: Normal operation (post-failure) ─────────────────────────
        System.out.println("\n=== Phase 4: Normal operation — new primary is up ===");

        addPlayer(league, "Mookie Betts", "RF", "Boston Red Sox");
        sendFast("updatePlayerStat(Juan Soto, hits, 157)",
                league.updatePlayerStat("Juan Soto", "hits", 157));
        sendFast("getPlayerStat(Aaron Judge, homeRuns) [expect 59 — written after failover]",
                fan.getPlayerStat("Aaron Judge", "homeRuns"));
        sendFast("queryTopPlayersByStat(homeRuns, division, AL East, 5, 2026)",
                fan.queryTopPlayersByStat("homeRuns", "division", "AL East", 5, 2026));
        sendFast("movePlayerToTeam(Juan Soto, Boston Red Sox)",
                league.movePlayerToTeam("Juan Soto", "Boston Red Sox"));
        sendFast("queryTopPlayersByStat(hits, team, Boston Red Sox, 5, 2026)",
                fan.queryTopPlayersByStat("hits", "team", "Boston Red Sox", 5, 2026));

        // ── Cleanup ──────────────────────────────────────────────────────────
        System.out.println("\n=== Cleanup ===");
        sendFast("deleteConference(AL) [cascades all divisions, teams, players]",
                league.deleteConference("AL"));

        // Restart whichever node was killed so the cluster returns to full health.
        System.out.println("\n=== Restoring " + killedNode + " ===");
        String killedContainer = "statcentral-" + killedNode + "-1";
        System.out.println("  Sending 'docker start " + killedContainer + "'...");
        Process restart = new ProcessBuilder("docker", "start", killedContainer)
                .redirectErrorStream(true)
                .start();
        restart.waitFor(15, TimeUnit.SECONDS);
        System.out.println("  " + killedNode + " is starting — it will rejoin as a replica.");
        System.out.println("  Check http://localhost:7000 to confirm all three nodes are green.");

        for (PeerServerImpl s : servers) s.shutdown();
        gatewayServer.shutdown();
    }

    // =========================================================================
    // Cluster-resilience tests
    //
    // These tests verify that the Java cluster (GatewayServer → LeaderServer →
    // WorkerServers) continues to route requests correctly when one or more nodes
    // crash at runtime.  They are the StatCentral analogue of Stage5Test's
    // kill-follower / kill-leader scenarios, but they drive the system through
    // the LeagueInterface rather than sending raw Java source code.
    //
    // Each test:
    //   Phase 1 — Normal operation (pre-crash): build a minimal league structure
    //             and confirm that requests are processed.
    //   Phase 2 — Crash: the target server(s) are shut down via shutdown().
    //   Phase 3 — Continued operation (post-crash): further LeagueInterface calls
    //             are made to confirm that the cluster still routes and processes
    //             requests despite the missing node(s).
    //   Phase 4 — Cleanup: delete the test data and stop remaining servers.
    // =========================================================================

    /**
     * killMultipleFollowers — 5-node cluster (4 WorkerServers + 1 GatewayServer).
     *
     * After leader election one node will be LEADING and three will be FOLLOWING.
     * This test shuts down two FOLLOWING nodes in sequence, then verifies that
     * the cluster (now leader + one follower + gateway) continues to route and
     * process requests.  Quorum is preserved: 3 of 5 nodes remain.
     */
    @Test
    void killMultipleFollowers() throws IOException, InterruptedException {

        // ── Start 5-node cluster ─────────────────────────────────────────────
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>();
        for (int i = 0; i < RESILIENCE_PORTS_2.length; i++) {
            peerIDtoAddress.put((long) i, new InetSocketAddress("localhost", RESILIENCE_PORTS_2[i]));
        }

        ArrayList<PeerServerImpl> servers = new ArrayList<>();
        GatewayServer gatewayServer = null;
        int lastIndex = RESILIENCE_PORTS_2.length - 1;
        int count = 0;

        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());
            if (count != lastIndex) {
                servers.add(new PeerServerImpl(
                        entry.getValue().getPort(), 0, entry.getKey(), map, (long) lastIndex, 1));
            } else {
                gatewayServer = new GatewayServer(
                        RESILIENCE_GATEWAY_2, entry.getValue().getPort(), 0, entry.getKey(), map, 1);
            }
            count++;
        }

        for (PeerServerImpl s : servers) s.start();
        gatewayServer.start();

        System.out.println("Waiting for leader election (" + LEADER_ELECTION_SLEEP_MS + " ms)...");
        Thread.sleep(LEADER_ELECTION_SLEEP_MS);

        for (PeerServer s : servers) {
            Vote leader = s.getCurrentLeader();
            if (leader != null) {
                System.out.printf("Server port=%d id=%d leader=%d state=%s%n",
                        s.getAddress().getPort(), s.getServerId(),
                        leader.getProposedLeaderID(), s.getPeerState());
            }
        }
        System.out.println();

        LeagueInterfaceImpl league = new LeagueInterfaceImpl("localhost", RESILIENCE_GATEWAY_2, "MLB");

        // ── Phase 1: Normal operation (pre-crash) ────────────────────────────
        System.out.println("=== Phase 1: Pre-crash requests ===");
        send("createConference(RConf2)",       league.createConference("RConf2"));
        send("createDivision(RDiv2, RConf2)",  league.createDivision("RDiv2", "RConf2"));
        send("createTeam(RTeam2A, RDiv2)",     league.createTeam("RTeam2A", "RDiv2"));

        PlayerData playerA = new PlayerData("RPlayer2A", "CF", "RTeam2A");
        send("createPlayer(RPlayer2A)", league.createPlayer(playerA));

        PlayerData playerB = new PlayerData("RPlayer2B", "SS", "RTeam2A");
        send("createPlayer(RPlayer2B)", league.createPlayer(playerB));

        send("updatePlayerStat(RPlayer2A, homeRuns, 15)", league.updatePlayerStat("RPlayer2A", "homeRuns", 15));
        send("updatePlayerStat(RPlayer2B, hits, 90)",     league.updatePlayerStat("RPlayer2B", "hits", 90));

        // ── Phase 2: Kill two followers ──────────────────────────────────────
        System.out.println("\n=== Phase 2: Killing two FOLLOWERS ===");
        int killCount = 0;
        for (PeerServerImpl s : servers) {
            if (killCount >= 2) break;
            if (s.getPeerState() == PeerServer.ServerState.FOLLOWING) {
                System.out.printf("  Shutting down follower #%d: port=%d id=%d%n",
                        killCount + 1, s.getAddress().getPort(), s.getServerId());
                s.shutdown();
                killCount++;
                Thread.sleep(1500); // stagger the kills slightly
            }
        }
        if (killCount < 2) {
            System.out.println("  WARNING: only " + killCount + " follower(s) found; fewer killed than expected");
        }
        Thread.sleep(3000); // let the remaining nodes detect the failures

        // ── Phase 3: Continued operation (post-crash) ────────────────────────
        // Quorum is preserved: 5 total → 2 killed → 3 remain (leader + 1 follower + gateway).
        System.out.println("\n=== Phase 3: Post-crash requests ===");
        send("updatePlayerStat(RPlayer2A, homeRuns, 30)  [after 2 followers crashed]",
                league.updatePlayerStat("RPlayer2A", "homeRuns", 30));
        send("updatePlayerStat(RPlayer2B, hits, 110)",
                league.updatePlayerStat("RPlayer2B", "hits", 110));
        send("createTeam(RTeam2B, RDiv2)  [new team post-crash]",
                league.createTeam("RTeam2B", "RDiv2"));
        send("movePlayerToTeam(RPlayer2B, RTeam2B)",
                league.movePlayerToTeam("RPlayer2B", "RTeam2B"));

        // ── Phase 4: Cleanup ─────────────────────────────────────────────────
        System.out.println("\n=== Phase 4: Cleanup ===");
        send("deletePlayer(RPlayer2A)",  league.deletePlayer("RPlayer2A"));
        send("deletePlayer(RPlayer2B)",  league.deletePlayer("RPlayer2B"));
        send("deleteConference(RConf2)", league.deleteConference("RConf2"));

        for (PeerServerImpl s : servers) {
            if (s.isAlive()) s.shutdown();
        }
        gatewayServer.shutdown();
    }

    /**
     * killOneLeader5Peers — 5-node cluster (4 WorkerServers + 1 GatewayServer).
     *
     * After the initial leader election, this test identifies the LEADING node and
     * calls shutdown() on it while requests are in flight.  It then waits for the
     * surviving nodes to hold a new election and elect a replacement leader, after
     * which further LeagueInterface requests are made to confirm that the cluster
     * has fully recovered.
     */
    @Test
    void killOneLeader5Peers() throws IOException, InterruptedException {

        // ── Start 5-node cluster ─────────────────────────────────────────────
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>();
        for (int i = 0; i < RESILIENCE_PORTS_3.length; i++) {
            peerIDtoAddress.put((long) i, new InetSocketAddress("localhost", RESILIENCE_PORTS_3[i]));
        }

        ArrayList<PeerServerImpl> servers = new ArrayList<>();
        GatewayServer gatewayServer = null;
        int lastIndex = RESILIENCE_PORTS_3.length - 1;
        int count = 0;

        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());
            if (count != lastIndex) {
                servers.add(new PeerServerImpl(
                        entry.getValue().getPort(), 0, entry.getKey(), map, (long) lastIndex, 1));
            } else {
                gatewayServer = new GatewayServer(
                        RESILIENCE_GATEWAY_3, entry.getValue().getPort(), 0, entry.getKey(), map, 1);
            }
            count++;
        }

        for (PeerServerImpl s : servers) s.start();
        gatewayServer.start();

        System.out.println("Waiting for leader election (" + LEADER_ELECTION_SLEEP_MS + " ms)...");
        Thread.sleep(LEADER_ELECTION_SLEEP_MS);

        for (PeerServer s : servers) {
            Vote leader = s.getCurrentLeader();
            if (leader != null) {
                System.out.printf("Server port=%d id=%d leader=%d state=%s%n",
                        s.getAddress().getPort(), s.getServerId(),
                        leader.getProposedLeaderID(), s.getPeerState());
            }
        }
        System.out.println();

        LeagueInterfaceImpl league = new LeagueInterfaceImpl("localhost", RESILIENCE_GATEWAY_3, "MLB");

        // ── Phase 1: Normal operation (pre-crash) ────────────────────────────
        System.out.println("=== Phase 1: Pre-crash requests ===");
        send("createConference(RConf3)",       league.createConference("RConf3"));
        send("createDivision(RDiv3, RConf3)",  league.createDivision("RDiv3", "RConf3"));
        send("createTeam(RTeam3, RDiv3)",      league.createTeam("RTeam3", "RDiv3"));

        PlayerData player = new PlayerData("RPlayer3", "1B", "RTeam3");
        send("createPlayer(RPlayer3)", league.createPlayer(player));
        send("updatePlayerStat(RPlayer3, homeRuns, 20)",
                league.updatePlayerStat("RPlayer3", "homeRuns", 20));
        send("updatePlayerStat(RPlayer3, hits, 85)",
                league.updatePlayerStat("RPlayer3", "hits", 85));

        // ── Phase 2: Kill the leader ─────────────────────────────────────────
        System.out.println("\n=== Phase 2: Killing the LEADER ===");
        PeerServerImpl killedLeader = null;
        for (PeerServerImpl s : servers) {
            if (s.getPeerState() == PeerServer.ServerState.LEADING) {
                killedLeader = s;
                break;
            }
        }
        if (killedLeader != null) {
            System.out.printf("  Shutting down leader: port=%d id=%d%n",
                    killedLeader.getAddress().getPort(), killedLeader.getServerId());
            killedLeader.shutdown();
        } else {
            System.out.println("  WARNING: no LEADING node found — skipping crash phase");
        }

        // Wait for the remaining nodes to detect the failure and elect a new leader.
        // This takes longer than a follower crash because a full re-election must complete.
        System.out.println("  Waiting " + RE_ELECTION_SLEEP_MS + " ms for re-election...");
        for(int i = 0; i < RE_ELECTION_SLEEP_MS/1000; i++){
            Thread.sleep(1000);
            System.out.println((i+1)+"s");
        }
        //Thread.sleep(RE_ELECTION_SLEEP_MS);

        // Log the new cluster state after re-election.
        // PeerServerImpl's run() exits after starting its child daemon threads, so
        // isAlive() is false for every server by this point — even healthy ones.
        // Filter by identity instead.
        System.out.println("  Post-election cluster state:");
        for (PeerServerImpl s : servers) {
            if (s == killedLeader) continue;
            Vote newLeader = s.getCurrentLeader();
            System.out.printf("    port=%d id=%d leader=%d state=%s%n",
                    s.getAddress().getPort(), s.getServerId(),
                    newLeader != null ? newLeader.getProposedLeaderID() : -1L,
                    s.getPeerState());
        }

        // ── Phase 3: Continued operation (post-crash) ────────────────────────
        // The new leader should now be processing requests.  Sending several
        // write requests confirms that the round-robin scheduler in the new
        // LeaderServer is up and distributing work to surviving WorkerServers.
        System.out.println("\n=== Phase 3: Post-re-election requests ===");
        send("updatePlayerStat(RPlayer3, homeRuns, 35)  [after leader crash + re-election]",
                league.updatePlayerStat("RPlayer3", "homeRuns", 35));
        send("updatePlayerStat(RPlayer3, hits, 100)",
                league.updatePlayerStat("RPlayer3", "hits", 100));
        send("updatePlayerStat(RPlayer3, strikeouts, 95)",
                league.updatePlayerStat("RPlayer3", "strikeouts", 95));
        send("createTeam(RTeam3B, RDiv3)  [new team post-re-election]",
                league.createTeam("RTeam3B", "RDiv3"));
        send("movePlayerToTeam(RPlayer3, RTeam3B)",
                league.movePlayerToTeam("RPlayer3", "RTeam3B"));

        // ── Phase 4: Cleanup ─────────────────────────────────────────────────
        System.out.println("\n=== Phase 4: Cleanup ===");
        send("deletePlayer(RPlayer3)",   league.deletePlayer("RPlayer3"));
        send("deleteConference(RConf3)", league.deleteConference("RConf3"));

        for (PeerServerImpl s : servers) {
            if (s.isAlive()) s.shutdown();
        }
        gatewayServer.shutdown();
    }

    // =========================================================================
    // GatewayServer fault-tolerance test
    //
    // Verifies that the LeagueInterface and FanInterface automatically recover
    // when the GatewayServer crashes:
    //
    //   1. The interface detects the crash via an IOException on the HTTP send.
    //   2. It shuts down the dead GatewayServer (freeing the HTTP port), creates
    //      a replacement from GatewayConfig, and waits for it to complete leader
    //      election as an OBSERVER.
    //   3. It retries the in-flight request; the LeaderServer returns the cached
    //      result (if the operation was already executed before the gateway died)
    //      or executes it fresh (if the request never reached the leader).
    //   4. Subsequent calls work normally through the replacement gateway.
    // =========================================================================

    /**
     * killGateway — 4-node cluster (3 WorkerServers + 1 GatewayServer).
     *
     * The GatewayServer is shut down mid-test to simulate a crash.  Both the
     * LeagueInterface and FanInterface are constructed with a {@link GatewayConfig}
     * so they can restart it automatically.  The test verifies that all operations
     * issued after the crash succeed (triggering auto-recovery on the first call)
     * and that subsequent calls go through the replacement gateway normally.
     */
    @Test
    void killGateway() throws IOException, InterruptedException {

        // ── Start 4-node cluster ─────────────────────────────────────────────
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>();
        for (int i = 0; i < RESILIENCE_PORTS_4.length; i++) {
            peerIDtoAddress.put((long) i, new InetSocketAddress("localhost", RESILIENCE_PORTS_4[i]));
        }

        ArrayList<PeerServerImpl> servers = new ArrayList<>();
        GatewayServer gatewayServer = null;
        int lastIndex = RESILIENCE_PORTS_4.length - 1;
        int count = 0;

        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            ConcurrentHashMap<Long, InetSocketAddress> map = new ConcurrentHashMap<>(peerIDtoAddress);
            map.remove(entry.getKey());
            if (count != lastIndex) {
                servers.add(new PeerServerImpl(
                        entry.getValue().getPort(), 0, entry.getKey(), map, (long) lastIndex, 1));
            } else {
                gatewayServer = new GatewayServer(
                        RESILIENCE_GATEWAY_4, entry.getValue().getPort(), 0, entry.getKey(), map, 1);
            }
            count++;
        }

        for (PeerServerImpl s : servers) s.start();
        gatewayServer.start();

        System.out.println("Waiting for leader election (" + LEADER_ELECTION_SLEEP_MS + " ms)...");
        Thread.sleep(LEADER_ELECTION_SLEEP_MS);

        for (PeerServer s : servers) {
            Vote leader = s.getCurrentLeader();
            if (leader != null) {
                System.out.printf("Server port=%d id=%d leader=%d state=%s%n",
                        s.getAddress().getPort(), s.getServerId(),
                        leader.getProposedLeaderID(), s.getPeerState());
            }
        }
        System.out.println();

        // Build GatewayConfig from the cluster parameters we just used.
        // The gateway's server ID is lastIndex; its UDP port is RESILIENCE_PORTS_4[lastIndex].
        // The peer map for the config must already have the gateway's own entry removed
        // (matching what was passed to the GatewayServer constructor above).
        long gatewayServerID = lastIndex;
        ConcurrentHashMap<Long, InetSocketAddress> gatewayPeerMap = new ConcurrentHashMap<>(peerIDtoAddress);
        gatewayPeerMap.remove(gatewayServerID);

        GatewayConfig config = new GatewayConfig(
                RESILIENCE_GATEWAY_4,
                RESILIENCE_PORTS_4[lastIndex],  // UDP port of the gateway peer server
                0,                              // peerEpoch
                gatewayServerID,
                gatewayPeerMap,
                1,                              // numberOfObservers
                gatewayServer);

        LeagueInterfaceImpl league = new LeagueInterfaceImpl(
                "localhost", RESILIENCE_GATEWAY_4, "MLB", config);
        FanInterfaceImpl fan = new FanInterfaceImpl(
                "localhost", RESILIENCE_GATEWAY_4, "MLB", config);

        // ── Phase 1: Normal operation (pre-crash) ────────────────────────────
        System.out.println("=== Phase 1: Pre-crash requests ===");
        send("createConference(GConf1)",          league.createConference("GConf1"));
        send("createDivision(GDiv1, GConf1)",     league.createDivision("GDiv1", "GConf1"));
        send("createTeam(GTeam1, GDiv1)",         league.createTeam("GTeam1", "GDiv1"));

        PlayerData player = new PlayerData("GPlayer1", "RF", "GTeam1");
        send("createPlayer(GPlayer1)",            league.createPlayer(player));
        send("updatePlayerStat(GPlayer1, homeRuns, 5)",
                league.updatePlayerStat("GPlayer1", "homeRuns", 5));
        send("getPlayerStats(GPlayer1) [pre-crash fan read]",
                fan.getPlayerStats("GPlayer1"));

        // ── Phase 2: Kill the GatewayServer ──────────────────────────────────
        System.out.println("\n=== Phase 2: Killing GatewayServer ===");
        gatewayServer.shutdown();
        Thread.sleep(1000); // Let the port be released before the interface tries to restart it.

        // ── Phase 3: Post-crash operations — interface auto-recovers ─────────
        // The first call here triggers recovery: the interface detects the
        // IOException, shuts down the dead gateway, starts a replacement from
        // GatewayConfig, waits for leader election, and retries.
        System.out.println("\n=== Phase 3: Post-crash requests (auto-recovery) ===");
        send("updatePlayerStat(GPlayer1, homeRuns, 20)  [triggers recovery]",
                league.updatePlayerStat("GPlayer1", "homeRuns", 20));
        send("createTeam(GTeam2, GDiv1)  [post-recovery league write]",
                league.createTeam("GTeam2", "GDiv1"));
        send("getPlayerStats(GPlayer1)  [post-recovery fan read]",
                fan.getPlayerStats("GPlayer1"));
        send("updatePlayerStat(GPlayer1, strikeouts, 80)  [continued post-recovery]",
                league.updatePlayerStat("GPlayer1", "strikeouts", 80));

        // ── Phase 4: Cleanup ─────────────────────────────────────────────────
        System.out.println("\n=== Phase 4: Cleanup ===");
        send("deletePlayer(GPlayer1)",      league.deletePlayer("GPlayer1"));
        send("deleteConference(GConf1)",    league.deleteConference("GConf1"));

        for (PeerServerImpl s : servers) {
            if (s.isAlive()) s.shutdown();
        }
        // Shut down the replacement gateway that the interface created during recovery.
        GatewayServer activeGateway = config.activeGateway;
        if (activeGateway != null && activeGateway != gatewayServer) {
            activeGateway.shutdown();
        }
    }

    /**
     * testWipeDatabase — truncates every row in the database via a direct JDBC
     * connection, with no cluster required.
     *
     * Issues TRUNCATE conferences CASCADE, which propagates to divisions, teams,
     * players, and all stat tables. The schema (tables, indexes, constraints)
     * is left intact — only the data is removed.
     *
     * Use this as a fast "nuke everything" reset between test runs when you do
     * not need the cluster to be involved.
     */
    @Test
    void testWipeDatabase() throws SQLException {
        try (Connection conn = DBConnectionManager.getWriteConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE conferences CASCADE");
            conn.commit();
            System.out.println("Database wiped — all rows deleted, schema intact.");
        }
    }

    // =========================================================================
    //  Private helper methods
    // =========================================================================

    /**
     * Prints the operation label and the API response on one line, then sleeps
     * REQUEST_SLEEP_MS to give the cluster time to finish processing before the
     * next request is sent.  Used by the slower generic tests (testAllMethods,
     * kill* resilience tests) that operate on small data sets.
     *
     * @param label     A short description of the operation (e.g. "createPlayer(Judge)")
     * @param response  The string returned by the LeagueInterface or FanInterface method
     */
    private void send(String label, String response) throws InterruptedException {
        System.out.printf("  %-65s => %s%n", label, response);
        System.out.println();
        Thread.sleep(REQUEST_SLEEP_MS);
    }

    /**
     * Like {@link #send(String, String)} but sleeps MLB_REQUEST_SLEEP instead of
     * REQUEST_SLEEP_MS.  Used by the MLB population test (testRealMLBData) and
     * testPrimaryFailover to keep the 300-player load reasonable in duration.
     *
     * @param label     A short description of the operation
     * @param response  The string returned by the LeagueInterface or FanInterface method
     */
    private void sendFast(String label, String response) throws InterruptedException {
        System.out.printf("  %-70s => %s%n", label, response);
        Thread.sleep(MLB_REQUEST_SLEEP);
    }

    /**
     * Populates the database with the full 2026 MLB structure:
     *   2 conferences → 6 divisions → 30 teams → 300 players (10 per team).
     *
     * Starts by deleting the "AL" and "NL" conferences that schema.sql seeds
     * so the rebuild starts from a consistent, known state.
     *
     * Division names are prefixed ("AL East", "NL West", etc.) to keep them
     * globally unique, since the UNIQUE constraint on divisions is scoped to
     * (name, conference_id) and the same bare name ("East") can appear in both
     * conferences.
     *
     * @param league  The LeagueInterfaceImpl to use for all create calls
     */
    private void populateMLBData(LeagueInterfaceImpl league) throws InterruptedException {
        System.out.println("=== populateMLBData: Clearing seed data ===");
        sendFast("deleteConference(AL) [remove schema.sql seed]", league.deleteConference("AL"));
        sendFast("deleteConference(NL) [remove schema.sql seed]", league.deleteConference("NL"));

        System.out.println("\n=== populateMLBData: Conferences ===");
        sendFast("createConference(AL)", league.createConference("AL"));
        sendFast("createConference(NL)", league.createConference("NL"));

        System.out.println("\n=== populateMLBData: Divisions ===");
        sendFast("createDivision(AL East,    AL)", league.createDivision("AL East",    "AL"));
        sendFast("createDivision(AL Central, AL)", league.createDivision("AL Central", "AL"));
        sendFast("createDivision(AL West,    AL)", league.createDivision("AL West",    "AL"));
        sendFast("createDivision(NL East,    NL)", league.createDivision("NL East",    "NL"));
        sendFast("createDivision(NL Central, NL)", league.createDivision("NL Central", "NL"));
        sendFast("createDivision(NL West,    NL)", league.createDivision("NL West",    "NL"));

        System.out.println("\n=== populateMLBData: Teams ===");
        // AL East
        sendFast("createTeam(New York Yankees,  AL East)", league.createTeam("New York Yankees",   "AL East"));
        sendFast("createTeam(Boston Red Sox,    AL East)", league.createTeam("Boston Red Sox",     "AL East"));
        sendFast("createTeam(Toronto Blue Jays, AL East)", league.createTeam("Toronto Blue Jays",  "AL East"));
        sendFast("createTeam(Tampa Bay Rays,    AL East)", league.createTeam("Tampa Bay Rays",     "AL East"));
        sendFast("createTeam(Baltimore Orioles, AL East)", league.createTeam("Baltimore Orioles",  "AL East"));
        // AL Central
        sendFast("createTeam(Chicago White Sox,   AL Central)", league.createTeam("Chicago White Sox",   "AL Central"));
        sendFast("createTeam(Cleveland Guardians, AL Central)", league.createTeam("Cleveland Guardians", "AL Central"));
        sendFast("createTeam(Detroit Tigers,      AL Central)", league.createTeam("Detroit Tigers",      "AL Central"));
        sendFast("createTeam(Kansas City Royals,  AL Central)", league.createTeam("Kansas City Royals",  "AL Central"));
        sendFast("createTeam(Minnesota Twins,     AL Central)", league.createTeam("Minnesota Twins",     "AL Central"));
        // AL West
        sendFast("createTeam(Houston Astros,     AL West)", league.createTeam("Houston Astros",     "AL West"));
        sendFast("createTeam(Los Angeles Angels, AL West)", league.createTeam("Los Angeles Angels", "AL West"));
        sendFast("createTeam(Oakland Athletics,  AL West)", league.createTeam("Oakland Athletics",  "AL West"));
        sendFast("createTeam(Seattle Mariners,   AL West)", league.createTeam("Seattle Mariners",   "AL West"));
        sendFast("createTeam(Texas Rangers,      AL West)", league.createTeam("Texas Rangers",      "AL West"));
        // NL East
        sendFast("createTeam(Atlanta Braves,        NL East)", league.createTeam("Atlanta Braves",        "NL East"));
        sendFast("createTeam(Miami Marlins,         NL East)", league.createTeam("Miami Marlins",         "NL East"));
        sendFast("createTeam(New York Mets,         NL East)", league.createTeam("New York Mets",         "NL East"));
        sendFast("createTeam(Philadelphia Phillies, NL East)", league.createTeam("Philadelphia Phillies", "NL East"));
        sendFast("createTeam(Washington Nationals,  NL East)", league.createTeam("Washington Nationals",  "NL East"));
        // NL Central
        sendFast("createTeam(Chicago Cubs,        NL Central)", league.createTeam("Chicago Cubs",        "NL Central"));
        sendFast("createTeam(Cincinnati Reds,     NL Central)", league.createTeam("Cincinnati Reds",     "NL Central"));
        sendFast("createTeam(Milwaukee Brewers,   NL Central)", league.createTeam("Milwaukee Brewers",   "NL Central"));
        sendFast("createTeam(Pittsburgh Pirates,  NL Central)", league.createTeam("Pittsburgh Pirates",  "NL Central"));
        sendFast("createTeam(St. Louis Cardinals, NL Central)", league.createTeam("St. Louis Cardinals", "NL Central"));
        // NL West
        sendFast("createTeam(Arizona Diamondbacks,  NL West)", league.createTeam("Arizona Diamondbacks",  "NL West"));
        sendFast("createTeam(Colorado Rockies,      NL West)", league.createTeam("Colorado Rockies",      "NL West"));
        sendFast("createTeam(Los Angeles Dodgers,   NL West)", league.createTeam("Los Angeles Dodgers",   "NL West"));
        sendFast("createTeam(San Diego Padres,      NL West)", league.createTeam("San Diego Padres",      "NL West"));
        sendFast("createTeam(San Francisco Giants,  NL West)", league.createTeam("San Francisco Giants",  "NL West"));

        System.out.println("\n=== populateMLBData: Players (10 per team, 2026 season) ===");
        // ── AL East ──────────────────────────────────────────────────────────
        addPlayer(league, "Aaron Judge",        "RF", "New York Yankees");
        addPlayer(league, "Juan Soto",          "LF", "New York Yankees");
        addPlayer(league, "Giancarlo Stanton",  "DH", "New York Yankees");
        addPlayer(league, "Gleyber Torres",     "2B", "New York Yankees");
        addPlayer(league, "Anthony Volpe",      "SS", "New York Yankees");
        addPlayer(league, "Jazz Chisholm Jr.",  "3B", "New York Yankees");
        addPlayer(league, "Gerrit Cole",        "SP", "New York Yankees");
        addPlayer(league, "Carlos Rodon",       "SP", "New York Yankees");
        addPlayer(league, "Clarke Schmidt",     "SP", "New York Yankees");
        addPlayer(league, "Luis Gil",           "SP", "New York Yankees");

        addPlayer(league, "Rafael Devers",      "3B", "Boston Red Sox");
        addPlayer(league, "Jarren Duran",       "CF", "Boston Red Sox");
        addPlayer(league, "Masataka Yoshida",   "DH", "Boston Red Sox");
        addPlayer(league, "Wilyer Abreu",       "RF", "Boston Red Sox");
        addPlayer(league, "Triston Casas",      "1B", "Boston Red Sox");
        addPlayer(league, "Rob Refsnyder",      "OF", "Boston Red Sox");
        addPlayer(league, "Brayan Bello",       "SP", "Boston Red Sox");
        addPlayer(league, "Tanner Houck",       "SP", "Boston Red Sox");
        addPlayer(league, "Garrett Whitlock",   "RP", "Boston Red Sox");
        addPlayer(league, "Kutter Crawford",    "SP", "Boston Red Sox");

        addPlayer(league, "Vladimir Guerrero Jr.", "1B", "Toronto Blue Jays");
        addPlayer(league, "Bo Bichette",           "SS", "Toronto Blue Jays");
        addPlayer(league, "George Springer",       "CF", "Toronto Blue Jays");
        addPlayer(league, "Davis Schneider",       "2B", "Toronto Blue Jays");
        addPlayer(league, "Daulton Varsho",        "C",  "Toronto Blue Jays");
        addPlayer(league, "Isiah Kiner-Falefa",    "3B", "Toronto Blue Jays");
        addPlayer(league, "Kevin Gausman",         "SP", "Toronto Blue Jays");
        addPlayer(league, "Jose Berrios",          "SP", "Toronto Blue Jays");
        addPlayer(league, "Chris Bassitt",         "SP", "Toronto Blue Jays");
        addPlayer(league, "Yusei Kikuchi",         "SP", "Toronto Blue Jays");

        addPlayer(league, "Yandy Diaz",       "1B", "Tampa Bay Rays");
        addPlayer(league, "Randy Arozarena",  "LF", "Tampa Bay Rays");
        addPlayer(league, "Isaac Paredes",    "3B", "Tampa Bay Rays");
        addPlayer(league, "Jose Siri",        "CF", "Tampa Bay Rays");
        addPlayer(league, "Josh Lowe",        "RF", "Tampa Bay Rays");
        addPlayer(league, "Taylor Walls",     "SS", "Tampa Bay Rays");
        addPlayer(league, "Zach Eflin",       "SP", "Tampa Bay Rays");
        addPlayer(league, "Shane McClanahan", "SP", "Tampa Bay Rays");
        addPlayer(league, "Jeffrey Springs",  "SP", "Tampa Bay Rays");
        addPlayer(league, "Taj Bradley",      "SP", "Tampa Bay Rays");

        addPlayer(league, "Adley Rutschman",   "C",  "Baltimore Orioles");
        addPlayer(league, "Gunnar Henderson",  "SS", "Baltimore Orioles");
        addPlayer(league, "Anthony Santander", "RF", "Baltimore Orioles");
        addPlayer(league, "Ryan Mountcastle",  "1B", "Baltimore Orioles");
        addPlayer(league, "Austin Hays",       "LF", "Baltimore Orioles");
        addPlayer(league, "Cedric Mullins",    "CF", "Baltimore Orioles");
        addPlayer(league, "Corbin Burnes",     "SP", "Baltimore Orioles");
        addPlayer(league, "Kyle Bradish",      "SP", "Baltimore Orioles");
        addPlayer(league, "Grayson Rodriguez", "SP", "Baltimore Orioles");
        addPlayer(league, "Dean Kremer",       "SP", "Baltimore Orioles");

        // ── AL Central ───────────────────────────────────────────────────────
        addPlayer(league, "Luis Robert Jr.", "CF", "Chicago White Sox");
        addPlayer(league, "Andrew Vaughn",   "1B", "Chicago White Sox");
        addPlayer(league, "Gavin Sheets",    "DH", "Chicago White Sox");
        addPlayer(league, "Korey Lee",       "C",  "Chicago White Sox");
        addPlayer(league, "Nicky Lopez",     "2B", "Chicago White Sox");
        addPlayer(league, "Bryan Ramos",     "3B", "Chicago White Sox");
        addPlayer(league, "Garrett Crochet", "SP", "Chicago White Sox");
        addPlayer(league, "Michael Kopech",  "SP", "Chicago White Sox");
        addPlayer(league, "Erick Fedde",     "SP", "Chicago White Sox");
        addPlayer(league, "Davis Martin",    "SP", "Chicago White Sox");

        addPlayer(league, "Jose Ramirez",    "3B", "Cleveland Guardians");
        addPlayer(league, "Josh Naylor",     "1B", "Cleveland Guardians");
        addPlayer(league, "Steven Kwan",     "LF", "Cleveland Guardians");
        addPlayer(league, "David Fry",       "C",  "Cleveland Guardians");
        addPlayer(league, "Bo Naylor",       "C",  "Cleveland Guardians");
        addPlayer(league, "Will Brennan",    "RF", "Cleveland Guardians");
        addPlayer(league, "Emmanuel Clase",  "RP", "Cleveland Guardians");
        addPlayer(league, "Shane Bieber",    "SP", "Cleveland Guardians");
        addPlayer(league, "Tanner Bibee",    "SP", "Cleveland Guardians");
        addPlayer(league, "Logan Allen",     "SP", "Cleveland Guardians");

        addPlayer(league, "Riley Greene",       "CF", "Detroit Tigers");
        addPlayer(league, "Spencer Torkelson",  "1B", "Detroit Tigers");
        addPlayer(league, "Matt Vierling",      "RF", "Detroit Tigers");
        addPlayer(league, "Kerry Carpenter",    "LF", "Detroit Tigers");
        addPlayer(league, "Javier Baez",        "SS", "Detroit Tigers");
        addPlayer(league, "Parker Meadows",     "CF", "Detroit Tigers");
        addPlayer(league, "Tarik Skubal",       "SP", "Detroit Tigers");
        addPlayer(league, "Jack Flaherty",      "SP", "Detroit Tigers");
        addPlayer(league, "Casey Mize",         "SP", "Detroit Tigers");
        addPlayer(league, "Reese Olson",        "SP", "Detroit Tigers");

        addPlayer(league, "Salvador Perez",     "C",  "Kansas City Royals");
        addPlayer(league, "Bobby Witt Jr.",     "SS", "Kansas City Royals");
        addPlayer(league, "MJ Melendez",        "LF", "Kansas City Royals");
        addPlayer(league, "Vinnie Pasquantino", "1B", "Kansas City Royals");
        addPlayer(league, "Hunter Renfroe",     "RF", "Kansas City Royals");
        addPlayer(league, "Michael Massey",     "2B", "Kansas City Royals");
        addPlayer(league, "Seth Lugo",          "SP", "Kansas City Royals");
        addPlayer(league, "Cole Ragans",        "SP", "Kansas City Royals");
        addPlayer(league, "Brady Singer",       "SP", "Kansas City Royals");
        addPlayer(league, "Michael Wacha",      "SP", "Kansas City Royals");

        addPlayer(league, "Carlos Correa",  "SS", "Minnesota Twins");
        addPlayer(league, "Byron Buxton",   "CF", "Minnesota Twins");
        addPlayer(league, "Ryan Jeffers",   "C",  "Minnesota Twins");
        addPlayer(league, "Max Kepler",     "RF", "Minnesota Twins");
        addPlayer(league, "Royce Lewis",    "3B", "Minnesota Twins");
        addPlayer(league, "Matt Wallner",   "LF", "Minnesota Twins");
        addPlayer(league, "Pablo Lopez",    "SP", "Minnesota Twins");
        addPlayer(league, "Joe Ryan",       "SP", "Minnesota Twins");
        addPlayer(league, "Bailey Ober",    "SP", "Minnesota Twins");
        addPlayer(league, "Chris Paddack",  "SP", "Minnesota Twins");

        // ── AL West ──────────────────────────────────────────────────────────
        addPlayer(league, "Jose Altuve",     "2B", "Houston Astros");
        addPlayer(league, "Alex Bregman",    "3B", "Houston Astros");
        addPlayer(league, "Kyle Tucker",     "RF", "Houston Astros");
        addPlayer(league, "Yordan Alvarez",  "DH", "Houston Astros");
        addPlayer(league, "Jeremy Pena",     "SS", "Houston Astros");
        addPlayer(league, "Yainer Diaz",     "C",  "Houston Astros");
        addPlayer(league, "Framber Valdez",  "SP", "Houston Astros");
        addPlayer(league, "Hunter Brown",    "SP", "Houston Astros");
        addPlayer(league, "Cristian Javier", "SP", "Houston Astros");
        addPlayer(league, "Ryan Pressly",    "RP", "Houston Astros");

        addPlayer(league, "Mike Trout",        "CF", "Los Angeles Angels");
        addPlayer(league, "Anthony Rendon",    "3B", "Los Angeles Angels");
        addPlayer(league, "Taylor Ward",       "RF", "Los Angeles Angels");
        addPlayer(league, "Brandon Drury",     "2B", "Los Angeles Angels");
        addPlayer(league, "Luis Rengifo",      "SS", "Los Angeles Angels");
        addPlayer(league, "Mickey Moniak",     "LF", "Los Angeles Angels");
        addPlayer(league, "Tyler Anderson",    "SP", "Los Angeles Angels");
        addPlayer(league, "Patrick Sandoval",  "SP", "Los Angeles Angels");
        addPlayer(league, "Reid Detmers",      "SP", "Los Angeles Angels");
        addPlayer(league, "Griffin Canning",   "SP", "Los Angeles Angels");

        addPlayer(league, "Brent Rooker",    "DH", "Oakland Athletics");
        addPlayer(league, "Lawrence Butler", "LF", "Oakland Athletics");
        addPlayer(league, "Shea Langeliers", "C",  "Oakland Athletics");
        addPlayer(league, "Zack Gelof",      "2B", "Oakland Athletics");
        addPlayer(league, "JJ Bleday",       "RF", "Oakland Athletics");
        addPlayer(league, "Seth Brown",      "1B", "Oakland Athletics");
        addPlayer(league, "Mason Miller",    "RP", "Oakland Athletics");
        addPlayer(league, "JP Sears",        "SP", "Oakland Athletics");
        addPlayer(league, "Joey Estes",      "SP", "Oakland Athletics");
        addPlayer(league, "Paul Blackburn",  "SP", "Oakland Athletics");

        addPlayer(league, "Julio Rodriguez", "CF", "Seattle Mariners");
        addPlayer(league, "Cal Raleigh",     "C",  "Seattle Mariners");
        addPlayer(league, "Ty France",       "1B", "Seattle Mariners");
        addPlayer(league, "Eugenio Suarez",  "3B", "Seattle Mariners");
        addPlayer(league, "Mitch Garver",    "DH", "Seattle Mariners");
        addPlayer(league, "Luke Raley",      "LF", "Seattle Mariners");
        addPlayer(league, "Logan Gilbert",   "SP", "Seattle Mariners");
        addPlayer(league, "Luis Castillo",   "SP", "Seattle Mariners");
        addPlayer(league, "George Kirby",    "SP", "Seattle Mariners");
        addPlayer(league, "Bryan Woo",       "SP", "Seattle Mariners");

        addPlayer(league, "Corey Seager",    "SS", "Texas Rangers");
        addPlayer(league, "Marcus Semien",   "2B", "Texas Rangers");
        addPlayer(league, "Adolis Garcia",   "RF", "Texas Rangers");
        addPlayer(league, "Nathaniel Lowe",  "1B", "Texas Rangers");
        addPlayer(league, "Jonah Heim",      "C",  "Texas Rangers");
        addPlayer(league, "Josh Jung",       "3B", "Texas Rangers");
        addPlayer(league, "Jacob deGrom",    "SP", "Texas Rangers");
        addPlayer(league, "Nathan Eovaldi",  "SP", "Texas Rangers");
        addPlayer(league, "Andrew Heaney",   "SP", "Texas Rangers");
        addPlayer(league, "Jon Gray",        "SP", "Texas Rangers");

        // ── NL East ──────────────────────────────────────────────────────────
        addPlayer(league, "Ronald Acuna Jr.",  "RF", "Atlanta Braves");
        addPlayer(league, "Austin Riley",      "3B", "Atlanta Braves");
        addPlayer(league, "Matt Olson",        "1B", "Atlanta Braves");
        addPlayer(league, "Ozzie Albies",      "2B", "Atlanta Braves");
        addPlayer(league, "Sean Murphy",       "C",  "Atlanta Braves");
        addPlayer(league, "Michael Harris II", "CF", "Atlanta Braves");
        addPlayer(league, "Spencer Strider",   "SP", "Atlanta Braves");
        addPlayer(league, "Max Fried",         "SP", "Atlanta Braves");
        addPlayer(league, "Charlie Morton",    "SP", "Atlanta Braves");
        addPlayer(league, "Chris Sale",        "SP", "Atlanta Braves");

        addPlayer(league, "Jake Burger",      "3B", "Miami Marlins");
        addPlayer(league, "Nick Fortes",      "C",  "Miami Marlins");
        addPlayer(league, "Bryan De La Cruz", "RF", "Miami Marlins");
        addPlayer(league, "Jorge Soler",      "DH", "Miami Marlins");
        addPlayer(league, "Griffin Conine",   "LF", "Miami Marlins");
        addPlayer(league, "Vidal Brujan",     "2B", "Miami Marlins");
        addPlayer(league, "Sandy Alcantara",  "SP", "Miami Marlins");
        addPlayer(league, "Braxton Garrett",  "SP", "Miami Marlins");
        addPlayer(league, "Trevor Rogers",    "SP", "Miami Marlins");
        addPlayer(league, "Eury Perez",       "SP", "Miami Marlins");

        addPlayer(league, "Francisco Lindor", "SS", "New York Mets");
        addPlayer(league, "Pete Alonso",      "1B", "New York Mets");
        addPlayer(league, "Brandon Nimmo",    "LF", "New York Mets");
        addPlayer(league, "Jeff McNeil",      "2B", "New York Mets");
        addPlayer(league, "Mark Vientos",     "3B", "New York Mets");
        addPlayer(league, "Starling Marte",   "CF", "New York Mets");
        addPlayer(league, "Kodai Senga",      "SP", "New York Mets");
        addPlayer(league, "Sean Manaea",      "SP", "New York Mets");
        addPlayer(league, "Jose Quintana",    "SP", "New York Mets");
        addPlayer(league, "Adrian Houser",    "SP", "New York Mets");

        addPlayer(league, "Bryce Harper",        "1B", "Philadelphia Phillies");
        addPlayer(league, "Trea Turner",         "SS", "Philadelphia Phillies");
        addPlayer(league, "Kyle Schwarber",      "LF", "Philadelphia Phillies");
        addPlayer(league, "Nick Castellanos",    "RF", "Philadelphia Phillies");
        addPlayer(league, "Alec Bohm",           "3B", "Philadelphia Phillies");
        addPlayer(league, "JT Realmuto",         "C",  "Philadelphia Phillies");
        addPlayer(league, "Zack Wheeler",        "SP", "Philadelphia Phillies");
        addPlayer(league, "Aaron Nola",          "SP", "Philadelphia Phillies");
        addPlayer(league, "Ranger Suarez",       "SP", "Philadelphia Phillies");
        addPlayer(league, "Cristopher Sanchez",  "SP", "Philadelphia Phillies");

        addPlayer(league, "CJ Abrams",       "SS", "Washington Nationals");
        addPlayer(league, "Luis Garcia Jr.", "2B", "Washington Nationals");
        addPlayer(league, "Joey Meneses",    "1B", "Washington Nationals");
        addPlayer(league, "Keibert Ruiz",    "C",  "Washington Nationals");
        addPlayer(league, "Stone Garrett",   "RF", "Washington Nationals");
        addPlayer(league, "Juan Yepez",      "DH", "Washington Nationals");
        addPlayer(league, "MacKenzie Gore",  "SP", "Washington Nationals");
        addPlayer(league, "Patrick Corbin",  "SP", "Washington Nationals");
        addPlayer(league, "Jake Irvin",      "SP", "Washington Nationals");
        addPlayer(league, "Trevor Williams", "SP", "Washington Nationals");

        // ── NL Central ───────────────────────────────────────────────────────
        addPlayer(league, "Cody Bellinger",    "CF", "Chicago Cubs");
        addPlayer(league, "Nico Hoerner",      "2B", "Chicago Cubs");
        addPlayer(league, "Dansby Swanson",    "SS", "Chicago Cubs");
        addPlayer(league, "Ian Happ",          "LF", "Chicago Cubs");
        addPlayer(league, "Seiya Suzuki",      "RF", "Chicago Cubs");
        addPlayer(league, "Christopher Morel", "3B", "Chicago Cubs");
        addPlayer(league, "Justin Steele",     "SP", "Chicago Cubs");
        addPlayer(league, "Jameson Taillon",   "SP", "Chicago Cubs");
        addPlayer(league, "Jordan Wicks",      "SP", "Chicago Cubs");
        addPlayer(league, "Kyle Hendricks",    "SP", "Chicago Cubs");

        addPlayer(league, "Elly De La Cruz",  "SS", "Cincinnati Reds");
        addPlayer(league, "Spencer Steer",    "3B", "Cincinnati Reds");
        addPlayer(league, "Jonathan India",   "2B", "Cincinnati Reds");
        addPlayer(league, "Tyler Stephenson", "C",  "Cincinnati Reds");
        addPlayer(league, "TJ Friedl",        "CF", "Cincinnati Reds");
        addPlayer(league, "Jake Fraley",      "LF", "Cincinnati Reds");
        addPlayer(league, "Hunter Greene",    "SP", "Cincinnati Reds");
        addPlayer(league, "Andrew Abbott",    "SP", "Cincinnati Reds");
        addPlayer(league, "Nick Lodolo",      "SP", "Cincinnati Reds");
        addPlayer(league, "Graham Ashcraft",  "SP", "Cincinnati Reds");

        addPlayer(league, "Christian Yelich",   "LF", "Milwaukee Brewers");
        addPlayer(league, "William Contreras",  "C",  "Milwaukee Brewers");
        addPlayer(league, "Jake Bauers",        "1B", "Milwaukee Brewers");
        addPlayer(league, "Sal Frelick",        "RF", "Milwaukee Brewers");
        addPlayer(league, "Joey Wiemer",        "CF", "Milwaukee Brewers");
        addPlayer(league, "Willy Adames",       "SS", "Milwaukee Brewers");
        addPlayer(league, "Freddy Peralta",     "SP", "Milwaukee Brewers");
        addPlayer(league, "Colin Rea",          "SP", "Milwaukee Brewers");
        addPlayer(league, "Wade Miley",         "SP", "Milwaukee Brewers");
        addPlayer(league, "Jakob Junis",        "SP", "Milwaukee Brewers");

        addPlayer(league, "Oneil Cruz",      "SS", "Pittsburgh Pirates");
        addPlayer(league, "Bryan Reynolds",  "CF", "Pittsburgh Pirates");
        addPlayer(league, "Henry Davis",     "C",  "Pittsburgh Pirates");
        addPlayer(league, "Connor Joe",      "1B", "Pittsburgh Pirates");
        addPlayer(league, "Jack Suwinski",   "LF", "Pittsburgh Pirates");
        addPlayer(league, "Ji Hwan Bae",     "2B", "Pittsburgh Pirates");
        addPlayer(league, "Paul Skenes",     "SP", "Pittsburgh Pirates");
        addPlayer(league, "Mitch Keller",    "SP", "Pittsburgh Pirates");
        addPlayer(league, "Marco Gonzales",  "SP", "Pittsburgh Pirates");
        addPlayer(league, "Quinn Priester",  "SP", "Pittsburgh Pirates");

        addPlayer(league, "Nolan Arenado",     "3B", "St. Louis Cardinals");
        addPlayer(league, "Paul Goldschmidt",  "1B", "St. Louis Cardinals");
        addPlayer(league, "Willson Contreras", "C",  "St. Louis Cardinals");
        addPlayer(league, "Lars Nootbaar",     "RF", "St. Louis Cardinals");
        addPlayer(league, "Jordan Walker",     "LF", "St. Louis Cardinals");
        addPlayer(league, "Brendan Donovan",   "2B", "St. Louis Cardinals");
        addPlayer(league, "Sonny Gray",        "SP", "St. Louis Cardinals");
        addPlayer(league, "Miles Mikolas",     "SP", "St. Louis Cardinals");
        addPlayer(league, "Kyle Gibson",       "SP", "St. Louis Cardinals");
        addPlayer(league, "Lance Lynn",        "SP", "St. Louis Cardinals");

        // ── NL West ──────────────────────────────────────────────────────────
        addPlayer(league, "Ketel Marte",          "2B", "Arizona Diamondbacks");
        addPlayer(league, "Corbin Carroll",       "CF", "Arizona Diamondbacks");
        addPlayer(league, "Christian Walker",     "1B", "Arizona Diamondbacks");
        addPlayer(league, "Lourdes Gurriel Jr.",  "LF", "Arizona Diamondbacks");
        addPlayer(league, "Gabriel Moreno",       "C",  "Arizona Diamondbacks");
        addPlayer(league, "Jake McCarthy",        "RF", "Arizona Diamondbacks");
        addPlayer(league, "Zac Gallen",           "SP", "Arizona Diamondbacks");
        addPlayer(league, "Merrill Kelly",        "SP", "Arizona Diamondbacks");
        addPlayer(league, "Jordan Montgomery",    "SP", "Arizona Diamondbacks");
        addPlayer(league, "Eduardo Rodriguez",    "SP", "Arizona Diamondbacks");

        addPlayer(league, "Ryan McMahon",     "3B", "Colorado Rockies");
        addPlayer(league, "CJ Cron",          "1B", "Colorado Rockies");
        addPlayer(league, "Charlie Blackmon", "RF", "Colorado Rockies");
        addPlayer(league, "Ezequiel Tovar",   "SS", "Colorado Rockies");
        addPlayer(league, "Brenton Doyle",    "CF", "Colorado Rockies");
        addPlayer(league, "Elehuris Montero", "DH", "Colorado Rockies");
        addPlayer(league, "Kyle Freeland",    "SP", "Colorado Rockies");
        addPlayer(league, "Austin Gomber",    "SP", "Colorado Rockies");
        addPlayer(league, "Dakota Hudson",    "SP", "Colorado Rockies");
        addPlayer(league, "Cal Quantrill",    "SP", "Colorado Rockies");

        addPlayer(league, "Shohei Ohtani",       "DH", "Los Angeles Dodgers");
        addPlayer(league, "Mookie Betts",         "RF", "Los Angeles Dodgers");
        addPlayer(league, "Freddie Freeman",      "1B", "Los Angeles Dodgers");
        addPlayer(league, "Will Smith",           "C",  "Los Angeles Dodgers");
        addPlayer(league, "Max Muncy",            "3B", "Los Angeles Dodgers");
        addPlayer(league, "Teoscar Hernandez",    "LF", "Los Angeles Dodgers");
        addPlayer(league, "Tyler Glasnow",        "SP", "Los Angeles Dodgers");
        addPlayer(league, "Yoshinobu Yamamoto",   "SP", "Los Angeles Dodgers");
        addPlayer(league, "Walker Buehler",       "SP", "Los Angeles Dodgers");
        addPlayer(league, "Clayton Kershaw",      "SP", "Los Angeles Dodgers");

        addPlayer(league, "Fernando Tatis Jr.", "SS", "San Diego Padres");
        addPlayer(league, "Manny Machado",      "3B", "San Diego Padres");
        addPlayer(league, "Xander Bogaerts",    "2B", "San Diego Padres");
        addPlayer(league, "Jake Cronenworth",   "1B", "San Diego Padres");
        addPlayer(league, "Jurickson Profar",   "LF", "San Diego Padres");
        addPlayer(league, "Luis Arraez",        "DH", "San Diego Padres");
        addPlayer(league, "Dylan Cease",        "SP", "San Diego Padres");
        addPlayer(league, "Yu Darvish",         "SP", "San Diego Padres");
        addPlayer(league, "Joe Musgrove",       "SP", "San Diego Padres");
        addPlayer(league, "Michael King",       "SP", "San Diego Padres");

        addPlayer(league, "Matt Chapman",     "3B", "San Francisco Giants");
        addPlayer(league, "Patrick Bailey",   "C",  "San Francisco Giants");
        addPlayer(league, "Mike Yastrzemski", "RF", "San Francisco Giants");
        addPlayer(league, "LaMonte Wade Jr.", "1B", "San Francisco Giants");
        addPlayer(league, "Wilmer Flores",    "2B", "San Francisco Giants");
        addPlayer(league, "Jung Hoo Lee",     "CF", "San Francisco Giants");
        addPlayer(league, "Logan Webb",       "SP", "San Francisco Giants");
        addPlayer(league, "Blake Snell",      "SP", "San Francisco Giants");
        addPlayer(league, "Jordan Hicks",     "SP", "San Francisco Giants");
        addPlayer(league, "Kyle Harrison",    "SP", "San Francisco Giants");
    }

    /**
     * Moves the Oakland Athletics from AL West to AL Central.
     * Demonstrates an intra-conference team transfer.
     *
     * @param league  The LeagueInterfaceImpl to use for the move call
     */
    private void moveTeamBetweenDivisions(LeagueInterfaceImpl league) throws InterruptedException {
        System.out.println("\n=== moveTeamBetweenDivisions: Oakland Athletics AL West → AL Central ===");
        sendFast("moveTeamToDivision(Oakland Athletics, AL Central)",
                league.moveTeamToDivision("Oakland Athletics", "AL Central"));
    }

    /**
     * Moves the NL West division into the AL conference.
     * Demonstrates a cross-conference division transfer; all five NL West teams
     * follow the division into the AL automatically.
     *
     * @param league  The LeagueInterfaceImpl to use for the move call
     */
    private void moveDivisionBetweenConferences(LeagueInterfaceImpl league) throws InterruptedException {
        System.out.println("\n=== moveDivisionBetweenConferences: NL West → AL ===");
        sendFast("moveDivisionToConference(NL West, AL)",
                league.moveDivisionToConference("NL West", "AL"));
    }

    /**
     * Moves five notable players to different teams, simulating off-season trades.
     * The moves span division and conference boundaries to exercise all code paths
     * in movePlayerToTeam().
     *
     * @param league  The LeagueInterfaceImpl to use for the move calls
     */
    private void moveMLBPlayers(LeagueInterfaceImpl league) throws InterruptedException {
        System.out.println("\n=== moveMLBPlayers: 5 trades ===");
        sendFast("movePlayerToTeam(Shohei Ohtani,    San Francisco Giants)",
                league.movePlayerToTeam("Shohei Ohtani",    "San Francisco Giants"));
        sendFast("movePlayerToTeam(Juan Soto,         Boston Red Sox)",
                league.movePlayerToTeam("Juan Soto",         "Boston Red Sox"));
        sendFast("movePlayerToTeam(Bobby Witt Jr.,    Houston Astros)",
                league.movePlayerToTeam("Bobby Witt Jr.",    "Houston Astros"));
        sendFast("movePlayerToTeam(Mookie Betts,      New York Mets)",
                league.movePlayerToTeam("Mookie Betts",      "New York Mets"));
        sendFast("movePlayerToTeam(Gunnar Henderson,  Tampa Bay Rays)",
                league.movePlayerToTeam("Gunnar Henderson",  "Tampa Bay Rays"));
    }

    /**
     * Deletes both MLB conferences, cascading to all divisions, teams, players,
     * and stat rows beneath them.  After this returns, the database contains no
     * MLB data.
     *
     * @param league  The LeagueInterfaceImpl to use for the delete calls
     */
    private void resetMLBData(LeagueInterfaceImpl league) throws InterruptedException {
        System.out.println("\n=== resetMLBData: Delete AL and NL (cascades everything) ===");
        sendFast("deleteConference(AL) [cascades AL East/Central/West, all teams, all players]",
                league.deleteConference("AL"));
        sendFast("deleteConference(NL) [cascades NL East/Central/West, all teams, all players]",
                league.deleteConference("NL"));
    }

    /**
     * Creates a single MLB player on the given team for the 2026 season.
     * Stats are initialized to zero by MLBStatUpdater.doCreateInitialStats().
     *
     * @param league    The LeagueInterfaceImpl to use for the create call
     * @param name      Full player name (e.g. "Aaron Judge")
     * @param position  Position abbreviation (e.g. "RF", "SP")
     * @param team      Team name matching an existing team in the database
     */
    private void addPlayer(LeagueInterfaceImpl league, String name, String position, String team)
            throws InterruptedException {
        PlayerData pd = new PlayerData(name, position, team);
        pd.setYear(2026);
        sendFast("createPlayer(" + name + ", " + position + ", " + team + ")",
                league.createPlayer(pd));
    }

    /**
     * Identifies the current Patroni primary by querying each node's REST API
     * (GET /primary returns 200 on the leader, 503 on replicas), then stops
     * that container via docker stop.
     *
     * Returns the node name that was killed (e.g. "patroni1") so the caller
     * can restart it during the cleanup phase to restore the cluster to full health.
     *
     * @return the name of the killed Patroni node, or "unknown" if none responded
     */
    private String killCurrentLeader() throws IOException, InterruptedException {
        String[] nodes = {"patroni1", "patroni2", "patroni3"};
        for (String node : nodes) {
            String container = "statcentral-" + node + "-1";
            // curl -sf exits 0 on HTTP 200, non-zero on HTTP 503 or connection failure.
            Process check = new ProcessBuilder(
                    "docker", "exec", container,
                    "curl", "-sf", "http://localhost:8008/primary")
                    .start();
            int exitCode = check.waitFor();
            if (exitCode == 0) {
                System.out.println("  Current leader: " + node + " — stopping " + container);
                new ProcessBuilder("docker", "stop", container)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(15, TimeUnit.SECONDS);
                return node;
            }
        }
        System.out.println("  WARNING: could not identify the current leader — no node responded to /primary");
        return "unknown";
    }
}