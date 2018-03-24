package client;

import runnableComponents.TcpListener;
import message.Message;
import message.Protocol;

import java.io.IOException;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientListener extends TcpListener {
    private static final Logger LOGGER = Logger.getLogger(ClientListener.class.getName());

    private FeedManager feedManager;

    private Protocol protocol;

    private final Lock idReceivedByListenerLock;
    private final Condition idHasBeenSet;
    private String receivedClientId;

    ClientListener(Protocol protocol, Lock idReceivedByListenerLock, Condition idHasBeenSet,
                   Lock pendingQueryLock, Condition matchesForPendingQueryReceived) {
        super();
        this.protocol = protocol;
        this.receivedClientId = null;
        this.idReceivedByListenerLock = idReceivedByListenerLock;
        this.idHasBeenSet = idHasBeenSet;

        this.feedManager = new FeedManager(
                protocol, new QueryMatcher(protocol, pendingQueryLock, matchesForPendingQueryReceived));
    }

    String getReceivedClientId() {
        // lock for this is obtained by Client in condition wait in ensureThisHasId()
        return receivedClientId;
    }

    @Override
    public void forceCloseSocket() {
        try {
            this.listenSocket.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to close listen socket in TcpListener:");
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        int messageSize = protocol.getMessageSize();
        try {
            while (shouldThreadContinue()) {
                listenForRemoteMessage();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "ClientListener failed to receive message or initializeMessageSocketIfNeeded" +
                    ": ");
            e.printStackTrace();
        } finally {
//            System.out.println("ClientListener in finally block");
            forceCloseSocket();
        }
    }

    private void listenForRemoteMessage() throws IOException {
        try {
            initializeMessageSocketIfNeeded();
//                System.out.println("Client about to wait for message;");
//                System.out.println("Waiting for message in client " + receivedClientId + ", message socket = " + messageSocket);
            Message newMessage = getMessageFromRemote();
//                System.out.println("Got message in client " + receivedClientId + ": " + newMessage.asRawMessage());
//                System.out.println("Client received message" + newMessage.asRawMessage());
//                System.out.println("\tChecking if received message is client id message  " + newMessage.asRawMessage());
            String possibleClientId = newMessage.extractIdIfThisIsIdMessage();
            if (!possibleClientId.isEmpty()) {
                setReceivedIdAndSignalClient(possibleClientId);
            } else if (!feedManager.handleRetrieveNotificationIfThisIsOne(newMessage)) {
                feedManager.handle(newMessage);
            }
        } catch (SocketException e) {
//            LOGGER.log(Level.WARNING, "ClientListener didn't receive incoming message (could be error or could" +
//                    "be result of closing socket to switch servers: " + e.toString());
        }
    }

    private Message getMessageFromRemote() throws IOException {
        String rawMessage = super.receiveMessage();
//        String rawMessage = new String(packetToReceive.getData(), 0, packetToReceive.getLength());
        return new Message(protocol, rawMessage, false);
    }

    private void setReceivedIdAndSignalClient(String clientId) {
//        System.out.println("Trying to grab client id lock for new client id " + clientId);
        idReceivedByListenerLock.lock();
        try {
            this.receivedClientId = clientId;
            idHasBeenSet.signalAll();
        } finally {
            idReceivedByListenerLock.unlock();
        }
    }

    List<Message> consumeCurrentMessageFeed() {
        return feedManager.consumeCurrentMessageFeed();
    }

    void addPendingQueryToMatch(String query) {
        feedManager.addPendingQueryToMatch(query);
    }

    List<Message> consumeMatchesIfAllReceivedFor(String query) {
        return feedManager.consumeMatchesIfAllReceivedFor(query);
    }
}

