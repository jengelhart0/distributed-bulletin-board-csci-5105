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
    public void enforceOnJoin(String clientIp, int clientPort, String finalizedClientId, String previousServer)
            throws IOException, NotBoundException, InterruptedException {
        if(previousServer != null) {
            String retrieveAllQuery = protocol.getRetrieveAllQuery();
            List<Message> retrieved = server.retrieveFromPeer(previousServer, new Message(protocol, retrieveAllQuery, true));
            System.out.println(this.server.getThisServersIpPortString() + " retrieved on join cons enforcement: " + retrieved.toString() + " Adding to store");
            for(Message byClient: retrieved) {
                dispatcher.publish(byClient.asRawMessage(), clientIp, clientPort);
            }
        }
    }

    @Override
    public boolean enforceOnPublish(Message message, String fromIp, int fromPort) throws IOException, NotBoundException, InterruptedException {
        System.out.println("In ReadYourWrites publish enforce");
        return dispatcher.publish(message.asRawMessage(), fromIp, fromPort);
    }

    @Override
    public boolean enforceOnRetrieve(Message message, String fromIp, int fromPort) throws IOException, NotBoundException, InterruptedException {
        // Nothing required for read-your-writes consistency
        return true;
    }

    @Override
    public void enforceOnLeave(String clientIp, int clientPort) throws IOException, NotBoundException, InterruptedException {
        // Nothing required for read-your-writes consistency
    }

    @Override
    public void synchronize() {
        // Nothing required for read-your-writes consistency
    }


}
