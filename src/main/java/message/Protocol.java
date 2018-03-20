package message;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Protocol {
    private static final Logger LOGGER = Logger.getLogger( Protocol.class.getName() );

    // It is understood that all messages have data/payload fields,
    // so we only indicate others (i.e., the query fields).
    private List<String> queryFields;
    private List<String[]> allowedFieldValues;
    private String delimiter;
    private String controlDelimiter;
    private String wildcard;
    private int messageSize;
    private String emptyInternalFields;
    private int numInternalFields;
    private int numExternalFields;

    public Protocol(List<String> fields, List<String[]> allowedFieldValues,
                    String delimiter, String wildcard, int messageSize) {
        this(fields, allowedFieldValues, delimiter, ":", wildcard, messageSize);
    }

    public Protocol(List<String> fields, List<String[]> allowedFieldValues,
                    String delimiter, String controlDelimiter, String wildcard, int messageSize) {
        for(String field: fields) {
            if (field.equals("messageId") || field.equals("clientId")) {
                throw new IllegalArgumentException("Fields 'messageId' and 'clientId' internally reserved.");
            }
        }

        this.delimiter = delimiter;
        this.controlDelimiter = controlDelimiter;
        this.messageSize = messageSize;
        this.wildcard = wildcard;

        combineInternalAndExternalFields(fields, allowedFieldValues);

    }
    private void combineInternalAndExternalFields(List<String> externalFields,
                                                  List<String[]> allowedExternalFieldValues) {
        this.numExternalFields = externalFields.size();
        this.numInternalFields = 2;
        this.emptyInternalFields = wildcard + delimiter + wildcard + delimiter;

        this.queryFields = new LinkedList<>();
        this.queryFields.add("messageId");
        this.queryFields.add("clientId");
        this.queryFields.addAll(externalFields);

        this.allowedFieldValues = new LinkedList<>();
        this.allowedFieldValues.add(new String[]{""});
        this.allowedFieldValues.add(new String[]{""});
        this.allowedFieldValues.addAll(allowedExternalFieldValues);
    }

    int getNumExternalFields() {
        return numExternalFields;
    }

    String getEmptyInternalFields() {
        return emptyInternalFields;
    }

    public String[] parse(String message) {
        return message.split(this.delimiter, -1);
    }

    public String[] controlParse(String message) {
        return message.split(this.controlDelimiter, -1);
    }

    public int getMessageSize() {
        return this.messageSize;
    }

    public String getDelimiter() {
        return this.delimiter;
    }

    public String getControlDelimiter() { return this.controlDelimiter; }

    public String getWildcard() {
        return wildcard;
    }

    public List<String> getQueryFields() {
        return queryFields;
    }

    public boolean validate(String message, boolean isSubscription) {
        if(isControlMessage(message)) {
            return true;
        }

        if (!isBasicallyValid(message)) {
            return false;
        }
        String[] parsedMessage = parse(message);
        boolean lastFieldEmpty = Pattern.matches(
                "^" + this.wildcard + "\\s*$", parsedMessage[parsedMessage.length - 1]);

        if(isSubscription) {
            return lastFieldEmpty;
        }
        return !lastFieldEmpty;
    }

    public String padMessage(String rawMessage) {

        String message = stripPadding(rawMessage);

        if(message.length() > messageSize) {
            throw new IllegalArgumentException(
                    "Tried to create message violating protocol: message too large");
        }


        StringBuilder padder = new StringBuilder(message);
        while (padder.length() < messageSize) {
            padder.append(" ");
        }
        return padder.toString();
    }

    public String stripPadding(String paddedMessage) {
        return paddedMessage.replaceAll("^\\s+", "").replaceAll("\\s+$", "");
    }

    boolean isControlMessage(String message) {
        return isClientIdMessage(message) || isRetrieveNotification(message);
    }

    private boolean isClientIdMessage(String message) {
        String[] parsed = controlParse(message);
        return parsed.length > 1
                && parsed[0].equals("clientId")
                && !parsed[1].isEmpty();
    }

    public boolean isRetrieveNotification(String message) {
        String[] parsed = controlParse(message);
        return parsed.length == 3
                && parsed[0].equals("retrieveNotification")
                && !parsed[1].equals("")
                && !parsed[2].equals("");
    }

    private boolean isBasicallyValid(String message) throws IllegalArgumentException {
        if(message == null || message.length() != this.messageSize) {
            return false;
        }
        String[] parsedValues = parse(message);
        int valuesLength = parsedValues.length;

        return (valuesLength == queryFields.size() + 1) && adheresToValueRestrictions(message);
    }

    private boolean adheresToValueRestrictions(String message) {
        String[] parsedValues = parse(message);
        for(int i = 0; i < allowedFieldValues.size(); i++) {
            String[] allowedValues = allowedFieldValues.get(i);
            String parsedValue = parsedValues[i];
            if(!(allowedValues.length == 1 && allowedValues[0].equals(wildcard))) {
                boolean foundValidValue = false;
                int j = 0;
                while (!foundValidValue && j < allowedValues.length) {
                    if (parsedValue.equals(allowedValues[j])) {
                        foundValidValue = true;
                    }
                    j++;
                }
                if(!foundValidValue) {
                    return false;
                }
            }
        }
        return true;
    }

    public String extractIdIfThisIsIdMessage(String message) {
        if(isControlMessage(message)) {
            // client id message format: "clientId;<ID>" if ';' is delimiter
            return controlParse(message)[1];
        }
        return "";
    }

    public boolean areInternalFieldsBlank(String message) {
        int internalFieldsLength = emptyInternalFields.length();
        return message.length() >= internalFieldsLength
                && message.startsWith(emptyInternalFields)
                // message contents always part of message but not counted as external field.
                && parse(message.substring(internalFieldsLength)).length == numExternalFields + 1;
    }

    String withoutInternalFields(String message) {
        String[] parsed = parse(message);
        // can happen if this is clientId message
        if (parsed.length <= numInternalFields) {
            return message;
        }
        String[] requested = Arrays.copyOfRange(parsed, numInternalFields, parsed.length);
        StringBuilder result = new StringBuilder(requested[0]);
        for (int i = 1; i < requested.length; i++) {
            result.append(delimiter).append(requested[i]);
        }
        return result.toString();
    }

    public String getRetrieveAllByClientQuery(String clientId) {
        StringBuilder message = new StringBuilder()
                .append("")
                .append(delimiter)
                .append(clientId);
        for(int i = 0; i < numExternalFields + 1; i++) {
            message.append(delimiter).append("");
        }
        return message.toString();
    }

    String insertMessageId(String message, String messageId) {
        String[] parsed = parse(message);
        parsed[0] = messageId;

        StringBuilder result = new StringBuilder();
        result.append(parsed[0]);
        for(int i = 1; i < parsed.length; i++) {
            result.append(delimiter).append(parsed[i]);
        }
        return result.toString();
    }

    String insertClientId(String message, String clientId) {
        String[] parsed = parse(message);
        parsed[1] = clientId;

        StringBuilder result = new StringBuilder();
        result.append(parsed[0]);
        for(int i = 1; i < parsed.length; i++) {
            result.append(delimiter).append(parsed[i]);
        }
        return result.toString();
    }

    public String buildRetrieveNotification(String query, int numRetrieved) {
        //TODO: make this work

        String strippedQuery = stripPadding(query);

        String retrieveNotification = "retrieveNotification"
                + getControlDelimiter()
                + strippedQuery
                + getControlDelimiter()
                + numRetrieved;
        return retrieveNotification;
    }

    String getMessageId(String message) {
        String[] parsed = parse(message);
        return parsed[0];
    }

    String getClientId(String message) {
        String[] parsed = parse(message);
        return parsed[1];
    }
}
