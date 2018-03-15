import client.Client;
import message.Message;
import org.junit.Test;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertTrue;

public class TestConsistency extends ReplicatedClientSetup {

//    @Test
//    public void testSingleClientMoveAmongServers() throws RemoteException, NotBoundException  {
//        List<Integer> serverPorts = new ArrayList<>(replicatedServers.keySet());
//
//        Client[] singleClientArray = new Client[]{super.uninitializedClients[0]};
//
//        makeAllJoinRandomServers(singleClientArray, serverPorts);
//        makeAllLeave(singleClientArray);
//        // move to different server
//        makeAllJoinRandomServers(singleClientArray, serverPorts);
//        makeAllLeave(singleClientArray);
//    }

    @Test
    public void testMultipleClientsMoveAmongServers() throws RemoteException, NotBoundException  {
        List<Integer> serverPorts = new ArrayList<>(replicatedServers.keySet());
        makeAllJoinRandomServers(uninitializedClients, serverPorts);
        makeAllLeave(uninitializedClients);
        // move to different servers
        makeAllJoinRandomServers(uninitializedClients, serverPorts);
        makeAllLeave(uninitializedClients);
    }

    private void makeAllJoinRandomServers(Client[] clients, List<Integer> serverPorts)
            throws RemoteException, NotBoundException {

        int numPorts = serverPorts.size();

        for(Client client: clients) {
            int randomIdx = ThreadLocalRandom.current().nextInt(0, numPorts);
            Integer port = serverPorts.get(randomIdx);
            client.initializeRemoteCommunication(super.testServerIp, port, super.serverInterfaceName);
        }
    }

    private void makeAllLeave(Client[] clients) {
        for(Client client: clients) {
            client.leave();
        }
    }

    @Test
    public void testReadYourWritesSingleClient() throws RemoteException, NotBoundException {
//        Client testClient = uninitializedClients[0];
//        testClient.initializeRemoteCommunication(testServerIp, serverPorts.get(0), serverInterfaceName);
//        testClient.publish(new Message(testProtocol1, ";my non-reply test content 1", false));
//        testClient.leave();
//
//        testClient.initializeRemoteCommunication(testServerIp, serverPorts.get(1), serverInterfaceName);
//        String delim = testProtocol1.getDelimiter();
//        String[] results = testClient.retrieve(new Message(testProtocol1, delim + testClient.getId() + delim + delim, true));
//

    }
}
