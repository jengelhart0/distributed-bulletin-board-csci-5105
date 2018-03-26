import client.Client;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.rmi.NotBoundException;

public class QuorumClientSetup extends QuorumServerSetup {

    Client[] clients;
    int numClientsPerServer = 10;

    @Before
    public void setUpClients() throws IOException, NotBoundException {
        System.out.println("Setting up test clients");

        int listenPort;
        clients = new Client[numClientsPerServer * numTestServers];
        listenPort = 8888;
        int clientIdx = 0;
        for (int serverPort: replicatedServers.keySet()) {
            for (int j = 0; j < numClientsPerServer; j++) {
                Client testClient = new Client(testProtocol1, listenPort++);
                testClient.initializeRemoteCommunication(testServerIp, serverPort, serverInterfaceName);
                clients[clientIdx++] = testClient;
            }
        }
    }

    @After
    public void teardownClients() {
        System.out.println("Cleaning up test clients");

        for(Client client: clients) {
            client.terminateClient();
        }
    }
}
