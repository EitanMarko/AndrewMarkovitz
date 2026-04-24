package cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Main class to start an individual peer server for demo purposes
 */
public class DemoPeerServer {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java DemoPeerServer <serverID>");
            System.exit(1);
        }
        
        long serverID = Long.parseLong(args[0]);
        int basePeerPort = 8010;
        long gatewayID = 0L;
        int numberOfObservers = 1;
        int numPeers = 7;
        
        // Create peer ID to address mapping
        Map<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>();
        
        // Add all peers (including gateway)
        for (int i = 0; i <= numPeers; i++) {
            int udpPort = basePeerPort + (i * 10);
            peerIDtoAddress.put((long) i, new InetSocketAddress("localhost", udpPort));
        }
        
        // Get this server's port
        int udpPort = peerIDtoAddress.get(serverID).getPort();
        
        System.out.println("Starting Peer Server with ID: " + serverID + " on UDP port: " + udpPort);
        
        // Create and start peer server
        PeerServerImpl peerServer = new PeerServerImpl(
            udpPort,
            0L, // Initial epoch
            serverID,
            peerIDtoAddress,
            gatewayID,
            numberOfObservers
        );
        
        peerServer.start();
        
        System.out.println("Peer Server " + serverID + " is running.");
        
        // Keep the JVM alive
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("Peer Server " + serverID + " shutting down.");
        }
    }
}
