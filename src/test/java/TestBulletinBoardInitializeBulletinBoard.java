import org.junit.Test;
import server.ReplicatedPubSubServer;

import java.net.UnknownHostException;
import java.rmi.RemoteException;

import static org.junit.Assert.assertTrue;

public class TestBulletinBoardInitializeBulletinBoard extends TestReplicatedBulletinBoardServers {
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
