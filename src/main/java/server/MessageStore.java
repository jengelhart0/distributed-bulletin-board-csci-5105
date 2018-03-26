package server;

import message.Message;

import java.util.Set;

public interface MessageStore {
    Set<String> retrieve(Message subscription);
    boolean publish(Message message);
    int getHighestMessageIdStored();
}
