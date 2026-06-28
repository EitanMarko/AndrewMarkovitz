package cluster;

import com.sun.net.httpserver.HttpServer;
import helperFiles.*;
import microservices.StatUpdater;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

// what is an "epoch"?
    // "For the purposes of leader election, a zxid (transaction id) is a single
    // number,  but in some other protocols it is represented as an epoch and a counter

// A:
    // It's a "phase"
    // A.K.A a new round of election algorithm
// Ex:
    // Epoch 1: Node A elected as leader
    // Epoch 2: Node A fails → Node B elected
    // Epoch 3: Node B fails → Node C elected

    // If Node A later sends messages claiming to still be leader,
    // other nodes can reject them because they’re from epoch 1, which is outdated.

public class PeerServerImpl extends Thread implements PeerServer, LoggingServer { // implement Runnable (like example class)?


    private final InetSocketAddress myAddress; // How do I figure out my address?
    private final int udpPort; // Set at constructor
    private ServerState state; // Is there an initial state I should set the server to?
    private final LinkedBlockingQueue<Message> outgoingMessages;
    private final LinkedBlockingQueue<Message> incomingMessages;
    private final LinkedBlockingQueue<Message> incomingElectionMessages;
    private final LinkedBlockingQueue<Message> incomingGossipMessages;
    private Long serverID; // Set at constructor
    private long peerEpoch; // Set at constructor
    private volatile Vote currentLeader; // What is this initialized at? How do I know the leader to begin with?
    private Map<Long,InetSocketAddress> peerIDtoAddress; // Is this the IDs of all other servers? Better as a List?
    private Map<InetSocketAddress, Long> peerAddresstoID;
    private UDPMessageSender senderWorker;
    private UDPMessageReceiver receiverWorker;
    private LeaderElection leaderElection;
    private Logger logger;
    private WorkerServer workerServer;
    private LeaderServer leaderServer;
    private Long gatewayID;
    private int numberOfObservers;
    private Set<Long> nonObservers;

    // -------------------------------------------------------
    // Stage 5 Fields
    // -------------------------------------------------------
    private MessageFilter messageFilter;
    private GossipSender gossipSender;
    private GossipReceiver gossipReceiver;
    private DeathDetector deathDetector;
    private Set<Long> deadPeers;
    private Set<Long> cleanupPeers;
    static final int GOSSIP = 1000;
    static final int FAIL = GOSSIP * 40;
    static final int CLEANUP = FAIL * 2;
    private ConcurrentHashMap<Long, Long> lastHeardFromServersMap;
    private Map<Long, Integer> heartbeatTracker;
    private HttpServer httpServer;
    private static final int HTTP_PORT_OFFSET = 100; // e.g., if UDP is 8010, HTTP is 8110
    private Logger summaryLogger;
    private String summaryLoggerName;
    private Logger verboseLogger;
    private String verboseLoggerName;




