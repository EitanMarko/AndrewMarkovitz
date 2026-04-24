package cluster;

import helperFiles.LoggingServer;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class GatewayReceiver extends Thread implements LoggingServer {
    private GatewayPeerServerImpl gatewayPeerServer;
    private ConcurrentHashMap<Integer, CompletableFuture<byte[]>> requestToFuture;
    private LinkedBlockingQueue<byte[]> requestQueue;
    private CompletionService<byte[]> completionService;
    private Logger logger;

    public GatewayReceiver(GatewayPeerServerImpl gatewayPeerServer, ConcurrentHashMap<Integer, CompletableFuture<byte[]>> requestToFuture, LinkedBlockingQueue<byte[]> requestQueue, CompletionService<byte[]> completionService) {
        this.gatewayPeerServer = gatewayPeerServer;
        this.requestToFuture = requestToFuture;
        this.requestQueue = requestQueue;
        this.completionService = completionService;

        try {
            this.logger = initializeLogging(GatewayReceiver.class.getCanonicalName() + "-on-gatewayServer");
        } catch (IOException e) {
            // if logger fails, do what?
        }
    }


    @Override
    public void run() {
        // poll the CompletionService

        //PROCESS CALLABLE (above):
        // When a callable completes in the completionservice:

        // check if the Callable's response is from the current leader (use the GatewayPeerServer)
            // if no, re-queue the request
            // if yes,
                // go to the requestIDtoFuture map (above), and complete the future by passing in the response
                    // e.g. (cf.complete("Hello, world!");  // marks it as done with a value)

        while(!this.isInterrupted()){
            logger.fine("-----------------------------------------------------------------------------------\n");
            Future<byte[]> responseFuture = null;
            try {
                logger.fine("take()ing response from the CompletionService\n");
                responseFuture = completionService.take();
                logger.fine("Got a future from the CompletionService\n");
            } catch (InterruptedException e) {
                logger.fine("InterruptedException in GatewayReceiver");
                break;
            }

            byte[] response = null;
            try {
                logger.fine("Getting result from the Future\n");
                response = responseFuture.get();
            } catch (InterruptedException e) {
                logger.warning("InterruptedException in GatewayReceiver");
                break;
            } catch (ExecutionException e) {
                // The Callable submitted by GatewaySender threw an exception
                // (e.g. an IOException because the leader's TCP socket wasn't
                // ready yet, or the leader crashed mid-request).  The correct
                // response is to skip this one failed future and keep running —
                // the GatewayReceiver must stay alive for all subsequent requests.
                // The HTTP handler that submitted this request will eventually
                // time out waiting on its CompletableFuture and return an error
                // to the client, which is acceptable.
                //
                // Previously this was `break`, which permanently killed the
                // GatewayReceiver loop on the very first TCP hiccup, making the
                // gateway deaf to all future responses.
                logger.warning("ExecutionException in GatewayReceiver — skipping failed future and continuing: " + e.getCause());
                continue;
            }

            String responseStr = new String(response);
            logger.fine("Response is: ["+responseStr+"]\n");
            String errorMsg = "[requeue this work:]";
            //String validResponseMsg =
            if(responseStr.startsWith(errorMsg)){
                logger.fine("Response contains an error\n");
                responseStr = responseStr.substring(errorMsg.length()); // remove the error message so code can be re-queued
                //responseStr = responseStr.substring(responseStr.indexOf("]")+1); // remove leader header

                /*if(responseStr.startsWith("{requestID: #")){ // remove requestID header
                    responseStr = responseStr.substring(responseStr.indexOf("}")+1);
                }*/

                requestQueue.offer(responseStr.getBytes()); // re-queue
                logger.fine("Re-queued the request\n");
                continue; // dequeue next completed request

                // If this block hit, the GatewaySender added the errorMsg, and never stripped the leader header
                    // But in order to re-queue the code, it must not have the leader header or the requestID header

                // If re-queuing:
                    // Strip leader header && requestID header

            }

            // IF NO ERROR IN RESPONSE:


            // Don't check who the leader is, bc that was checked when response was received in GatewaySender,
                // and the leader header was removed, leaving only the requestID header
/*
            // find out who sent the response
            int leaderSender = Integer.parseInt(responseStr.substring(responseStr.indexOf("#")+1, responseStr.indexOf("]")));

            //remove the leader identification part of response
            String senderStrippedResponseStr = responseStr.substring(responseStr.indexOf("]")+1);*/

            //find out the requestID of response
            logger.fine("----------------------------\nResponseStr is "+responseStr);
            int requestID = 0;
            try {
                String word = "requestID: #";
                int index = responseStr.indexOf(word);
                String result = responseStr.substring(index+word.length(), responseStr.indexOf("}"));
                //requestID = Integer.parseInt(responseStr.substring(responseStr.indexOf("#")+1, responseStr.indexOf("}")));
                requestID = Integer.parseInt(result);
            } catch (NumberFormatException e) {
                String shortened = responseStr.substring(responseStr.indexOf("#")+1, responseStr.indexOf("}"));
                String newStr = shortened.substring(shortened.indexOf("#")+1);
                logger.warning("There was an error because you tried to parse "+shortened+" as an int\n Now parsing "+newStr+"\n");
                requestID = Integer.parseInt(newStr);
            }
            logger.fine("requestID is: "+requestID+"--------------------\n");
            //remove the requestID part of response
            //response = senderStrippedResponseStr.substring(senderStrippedResponseStr.indexOf("}")+1).getBytes();
            String justResponseMsg = responseStr.substring(responseStr.indexOf("}")+1);
            response = justResponseMsg.getBytes();
            logger.fine("Proper response to requestId #"+requestID+" is: "+justResponseMsg+"\n");
            // get the requestID from message
            CompletableFuture<byte[]> cf = requestToFuture.get(requestID); //complete the future based on requestID in response
            cf.complete(response);
            logger.fine("Completed the future");
        }
        this.logger.fine("Exiting GatewayReceiver.run()");
    }

    public void shutdown(){
        interrupt();
    }
}
