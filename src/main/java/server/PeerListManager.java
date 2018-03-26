package server;

import client.Client;
import client.ClientUtils;
import communicate.Communicate;
import message.Message;
import message.Protocol;
import runnableComponents.Scheduler;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

class PeerListManager {
    private static final Logger LOGGER = Logger.getLogger( PeerListManager.class.getName() );

    private String serverInterfaceName;
    private InetAddress serverIp;
    private int serverPort;
    private Protocol protocol;
    private RegistryServerLiaison registryServerLiaison;
    private int numExpectedServers;

    private ReplicatedPubSubServer thisServer;

    private Scheduler peerListMonitor;

    private Communicate coordinator;
    private int fromCoordinatorPort;
    private final Lock coordinatorLock = new ReentrantLock();
    private final Condition coordinatorSet = coordinatorLock.newCondition();

    private CoordinationState coordinationState;

    // TODO: going to have to override equals/hashcode to make this work; base on ip/port? for checking client's last server for writes
    // Alternatively, just make this a list and iterate through until you find the one that matches client's last server

    private ConcurrentMap<String, Client> clientsForReplicatedPeers;
    private int nextPeerListenPort;

    PeerListManager(String serverInterfaceName, InetAddress serverIp, int serverPort, Protocol protocol,
                    int startingPeerListenPort, RegistryServerLiaison registryServerLiaison, int numExpectedServers) {
        this.serverInterfaceName = serverInterfaceName;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.protocol = protocol;
        this.nextPeerListenPort = startingPeerListenPort;
        this.registryServerLiaison = registryServerLiaison;
        this.clientsForReplicatedPeers = new ConcurrentHashMap<>();
        this.numExpectedServers = numExpectedServers;

        this.coordinator = null;
        this.fromCoordinatorPort = -1;
        this.coordinationState = null;
    }

    void initialize(ReplicatedPubSubServer thisServer) throws IOException, NotBoundException {
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
//                    waitForOtherPeersToJoin();
                    int synchronizeClock = 0;
                    while (shouldThreadContinue()) {
                        discoverReplicatedPeers();
//                        System.out.println("At server " + thisServer.getPort() + ": size of clientsForReplicatedPeers is " +
//                                clientsForReplicatedPeers.size());
//                        System.out.println("NUM procs avail: " + String.valueOf(Runtime.getRuntime().availableProcessors()));
//                        System.out.println("NUM active threads" + java.lang.Thread.activeCount());
                        Thread.sleep(1000);
                        if(++synchronizeClock % 10 == 0) {
                            thisServer.getConsistencyPolicy().synchronize();
                            synchronizeClock = 0;
                        }
                    }
                    System.out.println(thisServer.getPort() + " leaving peer list monitor thread");
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Detected registry server liaison finished execution." +
                            "Terminating peer list monitor. \nAssociated reason:\n");
                    e.printStackTrace();
                } catch (InterruptedException | NotBoundException e) {
                    LOGGER.log(Level.SEVERE, e.toString());
                    e.printStackTrace();
                    throw new RuntimeException("Failure in peer list monitor thread.");
                }
            }
        };
        new Thread(peerListMonitor).start();
    }

