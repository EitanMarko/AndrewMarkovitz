package skeletons;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * GatewayServer
 *
 * Acts as the entry point for all incoming HTTP requests from the LeagueInterface
 * and FanInterface. Forwards requests to the LeaderServer via TCP and returns
 * the results back to the appropriate interface via HTTP.
 */
public class GatewayServerSkeleton {

    /**
     * Receives an incoming HTTP request from either the LeagueInterface or the
     * FanInterface. Parses and validates the structure of the request before
     * forwarding it to the LeaderServer.
     *
     * @param request    The HTTP request sent by a LeagueInterface or FanInterface caller,
     *                   containing the operation type and any associated parameters
     * @return           An acknowledgment that the request has been received and is
     *                   being processed, or an immediate error if the request is malformed
     */
    public CompletableFuture<Void> receiveRequest(HttpRequest request) {return null;}

    /**
     * Asynchronously forwards a parsed request to the LeaderServer via TCP
     * for further processing and routing to the appropriate WorkerServer.
     *
     * @param request    The parsed request object to be forwarded to the LeaderServer
     * @return           The response retrieved from the LeaderServer after the request
     *                   has been processed, to be passed back up to the caller
     */
    public CompletableFuture<String> forwardToLeader(RequestSkeleton request) {return null;}

    /**
     * Asynchronously returns the result of a fully processed request back to the
     * originating LeagueInterface or FanInterface caller via HTTP.
     *
     * @param result      The result string retrieved from the LeaderServer,
     *                    either a success payload or an error message
     * @param response    The HTTP response object used to send the result back
     *                    to the original caller
     * @return            A future that completes when the HTTP response has been sent
     */
    public CompletableFuture<Void> returnResult(String result, HttpResponse response) {return null;}
}
