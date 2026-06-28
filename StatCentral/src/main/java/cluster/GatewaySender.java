package cluster;

import helperFiles.LoggingServer;
import helperFiles.Vote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.logging.Logger;

// set this thread to daemon in calling GatewayServer class!!!!!!!!!!!!
public class GatewaySender extends Thread implements LoggingServer {
    private GatewayPeerServerImpl gatewayPeerServer;
    private ConcurrentHashMap<Integer, CompletableFuture<byte[]>> requestToFuture;
    private LinkedBlockingQueue<byte[]> requestQueue;
    private CompletionService<byte[]> completionService;
    private Logger logger;

    public GatewaySender(GatewayPeerServerImpl gatewayPeerServer, ConcurrentHashMap<Integer, CompletableFuture<byte[]>> requestToFuture, LinkedBlockingQueue<byte[]> requestQueue, CompletionService<byte[]> completionService) {
        this.gatewayPeerServer = gatewayPeerServer;
        this.requestToFuture = requestToFuture;
        this.requestQueue = requestQueue;
        this.completionService = completionService;

        try {
            this.logger = initializeLogging(GatewaySender.class.getCanonicalName() + "-on-gatewayServer");
        } catch (IOException e) {
            // if logger fails, do what?
        }

    }

    // This thread will have a CompletionService

    // There will be a queue that the Gateway puts work on by extracting the relevant info from the HttpExchange, and queuing it up
        // This thread will have access to that queue, and submit the work to a completion service


    // When the Gateway receives a request from the client, it will create a CompletableFuture
    // In a Map<Integer, CompletableFuture> requestIDtoFuture, it will map the request's ID to the CompletableFuture
    // The Gateway will then call Future.get() to wait until the work has properly completed
        // This will return later when GatewaySender thread "completes the future"
        // At this point, Future.get() will return with the result


    // The Runnable in the CompletionService will:
        // Establish a connection with the leader
        // Send the work to the leader
        // Receive a response from the leader

        //Check if the leader that responded is the current leader
            // If no,
                // re-queue the work to be done, and the thread will later attempt to do this work with the new leader
            // If yes,
                // go to the requestIDtoFuture map (above), and complete the future by passing in the response

    // Specs:
    // "The Gateway sends any queued client work (be it new requests, be it requests that the response came from the old
        //leader after it was marked failed) to the new leader"
    // When the CompletionService takes requests off the queue and attempts to send it to the leader, it must know who the leader is
    // If the leader is null, wait until it's not null
    // when it's no longer null, that means a new leader has been chosen, and you can attempt to send it to that leader



