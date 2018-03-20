package server;

import message.Message;

import java.io.IOException;

public interface CommunicationManager {

    enum Call {
        RETURN_CLIENT_ID_TO_CLIENT, PUBLISH, SUBSCRIBE, UNSUBSCRIBE, RETRIEVE, PULL_MATCHES
    }

    Runnable task(Message message, MessageStore store, Call call);
    void deliverControlMessage(Message controlMessage);
    void subscribe(Message message);
    void unsubscribe(Message message);
    void retrieve(Message queryMessage, MessageStore store);
    void publish(Message message, MessageStore store);
    void pullSubscriptionMatchesFromStore(MessageStore store) throws IOException;
    void setClientId(String clientId);
    String getClientId();
    void clientLeft();
}
