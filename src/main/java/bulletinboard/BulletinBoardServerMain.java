import bulletinboard.BulletinBoard;
import communicate.Communicate;
import server.ReplicatedPubSubServer;
import server.SequentialConsistency;

import java.io.IOException;
import java.net.InetAddress;

public class BulletinBoardServerMain {
    private static int nextHeartbeatPort = 9453;

    public static void main(String[] args) {
        try {
            if(args.length != 4) {
                System.out.println("Wrong number of arguments.");
                System.out.println("\tUsage: java BulletinBoardServerMain " +
                        "<serverIp> <serverPort> <heartbeatPort> <registryServerIp> <registryServerPort> " +
                        "<consistency model> <total num servers planned>");
                System.out.println("\tValid consistency models:'sequential', 'readyourwrites', or 'quorum'");
                return;
            }
            ReplicatedPubSubServer.Builder replicatedPubSubServerBuilder =
                    new ReplicatedPubSubServer.Builder(BulletinBoard.BBPROTOCOL,
                                                       InetAddress.getByName(args[0]),
                                                       Integer.parseInt(args[3]))
                            .name(Communicate.NAME)
                            .serverPort(Integer.parseInt(args[1]))
                            .heartbeatPort(nextHeartbeatPort++)
                            .shouldRetrieveMatchesAutomatically(false);

            switch(args[2]) {
                case "sequential":
                    replicatedPubSubServerBuilder.consistencyPolicy(new SequentialConsistency());
                    break;
                case "readyourwrites":
                    replicatedPubSubServerBuilder.consistencyPolicy(new SequentialConsistency());
                    break;
                case "quorum":
                    replicatedPubSubServerBuilder.consistencyPolicy(new SequentialConsistency());
                    break;
                default:
                    System.out.println("Consistency model not specified correctly.");
                    System.out.println("\tValid consistency models:'sequential', 'readyourwrites', or 'quorum'");
                    return;
            }
            ReplicatedPubSubServer replicatedPubSubServer = replicatedPubSubServerBuilder.build();
            replicatedPubSubServer.initialize();
        } catch (IOException e) {
            System.out.println("Failed to create new replicated publish-subscribe server.");
            e.printStackTrace();
        }
    }
}
