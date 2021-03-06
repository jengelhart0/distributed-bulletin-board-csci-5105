import bulletinboard.BulletinBoard;
import communicate.Communicate;
import server.ReadYourWritesPolicy;
import server.ReplicatedPubSubServer;
import server.SequentialConsistency;
import server.QuorumConsistency;

import java.io.IOException;
import java.net.InetAddress;

public class BulletinBoardServerMain {
    private static int nextHeartbeatPort = 9453;

    public static void main(String[] args) {
        try {
            if(args.length != 6) {
                System.out.println("Wrong number of arguments.");
                System.out.println("\tUsage: java BulletinBoardServerMain " +
                        "<serverIp> <serverPort> <heartbeatPort> <registryServerIp> " +
                        "<consistency model> <total num servers planned>");
                System.out.println("\tValid consistency models:'sequential', 'readyourwrites', or 'quorum'");
                return;
            }
            if(args[0].contains("127") || args[3].contains("127")) {
              System.out.println("Do NOT use loopback address for server or registry server:\n" +
                      "use external IP, otherwise unexpected behavior will result.");
            }

            ReplicatedPubSubServer.Builder replicatedPubSubServerBuilder =
                    new ReplicatedPubSubServer.Builder(BulletinBoard.BBPROTOCOL,
                                                       InetAddress.getByName(args[0]),
                                                       Integer.parseInt(args[5]))
                            .name(Communicate.NAME)
                            .serverPort(Integer.parseInt(args[1]))
                            .heartbeatPort(Integer.parseInt(args[2]))
                            .registryServerAddress(args[3])
                            .shouldRetrieveMatchesAutomatically(false);
            System.out.println(args[4]);
            switch(args[4]) {
                case "sequential":
                    replicatedPubSubServerBuilder.consistencyPolicy(new SequentialConsistency());
                    break;
                case "readyourwrites":
                    replicatedPubSubServerBuilder.consistencyPolicy(new ReadYourWritesPolicy());
                    break;
               case "quorum":
                   int numTestServers = Integer.parseInt(args[5]);
                   int writeQuorum = numTestServers / 2 + 1;
                   int readQuorum = numTestServers + 1 - writeQuorum;
                   replicatedPubSubServerBuilder.consistencyPolicy(new QuorumConsistency(numTestServers, readQuorum, writeQuorum));
                   break;
                default:
                    System.out.println("Consistency model not specified correctly.");
                    System.out.println("\tValid consistency models: sequential, readyourwrites, or quorum");
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