    public PeerServerImpl(int udpPort, long peerEpoch, Long serverID, Map<Long, InetSocketAddress> peerIDtoAddress, Long gatewayID, int numberOfObservers) throws IOException {

        if(Objects.equals(serverID, gatewayID)){
            this.state = ServerState.OBSERVER; // Gateway is an OBSERVER
        }else{
            this.state = ServerState.LOOKING;
        }
        this.currentLeader = null;
        this.udpPort = udpPort;
        this.peerEpoch = peerEpoch;
        this.serverID = serverID;
        this.peerIDtoAddress = peerIDtoAddress;
        this.myAddress = new InetSocketAddress("localhost",udpPort); // Copied from https://github.com/Yeshiva-University-CS/com3800-2025/blob/main/src/main/java/edu/yu/cs/com3800/stage2/udp_example/ExamplePeerServer.java
        this.peerIDtoAddress.remove(serverID); // All other Peers are added to this

        this.peerAddresstoID = new HashMap<>();
        for(Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()){
            peerAddresstoID.put(entry.getValue(), entry.getKey()); // flip it
        }

        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.incomingMessages = new LinkedBlockingQueue<>(); // where all messages get sent to originally
        this.incomingElectionMessages = new LinkedBlockingQueue<>(); // filtered for ELECTION messages only
        this.incomingGossipMessages = new LinkedBlockingQueue<>(); // filtered for GOSSIP messages only
        this.logger = initializeLogging(PeerServerImpl.class.getCanonicalName() + "-on-server_"+serverID+"-on-tcpPort-"+getTcpPort());
        this.leaderElection = new LeaderElection(this, this.incomingElectionMessages, this.logger);
        this.gatewayID = gatewayID;
        this.numberOfObservers = numberOfObservers;
        this.nonObservers = new HashSet<>();

        // -------------------------------------------------------
        // Stage 5 Fields
        // -------------------------------------------------------
        this.deadPeers = new HashSet<>();
        this.cleanupPeers = new HashSet<>(); // pass to GossipSender and DeathDetector
        this.lastHeardFromServersMap = new ConcurrentHashMap<>(); // Will be filled in the run() method
        this.heartbeatTracker = new HashMap<>(); // tracks heartbeats of all other servers: (serverID -> lastHeartbeat)
        this.summaryLogger = initializeLogging(PeerServerImpl.class.getCanonicalName() + "-on-server_"+serverID+"-summary");
        //this.logger.severe(summaryLogger.getName());
        this.summaryLoggerName = getLoggerName(summaryLogger);
        this.verboseLogger = initializeLogging(PeerServerImpl.class.getCanonicalName() + "-on-server_"+serverID+"-verbose");
        this.verboseLoggerName = getLoggerName(verboseLogger);
    }

    private String getLoggerName(Logger logger){
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-kk_mm");
        String suffix = date.format(formatter);
        String dirName = "logs-" + suffix;
        String subStringBy = "edu.yu.cs.com3800.stage5.PeerServerImpl";
        int indexOfSubname = logger.getName().indexOf(subStringBy);
        //logger.severe("index of subname: "+indexOfSubname);
        String subName = logger.getName().substring(indexOfSubname+subStringBy.length());
        //logger.severe("subname: "+subName);
        String name = dirName+File.separator+subName+"-Log.txt";
        //logger.severe("name returning: "+name);

        return name;
    }


    @Override
    public void shutdown() {

        if(httpServer != null){
            httpServer.stop(0);
        }

        this.senderWorker.shutdown();
        this.receiverWorker.shutdown();
        this.messageFilter.shutdown();
        this.gossipSender.shutdown();
        this.gossipReceiver.shutdown();
        this.deathDetector.shutdown();

        switch (getPeerState()){
            case FOLLOWING:
                if (checkNotNull(workerServer == null, "\nworkerServer == null\n")){
                    break; // This should never hit, but if it does for some reason, program will end gracefully because this is a daemon thread
                }
                workerServer.shutdown();
                break;
            case LEADING:
                if (checkNotNull(leaderServer == null, "\nleaderServer == null\n")){
                    break; // This should never hit, but if it does for some reason, program will end gracefully because this is a daemon thread
                }
                leaderServer.shutdown(); // This will also effectively shutdown TCPServer
        }
        interrupt(); // Shut down this PeerServerImpl
    }

    private boolean checkNotNull(boolean isNull, String msg) {
        if (isNull) {
            this.logger.log(Level.SEVERE, msg);
            return true;
        }
        return false;
    }

    public Long getGatewayID() {
        return gatewayID;
    }

    public List<Long> getNonObservers() {
        return new ArrayList<>(nonObservers);

    }

    public void addToNonObserverList(long nonObserverID){
        nonObservers.add(nonObserverID);
    }

    public int getTcpPort() {
        return udpPort+2;
    }

    public int getNumberOfObservers() {
        return numberOfObservers;
    }

    @Override
    public void setCurrentLeader(Vote v) throws IOException {
        this.currentLeader = v;
    }

    @Override
    public Vote getCurrentLeader() {
        return this.currentLeader;
    }

    @Override
    public void sendMessage(Message.MessageType type, byte[] messageContents, InetSocketAddress target) throws IllegalArgumentException {
        Message msg = new Message(type,messageContents, myAddress.getHostString(), getUdpPort(), target.getHostString(), target.getPort());
        this.outgoingMessages.offer(msg);
    }

