package cluster;

import helperFiles.LoggingServer;
import helperFiles.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

// Implement this class such that:
    // It reads GOSSIP messages
    // Applies changes to list of dead nodes
    // Applies changes to quorum size
public class GossipReceiver extends Thread implements LoggingServer {
    private PeerServerImpl server;
    private LinkedBlockingQueue<Message> queue;
    private ConcurrentHashMap<Long, Long> lastHeardFromServersMap; // Maps servers to the last time we heard from them
    private Map<Long, Integer> heartbeatTracker; // serverID -> lastHeartbeat
    private int lastSequenceNumber;
    private long failIntervalNano;
    private Set<Long> deadPeers;
    private Set<Long> cleanupPeers;
    private Logger summaryLogger;
    private Logger verboseLogger;

    private Logger logger;
    public GossipReceiver(PeerServerImpl server, LinkedBlockingQueue<Message> queue, ConcurrentHashMap<Long, Long> lastHeardFromServersMap, Set<Long> deadPeers, int failInterval, Map<Long, Integer> heartbeatTracker, Set<Long> cleanupPeers, Logger summaryLogger, Logger verboseLogger) {
        this.server = server;
        this.queue = queue;
        this.lastHeardFromServersMap = lastHeardFromServersMap;
        this.heartbeatTracker = heartbeatTracker;
        this.deadPeers = deadPeers;
        this.cleanupPeers = cleanupPeers; // pass to GossipSender and DeathDetector
        this.lastSequenceNumber = 0;
        long oneBillion = 1_000_000_000;
        this.failIntervalNano = failInterval * oneBillion;

        try {
            this.logger = initializeLogging(GossipReceiver.class.getCanonicalName() + "-on-server_"+this.server.getServerId()+"-on-tcpPort-"+this.server.getTcpPort());
            this.summaryLogger = summaryLogger;
            this.verboseLogger = verboseLogger;
        } catch (IOException e) {
            // error message for problem initializing logging? (println)
        }
    }

    @Override
    public void run() {
        // code here
        while(!this.isInterrupted()) {
            Message msg;
            try {
                msg = this.queue.take();
            } catch (InterruptedException e) {
                break;
            }

            // Add method to synchronize the contents of the message with the data this server knows
            readEntries(msg);
            //msg.getMessageContents();
        }
        this.logger.fine("Exiting GossipReceiver.run() on server "+this.server.getServerId()+"\n");
    }

    public void shutdown(){
        interrupt();
    }

    public void readEntries(Message msg){

        // String contentStr = "{From server 12, seqNum: 42} -> (3) : [1699999999], (7) : [1700000123], ";
        String contentStr = new String(msg.getMessageContents());
        // ---- Header ----
        int headerEnd = contentStr.indexOf("}");
        String header = contentStr.substring(0, headerEnd)
                .replace("{From server ", "");

        String[] headerParts = header.split(", seqNum: ");
        long serverId = Long.parseLong(headerParts[0]);
        int sequenceNumber = Integer.parseInt(headerParts[1]);

        if (deadPeers.contains(serverId)) {
            logger.fine("Rejecting GOSSIP message #"+sequenceNumber+ " from dead server "+serverId+"\n");
            return;
        }

        lastSequenceNumber = sequenceNumber; // Update the sequence number

        // Records
        String recordsPart = contentStr.substring(headerEnd + 4); // "} -> "
        String[] records = recordsPart.split(", ");

        lastHeardFromServersMap.put(serverId, System.nanoTime()); // update that we last heard from this server directly

        verboseLogger.fine("\n\nReceived GOSSIP message from server #"+serverId+", at sequence #"+sequenceNumber+", at (nano)time ["+System.nanoTime()+"]:\n\n");
        for (String record : records) {
            if (record.isBlank()){
                logger.info("BLANK RECORD");
                continue;
            }

            int openParen = record.indexOf('(');
            int closeParen = record.indexOf(')');
            int openBracket = record.indexOf('[');
            int closeBracket = record.indexOf(']');

            long id = Long.parseLong(record.substring(openParen + 1, closeParen));
            int heartbeat = Integer.parseInt(record.substring(openBracket + 1, closeBracket));

            if(cleanupPeers.contains(id)){
                logger.fine("Rejecting heartbeat about cleaned up server #"+id+" at time ["+System.nanoTime()+"]\n");
                continue; // skip over "cleaned up" peer
            }

            logger.fine("Server "+serverId+" sent GOSSIP sequence #"+sequenceNumber+":\n"+"sID= " + id + ", heartbeat= " + heartbeat+", Received at: ["+System.nanoTime()+"]");
            // Add verbose logging here
            verboseLogger.fine("Server #"+id+", heartbeat == "+heartbeat);

            if(!deadPeers.contains(id)){ // if server is alive
                if(heartbeatTracker.get(id) == null || heartbeat > heartbeatTracker.get(id) ){ // if no record of this server yet, or received updated heartbeat
                    //lastHeardFromServersMap.put(id, System.nanoTime());
                    logger.fine("Updating heartbeat for server #"+id+" from ["+ heartbeatTracker.get(id)+ "] to ["+ heartbeat+"]\n");
                    heartbeatTracker.put(id, heartbeat); // update heartbeat for this server

                    // Summary log
                    summaryLogger.fine("["+server.getServerId()+"]: updated ["+id+"]'s heartbeat sequence to ["+heartbeat+"] based on message from ["+serverId+"] at node time ["+System.nanoTime()+"]\n");
                }
                // Else, server is alive, and there is a record of a heartbeat from it, and the new heartbeat is not greater than the old heartbeat tracked, don't update
                else{
                    logger.fine("No update to server #"+id+"'s heartbeat. Old: ["+heartbeatTracker.get(id)+ "], New: ["+ heartbeat+"]\n");
                }
            }
            else{
                logger.fine("Server "+id+" is dead, so can't update heartbeat\n");
            }


        }
        logger.fine("----------------------------------------------------------------------------------\n");
        verboseLogger.fine("\n----------------------------------------------------------------------------------\n");

    }

}
