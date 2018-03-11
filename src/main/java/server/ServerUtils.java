package server;

import message.Protocol;

public class ServerUtils {
    static String getIpPortString(String ip, int port, Protocol protocol) {
        return ip + protocol.getDelimiter() + Integer.toString(port);
    }
}
