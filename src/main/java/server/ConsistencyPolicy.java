package server;

import message.Protocol;

import java.io.IOException;
import java.rmi.NotBoundException;

public interface ConsistencyPolicy {
    void initialize(ReplicatedPubSubServer server, Protocol protocol, Dispatcher dispatcher);
    void enforceOnJoin(String clientIp, int clientPort, String existingClientId, String previousServer)
            throws IOException, NotBoundException, InterruptedException;
    void enforceOnPublish(String message, String clientIp, int clientPort, String existingClientId)
            throws IOException, NotBoundException, InterruptedException;
}