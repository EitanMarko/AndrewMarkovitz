package helperFiles;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UDPMessageReceiver extends Thread implements LoggingServer {
    private static final int MAXLENGTH = 4096;
    private final InetSocketAddress myAddress;
    private final int myPort;
    private LinkedBlockingQueue<Message> incomingMessages;
    private Logger logger;
    private PeerServer peerServer;

    public UDPMessageReceiver(LinkedBlockingQueue<Message> incomingMessages, InetSocketAddress myAddress, int myPort, PeerServer peerServer) throws IOException {
        this.incomingMessages = incomingMessages;
        this.myAddress = myAddress;
        this.myPort = myPort;
        this.logger = initializeLogging(UDPMessageReceiver.class.getCanonicalName() + "-on-port-" + this.myPort);
        this.setDaemon(true);
        this.peerServer = peerServer;
        setName("UDPMessageReceiver-port-" + this.myPort);
    }

    public void shutdown() {
        interrupt();
    }

    @Override
    public void run() {
        //create the socket
        DatagramSocket socket = null;
        try {
            //System.out.println("DEBUG: Creating receiving socket on " + this.myAddress.getPort());
            socket = new DatagramSocket(this.myAddress);
            socket.setSoTimeout(3000);
        }
        catch (Exception e) {
            this.logger.log(Level.SEVERE, "failed to create receiving socket on " + this.myAddress.getPort(), e);
            return;
        }
        //loop
        while (!this.isInterrupted()) {
            try {
                this.logger.fine("Waiting for packet");
                DatagramPacket packet = new DatagramPacket(new byte[MAXLENGTH], MAXLENGTH);
                socket.receive(packet); // Receive packet from a client

                // Trim to the actual received bytes; packet.getData() always returns the
                // full 4096-byte backing buffer regardless of how many bytes arrived.
                byte[] msgBytes = Arrays.copyOf(packet.getData(), packet.getLength());

                // Pre-validate before constructing Message.
                // Every valid Message serialises its type as a big-endian Java char
                // ('E','W','C','G','L'), so the first byte is always 0x00 (high byte
                // of an ASCII char < 256).  Stray UDP packets from WSL2 networking,
                // Docker, mDNS, etc. almost never start with 0x00, so this one-byte
                // check silently discards them and stops BufferUnderflowException spam.
                if (msgBytes.length < 19 || msgBytes[0] != 0x00) {
                    this.logger.fine("Discarding non-Message UDP packet (length=" + msgBytes.length + ")");
                    continue;
                }

                Message received = new Message(msgBytes);
                if (received.getMessageType() == null) {
                    this.logger.fine("Discarding UDP packet with unknown message type");
                    continue;
                }
                InetSocketAddress sender = new InetSocketAddress(received.getSenderHost(), received.getSenderPort());
                //ignore messages from peers marked as dead
                if (this.peerServer != null && this.peerServer.isPeerDead(sender)) {
                    this.logger.fine("UDP packet received from dead peer: " + sender.toString() + "; ignoring it.");
                    continue;
                }
                this.logger.fine("UDP packet received:\n" + received.toString());
                //this is logic required for stage 5...
                if (sendLeader(received)) {
                    Vote leader = this.peerServer.getCurrentLeader();
                    //might've entered election between the two previous lines of code, which would make leader null, hence must test
                    if(leader != null){
                        ElectionNotification notification = new ElectionNotification(leader.getProposedLeaderID(), this.peerServer.getPeerState(), this.peerServer.getServerId(), this.peerServer.getPeerEpoch());
                        byte[] msgContent = LeaderElection.buildMsgContent(notification);
                        sendElectionReply(msgContent, sender);
                    }
                //end stage 5 logic
                }else if(!this.strayElectionMessage(received)){
                    //use interrupt-safe version, i.e. offer
                    boolean done = false;
                    while(!done){
                        done = this.incomingMessages.offer(received);
                    }
                }
            }
            catch (SocketTimeoutException ste) {
            }
            catch (Exception e) {
                if (!this.isInterrupted()) {
                    this.logger.log(Level.WARNING, "Exception caught while trying to receive UDP packet", e);
                }
            }
        }
        //cleanup
        if (socket != null) {
            socket.close();
        }
        this.logger.log(Level.FINE,"Exiting UDPMessageReceiver.run()");
    }

    private void sendElectionReply(byte[] msgContent, InetSocketAddress target) {
        Message msg = new Message(Message.MessageType.ELECTION, msgContent, this.myAddress.getHostString(), this.myPort, target.getHostString(), target.getPort());
        try (DatagramSocket socket = new DatagramSocket()){
            byte[] payload = msg.getNetworkPayload();
            DatagramPacket sendPacket = new DatagramPacket(payload, payload.length, target);
            socket.send(sendPacket);
            this.logger.fine("Election reply sent:\n" + msg.toString());
        }
        catch (IOException e) {
            this.logger.warning("Failed to send election reply:\n" + msg.toString());
        }
    }

    /**
     * see if we got an Election LOOKING message while we are in FOLLOWING or LEADING
     * @param received
     * @return
     */
    private boolean sendLeader(Message received) {
        if (received.getMessageType() != Message.MessageType.ELECTION) {
            return false;
        }
        ElectionNotification receivedNotification = LeaderElection.getNotificationFromMessage(received);
        PeerServer.ServerState receivedState = receivedNotification.getState();
        if ((receivedState == PeerServer.ServerState.LOOKING || receivedState == PeerServer.ServerState.OBSERVER) && (this.peerServer.getPeerState() == PeerServer.ServerState.FOLLOWING || this.peerServer.getPeerState() == PeerServer.ServerState.LEADING)) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * if neither sender nor I am looking, and this is an election message, let it disappear
     * @param received
     * @return
     */
    private boolean strayElectionMessage(Message received) {
        if (received.getMessageType() != Message.MessageType.ELECTION) {
            return false;
        }
        ElectionNotification receivedNotification = LeaderElection.getNotificationFromMessage(received);
        if (receivedNotification.getState() != PeerServer.ServerState.LOOKING && this.peerServer.getCurrentLeader() != null) {
            return true;
        }
        else {
            return false;
        }
    }
}