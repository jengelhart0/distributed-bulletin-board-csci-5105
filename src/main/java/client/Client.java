package client;

import java.io.IOException;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import communicate.Communicate;
import communicate.CommunicateArticle;
import communicate.Communicate.RemoteMessageCall;
import message.Message;
import message.Protocol;

public class Client implements Runnable {
    private static final Logger LOGGER = Logger.getLogger( Client.class.getName() );

    private Communicate communicate = null;
    private String communicateName;
    private String remoteHost;
    private int remoteServerPort;
    private Protocol protocol;

    private boolean terminate;
    private final Object terminateLock = new Object();

    private ClientListener listener = null;
    private int listenPort;

    private InetAddress localAddress;

    public Client (String remoteHost, int remoteServerPort, String communicateName, Protocol protocol,
                   int listenPort) throws IOException, NotBoundException {
        this.remoteHost = remoteHost;
        this.remoteServerPort = remoteServerPort;
        this.communicateName = communicateName;
        this.protocol = protocol;
        this.localAddress = InetAddress.getLocalHost();
        this.listenPort = listenPort;
        this.terminate = false;

        initializeRemoteCommunication();
    }

    public boolean join() {
        return communicateWithRemote(RemoteMessageCall.JOIN);
    }

    public boolean leave() {
        return communicateWithRemote(RemoteMessageCall.LEAVE);
    }

    public boolean publish(Message message) {
        return communicateWithRemote(message, RemoteMessageCall.PUBLISH);
    }

    public boolean subscribe(Message subscription) {
        return communicateWithRemote(subscription, RemoteMessageCall.SUBSCRIBE);
    }

    public boolean unsubscribe(Message subscription) {
        return communicateWithRemote(subscription, RemoteMessageCall.UNSUBSCRIBE);
    }

    private boolean ping() throws RemoteException {
        return this.communicate.Ping();
    }

    private boolean communicateWithRemote(RemoteMessageCall call) {
        return communicateWithRemote(null, call);
    }

    private boolean communicateWithRemote(Message message, RemoteMessageCall call) {
        try {
            boolean isCallSuccessful = makeCall(message, call);
            if (!isCallSuccessful) {
                throw new RuntimeException("RMI returned false.");
            }
        } catch (RemoteException | IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Attempt to establish communication or communicate failed: " + e.toString());
            return false;
        }
        return true;
    }

    private boolean makeCall(Message message, RemoteMessageCall call)
            throws RemoteException {

        if (this.communicate == null) {
            throw new IllegalArgumentException(
                    "It appears client's remote communication is unestablished. " +
                            "Ensure you called start() on client thread."
            );
        }

        String address = this.localAddress.getHostAddress();
        if (message == null) {
            switch (call) {
                case JOIN:
                    return this.communicate.Join(address, this.listenPort);
                case LEAVE:
                    return this.communicate.Leave(address, this.listenPort);
                default:
                    throw new IllegalArgumentException(
                            "Either Invalid RemoteMessageCall passed or message was null");
            }
        }
        switch (call) {
            case PUBLISH:
                return this.communicate.Publish(
                        message.asRawMessage(), address, this.listenPort);
            case SUBSCRIBE:
                return this.communicate.Subscribe(
                        address, this.listenPort, message.asRawMessage());
            case UNSUBSCRIBE:
                return this.communicate.Unsubscribe(
                        address, this.listenPort, message.asRawMessage());
            default:
                throw new IllegalArgumentException("Invalid RemoteMessageCall passed");
        }
    }

    @Override
    public void run() {
        try {
            while(true) {
                synchronized (terminateLock) {
                    if (terminate) {
                        break;
                    }
                }
                ping();
                Thread.sleep(10000);
            }
        } catch (RemoteException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, e.toString());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void initializeRemoteCommunication() throws RemoteException, NotBoundException, SocketException {
        startMessageListener();
        establishRemoteObject();
        if (!join()) {
            LOGGER.log(Level.WARNING, "Server refused join (likely because it already has MAXCLIENTS). Cleaning up...");
            cleanup();
        }
    }

    private void startMessageListener() throws SocketException {
        this.listener = new ClientListener(this.protocol);
        this.listener.listenAt(this.listenPort, this.localAddress);
        Thread listenerThread = new Thread(this.listener);
        listenerThread.start();

        if(!listenerThread.isAlive()) {
            throw new RuntimeException();
        }
    }

    private void establishRemoteObject() throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(this.remoteHost);
        this.communicate = (Communicate) registry.lookup(this.communicateName);
    }

    public void terminateClient() {
        synchronized (terminateLock) {
            this.terminate = true;
        }
    }

    private void cleanup() {
        leave();
        this.listener.tellThreadToStop();
        this.listener.forceCloseSocket();
    }

    public List<Message> getCurrentMessageFeed() {
        return this.listener.getCurrentMessageFeed();
    }
}
