package cluster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * MultiProcessDemo
 *
 * Demonstrates that every StatCentral component can run in its own independent
 * OS process — each with its own JVM, heap, and thread pool — communicating
 * only over network sockets (UDP for gossip/election, TCP for work dispatch,
 * HTTP for client requests).
 *
 * ── What this class does ─────────────────────────────────────────────────────
 *
 *  1. LAUNCH WORKERS  — Starts three worker peer-server processes (IDs 1, 2, 3)
 *                       using StartPeer as the entry point.  Each runs leader
 *                       election and, once a leader is chosen, transitions into
 *                       its WorkerServer role automatically.
 *
 *  2. LAUNCH GATEWAY  — Starts the GatewayServer process (ID 0) using
 *                       StartGateway.  The gateway participates in election as
 *                       an OBSERVER and then begins accepting HTTP requests.
 *
 *  3. WAIT FOR LEADER — Polls GET /status on the gateway until the response
 *                       starts with "LEADER:", confirming that election is
 *                       complete and the cluster is ready to serve requests.
 *
 *  4. RUN CLIENT DEMO — Calls RunStatDemo.main() in-process to exercise the
 *                       full StatCentral API: conference → division → team →
 *                       player → stat updates → fan queries → cleanup.
 *
 *  5. STREAM LOGS     — Each child process's stdout/stderr is forwarded to the
 *                       parent's console on a dedicated daemon thread so you can
 *                       see what every node is doing in real time.
 *
 *  6. CLEAN UP        — A JVM shutdown hook kills every child process when the
 *                       demo ends (normally or via Ctrl-C), so no orphan Java
 *                       processes are left behind.
 *
 * ── Classpath trick ──────────────────────────────────────────────────────────
 *
 *  Child processes need the same classes and dependency JARs as this process.
 *  Rather than rebuilding the classpath from scratch, we read it directly from
 *  the running JVM:
 *
 *    System.getProperty("java.class.path")
 *
 *  When Maven runs this class (via mvn exec:java or a JUnit test), it has
 *  already resolved all dependencies and set java.class.path accordingly.
 *  Passing that same string to ProcessBuilder means children automatically
 *  get every JAR they need — no fat-JAR or dependency-copy step required.
 *
 * ── Cluster topology ─────────────────────────────────────────────────────────
 *
 *   ID 0 — Gateway   UDP 8000, HTTP 8080
 *   ID 1 — Worker 1  UDP 8010, TCP 8012
 *   ID 2 — Worker 2  UDP 8020, TCP 8022
 *   ID 3 — Worker 3  UDP 8030, TCP 8032
 *
 * ── How to run ───────────────────────────────────────────────────────────────
 *
 *   Option A — Maven exec plugin (works from any terminal):
 *     mvn exec:java -Dexec.mainClass=cluster.MultiProcessDemo
 *
 *   Option B — IntelliJ: right-click MultiProcessDemo → Run 'MultiProcessDemo.main()'
 *
 * ── Prerequisites ────────────────────────────────────────────────────────────
 *
 *   • The StatCentral Docker stack must be running (docker compose up -d)
 *     so that the worker microservices can reach the database.
 *   • Ports 8000, 8010, 8020, 8030 (UDP) and 8012, 8022, 8032 (TCP) and
 *     8080 (HTTP) must be free before starting.
 */
public class MultiProcessDemo {

    // ── Cluster topology constants ────────────────────────────────────────────

    /** Number of nodes in the cluster (1 gateway + 3 workers). */
    private static final int    NUM_SERVERS  = 4;

    /**
     * UDP port base.  Node with ID i listens on UDP port BASE_UDP + i*10.
     * The WorkerServer TCP port is UDP port + 2 (enforced by PeerServerImpl).
     */
    private static final int    BASE_UDP     = 8000;

    /** Server ID assigned to the gateway node (also the OBSERVER in election). */
    private static final long   GATEWAY_ID   = 0L;

    /** HTTP port the GatewayServer listens on for client requests. */
    private static final int    HTTP_PORT    = 8080;

