package server;

import message.Protocol;

public class ServerUtils {
    static String getIpPortString(String ip, int port, Protocol protocol) {
        return ip + protocol.getDelimiter() + Integer.toString(port);
    }

    static String getIpFromIpPortString(String ipPortString, Protocol protocol) {
        return ipPortString.split(protocol.getDelimiter())[0];
    }

    static int getPortFromIpPortString(String ipPortString, Protocol protocol) {
        return Integer.parseInt(ipPortString.split(protocol.getDelimiter())[1]);
    }

}
