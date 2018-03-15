import communicate.Communicate;
import org.junit.Test;
import server.ReplicatedPubSubServer;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.*;

import static org.junit.Assert.assertTrue;

public class TestReplicatedServers extends ReplicatedServerSetup {

    @Test
    public void testServersAgreeOnCoordinator() throws IOException, NotBoundException {
        boolean allServersAgreeOnCoordinator = false;
        List<ReplicatedPubSubServer> testServers = new LinkedList<>(replicatedServers.values());
        Communicate firstServersCoordinator;
        firstServersCoordinator = testServers.get(0).getCoordinator();
        String firstServersCoordinatorString = firstServersCoordinator.getThisServersIpPortString();
        for(ReplicatedPubSubServer server: testServers) {
            String currentServersCoordinator = server.getCoordinator().getThisServersIpPortString();
            allServersAgreeOnCoordinator = currentServersCoordinator.equals(firstServersCoordinatorString);
        }
        assertTrue(allServersAgreeOnCoordinator);
    }
}
