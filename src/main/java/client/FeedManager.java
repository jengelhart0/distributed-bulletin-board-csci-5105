package client;

import message.Message;
import message.Protocol;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class FeedManager {

    private Protocol protocol;
    private QueryMatcher queryMatcher;
    private List<Message> messageFeed;

    FeedManager(Protocol protocol, QueryMatcher queryMatcher) {
        this.messageFeed = Collections.synchronizedList(new LinkedList<>());
        this.protocol = protocol;
        this.queryMatcher = queryMatcher;
    }

    void handle(Message newMessage) {
        if(!queryMatcher.addIfMatchesAQuery(newMessage.asRawMessage())) {
            this.messageFeed.add(newMessage);
        }
    }

    List<Message> consumeCurrentMessageFeed() {
        List<Message> feedCopy = Collections.synchronizedList(new LinkedList<>());
        feedCopy.addAll(this.messageFeed);
        this.messageFeed.clear();
        return feedCopy;
    }

    boolean handleRetrieveNotificationIfThisIsOne(Message newMessage) {
        String messageString = newMessage.asRawMessage();
        String[] parsed = protocol.controlParse(messageString);
        if(protocol.isRetrieveNotification(messageString)) {
            String query = parsed[1];
            int numIncoming = Integer.parseInt(protocol.stripPadding(parsed[2]));
            queryMatcher.setNumIncomingFor(query, numIncoming);
            return true;
        }
        return false;
    }

    void addPendingQueryToMatch(String query) {
        queryMatcher.addPendingQuery(query);
    }

    List<Message> consumeMatchesIfAllReceivedFor(String query) {
        return queryMatcher.consumeMatchesIfAllReceivedFor(query);
    }
}
