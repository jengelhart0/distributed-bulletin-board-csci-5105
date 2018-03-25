import bulletinboard.BulletinBoardClient;
import bulletinboard.BulletinBoard;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public class BulletinBoardClientMain {
    private static void enterInteractiveBulletinBoardSession() {
        List<BulletinBoardClient> bbClients = new ArrayList<>();
        int listenPort = 13888;
        String menu = "Welcome to the interactive mode of this Publish-Subscribe system\n" +
                "Options:" +
                "\n\tCreate new client:\t\t'create at <serverIp> <serverPort>'" +
                "\n\tSee client list:\t\t'list'" +
                "\n\tRead list of messages:\t\t'<client number> read" +
                "\n\tScroll message list:\t\t'<client number> s" +
                "\n\tChoose messages:\t\t'<client number> choose <messageId>" +
                "\n\tPost:\t\t'<client number> post <title;message>'" +
                "\n\tReply:\t\t'<client number> reply <replyToMessageId;title;message>'" +
                "\n\tMove client to a different server:\t\t'<client number> move <new server IP> <new server Port>'" +
                "\n\tAutomatically post random posts/replies to all bbClients:\t\t'testposts <num posts/replies per client>'" +
                "\n\nSee this menu again: type 'menu'";

        try (Scanner reader = new Scanner(System.in)) {
            System.out.println(menu);
            String[] parsedInput;
            String firstWord;
            boolean terminate = false;
            while(true) {
                if(terminate) {
                    break;
                }
                try {
                    String input = reader.nextLine();
                    parsedInput = input.split(" ");

                    firstWord = parsedInput[0];

                    switch (firstWord) {
                        case "create":
                            bbClients.add(createNewBbClient(parsedInput[1], Integer.parseInt(parsedInput[2]), listenPort++));
                            System.out.println("Created client " + Integer.toString(bbClients.size() - 1));
                            break;
                        case "list":
                            System.out.println("Existing bbClients");
                            for (int i = 0; i < bbClients.size(); i++) {
                                System.out.println("\tClient :" + Integer.toString(i));
                            }
                            break;
                        case "testposts":
                            makeRandomPosts(bbClients, Integer.parseInt(parsedInput[1]));
                        case "menu":
                            System.out.println(menu);
                            break;
                        case "terminate":
                            System.out.println("Terminating.");
                            terminate = true;
                        default:
                            int clientIdx = getClientIdxIfInputValid(parsedInput, bbClients);
                            if (clientIdx >= 0) {
                                executeInteractiveBbClientCommand(bbClients, clientIdx, parsedInput);
                            }
                            break;
                    }
                } catch (IOException | NotBoundException e) {
                    System.out.println("Attempt to create client failed: " + e.toString());
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.out.println("Wrong number of arguments. Review menu options.");
                }
            }
        } finally {
            for(BulletinBoardClient bbClient : bbClients) {
                bbClient.terminateClient();
            }
        }
    }

    private static int getClientIdxIfInputValid(String[] parsedInput, List<BulletinBoardClient> bbClients) {
        try {
            int clientIdx = Integer.parseInt(parsedInput[0]);
            if (clientIdx < 0 || clientIdx >= bbClients.size()) {
                System.out.println("Invalid client number. Disappointing.");
                return -1;
            }
            if (parsedInput.length < 2) {
                System.out.println("Too few arguments. Review menu options.");
                return -1;
            }
            return clientIdx;
        } catch (NumberFormatException e) {
            System.out.println("Invalid command. Review menu options");
            return -1;
        }
    }

    private static void executeInteractiveBbClientCommand(List<BulletinBoardClient> bbClients, int clientIdx, String[] parsedInput) {
        try {
            BulletinBoardClient bbClient = bbClients.get(clientIdx);
            String command = parsedInput[1];
            boolean success;
            String rawMessage, title, replyToMessageId;
            switch (command) {
                case "post":
                    title = parsedInput[2];
                    rawMessage = String.join(
                        " ", Arrays.asList(parsedInput).subList(3, parsedInput.length));
                    success = bbClient.post(title, rawMessage);
                    break;
                case "reply":
                    replyToMessageId = parsedInput[2];
                    title = parsedInput[3];
                    rawMessage = String.join(
                        " ", Arrays.asList(parsedInput).subList(4, parsedInput.length));
                    success = bbClient.reply(replyToMessageId, title, rawMessage);
                    break;
                case "move":
                    String newServerIp = parsedInput[2];
                    int newServerPort = Integer.parseInt(parsedInput[3]);
                    bbClient.moveToServerAt(newServerIp, newServerPort);
                    success = true;
                    break;
                case "read":
                    bbClient.read();
                    success = true;
                    break;
                case "choose":
                    bbClient.choose(Integer.parseInt(parsedInput[2]));
                    success = true;
                    break;
                case "s":
                    bbClient.scroll();
                    success = true;
                    break;
                default:
                    System.out.println("Invalid command for client. Review menu options");
                    success = false;
            }
            if (success) {
                System.out.println("Message command made for client " + Integer.toString(clientIdx));
            } else {
                System.out.println("Message command attempt failed.");
            }
        } catch (IllegalArgumentException i) {
            System.out.println("Invalid message format. Make sure you know the protocol!");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Wrong number of arguments. Review menu options.");
        }
    }

    private static BulletinBoardClient createNewBbClient(String serverIp, int serverPort, int nextListenPort)
            throws IOException, NotBoundException {
        BulletinBoardClient newClient = new BulletinBoardClient(BulletinBoard.NUM_TO_DISPLAY);
        newClient.initializeClient(nextListenPort, serverIp, serverPort);
        System.out.println("New BB Client created and initialized at server " + serverIp + " " + serverPort);
        return newClient;
    }

    private static void makeRandomPosts(List<BulletinBoardClient> bbClients, int numPostsAndRepliesPerClient) {
        int simulatedMessageId = 0;
        for(int i = 0; i < numPostsAndRepliesPerClient; i++) {
            String title = "Test title " + i;
            String message = "Mindblowing insight " + i;
            for(int j = 0; j < bbClients.size(); j++) {
                bbClients.get(j).post(title, message);
                System.out.println("Making test post for client " + j +
                        " with title " + title + " after simulated network delay");
                simulateRandomNetworkDelay(10);
                simulatedMessageId++;
            }
            for(int j = 0; j < bbClients.size(); j++) {
                String randomMessageToReplyTo = String.valueOf(
                        ThreadLocalRandom.current().nextInt(0, simulatedMessageId));
                bbClients.get(j).reply(randomMessageToReplyTo, title, message);
                System.out.println("Making test reply for client " + j +
                        " with title " + title + " to original post " + randomMessageToReplyTo +
                        " after simulated network delay");
                simulateRandomNetworkDelay(10);
                simulatedMessageId++;
            }
        }
    }

    public static void simulateRandomNetworkDelay(int maxDelay) {
        int randomDelay = ThreadLocalRandom.current().nextInt(0, maxDelay);
        try {
            Thread.sleep(randomDelay);
        } catch (InterruptedException e) {
            System.out.println("Thread interrupting while sleeping in simulateRandomNetworkDelay.");
        }
    }

    public static void main(String[] args) {
        enterInteractiveBulletinBoardSession();
    }
}
