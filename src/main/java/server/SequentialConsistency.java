package server;

import message.Message;
import message.Protocol;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.SortedSet;
import java.util.TreeSet;

public class SequentialConsistency implements ConsistencyPolicy {
    private ReplicatedPubSubServer server;
    private Protocol protocol;
    private Dispatcher dispatcher;
    private MessageStore store;
    // maintains sorted order of next message to publish, since messageId is first field and determines correct publication order
    private SortedSet<String> toPublishQueue;
    private final Object queueLock = new Object();
    private int nextExpectedMessageId;

    @Override
    public void initialize(ReplicatedPubSubServer server, Protocol protocol, Dispatcher dispatcher, MessageStore store) {
        this.server = server;
        this.protocol = protocol;
        this.dispatcher = dispatcher;
        this.store = store;
        this.toPublishQueue = new TreeSet<>();
        this.nextExpectedMessageId = 0;
    }

    @Override
    public void enforceOnJoin(String clientIp, int clientPort, String existingClientId, String previousServer) throws IOException, NotBoundException, InterruptedException {
        if(!server.isCoordinator()) {
            server.getCoordinator().Join(clientIp, clientPort, existingClientId, previousServer);
        }
    }

    @Override
     public void enforceOnPublish(String message, String clientIp, int clientPort, String existingClientId) throws IOException, NotBoundException, InterruptedException {
        // if this server is coordinator
        Message newMessage = new Message(protocol, message, false);

        if(server.isCoordinator()) {
            String messageId = server.requestNewMessageId();
            newMessage.ensureInternalsExistAndRegenerateQuery(messageId, existingClientId);
            sequentialPublish(newMessage, clientIp, clientPort);
        // case when the coordinator is publishing to this server
        } else if(server.getCoordinatorIp().equals(clientIp)
                && server.getCoordinatorPort().equals(String.valueOf(clientPort))) {
            if(!protocol.areInternalFieldsBlank(newMessage.asRawMessage())) {
                sequentialPublish(newMessage, clientIp, clientPort);
            } else {
                throw new IllegalArgumentException("enforcing on publish, not in coordinator: internal fields should not be blank.");
            }
        // case when this publication is from a 'real' client
        } else {
            // when sending new writes to Coordinator, we forward clientIp and clientPort. Coordinator maintains manager for
            // client as well (see join/leave).
            server.getCoordinator().Publish(newMessage.asRawMessage(), clientIp, clientPort);
        }
    }

    private void sequentialPublish(Message message, String clientIp, int clientPort) {
        int messageId = Integer.parseInt(message.getMessageId());
        if(messageId != nextExpectedMessageId) {
            toPublishQueue.add(message.asRawMessage());
        } else {
            for(String publication: toPublishQueue) {
                // this publication could be from a client attached to a different server. If so we publish through a
                // general 'clientElsewhere' manager
                if(dispatcher.getManagerFor(clientIp, clientPort) == null) {
                    dispatcher.publish(publication, "clientElsewhere", -1);
                } else {
                    dispatcher.publish(message.asRawMessage(), clientIp, clientPort);
                }
            }
            nextExpectedMessageId++;
        }
    }

}
