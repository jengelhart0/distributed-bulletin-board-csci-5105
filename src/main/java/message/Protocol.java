package message;

import client.Client;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Protocol {
    private static final Logger LOGGER = Logger.getLogger( Protocol.class.getName() );

    // It is understood that all messages have data/payload fields,
    // so we only indicate others (i.e., the query fields).
    private List<String> queryFields;
    private List<String[]> allowedFieldValues;
    private String delimiter;
    private String wildcard;
    private int messageSize;
    private String emptyInternalFields;
    private int numInternalFields;
    private int numExternalFields;

    public Protocol(List<String> fields, List<String[]> allowedFieldValues,
                    String delimiter, String wildcard, int messageSize) {
        for(String field: fields) {
            if (field.equals("messageId") || field.equals("clientId")) {
                throw new IllegalArgumentException("Fields 'messageId' and 'clientId' internally reserved.");
            }
        }

        this.delimiter = delimiter;
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

    public int getMessageSize() {
        return this.messageSize;
    }

    public String getDelimiter() {
        return this.delimiter;
    }

    public String getWildcard() {
        return wildcard;
    }

    public List<String> getQueryFields() {
        return queryFields;
    }

    public boolean validate(String message, boolean isSubscription) {
        if(isClientIdMessage(message)) {
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
        StringBuilder padder = new StringBuilder(rawMessage);
        while (padder.length() < messageSize) {
            padder.append(" ");
        }
        return padder.toString();
    }

    boolean isClientIdMessage(String message) {
        String[] parsed = parse(message);
        return parsed.length > 1
                && parsed[0].equals("clientId")
                && !parsed[1].isEmpty();
    }

    private boolean isBasicallyValid(String message) throws IllegalArgumentException {
        if(message == null || message.length() != this.messageSize) {
            return false;
        }

        String[] parsedValues = parse(message);
        int valuesLength = parsedValues.length;
        if(valuesLength != queryFields.size() + 1) {
            return false;
        }

        if(!adheresToValueRestrictions(message)) {
            return false;
        }

        boolean nonWildcardValueExists = false;
        int i = 0;
        while(!nonWildcardValueExists && i < (valuesLength - 1)) {
            if (!(parsedValues[i].equals(this.wildcard))) {
                nonWildcardValueExists = true;
            }
            i++;
        }
        return nonWildcardValueExists;
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
        if(isClientIdMessage(message)) {
            // client id message format: "clientId;<ID>" if ';' is delimiter
            return parse(message)[1];
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
            result.append(delimiter + requested[i]);
        }
        return result.toString();
    }

}
