package client;

import listener.Listener;
import message.Message;
import message.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientListener extends Listener {
    private static final Logger LOGGER = Logger.getLogger( ClientListener.class.getName() );

    private Protocol protocol;
    private List<Message> messageFeed;

    ClientListener(Protocol protocol) {
        super();
        this.protocol = protocol;
        this.messageFeed = Collections.synchronizedList(new LinkedList<>());
    }

    List<Message> getCurrentMessageFeed() {
        List<Message> feedCopy = Collections.synchronizedList(new LinkedList<>());
        feedCopy.addAll(this.messageFeed);
        this.messageFeed.clear();
        return feedCopy;
    }


    @Override
    public void forceCloseSocket() {
        closeListenSocket();
    }

    @Override
    public void run() {
        int messageSize = protocol.getMessageSize();

        byte[] messageBuffer = new byte[messageSize];
        DatagramPacket packetToReceive = new DatagramPacket(new byte[messageSize], messageSize);
        try {
            while(true) {
                Message newMessage = getMessageFromRemote(messageBuffer, packetToReceive);
                this.messageFeed.add(newMessage);
            }
        } catch (SocketException e) {
            if (shouldThreadStop()) {
                LOGGER.log(Level.FINE, "ClientListener gracefully exiting after being asked to stop.");
            } else {
                LOGGER.log(Level.WARNING, "ClientListener failed to receive incoming message: " + e.toString());
                e.printStackTrace();
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "ClientListener failed to receive incoming message: " + e.toString());
            e.printStackTrace();
        }
        finally {
            closeListenSocket();
        }
    }

    private Message getMessageFromRemote(byte[] messageBuffer, DatagramPacket packetToReceive) throws IOException {
        super.receivePacket(packetToReceive);
        String rawMessage = new String(packetToReceive.getData(), 0, packetToReceive.getLength());
        return new Message(protocol, rawMessage, false);
    }
}
