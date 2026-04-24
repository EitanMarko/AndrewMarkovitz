package cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

public class GatewayPeerServerImpl extends PeerServerImpl{
    public GatewayPeerServerImpl(int udpPort, long peerEpoch, Long serverID, Map<Long, InetSocketAddress> peerIDtoAddress, Long gatewayID, int numberOfObservers) throws IOException {
        super(udpPort, peerEpoch, serverID, peerIDtoAddress, gatewayID, numberOfObservers);
    }

    @Override
    public void setPeerState(ServerState newState) {
        return; // method does nothing - prevents state change to GatewayPeerServerImpl
    }
}
