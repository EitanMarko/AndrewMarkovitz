package helperFiles;

import cluster.PeerServerImpl;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static helperFiles.PeerServer.ServerState.*;

/**We are implemeting a simplfied version of the election algorithm. For the complete version which covers all possible scenarios, see https://github.com/apache/zookeeper/blob/90f8d835e065ea12dddd8ed9ca20872a4412c78a/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java#L913
 */
public class LeaderElection {
    /**
     * time to wait once we believe we've reached the end of leader election.
     */
    private final static int finalizeWait = 3200;

    /**
     * Upper bound on the amount of time between two consecutive notification checks.
     * This impacts the amount of time to get the system up again after long partitions. Currently 30 seconds.
     */
    private final static int maxNotificationInterval = 30000;
    private PeerServerImpl server;
    //private PeerServer.ServerState state;
    private final LinkedBlockingQueue<Message> incomingMessages;
    Logger logger;
    Vote proposedLeader;
    long proposedEpoch;
    Map<Long, ElectionNotification> votesReceived; // Tracks who sent what vote
    Map<Long, List<Long>> leaderToVotes; // Leader -> Those who elected him
    public LeaderElection(PeerServer server, LinkedBlockingQueue<Message> incomingMessages, Logger logger) {
        this.server = (PeerServerImpl) server;
        this.incomingMessages = incomingMessages;
        this.logger = logger;
        this.proposedLeader = null;
        this.proposedEpoch = server.getPeerEpoch(); // Can be set here because it only changes (PeerServerImpl.setPeerEpoch() is called) in acceptElectionWinner()
        this.votesReceived = new HashMap<>();
        this.leaderToVotes = new HashMap<>();

    }


    /**
     * Note that the logic in the comments below does NOT cover every last "technical" detail you will need to address to implement the election algorithm.
     * How you store all the relevant state, etc., are details you will need to work out.
     * @return the elected leader
     */
    public synchronized Vote lookForLeader() {
        int expBackOff = 100;

        // STAGE 5 - reset proposedLeader and election records for each election
        //this.proposedLeader = null; // STAGE 5 - reset so peer has no leader
        this.votesReceived = new HashMap<>(); // reset records for new election
        this.leaderToVotes = new HashMap<>(); // reset records for new election

        try {
            //send initial notifications to get things started
            this.proposedEpoch++;
            this.server.setPeerEpoch(proposedEpoch); // add one to current epoch

            //STAGE 5
            if(server.getPeerState() != OBSERVER){
                this.proposedLeader = new Vote(server.getServerId(), server.getPeerEpoch());
            }
            else{
                this.proposedLeader = null;
            }
            this.logger.log(Level.FINE,"Epoch #"+proposedEpoch+": Looking for leader"+ " ["+System.nanoTime()+"]\n");
            if(incomingMessages.isEmpty()){
                this.logger.log(Level.FINE,"Message Queue is EMPTY\n");
            }else{
                this.logger.log(Level.FINE,"Message Queue HAS MESSAGES\n");
            }
            //this.logger.log(Level.FINE, "Server's epoch0: "+server.getPeerEpoch()+ " ["+System.currentTimeMillis()+"]");

            sendNotifications();
            //Loop in which we exchange notifications with other servers until we find a leader
            while(true){
                //Remove next notification from queue
                Message message = incomingMessages.poll();
                //If no notifications received...
                if(message == null){
                    //...resend notifications to prompt a reply from others

                    if (server.getPeerState() != OBSERVER) { // OBSERVERs need not repeatedly send messages, because it will clog leader election and other servers will not be able to function in time
                        sendNotifications();
                    }
                    //...use exponential back-off when notifications not received but no longer than maxNotificationInterval...
                    Thread.sleep(expBackOff);
                    expBackOff = Math.min(maxNotificationInterval, expBackOff*2); // add "jitter" to avoid multiple threads retrying in sync?
                }
                else{
                    //If we did get a message...
                        //...if it's for an earlier epoch, or from an observer, ignore it.

                    if(getMsgEpoch(message) < proposedEpoch ){ // if the message is from a lower epoch, reject it
                        continue;
                    }
                    if(getMsgState(message) == OBSERVER){ // Ignore message
                        this.logger.log(Level.FINE, "Received message from "+getMsgLong(message, "senderID")+" (OBSERVER)\n");
                        continue;
                    }else{ // Save so ID of message sender WorkerServer knows who to send messaages to (non-OBSERVER)
                        this.logger.log(Level.FINE, "Received message from "+getMsgLong(message, "senderID") + " with leader "+getNotificationFromMessage(message).getProposedLeaderID()+" [Epoch: "+getMsgEpoch(message)+"], at time ["+System.nanoTime()+"]\n");
                        server.addToNonObserverList(getMsgLong(message, "senderID"));
                    }


                    ElectionNotification notification = getNotificationFromMessage(message);
                    if(notification.getPeerEpoch() < this.server.getPeerEpoch()){ // SAME CODE AS EARLIER?
                        continue;
                    }

                    // Keep epoch updated as long as notification isn't a lower epoch
                    //proposedEpoch = notification.getPeerEpoch();
                    // STAGE 5 - this only does anything when the epoch is higher. This case is handled in the following
                        // supersedesCurrentVote() call
                        // If we kept it as it was, supersedesCurrentVote() would never think the epoch is higher, bc
                            // we were setting this proposedEpoch to the higher one before that method could compare this
                            // old one to the higher one

                        //...if the received message has a vote for a leader which supersedes mine, change my vote (and send notifications to all other voters about my new vote).
                    if(supersedesCurrentVote(notification.getProposedLeaderID(), notification.getPeerEpoch())){
                        //proposedLeader = notification.getProposedLeaderID();
                        proposedLeader = notification;
                        this.logger.log(Level.FINE,"Server "+server.getServerId()+" changed its leader to "+ notification.getProposedLeaderID()+ "\n");
                        sendNotifications();
                    }
                        //(Be sure to keep track of the votes I received and who I received them from.)
                    trackAndUpdateLeaderAndVotes(notification);

                    //If I have enough votes to declare my currently proposed leader as the leader...
                    if(haveEnoughVotes(this.votesReceived, notification)){
                        //..do a last check to see if there are any new votes for a higher ranked possible leader. If there are, continue in my election "while" loop.
                        Thread.sleep(finalizeWait);

                        boolean foundHigherLeader = false;
                        Message nextMessage;
                        while ((nextMessage = incomingMessages.peek()) != null) { // Peek, because if we find a higher number, the big while-loop will run again, and notifications will be sent after the proposedLeader is set to this one

                            //if(getNotificationFromMessage(nextMessage).getProposedLeaderID() > proposedLeader)
                            if (getNotificationFromMessage(nextMessage).getProposedLeaderID() > proposedLeader.getProposedLeaderID()) {
                                foundHigherLeader = true;
                                break;
                            }
                            incomingMessages.remove();
                        }
                        if (foundHigherLeader){
                            continue;
                        }
                            //If there are no new relevant message from the reception queue, set my own state to either LEADING or FOLLOWING and RETURN the elected leader.
                        Vote winner = proposedLeader;
                        if(server.getPeerState() == OBSERVER){
                            int u = 8;
                        }
                        return acceptElectionWinner(winner);
                    }

                }

            }
        }
        catch (Exception e) {
            this.logger.log(Level.SEVERE,"Exception occurred during election; election canceled",e);
        }
        return null;
    }

