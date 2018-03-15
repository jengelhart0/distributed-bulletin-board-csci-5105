import message.Protocol;
import org.junit.After;
import org.junit.Before;
import server.ReplicatedPubSubServer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReplicatedServerSetup {

    Map<Integer, ReplicatedPubSubServer> replicatedServers = new ConcurrentHashMap<>();
    List<Integer> serverPorts;

    String serverInterfaceName = "CommunicateTest";
    int numTestServers = 7;
    String testServerIp = "localhost";
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

    Protocol testProtocol1 = new Protocol(
            Collections.singletonList("replyTo"),
            Collections.singletonList(new String[]{""}),
            ";",
            "",
            512);

    @Before
    public void setUpReplicatedServers() throws UnknownHostException, InterruptedException {

        int nextServerPort = 1099;
        int nextHearbeatPort = 9453;

        for (int i = 0; i < numTestServers; i++) {
            ReplicatedPubSubServer testReplicatedPubSubServer =
                    new ReplicatedPubSubServer.Builder(testProtocol1, InetAddress.getByName(testServerIp))
                            .name(serverInterfaceName)
                            .serverPort(nextServerPort)
                            .heartbeatPort(nextHearbeatPort++)
                            .shouldRetrieveMatchesAutomatically(false)
                            .build();
            testReplicatedPubSubServer.initialize();
            replicatedServers.put(nextServerPort++, testReplicatedPubSubServer);
        }
        serverPorts = new ArrayList<>(replicatedServers.keySet());
        Thread.sleep(100);
    }

    @After
    public void cleanupServers() throws InterruptedException {
        for(ReplicatedPubSubServer server: replicatedServers.values()) {
            server.cleanup();
        }
//        Thread.sleep(numTestServers * 500);
    }
}
