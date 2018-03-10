import communicate.Communicate;
import message.Protocol;
import org.junit.Test;
import server.ReplicatedPubSubServer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;

import static org.junit.Assert.assertTrue;

public class BulletinBoardInitializeTest {

    private int NUM_TEST_SERVERS = 5;

    Protocol BULLETIN_PROTOCOL = new Protocol(
            new String[]{"messageId", "clientId", "replyTo"},
            new String[][]{
                    new String[]{""},
                    new String[]{""},
                    new String[]{""},
            },
            ";",
            "",
            256);

    @Test
    public void testInitializeAndAgreeOnCoordinator() throws UnknownHostException, RemoteException {
        ReplicatedPubSubServer[] replicatedServers = new ReplicatedPubSubServer[NUM_TEST_SERVERS];
        for(int i = 0; i < NUM_TEST_SERVERS; i++) {
            ReplicatedPubSubServer testReplicatedPubSubServer =
                    new ReplicatedPubSubServer.Builder(BULLETIN_PROTOCOL, InetAddress.getByName("localhost")).build();
            testReplicatedPubSubServer.initialize();
            replicatedServers[i] = testReplicatedPubSubServer;
        }

        boolean allServersAgreeOnCoordinator = true;
        String firstServersCoordinator = replicatedServers[0].getCoordinator().getThisServersIpPortString();
        for(int i = 1; i < NUM_TEST_SERVERS; i++) {
            String currentServersCoordinator = replicatedServers[i].getCoordinator().getThisServersIpPortString();
            if(!currentServersCoordinator.equals(firstServersCoordinator)) {
                allServersAgreeOnCoordinator = false;
            }
        }

        assertTrue(allServersAgreeOnCoordinator);
    }
}
