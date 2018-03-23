import message.Protocol;
import org.junit.After;
import org.junit.Before;
import server.ReadYourWritesPolicy;
import server.ReplicatedPubSubServer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReadYourWritesServerSetup {
    private static final Logger LOGGER = Logger.getLogger( ReadYourWritesServerSetup.class.getName() );

    Map<Integer, ReplicatedPubSubServer> replicatedServers = new ConcurrentHashMap<>();
    List<Integer> serverPorts;

    String serverInterfaceName;
    int numTestServers;
    String testServerIp;
    Protocol testProtocol1;

    ReadYourWritesServerSetup() {
        serverInterfaceName = "CommunicateTest";
        numTestServers = 5;
        try {
            testServerIp = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "UnknownHostException while trying to set up servers");
            e.printStackTrace();
            return;
        }
        System.out.println(testServerIp);

        testProtocol1 = new Protocol(
                Collections.singletonList("replyTo"),
                Collections.singletonList(new String[]{""}),
                ";",
                "",
                64);

    }
//    Protocol bulletinProtocol = new Protocol(
//            new String[]{"messageId", "replyTo", "clientId"},
//            new String[][]{
//                    new String[]{""},
//                    new String[]{""},
//                    new String[]{""},
//            },
//            ";",
//            "",
//            256);


    @Before
    public void setUpReplicatedServers() throws UnknownHostException {

        int nextServerPort = 1099;
        int nextHearbeatPort = 9453;

        for (int i = 0; i < numTestServers; i++) {
            ReplicatedPubSubServer testReplicatedPubSubServer =
                    new ReplicatedPubSubServer.Builder(testProtocol1, InetAddress.getByName(testServerIp), numTestServers)
                            .name(serverInterfaceName)
                            .serverPort(nextServerPort)
                            .heartbeatPort(nextHearbeatPort++)
                            .shouldRetrieveMatchesAutomatically(false)
                            .consistencyPolicy(new ReadYourWritesPolicy())
                            .build();
            testReplicatedPubSubServer.initialize();
            replicatedServers.put(nextServerPort++, testReplicatedPubSubServer);
        }
        serverPorts = new ArrayList<>(replicatedServers.keySet());
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Thread interrupted while sleeping in setUpReplicatedServers.");
        }
    }

    @After
    public void cleanupServers() {
        for(ReplicatedPubSubServer server: replicatedServers.values()) {
            server.cleanup();
        }
//        Thread.sleep(numTestServers * 500);
    }
}
