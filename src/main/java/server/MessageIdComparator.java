package server;

import message.Protocol;

import java.util.Comparator;

public class MessageIdComparator implements Comparator<String> {
    Protocol protocol;

    public MessageIdComparator(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public int compare(String s1, String s2) {
        if(s1.isEmpty() || s2.isEmpty()) {
            return s1.compareTo(s2);
        }
        int compareVal = protocol.getMessageIdAsInt(s1) - protocol.getMessageIdAsInt(s2);
        if(compareVal == 0 && !s1.equals(s2)) {
            throw new IllegalArgumentException("MessageIdComparator.compare(): different messages should not share messageId!");
        }
        return compareVal;
    }
}
