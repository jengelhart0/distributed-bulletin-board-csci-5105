package client;

import message.Message;
import message.Protocol;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class QueryMatcher {
    private Protocol protocol;
    private ConcurrentMap<String, List<String>> queryMatches;
    private ConcurrentMap<String, Integer> numIncomingForQuery;
    private ConcurrentMap<String, Integer> numReceivedForQuery;
    private final Lock pendingQueryLock;
    private final Condition matchesForPendingQueryReceived;

    QueryMatcher(Protocol protocol, Lock pendingQueryLock, Condition matchesForPendingQueryReceived) {
        this.protocol = protocol;
        this.pendingQueryLock = pendingQueryLock;
        this.matchesForPendingQueryReceived = matchesForPendingQueryReceived;
        this.queryMatches = new ConcurrentHashMap<>();
        this.numIncomingForQuery = new ConcurrentHashMap<>();
        this.numReceivedForQuery = new ConcurrentHashMap<>();
    }

    void setNumIncomingFor(String query, int numIncoming) {
        numIncomingForQuery.put(query, numIncoming);
    }

    void notifyNoResults(String query) {
        signalIfAllMatchesReceivedFor(query);
    }

    boolean addIfMatchesAQuery(String message) {
        for(String query: queryMatches.keySet()) {
            if (isAMatch(message, query)) {
                queryMatches.get(query).add(message);
                numReceivedForQuery.put(query, numReceivedForQuery.get(query) + 1);
                signalIfAllMatchesReceivedFor(query);
                return true;
            }
        }
        return false;
    }

    private boolean isAMatch(String message, String query) {
        String[] parsedQuery = protocol.parse(query);
        String[] parsedMessage = protocol.parse(message);

        if(parsedMessage.length == parsedQuery.length) {
            // Should skip comparing last field (contents)
            for(int i = 0; i < parsedMessage.length - 1; i++) {
                if(!( parsedQuery[i].equals(protocol.getWildcard() )
                        || parsedMessage[i].equals(parsedQuery[i]) )) {
                    return false;
                }
            }
        }
        return true;
    }

    List<Message> consumeMatchesIfAllReceivedFor(String query) {
        if(allMatchesReceivedFor(query)) {
            List<Message> allMatches = Collections.synchronizedList(new LinkedList<>());
            for(String match: queryMatches.get(query)) {
                allMatches.add(new Message(protocol, match, false));
            }
            queryMatches.put(query, Collections.synchronizedList(new LinkedList<>()));
            return allMatches;
        }
        return null;
    }

    private void signalIfAllMatchesReceivedFor(String query) {
        if(allMatchesReceivedFor(query)) {
            pendingQueryLock.lock();
            try {
                matchesForPendingQueryReceived.signalAll();
            } finally {
                pendingQueryLock.unlock();
            }
        }
    }

    private boolean allMatchesReceivedFor(String query) {
        return numReceivedForQuery.get(query).equals(numIncomingForQuery.get(query));
    }

    public void addPendingQuery(String query) {
        queryMatches.put(query, Collections.synchronizedList(new LinkedList<>()));
        numReceivedForQuery.put(query, 0);
        numIncomingForQuery.put(query, -1);
    }
}
