package server;

import message.Message;

import java.io.IOException;

public interface CommunicationManager {

    enum Call {
        RETURN_CLIENT_ID_TO_CLIENT, PUBLISH, SUBSCRIBE, UNSUBSCRIBE, PULL_MATCHES
    }

    Runnable task(Message message, MessageStore store, Call call);
    void deliverClientId(Message clientIdMessage);
    void subscribe(Message message);
    void unsubscribe(Message message);
    void publish(Message message, MessageStore store);
    void pullSubscriptionMatchesFromStore(MessageStore store) throws IOException;
    void clientLeft();
}
