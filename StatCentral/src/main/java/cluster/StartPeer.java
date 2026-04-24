package cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * StartPeer
 *
 * Standalone entry point for launching a single PeerServerImpl (worker or
 * follower) as an independent OS process.  Pass the server's ID and the
 * cluster topology; the server performs leader election over UDP and, once a
 * leader is chosen, starts its WorkerServer or LeaderServer role automatically.
 *
 * Usage:
 *   java -cp <classpath> cluster.StartPeer <serverID> <numServers> <baseUdpPort> [gatewayID]
 *
 * Arguments:
 *   serverID     — Stable numeric ID for this peer (0-based, must not equal gatewayID)
 *   numServers   — Total number of nodes in the cluster, including the gateway
 *   baseUdpPort  — UDP port for serverID 0; port for ID i = baseUdpPort + i*10
 *   gatewayID    — Optional; ID of the gateway/observer node (default 0)
 *
 * Example — 4-node cluster (gateway=0, workers=1,2,3), base port 8000:
 *   java -cp ... cluster.StartPeer 1 4 8000 0
 *   java -cp ... cluster.StartPeer 2 4 8000 0
 *   java -cp ... cluster.StartPeer 3 4 8000 0
 */
public class StartPeer {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 3) {
            System.err.println("Usage: StartPeer <serverID> <numServers> <baseUdpPort> [gatewayID]");
            System.exit(1);
        }

        long serverID    = Long.parseLong(args[0]);
        int  numServers  = Integer.parseInt(args[1]);
        int  baseUdpPort = Integer.parseInt(args[2]);
        long gatewayID   = args.length > 3 ? Long.parseLong(args[3]) : 0L;
        int  numObservers = 1; // the gateway is the single observer

        // Build peer map: ID i → localhost:(baseUdpPort + i*10)
        Map<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>();
        for (int i = 0; i < numServers; i++) {
            peerIDtoAddress.put((long) i,
                    new InetSocketAddress("localhost", baseUdpPort + i * 10));
        }

        int udpPort = baseUdpPort + (int) serverID * 10;
        System.out.println("[StartPeer] Peer " + serverID
                + " starting on UDP port " + udpPort
                + "  (TCP port " + (udpPort + 2) + ")");

        PeerServerImpl peer = new PeerServerImpl(
                udpPort, 0L, serverID, peerIDtoAddress, gatewayID, numObservers);
        peer.start();

        System.out.println("[StartPeer] Peer " + serverID + " running — waiting for leader election.");

        // Keep the JVM alive indefinitely (peer threads are non-daemon).
        Thread.currentThread().join();
    }
}