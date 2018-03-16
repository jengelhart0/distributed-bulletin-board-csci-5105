package server;

import message.Protocol;

public interface ConsistencyPolicy {
    void enforceOnJoin(String clientIP, int clientPort, String existingClientId, String previousServer)
            throws InterruptedException;

    void initialize(ReplicatedPubSubServer server, Protocol protocol, Dispatcher dispatcher);
}