    private PeerServer.ServerState getMsgState(Message msg){

        ElectionNotification receivedNotification = LeaderElection.getNotificationFromMessage(msg);
        return receivedNotification.getState();

        /*ByteBuffer msgBytes = ByteBuffer.wrap(msg.getMessageContents());
        long leader = msgBytes.getLong();
        return PeerServer.ServerState.getServerState(msgBytes.getChar());*/
    }

    private long getMsgEpoch(Message message){ // This is assuming contents are composed of Vote.toString.getBytes() [see buildMsgContent()]
        return getMsgLong(message, "peerEpoch");
    }

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

    private void sendNotifications(){
        long sendLeader = server.getServerId();
        if(proposedLeader != null){
            sendLeader = proposedLeader.getProposedLeaderID();
            logger.fine("Sending notifications (proposing leader: "+sendLeader+"), time ["+System.nanoTime()+"]\n");
        }else{
            logger.fine("Sending notifications (proposing MYSELF as leader: "+sendLeader+"), time ["+System.nanoTime()+"]\n");
        }
        ElectionNotification electionNotification = new ElectionNotification(sendLeader, server.getPeerState(), server.getServerId(), proposedEpoch);
        server.sendBroadcast(Message.MessageType.ELECTION, buildMsgContent(electionNotification));
    }

    private void trackAndUpdateLeaderAndVotes(ElectionNotification notification){ // Add a vote to leader whether it had votes before or not

        long newLeader = notification.getProposedLeaderID();
        long senderID = notification.getSenderID();

        // if the sender previously had a vote in votesReceived, remove the vote from the previous leader of that senderID
        if (votesReceived.get(senderID) != null){
            long previousLeader = votesReceived.get(senderID).getProposedLeaderID(); // This senderID's previous vote
            leaderToVotes.get(previousLeader).remove(senderID);
        }

        if(leaderToVotes.get(newLeader) == null){
            List<Long> newLeaderList = new ArrayList();
            newLeaderList.add(senderID);

            leaderToVotes.put(newLeader, newLeaderList);
            votesReceived.put(senderID, notification);
            return;
        }
        leaderToVotes.get(newLeader).add(senderID);
        votesReceived.put(senderID, notification); // Update who sent which notifications
    }

