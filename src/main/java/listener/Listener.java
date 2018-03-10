package listener;

import message.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public abstract class Listener implements Runnable {

    private Protocol protocol;
    private DatagramSocket listenSocket = null;

    private boolean shouldThreadStop;
    private final Object stopLock = new Object();

    protected Listener(Protocol protocol) {
        this.protocol = protocol;
        this.shouldThreadStop = false;
    }

    @Override
    public abstract void run();

    public abstract void forceCloseSocket();

    public void listenAt(int listenPort, InetAddress localAddress) throws SocketException {
        this.listenSocket = new DatagramSocket(listenPort);
    }

    public void tellThreadToStop() {
        synchronized (this.stopLock) {
            this.shouldThreadStop = true;
        }
    }

    protected Protocol getProtocol() {
        return this.protocol;
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

    protected boolean shouldThreadStop() {
        synchronized (this.stopLock) {
            return this.shouldThreadStop;
        }
    }
}
