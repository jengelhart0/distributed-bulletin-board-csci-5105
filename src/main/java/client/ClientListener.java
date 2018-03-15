package client;

import runnableComponents.Listener;
import message.Message;
import message.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientListener extends Listener {
    private static final Logger LOGGER = Logger.getLogger( ClientListener.class.getName() );

    private Protocol protocol;
    private List<Message> messageFeed;
    private String receivedClientId;
    private Lock idReceivedByListenerLock;
    private Condition idHasBeenSet;


    ClientListener(Protocol protocol, Lock idReceivedByListenerLock, Condition idHasBeenSet) {
        super();
        this.protocol = protocol;
        this.messageFeed = Collections.synchronizedList(new LinkedList<>());
        this.receivedClientId = null;
        this.idReceivedByListenerLock = idReceivedByListenerLock;
        this.idHasBeenSet = idHasBeenSet;
    }

    List<Message> getCurrentMessageFeed() {
        List<Message> feedCopy = Collections.synchronizedList(new LinkedList<>());
        feedCopy.addAll(this.messageFeed);
        this.messageFeed.clear();
        return feedCopy;
    }

    String getReceivedClientId() {
        // lock for this is obtained by Client in condition wait in ensureThisHasId()
        return receivedClientId;
    }

    @Override
    public void forceCloseSocket() {
        closeListenSocket();
    }

    @Override
    public void run() {
        int messageSize = protocol.getMessageSize();

        DatagramPacket packetToReceive = new DatagramPacket(new byte[messageSize], messageSize);
        try {
            while(shouldThreadContinue()) {
//                System.out.println("Client about to wait for message;");
                Message newMessage = getMessageFromRemote(packetToReceive);
//                System.out.println("Client received message" + newMessage.asRawMessage());
//                System.out.println("\tChecking if received message is client id message  " + newMessage.asRawMessage());
                String possibleClientId = newMessage.extractIdIfThisIsIdMessage();
                if(!possibleClientId.isEmpty()) {
                    setReceivedIdAndSignalClient(possibleClientId);

                } else {
                    this.messageFeed.add(newMessage);
                }
            }
        } catch (SocketException e) {
            if (!shouldThreadContinue()) {
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

    private Message getMessageFromRemote(DatagramPacket packetToReceive) throws IOException {
        super.receivePacket(packetToReceive);
        String rawMessage = new String(packetToReceive.getData(), 0, packetToReceive.getLength());
        return new Message(protocol, rawMessage, false);
    }

    private void setReceivedIdAndSignalClient(String clientId) {
//        System.out.println("Trying to grab client id lock for new client id " + clientId);
        idReceivedByListenerLock.lock();
        try {
            this.receivedClientId = clientId;
            idHasBeenSet.signal();
        } finally {
            idReceivedByListenerLock.unlock();
        }
    }
}

