package cluster;

import helperFiles.LoggingServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;


public class TCPServer extends Thread implements LoggingServer {
    private ServerSocket serverSocket;
    private final LinkedBlockingQueue<WorkAndStream> workFromClientQueue;
    private Logger logger;
    private PeerServerImpl server;


    public TCPServer(PeerServerImpl server, LinkedBlockingQueue<WorkAndStream> workFromClientQueue) throws IOException {
        this.workFromClientQueue = workFromClientQueue;
        this.serverSocket = new ServerSocket(server.getTcpPort());
        this.server = server;
        this.logger = initializeLogging(TCPServer.class.getCanonicalName() + "-on-server_"+server.getServerId()+"-on-tcpPort-"+server.getTcpPort());
    }

    @Override
    public void run() {
        this.logger.fine("Starting run() on TCPServer\n");
        boolean firstReq = true;
        while(!isInterrupted()){
            // Accept connections, read stuff, and add to leader's queue (for leader to allocate)

            try {
                Socket gatewaySocket = serverSocket.accept();
                if (firstReq) {
                    this.logger.fine("First connection accepted on TCPServer.run()\n");
                    int sleep = Math.max(10000, server.getNonObservers().size()*1000);// Make higher?
                    serverSocket.setSoTimeout(sleep);
                    firstReq = false;
                }
                InputStream in = gatewaySocket.getInputStream();
                byte[] lenBytes = in.readNBytes(4);
                int len = ByteBuffer.wrap(lenBytes).getInt(); // See how many bytes to read from inputstream - int value (4 bytes)
                logger.fine("Received work to parse for leader\n");

                byte[] request = in.readNBytes(len); // Now read that many bytes (code)
                String reqStr = new String(request);

                WorkAndStream workAndStream = new WorkAndStream(request, gatewaySocket.getOutputStream());
                workFromClientQueue.offer(workAndStream);
                logger.fine("Parsed work and sent back to leader\n");

            } catch (IOException e) {
                logger.fine("Before, would've exited. Now continuing until shutdown");
                continue;
            }
        }
        logger.fine("Exiting TCPServer.run() on server #"+server.getServerId());
    }

    public void shutdown(){
        try {
            serverSocket.close();
            logger.fine("TCPServer closed ServerSocket on leader server #"+server.getGatewayID()+" at time ["+System.nanoTime()+"]\n");
        } catch (IOException e) {
        }
        interrupt();

    }



}