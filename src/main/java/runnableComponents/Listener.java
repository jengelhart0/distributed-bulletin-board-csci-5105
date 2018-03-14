package runnableComponents;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public abstract class Listener extends Scheduler {

    private DatagramSocket listenSocket = null;

    protected Listener() {
        super();
    }

    public abstract void forceCloseSocket();

    public void listenAt(int listenPort, InetAddress localAddress) throws SocketException {
        this.listenSocket = new DatagramSocket(listenPort);
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
