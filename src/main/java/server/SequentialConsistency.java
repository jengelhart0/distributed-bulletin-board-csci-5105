package server;

import message.Message;
import message.Protocol;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SequentialConsistency implements ConsistencyPolicy {
    private ReplicatedPubSubServer server;
    private Protocol protocol;
    private Dispatcher dispatcher;
    // maintains sorted order of next message to publish, since messageId is first field and determines correct publication order
    private SortedMap<Integer, String> toPublishQueue;
    private ConcurrentMap<Integer, String> messageIdToPublisherIpPortString;
    private int nextExpectedMessageId;

    @Override
    public void initialize(ReplicatedPubSubServer server, Protocol protocol, Dispatcher dispatcher) {
        this.server = server;
        this.protocol = protocol;
        this.dispatcher = dispatcher;
        this.toPublishQueue = Collections.synchronizedSortedMap(new TreeMap<>());
        this.messageIdToPublisherIpPortString = new ConcurrentHashMap<>();
        this.nextExpectedMessageId = 0;
    }

    @Override
    public void enforceOnJoin(String clientIp, int clientPort, String finalizedClientId, String previousServer) throws IOException, NotBoundException, InterruptedException {
        // TODO: do we really need the coordinator need to have every user client join it? Working hypoth: no.
//        if(!server.isCoordinator()) {
//            server.getCoordinator().Join(clientIp, clientPort, finalizedClientId, previousServer);
//        }
    }

    @Override
    public void enforceOnLeave(String clientIp, int clientPort) throws IOException, NotBoundException, InterruptedException {
//        if(!server.isCoordinator()) {
//            server.getCoordinator().Leave(clientIp, clientPort);
//        }
    }

    @Override
    public boolean enforceOnPublish(Message message, String fromIp, int fromPort) throws IOException, NotBoundException {
        System.out.println("In Sequential publish enforce");
        if(server.isCoordinator()) {
          System.out.println("Seq pub enf: I am coordinator");
            // in this case, the message is from 1) a direct user client or 2) a non-coordinator peer. In both cases we
            // have a manager for fromIp/fromPort
            sequentialPublish(message, fromIp, fromPort);
            System.out.println("Seq pub enf: I am coordinator, finished seqPub, now publishing to all peers");
            publishToAllPeersAsCoordinator(message);

        // case when the coordinator is publishing to this server.
        // All servers have manager for coordinator, since it joined through peerClient
        } else if( server.messageIsFromCoordinator(fromIp, fromPort) ) {
            System.out.println("Seq pub enf: Message is from Coord. Message: " + message.asRawMessage());
            sequentialPublish(message, fromIp, fromPort);
            System.out.println("Seq pub enf: I am NOT coordinator, finished seqPub");
        // case when this publication is from a 'real' client
        } else {
            System.out.println("Seq pub enf: Message is from actual client: Sending to coordinator ");
            server.publishToCoordinator(message);
            System.out.println("Seq pub enf: finished publishing to coord");
        }
        System.out.println("Leaving enforceOnPublish (last thing before return to client)");
        return true;
    }

    @Override
    public boolean enforceOnRetrieve(Message message, String fromIp, int fromPort) throws IOException, NotBoundException, InterruptedException {
        // Nothing required for sequential consistency
        return false;
    }

    private void publishToAllPeersAsCoordinator(Message message) throws IOException, NotBoundException {
        if(!server.isCoordinator()) {
            throw new IllegalArgumentException("SequentialConsistency: Tried to publish to all peers without being coordinator");
        }
        server.publishToAllPeers(message);
    }

    private void sequentialPublish(Message message, String fromIp, int fromPort) {
        // Should only be called by the coordinator or if message is from the coordinator
        queueMessage(message, fromIp, fromPort);
        publishAllCorrectlyOrderedPublications();
    }

    private void queueMessage(Message message, String fromIp, int fromPort) {
        int messageId = Integer.parseInt(message.getMessageId());
        toPublishQueue.put(messageId, message.asRawMessage());
        messageIdToPublisherIpPortString.put(messageId, ServerUtils.getIpPortString(fromIp, fromPort, protocol));
    }

    private void publishAllCorrectlyOrderedPublications() {
        // toPublishQueue is a sorted map, whose keys are messageIds, so this iterates in order of pending messages' messageIds
        for (Integer currentMessageId : toPublishQueue.keySet()) {
            // If we are here, either 1) message is from coordinator or 2) this is coordinator.
            // Either way, fromIp and fromPort are the Coordinator's location. We have a manager for all peers, including coordinator.
            if(currentMessageId == nextExpectedMessageId) {
                publishAndRemoveFromQueue(currentMessageId);
                nextExpectedMessageId++;
            } else {
                break;
            }
        }
    }

    private void publishAndRemoveFromQueue(int currentMessageId) {
        String ipPortString = messageIdToPublisherIpPortString.get(currentMessageId);
        String fromIp = ServerUtils.getIpFromIpPortString(ipPortString, protocol);
        int fromPort = ServerUtils.getPortFromIpPortString(ipPortString, protocol);

        String publication = toPublishQueue.get(currentMessageId);
        dispatcher.publish(publication, fromIp, fromPort);

        toPublishQueue.remove(currentMessageId);
        messageIdToPublisherIpPortString.remove(currentMessageId);
    }

    @Override
    public void synchronize() {
        // nothing required for sequential consistency.
    }
}
