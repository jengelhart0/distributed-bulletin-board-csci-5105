package server;

import communicate.Communicate;
import message.Protocol;

import java.io.IOException;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import client.Client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReplicatedPubSubServer implements Communicate {
    private static final Logger LOGGER = Logger.getLogger( ReplicatedPubSubServer.class.getName() );

    private String name;
    private Protocol protocol;
    private InetAddress ip;
    private int rmiPort;

    private int maxClients;
    private int numClients;

    private static final Object numClientsLock = new Object();
    private static final Object coordinatorLock = new Object();

    private Dispatcher dispatcher;


    private HeartbeatListener heartbeatListener;
    private int heartbeatPort;

    private InetAddress registryServerAddress;
    private int registryServerPort;
    private int serverListSize;

    private String registerMessage;
    private String deregisterMessage;

    private ReplicatedPubSubServer coordinator;

    // TODO: going to have to override equals/hashcode to make this work; base on ip/port? for checking client's last server for writes
    // Alternatively, just make this a list and iterate through until you find the one that matches client's last server
    private ConcurrentMap<String, Client> clientsForReplicatedPeers;
    private int nextPeerListenPort;

    public static class Builder {
        private Protocol protocol;
        private InetAddress ip;

        private String name;
        private int maxClients;

        private int heartbeatPort;

        private int rmiPort;

        private InetAddress registryServerAddress;
        private int registryServerPort;
        private int serverListSize;

        private int startingPeerListenPort;

        private MessageStore store;

        private boolean shouldRetrieveMatchesAutomatically;


        public Builder(Protocol protocol, InetAddress ip) throws UnknownHostException {
            this.protocol = protocol;
            this.ip = ip;

            // set optional parameters to defaults
            this.name = Communicate.NAME;
            this.maxClients = 1000;
            this.serverListSize = 1024;
            this.heartbeatPort = 9453;
            this.rmiPort = 1099;
            this.registryServerAddress = InetAddress.getByName("localhost");
            this.registryServerPort = 5104;
            this.startingPeerListenPort = 8888;
            this.store = new PairedKeyMessageStore();
            this.shouldRetrieveMatchesAutomatically = true;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder maxClients(int maxClients) {
            this.maxClients = maxClients;
            return this;
        }

        public Builder serverListSize(int serverListSize) {
            this.serverListSize = serverListSize;
            return this;
        }

        public Builder heartbeatPort(int heartbeatPort) {
            this.heartbeatPort = heartbeatPort;
            return this;
        }

        public Builder rmiPort(int rmiPort) {
            this.rmiPort = rmiPort;
            return this;
        }

        public Builder startingPeerListenPort(int peerListenPort) {
            this.startingPeerListenPort = peerListenPort;
            return this;
        }

        public Builder registryServerAddress(String registryServerAddress) throws UnknownHostException {
            this.registryServerAddress = InetAddress.getByName(registryServerAddress);
            return this;
        }

        public Builder registryServerPort(int registryServerPort) {
            this.registryServerPort = registryServerPort;
            return this;
        }

        public Builder store(MessageStore store) {
            this.store = store;
            return this;
        }

        public Builder shouldRetrieveMatchesAutomatically(boolean shouldRetrieveAutomatically) {
            this.shouldRetrieveMatchesAutomatically = shouldRetrieveAutomatically;
            return this;
        }

        public ReplicatedPubSubServer build() {
            return new ReplicatedPubSubServer(this);
        }
    }

    private ReplicatedPubSubServer(Builder builder) {

        this.name = builder.name;
        this.protocol = builder.protocol;
        this.ip = builder.ip;
        this.maxClients = builder.maxClients;

        this.dispatcher = new Dispatcher(this.protocol, builder.store, builder.shouldRetrieveMatchesAutomatically);

        this.heartbeatPort = builder.heartbeatPort;
        this.rmiPort = builder.rmiPort;
        this.registryServerAddress = builder.registryServerAddress;
        this.registryServerPort = builder.registryServerPort;
        this.serverListSize = builder.serverListSize;
        this.nextPeerListenPort = builder.startingPeerListenPort;

        this.clientsForReplicatedPeers = new ConcurrentHashMap<>();
    }

    public void initialize() {
        try {
            setCommunicationVariables(name, maxClients, protocol, ip, rmiPort, heartbeatPort,
                    registryServerAddress, registryServerPort, serverListSize);
            startHeartbeat();
            makeThisARemoteCommunicationServer();
            registerWithRegistryServer();
            //discoverReplicatedPeers();
            dispatcher.initialize();
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed on server initialization: " + e.toString());
            e.printStackTrace();
            cleanup();
        }
        LOGGER.log(Level.INFO, "Finished initializing remote server.");
    }

    public void cleanup() {
        try {
            heartbeatListener.tellThreadToStop();
            heartbeatListener.forceCloseSocket();
            deregisterFromRegistryServer();
            dispatcher.cleanup();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "While cleaning up server: " + e.toString());
            e.printStackTrace();
        }
    }

    private void setCommunicationVariables(String name, int maxClients, Protocol protocol, InetAddress ip, int rmiPort,
                                           int heartbeatPort, InetAddress registryServerIp, int registryServerPort, int serverListSize)
            throws UnknownHostException {

        this.name = name;
        this.protocol = protocol;

        this.maxClients = maxClients;
        this.numClients = 0;

        this.heartbeatPort = heartbeatPort;

        this.ip = ip;
        this.rmiPort = rmiPort;

        this.registryServerAddress = registryServerIp;
        this.registryServerPort = registryServerPort;
        this.serverListSize = serverListSize;

        setRegistryServerMessages();
    }

    private void startHeartbeat() throws IOException {
        this.heartbeatListener = new HeartbeatListener(this.protocol);
        this.heartbeatListener.listenAt(this.heartbeatPort, this.ip);
        Thread heartbeatThread = new Thread(this.heartbeatListener);
        heartbeatThread.start();

        if(!heartbeatThread.isAlive()) {
            throw new RuntimeException();
        }
    }

    private void makeThisARemoteCommunicationServer() {
        LOGGER.log(Level.INFO, "IP " + this.ip.getHostAddress());
        LOGGER.log(Level.INFO, "Port " + Integer.toString(this.rmiPort));

        try {
            System.setProperty("java.rmi.server.hostname", this.ip.getHostAddress());

            Communicate stub =
                    (Communicate) UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.createRegistry(this.rmiPort);
            registry.rebind(this.name, stub);
            LOGGER.log(Level.INFO, "ReplicatedPubSubServer bound");
        } catch (RemoteException re) {
            LOGGER.log(Level.SEVERE, re.toString());
            re.printStackTrace();
        }
    }

    private void setRegistryServerMessages() throws UnknownHostException {
        String ip = this.ip.getHostAddress();
        this.registerMessage = "Register;RMI;" + ip + ";" + heartbeatPort + ";" + name + ";" + rmiPort;
        this.deregisterMessage = "Deregister;RMI;" + ip + ";" + heartbeatPort;
    }

    private void registerWithRegistryServer() throws IOException {
        sendRegistryServerMessage(this.registerMessage);
    }

    private void deregisterFromRegistryServer() throws IOException {
        sendRegistryServerMessage(this.deregisterMessage);
    }

    public Set<String> getListOfServers() throws IOException {
        int listSizeinBytes = this.serverListSize;
        DatagramPacket registryPacket = new DatagramPacket(new byte[listSizeinBytes], listSizeinBytes);

        try (DatagramSocket getListSocket = new DatagramSocket()) {
            String getListMessage = "GetList;RMI;"
                    + ip.getHostAddress()
                    + ";"
                    + this.heartbeatPort;
            // sending here to minimize chance response arrives before we listen for it
            getListSocket.send(makeRegistryServerPacket(getListMessage));
            getListSocket.receive(registryPacket);
        }
        String[] rawServerList = new String(registryPacket.getData(), 0, registryPacket.getLength(), "UTF8")
                .split(this.protocol.getDelimiter());

        Set<String> results = new HashSet<>();
        for (int i = 0; i < rawServerList.length; i+=2) {
            results.add(rawServerList[i] + ";" + rawServerList[i+1]);
        }
        return results;
    }

    private void sendRegistryServerMessage(String rawMessage) throws IOException {
        DatagramPacket packet = makeRegistryServerPacket(rawMessage);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(packet);
        }
    }

    private DatagramPacket makeRegistryServerPacket(String rawMessage) {
        int messageSize = this.protocol.getMessageSize();
        DatagramPacket packet = new DatagramPacket(new byte[messageSize], messageSize, this.registryServerAddress, registryServerPort);
        packet.setData(rawMessage.getBytes());
        return packet;
    }

    private void discoverReplicatedPeers() throws IOException, NotBoundException {
        Set<String> peersAndThis = getListOfServers();
        joinDiscoveredPeers(peersAndThis);
        leaveStalePeers(peersAndThis);
        findCoordinator();
    }

    private void joinDiscoveredPeers(Set<String> replicatedServers) throws IOException, NotBoundException {

        replicatedServers.remove(getThisServersIpPortString());

        for(String server: replicatedServers) {
            String[] serverLocation = server.split(";");
            String address = serverLocation[0];
            int port = Integer.parseInt(serverLocation[1]);

            if(!clientsForReplicatedPeers.containsKey(server)) {
                Client peerClient = new Client(address, port, name, protocol, nextPeerListenPort++);
                new Thread(peerClient).start();

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
        ReplicatedPubSubServer currentCoordinator = null;
        if(clientsForReplicatedPeers.isEmpty()) {
            currentCoordinator = this;
        } else {
            for(Client peerClient: clientsForReplicatedPeers.values()) {
                ReplicatedPubSubServer peerCoordinator = peerClient.getServer().getCoordinator();
                if (peerCoordinator != null) {
                    currentCoordinator = peerCoordinator;
                }
            }
        }

        synchronized (coordinatorLock) {
            this.coordinator = currentCoordinator;
        }
    }

    @Override
    public boolean Join(String IP, int Port) throws RemoteException {
        synchronized (numClientsLock) {
            if(numClients >= maxClients) {
                return false;
            }
            numClients++;
        }
        dispatcher.addNewClient(IP, Port);
        return true;
    }

    @Override
    public boolean Leave(String IP, int Port) throws RemoteException {
        boolean clientWasActuallyBeingManaged = dispatcher.informManagerThatClientLeft(IP, Port);
        // Only decrement num clients if a non-null manager was associated with client
        if(clientWasActuallyBeingManaged) {
            synchronized (numClientsLock) {
                numClients--;
            }
        }
        return true;
    }

    @Override
    public boolean Subscribe(String IP, int Port, String Message) throws RemoteException {
        return dispatcher.subscribe(IP, Port, Message);
    }

    @Override
    public boolean Unsubscribe(String IP, int Port, String Message) throws RemoteException {
        return dispatcher.unsubscribe(IP, Port, Message);
    }

    @Override
    public boolean Publish(String Message, String IP, int Port) throws RemoteException {
        return dispatcher.publish(Message, IP, Port);
    }

    @Override
    public boolean Ping() throws RemoteException {
        return true;
    }

    @Override
    public ReplicatedPubSubServer getCoordinator() {
        synchronized (coordinatorLock) {
            return coordinator;
        }
    }

    public String getThisServersIpPortString() {
        return ServerUtils.getIpPortString(ip.getHostAddress(), rmiPort, protocol);
    }
}
