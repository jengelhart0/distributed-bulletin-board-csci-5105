import client.Client;

import java.io.IOException;
import java.rmi.NotBoundException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import communicate.Communicate;
import communicate.CommunicateArticle;
import message.Message;
import message.Protocol;

public class ClientMain {
    private static final Logger LOGGER = Logger.getLogger( ClientMain.class.getName() );

    private static Client createNewClient(String remoteServerIp, int listenPort) throws IOException, NotBoundException {
        Client client = new Client(CommunicateArticle.ARTICLE_PROTOCOL, listenPort);
//        System.out.println("Created client at listenport" + listenPort);
        client.initializeRemoteCommunication(remoteServerIp, CommunicateArticle.REMOTE_OBJECT_PORT, Communicate.NAME);
//        System.out.println("Initialized client at listenport" + listenPort);
        return client;
    }

    private static boolean validateReceivedMessages(List<Message> received, String[] expected, Protocol protocol) {

        Set<String> receivedMessages = new HashSet<>();

        for (Message message : received) {
            receivedMessages.add(message.withoutInternalFields());
        }

        Set<String> expectedSet = new HashSet<>();

        for (String expectedString: expected) {
            expectedSet.add(protocol.padMessage(expectedString));
        }


        int expectedSize = expectedSet.size();
        int receivedSize = receivedMessages.size();

        expectedSet.retainAll(receivedMessages);

        int intersectSize = expectedSet.size();

        return (expectedSize == receivedSize) && (receivedSize == intersectSize);
    }

    private static void runAllTests(String remoteServerIp, Protocol protocol) throws  IOException, NotBoundException, InterruptedException {
        System.out.println("Running all tests...");

        String[] publications1 =
                {"Science;Someone;UMN;content 1 is great!", "Sports;Me;Reuters;content2", "Lifestyle;Jane;YourFavoriteMagazine;content3",
                 "Entertainment;Someone;Reuters;content4", "Business;Jane;The Economist;content5", "Technology;Jack;Wired;content6",
                 "Entertainment;Claus;Reuters;content7", "Business;Albert;The Economist;content8", "Business;Albert;Extra;content9",
                 ";;The Economist;content10", "Science;;;content11", ";Jack;;content12", "Sports;Me;;content13",
                 "Lifestyle;;Jane;my spaced content14", "Business;Jack;;content15"};

        String[] subscriptions1 =
                {"Science;Someone;UMN;", "Sports;Me;Reuters;", "Lifestyle;Jane;YourFavoriteMagazine;",
                 "Entertainment;Someone;Reuters;", "Business;Jane;The Economist;", ";Jack;;"};

        String[] expected1 =
                {publications1[0], publications1[1], publications1[2], publications1[3], publications1[4],
                 publications1[5], publications1[11], publications1[14]};

        Thread.sleep(3000);

        String resultMessage;
//        if(testSingleSubscriberSinglePublisher(remoteServerIp, protocol, publications1, subscriptions1, expected1)) {
//            resultMessage = "Test single subscriber, single publisher PASSED.";
//        } else {
//            resultMessage = "Test single subscriber, single publisher FAILED.";
//        }
//        System.out.println(resultMessage);
//
//
//        if(testSinglePublishMultipleSubscribers(remoteServerIp, 10, protocol, publications1, subscriptions1, expected1)) {
//            resultMessage = "Test single publisher, multiple subscribers PASSED.";
//        } else {
//            resultMessage = "Test single publisher, multiple subscribers FAILED.";
//        }
//        System.out.println(resultMessage);

        if(testSingleRetrieve(remoteServerIp, protocol)) {
            resultMessage = "Test single retrieve PASSED.";
        } else {
            resultMessage = "Test single retrieve FAILED.";
        }
        System.out.println(resultMessage);

        if(testHighLoad(remoteServerIp, 300, protocol, publications1, subscriptions1)) {
            resultMessage = "Test high load PASSED.";
        } else {
            resultMessage = "Test high load FAILED.";
        }
        System.out.println(resultMessage);

        String[] invalidPublications =
                {"Spurts;Me;Reuters;wrongType", "Entertainment;Someone;Reuters;;;",
                        ";;;", "Entertainment:test:test:test"};

        if(testInvalid(invalidPublications, protocol, false)) {
            resultMessage = "Test invalid publications PASSED.";
        } else {
            resultMessage = "Test invalid publications FAILED.";
        }
        System.out.println(resultMessage);

        String[] invalidSubscriptions =
                {";;;invalidContents1", "Teknology;;;", ";stillNoContentsAllowed;;invalidContents2", "this;isn't;allowed;;"};

        if(testInvalid(invalidSubscriptions, protocol, true)) {
            resultMessage = "Test invalid subscriptions PASSED.";
        } else {
            resultMessage = "Test invalid subscriptions FAILED.";
        }
        System.out.println(resultMessage);

        if(testUnsubscribe(protocol, remoteServerIp)) {
            resultMessage = "Test unsubscribe PASSED.";
        } else {
            resultMessage = "Test unsubscribe FAILED.";
        }
        System.out.println(resultMessage);

        if(testLeave(remoteServerIp)) {
            System.out.println("Test leave PASSED.");
        } else {
            System.out.println("Test leave FAILED.");
        }

    }

