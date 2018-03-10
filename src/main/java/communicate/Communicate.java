package communicate;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Communicate extends Remote {
    String NAME = "Communicate";

    enum RemoteMessageCall {
        JOIN, LEAVE, PUBLISH, SUBSCRIBE, UNSUBSCRIBE
    }

    boolean JoinServer(String IP, int Port) throws RemoteException;
    boolean LeaveServer(String IP, int Port) throws RemoteException;
    boolean Join(String IP, int Port) throws RemoteException;
    boolean Leave(String IP, int Port) throws RemoteException;
    boolean Subscribe(String IP, int Port, String Message) throws RemoteException;
    boolean Unsubscribe(String IP, int Port, String Message) throws RemoteException;
    boolean Publish(String Message, String IP, int Port) throws RemoteException;
    boolean PublishServer(String Message, String IP, int Port) throws RemoteException;
    boolean Ping() throws RemoteException;
}
