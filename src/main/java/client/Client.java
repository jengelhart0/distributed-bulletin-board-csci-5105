package client;

import java.io.IOException;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import communicate.Communicate;
import communicate.Communicate.RemoteMessageCall;
import message.Message;
import message.Protocol;

public class Client implements Runnable {
    private static final Logger LOGGER = Logger.getLogger( Client.class.getName() );

    private Communicate communicate = null;
    private final Object communicateLock = new Object();
    private String communicateName;
    private String remoteHost;
    private int remoteServerPort;
    private String previousServer;
    private Protocol protocol;

    private boolean terminate;
    private final Object terminateLock = new Object();

    private ClientListener listener = null;
    private int listenPort;

    private InetAddress localAddress;

    private String id;
    private final Lock idReceivedByListenerLock = new ReentrantLock();
    private final Condition idHasBeenSet = idReceivedByListenerLock.newCondition();

    public Client(Protocol protocol, int listenPort) throws IOException, NotBoundException {
        this.protocol = protocol;
        this.localAddress = InetAddress.getLocalHost();
        this.listenPort = listenPort;
        this.id = null;
        this.terminate = false;
        this.previousServer = null;

        startMessageListener();
        new Thread(this).start();
    }

    private void startMessageListener() throws SocketException {
        this.listener = new ClientListener(this.protocol, idReceivedByListenerLock, idHasBeenSet);
        this.listener.listenAt(this.listenPort, this.localAddress);
        Thread listenerThread = new Thread(this.listener);
        listenerThread.start();

        if(!listenerThread.isAlive()) {
            throw new RuntimeException();
        }
    }

    public void initializeRemoteCommunication(String remoteHost, int remoteServerPort, String communicateName)
            throws RemoteException, NotBoundException {
        this.remoteHost = remoteHost;
        this.remoteServerPort = remoteServerPort;
        this.communicateName = communicateName;

        establishRemoteObject();
        if (!join()) {
            LOGGER.log(Level.WARNING, "Server refused returnClientIdToClient (likely because it already has MAXCLIENTS). Cleaning up...");
            cleanup();
        }
    }

    private void establishRemoteObject() throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(this.remoteHost, this.remoteServerPort);
        synchronized (communicateLock) {
            this.communicate = (Communicate) registry.lookup(this.communicateName);
        }
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
            boolean isCallSuccessful;
            synchronized (communicateLock) {
                isCallSuccessful = makeCall(message, call);
            }
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
                    "It appears client's remote communication is unestablished."
            );
        }

        String address = this.localAddress.getHostAddress();
        if (message == null) {
            switch (call) {
                case JOIN:
                    boolean isCallSuccessful = this.communicate.Join(address, this.listenPort, this.id, this.previousServer);
                    if (isCallSuccessful) {
                        ensureThisHasId();
                    }
                    this.previousServer = remoteHost + protocol.getDelimiter() + remoteServerPort;
                    return isCallSuccessful;
                case LEAVE:
                    this.communicate.Leave(address, this.listenPort);
                    this.communicate = null;
                    return true;
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

    private void ensureThisHasId() {
        if (id == null) {
            idReceivedByListenerLock.lock();
            try {
                idHasBeenSet.await();
                id = listener.getReceivedClientId();
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING,
                        "Client interrupted while waiting for its ID to be set! " +
                                "May result in unusual behavior (e.g., trying to publish without a client id).");
            } finally {
                idReceivedByListenerLock.unlock();
            }
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
                synchronized (communicateLock) {
                    if (communicate != null) {
                        ping();
                    }
                }
                Thread.sleep(10000);
            }
        } catch (RemoteException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, e.toString());
            e.printStackTrace();
        } finally {
            cleanup();
        }
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

    public Communicate getServer() {
        synchronized (communicateLock) {
            return this.communicate;
        }
    }

    public List<Message> getCurrentMessageFeed() {
        return this.listener.getCurrentMessageFeed();
    }
}