    @Override
    public void sendBroadcast(Message.MessageType type, byte[] messageContents) {
        for(InetSocketAddress peer : peerIDtoAddress.values()) {
            sendMessage(type, messageContents, peer);
        }
    }

    @Override
    public ServerState getPeerState() {
        return this.state;
    }

    @Override
    public void setPeerState(ServerState newState) {
        summaryLogger.fine("["+getServerId()+"]: switching from ["+getPeerState()+"] to ["+newState+"]\n");
        //System.out.println("["+getServerId()+"]: switching from ["+getPeerState()+"] to ["+newState+"]\n");
        this.state = newState;
    }

    @Override
    public Long getServerId() {
        return this.serverID;
    }

    @Override
    public long getPeerEpoch() {
        return this.peerEpoch;
    }

    public void setPeerEpoch(long peerEpoch) {
        this.peerEpoch = peerEpoch;
    }

    @Override
    public InetSocketAddress getAddress() {
        return this.myAddress;
    }

    @Override
    public int getUdpPort() {
        return this.udpPort;
    }

    @Override
    public InetSocketAddress getPeerByID(long peerId) {
        return peerIDtoAddress.get(peerId);
    }

    @Override
    public int getQuorumSize() {
        int numOfNonObserverPeers = (peerIDtoAddress.size() - deadPeers.size()) - getNumberOfObservers(); // subtract number of dead nodes
        // What if there's only one peer left?
        if(numOfNonObserverPeers < 3){
            return numOfNonObserverPeers/2;
        }
        return ((numOfNonObserverPeers + 1) / 2 ) + 1;

        //peerIDtoAddress.size() is all the peers except this one
        // peerIDtoAddress.size() + 1 == all servers
        // ((peerIDtoAddress.size() + 1)/2) +1 --> "more than half"
    }

    public Map<Long, InetSocketAddress> getPeerIDtoAddress() {
        return peerIDtoAddress;
    }