//    private void waitForOtherPeersToJoin () {
//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            LOGGER.log(Level.SEVERE, "Interrupted waiting for other peers to join");
//        }
//    }

    private void discoverReplicatedPeers() throws IOException, NotBoundException {
        Set<String> peers = registryServerLiaison.getListOfServers();
        if(peers.size() >= numExpectedServers) {
//            System.out.println(thisServer.getPort() + ": discovered list of servers: " + peers.toString());
            peers.remove(serverIp.getHostAddress() + registryServerLiaison.getDelimiter() + serverPort);
            // System.out.println("calling findAndJoinCoordinatorIfUnknown");
            findAndJoinCoordinatorIfUnknown(peers);
            // System.out.println("calling joinDiscoveredPeers");
            joinDiscoveredPeers(peers);
            // System.out.println("returned from joinDiscoveredPeers");

            // TODO: add back in after testing??
            //        leaveStalePeers(peers);
        } else {
          System.out.println("Only " + peers.size() + " peers");
        }
    }

    private void findAndJoinCoordinatorIfUnknown(Set<String> replicatedServers) throws IOException, NotBoundException {
//        System.out.println(thisServer.getPort() + ": about to grab lock in findAndJoinCoordinator");
        coordinatorLock.lock();
        try {
            if(coordinator == null) {
//                System.out.println(thisServer.getPort() + ": coordinator null, looking for it");
                String newCoordinatorLocation = thisServer.getThisServersIpPortString();
                for (String server : replicatedServers) {
                    if (server.compareTo(newCoordinatorLocation) < 0) {
                        newCoordinatorLocation = server;
                    }
                }
                registerCoordinator(newCoordinatorLocation);
            }
            // System.out.println(thisServer.getPort() + " signaling the coordinator is set to " + coordinator.getThisServersIpPortString());
            coordinatorSet.signalAll();
        } finally {
            coordinatorLock.unlock();
        }
    }

    private void registerCoordinator(String newCoordinatorLocation) throws IOException, NotBoundException {
        // Do NOT lock coordinator here: the only caller locks it.
        if (newCoordinatorLocation.equals(thisServer.getThisServersIpPortString())) {
            establishCoordinationState();
            this.coordinator = thisServer;
            System.out.println(thisServer.getPort() + ": I had min ipPortString. I am the coordinator then.");
        } else {
            Client coordinatorClient = createCoordinatorClient(newCoordinatorLocation);
            clientsForReplicatedPeers.put(newCoordinatorLocation, coordinatorClient);
            this.coordinator = coordinatorClient.getServer();
        }
    }

    private Client createCoordinatorClient(String newCoordinatorLocation) throws IOException, NotBoundException {
        Client coordinatorClient = null;
        while(coordinatorClient == null) {
            coordinatorClient = ClientUtils.tryToCreateNewClientAt(protocol, nextPeerListenPort++);
        }
        String coordinatorIp = ServerUtils.getIpFromIpPortString(newCoordinatorLocation, protocol);
        int coordinatorPort = ServerUtils.getPortFromIpPortString(newCoordinatorLocation, protocol);
        coordinatorClient.initializeRemoteCommunication(coordinatorIp, coordinatorPort, serverInterfaceName);
        return coordinatorClient;
    }

    private void establishCoordinationState() {
        // For this POC, we are only building functionality that assumes Coordinator is established at system init and
        // stably remains Coordinator. Allowing Coordinator to crash/change would mean we would need to 'recover'
        // Coordinator state here, presumably by asking every peer for their latest used message/clientIds and using
        // responses to determine next ids to use.

        // Do NOT lock coordinator here. Calls to this are locked.
        if(coordinationState == null) {
            this.coordinationState = new CoordinationState();
        }
    }

    private void joinDiscoveredPeers(Set<String> replicatedServers) throws IOException, NotBoundException {
        for(String server: replicatedServers) {
            String[] serverLocation = server.split(registryServerLiaison.getDelimiter());
            String peerAddress = serverLocation[0];
            int peerPort = Integer.parseInt(serverLocation[1]);

//            System.out.println("This server: " + thisServer.getThisServersIpPortString() + " Current Peer: " + server);
//            System.out.println("clientsForReplicatedPeers");
//            System.out.println(clientsForReplicatedPeers.keySet().toString());
            if(!clientsForReplicatedPeers.containsKey(server)) {
//                System.out.println("No peer client for peer " + server + " at " + thisServer.getThisServersIpPortString());

                Client peerClient = null;
                while(peerClient == null) {
                    peerClient = ClientUtils.tryToCreateNewClientAt(protocol, this.nextPeerListenPort++);
//                    if (peerClient != null) {
//                        System.out.println("Created client for peer " + server + " at "
//                                + nextPeerListenPort + " for " + thisServer.getThisServersIpPortString());
//
//                    }
                }
                peerClient.initializeRemoteCommunication(peerAddress, peerPort, serverInterfaceName);
                clientsForReplicatedPeers.put(server, peerClient);
            }
        }
        coordinatorLock.lock();
        try {
            if(thisServer.isCoordinator()) {
                tellPeersTheFromCoordinatorPort();
            }
        } finally {
            coordinatorLock.unlock();
        }
    }