    private static void addSubscriptions(String[] subscriptions, Protocol protocol, Client client)
            throws  IOException, NotBoundException {

        for (String subscription : subscriptions) {
            client.subscribe(new Message(protocol, subscription, true));
        }
    }

    private static void makePublications(String[] publications, Protocol protocol, Client client)
            throws  IOException, NotBoundException {

        for (String publication : publications) {
            client.publish(new Message(protocol, publication, false));
        }
    }

    private static boolean testSingleRetrieve(String remoteServerIp, Protocol protocol) throws IOException, NotBoundException, InterruptedException {

        int listenPort = 8888;

        Client publisher = createNewClient(remoteServerIp, listenPort++);
        String[] publications = new String[]{"Sports;;;testing retrieve 1"};
        makePublications(publications, protocol, publisher);

        Client retriever = createNewClient(remoteServerIp, listenPort);

        List<Message> receivedMessages = retriever.retrieve(new Message(protocol, "Sports;;;", true));

        boolean passed = validateReceivedMessages(receivedMessages, publications, protocol);

        retriever.terminateClient();
        publisher.terminateClient();

        return passed;
    }

    private static boolean testSingleSubscriberSinglePublisher(String remoteServerIp, Protocol protocol, String[] publications,
            String[] subscriptions, String[] expected) throws IOException, NotBoundException, InterruptedException {

        int listenPort = 8888;

        Client subscriber = createNewClient(remoteServerIp, listenPort++);
        addSubscriptions(subscriptions, protocol, subscriber);

        Client publisher = createNewClient(remoteServerIp, listenPort);
        makePublications(publications, protocol, publisher);

        Thread.sleep(6000);

        List<Message> receivedMessages = subscriber.getCurrentMessageFeed();

        boolean passed = validateReceivedMessages(receivedMessages, expected, protocol);

        subscriber.terminateClient();
        publisher.terminateClient();

        return passed;
    }

    private static boolean testSinglePublishMultipleSubscribers(String remoteServerIp, int numSubscribers, Protocol protocol,
            String[] publications, String[] subscriptions, String[] expected) throws IOException, NotBoundException, InterruptedException {

        int listenPort = 9888;

        List<Client> subscribers = new LinkedList<>();

        for (int i = 0; i < numSubscribers; i++) {
            Client newSubscriber = createNewClient(remoteServerIp, listenPort++);
            subscribers.add(newSubscriber);
            addSubscriptions(subscriptions, protocol, newSubscriber);
        }

        Client publisher = createNewClient(remoteServerIp, listenPort);
        makePublications(publications, protocol, publisher);
        publisher.terminateClient();

        Thread.sleep(6000);

        boolean allPassed = true;
        for (Client subscriber: subscribers) {
            if (!validateReceivedMessages(subscriber.getCurrentMessageFeed(), expected, protocol)) {
                allPassed = false;
                subscriber.terminateClient();
                break;
            }
            subscriber.terminateClient();
        }
        return allPassed;
    }

