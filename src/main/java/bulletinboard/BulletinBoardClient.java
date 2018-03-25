package bulletinboard;

import client.Client;
import communicate.Communicate;
import message.Message;
import message.Protocol;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BulletinBoardClient {
    private static final Logger LOGGER = Logger.getLogger( BulletinBoardClient.class.getName() );

    private Protocol bbProtocol;
    private Client client;
    private String communicationName;

    private int numMessagesToDisplay;
    private int nextToScroll;

    private List<Post> readOrderMessages;
    private Map<Integer, Post> idToMessages;
    private final Object readOrderLock = new Object();

    public BulletinBoardClient(int numMessagesToDisplay) throws IOException, NotBoundException {
        this.bbProtocol = BulletinBoard.BBPROTOCOL;
        this.numMessagesToDisplay = numMessagesToDisplay;
        this.communicationName = Communicate.NAME;
    }

    public void initializeClient(int nextListenPort, String serverIp, int serverPort) throws IOException, NotBoundException {
        while(this.client == null) {
            this.client = new Client(bbProtocol, nextListenPort);
        }
        client.initializeRemoteCommunication(serverIp, serverPort, communicationName);
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
                new Message(bbProtocol, bbProtocol.getRetrieveAllQuery(), true));

        int messageId, replyToId;
        Post post;
        for(Message message: rawResults) {
            post = new Post(message);
            messageId = post.getMessageId();
            replyToId = post.getReplyToId();

            if (replyToId == -1) {
                nonReplies.add(messageId);
            } else {
                List<Integer> replies = replyMap.getOrDefault(replyToId, new LinkedList<>());
                replies.add(messageId);
                replyMap.put(replyToId, replies);
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
            if(nextToScroll >= readOrderMessages.size()) {
                System.out.println("END");
            } else {
                System.out.println(readOrderMessages.size() - nextToScroll + " MORE TO SCROLL");
            }
        }
    }

    private void printPostTitle(Post postToPrint) {
        if(postToPrint != null) {
            printPost(
                    postToPrint.getMessageId(),
                    postToPrint.getTitle(),
                    "",
                    postToPrint.getPrintDepth()
            );
        } else {
            System.out.println("That choice does not exist at the specified client");
        }
    }

    private void printWholePost(Post postToPrint) {
      if(postToPrint != null) {
        printPost(
                postToPrint.getMessageId(),
                postToPrint.getTitle(),
                postToPrint.getMessage(),
                postToPrint.getPrintDepth()
        );
      } else {
          System.out.println("That choice does not exist at the specified client");
      }
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
        int numToScroll = 5;
        int numScrolled = 0;
        synchronized (readOrderLock) {
            while (nextToScroll != -1
                    && nextToScroll < readOrderMessages.size()
                    && numScrolled < numToScroll) {
                printPostTitle(readOrderMessages.get(nextToScroll++));
                numScrolled++;
                nextToScroll++;
            }

            if(nextToScroll >= readOrderMessages.size()) {
                System.out.println("END");
            } else {
                System.out.println(readOrderMessages.size() - nextToScroll + " MORE TO SCROLL");
            }

        }
    }

    public void choose(int messageId) {
        printWholePost(idToMessages.get(messageId));
    }

    public void moveToServerAt(String serverIp, int serverPort) {
        try {
            if (client.leave()) {
                client.initializeRemoteCommunication(serverIp, serverPort, communicationName);
            }
        } catch (IOException | NotBoundException e) {
            LOGGER.log(Level.SEVERE, "moveToServerAt failed:");
            e.printStackTrace();
        }
    }

    public void terminateClient() {
        client.terminateClient();
    }
}
