package cluster;

import helperFiles.ElectionNotification;
import helperFiles.LeaderElection;
import helperFiles.LoggingServer;
import helperFiles.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

// Class to send GOSSIP messages for its PeerServerImpl
public class GossipSender extends Thread implements LoggingServer {

    private int gossipInterval;
    private PeerServerImpl server;
    private Logger logger;
    private ConcurrentHashMap<Long, Long> lastHeardFromServersMap; // Maps servers to the last time we heard from them
    private Map<Long, Integer> heartbeatTracker; // serverID -> lastHeartbeat
    private Set<Long> cleanupPeers;


    public GossipSender(PeerServerImpl server, int gossipInterval, ConcurrentHashMap<Long, Long> lastHeardFromServersMap, Map<Long, Integer> heartbeatTracker, Set<Long> cleanupPeers) {
        this.gossipInterval = gossipInterval;
        this.server = server;
        this.lastHeardFromServersMap = lastHeardFromServersMap;
        this.heartbeatTracker = heartbeatTracker;
        this.cleanupPeers = cleanupPeers;

        /*this.lastHeardFromServersMap = new HashMap<>();
        for(Long id : server.getPeerIDtoAddress().keySet()){
            lastHeardFromServersMap.put(id, System.nanoTime());
        }*/
        try {
            this.logger = initializeLogging(GossipSender.class.getCanonicalName() + "-on-server_"+this.server.getServerId()+"-on-tcpPort-"+this.server.getTcpPort());
        } catch (IOException e) {
            // error message for problem initializing logging? (println)
        }

    }

    // Fix this method so GOSSIP messages only get sent one at a time and to random peers
    @Override
    public void run() {
        List<Map.Entry<Long, InetSocketAddress>> list = new ArrayList<>(server.getPeerIDtoAddress().entrySet());
        int sequenceNumber = 0;
        while(!this.isInterrupted()){
            sequenceNumber++;
            // broadcast GOSSIP messages
            double randomNum = Math.random();
            while(randomNum < list.size()){
                randomNum = randomNum*10;
            }

            Long sendToPeerID = list.get((int) (randomNum % list.size())).getKey();
            InetSocketAddress sendToPeerAddress = server.getPeerIDtoAddress().get(sendToPeerID);

            server.sendMessage(Message.MessageType.GOSSIP, buildMsgContent(sequenceNumber), sendToPeerAddress);
            logger.fine("Server "+server.getServerId()+" sent GOSSIP sequence #"+sequenceNumber+" to server "+ sendToPeerID+" ["+System.nanoTime()+"]"+"\n");
            try {
                Thread.sleep(gossipInterval);
            } catch (InterruptedException e) {
                break;
            }
        }
        this.logger.fine("Exiting GossipSender.run() on server "+this.server.getServerId()+"\n");
    }

    public void shutdown(){
        interrupt();
    }

    private byte[] buildMsgContent(int sequenceNumber) {

        String contentStr = "{From server "+server.getServerId()+", seqNum: "+sequenceNumber+"} -> ";
        for(Long id : lastHeardFromServersMap.keySet()){

            if(cleanupPeers.contains(id)){
                logger.fine("Not sending cleaned up peer #"+id+"\n");
                continue; // skip over "cleaned up" peers
            }
            int heartbeat;
            if(Objects.equals(id, server.getServerId())){
                heartbeat = sequenceNumber;
            }
            else{
                if(heartbeatTracker.get(id) == null){
                    continue;
                    // For now, in the impl where lastHeardFromServersMap starts with all servers known, just move on if there's no heartbeat for a server
                    // If we have to re-impl so servers "find out" about each other, this shouldn't be necessary because servers will only be in the lastHeardFromServersMap if they're in the heartbeatTracker
                        // Because a server will only know about another server if it got a heartbeat from it
                        // Order to add a server then:
                            // First: heartbeatTracker
                            // Second: lastHeardFromServersMap
                                // This way, you'll never have a situation where a server is in the lastHeardFromServersMap but not the heartbeatTracker, and therefore, you won't run into this issue
                }
                heartbeat = heartbeatTracker.get(id);
            }
            String addRecord = "("+ id + ") : [" + heartbeat+"], ";
            contentStr += addRecord;
        }
        return contentStr.getBytes();
        // At the end, just turn it into a byte[]
    }
}
