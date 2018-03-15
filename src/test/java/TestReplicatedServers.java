import communicate.Communicate;
import message.Protocol;
import org.junit.Before;
import org.junit.Test;
import server.ReplicatedPubSubServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertTrue;

public class TestReplicatedServers {

    private Map<Integer, ReplicatedPubSubServer> replicatedServers = new ConcurrentHashMap<>();


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
    public void setUpReplicatedServers() throws UnknownHostException, InterruptedException {
        int numTestServers = 5;
        String testIp = "localhost";
        int nextServerPort = 1099;
        int nextHearbeatPort = 9453;

        for (int i = 0; i < numTestServers; i++) {
            ReplicatedPubSubServer testReplicatedPubSubServer =
                    new ReplicatedPubSubServer.Builder(testProtocol1, InetAddress.getByName(testIp))
                            .serverPort(nextServerPort)
                            .heartbeatPort(nextHearbeatPort++)
                            .shouldRetrieveMatchesAutomatically(false)
                            .build();
            testReplicatedPubSubServer.initialize();
            replicatedServers.put(nextServerPort++, testReplicatedPubSubServer);
        }
        Thread.sleep(100);
    }

    @Test
    public void testServersAgreeOnCoordinator() throws IOException, NotBoundException {
        boolean allServersAgreeOnCoordinator = true;
        List<ReplicatedPubSubServer> testServers = new LinkedList<>(replicatedServers.values());
        Communicate firstServersCoordinator;
        firstServersCoordinator = testServers.get(0).getCoordinator();
        String firstServersCoordinatorString = firstServersCoordinator.getThisServersIpPortString();
        for(ReplicatedPubSubServer server: testServers) {
            String currentServersCoordinator = server.getCoordinator().getThisServersIpPortString();
            if(!currentServersCoordinator.equals(firstServersCoordinatorString)) {
                allServersAgreeOnCoordinator = false;
            }
        }

        assertTrue(allServersAgreeOnCoordinator);
    }


}