    private static boolean testHighLoad(String remoteServerIp, int numClient, Protocol protocol, String[] publications,
            String[] subscriptions) throws IOException, NotBoundException, InterruptedException {
        int listenPort = 12888;

        List<Client> clients = new LinkedList<>();

        for (int i = 0; i < numClient; i++) {
            Client newClient = createNewClient(remoteServerIp, listenPort++);
            LOGGER.log(Level.FINE, "Created client " + Integer.toString(i));
            clients.add(newClient);
            addSubscriptions(subscriptions, protocol, newClient);
        }

        for (int i = 0; i < clients.size(); i++) {
            for(String publication: publications) {
                clients.get(i).publish(new Message(protocol, publication + Integer.toString(i), false));
            }
        }

        Thread.sleep(10000);

        boolean allPassed = true;
        for (Client client: clients) {
            if (client.getCurrentMessageFeed().isEmpty()) {
                allPassed = false;
                client.terminateClient();
                break;
            }
            client.terminateClient();
        }
        return allPassed;
    }

    private static boolean testInvalid(String[] invalidMessages, Protocol protocol, boolean isSubscription)
            throws IOException, NotBoundException {

        int numInvalidCaught = 0;

        for(String invalid: invalidMessages) {
            try {
                new Message(protocol, invalid, isSubscription);
            } catch (IllegalArgumentException e) {
                numInvalidCaught++;
            }
        }
        return(invalidMessages.length == numInvalidCaught);
    }

    public static boolean testLeave(String remoteServerIp) throws IOException, NotBoundException {
        int listenPort = 10888;
        Client client = createNewClient(remoteServerIp, listenPort);
        client.terminateClient();
        return true;
    }

    public static boolean testUnsubscribe(Protocol protocol, String remoteServerIp)
            throws IOException, NotBoundException, InterruptedException {

        String[] subscriptions = {"Science;Someone;UMN;", "Sports;Me;Reuters;"};
        String[] publications = {"Science;Someone;UMN;content 1 is great!", "Sports;Me;Reuters;content2"};

        int listenPort = 11888;

        Client unsubscriber = createNewClient(remoteServerIp, listenPort++);
        addSubscriptions(subscriptions, protocol, unsubscriber);

        unsubscriber.unsubscribe(new Message(protocol, subscriptions[0], true));
        unsubscriber.unsubscribe(new Message(protocol, subscriptions[1], true));

        Client publisher = createNewClient(remoteServerIp, listenPort);
        makePublications(publications, protocol, publisher);
        publisher.terminateClient();

        Thread.sleep(6000);

        boolean passed = validateReceivedMessages(unsubscriber.getCurrentMessageFeed(), new String[]{}, protocol);
        unsubscriber.terminateClient();
        return passed;
    }

