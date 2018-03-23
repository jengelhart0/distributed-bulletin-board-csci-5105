import client.Client;
import message.Message;
import org.junit.Test;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

public class TestSequentialConsistency extends SequentialClientSetup {
    private static final Logger LOGGER = Logger.getLogger( TestSequentialConsistency.class.getName() );

    @Test
    public void testSequentialConsistencySimple() throws RemoteException, NotBoundException {
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
            testClient1.publish(new Message(
                    testProtocol1,
                    ";non-reply testClient1 at server on port" + port1,
                    false));
            System.out.println("TestSequential: After return from testClient1.publish()");
            testClient2.publish(new Message(
                    testProtocol1,
                    ";non-reply testClient2 at server on port" + port2,
                    false));
            testClient3.publish(new Message(
                    testProtocol1,
                    ";non-reply testClient3 at server on port" + port3,
                    false));

//            try {
//                Thread.sleep(500);
//            } catch (InterruptedException e) {
//                LOGGER.log(Level.WARNING,
//                        "Interrupted while waiting to give publications a chance to " +
//                                "propagate in testSequentialConsistencySimple");
//                assertTrue(false);
//            }

            System.out.println("TestSequential: Attempting to retrieve from clients now");
            List<Message> results3 = testClient3.retrieve(new Message(testProtocol1, ";", true));
            List<Message> results1 = testClient1.retrieve(new Message(testProtocol1, ";", true));
            List<Message> results2 = testClient2.retrieve(new Message(testProtocol1, ";", true));

            assertTrue((results1.size() == results2.size()) && (results2.size() == results3.size()));
            for(int i = 0; i < results1.size(); i++) {
                assertTrue(
                        results1.get(i).asRawMessage()
                        .equals(results2.get(i).asRawMessage())
                        && results2.get(i).asRawMessage()
                        .equals(results3.get(i).asRawMessage())
                );
            }
        } else {
            LOGGER.log(Level.WARNING, "Need at least 3 for numTestServers for test testSequentialConsistencySimple");
            assertTrue(false);
        }
    }

    @Test
    public void testSequentialConsistencyComplex() throws RemoteException, NotBoundException {
        for(int i = 0; i < 8; i++) {
            for(Client client: clients) {
                simulateRandomNetworkDelay(75);
                // the modulo and integer division simulate choices to post original message or to reply
                if(i % 3 == 0) {
                    client.publish(new Message(
                            testProtocol1,
                            ";test message " + i + " from client at port " + client.getListenPort(),
                            false));
                } else {
                    client.publish(new Message(
                            testProtocol1,
                            i / 3 + ";test message " + i + " from client at port " + client.getListenPort(),
                            false));

                }
            }
        }
        List<List<Message>> results = new LinkedList<>();
        for(Client client: clients) {
            simulateRandomNetworkDelay(25);
            results.add(client.retrieve(new Message(testProtocol1, ";", true)));
        }

        int expectedSize = results.get(0).size();

        for(List<Message> result: results) {
            assertTrue(result.size() == expectedSize);
        }

        List<Message> standard = results.get(0);
        for(int i = 0; i < expectedSize; i++) {
            String expected = standard.get(i).asRawMessage();
//            System.out.println("expected: " + expected);
            for(int j = 1; j < results.size(); j++) {
//                System.out.println("actual: " + results.get(j).get(i).asRawMessage());
                assertTrue(results.get(j).get(i)
                        .asRawMessage()
                        .equals(expected));
            }
        }
    }

    public void simulateRandomNetworkDelay(int maxDelay) {
        int randomDelay = ThreadLocalRandom.current().nextInt(0, maxDelay);
        try {
            Thread.sleep(randomDelay);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Thread interrupting while sleeping in simulateRandomNetworkDelay.");
        }
    }

}
