package server;

import message.Message;

import java.util.Set;

public interface MessageStore {
    Set<String> retrieve(Message subscription, int limit);
    boolean publish(Message message);
}
