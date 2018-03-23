package communicate;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Communicate extends Remote {
    String NAME = "Communicate";

    enum RemoteMessageCall {
        JOIN, LEAVE, PUBLISH, SUBSCRIBE, UNSUBSCRIBE, RETRIEVE
    }

    boolean Join(String IP, int Port, String existingClientId, String previousServer)
            throws NotBoundException, IOException, InterruptedException;
    boolean Leave(String IP, int Port) throws RemoteException;
    boolean Subscribe(String IP, int Port, String Message) throws RemoteException;
    boolean Unsubscribe(String IP, int Port, String Message) throws RemoteException;
    boolean Retrieve(String IP, int Port, String queryMessage) throws RemoteException;
    boolean Publish(String Message, String IP, int Port) throws RemoteException;
    boolean Ping() throws RemoteException;
    Communicate getCoordinator() throws NotBoundException, IOException;
//    boolean isCoordinatorKnown() throws RemoteException;
    String requestNewClientId() throws IOException, NotBoundException;
    String requestNewMessageId() throws IOException, NotBoundException;
    String getThisServersIpPortString() throws RemoteException;
}