    @Override
    public void run() {

        while(!this.isInterrupted()){
            logger.fine("-----------------------------------------------------------------------------------\n");
            byte[] codeBytes = null;
            try {
                logger.fine("take()ing request from the requestQueue\n");
                codeBytes = requestQueue.take();
                logger.fine("Got the byte[] request from the requestQueue\n");
            } catch (InterruptedException e) {
                logger.fine("GatewaySender interrupted when attempting to take() off queue");
                break;
            }
            if(codeBytes == null){ // you can't LinkedblockingQueue.offer(null) - this would throw a NullPointerException
                continue;
            }

            final byte[] finalCodeBytes = codeBytes;

            String codeStr = new String(codeBytes);
            logger.fine("Request's code is:"+codeStr+"\n");
            String returnError = "[requeue this work:]" +codeStr;


            // poll the queue, so when a request appears, you can take it off (do this for every request)
            Callable<byte[]> callable = () -> {

                // establish a connection with the leader
                    // if there is no leader currently, wait until there is one
                Vote leader = gatewayPeerServer.getCurrentLeader();

                // currently in in leader election: send message for GatewayReceiver to re-queue work
                if (leader == null) {
                    return returnError.getBytes();
                }

                //send the work

                byte[] response = new byte[0];
                try {
                    InetSocketAddress leaderAddress = gatewayPeerServer.getPeerIDtoAddress().get(leader.getProposedLeaderID());
                    int leaderTcpPort = leaderAddress.getPort() + 2;
                    String leaderHostName = leaderAddress.getHostName();

                    Socket gatewaySocket = new Socket(leaderHostName, leaderTcpPort);
                    OutputStream out = gatewaySocket.getOutputStream();
                    InputStream in = gatewaySocket.getInputStream();

                    String code = new String(finalCodeBytes); // for testing
                    byte[] lengthHeaderBuffer = ByteBuffer.allocate(4).putInt(finalCodeBytes.length).array();
                    logger.fine("About to send work to leader TCP port: "+ leaderTcpPort+"\n");
                    out.write(lengthHeaderBuffer); // Header which tells leader how many bytes to read off outputstream
                    out.write(finalCodeBytes); // Now the bytes of the code
                    out.flush();

                    // Figure out how many bytes in message body
                    logger.fine("Waiting to read bytes"+"\n");
                    byte[] lenBytes = in.readNBytes(4); // blocking call, will wait for a response

                    String lenBytesStr = new String(lenBytes); // for testing
                    int len = ByteBuffer.wrap(lenBytes).getInt();
                    logger.fine("Finished reading bytes"+"\n");

                    //Read that many bytes
                    response = in.readNBytes(len);

                    String responseStr = new String(response);
                    // Validate leader header before parsing.  During the brief window
                    // between the GatewayPeerServer learning a new leader and that
                    // leader's LeaderServer actually starting, the gateway can
                    // accidentally connect to the new leader's still-running WorkerServer,
                    // which returns "{requestID: #N}result" without the "[Sent by leader
                    // #X]" prefix.  If we let substring() run on that string it throws
                    // StringIndexOutOfBoundsException inside the Callable, which becomes
                    // an ExecutionException that GatewayReceiver skips — the future is
                    // never completed and never re-queued, causing a 15-second timeout.
                    // Returning returnError instead triggers re-queuing so the request is
                    // retried once the real LeaderServer is running.
                    if (!responseStr.startsWith("[Sent by leader #")) {
                        return returnError.getBytes();
                    }
                    // find out who sent the response
                    int leaderSender = Integer.parseInt(responseStr.substring(responseStr.indexOf("#")+1, responseStr.indexOf("]")));

                    // if leader is currently in election (no leader) or the leader that sent this response is not the currrent leader
                    if(gatewayPeerServer.getCurrentLeader() == null || leaderSender != gatewayPeerServer.getCurrentLeader().getProposedLeaderID()){
                        return returnError.getBytes(); // send message for GatewayReceiver to re-queue work
                    }

                    //remove the leader identification part of response
                    response = responseStr.substring(responseStr.indexOf("]")+1).getBytes();
                    String strippedResponseStr = new String(response);
                    return response;

                } catch (IOException e) {
                    //logger.warning("Issue connecting to leader");

                    // could not connect to the leader: send message for GatewayReceiver to re-queue work
                    return returnError.getBytes();

                }



                // receive a response
                    // What if the leader dies and i don't receive a response?
                        // might be ok bc this happens in a daemon thread...

            };
            logger.fine("About to submit() Callable to CompletionService\n");
            completionService.submit(callable); // submit the above callable to the completionService
            logger.fine("Submit()ted Callable to CompletionService\n");
        }
        this.logger.fine("Exiting GatewaySender.run()");

    //PROCESS CALLABLE (above):
        // When a callable completes in the completionservice:

        // check if the Callable's response is from the current leader (use the GatewayPeerServer)
            // if no, re-queue the request
            // if yes,
                // go to the requestIDtoFuture map (above), and complete the future by passing in the response
                    // e.g. (cf.complete("Hello, world!");  // marks it as done with a value)


        // cleanup - may be unnecessary if this is running in a daemon thread and there's no one to call interrupt()
            //executor.shutdown();

    }

    public void shutdown(){
        interrupt();
    }
}
