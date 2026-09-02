package cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClusterApp
 *
 * Persistent, containerized entry point for the StatCentral application tier.
 * Brings up one GatewayServer (HTTP-facing, serverID 0) and three worker
 * PeerServerImpl nodes (serverIDs 1-3) inside a single JVM, then blocks
 * forever so the container stays up and keeps serving requests from
 * FanInterfaceImpl / LeagueInterfaceImpl clients (including the web front end).
 *
 * This mirrors the cluster bring-up sequence already proven in
 * FullInterfaceTest and MultiProcessDemo (same constructors, same
 * gateway-starts-before-peers ordering), but skips the demo's
 * populate/cleanup/exit steps since this is meant to run indefinitely as a
 * long-lived Docker service rather than a one-shot demo.
 *
 * Configuration (environment variables, all optional):
 *   HTTP_PORT      — port the GatewayServer listens on for /request and /status
 *                    (default 8080)
 *   BASE_UDP_PORT  — base UDP port for peer election/gossip; node i listens on
 *                    BASE_UDP_PORT + i*10 (default 8000)
 *
 * Database connectivity (PGBOUNCER_HOST / PGBOUNCER_PORT) is configured
 * separately via DBConnectionManager's own environment variables.
 */
public class ClusterApp {

    private static final int  NUM_SERVERS = 4; // 1 gateway + 3 workers
    private static final long GATEWAY_ID  = 0L;
    private static final int  ELECTION_TIMEOUT_SECS = 40;

    public static void main(String[] args) throws IOException, InterruptedException {

        int httpPort    = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
        int baseUdpPort = Integer.parseInt(System.getenv().getOrDefault("BASE_UDP_PORT", "8000"));

        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>();
        for (int i = 0; i < NUM_SERVERS; i++) {
            peerIDtoAddress.put((long) i, new InetSocketAddress("localhost", baseUdpPort + i * 10));
        }

        // Build the worker peers up front (each gets its own copy of the address
        // map, since PeerServerImpl's constructor mutates it by removing its own
        // ID — sharing one map instance across constructors would corrupt it).
        List<PeerServerImpl> workers = new ArrayList<>();
        for (long id = 1; id < NUM_SERVERS; id++) {
            ConcurrentHashMap<Long, InetSocketAddress> peerMap = new ConcurrentHashMap<>(peerIDtoAddress);
            int udpPort = baseUdpPort + (int) id * 10;
            workers.add(new PeerServerImpl(udpPort, 0L, id, peerMap, GATEWAY_ID, 1));
        }

        ConcurrentHashMap<Long, InetSocketAddress> gatewayPeerMap = new ConcurrentHashMap<>(peerIDtoAddress);
        GatewayServer gateway = new GatewayServer(httpPort, baseUdpPort, 0L, GATEWAY_ID, gatewayPeerMap, 1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[ClusterApp] Shutdown signal received — stopping gateway and workers...");
            try { gateway.shutdown(); } catch (Exception ignored) {}
            for (PeerServerImpl w : workers) {
                try { w.shutdown(); } catch (Exception ignored) {}
            }
        }, "ClusterApp-ShutdownHook"));

        // Start the gateway first and give its UDP listener time to warm up
        // before any worker begins leader election (same ordering rationale as
        // MultiProcessDemo — avoids missing the first round of votes).
        System.out.println("[ClusterApp] Starting gateway (HTTP :" + httpPort + ")...");
        gateway.start();
        Thread.sleep(2000);

        System.out.println("[ClusterApp] Starting " + workers.size() + " worker peers...");
        for (PeerServerImpl w : workers) {
            w.start();
        }

        System.out.println("[ClusterApp] Waiting for leader election...");
        waitForLeader(httpPort);

        System.out.println("[ClusterApp] Cluster ready — serving requests on http://0.0.0.0:" + httpPort);
        Thread.currentThread().join();
    }

    /** Polls GET /status until a leader is reported, or until the timeout elapses. */
    private static void waitForLeader(int httpPort) throws InterruptedException {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        String statusUrl = "http://localhost:" + httpPort + "/status";

        for (int i = 0; i < ELECTION_TIMEOUT_SECS; i++) {
            Thread.sleep(1000);
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(statusUrl))
                        .GET()
                        .timeout(Duration.ofSeconds(2))
                        .build();
                String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
                if (body.startsWith("LEADER:")) {
                    System.out.println("[ClusterApp] " + body.lines().findFirst().orElse(""));
                    return;
                }
            } catch (Exception e) {
                // Gateway not reachable yet — keep waiting.
            }
        }
        System.out.println("[ClusterApp] WARNING: no leader elected within "
                + ELECTION_TIMEOUT_SECS + "s — the cluster may still be starting up.");
    }
}
