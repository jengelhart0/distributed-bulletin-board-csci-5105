package server;

import message.Message;
import message.Protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientManager implements CommunicationManager {
    private static final Logger LOGGER = Logger.getLogger( ClientManager.class.getName() );

    private String clientIp;
    private int clientPort;

    private boolean clientLeft;

    private Protocol protocol;

    private List<Message> subscriptions;
    private List<Message> publications;

    private final Object subscriptionLock = new Object();
    private final Object publicationLock = new Object();

    private static final MessageStore store = PairedKeyMessageStore.getInstance();

    ClientManager(String clientIp, int clientPort, Protocol protocol) {
        this.clientIp = clientIp;
        this.clientPort = clientPort;

        this.clientLeft = false;

        this.protocol = protocol;

        this.subscriptions = new LinkedList<>();
        this.publications = new ArrayList<>();
    }

    public Runnable task(Message message, CommunicationManager.Call call) {
        switch(call) {
            case SUBSCRIBE:
                return () -> subscribe(message);
            case PUBLISH:
                return () -> publish(message);
            case UNSUBSCRIBE:
                return () -> unsubscribe(message);
            case PULL_MATCHES:
                return this::pullSubscriptionMatchesFromStore;
            default:
                throw new IllegalArgumentException("Task call made to ClientManager not recognized.");
        }
    }

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
    public void publish(Message message) {
        synchronized (publicationLock) {
            this.publications.add(message);
        }
        store.publish(message);
    }

    @Override
    public void pullSubscriptionMatchesFromStore() {
        if(!clientLeft) {
            // Get all subscriptions into cheap container so we get out of synchronized block fast
            // (as store.retrieve(...) is relatively intensive).
            Message[] subscriptionsToMatch;
            synchronized (subscriptionLock) {
                subscriptionsToMatch = subscriptions.toArray(new Message[subscriptions.size()]);
            }

            Set<String> toDeliver = getSubscriptionMatches(subscriptionsToMatch);

            int messageSize = this.protocol.getMessageSize();
            try {
                DatagramPacket deliveryPacket = new DatagramPacket(
                        new byte[messageSize], messageSize, InetAddress.getByName(clientIp), clientPort);

                deliverPublications(toDeliver, deliveryPacket, messageSize);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to send matched publications in ClientManager: " + e.toString());
                e.printStackTrace();
            }
        }
    }

    private Set<String> getSubscriptionMatches(Message[] subscriptionsToMatch) {
        Set<String> toDeliver = new HashSet<>();
        MessageStore store = PairedKeyMessageStore.getInstance();
        for(Message subscription: subscriptionsToMatch) {
            toDeliver.addAll(store.retrieve(subscription));
        }
        return toDeliver;
    }

    private void deliverPublications(Set<String> publicationsToDeliver,
                                     DatagramPacket deliveryPacket, int messageSize) throws IOException {

        try (DatagramSocket deliverySocket = new DatagramSocket();
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream))
        {
            byte[] messageBuffer = new byte[messageSize];
            String paddedPublication;
            for (String publication: publicationsToDeliver) {
                if(publication.length() > messageSize) {
                    throw new IllegalArgumentException(
                            "ClientManager tried to deliver publication violating protocol: wrong messageSize");
                }
                paddedPublication = this.protocol.padMessage(publication);
                messageBuffer = paddedPublication.getBytes();
                DatagramPacket packetToSend = new DatagramPacket(
                        messageBuffer, messageSize, InetAddress.getByName(clientIp), this.clientPort);

                deliverySocket.send(packetToSend);
            }
        }
    }

    public void informManagerThatClientLeft() {
        this.clientLeft = true;
    }
}
