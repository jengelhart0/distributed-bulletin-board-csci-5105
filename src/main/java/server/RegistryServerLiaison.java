package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

class RegistryServerLiaison {

    private HeartbeatListener heartbeatListener;
    private int heartbeatPort;

    private String serverName;
    private InetAddress serverIp;
    private int serverPort;

    private InetAddress registryServerAddress;
    private int registryServerPort;
    private String registerMessage;
    private String deregisterMessage;
    private String registryMessageDelimiter;
    private int registryMessageSize;
    private int serverListSize;

    RegistryServerLiaison(int heartbeatPort, InetAddress registryServerAddress, int registryServerPort,
                          String registryMessageDelimiter, int registryMessageSize, int serverListSize) {

        this.heartbeatPort = heartbeatPort;
        this.registryServerAddress = registryServerAddress;
        this.registryServerPort = registryServerPort;
        this.registryMessageDelimiter = registryMessageDelimiter;
        this.registryMessageSize = registryMessageSize;
        this.serverListSize = serverListSize;
    }

    void initialize(String serverName, InetAddress serverIp, int serverPort) throws IOException {
        this.serverName = serverName;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        setRegistryServerMessages();
        startHeartbeat();
        registerWithRegistryServer();
    }

    void cleanup() throws IOException {
        heartbeatListener.tellThreadToStop();
        heartbeatListener.forceCloseSocket();
        deregisterFromRegistryServer();
    }

    private void setRegistryServerMessages() {
        String ip = serverIp.getHostAddress();
        this.registerMessage = "Register;RMI;" + ip + ";" + heartbeatPort + ";" + serverName + ";" + serverPort;
        this.deregisterMessage = "Deregister;RMI;" + ip + ";" + heartbeatPort;
    }

    private void startHeartbeat() throws IOException {
        this.heartbeatListener = new HeartbeatListener(registryMessageSize);
        this.heartbeatListener.listenAt(this.heartbeatPort, this.serverIp);
        Thread heartbeatThread = new Thread(this.heartbeatListener);
//        heartbeatListener.setThread(heartbeatThread);
        heartbeatThread.start();

        if(!heartbeatThread.isAlive()) {
            throw new RuntimeException();
        }
    }

    private void registerWithRegistryServer() throws IOException {
        sendRegistryServerMessage(this.registerMessage);
    }

    private void deregisterFromRegistryServer() throws IOException {
        sendRegistryServerMessage(this.deregisterMessage);
    }

    private void sendRegistryServerMessage(String rawMessage) throws IOException {
        DatagramPacket packet = makeRegistryServerPacket(rawMessage);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(packet);
        }
    }

    private DatagramPacket makeRegistryServerPacket(String rawMessage) {
        DatagramPacket packet = new DatagramPacket(new byte[registryMessageSize], registryMessageSize,
                this.registryServerAddress, registryServerPort);
        packet.setData(rawMessage.getBytes());
        return packet;
    }

    public Set<String> getListOfServers() throws IOException {
        int listSizeinBytes = this.serverListSize;
        DatagramPacket registryPacket = new DatagramPacket(new byte[listSizeinBytes], listSizeinBytes);

        try (DatagramSocket getListSocket = new DatagramSocket()) {
            String getListMessage = "GetList;RMI;"
                    + serverIp.getHostAddress()
                    + ";"
                    + this.heartbeatPort;
            // sending here to minimize chance response arrives before we listen for it
            getListSocket.send(makeRegistryServerPacket(getListMessage));
            getListSocket.receive(registryPacket);
        }
        String[] rawServerList = new String(registryPacket.getData(), 0, registryPacket.getLength(), "UTF8")
                .split(registryMessageDelimiter);

        Set<String> results = new HashSet<>();
        for (int i = 0; i < rawServerList.length; i+=2) {
            results.add(rawServerList[i] + ";" + rawServerList[i+1]);
        }
        return results;
    }

//    boolean isAlive() {
//        return heartbeatListener.isAlive();
//    }

}
