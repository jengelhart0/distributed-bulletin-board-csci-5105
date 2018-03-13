package message;

import java.util.Date;
import java.util.Set;

public class Message {

    private Protocol protocol;
    private String asString;
    private Query query;
    private boolean isSubscription;

    public Message(Protocol protocol, String rawMessage, boolean isSubscription) {
        this.protocol = protocol;

        setProcessedMessage(rawMessage, protocol);

        if (!validate(isSubscription)) {
            throw new IllegalArgumentException("Was an invalid subscription: " + asString);
        }
        this.isSubscription = isSubscription;

        setQuery();
    }

    private void setProcessedMessage(String rawMessage, Protocol protocol) {
        String processedMessage = rawMessage;
        String[] fieldsInMessage = protocol.parse(rawMessage);
        if(fieldsInMessage.length == protocol.getNumExternalFields()) {
            processedMessage = protocol.getEmptyInternalFields() + rawMessage;
        }

        int messageSize = protocol.getMessageSize();
        if(processedMessage.length() <= messageSize) {
            processedMessage = protocol.padMessage(processedMessage);
        }

        this.asString = processedMessage;
    }

    private boolean validate(boolean isSubscription) {
        return protocol.validate(asString, isSubscription);
    }

    public String asRawMessage() {
        return asString;
    }

    private void setQuery() {
        this.query = generateQuery(this, this.protocol);
    }

    public Query generateQuery(Message message, Protocol protocol) {
        return new Query(protocol.getQueryFields(),
                protocol.parse(message.asRawMessage()),
                protocol.getWildcard(),
                message.isSubscription())

                .generate();
    }

    public Set<String> getQueryConditions() {
        return this.query.getConditions();
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getLastRetrievedFor(String condition) {
        return this.query.getLastRetrievedFor(condition);
    }

    public void setLastRetrievedFor(String condition, String lastRetrieved) {
        this.query.setLastRetrievedFor(condition, lastRetrieved);
    }

    public Date getLastAccess() {
        return this.query.getLastAccess();
    }

    public void setLastAccess(Date current) {
        this.query.setLastAccess(current);
    }

    public boolean isSubscription() {
        return isSubscription;
    }

    public void refreshAccessOffsets() {
        this.query.refreshAccessOffsets();
    }
}