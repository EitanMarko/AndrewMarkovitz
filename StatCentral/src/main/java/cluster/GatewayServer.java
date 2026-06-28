package cluster;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import helperFiles.LoggingServer;
import helperFiles.Util;
import helperFiles.Vote;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;


public class GatewayServer extends Thread implements LoggingServer{
    private GatewayPeerServerImpl gatewayPeerServer;
    private HttpServer httpServer;
    private ConcurrentHashMap<Integer,byte[]> cache;
    private Logger logger;
    private AtomicInteger atomicInt;
    private ConcurrentHashMap<Integer, CompletableFuture<byte[]>> requestToFuture;
    private LinkedBlockingQueue<byte[]> requestQueue;
    private GatewaySender gatewaySender;
    private GatewayReceiver gatewayReceiver;
    private CompletionService<byte[]> completionService;


    public GatewayServer(int httpPort, int peerPort, long peerEpoch, Long serverID, ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress, int numberOfObservers) throws IOException{
        gatewayPeerServer = new GatewayPeerServerImpl(peerPort, peerEpoch, serverID, peerIDtoAddress, serverID, numberOfObservers);
        this.httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        this.cache = new ConcurrentHashMap<>();
        this.logger = initializeLogging(GatewayServer.class.getCanonicalName());
        this.atomicInt = new AtomicInteger(0);

        //STAGE 5 - for GatewayShliach to handle
        this.requestQueue = new LinkedBlockingQueue<>();
        this.requestToFuture = new ConcurrentHashMap<>();

        // Pass the completionService on to the gatewayReceiver to process completed requests
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            //t.setName("daemon-worker-" + t.getId());
            return t;
        });
        this.completionService = new ExecutorCompletionService<>(executorService);

        this.gatewaySender = new GatewaySender(gatewayPeerServer, requestToFuture, requestQueue, completionService);
        this.gatewayReceiver = new GatewayReceiver(gatewayPeerServer, requestToFuture, requestQueue, completionService);


        httpServer.createContext("/compileandrun", exchange -> {
            new MyHandler().handle(exchange);
                }); // new handler for every "exchange" (request) received from client

        // Handler for StatCentral requests
        httpServer.createContext("/request", exchange -> {
            new MyHandler().handle(exchange);
        }); // new handler for every "exchange" (request) received from client


        // If this doesn't work, use Util.startAsDaemon() (only downside - doesn't name threads)
        Executor executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                r -> { // Thread factory for thread pool
                    Thread thread = new Thread(r);
                    thread.setDaemon(true); // daemon threads
                    thread.setName("Gateway-Thread-"+thread.threadId()); // Name threads for logging
                    return thread;
                });
        //---------------------------------------------------------------------------------------------


        // Status endpoint - checks if leader exists and returns node roles
        httpServer.createContext("/status", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.getResponseBody().close();
                return;
            }

            Vote leader = gatewayPeerServer.getCurrentLeader();
            StringBuilder response = new StringBuilder();

            if (leader == null) {
                response.append("NO_LEADER");
            } else {
                response.append("LEADER:").append(leader.getProposedLeaderID()).append("\n");

                // Add all peer information
                Map<Long, InetSocketAddress> peers = gatewayPeerServer.getPeerIDtoAddress();
                for (Map.Entry<Long, InetSocketAddress> entry : peers.entrySet()) {
                    Long peerId = entry.getKey();
                    if (gatewayPeerServer.isPeerDead(peerId)) {
                        continue; // Skip dead peers
                    }

                    String role;
                    if (peerId.equals(leader.getProposedLeaderID())) {
                        role = "LEADER";
                    } else if (peerId.equals(gatewayPeerServer.getGatewayID())) {
                        role = "OBSERVER";
                    } else {
                        role = "FOLLOWER";
                    }
                    response.append(peerId).append(":").append(role).append("\n");
                }

                // Add gateway itself
                response.append(gatewayPeerServer.getServerId()).append(":OBSERVER\n");
            }

            byte[] responseBytes = response.toString().getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        });

        //----------------------------------------------------------------------------------------------
        httpServer.setExecutor(executor);
        httpServer.start();
    }

    public GatewayPeerServerImpl getPeerServer(){
        return gatewayPeerServer;
    }

    public HttpServer getHttpServer() {
        return httpServer;
    }

    public void shutdown(){
        gatewayPeerServer.shutdown();
        gatewaySender.shutdown();
        gatewayReceiver.shutdown();
        if (httpServer != null) {
            httpServer.stop(0);
        }
        // shutdown() CompletionService's executor? I don't think it's necesssary bc:
            // We used an executor in the past and never shut it down
                // this works bc all threads in the executor are daemon threads

    }

    @Override
    public void run() {
        gatewayPeerServer.start();
        // daemon???

        this.gatewaySender.setDaemon(true);
        this.gatewaySender.start();

        this.gatewayReceiver.setDaemon(true);
        this.gatewayReceiver.start();
    }

    private class MyHandler implements HttpHandler, LoggingServer {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // Check for bad request or content type
            if (badRequestType(exchange)){
                return;
            }
            int requestNum = atomicInt.incrementAndGet();

            //STAGE 5 - Include request number in request when sent off to leader
            String requestNumStr = "["+ requestNum +"]";
            byte[] requestNumBytes = requestNumStr.getBytes();
            byte[] bytesFromNetwork = exchange.getRequestBody().readAllBytes();

            byte[] codeBytes = new byte[requestNumBytes.length + bytesFromNetwork.length];
            System.arraycopy(requestNumBytes, 0, codeBytes, 0, requestNumBytes.length);
            System.arraycopy(bytesFromNetwork, 0, codeBytes, requestNumBytes.length, bytesFromNetwork.length);

            //int hash = Arrays.hashCode(codeBytes);
            int hash = Arrays.hashCode(bytesFromNetwork); // I only want to cache the code component of this, because if the same code comes thru again, it'll have a different request number

            if(cache.get(hash) != null){ // Cache hit
                logger.fine("Cache HIT at time [Request #"+requestNum+"]\n");
                getFromCache(exchange, hash); // Write back to client from cache
                return;
            }
            else{ // Cache miss
                logger.fine("Cache MISS [Request #"+requestNum+"]\n");
            }


            // At this point, a cache miss, we send the job of communicating with the leader to the GatewaySender
                // We will give it the code (String)


            byte[] response = new byte[0];


            CompletableFuture<byte[]> completableFuture = new CompletableFuture<>();
            requestToFuture.put(requestNum, completableFuture);
            String codeStr = new String(codeBytes); // for testing
            requestQueue.offer(codeBytes);
            logger.fine("Just offer()ed code on the queue (to GatewaySender): "+codeStr+"\n");
            try {
                response = completableFuture.get(); // set response
            } catch (InterruptedException e) {
                logger.warning("InterruptedException when blocking on completableFuture.get() in Gateway thread: "+MyHandler.class.getCanonicalName());
                return;
            } catch (ExecutionException e) {
                logger.warning("ExecutionException when blocking on completableFuture.get() in Gateway thread: "+MyHandler.class.getCanonicalName());
                return;
            }
            String responseStr = new String(response); // for testing
            logger.fine("Received response from code: ["+responseStr+"]\n");
            boolean exception = false;
            byte[] sendExceptionResponse = {};
            String exceptionIndicator = "ExCePtIoN4o0";
            if(responseStr.startsWith(exceptionIndicator)){
                exception = true;
                sendExceptionResponse = exceptionResponse(response);
            }

            cache.put(hash, response); // cache the response with the exception indicator
            logger.fine("Cache PUT at time [Request #"+requestNum+"]\n");

            // Send response
            exchange.getResponseHeaders().add("Cached-Response", String.valueOf(false));

            if(exception){
                exchange.sendResponseHeaders(400, sendExceptionResponse.length);
                OutputStream responseBody = exchange.getResponseBody();
                responseBody.write(sendExceptionResponse);
                responseBody.close();
            }
            else{
                exchange.sendResponseHeaders(200, response.length);
                OutputStream responseBody = exchange.getResponseBody();
                responseBody.write(response);
                responseBody.close();
            }

            //requestIDtoWork.remove(requestNum); // response has been received and sent off, so now it's no longer queued
        }

        private boolean badRequestType(HttpExchange exchange) throws IOException {
            if(!"POST".equalsIgnoreCase(exchange.getRequestMethod())){ // NOT a POST request: send response code 405
                exchange.getResponseHeaders().add("Cached-Response", String.valueOf(false));
                exchange.sendResponseHeaders(405, -1); // Only send code, but no response body
                exchange.getResponseBody().close();
                return true;
            }
            return false;
        }

        private void getFromCache(HttpExchange exchange, int hash) throws IOException {
            byte[] response = cache.get(hash);

            boolean exception = false;
            String exceptionIndicator = "ExCePtIoN4o0";
            String responseStr = new String(response);
            if(responseStr.startsWith(exceptionIndicator)){
                exception = true;
                response = exceptionResponse(response);
            }

            exchange.getResponseHeaders().add("Cached-Response", String.valueOf(true));
            if(exception){
                exchange.sendResponseHeaders(400, response.length);
            }
            else{
                exchange.sendResponseHeaders(200, response.length);
            }
            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(response);
            responseBody.close();
        }

        private byte[] exceptionResponse(byte[] exceptionResponse){
            String exceptionIndicator = "ExCePtIoN4o0";
            byte[] response = new byte[exceptionResponse.length - exceptionIndicator.length()];
            for(int i = 0; i <  response.length; i++){
                response[i] = exceptionResponse[i+exceptionIndicator.length()];
            }
            String responseStr = new String(response); // for testing
            return response;
        }
    }

}