package cluster;

import helperFiles.ElectionNotification;
import helperFiles.LoggingServer;
import helperFiles.Message;
import helperFiles.PeerServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import static helperFiles.LeaderElection.buildMsgContent;

public class MessageFilter extends Thread implements LoggingServer {

    private LinkedBlockingQueue<Message> incomingQueue;
    private LinkedBlockingQueue<Message> electionQueue;
    private LinkedBlockingQueue<Message> gossipQueue;
    private PeerServerImpl server;
    private Logger logger;

    public MessageFilter(PeerServerImpl server, LinkedBlockingQueue<Message> incomingQueue,
                         LinkedBlockingQueue<Message> electionQueue,
                         LinkedBlockingQueue<Message> gossipQueue) {
        this.incomingQueue = incomingQueue;
        this.electionQueue = electionQueue;
        this.gossipQueue = gossipQueue;
        this.server = server;
        try {
            this.logger = initializeLogging(MessageFilter.class.getCanonicalName() + "-on-server_"+this.server.getServerId()+"-on-tcpPort-"+this.server.getTcpPort());
        } catch (IOException e) {
            // error message for problem initializing logging? (println)
        }
    }

    @Override
    public void run() {
        while(!isInterrupted()){
            Message msg;
            try {
                msg = incomingQueue.take();
            } catch (InterruptedException e) {
                break;
            }
            if(msg.getMessageType() == Message.MessageType.ELECTION){
                /*logger.fine("---------------------------------------------------------------------------------------------------------------\n");
                logger.fine("Got ELECTION message in "+ server.getPeerState()+ " state, from server #"+getMsgLong(msg, "senderID")+" (epoch "+getMsgLong(msg, "peerEpoch")+"), electing server #"+getMsgLong(msg, "leader")+", at time ["+System.nanoTime()+"]\n");
                // respond to that election notification if not currently in election (or an observer)
                //if(server.getPeerState() == PeerServer.ServerState.LEADING || server.getPeerState() == PeerServer.ServerState.FOLLOWING){

                if(server.getPeerState() != PeerServer.ServerState.LOOKING && server.getCurrentLeader() != null){ //not before first election
                    ElectionNotification electionNotification = new ElectionNotification(server.getCurrentLeader().getProposedLeaderID(), server.getPeerState(), server.getServerId(), server.getPeerEpoch());
                    //get the id of the sender from the message payload
                    // use the server's map of id:address to route this new message back
                    InetSocketAddress senderAddress = server.getPeerIDtoAddress().get(getMsgLong(msg, "senderID"));
                    server.sendMessage(Message.MessageType.ELECTION, buildMsgContent(electionNotification), senderAddress);

                    //logger.fine("---------------------------------------------------------------------------------------------------------------\nReceived election message from Server #"+getMsgLong(msg, "senderID")+ " [epoch: "+getMsgLong(msg, "peerEpoch")+"] at time ["+System.nanoTime()+"]");
                    logger.fine("Responding with election message for leader"+server.getCurrentLeader().getProposedLeaderID()+ " [epoch: "+server.getPeerEpoch()+"] at time ["+System.nanoTime()+"]\n");

                }
                if(server.getPeerState() == PeerServer.ServerState.LOOKING){
                    logger.fine("PeerState is LOOKING, so no response sent\n");
                }
                if(server.getCurrentLeader() == null){
                    logger.fine("Leader is null, so no response sent");
                }
                logger.fine("---------------------------------------------------------------------------------------------------------------\n");
                */
                electionQueue.offer(msg);
            }
            if(msg.getMessageType() == Message.MessageType.GOSSIP){
                logger.fine("Got GOSSIP message");
                gossipQueue.offer(msg);
            }
            // WHAT HAPPENS WITH OTHER TYPES OF MESSAGES???
        }
        this.logger.fine("Exiting MessageFilter.run() on server "+this.server.getServerId()+"\n");
    }

    public void shutdown(){
        interrupt();
    }

    // copied method from LeaderElection class
    private long getMsgLong(Message message, String lookingFor){
        ByteBuffer msgBytes = ByteBuffer.wrap(message.getMessageContents());
        long leader = msgBytes.getLong();
        char stateChar = msgBytes.getChar();
        long senderID = msgBytes.getLong();
        long peerEpoch = msgBytes.getLong();

        if(lookingFor.equals("leader")){
            return leader;
        }
        if(lookingFor.equals("senderID")){
            return senderID;
        }
        if(lookingFor.equals("peerEpoch")){
            return peerEpoch;
        }
        return -1; // THIS WILL NEVER HIT
    }
}
