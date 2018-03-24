package runnableComponents;

import java.io.IOException;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Listener extends Scheduler {
    private static final Logger LOGGER = Logger.getLogger( Listener.class.getName() );

    private DatagramSocket listenSocket = null;

    protected Listener() {
        super();
    }

    public abstract void forceCloseSocket();

    public void listenAt(int listenPort, InetAddress localAddress) throws SocketException {
        this.listenSocket = new DatagramSocket(listenPort);
        this.listenSocket.setReceiveBufferSize(listenSocket.getReceiveBufferSize() * 2);
        System.out.println("Listen socket recv buffer size " + listenSocket.getReceiveBufferSize());

//        while(this.listenSocket == null) {
//            try {
//                this.listenSocket = new DatagramSocket(listenPort);
//            } catch (BindException e) {
//                LOGGER.log(Level.INFO, "Tried to create listen socket at " + listenPort + " but failed. Trying" +
//                        "again after incrementing.");
//                listenPort++;
//            }
//

        }

    protected void receivePacket(DatagramPacket packet) throws IOException {
        this.listenSocket.receive(packet);
    }

    protected void sendPacket(DatagramPacket packet) throws IOException {
        this.listenSocket.send(packet);
    }

    protected void closeListenSocket() {
        this.listenSocket.close();
    }

}
