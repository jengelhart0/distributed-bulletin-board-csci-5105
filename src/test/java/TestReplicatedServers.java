import message.Protocol;
import org.junit.Before;
import org.junit.Test;
import server.ReplicatedPubSubServer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class TestReplicatedServers {

    int numTestServers = 5;
    String testIp = "localhost";
    Map<Integer, ReplicatedPubSubServer> replicatedServers = new HashMap<>();
    String serverInterfaceName = "CommunicateTest";
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
            256);

    @Before
    public void setUpReplicatedBulletinBoardServers() throws UnknownHostException {
        int nextServerPort = 1099;

        for (int i = 0; i < numTestServers; i++) {
            ReplicatedPubSubServer testReplicatedPubSubServer =
                    new ReplicatedPubSubServer.Builder(testProtocol1, InetAddress.getByName(testIp))
                            .serverPort(nextServerPort)
                            .shouldRetrieveMatchesAutomatically(false)
                            .build();
            testReplicatedPubSubServer.initialize();
            replicatedServers.put(nextServerPort++, testReplicatedPubSubServer);
        }
    }

    @Test
    public void testInitializeAndAgreeOnCoordinator() throws UnknownHostException, RemoteException {

        boolean allServersAgreeOnCoordinator = true;
        ReplicatedPubSubServer[] testServers = (ReplicatedPubSubServer[]) replicatedServers.values().toArray();
        String firstServersCoordinator = testServers[0].getCoordinator().getThisServersIpPortString();
        for(ReplicatedPubSubServer server: testServers) {
            String currentServersCoordinator = server.getCoordinator().getThisServersIpPortString();
            if(!currentServersCoordinator.equals(firstServersCoordinator)) {
                allServersAgreeOnCoordinator = false;
            }
        }

        assertTrue(allServersAgreeOnCoordinator);
    }


}
