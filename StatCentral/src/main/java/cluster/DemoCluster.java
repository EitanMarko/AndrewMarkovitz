package cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main class to start the distributed cluster for demo purposes
 */
public class DemoCluster {
    
    public static void main(String[] args) throws IOException, InterruptedException {
        // Configuration
        int numPeers = 7; // 7 peer servers
        int gatewayHttpPort = 8080;
        int basePeerPort = 8010; // Starting UDP port for peers
        long gatewayID = 0L; // Gateway server ID
        int numberOfObservers = 1; // Just the gateway
        
        // Create peer ID to address mapping
        Map<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>();
        
        // Add all peers (including gateway) to the map
        for (int i = 0; i <= numPeers; i++) {
            long serverID = i;
            int udpPort = basePeerPort + (i * 10);
            peerIDtoAddress.put(serverID, new InetSocketAddress("localhost", udpPort));
        }
        
        // Convert to ConcurrentHashMap for thread safety
        ConcurrentHashMap<Long, InetSocketAddress> peerMap = new ConcurrentHashMap<>(peerIDtoAddress);
        
        // Start the gateway server
        System.out.println("Starting Gateway Server (ID: " + gatewayID + ")");
        GatewayServer gateway = new GatewayServer(
            gatewayHttpPort,
            peerIDtoAddress.get(gatewayID).getPort(),
            0L,
            gatewayID,
            new ConcurrentHashMap<>(peerMap),
            numberOfObservers
        );
        gateway.setDaemon(false); // Keep JVM alive
        gateway.start();
        
        // Small delay to ensure gateway is up
        Thread.sleep(500);
        
        System.out.println("Gateway started on HTTP port: " + gatewayHttpPort);
        System.out.println("Cluster is running. Press Ctrl+C to stop.");
        
        // Keep the main thread alive
        while (true) {
            Thread.sleep(1000);
        }
    }
}
