package bulletinboard;

import client.Client;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class BulletinBoardClientMain {

    List<Client> clients = new ArrayList<>();

    private void enterInteractiveBulletinBoardSession() {
        int listenPort = 13888;
        String menu = "Welcome to the interactive mode of this Publish-Subscribe system\n" +
                "Options:" +
                "\n\tCreate new client:\t\ttype 'create at <serverIp> <serverPort>'" +
                "\n\tSee client list:\t\ttype 'list'" +
                "\n\tRead list of messages:\t\ttype '<client number> read" +
                "\n\tScroll message list:\t\t type ENTER" +
                "\n\tChoose messages:\t\ttype '<client number> choose <messageId>" +
                "\n\tPost:\t\ttype '<client number> post <title;message>'" +
                "\n\tReply:\t\ttype '<client number> reply <replyToMessageId;title;message>'" +
                "\n\tAutomatically post random posts/replies to all clients:\t\ttype 'random posts'" +
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
                        case "random":
                            makeRandomPosts();
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

    public static void main(String[] args) {
        enterInteractiveBulletinBoardSession();
    }

}
