package server;

import message.Message;
import message.Protocol;

import java.io.IOException;
import java.rmi.NotBoundException;

public interface ConsistencyPolicy {
    void initialize(ReplicatedPubSubServer server, Protocol protocol, Dispatcher dispatcher);
    void enforceOnJoin(String clientIp, int clientPort, String finalizedClientId, String previousServer)
            throws IOException, NotBoundException, InterruptedException;
    boolean enforceOnPublish(Message message, String fromIp, int fromPort)
            throws IOException, NotBoundException, InterruptedException;
    boolean enforceOnRetrieve(Message message, String fromIp, int fromPort)
            throws IOException, NotBoundException, InterruptedException;
    void enforceOnLeave(String clientIp, int clientPort)
            throws IOException, NotBoundException, InterruptedException;
    void synchronize();
}