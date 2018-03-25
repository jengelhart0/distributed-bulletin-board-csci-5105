import client.Client;
import message.Message;
import org.junit.Test;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

public class TestReadYourWritesConsistency extends ReadYourWritesClientSetup {
    private static final Logger LOGGER = Logger.getLogger( TestReadYourWritesConsistency.class.getName() );

    @Test
    public void testSingleClientMoveAmongServers() throws IOException, NotBoundException  {
        List<Integer> serverPorts = new ArrayList<>(replicatedServers.keySet());

        Client[] singleClientArray = new Client[]{super.uninitializedClients[0]};

        makeAllJoinRandomServers(singleClientArray, serverPorts);
        makeAllLeave(singleClientArray);
        // move to different server
        makeAllJoinRandomServers(singleClientArray, serverPorts);
        makeAllLeave(singleClientArray);
    }

    @Test
    public void testMultipleClientsMoveAmongServers() throws IOException, NotBoundException  {
        List<Integer> serverPorts = new ArrayList<>(replicatedServers.keySet());
        makeAllJoinRandomServers(uninitializedClients, serverPorts);
        makeAllLeave(uninitializedClients);
        // move to different servers
        makeAllJoinRandomServers(uninitializedClients, serverPorts);
        makeAllLeave(uninitializedClients);
    }

    private void makeAllJoinRandomServers(Client[] clients, List<Integer> serverPorts)
            throws IOException, NotBoundException {

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
    public void testReadYourWritesSingleClient() throws IOException, NotBoundException {
        Client testClient = uninitializedClients[0];
        testClient.initializeRemoteCommunication(testServerIp, serverPorts.get(0), serverInterfaceName);
        String testMessage = ";my non-reply test content 1";
        testClient.publish(new Message(testProtocol1, testMessage, false));
        System.out.println("testClient about to leave");
        testClient.leave();

//        Thread.sleep(5000);
        System.out.println("testClient establishing remote communication elsewhere");
        testClient.initializeRemoteCommunication(testServerIp, serverPorts.get(1), serverInterfaceName);
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            LOGGER.log(Level.WARNING, "Thread interrupting while sleeping in testReadYourWritesSingleClient.");
//        }
        System.out.println("testClient is at " + testClient.getId() + " entering retrieve()");
        List<Message> results = testClient.retrieve(
                new Message(testProtocol1, testProtocol1.getRetrieveAllQuery(), true));

        boolean found = false;
        for(Message message: results) {
            if(testProtocol1.stripPadding(message.withoutInternalFields()).equals(testMessage)) {
                found = true;
            }
        }

        testClient.terminateClient();
        assertTrue(found);
    }

}
