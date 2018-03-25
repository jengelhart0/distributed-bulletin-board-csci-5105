package client;

import message.Protocol;

import java.io.IOException;
import java.net.BindException;
import java.rmi.NotBoundException;

public class ClientUtils {
    public static Client tryToCreateNewClientAt(Protocol protocol, int listenPort) throws IOException, NotBoundException {
        try {
            return new Client(protocol, listenPort);
        } catch (BindException e) {
            return null;
        }
    }
}
