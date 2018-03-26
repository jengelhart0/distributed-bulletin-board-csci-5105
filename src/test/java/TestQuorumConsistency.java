import client.Client;
import message.Message;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

public class TestQuorumConsistency extends QuorumClientSetup {
    private static final Logger LOGGER = Logger.getLogger( TestQuorumConsistency.class.getName() );

    public void testQuorumConsistencySimple() throws RemoteException, NotBoundException {
        if(numTestServers >= 3) {
            Client testClient1 = clients[0];
            Client testClient2 = clients[numClientsPerServer];
            Client testClient3 = clients[2 * numClientsPerServer];

            String port1 = testClient1.getServer().getThisServersIpPortString().split(testProtocol1.getDelimiter())[1];
            String port2 = testClient2.getServer().getThisServersIpPortString().split(testProtocol1.getDelimiter())[1];
            String port3 = testClient3.getServer().getThisServersIpPortString().split(testProtocol1.getDelimiter())[1];

//            try {
//                Thread.sleep(500);
//            } catch (InterruptedException e) {
//
//            }

            String message1 = ";non-reply testClient1 at server on port" + port1;
            String message2 = ";non-reply testClient2 at server on port" + port2;
            String message3 = ";non-reply testClient3 at server on port" + port3;

            testClient1.publish(new Message(
                    testProtocol1,
                    message1,
                    false));
            System.out.println("TestQuorum: After return from testClient1.publish()");
            testClient2.publish(new Message(
                    testProtocol1,
                    message2,
                    false));
            testClient3.publish(new Message(
                    testProtocol1,
                    message3,
                    false));

//            try {
//                Thread.sleep(500);
//            } catch (InterruptedException e) {
//                LOGGER.log(Level.WARNING,
//                        "Interrupted while waiting to give publications a chance to " +
//                                "propagate in testSequentialConsistencySimple");
//                assertTrue(false);
//            }

            System.out.println("TestQuorum: Attempting to retrieve from clients now");
            List<List<Message>> resultLists = new LinkedList<>();
            resultLists.add(testClient3.retrieve(new Message(testProtocol1, ";", true)));
            resultLists.add(testClient1.retrieve(new Message(testProtocol1, ";", true)));
            resultLists.add(testClient2.retrieve(new Message(testProtocol1, ";", true)));

            // Each retrieve should require its server to read from the server with the most recent update (i.e., one
            // that stores the highest messageId)
            assertTrue(
            (testClient1.getHighestMessageIdStoredAtServer()
                    == testClient2.getHighestMessageIdStoredAtServer())
                    &&
                    (testClient2.getHighestMessageIdStoredAtServer()
                            == testClient3.getHighestMessageIdStoredAtServer()));

            int numMessage1 = 0, numMessage2 = 0, numMessage3 = 0;
            for(List<Message> resultList: resultLists) {
                for (Message m : resultList) {
                    if (m.asRawMessage().equals(message1)) {
                        numMessage1++;
                    }
                    if (m.asRawMessage().equals(message2)) {
                        numMessage2++;
                    }
                    if (m.asRawMessage().equals(message3)) {
                        numMessage3++;
                    }
                }
            }
            assertTrue(numMessage1 >= writeQuorum && numMessage2 >= writeQuorum && numMessage3 >= writeQuorum);
        } else {
            LOGGER.log(Level.WARNING, "Need at least 3 for numTestServers for test testQuorumConsistencySimple");
            assertTrue(false);
        }
    }
}
