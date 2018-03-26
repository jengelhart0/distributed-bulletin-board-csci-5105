import client.Client;
import message.Message;
import org.junit.Test;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

public class TestReadYourWritesConsistency extends ReadYourWritesClientSetup {
    private static final Logger LOGGER = Logger.getLogger( TestReadYourWritesConsistency.class.getName() );

    @Test
    public void testReadYourWritesSingleClient() throws IOException, NotBoundException {
        Client testClient = clients[0];
        String testMessage = ";my non-reply test content 1";
        testClient.publish(new Message(testProtocol1, testMessage, false));
        testClient.leave();

        testClient.initializeRemoteCommunication(testServerIp, serverPorts.get(1), serverInterfaceName);

        List<Message> results = testClient.retrieve(
                new Message(testProtocol1, testProtocol1.getRetrieveAllQuery(), true));

        boolean found = false;
        for(Message message: results) {
            if(testProtocol1.stripPadding(message.withoutInternalFields()).equals(testMessage)) {
                found = true;
            }
        }

        testClient.terminateClient();
        assertTrue(found);
    }

}
