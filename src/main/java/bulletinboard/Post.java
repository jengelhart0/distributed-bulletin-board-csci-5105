package bulletinboard;

import message.Message;

class Post {

    private int messageId;
    private int clientId;
    private int replyToId;
    private String title;
    private String message;
    private int printDepth;

    Post(Message message) {
        setFields(message);
    }

    private void setFields(Message message) {
        String[] parsedFields = message.getProtocol().parse(message.asRawMessage());
        if(parsedFields.length != 5) {
            throw new IllegalArgumentException("BulletinBoard post had wrong number of fields");
        }
        this.messageId = Integer.parseInt(parsedFields[0]);
        this.clientId = Integer.parseInt(parsedFields[1]);
        this.replyToId = parsedFields[2].equals("") ? -1 : Integer.parseInt(parsedFields[2]);
        this.title = parsedFields[3];
        this.message = parsedFields[4];
        this.printDepth = 0;
    }

    int getMessageId() {
        return messageId;
    }

    int getClientId() {
        return clientId;
    }

    int getReplyToId() {
        return replyToId;
    }

    String getTitle() {
        return title;
    }

    String getMessage() {
        return message;
    }

    int getPrintDepth() {
        return printDepth;
    }

    void setPrintDepth(int depth) {
        this.printDepth = depth;
    }
}
