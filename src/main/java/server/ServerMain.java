import communicate.CommunicateArticle;
import server.ReplicatedPubSubServer;

import java.io.IOException;

import java.net.InetAddress;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerMain {
    private static final Logger LOGGER = Logger.getLogger( ReplicatedPubSubServer.class.getName() );

    private static ReplicatedPubSubServer startServer(String remoteServerIp) throws IOException {
        ReplicatedPubSubServer replicatedPubSubServer =
                new ReplicatedPubSubServer.Builder(CommunicateArticle.ARTICLE_PROTOCOL, InetAddress.getByName(remoteServerIp))
                .build();

        replicatedPubSubServer.initialize();
        return replicatedPubSubServer;
    }

    private static boolean teardownTest(String remoteServerIp) throws IOException, InterruptedException {
        ReplicatedPubSubServer server = startServer(remoteServerIp);
        Thread.sleep(10000);
        server.cleanup();
        return true;
    }

    private static Set<String> getListTest(String remoteServerIp) throws IOException, InterruptedException {
        ReplicatedPubSubServer server = startServer(remoteServerIp);
        Thread.sleep(3000);
        Set<String> serverList = server.getListOfServers();
        if (serverList != null) {
            System.out.print("Server List: ");
            for (String serverString: serverList) {
                System.out.print(serverString + " ");
            }
        }
        return serverList;
    }

    public static void main(String[] args) throws IOException, InterruptedException{

        if (args.length < 1) {
            LOGGER.log(Level.SEVERE, "Need to pass single argument IPv4 address of server.");
            return;
        }
        String remoteServerIp = args[0];
        LOGGER.log(Level.INFO, "Attempting to create server at " + remoteServerIp);

        if (args.length == 2) {
            String registryServerTest = args[1];
            switch (registryServerTest) {
                case "testTeardown":
                    teardownTest(remoteServerIp);
                    break;
                case "testGetList":
                    getListTest(remoteServerIp);
                    break;
            }

        } else {
            if (args.length > 2) {
                LOGGER.log(Level.SEVERE, "Usage: java ServerMain <serverIp> <test name>");
                return;
            }
            startServer(remoteServerIp);
        }
    }
}
