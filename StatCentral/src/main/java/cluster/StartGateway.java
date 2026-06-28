package cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StartGateway
 *
 * Standalone entry point for launching the GatewayServer as an independent OS
 * process.  The gateway participates in leader election as an OBSERVER (it
 * votes but does not count toward quorum) and then accepts HTTP requests from
 * LeagueInterfaceImpl / FanInterfaceImpl, forwarding them to the elected leader
 * over TCP.
 *
 * Usage:
 *   java -cp <classpath> cluster.StartGateway <gatewayID> <numServers> <baseUdpPort> <httpPort>
 *
 * Arguments:
 *   gatewayID    — Stable numeric ID for the gateway node
 *   numServers   — Total number of nodes in the cluster, including the gateway
 *   baseUdpPort  — UDP port for serverID 0; port for ID i = baseUdpPort + i*10
 *   httpPort     — HTTP port clients send requests to
 *
 * Example — 4-node cluster (gateway=0, base UDP 8000, HTTP 8080):
 *   java -cp ... cluster.StartGateway 0 4 8000 8080
 */
public class StartGateway {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 4) {
            System.err.println("Usage: StartGateway <gatewayID> <numServers> <baseUdpPort> <httpPort>");
            System.exit(1);
        }

        long gatewayID   = Long.parseLong(args[0]);
        int  numServers  = Integer.parseInt(args[1]);
        int  baseUdpPort = Integer.parseInt(args[2]);
        int  httpPort    = Integer.parseInt(args[3]);
        int  numObservers = 1;

        // Build full peer map (gateway's own entry is removed by PeerServerImpl internally).
        ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress = new ConcurrentHashMap<>();
        for (int i = 0; i < numServers; i++) {
            peerIDtoAddress.put((long) i,
                    new InetSocketAddress("localhost", baseUdpPort + i * 10));
        }

        int peerPort = baseUdpPort + (int) gatewayID * 10;
        System.out.println("[StartGateway] Gateway " + gatewayID
                + " starting — HTTP port " + httpPort
                + ", peer UDP port " + peerPort);

        GatewayServer gateway = new GatewayServer(
                httpPort, peerPort, 0L, gatewayID, peerIDtoAddress, numObservers);
        gateway.start();

        System.out.println("[StartGateway] Gateway running at http://localhost:" + httpPort);
        System.out.println("[StartGateway] GET http://localhost:" + httpPort + "/status to check cluster state.");

        // Keep the JVM alive (GatewayServer threads include non-daemon threads via
        // the embedded HttpServer, but joining here makes intent explicit).
        Thread.currentThread().join();
    }
}