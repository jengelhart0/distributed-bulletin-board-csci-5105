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

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReplicatedPubSubServer implements Communicate {
    private static final Logger LOGGER = Logger.getLogger( ReplicatedPubSubServer.class.getName() );

    private String name;
    private Protocol protocol;
    private InetAddress ip;
    private int port;

    private int maxClients;
    private int numClients;

    private final Object numClientsLock = new Object();

    private Dispatcher dispatcher;
    private PeerListManager peerListManager;

    private ReplicatedPubSubServer(Builder builder) {

        this.name = builder.name;
        this.protocol = builder.protocol;
        this.ip = builder.ip;
        this.port = builder.serverPort;
        this.maxClients = builder.maxClients;

        this.dispatcher = new Dispatcher(this.protocol, builder.store, builder.shouldRetrieveMatchesAutomatically);
        RegistryServerLiaison registryServerLiaison = new RegistryServerLiaison(
                builder.heartbeatPort,
                builder.registryServerAddress,
                builder.registryServerPort,
                builder.registryMessageDelimiter,
                builder.registryMessageSize,
                builder.serverListSize);

        this.peerListManager = new PeerListManager(name, ip, port, protocol,
                builder.startingPeerListenPort, registryServerLiaison);
    }

    public static class Builder {
        private Protocol protocol;
        private InetAddress ip;

        private String name;
        private int maxClients;

        private int serverPort;

        private InetAddress registryServerAddress;
        private int registryServerPort;
        private String registryMessageDelimiter;
        private int registryMessageSize;
        private int serverListSize;
        private int heartbeatPort;

        private int startingPeerListenPort;

        private MessageStore store;

        private boolean shouldRetrieveMatchesAutomatically;


        public Builder(Protocol protocol, InetAddress ip) throws UnknownHostException {
            this.protocol = protocol;
            this.ip = ip;

            // set optional parameters to defaults
            this.name = Communicate.NAME;
            this.maxClients = 1000;
            this.serverPort = 1099;
            this.registryServerAddress = InetAddress.getByName("localhost");
            this.registryServerPort = 5105;
            this.registryMessageDelimiter = ";";
            this.registryMessageSize = 120;
            this.serverListSize = 1024;
            this.heartbeatPort = 9453;
            this.startingPeerListenPort = 18888;
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

        public Builder registryServerAddress(String registryServerAddress) throws UnknownHostException {
            this.registryServerAddress = InetAddress.getByName(registryServerAddress);
            return this;
        }

        public Builder registryServerPort(int registryServerPort) {
            this.registryServerPort = registryServerPort;
            return this;
        }

        public Builder registryMessageDelimiter(String delimiter) {
            this.registryMessageDelimiter = delimiter;
            return this;
        }

        public Builder registryMessageSize(int size) {
            this.registryMessageSize = size;
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

        public Builder serverPort(int serverPort) {
            this.serverPort = serverPort;
            return this;
        }

        public Builder startingPeerListenPort(int peerListenPort) {
            this.startingPeerListenPort = peerListenPort;
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

    public void initialize() {
        try {
            setCommunicationVariables(name, maxClients, protocol, ip, port);
            makeThisARemoteCommunicationServer();
            dispatcher.initialize();
            peerListManager.initialize(this);
        } catch (IOException | NotBoundException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed on server initialization: " + e.toString());
            e.printStackTrace();
            cleanup();
        }
        LOGGER.log(Level.INFO, "Finished initializing remote server.");
    }

    public void cleanup() {
        try {
            peerListManager.cleanup();
            dispatcher.cleanup();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "While cleaning up server: " + e.toString());
            e.printStackTrace();
        }
    }

    private void setCommunicationVariables(String name, int maxClients, Protocol protocol, InetAddress ip, int rmiPort) {
        this.name = name;
        this.protocol = protocol;

        this.maxClients = maxClients;
        this.numClients = 0;

        this.ip = ip;
        this.port = rmiPort;
    }

    private void makeThisARemoteCommunicationServer() {
//        LOGGER.log(Level.INFO, "IP " + this.ip.getHostAddress());
//        LOGGER.log(Level.INFO, "Port " + Integer.toString(this.port));

        try {
            System.setProperty("java.rmi.server.hostname", this.ip.getHostAddress());

            Communicate stub =
                    (Communicate) UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.createRegistry(this.port);
            registry.rebind(this.name, stub);
//            LOGGER.log(Level.INFO, "ReplicatedPubSubServer bound");
        } catch (RemoteException re) {
            LOGGER.log(Level.SEVERE, re.toString());
            re.printStackTrace();
        }
    }

    @Override
    public boolean Join(String IP, int Port, String existingClientId, String previousServer) throws NotBoundException, IOException {
        synchronized (numClientsLock) {
            if(numClients >= maxClients) {
                LOGGER.log(Level.SEVERE, "Maximum clients exceeded for server " + getThisServersIpPortString());
                return false;
            }
            numClients++;
        }
        dispatcher.addNewClient(IP, Port);
        if(existingClientId == null) {
//            System.out.println(getThisServersIpPortString() + " going to getCoordinator() in Join() for new client ID");
            String newClientId = peerListManager.getCoordinator().requestNewClientId();
            return dispatcher.returnClientIdToClient(IP, Port, newClientId);
        }
//        consistency.enforceOnJoin(existingClientId, previousServer);
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
    public boolean Retrieve(String IP, int Port, String queryMessage) throws RemoteException {
        return dispatcher.retrieve(IP, Port, queryMessage);
    }

    @Override
    public boolean Publish(String Message, String IP, int Port) throws RemoteException {
        // TODO: need to add messageId/clientId
        return protocol.areInternalFieldsBlank(Message) && dispatcher.publish(Message, IP, Port);
    }

    @Override
    public boolean Ping() throws RemoteException {
        return true;
    }

    @Override
    public Communicate getCoordinator() throws IOException, NotBoundException {
        return peerListManager.getCoordinator();
    }

    @Override
    public boolean isCoordinatorKnown() throws RemoteException {
        return peerListManager.isCoordinatorKnown();
    }

    @Override
    public String requestNewMessageId() throws RemoteException {
        return peerListManager.requestNewMessageId();
    }

    @Override
    public String requestNewClientId() throws RemoteException {
        return peerListManager.requestNewClientId();
    }

    public Set<String> getListOfServers() throws IOException {
        return peerListManager.getListOfServers();
    }

    public String getThisServersIpPortString() {
        return ServerUtils.getIpPortString(ip.getHostAddress(), port, protocol);
    }
}
