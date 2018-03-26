import message.Protocol;
import org.junit.After;
import org.junit.Before;
import server.QuorumConsistency;
import server.ReplicatedPubSubServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QuorumServerSetup {
    private static final Logger LOGGER = Logger.getLogger( QuorumServerSetup.class.getName() );

    Map<Integer, ReplicatedPubSubServer> replicatedServers = new ConcurrentHashMap<>();
    List<Integer> serverPorts;

    String serverInterfaceName;
    int numTestServers;
    int writeQuorum;
    int readQuorum;
    String testServerIp;
    Protocol testProtocol1;

    QuorumServerSetup() {
        serverInterfaceName = "CommunicateTest";
        // needs to match requirement in TestQuorumConsistency: need each server to have a 'representative' client so we
        // can check the number of messages retrieved for each unique message against the write quorum
        numTestServers = 3;

        try {
            testServerIp = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "UnknownHostException while trying to set up servers");
            e.printStackTrace();
            return;
        }

        testProtocol1 = new Protocol(
                Collections.singletonList("replyTo"),
                Collections.singletonList(new String[]{""}),
                ";",
                "",
                64);

    }

    @Before
    public void setUpReplicatedServers() throws IOException {
        System.out.println("Setting up test servers");

        int nextServerPort = 1099;
        int nextHearbeatPort = 9453;

        this.writeQuorum = numTestServers / 2 + 1;
        this.readQuorum = numTestServers + 1 - writeQuorum;

        for (int i = 0; i < numTestServers; i++) {
            ReplicatedPubSubServer testReplicatedPubSubServer =
                    new ReplicatedPubSubServer.Builder(testProtocol1, InetAddress.getByName(testServerIp), numTestServers)
                            .name(serverInterfaceName)
                            .serverPort(nextServerPort)
                            .heartbeatPort(nextHearbeatPort++)
                            .shouldRetrieveMatchesAutomatically(false)
                            .consistencyPolicy(new QuorumConsistency(numTestServers, readQuorum, writeQuorum))
                            .build();
            testReplicatedPubSubServer.initialize();
            replicatedServers.put(nextServerPort++, testReplicatedPubSubServer);
        }
        serverPorts = new ArrayList<>(replicatedServers.keySet());
//        try {
//            Thread.sleep(1500);
//        } catch (InterruptedException e) {
//            LOGGER.log(Level.WARNING, "Thread interrupted while sleeping in setUpReplicatedServers.");
//        }
    }

    @After
    public void cleanupServers() {
        System.out.println("Cleaning up test servers");
        for(ReplicatedPubSubServer server: replicatedServers.values()) {
            server.cleanup();
        }
//        Thread.sleep(numTestServers * 500);
    }
}
