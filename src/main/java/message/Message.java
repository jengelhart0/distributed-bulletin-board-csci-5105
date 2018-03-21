package message;

import java.util.Date;
import java.util.Set;

public class Message {

    private Protocol protocol;
    private String asString;
    private Query query;
    private boolean isSubscription;
    private String withoutInternal;

    public Message(Protocol protocol, String rawMessage, boolean isSubscription) {
        this.protocol = protocol;
        this.isSubscription = isSubscription;
        if (!isControlMessage(rawMessage)) {
            setProcessedMessage(rawMessage, protocol);
            if (!validate(isSubscription)) {
                throw new IllegalArgumentException("Was an invalid message: " + asString);
            }
            setQuery();
        } else {
            this.asString = protocol.padMessage(rawMessage);
        }
    }

    private void setProcessedMessage(String rawMessage, Protocol protocol) {
        String processedMessage = rawMessage;
        String[] fieldsInMessage = protocol.parse(rawMessage);
        // message contents is an "assumed field": its in fieldsInMessage but never counted in externalFields
        if(fieldsInMessage.length == protocol.getNumExternalFields() + 1) {
            processedMessage = protocol.getEmptyInternalFields() + rawMessage;
        }

//        int messageSize = protocol.getMessageSize();
//        if(processedMessage.length() <= messageSize) {
            processedMessage = protocol.padMessage(processedMessage);
//        }

        this.asString = processedMessage;
    }

    private boolean validate(boolean isSubscription) {
        return protocol.validate(asString, isSubscription);
    }

    public String asRawMessage() {
        return asString;
    }

    public String withoutInternalFields() {
        if (withoutInternal == null) {
            withoutInternal = protocol.padMessage(protocol.withoutInternalFields(asString));
        }
        return withoutInternal;
    }

    private void setQuery() {
        this.query = generateQuery(this, this.protocol);
    }

    private Query generateQuery(Message message, Protocol protocol) {
        return new Query(protocol.getQueryFields(),
                protocol.parse(message.asRawMessage()),
                protocol.getWildcard(),
                message.isSubscription())

                .generate();
    }

    public void regenerateQuery() {
        setQuery();
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

    private boolean isControlMessage(String message) {
        return protocol.isControlMessage(message);
    }

    public boolean isCoordinatorPortMessage() {
        return protocol.isCoordinatorPortMessage(protocol.stripPadding(asString));
    }

    public String extractIdIfThisIsIdMessage() {
        return protocol.extractIdIfThisIsIdMessage(withoutInternalFields());
    }

//    public void ensureInternalsExistAndRegenerateQuery(String messageId, String clientId) {
//        if(protocol.areInternalFieldsBlank(asString)) {
//            String withId = protocol.insertInternals(asString, messageId, clientId);
//            if (withId == null) {
//                throw new IllegalArgumentException("Tried to insert client id in message whose internal fields weren't blank.");
//            }
//            asString = withId;
//            setQuery();
//        }
//    }

    public void insertMessageId(String messageId) {
        this.asString = protocol.insertMessageId(asString, messageId);
    }

    public void insertClientId(String clientId) {
        this.asString = protocol.insertClientId(asString, clientId);
    }

    public String getMessageId() {
        return protocol.getMessageId(asString);
    }

    public String getClientId() {
        return protocol.getClientId(asString);
    }

}