package server;

import communicate.Communicate;
import message.Message;
import message.Protocol;

import java.io.IOException;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.List;
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
    private ConsistencyPolicy consistencyPolicy;

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
        builder.consistencyPolicy.initialize(this, protocol, dispatcher);
        this.consistencyPolicy = builder.consistencyPolicy;
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

        private ConsistencyPolicy consistencyPolicy;

        private boolean shouldRetrieveMatchesAutomatically;


        public Builder(Protocol protocol, InetAddress ip) throws UnknownHostException {
            this.protocol = protocol;
            this.ip = ip;

            // set optional parameters to defaults
            this.name = Communicate.NAME;
            this.maxClients = 2000;
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
            this.consistencyPolicy = new ReadYourWritesPolicy();
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

        public Builder consistencyPolicy(ConsistencyPolicy consistencyPolicy) {
            this.consistencyPolicy = consistencyPolicy;
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
    public boolean Join(String IP, int Port, String existingClientId, String previousServer)
            throws NotBoundException, IOException, InterruptedException {

        System.out.println("Joining client with id " + existingClientId + " to " + this.port);
        synchronized (numClientsLock) {
            if(numClients >= maxClients) {
                LOGGER.log(Level.SEVERE, "Maximum clients exceeded for server " + getThisServersIpPortString());
                return false;
            }
            numClients++;
        }
        String finalizedId = registerClientInServer(IP, Port, existingClientId);
        consistencyPolicy.enforceOnJoin(IP, Port, finalizedId, previousServer);

        return true;
    }

    private String registerClientInServer(String ip, int port, String existingClientId) throws IOException, NotBoundException {
        dispatcher.addNewClient(ip, port);
        if(existingClientId == null) {
            return generateAndReturnClientId(ip, port);
        } else {
            System.out.println("Need to enforce consistency");
            dispatcher.setClientIdFor(ip, port, protocol.stripPadding(existingClientId));
            return existingClientId;
        }

    }

    private String generateAndReturnClientId(String ip, int port) throws IOException, NotBoundException {
        // System.out.println(getThisServersIpPortString() + " going to getCoordinator() in Join() for new client ID");
        String newClientId = protocol.stripPadding(peerListManager.getCoordinator().requestNewClientId());
        dispatcher.setClientIdFor(ip, port, newClientId);
        dispatcher.returnClientIdToClient(ip, port, newClientId);
        System.out.println("Client ID generated: " + newClientId);
        return newClientId;
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
//        System.out.println("Going through pubsubserver to dispatcher with message " + queryMessage);
        return dispatcher.retrieve(IP, Port, queryMessage);
    }

    @Override
    public boolean Publish(String Message, String IP, int Port) throws RemoteException {
        //        System.out.println("Trying to add message " + Message + " to store, server port " + this.port);

        // Commented out because servers will be publishing to each other through peerClients, but messages will contain
        // original messageId/clientId
        // return protocol.areInternalFieldsBlank(Message) && dispatcher.publish(Message, IP, Port);
        try {
            Message newMessage = new Message(protocol, Message, false);
            ensureMessageInternalsExistAndRegenerateQuery(IP, Port, newMessage);
            return consistencyPolicy.enforceOnPublish(newMessage, IP, Port);
        } catch (IOException | NotBoundException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Exception while trying to Publish():");
            e.printStackTrace();
            return false;
        }
    }

    private void ensureMessageInternalsExistAndRegenerateQuery(String clientIp, int clientPort, Message newMessage) throws IOException, NotBoundException {
        String clientId = newMessage.getClientId();
        String messageId = newMessage.getMessageId();
        // If message had no clientId, message is directly from a user client and manager will have id. If it doesn't it will throw exception.
        if(clientId.isEmpty()) {
            clientId = dispatcher.getClientIdFromManager(clientIp, clientPort);
            newMessage.insertClientId(clientId);
        }
        // If message had no messageId, message is directly from a user client. Otherwise it should already have a messageId.
        if(messageId.isEmpty()) {
            // TODO: this might not work: we may need to match coordinator with the right peerClient and make calls through that...
            messageId = getCoordinator().requestNewMessageId();
            newMessage.insertMessageId(messageId);
        }
        newMessage.regenerateQuery();
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

    void publishToCoordinator(Message publication) throws IOException, NotBoundException {
        peerListManager.publishToCoordinator(publication);
    }

    void publishToAllPeers(Message publication) throws RemoteException {
        peerListManager.publishToAllPeers(publication);
    }

    List<Message> retrieveFromPeer(String server, Message queryMessage) throws InterruptedException {
        return peerListManager.retrieveFromPeer(server, queryMessage);
    }

    boolean isCoordinator() throws IOException, NotBoundException {
        return peerListManager.isCoordinator();
    }

    String getCoordinatorIp() throws IOException, NotBoundException {
        return peerListManager.getCoordinatorIp();
    }

    int getCoordinatorPort() throws IOException, NotBoundException {
        return peerListManager.getCoordinatorPort();
    }

    String getIp() {
        return ip.getHostAddress();
    }

    int getPort() {
        return port;
    }

}