    /**
     * How long (seconds) to wait for leader election before giving up.
     * Election normally completes in 2-5 seconds; 40 s is a generous ceiling.
     */
    private static final int    ELECTION_TIMEOUT_SECS = 40;

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {

        // Force AWT out of headless mode so JOptionPane can show a real window.
        // mvn exec:java sometimes inherits java.awt.headless=true from the Maven
        // process; setting it to false here before any AWT class is loaded ensures
        // the Swing dialog appears even when launched from a terminal.
        System.setProperty("java.awt.headless", "false");

        // Collect every child Process so the shutdown hook can kill them all.
        List<Process> children = new ArrayList<>();

        // ── Shutdown hook ─────────────────────────────────────────────────────
        // Registered immediately so it fires even if we crash before launching
        // all processes.  Iterates the (possibly partial) children list and
        // terminates each process.  This prevents orphan JVM processes from
        // holding onto ports after the demo ends.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[demo] Shutdown hook firing — killing child processes...");
            for (Process p : children) {
                p.destroyForcibly();
            }
            System.out.println("[demo] All child processes terminated.");
        }, "ShutdownHook"));

        // ── Step 1: Resolve the classpath ─────────────────────────────────────
        // java.class.path is exactly what Maven set when it launched this JVM.
        // Passing it unchanged to child processes gives them the same compiled
        // classes and dependency JARs without any extra build steps.
        String classpath = System.getProperty("java.class.path");

        // Locate the java executable that launched this process so children use
        // the same JVM version.  java.home points to the JRE/JDK root directory;
        // the executable lives in its bin/ subdirectory.
        String javaExe = System.getProperty("java.home")
                + java.io.File.separator + "bin"
                + java.io.File.separator + "java";

        // ── Step 2: Start the gateway process FIRST ───────────────────────────
        // The gateway must start before the worker peers so its UDP listener is
        // fully initialized before any election traffic begins.
        //
        // Why order matters — the race on first run:
        //   LeaderElection.lookForLeader() has a 3.2-second "finalizeWait" sleep
        //   that fires once workers reach quorum among themselves.  If the gateway
        //   starts AFTER workers have entered that sleep, no election notifications
        //   arrive at the gateway and it never learns who was elected.  Starting
        //   the gateway first and giving it 2 seconds to warm up its
        //   UDPMessageReceiver guarantees it is listening before the first worker
        //   vote is cast.
        //
        // StartGateway arguments: <gatewayID> <numServers> <baseUdpPort> <httpPort>
        System.out.println("[demo] Launching gateway (HTTP :" + HTTP_PORT + ") first to warm up UDP listener...");
        Process gateway = new ProcessBuilder(
                javaExe,
                "-cp", classpath,
                "cluster.StartGateway",
                String.valueOf(GATEWAY_ID),
                String.valueOf(NUM_SERVERS),
                String.valueOf(BASE_UDP),
                String.valueOf(HTTP_PORT)
        )
        .redirectErrorStream(true)
        .start();

        children.add(gateway);
        streamOutput(gateway, "gateway");

        // Wait for the gateway's PeerServerImpl thread to bind its UDP socket and
        // enter lookForLeader() before any worker fires an election message.
        // 2 seconds is sufficient based on observed startup logs.
        System.out.println("[demo] Waiting 2s for gateway UDP listener to initialize...");
        Thread.sleep(2000);

        // ── Step 3: Start worker peer processes ───────────────────────────────
        // IDs 1, 2, 3 are non-observer peers.  After leader election they become
        // either the LeaderServer (exactly one) or WorkerServers (the rest).
        // StartPeer arguments: <serverID> <numServers> <baseUdpPort> <gatewayID>
        System.out.println("[demo] Launching worker peers...");
        for (int id = 1; id < NUM_SERVERS; id++) {
            System.out.println("[demo] Launching worker peer " + id + "...");
            Process p = new ProcessBuilder(
                    javaExe,
                    "-cp", classpath,
                    "cluster.StartPeer",
                    String.valueOf(id),          // this peer's server ID
                    String.valueOf(NUM_SERVERS), // total nodes (so peer can build the address map)
                    String.valueOf(BASE_UDP),    // base UDP port
                    String.valueOf(GATEWAY_ID)   // gateway's ID (for OBSERVER exclusion)
            )
            .redirectErrorStream(true) // merge stderr into stdout for simpler log streaming
            .start();

            children.add(p);

            // Stream this process's output to the console on a daemon thread.
            // Daemon threads don't prevent JVM exit, so they clean up automatically.
            streamOutput(p, "peer-" + id);
        }

        // ── Step 4: Wait for leader election ──────────────────────────────────
        // Poll GET /status on the gateway.  The gateway returns "NO_LEADER" until
        // election completes, then "LEADER:<id>\n<id>:ROLE\n..." once a leader
        // has been chosen.  We keep polling until we see "LEADER:" or time out.
        System.out.println("[demo] Waiting for leader election...");

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        String statusUrl = "http://localhost:" + HTTP_PORT + "/status";
        boolean elected  = false;

        for (int i = 0; i < ELECTION_TIMEOUT_SECS; i++) {
            Thread.sleep(1000);
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(statusUrl))
                        .GET()
                        .timeout(Duration.ofSeconds(2))
                        .build();

                HttpResponse<String> resp =
                        http.send(req, HttpResponse.BodyHandlers.ofString());

                String body = resp.body();
                if (body.startsWith("LEADER:")) {
                    // Extract the leader's ID from the first line ("LEADER:<id>")
                    String leaderId = body.lines().findFirst()
                            .orElse("LEADER:?")
                            .substring("LEADER:".length());
                    System.out.println("[demo] Leader elected: node " + leaderId);
                    elected = true;
                    break;
                }
                System.out.println("[demo]   (" + (i + 1) + "s) still waiting — " + body.trim());

            } catch (Exception e) {
                // Gateway not yet accepting connections — keep waiting.
                System.out.println("[demo]   (" + (i + 1) + "s) gateway not reachable yet...");
            }
        }

        if (!elected) {
            System.err.println("[demo] ERROR: no leader elected within "
                    + ELECTION_TIMEOUT_SECS + " seconds.");
            System.exit(1);
        }

        // Allow the newly elected LeaderServer and its WorkerServer counterparts
        // a moment to fully initialise their TCP server sockets before the first
        // client request arrives.
        Thread.sleep(2000);

        // ── Step 5: Print the full cluster state ──────────────────────────────
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            String status = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            System.out.println("[demo] Cluster state:");
            status.lines().forEach(line -> System.out.println("[demo]   " + line));
        } catch (Exception e) {
            System.out.println("[demo] Could not fetch cluster state: " + e.getMessage());
        }

        // ── Step 6: Populate the database and run read queries ───────────────
        // populate() runs in this process — it is just an HTTP client talking to
        // the gateway, so no extra JVM is needed.  It returns the
        // LeagueInterfaceImpl so we can issue cleanup through the same connection
        // after the user has had a chance to inspect the database.
        System.out.println();
        System.out.println("[demo] ─────────────────────────────────────────────");
        System.out.println("[demo] Populating database and running queries...");
        System.out.println("[demo] ─────────────────────────────────────────────");
        user.LeagueInterfaceImpl league = RunStatDemo.populate(HTTP_PORT, "MLB");

        // ── Step 7: Pause — let the user inspect the database ────────────────
        // This mirrors the pattern used in FullInterfaceTest.testRealMLBData():
        // print a console message, then show a Swing dialog that blocks until
        // the user clicks OK.  While the dialog is open you can connect to the
        // database (e.g. via pgAdmin or psql) and inspect the rows that were
        // just written.  Cleanup only runs after you dismiss the dialog.
        //
        // If AWT is unavailable for any reason (e.g. running in a truly headless
        // environment) we fall back to a simple "press Enter" console prompt so
        // the demo never crashes silently and skips the inspection window.
        System.out.println();
        System.out.println("[demo] Database populated. Inspect it now, then click OK in the dialog to clean up.");
        pauseForInspection();

        // ── Step 8: Clean up demo data ────────────────────────────────────────
        // Deletes the top-level conference, which cascades through the database
        // to remove the division, team, player, and stats row created by populate().
        System.out.println("[demo] ─────────────────────────────────────────────");
        System.out.println("[demo] Cleaning up demo data...");
        System.out.println("[demo] ─────────────────────────────────────────────");
        RunStatDemo.cleanup(league);

        // ── Done ──────────────────────────────────────────────────────────────
        // The shutdown hook (registered above) will kill the child processes as
        // the JVM exits here.
        System.out.println();
        System.out.println("[demo] Demo finished. Exiting — shutdown hook will stop all cluster nodes.");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Pauses execution so the user can inspect the database before cleanup runs.
     *
     * First attempts to show a blocking Swing dialog (JOptionPane).  This is the
     * same approach used in FullInterfaceTest — the dialog sits on top of other
     * windows and the user clicks OK when ready.
     *
     * If AWT is unavailable (headless environment, no display, etc.) the method
     * falls back to a console "press Enter to continue" prompt so the demo never
     * silently skips the inspection window.
     */
    private static void pauseForInspection() {
        try {
            javax.swing.JOptionPane.showMessageDialog(
                    null,
                    "The demo data has been written to the database.\n\n"
                    + "You can now inspect it (e.g. via pgAdmin or psql).\n\n"
                    + "Click OK when you are ready to delete the demo data.",
                    "StatCentral — Inspect & Reset",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } catch (java.awt.HeadlessException | java.awt.AWTError e) {
            // No graphical display available — fall back to console prompt if a real
            // terminal is attached, otherwise continue automatically (e.g. Surefire fork).
            java.io.Console console = System.console();
            if (console != null) {
                console.readLine("[demo] (No GUI available — press Enter in this console to continue with cleanup)");
            } else {
                System.out.println("[demo] (No GUI and no interactive console — continuing with cleanup automatically)");
            }
        }
    }

    /**
     * Spawns a daemon thread that continuously reads lines from the given
     * process's stdout (stderr is merged in via redirectErrorStream) and
     * prints them to the console prefixed with "[<label>]".
     *
     * Using a dedicated thread per process is the standard approach for
     * consuming child-process output without blocking — if we don't drain the
     * output stream, the child's OS pipe buffer fills up and the child blocks.
     *
     * @param process  The child process whose output should be forwarded
     * @param label    A short identifier printed before each line (e.g. "peer-1")
     */
    private static void streamOutput(Process process, String label) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[" + label + "] " + line);
                }
            } catch (IOException e) {
                // Process exited — stream closed normally, nothing to do.
            }
        }, "log-" + label);

        // Daemon so this thread doesn't prevent the JVM from exiting when main() returns.
        t.setDaemon(true);
        t.start();
    }
}