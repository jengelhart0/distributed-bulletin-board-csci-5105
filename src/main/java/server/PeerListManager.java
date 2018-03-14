package server;

import client.Client;
import message.Protocol;
import runnableComponents.Scheduler;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

class PeerListManager {
    private static final Logger LOGGER = Logger.getLogger( PeerListManager.class.getName() );

    private String serverInterfaceName;
    private InetAddress serverIp;
    private int serverPort;
    private Protocol protocol;
    private RegistryServerLiaison registryServerLiaison;

    private ReplicatedPubSubServer thisServer;

    private Scheduler peerListMonitor;

    private ReplicatedPubSubServer coordinator;
    private final Object coordinatorLock = new Object();

    private CoordinationState coordinationState;

    // TODO: going to have to override equals/hashcode to make this work; base on ip/port? for checking client's last server for writes
    // Alternatively, just make this a list and iterate through until you find the one that matches client's last server

    private ConcurrentMap<String, Client> clientsForReplicatedPeers;
    private int nextPeerListenPort;

    PeerListManager(String serverInterfaceName, InetAddress serverIp, int serverPort, Protocol protocol,
                    int startingPeerListenPort, RegistryServerLiaison registryServerLiaison) {
        this.serverInterfaceName = serverInterfaceName;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.protocol = protocol;
        this.nextPeerListenPort = startingPeerListenPort;
        this.registryServerLiaison = registryServerLiaison;
        this.clientsForReplicatedPeers = new ConcurrentHashMap<>();

        this.coordinator = null;

        this.coordinationState = null;
    }

    void initialize(ReplicatedPubSubServer thisServer) throws IOException {
        this.registryServerLiaison.initialize(this.serverInterfaceName, this.serverIp, this.serverPort);
        // We only know about current server at this point, so it must be our coordinator
        this.thisServer = thisServer;
        startPeerListMonitor();
    }

    void cleanup() throws IOException {
        registryServerLiaison.cleanup();
        peerListMonitor.tellThreadToStop();
    }

    private void startPeerListMonitor() {
        this.peerListMonitor = new Scheduler() {
            @Override
            public void run() {
                try {
                    while (shouldThreadContinue()) {
                        discoverReplicatedPeers();
                        Thread.sleep(2500);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Detected registry server liaison finished execution." +
                            "Terminating peer list monitor. \nAssociated reason:\n" + e.toString());
                } catch (InterruptedException | NotBoundException e) {
                    LOGGER.log(Level.SEVERE, e.toString());
                    e.printStackTrace();
                    throw new RuntimeException("Failure in peer list monitor thread.");
                }
            }
        };
        new Thread(peerListMonitor).start();
    }

    private void discoverReplicatedPeers() throws IOException, NotBoundException {
        Set<String> peers = registryServerLiaison.getListOfServers();
        // TODO: won't work until we can get accurate port back from registry server getList (currently storing heartbeatPort)!
        peers.remove(serverIp.getHostAddress() + registryServerLiaison.getDelimiter() + serverPort);
        joinDiscoveredPeers(peers);
        leaveStalePeers(peers);
        findCoordinator();
    }

    private void joinDiscoveredPeers(Set<String> replicatedServers) throws IOException, NotBoundException {

        for(String server: replicatedServers) {
            String[] serverLocation = server.split(registryServerLiaison.getDelimiter());
            String peerAddress = serverLocation[0];
            int peerPort = Integer.parseInt(serverLocation[1]);

            if(!clientsForReplicatedPeers.containsKey(server)) {
                Client peerClient = new Client(protocol, nextPeerListenPort++);
                peerClient.initializeRemoteCommunication(peerAddress, peerPort, serverInterfaceName);

                clientsForReplicatedPeers.put(server, peerClient);
            }
        }
    }

    private void leaveStalePeers(Set<String> peers) {
        for(String server: clientsForReplicatedPeers.keySet()) {
            if(!peers.contains(server)) {
                Client toRemove = clientsForReplicatedPeers.remove(server);
                toRemove.terminateClient();
            }
        }
    }

    private void findCoordinator() throws RemoteException {
        ReplicatedPubSubServer newCoordinator = thisServer;
        if(!clientsForReplicatedPeers.isEmpty()) {
            // TODO: find a more efficient means of coordinator determination
            for(Client peerClient: clientsForReplicatedPeers.values()) {
                ReplicatedPubSubServer peerCoordinator = peerClient.getServer().getCoordinator();
                if (peerCoordinator != null) {
                    newCoordinator = peerCoordinator;
                }
            }
        }

        setCoordinator(newCoordinator);
    }

    ReplicatedPubSubServer getCoordinator() {
        synchronized (coordinatorLock) {
            return this.coordinator;
        }
    }

    private void setCoordinator(ReplicatedPubSubServer newCoordinator) {

        synchronized (coordinatorLock) {

            if(newCoordinator.equals(thisServer)) {
                establishCoordinationState();
            }

            this.coordinator = newCoordinator;
        }
    }

    private void establishCoordinationState() {
        // For this POC, we are only building functionality that assumes Coordinator is established at system init and
        // stably remains Coordinator. Allowing Coordinator to crash/change would mean we would need to 'recover'
        // Coordinator state here, presumably by asking every peer for their latest used message/clientIds and using
        // responses to determine next ids to use.
        if(coordinationState == null) {
            this.coordinationState = new CoordinationState();
        }
    }

    public Set<String> getListOfServers() throws IOException {
        return registryServerLiaison.getListOfServers();
    }

    String requestNewMessageId() {
        if(this.coordinator == thisServer && this.coordinationState != null) {
            return this.coordinationState.requestNewMessageId();
        }
        throw new IllegalArgumentException("Server at " + thisServer.getThisServersIpPortString() +
                " asked for new message ID but is not coordinator or has no coordination state!");
    }

    String requestNewClientId() {
        if(this.coordinator == thisServer && this.coordinationState != null) {
            return this.coordinationState.requestNewClientId();
        }
        throw new IllegalArgumentException("Server at " + thisServer.getThisServersIpPortString() +
                " asked for new client ID but is not coordinator or has no coordination state!");
    }
}
