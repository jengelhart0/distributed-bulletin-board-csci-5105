package message;

import java.util.Set;
import java.util.regex.Pattern;

public class Protocol {
    // It is understood that all messages have data/payload fields,
    // so we only indicate others (i.e., the query fields).
    private String[] queryFields;
    private String[][] allowedFieldValues;
    private String delimiter;
    private String wildcard;
    private int messageSize;

    public Protocol(String[] fields, String[][] allowedFieldValues,
                    String delimiter, String wildcard, int messageSize) {
        this.queryFields = fields;
        this.allowedFieldValues = allowedFieldValues;
        this.delimiter = delimiter;
        this.messageSize = messageSize;
        this.wildcard = wildcard;
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

    public String[] getQueryFields() {
        return queryFields;
    }

    public boolean validate(String message, boolean isSubscription) {
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

    private boolean isBasicallyValid(String message) throws IllegalArgumentException {
        if(message == null || message.length() != this.messageSize) {
            return false;
        }

        String[] parsedValues = parse(message);
        int valuesLength = parsedValues.length;
        if(valuesLength != queryFields.length + 1) {
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
        for(int i = 0; i < allowedFieldValues.length; i++) {
            String[] allowedValues = allowedFieldValues[i];
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
}
