package server;

import message.Message;
import message.Protocol;
import runnableComponents.Scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.Executors.newCachedThreadPool;

class Dispatcher {
    private static final Logger LOGGER = Logger.getLogger( Dispatcher.class.getName() );

    private Protocol protocol;
    private ExecutorService clientTaskExecutor;

    private Map<String, CommunicationManager> clientToClientManager;

    private boolean shouldRetrieveMatchesAutomatically;
    private Scheduler subscriptionPullScheduler;

    private MessageStore store;

    Dispatcher(Protocol protocol, MessageStore store, boolean shouldRetrieveMatchesAutomatically) {
        this.protocol = protocol;
        this.store = store;
        this.shouldRetrieveMatchesAutomatically = shouldRetrieveMatchesAutomatically;
        this.clientToClientManager = new ConcurrentHashMap<>();
        // manages publications from clients connected to other servers
        clientToClientManager.put(
                "clientElsewhere" + protocol.getDelimiter() + "-1",
                new ClientManager("clientElsewhere", -1, protocol));
    }

    void initialize() {
        createClientTaskExecutor();
        if(shouldRetrieveMatchesAutomatically) {
            startSubscriptionPullScheduler();
        }
    }

    void cleanup() {
        clientTaskExecutor.shutdown();
        if(shouldRetrieveMatchesAutomatically) {
            subscriptionPullScheduler.tellThreadToStop();
        }
    }

    private void createClientTaskExecutor() {
        this.clientTaskExecutor = newCachedThreadPool();
    }

    private void startSubscriptionPullScheduler() {
        this.subscriptionPullScheduler = new Scheduler() {
            @Override
            public void run() {
                try {
                    while (shouldThreadContinue()) {
                        Thread.sleep(500);
                        for (CommunicationManager manager: clientToClientManager.values()) {
                            queueTaskFor(manager, CommunicationManager.Call.PULL_MATCHES, null);
                        }
                    }
                } catch (InterruptedException e) {
                    LOGGER.log(Level.SEVERE, e.toString());
                    e.printStackTrace();
                    throw new RuntimeException("Failure in subscription pull scheduler thread.");
                }
            }
        };
        new Thread(subscriptionPullScheduler).start();
    }

    public void addNewClient(String ip, int port) {
        if(!clientToClientManager.containsKey(ServerUtils.getIpPortString(ip, port, protocol))) {
            CommunicationManager newClientManager = new ClientManager(ip, port, this.protocol);
            clientToClientManager.put(ServerUtils.getIpPortString(ip, port, protocol), newClientManager);
        }
    }

    public boolean informManagerThatClientLeft(String ip, int port) {
        CommunicationManager whoseClientLeft = clientToClientManager.remove(ServerUtils.getIpPortString(ip, port, protocol));
        if(whoseClientLeft != null) {
            whoseClientLeft.clientLeft();
            return true;
        }
        return false;
     }

    void setClientIdFor(String ip, int port, String clientId) {
        getManagerFor(ip, port).setClientId(clientId);
    }

    public boolean returnClientIdToClient(String IP, int Port, String clientId) {
        String clientIdMessage = "clientId" + protocol.getControlDelimiter() + clientId;
        return createMessageTask(IP, Port, clientIdMessage, CommunicationManager.Call.RETURN_CLIENT_ID_TO_CLIENT, false);
    }

    public boolean subscribe(String IP, int Port, String Message) {
        return createMessageTask(IP, Port, Message, CommunicationManager.Call.SUBSCRIBE, true);
    }

    public boolean unsubscribe(String IP, int Port, String Message) {
        return createMessageTask(IP, Port, Message, CommunicationManager.Call.UNSUBSCRIBE, true);
    }

    public boolean retrieve(String IP, int Port, String queryMessage) {
//        System.out.println("Going straight through dispatcher with message " + queryMessage);

        return createMessageTask(IP, Port, queryMessage, CommunicationManager.Call.RETRIEVE, true);
    }

    public boolean publish(String Message, String IP, int Port) {
        return createMessageTask(IP, Port, Message, CommunicationManager.Call.PUBLISH, false);
    }

    private boolean createMessageTask(String ip, int port, String rawMessage,
                                      CommunicationManager.Call call, boolean isSubscription) {

        Message newMessage = createNewMessage(rawMessage, isSubscription);
        if(newMessage == null) {
            return false;
        }
        CommunicationManager manager = getManagerFor(ip, port);
        if(manager == null) {
            LOGGER.log(Level.WARNING, "Client had no manager. May not have joined.");
            return false;
        }
        queueTaskFor(manager, call, newMessage);
        return true;
    }

    private Message createNewMessage(String rawMessage, boolean isSubscription) {
        try {
            return new Message(this.protocol, rawMessage, isSubscription);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Invalid message received from");
            e.printStackTrace();
            return null;
        }
    }

    CommunicationManager getManagerFor(String ip, int port) {
        return clientToClientManager.getOrDefault(ServerUtils.getIpPortString(ip, port, protocol), null);
    }

    private void queueTaskFor(CommunicationManager manager, CommunicationManager.Call call, Message message) {
        this.clientTaskExecutor.execute(manager.task(message, store, call));
    }
}
