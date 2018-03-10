package server;

import listener.Listener;
import message.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HeartbeatListener extends Listener {
    private static final Logger LOGGER = Logger.getLogger( HeartbeatListener.class.getName() );

    HeartbeatListener(Protocol protocol) {
        super(protocol);
    }

    @Override
    public void run() {
        int messageSize = super.getProtocol().getMessageSize();
        DatagramPacket heartbeatPacket = new DatagramPacket(new byte[messageSize], messageSize);

        try {
            while (true) {
                super.receivePacket(heartbeatPacket);
                String rawMessage = new String(heartbeatPacket.getData(), 0, heartbeatPacket.getLength());
                super.sendPacket(heartbeatPacket);
                LOGGER.log(Level.INFO, rawMessage);
            }
        } catch (SocketException e) {
            if (shouldThreadStop()) {
                LOGGER.log(Level.FINE, "HeartbeatListener gracefully exiting after being asked to stop.");
            } else {
                LOGGER.log(Level.WARNING, "HeartbeatListener failed to receive incoming message: " + e.toString());
                e.printStackTrace();
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "HeartbeatListener failed to receive incoming message: " + e.toString());
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
