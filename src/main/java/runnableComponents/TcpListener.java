package runnableComponents;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class TcpListener extends Scheduler {
    private static final Logger LOGGER = Logger.getLogger( TcpListener.class.getName() );
    protected ServerSocket listenSocket = null;
    protected Socket messageSocket = null;
    private final Object socketLock = new Object();
    protected BufferedReader messageIn;

    protected TcpListener() {
        super();
    }

    public void listenAt(int listenPort, InetAddress localAddress) throws IOException, SocketException {
        this.listenSocket = new ServerSocket(listenPort);
    }

    public void initializeMessageSocketIfNeeded() throws IOException {
        synchronized (socketLock) {
            if (messageSocket == null) {
                System.out.println("Waiting for socket request from remote");
                messageSocket = listenSocket.accept();
                System.out.println("Accepted new socket request from remote in initializeMessageSocketIfNeeded");
                messageSocket.setKeepAlive(true);
                messageSocket.setReceiveBufferSize(messageSocket.getReceiveBufferSize() * 2);
                messageIn = new BufferedReader(
                        new InputStreamReader(messageSocket.getInputStream()));

            }
        }
    }

    public void resetMessageSocket() throws IOException {
        // Socket closed by ClientManager in server
        synchronized (socketLock) {
            messageSocket.close();
            messageSocket = null;
            messageIn = null;
        }
    }

    public abstract void forceCloseSockets() throws IOException;

    protected String receiveMessage() throws IOException {
        return messageIn.readLine();
    }

    protected void closeSockets() {
        try {
            this.listenSocket.close();
            this.messageIn.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to close sockets in TcpListener:");
            e.printStackTrace();
        }
    }
}
