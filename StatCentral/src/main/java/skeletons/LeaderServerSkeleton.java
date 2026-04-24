package skeletons;

import java.util.concurrent.CompletableFuture;

/**
 * LeaderServer
 *
 * The central routing node of the StatCentral distributed system. Receives requests
 * from the GatewayServer, hashes them to determine which WorkerServer should handle
 * them, forwards the request via TCP, and returns the result back to the GatewayServer.
 */
public class LeaderServerSkeleton {

    /**
     * Receives an incoming request forwarded from the GatewayServer via TCP.
     * Initiates the hashing and routing process for the request.
     *
     * @param request    The request object forwarded from the GatewayServer,
     *                   containing the operation type and associated parameters
     * @return           A future that completes when the request has been received
     *                   and handed off for hashing and routing
     */
    public CompletableFuture<Void> receiveRequest(RequestSkeleton request) {return null;}

    /**
     * Computes a hash value for a given request. The hash result is used to
     * determine which WorkerServer the request should be routed to.
     *
     * @param request    The request object to be hashed
     * @return           An integer hash value derived from the request,
     *                   used to select the appropriate WorkerServer
     */
    public int hashRequest(RequestSkeleton request) {return 0;}

    /**
     * Hashes the incoming request and asynchronously forwards it to the appropriate
     * WorkerServer via TCP based on the result of the hash function.
     *
     * @param request    The request object to be hashed and routed to a WorkerServer
     * @return           The response retrieved from the WorkerServer after the request
     *                   has been processed, to be passed back up to the GatewayServer
     */
    public CompletableFuture<String> routeToWorker(RequestSkeleton request) {return null;}

    /**
     * Asynchronously returns the result of a processed request back to the
     * GatewayServer via TCP.
     *
     * @param result    The result string retrieved from the WorkerServer,
     *                  either a success payload or an error message
     * @return          A future that completes when the result has been sent
     *                  back to the GatewayServer
     */
    public CompletableFuture<Void> returnResult(String result) {return null;}
}
