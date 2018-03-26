package server;

import message.Message;
import message.Protocol;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PairedKeyMessageStore implements MessageStore {

    private Map<String, PublicationList> store;
    private Date lastStoreFlush;
    private Protocol protocol;

    private int highestMessageIdStored;
    private final Object highestMessageIdStoredLock = new Object();


    PairedKeyMessageStore(Protocol protocol) {
        this.store = new ConcurrentHashMap<>();
        this.lastStoreFlush = new Date();
        this.protocol = protocol;
        this.highestMessageIdStored = -1;

    }

    @Override
    public SortedSet<String> retrieve(Message subscription) {
        if (!subscription.isSubscription()) {
            throw new IllegalArgumentException("Store retrieve() received non-subscription message.");
        }

        freshenOffsetsIfNecessary(subscription);
        subscription.setLastAccess(new Date());

        SortedSet<String> matchedPublications = null;
        SortedSet<String> candidates;

        Set<String> conditions = subscription.getQueryConditions();

        for(String condition: conditions) {
            String lastRetrieved = subscription.getLastRetrievedFor(condition);

            candidates = (
                    store.containsKey(condition) ?
                    store.get(condition).getPublicationsStartingAt(lastRetrieved)
                    : new TreeSet<>(new MessageIdComparator(this.protocol)));

            if (!candidates.isEmpty()) {
                subscription.setLastRetrievedFor(condition, candidates.last());
            }

            if (matchedPublications != null) {
                matchedPublications.retainAll(candidates);
            } else {
                matchedPublications = candidates;
            }
        }
        return matchedPublications;
    }

    private void freshenOffsetsIfNecessary(Message subscription) {
        if (subscription.getLastAccess().compareTo(lastStoreFlush) < 0) {
            subscription.refreshAccessOffsets();
        }
    }

    @Override
    public boolean publish(Message message) {
        message.setLastAccess(new Date());
        Set<String> conditions = message.getQueryConditions();
//        System.out.println(store.keySet().size());
        for (String condition : conditions) {

            PublicationList listToAddPublicationTo = store.get(condition);
            if(listToAddPublicationTo == null) {
                listToAddPublicationTo = new PublicationList(protocol);
                store.put(condition, listToAddPublicationTo);
//                System.out.println("Added message " + message.asRawMessage() + " to store at condition " + condition );
            }
            listToAddPublicationTo.synchronizedAdd(message.asRawMessage());
            message.setLastRetrievedFor(condition, message.asRawMessage());
            updateHighestMessageIdStored(message);
        }
        return true;
    }

    private void updateHighestMessageIdStored(Message message) {
        int messageId = Integer.parseInt(message.getMessageId());
        synchronized (highestMessageIdStoredLock) {
            this.highestMessageIdStored = messageId > highestMessageIdStored ? messageId : highestMessageIdStored;
        }
    }

    @Override
    public int getHighestMessageIdStored() {
        synchronized (highestMessageIdStoredLock) {
            return highestMessageIdStored;
        }
    }

}
