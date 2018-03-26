package server;

import message.Message;
import message.Protocol;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.List;
import java.util.Set;

public class QuorumConsistency implements ConsistencyPolicy {
    private ReplicatedPubSubServer server;
    private Protocol protocol;
    private Dispatcher dispatcher;

    private int numServers;
    private int readQuorumSize;
    private int writeQuorumSize;


    public QuorumConsistency(int numServers, int readQuorumSize, int writeQuorumSize) {
        if (readQuorumSize + writeQuorumSize > numServers && writeQuorumSize > numServers / 2) {
            this.numServers = numServers;
            this.readQuorumSize = readQuorumSize;
            this.writeQuorumSize = writeQuorumSize;
        } else {
            throw new IllegalArgumentException(
                    "Required: readQuorum + writeQuorum > numServers && writeQuorum > numServers / 2");
        }
    }

    @Override
    public void initialize(ReplicatedPubSubServer server, Protocol protocol, Dispatcher dispatcher) {
        this.server = server;
        this.protocol = protocol;
        this.dispatcher = dispatcher;
        try {
            dispatcher.addNewClient("quorumUpdatePublisher", -1);
        } catch (IOException e) {
            throw new RuntimeException("in QuorumConsistency.initialize(): Failed to add quorumUpdatePublisher manager: "
                    + e.toString());
        }
    }
    @Override
    public void enforceOnJoin(String clientIp, int clientPort, String finalizedClientId, String previousServer) throws IOException, NotBoundException, InterruptedException {
        // nothing required for quorum consistency.
    }

    @Override
    public boolean enforceOnPublish(Message message, String fromIp, int fromPort) throws IOException, NotBoundException, InterruptedException {
        int clientIdOfSource = Integer.parseInt(dispatcher.getManagerFor(fromIp, fromPort).getClientId());
        int clientIdInMessage = Integer.parseInt(message.getClientId());
        // Message stores clientId of original client publisher. Manager stores id of the client currently publishing (whether
        // the original client or the intermediate server spreading through a quorum write).
        boolean publishComesFromUserClient = clientIdOfSource == clientIdInMessage;
        if (publishComesFromUserClient) {
            if (!server.createWriteQuorum(message, writeQuorumSize)) {
                throw new RuntimeException("Write quorum not established." +
                        " System not set up for failure in current implementation. Terminating.");
            }
        }
        return dispatcher.publish(message.asRawMessage(), fromIp, fromPort);
    }



    @Override
    public boolean enforceOnRetrieve(Message message, String fromIp, int fromPort) throws IOException, NotBoundException, InterruptedException {
        List<Message> retrieveThroughQuorum = server.createReadQuorum(message, readQuorumSize);
        boolean publishedHereSuccessfully;
        for(Message retrievedMessage: retrieveThroughQuorum) {
            publishedHereSuccessfully = dispatcher.publish(retrievedMessage.asRawMessage(),
                    "quorumUpdatePublisher",
                    -1);
            if(!publishedHereSuccessfully) {
                throw new RuntimeException("Failed to publish a result from read quorum." +
                        " System not set up for failure in current implementation. Terminating.");
            }
        }
        return true;
    }

    @Override
    public void enforceOnLeave(String clientIp, int clientPort) throws IOException, NotBoundException, InterruptedException {
        // nothing required for quorum consistency.
    }

    public void synchronize() {
        Set<String> possibleUpdates = server.getAllMessagesFromPeers();
        for(String possibleUpdate: possibleUpdates) {
            dispatcher.publish(possibleUpdate, "quorumUpdatePublisher", -1);
        }
    }
}