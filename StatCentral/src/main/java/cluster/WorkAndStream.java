package cluster;

import java.io.OutputStream;

// Utility class for TCPServer in order to associate a request with the OutputStream of the Gateway's socket
    // This way, WorkerServer can pull this object off the queue, pass the work along to a worker,
        // and send the completed work back to the Gateway via the appropriate OutputStream
public class WorkAndStream {

    byte[] work;
    OutputStream outStream;
    public WorkAndStream(byte[] work, OutputStream stream) {
        this.work = work;
        this.outStream = stream;
    }

    public OutputStream getOutputStream() {
        return outStream;
    }

    public byte[] getWork() {
        return work;
    }

}