    private Vote acceptElectionWinner(Vote n) {
        //set my state to either LEADING or FOLLOWING
        //clear out the incoming queue before returning
        long voteID = n.getProposedLeaderID();
        if(this.server.getServerId() == voteID){
            this.server.setPeerState(PeerServer.ServerState.LEADING);
            logger.fine("Set peer state to LEADING at time ["+System.nanoTime()+"]\n");
        }
        else if(server.getPeerState() != OBSERVER){ // OBSERVERs must remain OBSERVERs
            this.server.setPeerState(FOLLOWING);
            logger.fine("Set peer state to FOLLOWING at time ["+System.nanoTime()+"]\n");
        }
        this.server.setPeerEpoch(n.getPeerEpoch()); // Set epoch to winner's epoch
        this.logger.fine("Setting epoch to "+n.getPeerEpoch());
        this.incomingMessages.clear();
        this.logger.fine("Elected Leader: "+n.getProposedLeaderID()+"\n");
        this.logger.log(Level.FINE,"PeerState is "+server.getPeerState()+ "\n");
        return new Vote(voteID, n.getPeerEpoch());
    }

    /*
     * We return true if one of the following two cases hold:
     * 1- New epoch is higher
     * 2- New epoch is the same as current epoch, but server id is higher.
     */
    protected boolean supersedesCurrentVote(long newId, long newEpoch) {
        if(this.proposedLeader == null){
            return true;
        }

        // STAGE 5:
            // If the (newEpoch > this.proposedEpoch), refresh all record of previous epoch election, and update the proposedEpoch
                // no need to change this peer's epoch, because it will be updated in acceptElectionWinner() when a new
                    // leader is accepted
                // No need to add this vote to records, because it is done after this method call in trackAndUpdateLeaderAndVotes()

                // See trackAndUpdateLeaderAndVotes() for which data structures need to be wiped upon new epoch
                    // //votesReceived and leaderToVotes

        if(newEpoch > this.proposedEpoch){
            votesReceived = new HashMap<>(); // reset records for new election
            leaderToVotes = new HashMap<>(); // reset records for new election
            proposedEpoch = newEpoch; // update LeaderElection epoch
            return true;
        }
        if((newEpoch == this.proposedEpoch) && (newId > this.proposedLeader.getProposedLeaderID())){
            return true;
        }
        return false;

        //return (newEpoch > this.proposedEpoch) || ((newEpoch == this.proposedEpoch) && (newId > this.proposedLeader.getProposedLeaderID()));
    }

    /**
     * Termination predicate. Given a set of votes, determines if we have sufficient support for the proposal to declare the end of the election round.
     * Who voted for who isn't relevant, we only care that each server has one current vote.
     */
    protected boolean haveEnoughVotes(Map<Long, ElectionNotification> votes, Vote proposal) {

        //is the number of votes for the proposal > the size of my peer server’s quorum?
        long proposedLeader = proposal.getProposedLeaderID();
        List<Long> votesForLeader = leaderToVotes.get(proposedLeader);
        logger.fine("Server only received "+votesForLeader.size()+" votes for "+proposal.getProposedLeaderID()+"(from "+votesForLeader+"), and quorum is "+ this.server.getQuorumSize()+"\n");
        if(votesForLeader != null && votesForLeader.size() >= this.server.getQuorumSize()){
            return true;
        }
        int numOfNonObserverPeers = server.getPeerIDtoAddress().size() - server.getNumberOfObservers();
        //if(numOfNonObserverPeers == 1 && proposedLeader == this.proposedLeader)
        if(numOfNonObserverPeers == 1 && proposedLeader == this.proposedLeader.getProposedLeaderID()){ // Added handling for case of 2 peers
            return true;
        }
        return false;

    }

    public static ElectionNotification getNotificationFromMessage(Message msg){
        ByteBuffer msgBytes = ByteBuffer.wrap(msg.getMessageContents());
        long leader = msgBytes.getLong();
        char stateChar = msgBytes.getChar();
        long senderID = msgBytes.getLong();
        long peerEpoch = msgBytes.getLong();
        return new ElectionNotification(leader, PeerServer.ServerState.getServerState(stateChar), senderID, peerEpoch); // What if getState() returns null???

    }

    public static byte[] buildMsgContent(ElectionNotification notification) {
        String notificationString = notification.toString();
        byte[] bytes = new byte[26];
        long leader = notification.getProposedLeaderID();
        char stateChar = notification.getState().getChar();
        long senderID = notification.getSenderID();
        long peerEpoch = notification.getPeerEpoch();

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(leader);
        byte[] leaderBytes = buffer.array();
        for(int i = 0; i < 8; i++){
            bytes[i] = leaderBytes[i];
        }

        // stateChar
        ByteBuffer buffer2 = ByteBuffer.allocate(Character.BYTES);
        buffer2.putChar(stateChar);
        bytes[8] = buffer2.array()[0];
        bytes[9] = buffer2.array()[1];

        ByteBuffer buffer3 = ByteBuffer.allocate(Long.BYTES);
        buffer3.putLong(senderID);
        byte[] senderBytes = buffer3.array();
        for(int i = 10; i < 18; i++){
            bytes[i] = senderBytes[i-10];
        }

        ByteBuffer buffer4 = ByteBuffer.allocate(Long.BYTES);
        buffer4.putLong(peerEpoch);
        byte[] epochBytes = buffer4.array();
        for(int i = 18; i < 26; i++){
            bytes[i] = epochBytes[i-18];
        }
        return bytes; // e.g. (2, 1) -> proposedLeader 2, epoch 1
    }
}