//    private void leaveStalePeers(Set<String> peers) {
//        for(String server: clientsForReplicatedPeers.keySet()) {
//            if(!peers.contains(server)) {
//                System.out.println("leaveStalePeers: removing peer client " + server);
//                Client toRemove = clientsForReplicatedPeers.remove(server);
//                toRemove.terminateClient();
//            }
//        }
//    }

    private void tellPeersTheFromCoordinatorPort() {
        for(Client peerClient: clientsForReplicatedPeers.values()) {
            peerClient.publish(new Message(
                    protocol,
                    protocol.buildCoordinatorPortNotification(),
                    false));
        }
    }


    Communicate getCoordinator() throws IOException, NotBoundException {
        Communicate coord = null;
        coordinatorLock.lock();
        try {
            while (coordinator == null) {
                System.out.println(thisServer.getPort() + ": about to wait for coordinator to be set");
                coordinatorSet.await();
                System.out.println(thisServer.getPort() + ": just woke up from napping on coordinator being set");
            }
            coord = coordinator;
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Interrupted while waiting for coordinator to be set.");
        } finally {
            coordinatorLock.unlock();
        }
        return coord;
    }

    public boolean isCoordinator() throws IOException, NotBoundException {
        return thisServer.getThisServersIpPortString()
                .equals(getCoordinator().getThisServersIpPortString());
    }

    public Set<String> getListOfServers() throws IOException {
        return registryServerLiaison.getListOfServers();
    }

    String requestNewMessageId() throws IOException, NotBoundException {
        if(isCoordinator() && this.coordinationState != null) {
            return this.coordinationState.requestNewMessageId();
        }
        throw new IllegalArgumentException("Server at " + thisServer.getThisServersIpPortString() +
                " asked for new message ID but is not coordinator or has no coordination state!");
    }

    String requestNewClientId() throws IOException, NotBoundException {
        if(isCoordinator() && this.coordinationState != null) {
            return this.coordinationState.requestNewClientId();
        }
        throw new IllegalArgumentException("Server at " + thisServer.getThisServersIpPortString() +
                " asked for new client ID but is not coordinator or has no coordination state!");
    }

    List<Message> retrieveFromPeer(String server, Message queryMessage) throws InterruptedException {
        if(clientsForReplicatedPeers.containsKey(server)) {
            return clientsForReplicatedPeers.get(server).retrieve(queryMessage);
        } else {
            throw new IllegalArgumentException("retrieveFromPeer: Tried to retrieve from a peer with no client in " +
                    "clientsForReplicatedPeers!");
        }
    }

    void publishToCoordinator(Message message) throws IOException, NotBoundException {
        String coordinatorIpPort = getCoordinator().getThisServersIpPortString();
        Client coordinatorClient = clientsForReplicatedPeers.get(coordinatorIpPort);
        if(coordinatorClient == null) {
            throw new IllegalArgumentException("Exception trying to publishToCoordinator: Have no coordinatorClient");
        }
        coordinatorClient.publish(message);
    }

    void publishToAllPeers(Message publication)  throws RemoteException {
        for(Client client: clientsForReplicatedPeers.values()) {
//            System.out.println("Publishing to peer for " + client.getServer().getThisServersIpPortString() + "\n\t"
//            + "message " + publication.asRawMessage());
            if (!client.publish(publication)) {
                throw new RemoteException("publishToPeer: Tried to publish to a peer with no client in " +
                        "clientsForReplicatedPeers!");
            }
        }
    }

    boolean createWriteQuorum(Message publication, int quorumSize) throws RemoteException {
        List<Client> shuffledPeerClients = getShuffledPeerClients(quorumSize);
        int numSuccessful = 0;
        int nextToTry = 0;
        while(numSuccessful < quorumSize) {
            if(shuffledPeerClients.get(nextToTry++).publish(publication)) {
                numSuccessful++;
            }
        }
        return numSuccessful == quorumSize;
    }

    List<Message> createReadQuorum(Message query, int quorumSize) throws RemoteException {
        List<Client> shuffledPeerClients = getShuffledPeerClients(quorumSize);

        int highestMessageIdAtPeer;
        Client bestChoice = shuffledPeerClients.get(0);
        int highestMessageIdFound = bestChoice.getHighestMessageIdStoredAtServer();
        Client currentPeerClient;
        for(int i = 1; i < quorumSize; i++) {
            currentPeerClient = shuffledPeerClients.get(i);
            highestMessageIdAtPeer = currentPeerClient.getHighestMessageIdStoredAtServer();

            if(highestMessageIdAtPeer > highestMessageIdFound) {
                highestMessageIdFound = highestMessageIdAtPeer;
                bestChoice = currentPeerClient;
            }
        }
        return bestChoice.retrieve(query);
    }

    Set<String> getAllMessagesFromPeers() {
        Set<String> allMessages = new HashSet<>();
        for(Client peerClient: clientsForReplicatedPeers.values()) {
            List<Message> peerMessages = peerClient.retrieve(
                            new Message(
                                    protocol,
                                    protocol.getRetrieveAllQuery(),
                                    false));
            for(Message message: peerMessages) {
                allMessages.add(message.asRawMessage());
            }
        }
        return allMessages;
    }

    private List<Client> getShuffledPeerClients(int quorumSize) {
        List<Client> shuffledPeerClients = new ArrayList<>(clientsForReplicatedPeers.values());
        if(shuffledPeerClients.size() < quorumSize) {
            throw new IllegalArgumentException("getShuffledPeerClients: quorum request larger than num peer clients!");
        }

        Collections.shuffle(shuffledPeerClients);
        return shuffledPeerClients;
    }

    String getCoordinatorIp() throws IOException, NotBoundException {
        String ip;

        String ipPort = getCoordinator().getThisServersIpPortString();
        String[] parsedIpPort = ipPort.split(protocol.getDelimiter());
        ip = parsedIpPort[0];

        return ip;
    }
