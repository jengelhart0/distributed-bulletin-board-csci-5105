package message;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Query {
    private String[] fields;
    private String[] values;
    private boolean isSubscription;
    private String wildcard;
    private Map<String, String> query;
    private Date lastAccess;
    private final Object lastAccessLock = new Object();

    Query(String[] fields, String[] values, String wildcard, boolean isSubscription) {
        this.fields = fields;
        this.values = values;
        this.query = new ConcurrentHashMap<>();
        this.wildcard = wildcard;
        this.isSubscription = isSubscription;
        this.lastAccess = new Date();
    }

    Query generate() {
        int i;
        int numFields = fields.length;
        String field, value;

        for (i = 0; i < numFields; i++) {
            field = fields[i];
            value = values[i];
            query.put(field + "_" + value, "");

            // Messages to be published need to be added to wildcard buckets
            if (!isSubscription) {
                query.put(field + "_" + wildcard, "");
            }
        }
        return this;
    }

    public void refreshAccessOffsets() {
        for(String fieldValuePair: query.keySet()) {
            setLastRetrievedFor(fieldValuePair, "");
        }
    }

    public Set<String> getConditions() {
        return query.keySet();
    }

    public String getLastRetrievedFor(String fieldValuePair) {
        return query.get(fieldValuePair);
    }

    public void setLastRetrievedFor(String fieldValuePair, String lastRetrieved) {
        query.put(fieldValuePair, lastRetrieved);
    }

    public Date getLastAccess() {
        synchronized (lastAccessLock) {
            return lastAccess;
        }
    }

    public void setLastAccess(Date lastAccess) {
        synchronized (lastAccessLock) {
            this.lastAccess = lastAccess;
        }
    }
}
