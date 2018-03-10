package server;

import message.Message;
import message.Protocol;
import message.Query;

import java.util.Set;

public interface MessageStore {
    Set<String> retrieve(Message subscription);
    boolean publish(Message message);
}