    @Override
    public void run() {

        try {

            try {
                setupHttpServer();
            } catch (IOException e) {
                logger.severe("Issue with creating endpoints for loggers\n");
            }

            this.senderWorker = new UDPMessageSender(outgoingMessages, udpPort);
            this.senderWorker.setDaemon(true); //daemon
            this.senderWorker.start();

            this.receiverWorker = new UDPMessageReceiver(incomingMessages, myAddress, udpPort, this);
            this.receiverWorker.setDaemon(true); // daemon
            this.receiverWorker.start();

            // Filters messages so leader election only sees ELECTION messages and Gossip-reading Thread only reads GOSSIP messages
            this.messageFilter = new MessageFilter(this, incomingMessages, incomingElectionMessages, incomingGossipMessages);
            this.messageFilter.setDaemon(true);
            this.messageFilter.start();

            // Insert time stamps into the map to track when other servers were last heard from - SHOULD I DO THIS???
            for(Long id : getPeerIDtoAddress().keySet()){ // other servers
                lastHeardFromServersMap.put(id, System.nanoTime());
            }
            lastHeardFromServersMap.put(getServerId(), System.nanoTime()); // this server

            this.gossipSender = new GossipSender(this, GOSSIP, lastHeardFromServersMap, heartbeatTracker, cleanupPeers);
            this.gossipSender.setDaemon(true);
            this.gossipSender.start();

            this.gossipReceiver = new GossipReceiver(this, incomingGossipMessages, lastHeardFromServersMap, deadPeers, FAIL, heartbeatTracker, cleanupPeers, summaryLogger, verboseLogger);
            this.gossipReceiver.setDaemon(true);
            this.gossipReceiver.start();

            this.deathDetector = new DeathDetector(this, deadPeers, lastHeardFromServersMap, FAIL, CLEANUP, cleanupPeers, summaryLogger, verboseLogger);
            this.deathDetector.setDaemon(true);
            this.deathDetector.start();

            Set<Integer> ports = new HashSet<>();
            for(Long id : peerIDtoAddress.keySet()){
                ports.add(peerIDtoAddress.get(id).getPort());
            }

        } catch (IOException e) {
            logger.severe("IOException in PeerServerImpl.run()");
            return;
        }


        // Leader election
        boolean foundLeader = false;

        if(getPeerState() == ServerState.OBSERVER){ // Outside while-loop, so only logs once
            if(Objects.equals(serverID, gatewayID)){
                this.logger.log(Level.FINE,"\n\n\n-------------------------------------------------\nTRACKING - GATEWAYSERVER [OBSERVER]  ("+getServerId()+")\n-------------------------------------------------\n");

            } else {
                this.logger.log(Level.FINE, "\n\n\n-------------------------------------------------\nTRACKING - OBSERVER (" + getServerId() + ")\n-------------------------------------------------\n");
            }
        }

        while (!this.isInterrupted()){
            switch (getPeerState()){
                case OBSERVER: // OBSERVER "participates" in the election, but its votes are ignored
                case LOOKING:
                    Vote leader = leaderElection.lookForLeader();
                    try {
                        //logger.warning("Server "+getServerId()+" ABOUT TO set current leader");
                        setCurrentLeader(leader);
                        //logger.warning("Server "+getServerId()+" set current leader ["+System.currentTimeMillis()+"]");
                    } catch (IOException e) {
                        this.logger.log(Level.SEVERE, "IOException in server "+getServerId());
                        interrupt(); // Set interrupt flag so while-loop exits
                        break;
                    }
                    foundLeader = true;
                    break;

                case FOLLOWING:
                case LEADING:
                        if(!incomingMessages.isEmpty()){

                            // If there's an election message, set to LOOKING, and loop will take you to the election ( leaderElection.lookForLeader() )
                            Message msg = incomingMessages.peek();
                            if(msg != null && msg.getMessageType() == Message.MessageType.ELECTION){
                                ElectionNotification notification = LeaderElection.getNotificationFromMessage(msg);

                                if(notification.getState() == ServerState.LOOKING){
                                    setPeerState(ServerState.LOOKING);
                                    continue;
                                }
                            }

                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                this.logger.log(Level.FINE, "LEADER INTERRUPTED - BREAKING\n\n");
                                interrupt(); // Reset interrupt flag so while-loop exits
                                break;
                            }
                        }

            }
            if(foundLeader){ // OBSERVER will never hit this
                break;
            }
        }
        if(getPeerState() == ServerState.OBSERVER && this.isInterrupted()){
            this.logger.log(Level.FINE, "SHUTDOWN");
        }

