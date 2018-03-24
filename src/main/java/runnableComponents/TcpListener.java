package runnableComponents;


import java.io.BufferedReader;
import java.io.IOException;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class TcpListener extends Scheduler {
    private static final Logger LOGGER = Logger.getLogger( TcpListener.class.getName() );
    protected ServerSocket listenSocket = null;
    protected Socket messageSocket = null;
    protected BufferedReader messageIn;

    protected TcpListener() {
        super();
    }

    public void listenAt(int listenPort, InetAddress localAddress) throws IOException, SocketException {
        this.listenSocket = new ServerSocket(listenPort);
    }

    public abstract void forceCloseSockets() throws IOException;

    protected String receiveMessage() throws IOException {
        return messageIn.readLine();
    }

    protected void closeSockets() {
        try {
            this.messageSocket.close();
            this.listenSocket.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to close sockets in TcpListener:");
            e.printStackTrace();
        }
    }
}
