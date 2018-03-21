import client.Client;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.rmi.NotBoundException;


public class ReadYourWritesClientSetup extends ReadYourWritesServerSetup {

    Client[] clients;
    Client[] uninitializedClients;
    int numClientsPerServer = 10;

    @Before
    public void setUpClients() throws IOException, NotBoundException {
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
        // Create uninitialized clients, which can move among servers
        int numUninitializedClients = 20;
        uninitializedClients = new Client[numUninitializedClients];
        listenPort = 33848;
        for(int i = 0; i < numUninitializedClients; i++) {
            uninitializedClients[i] = new Client(testProtocol1, listenPort++);
        }
    }

    @After
    public void teardownClients() {
        for(Client client: clients) {
            client.terminateClient();
        }
        for(Client client: uninitializedClients) {
            client.terminateClient();
        }
    }
}
