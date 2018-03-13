import client.Client;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;

import java.rmi.NotBoundException;

import static org.junit.Assert.assertTrue;

public class TestConsistency extends TestReplicatedServers {

    Client[] clients;

    @Before
    public void setUpClients() throws IOException, NotBoundException {

        int numClientsPerServer = 10;
        clients = new Client[numClientsPerServer * super.numTestServers];
        int listenPort = 8888;
        int clientIdx = 0;
        for (int serverPort: super.replicatedServers.keySet()) {
            for (int j = 0; j < numClientsPerServer; j++) {
                Client testClient = new Client(super.testProtocol1, listenPort++);
                testClient.initializeRemoteCommunication(super.testIp, serverPort, super.serverInterfaceName);
                clients[clientIdx++] = testClient;
            }
        }
    }

    @Test
    public void testReadYourWritesSingleClient() {
        Client testClient = clients[0];
        testClient.join();
        testClient.publish("")

    }
}
