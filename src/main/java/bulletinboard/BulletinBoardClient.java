package bulletinboard;

import client.Client;
import communicate.Communicate;
import message.Message;
import message.Protocol;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.*;

public class BulletinBoardClient {
    private Protocol bbProtocol;
    private Client client;

    private int numMessagesToDisplay;
    private int nextToScroll;

    private List<Post> readOrderMessages;
    private Map<Integer, Post> idToMessages;
    private final Object readOrderLock = new Object();

    public BulletinBoardClient(int numMessagesToDisplay) throws IOException, NotBoundException {
        this.bbProtocol =  new Protocol(
                Arrays.asList("replyTo", "title"),
                Arrays.asList(new String[]{""}, new String[]{""}),
                ";",
                "",
                256);
        this.numMessagesToDisplay = numMessagesToDisplay;
    }

    public void initializeClient(int nextListenPort, String serverIp, int serverPort) throws IOException, NotBoundException {
        while(this.client == null) {
            this.client = new Client(bbProtocol, nextListenPort);
        }
        client.initializeRemoteCommunication(serverIp, serverPort, Communicate.NAME);
    }

    public boolean post(String title, String message) {
        return bbWrite(bbProtocol.getWildcard(), title, message);
    }

    public boolean reply(String replyToMessageId, String title, String message) {
        return bbWrite(replyToMessageId, title, message);
    }

    private boolean bbWrite(String replyToMessageId, String title, String message) {
        String delim = bbProtocol.getDelimiter();
        String rawMessage = replyToMessageId + delim + title + delim + message;
        Message post = new Message(bbProtocol, rawMessage, false);
        return client.publish(post);
    }

    public void read() {
        String wc = bbProtocol.getWildcard();
        Map<Integer, List<Integer>> replyMap = new HashMap<>();
        Map<Integer, Post> idToMessages = new HashMap<>();
        List<Integer> nonReplies = new LinkedList<>();
        List<Message> rawResults = client.retrieve(
                new Message(bbProtocol, wc + bbProtocol.getDelimiter() + wc, true));

        int messageId, replyToId;
        Post post;
        for(Message message: rawResults) {
            post = new Post(message);
            messageId = post.getMessageId();
            replyToId = post.getReplyToId();

            if (replyToId == -1) {
                nonReplies.add(messageId);
            } else {
                replyMap.getOrDefault(replyToId, new LinkedList<>()).add(messageId);
            }
            idToMessages.put(messageId, post);
        }
        List<Post> newReadOrderList = generateReadOrder(nonReplies, replyMap, idToMessages);
        synchronized (readOrderLock) {
            this.readOrderMessages = newReadOrderList;
            this.idToMessages = idToMessages;
        }

        printFirstMessagesInReadOrder();
    }

    private List<Post> generateReadOrder(List<Integer> nonReplies,
                                         Map<Integer, List<Integer>> replyMap,
                                         Map<Integer, Post> idToMessages) {
        List<Post> newReadOrder = new LinkedList<>();
        addMessagesToReadOrderDfs(nonReplies, replyMap, idToMessages, newReadOrder, 0);
        return newReadOrder;
    }

    private void addMessagesToReadOrderDfs(List<Integer> messages,
                                           Map<Integer, List<Integer>> replyMap,
                                           Map<Integer, Post> idToMessages,
                                           List<Post> newReadOrder,
                                           int printDepth) {
        for(int messageId: messages) {
            Post post = idToMessages.get(messageId);
            post.setPrintDepth(printDepth);
            newReadOrder.add(post);
            List<Integer> repliesToMessage = replyMap.getOrDefault(messageId, new LinkedList<>());
            addMessagesToReadOrderDfs(repliesToMessage, replyMap, idToMessages, newReadOrder, printDepth + 1);
        }
    }

    private void printFirstMessagesInReadOrder() {
        int messageNum;
        synchronized (readOrderLock) {
            for (messageNum = 0;
                 messageNum < numMessagesToDisplay && messageNum < readOrderMessages.size();
                 messageNum++)
            {

                Post postToPrint = readOrderMessages.get(messageNum);
                printPostTitle(postToPrint);
            }
            this.nextToScroll = messageNum;
            if(messageNum == readOrderMessages.size()) {
                System.out.println("END");
            }
        }
    }

    private void printPostTitle(Post postToPrint) {
        printPost(
                postToPrint.getMessageId(),
                postToPrint.getTitle(),
                "",
                postToPrint.getPrintDepth()
        );
    }

    private void printWholePost(Post postToPrint) {
        printPost(
                postToPrint.getMessageId(),
                postToPrint.getTitle(),
                postToPrint.getMessage(),
                postToPrint.getPrintDepth()
        );
    }

    private void printPost(int messageId, String title, String message, int depth) {
        StringBuilder displayString = new StringBuilder();
        for(int d = 0; d < depth; d++) {
            displayString.append("\t");
        }
        displayString.append(messageId)
                .append("  ")
                .append(title)
                .append("\n")
                .append(message);
        System.out.println(displayString.toString());

    }

    public void scroll() {
        synchronized (readOrderLock) {
            if (nextToScroll != -1 && nextToScroll < readOrderMessages.size()) {
                printPostTitle(readOrderMessages.get(nextToScroll++));
            }
        }
    }

    public void choose(int messageId) {
        printWholePost(idToMessages.get(messageId));
    }



}
