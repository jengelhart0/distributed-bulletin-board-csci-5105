package server;

import runnableComponents.UdpListener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HeartbeatUdpListener extends UdpListener {
    private static final Logger LOGGER = Logger.getLogger( HeartbeatUdpListener.class.getName() );

    private int messageSize;

//    private Thread heartbeatThread;

    HeartbeatUdpListener(int messageSize) {
        super();
        this.messageSize = messageSize;
    }

//    boolean isAlive() {
//        return this.heartbeatThread.isAlive();
//    }
//
//    void setThread(Thread thread) {
//        this.heartbeatThread = thread;
//    }

    @Override
    public void run() {
        DatagramPacket heartbeatPacket = new DatagramPacket(new byte[messageSize], messageSize);

        try {
            while (shouldThreadContinue()) {
                super.receivePacket(heartbeatPacket);
                String rawMessage = new String(heartbeatPacket.getData(), 0, heartbeatPacket.getLength());
                super.sendPacket(heartbeatPacket);
//                LOGGER.log(Level.INFO, rawMessage);
            }
        } catch (SocketException e) {
            if (!shouldThreadContinue()) {
                LOGGER.log(Level.FINE, "HeartbeatUdpListener gracefully exiting after being asked to stop.");
            } else {
                LOGGER.log(Level.WARNING, "HeartbeatUdpListener failed to receive incoming message: " + e.toString());
                e.printStackTrace();
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "HeartbeatUdpListener failed to receive incoming message: " + e.toString());
            e.printStackTrace();
        } finally {
            closeListenSocket();
        }
    }

    @Override
    public void forceCloseSocket() {
        closeListenSocket();
    }

}
