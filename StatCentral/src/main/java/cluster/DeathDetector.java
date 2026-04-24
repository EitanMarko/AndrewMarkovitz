package cluster;

import helperFiles.LoggingServer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DeathDetector extends Thread implements LoggingServer {
    private PeerServerImpl server;
    private ConcurrentHashMap<Long, Long> lastHeardFromServersMap;
    private Set<Long> deadPeers;
    private Set<Long> cleanupPeers;
    private long failIntervalNano;
    private long cleanupIntervalNano;
    private Logger logger;
    private Logger summaryLogger;
    private Logger verboseLogger;
    public DeathDetector(PeerServerImpl server, Set<Long> deadPeers, ConcurrentHashMap<Long, Long> lastHeardFromServersMap, int failInterval, int cleanupInterval, Set<Long> cleanupPeers, Logger summaryLogger, Logger verboseLogger) {
        this.server = server;
        this.deadPeers = deadPeers;
        this.cleanupPeers = cleanupPeers;
        this.lastHeardFromServersMap = lastHeardFromServersMap;
        long oneMillion = 1000000;
        this.failIntervalNano = failInterval * oneMillion;
        this.cleanupIntervalNano = cleanupInterval * oneMillion;
        try {
            this.logger = initializeLogging(DeathDetector.class.getCanonicalName() + "-on-server_"+this.server.getServerId()+"-on-tcpPort-"+this.server.getTcpPort());
            this.summaryLogger = summaryLogger;
            this.verboseLogger = verboseLogger;
        } catch (IOException e) {
            // error message for problem initializing logging? (println)
        }
    }

    @Override
    public void run() {

        // DON'T MARK YOURSELF DEAD!!! - ADD THIS

        while(!this.isInterrupted()){
            for(Map.Entry<Long, Long> entry: lastHeardFromServersMap.entrySet()){

                if(Objects.equals(entry.getKey(), server.getServerId())){ // Server should never mark itself dead
                    continue;
                }

                if(System.nanoTime() - entry.getValue() > failIntervalNano){ // if its been too long (FAIL time)
                    if(deadPeers.add(entry.getKey())){ // only log when we peer is first detected as dead
                        logger.fine("Server #"+server.getServerId()+" detected dead peer: [Server #"+entry.getKey()+"] at time ["+System.nanoTime()+"]\n");
                        server.reportFailedPeer(entry.getKey());

                        // Summary log
                        summaryLogger.fine("["+server.getServerId()+"]: no heartbeat from server ["+ entry.getKey()+"] - SERVER FAILED\n");
                        //System.out.println("["+server.getServerId()+"]: no heartbeat from server ["+ entry.getKey()+"] - SERVER FAILED\n");
                    }
                }
                if(System.nanoTime() - entry.getValue() > cleanupIntervalNano){
                    if(cleanupPeers.add(entry.getKey())){ // only log when we peer is first cleaned up
                        logger.fine("Server #"+server.getServerId()+" cleaned up dead peer: [Server #"+entry.getKey()+"] at time ["+System.nanoTime()+"]\n");
                    }
                }
            }
        }
        this.logger.fine("Exiting DeathDetector.run() on server "+this.server.getServerId()+"\n");
    }

    public void shutdown(){
        interrupt();
    }
}
