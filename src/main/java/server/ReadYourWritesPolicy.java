package server;

import message.Message;
import message.Protocol;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.List;

public class ReadYourWritesPolicy implements ConsistencyPolicy {

    private ReplicatedPubSubServer server;
    private Protocol protocol;
    private Dispatcher dispatcher;

    public void initialize(ReplicatedPubSubServer server, Protocol protocol, Dispatcher dispatcher) {
        this.server = server;
        this.protocol = protocol;
        this.dispatcher = dispatcher;
    }

    @Override
    public void enforceOnJoin(String clientIp, int clientPort, String existingClientId, String previousServer)
            throws IOException, NotBoundException, InterruptedException {
        String clientId = protocol.stripPadding(existingClientId);
        if(clientId != null && previousServer != null) {
            String retrieveAllByClientQuery = protocol.getRetrieveAllByClientQuery(clientId);
            List<Message> retrieved = server.retrieveFromPeer(previousServer, new Message(protocol, retrieveAllByClientQuery, true));
            System.out.println(this.server.getThisServersIpPortString() + " retrieved: " + retrieved.toString() + " Adding to store");
            for(Message byClient: retrieved) {
                dispatcher.publish(byClient.asRawMessage(), clientIp, clientPort);
            }
        }
    }


}