// Misleading!! Mismatch between coordinator remote port and this servers peer coordinator clients listen port!
//
//    int getCoordinatorPort() throws IOException, NotBoundException {
//        String port;
//
//        String ipPort = getCoordinator().getThisServersIpPortString();
//        String[] parsedIpPort = ipPort.split(protocol.getDelimiter());
//        port = parsedIpPort[1];
//
//        return Integer.parseInt(port);
//    }

    //
    void setFromCoordinatorPort(int port) {
        coordinatorLock.lock();
        this.fromCoordinatorPort = port;
        coordinatorLock.unlock();
    }

    boolean messageIsFromCoordinator(String fromIp, int fromPort) throws IOException, NotBoundException {
        int coordPort;
        coordinatorLock.lock();
        System.out.println("coordinatorIp " + getCoordinatorIp());
        System.out.println("fromIp " + fromIp);
        System.out.println("coordinatorPort " + fromCoordinatorPort);
        System.out.println("fromPort " + fromPort);

        try {
            if (fromCoordinatorPort < 0) {
                throw new IllegalArgumentException("Tried to check if messageIsFromCoordinator but fromCoordinatorPort" +
                        " is not set");
            }
            coordPort = fromCoordinatorPort;
        } finally {
            coordinatorLock.unlock();
        }
        return fromIp.equals(getCoordinatorIp()) && (fromPort == coordPort);
    }
}