        switch (getPeerState()){
            case FOLLOWING:

                try {
                    Map<String, StatUpdater> statUpdaterRegistry = new HashMap<>();
                    //statUpdaterRegistry.put("MLB", new impl.MLBStatUpdater());
                    workerServer = new WorkerServer(this, peerIDtoAddress.get(currentLeader.getProposedLeaderID()), outgoingMessages, incomingMessages, logger, FAIL, statUpdaterRegistry);
                } catch (IOException e) {
                    this.logger.log(Level.SEVERE, "Error when initializing WorkerServer's logger");
                    this.logger.log(Level.SEVERE, e.getMessage());
                }
                workerServer.setDaemon(true); // Just in case
                workerServer.start();
                break;

            case LEADING:
                try {
                    leaderServer = new LeaderServer(this, peerIDtoAddress, outgoingMessages, incomingMessages, deadPeers, logger);
                } catch (IOException e) {
                    this.logger.log(Level.SEVERE, "ServerSocket could not be instantiated in the LeaderServer's constructor (leaderServer's tcpPort: "+getTcpPort()+")");
                    this.logger.log(Level.SEVERE, e.getMessage());

                    break;
                }
                leaderServer.setDaemon(true); // Just in case
                leaderServer.start();
        }


    }

    @Override
    public boolean isPeerDead(long peerID) {
        return deadPeers.contains(peerID);
    }

    @Override
    public boolean isPeerDead(InetSocketAddress address) {
        return deadPeers.contains(peerAddresstoID.get(address));
    }

    @Override
    public void reportFailedPeer(long peerID) {
        boolean leaderDied = false;
        if(getCurrentLeader() != null && getCurrentLeader().getProposedLeaderID() == peerID){
            // start new election
            logger.fine("\n\nNEW ELECTION: Server #"+getServerId()+" IS ENTERING A NEW ELECTION \n\n");
            leaderDied = true;
            setPeerState(ServerState.LOOKING);
            logger.fine("Set peer state to LOOKING at time ["+System.nanoTime()+"]\n");
            try {
                setCurrentLeader(null);
            } catch (IOException e) {
                logger.warning("IOException when attempting to re-elect leader");
            }
            Vote leader = leaderElection.lookForLeader();
            try {
                setCurrentLeader(leader);
            } catch (IOException e) {
                this.logger.warning("Couldn't set current leader in reportFailedPeer()");
            }
            logger.fine("\n\nNEW ELECTION: Server #"+getServerId()+" has the following ID as its leader: "+leader.getProposedLeaderID()+" and its state is "+getPeerState()+"\n\n");
        }

        // if the old leader died, and there was a new election
        if (leaderDied) {
            if(getPeerState() == ServerState.LEADING){ // this is the leader
                workerServer.shutdown();

                try {
                    leaderServer = new LeaderServer(this, peerIDtoAddress, outgoingMessages, incomingMessages, deadPeers, logger);
                } catch (IOException e) {
                    this.logger.log(Level.SEVERE, "ServerSocket could not be instantiated in the LeaderServer's constructor (leaderServer's tcpPort: "+getTcpPort()+")");
                }
                leaderServer.setDaemon(true); // Just in case
                // add all completed work to the leaderServer
                leaderServer.addCompletedWork(workerServer.getCompletedWork());
                // all work completed by followers that remained followers will just be resent as cached (check Piazza @140)
                leaderServer.start();
            }
            if(getPeerState() == ServerState.FOLLOWING){
                // follower doesn't need to change the socket connection of the WorkerServer, because the socket is a
                    //ServerSocket that the new leader will just connect to
            }
        }
    }


    private void setupHttpServer() throws IOException {
        int httpPort = this.udpPort + HTTP_PORT_OFFSET;
        this.httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);

        // Summary log endpoint
        httpServer.createContext("/summary-log", exchange -> {
            try {
                //logger.severe(summaryLogger.getName());
                //String logPath = "logs/" + getServerId() + "-summary.log";
                String logPath = summaryLoggerName;
                //logger.severe("Created summary log file at path: "+logPath);
                java.nio.file.Path path = java.nio.file.Paths.get(logPath);
                byte[] logContent = java.nio.file.Files.readAllBytes(path);

                exchange.sendResponseHeaders(200, logContent.length);
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(logContent);
                os.close();
            } catch (IOException e) {
                byte[] error = "Log file not found".getBytes();
                exchange.sendResponseHeaders(404, error.length);
                exchange.getResponseBody().write(error);
                exchange.getResponseBody().close();
            }
        });

        // Verbose log endpoint
        httpServer.createContext("/verbose-log", exchange -> {
            try {
                String logPath = verboseLoggerName;
                //logger.severe("Created verbose log file at path: "+logPath);
                java.nio.file.Path path = java.nio.file.Paths.get(logPath);
                byte[] logContent = java.nio.file.Files.readAllBytes(path);

                exchange.sendResponseHeaders(200, logContent.length);
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(logContent);
                os.close();
            } catch (IOException e) {
                byte[] error = "Log file not found".getBytes();
                exchange.sendResponseHeaders(404, error.length);
                exchange.getResponseBody().write(error);
                exchange.getResponseBody().close();
            }
        });

        // Get log paths in bash script
        httpServer.createContext("/log-paths", exchange -> {
            try {
                // Plain text response: line 1 = summary path, line 2 = verbose path
                String response = summaryLoggerName + "\n" + verboseLoggerName;

                byte[] responseBytes = response.getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);
                java.io.OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } catch (Exception e) {
                byte[] error = "Could not retrieve log paths".getBytes();
                exchange.sendResponseHeaders(500, error.length);
                exchange.getResponseBody().write(error);
                exchange.getResponseBody().close();
            }
        });

        httpServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
        httpServer.start();

        logger.fine("HTTP server started on port: " + httpPort);
    }
}


