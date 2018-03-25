package server;

import message.Message;
import message.Protocol;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientManager implements CommunicationManager {
    private static final Logger LOGGER = Logger.getLogger( ClientManager.class.getName() );

    private String clientIp;
    private int clientPort;
    private String clientId;

    private Socket clientMessageSocket;
    private BufferedWriter writer;

    private boolean clientLeft;

    private Protocol protocol;

    private List<Message> subscriptions;
    private List<Message> publications;

    private final Object subscriptionLock = new Object();
    private final Object publicationLock = new Object();

    ClientManager(String clientIp, int clientPort, Protocol protocol) throws IOException {
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.clientId = null;

        this.clientLeft = false;

        this.protocol = protocol;

        this.subscriptions = new LinkedList<>();
        this.publications = new ArrayList<>();

        initializeClientMessageSocket();
    }

    private void initializeClientMessageSocket() throws IOException {
        clientMessageSocket = new Socket(clientIp, clientPort);
        clientMessageSocket.setReceiveBufferSize(clientMessageSocket.getReceiveBufferSize() * 2);
        clientMessageSocket.setKeepAlive(true);
        writer = new BufferedWriter(
                new OutputStreamWriter(
                        clientMessageSocket.getOutputStream()));
    }

    public Runnable task(Message message, MessageStore store, Call call) {
        switch(call) {
            case RETURN_CLIENT_ID_TO_CLIENT:
                return () -> deliverControlMessage(message);
            case SUBSCRIBE:
                return () -> subscribe(message);
            case PUBLISH:
                return () -> publish(message, store);
            case UNSUBSCRIBE:
                return () -> unsubscribe(message);
            case RETRIEVE:
                return() -> retrieve(message, store);
            case PULL_MATCHES:
                return () -> pullSubscriptionMatchesFromStore(store);
            default:
                throw new IllegalArgumentException("Task call made to ClientManager not recognized.");
        }
    }
    @Override
    public void deliverControlMessage(Message controlMessage) {
//        System.out.println("Delivering control message to " + clientIp + ", " + clientPort);
        deliverPublication(controlMessage.asRawMessage(), protocol.getMessageSize());
    }

//    private String extractClientId(Message clientIdMessage) {
//        String[] pieces = clientIdMessage
//                .asRawMessage()
//                .split(protocol.getDelimiter());
//        if (pieces.length < 2) {
//            throw new IllegalArgumentException("Invalid clientIdMessage in extractClientId()");
//        }
//        return pieces[1];
//    }

    @Override
    public void subscribe(Message message) {
        synchronized (subscriptionLock) {
            this.subscriptions.add(message);
        }
    }

    @Override
    public void unsubscribe(Message unsubscription) {
        String unsubscriptionString = unsubscription.asRawMessage();

        synchronized (subscriptionLock) {
            List<Message> afterUnsubscribe = subscriptions
                    .stream()
                    .filter(subscription -> !subscription.asRawMessage().equals(unsubscriptionString))
                    .collect(Collectors.toCollection(LinkedList::new));

            this.subscriptions = Collections.synchronizedList(afterUnsubscribe);
        }
    }

    @Override
    public void retrieve(Message queryMessage, MessageStore store) {
        Set<String> toDeliver = getSingleQueryMatches(queryMessage, store);
        int numRetrieved = toDeliver.size();
        System.out.println("Delivering to client " + clientId + " " + toDeliver.toString());
        String retrieveNotification = protocol.buildRetrieveNotification(queryMessage.asRawMessage(), numRetrieved);
        deliverControlMessage(new Message(protocol, retrieveNotification, true));
        deliverPublications(toDeliver, protocol.getMessageSize());
    }

    @Override
    public void publish(Message message, MessageStore store) {
//        Removed, as this was put in consistency policy instead
//        message.ensureInternalsExistAndRegenerateQuery(clientId);
        synchronized (publicationLock) {
            this.publications.add(message);
        }
        store.publish(message);
    }

    private Set<String> getSingleQueryMatches(Message queryMessage, MessageStore store) {
        Message[] singleQuery = new Message[]{queryMessage};
        return getSubscriptionMatches(singleQuery, store);
    }

    @Override
    public void pullSubscriptionMatchesFromStore(MessageStore store) {
        if(!clientLeft) {
            // Get all subscriptions into cheap container so we get out of synchronized block fast
            // (as store.retrieve(...) is relatively intensive).
            Message[] subscriptionsToMatch;
            synchronized (subscriptionLock) {
                subscriptionsToMatch = subscriptions.toArray(new Message[subscriptions.size()]);
            }

            Set<String> toDeliver = getSubscriptionMatches(subscriptionsToMatch, store);
            deliverPublications(toDeliver, protocol.getMessageSize());
        }
    }

    @Override
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public String getClientId() {
        if(clientId == null) {
            throw new IllegalArgumentException("Tried to get client ID from manager but it was null");
        }
        return clientId;
    }

    private Set<String> getSubscriptionMatches(Message[] subscriptionsToMatch, MessageStore store) {
        Set<String> toDeliver = new HashSet<>();
        for(Message subscription: subscriptionsToMatch) {
            toDeliver.addAll(store.retrieve(subscription, ));
        }
        return toDeliver;
    }

    private void deliverPublications(Set<String> publicationsToDeliver, int messageSize) {
        String paddedPublication;
        try {
            if(!clientLeft) {
                for (String publication : publicationsToDeliver) {
                    paddedPublication = this.protocol.padMessage(publication);
                    //                messageBuffer = paddedPublication.getBytes();
                    writer.write(paddedPublication);
                    writer.newLine();
                    writer.flush();
                }
            } else {
                writer.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to send matched publications in ClientManager: " + e.toString());
            e.printStackTrace();
        }
    }

    private void deliverPublication(String publicationToDeliver, int messageSize) {
        Set<String> singlePublicationSet = new HashSet<>();
        singlePublicationSet.add(publicationToDeliver);
        deliverPublications(singlePublicationSet, messageSize);
//        System.out.println("deliveredPublication");
    }

    public void clientLeft() {
        this.clientLeft = true;
    }
}
