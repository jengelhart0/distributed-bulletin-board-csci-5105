package server;

import message.Message;
import message.Protocol;

public class ServerMessage {
    private Message message;
    private String messageId;
    private String clientId;

    ServerMessage(String message, Protocol protocol, boolean isSubscription) {
        int numExternalFields = protocol.getQueryFields().size();
        String[] parsed = protocol.parse(message);
        StringBuilder processedMessage = new StringBuilder();
        if(parsed.length == numExternalFields + 2) {
            messageId = parsed[0];
            clientId = parsed[1];

            processedMessage.append(parsed[2]);
            for(int i = 3; i < parsed.length; i++) {
                processedMessage.append(protocol.getDelimiter() + parsed);
            }
        }
        this.message = new Message(protocol, processedMessage.toString(), isSubscription);
    }



}
