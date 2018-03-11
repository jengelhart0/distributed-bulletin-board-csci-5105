import client.Client;
import message.Protocol;
import org.junit.Before;
import org.junit.Test;
import server.ReplicatedPubSubServer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class TestReplicatedBulletinBoardServers {

    int numTestServers = 5;
    String testIp = "localhost";
    Map<Integer, ReplicatedPubSubServer> replicatedServers = new HashMap<>();
    String serverInterfaceName = "CommunicateBulletinBoard";
    Protocol bulletinProtocol = new Protocol(
            new String[]{"messageId", "clientId", "replyTo"},
            new String[][]{
                    new String[]{""},
                    new String[]{""},
                    new String[]{""},
            },
            ";",
            "",
            256);

    @Before
    public void setUpReplicatedBulletinBoardServers() throws UnknownHostException {
        int nextServerPort = 1099;

        for (int i = 0; i < numTestServers; i++) {
            ReplicatedPubSubServer testReplicatedPubSubServer =
                    new ReplicatedPubSubServer.Builder(bulletinProtocol, InetAddress.getByName(testIp))
                            .serverPort(nextServerPort)
                            .shouldRetrieveMatchesAutomatically(false)
                            .build();
            testReplicatedPubSubServer.initialize();
            replicatedServers.put(nextServerPort++, testReplicatedPubSubServer);
        }
    }
}
