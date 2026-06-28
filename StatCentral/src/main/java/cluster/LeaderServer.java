package cluster;
import helperFiles.LoggingServer;
import helperFiles.Message;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LeaderServer extends Thread implements LoggingServer{

    List<Long> nonObserverPeerIDs;
    Map<Long, InetSocketAddress> peerIDtoAddress;
    private final LinkedBlockingQueue<Message> outgoingMessages;
    private final LinkedBlockingQueue<Message> incomingMessages;
    PeerServerImpl leaderServer;
    Map<Long, ClientInfoHolder> requestIDToClientInfo; // RequestID (long)-> Client info (ClientInfoHolder)
    private Logger logger;
    private final LinkedBlockingQueue<WorkAndStream> workFromClientQueue;
    Executor threadPool;
    TCPServer tcpServer;
    Set<Long> deadPeers;
    // Thread-safe LRU cache: body-hash -> result string (without {requestID:#N} header).
    // Keyed by the hash of the raw request body (after stripping the [N] prefix) so
    // that a retry from a new GatewayServer — which assigns a different request number —
    // still resolves to the same cached result and avoids re-executing the operation.
    // Bounded to 1 000 entries to prevent unbounded growth.
    private final Map<Integer, String> completedWork;


    public LeaderServer(PeerServerImpl leaderServer, Map<Long, InetSocketAddress> peerIDtoAddress,
                            LinkedBlockingQueue<Message> outgoingMessages, LinkedBlockingQueue<Message> incomingMessages, Set<Long> deadPeers, Logger logger) throws IOException {
        this.peerIDtoAddress = peerIDtoAddress;
        this.outgoingMessages = outgoingMessages;
        this.incomingMessages = incomingMessages;
        this.leaderServer = leaderServer;
        this.requestIDToClientInfo = new HashMap<>();
        this.completedWork = Collections.synchronizedMap(
            new LinkedHashMap<Integer, String>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                    return size() > 1000;
                }
            }
        );
        this.deadPeers = deadPeers;
        //this.logger = logger;
        this.logger = initializeLogging(LeaderServer.class.getCanonicalName() + "-on-server_"+leaderServer.getServerId()+"-on-tcpPort-"+leaderServer.getTcpPort());

        nonObserverPeerIDs = leaderServer.getNonObservers();

         threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                r -> { // Thread factory for thread pool
                    Thread thread = new Thread(r);
                    thread.setDaemon(true); // set threads to be daemon threads
                    thread.setName("LeaderServer-Thread-"+thread.threadId()); // Name threads for logging
                    return thread;
                });

        workFromClientQueue = new LinkedBlockingQueue<>();
        tcpServer = new TCPServer(leaderServer, workFromClientQueue); // spin, and just accept work
        tcpServer.setDaemon(true);
    }


    public void shutdown(){
        tcpServer.shutdown();
        interrupt();
    }


    @Override
    public void run() {
        tcpServer.start();

        this.logger.log(Level.FINE,"\n\n\n-------------------------------------------------\nWORK AHEAD - LEADER ("+leaderServer.getServerId()+", TCP Port "+leaderServer.getTcpPort()+")\n-------------------------------------------------\n");
        // Receive message
        // Pick a follower
        // Send to follower
        // Receive response
        // Send back to the gateway (without expecting a response)


        // Steps:
            // Find next peer to send work to
            // Get work to give peer - TCP
            // Get URI of the GatewayServer - HTTP
            // Send to GatewayServer - HTTP

        int i = 0;
        while(!isInterrupted()){

            // Now wait for work to appear in the queue, send it to the worker, then return completed work to the gateway via TCP

            // Pick a worker
            i++;
            int workerIndex = i% nonObserverPeerIDs.size();
            long assignedPeerID = nonObserverPeerIDs.get(workerIndex);

            if(deadPeers.contains(assignedPeerID)){ // if this peer is dead, skip him, and don't send him work
                //nonObserverPeerIDs.remove(workerIndex);
                logger.fine("Skipping node "+assignedPeerID+" because he's dead");

                // Instead of removing this node from the list of workers, we just skip him
                //i++; // move on
                continue;
            }
            logger.fine("Sending Runnable work for server #"+assignedPeerID);

            // Get the work to send - CHANGED IN STAGE5 (in stage4, this came before picking a peer to do work)
            byte[] workToSend = null;
            String code;
            WorkAndStream workAndStream = null;
            try {
                logger.fine("Take()ing off queue"+"\n");
                workAndStream = workFromClientQueue.take();
                workToSend = workAndStream.getWork(); // Wait for work to appear from client thru gateway
                code = new String(workToSend); // for testing
                //logger.info("\n CODE IS: \n\n"+code+"\n\n");
            } catch (InterruptedException e) {
                logger.log(Level.FINE,"Exiting LeaderServer.run()\n");
                break;
            }

            int requestNum = Integer.parseInt(code.substring(code.indexOf("[")+1,code.indexOf("]")));
            //logger.severe("\n REQUESTNUM: "+requestNum+"\n\n");

            // Final vars for Runnable lambda
            final byte[] workToSendFinal = workToSend;
            final WorkAndStream workAndStreamFinal = workAndStream;

            // Runnable to allocate work, send to worker, receive completed work, and return completed work to the gateway
            Runnable allocateWorkAndReturnToGateway = () -> {

                // Compute a hash of the raw request body (everything after the [N] prefix).
                // This is the deduplication key: a retry from a new GatewayServer sends the
                // same body with a different N, so the same body hash resolves to the cached
                // result and prevents re-executing the operation.
                int bracketClose = code.indexOf("]");
                byte[] bodyBytes = Arrays.copyOfRange(workToSendFinal, bracketClose + 1, workToSendFinal.length);
                int bodyHash = Arrays.hashCode(bodyBytes);

                InetSocketAddress workerAddress = leaderServer.getPeerIDtoAddress().get(assignedPeerID);
                if(workerAddress == null){
                    logger.log(Level.SEVERE, "worker address is null for peer ID: "+assignedPeerID);
                }
                int workerTcpPort = workerAddress.getPort() + 2;
                String workerHostName = workerAddress.getHostName();

                Socket leaderSocket;
                OutputStream outToWorker;
                InputStream inFromWorker;

                byte[] responseFromWorker; // Holds completed work sent back from worker
                String responseStr = null;

                String header = "[Sent by leader #"+leaderServer.getServerId()+"]";
                //String header = "[Sent by leader #"+leaderServer.getServerId()+", requestID:"++"]";

                if (!completedWork.containsKey(bodyHash)) { // We have not yet done this work (this server)
                    try {
                        String c = code;
                        leaderSocket = new Socket(workerHostName, workerTcpPort);
                        leaderSocket.setSoTimeout(5_000); // allow up to 5s for DB operations to complete
                        outToWorker = leaderSocket.getOutputStream();
                        inFromWorker = leaderSocket.getInputStream();

                        // Header which tells leader how many bytes to read off outputstream
                        logger.fine("About to send work to worker TCP port: "+ workerTcpPort+"\n");
                        byte[] lengthHeaderBuffer = ByteBuffer.allocate(4).putInt(workToSendFinal.length).array();
                        outToWorker.write(lengthHeaderBuffer);

                        // Now send work to outputstream (for worker to do)
                        outToWorker.write(workToSendFinal);
                        outToWorker.flush();
                        logger.fine("Assigned work to server "+ assignedPeerID+"\n");

                        // See how many bytes to read from inputstream - int value (4 bytes)
                        byte[] lenBytes = inFromWorker.readNBytes(4); // blocking call, will wait for a response
                        if (lenBytes.length < 4) {
                            throw new IOException("Worker " + assignedPeerID + " closed connection before sending response (EOF after " + lenBytes.length + " bytes)");
                        }
                        int len = ByteBuffer.wrap(lenBytes).getInt();

                        //Read that many bytes
                        responseFromWorker = inFromWorker.readNBytes(len);
                        responseStr = new String(responseFromWorker);

                        logger.fine("Received completed work from server "+ assignedPeerID+"\n");

                        // Cache the result-only string (strip the WorkerServer's {requestID:#N} routing
                        // header) BEFORE attempting the write back to the gateway.  If the gateway socket
                        // is already dead the IOException below is caught cleanly and the result is still
                        // available for the interface's retry via a new GatewayServer.
                        int workerHeaderEnd = responseStr.indexOf("}") + 1;
                        String resultOnly = responseStr.substring(workerHeaderEnd);
                        completedWork.put(bodyHash, resultOnly);

                    } catch (IOException e) { // failed server
                        WorkAndStream workAndStreamReallocated = new WorkAndStream(workAndStreamFinal.getWork(), workAndStreamFinal.getOutputStream());
                        workFromClientQueue.offer(workAndStreamReallocated);
                        logger.fine("Reallocated work that failed to send to peer #"+assignedPeerID+"\n");
                        return;
                    }
                }
                else {
                    // Cache hit: a previous attempt already executed this operation and cached
                    // the result.  Reconstruct the WorkerServer response format using the
                    // CURRENT request number so the GatewayReceiver can resolve it to the
                    // correct CompletableFuture in the new (replacement) GatewayServer.
                    String cachedResult = completedWork.get(bodyHash);
                    String workerHeader = "{requestID: #" + requestNum + "}";
                    responseFromWorker = (workerHeader + cachedResult).getBytes();
                    responseStr = new String(responseFromWorker);
                    logger.fine("Cache hit for bodyHash " + bodyHash
                            + " — returning cached result for request #" + requestNum + "\n");
                }


                //Now send completed work back to the gateway
                try {
                    // Include leaderID in response to gateway
                    String response = new String(responseFromWorker);
                    // include here the requestID
                    String leaderIDincluded = header + response;
                    responseFromWorker = leaderIDincluded.getBytes();

                    byte[] lengthHeaderBuffer = ByteBuffer.allocate(4).putInt(responseFromWorker.length).array();
                    OutputStream out = workAndStreamFinal.getOutputStream();
                    out.write(lengthHeaderBuffer);
                    out.write(responseFromWorker);
                    out.flush();
                    logger.fine("Sent completed work from server "+assignedPeerID+ " back to the GatewayServer\n");
                } catch (IOException e) {
                    // The GatewayServer socket is dead (gateway crashed).
                    // The result was already written to completedWork before this send was
                    // attempted, so when the interface restarts the gateway and retries the
                    // request the cache-hit path above will return the cached result without
                    // re-executing the operation against the database.
                    this.logger.log(Level.SEVERE,
                            "IOException writing result back to gateway — result cached for retry (bodyHash="
                            + bodyHash + ", requestNum=" + requestNum + ")");
                }


            };
            threadPool.execute(allocateWorkAndReturnToGateway);
        }

    }

    public void addCompletedWork(Map<Integer, String> completedWork) {
        this.completedWork.putAll(completedWork);
    }

    private class ClientInfoHolder{
        private String host;
        private int port;
        public ClientInfoHolder(String clientHost, int clientPort) {
            this.host = clientHost;
            this.port = clientPort;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }





}
