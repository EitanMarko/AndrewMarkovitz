package cluster;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GatewayConfig
 *
 * Holds every parameter needed to construct a new GatewayServer, plus a
 * volatile reference to the currently running instance.
 *
 * Pass this to LeagueInterfaceImpl / FanInterfaceImpl at construction time to
 * enable automatic GatewayServer recovery: when the interface detects that the
 * gateway is unreachable it shuts down the dead instance (if it is still
 * registered here), spins up a fresh one from these parameters, waits for the
 * new gateway to complete leader election, and then retries the failed request.
 *
 * Thread safety:
 *   activeGateway is volatile so the interface can update it from whatever
 *   thread wins the recovery race without needing an explicit lock on this
 *   object.
 */
public class GatewayConfig {

    // ── GatewayServer constructor parameters ──────────────────────────────────

    /** HTTP port the GatewayServer listens on for client requests. */
    public final int httpPort;

    /** UDP port the embedded GatewayPeerServerImpl uses for gossip / election. */
    public final int peerPort;

    /** Peer epoch passed to GatewayPeerServerImpl at construction. */
    public final long peerEpoch;

    /** Stable server ID assigned to the gateway node in the cluster. */
    public final long serverID;

    /**
     * Peer-ID-to-address map for the cluster, with the gateway's own entry
     * already removed (PeerServerImpl removes it in its constructor anyway,
     * but doing it here keeps the map stable across restarts).
     */
    public final ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress;

    /** Number of observer nodes in the cluster (1 for the gateway itself). */
    public final int numberOfObservers;

    // ── Mutable runtime state ──────────────────────────────────────────────────

    /**
     * The currently running GatewayServer instance.
     *
     * Set to the initial gateway at construction time.  The interface replaces
     * this reference each time it performs a restart so that subsequent calls
     * can shut down the replacement if needed.
     */
    public volatile GatewayServer activeGateway;

    // ── Constructor ────────────────────────────────────────────────────────────

    /**
     * @param httpPort         HTTP port for client-facing requests
     * @param peerPort         UDP port for the GatewayPeerServerImpl
     * @param peerEpoch        Initial epoch for the peer server
     * @param serverID         Stable cluster ID for the gateway node
     * @param peerIDtoAddress  Peer map (gateway's own ID already removed)
     * @param numberOfObservers Number of observer nodes (typically 1)
     * @param initialGateway   The GatewayServer instance that was just started;
     *                         may be {@code null} if the caller will set it later
     */
    public GatewayConfig(int httpPort, int peerPort, long peerEpoch, long serverID,
                         ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress,
                         int numberOfObservers, GatewayServer initialGateway) {
        this.httpPort          = httpPort;
        this.peerPort          = peerPort;
        this.peerEpoch         = peerEpoch;
        this.serverID          = serverID;
        this.peerIDtoAddress   = peerIDtoAddress;
        this.numberOfObservers = numberOfObservers;
        this.activeGateway     = initialGateway;
    }
}