    public static void enterInteractiveLoop(String remoteServerIp, Protocol protocol) throws IOException, NotBoundException {
        int listenPort = 13888;
        List<Client> clients = new ArrayList<>();
        String menu = "Welcome to the interactive mode of this Publish-Subscribe system\n" +
                "Options:" +
                "\n\tCreate new client:\t\ttype 'create'" +
                "\n\tSee client list:\t\ttype 'list'" +
                "\n\tSee client messages arrived so far:\t\ttype '<client number> retrieve" +
                "\n\tAdd subscription:\t\ttype '<client number> subscribe <valid subscription format>'" +
                "\n\tRemove subscription:\t\ttype '<client number> unsubscribe <valid subscription format>'" +
                "\n\tPublish:\t\ttype '<client number> publish <valid publication format>'" +
                "\n\tLeave:\t\ttype '<client number> leave'" +
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
                            clients.add(createNewClient(remoteServerIp, listenPort++));
                            System.out.println("Created client " + Integer.toString(clients.size() - 1));
                            break;
                        case "list":
                            System.out.println("Existing clients");
                            for (int i = 0; i < clients.size(); i++) {
                                System.out.println("\tClient :" + Integer.toString(i));
                            }
                            break;
                        case "menu":
                            System.out.println(menu);
                            break;
                        case "terminate":
                            System.out.println("Terminating.");
                            terminate = true;
                        default:
                            int clientIdx = getClientIdxIfInputValid(parsedInput, clients);
                            if (clientIdx >= 0) {
                                String command = parsedInput[1];
                                executeInteractiveClientCommand(clients, clientIdx, parsedInput);
                            }
                            break;
                    }
                } catch (IOException | NotBoundException e) {
                    System.out.println("Attempt to create client failed: " + e.toString());
                }
            }
        } finally {
            for(Client client : clients) {
                client.terminateClient();
            }
        }
    }

    private static int getClientIdxIfInputValid(String[] parsedInput, List<Client> clients) {
        try {
            int clientIdx = Integer.parseInt(parsedInput[0]);
            if (clientIdx < 0 || clientIdx >= clients.size()) {
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

    private static void executeInteractiveClientCommand(List<Client> clients, int clientIdx, String[] parsedInput) {
        try {
            Client client = clients.get(clientIdx);
            String command = parsedInput[1];
            Protocol protocol = CommunicateArticle.ARTICLE_PROTOCOL;
            boolean success;
            if (parsedInput.length >= 3) {
                String rawMessage = String.join(
                        " ", Arrays.asList(parsedInput).subList(2, parsedInput.length));
                switch (command) {
                    case "subscribe":
                        success = client.subscribe(new Message(protocol, rawMessage, true));
                        break;
                    case "unsubscribe":
                        success = client.unsubscribe(new Message(protocol, rawMessage, true));
                        break;
                    case "publish":
                        success = client.publish(new Message(protocol, rawMessage, false));
                        break;
                    default:
                        System.out.println("Invalid command for client. Review menu options");
                        success = false;
                }
                if (success) {
                    System.out.println("Message command with message " + rawMessage
                            + " made for client " + Integer.toString(clientIdx));
                } else {
                    System.out.println("Message command attempt failed.");
                }

            } else {
                switch (command) {
                    case "retrieve":
                        System.out.println("Unread messages that have arrived for client " + Integer.toString(clientIdx));
                        List<Message> feed = client.getCurrentMessageFeed();
                        for (Message m : feed) {
                            System.out.println("\t" + m.asRawMessage());
                        }
                        break;
                    case "leave":
                        if(client.leave()) {
                            System.out.println("Leave command accepted.");
                            clients.remove(client);
                        } else {
                            System.out.println("Leave command failed.");
                        }
                        break;
                    default:
                        System.out.println("Invalid command for client. Review menu options");
                }
            }
        } catch (IllegalArgumentException i) {
            System.out.println("Invalid message format. Make sure you know the protocol!");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Wrong number of arguments. Review menu options.");
        }
    }

    public static void main(String[] args) throws IOException, NotBoundException, InterruptedException {

        if (args.length < 1) {
            LOGGER.log(Level.SEVERE, "Need to pass single argument IPv4 address of server.");
            return;
        }

        // public 'server' ip is 73.242.4.186. Testing localhost just to get it up and going.
        String remoteServerIp = args[0];
        LOGGER.log(Level.INFO, "Attempting to communicate with server at " + remoteServerIp);

        if (args.length == 2 && args[1].equals("runAllTests")) {
            runAllTests(remoteServerIp, CommunicateArticle.ARTICLE_PROTOCOL);
        } else {
            enterInteractiveLoop(remoteServerIp, CommunicateArticle.ARTICLE_PROTOCOL);
        }
    }
}
