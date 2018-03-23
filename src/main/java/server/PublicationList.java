package server;

import message.Protocol;

import java.util.*;
import java.util.logging.Logger;

class PublicationList {
    private static final Logger LOGGER = Logger.getLogger( ReplicatedPubSubServer.class.getName() );

    private SortedSet<String> publications;
    private final Object listLock = new Object();
    private Protocol protocol;
    PublicationList(Protocol protocol) {
        this.protocol = protocol;
        this.publications = new TreeSet<>(new MessageIdComparator(protocol));
    }

    SortedSet<String> getPublicationsStartingAt(String lastReceived) {
        synchronized (listLock) {
            if (this.publications.contains(lastReceived)) {
                SortedSet<String> result = new TreeSet<>(new MessageIdComparator(protocol));
                result.addAll(this.publications.tailSet(lastReceived));
                result.remove(lastReceived);
                return result;
            }
            if (lastReceived.equals("")) {
                SortedSet<String> result = new TreeSet<>(new MessageIdComparator(protocol));
                result.addAll(this.publications);
                return result;

            }
            return new TreeSet<>(new MessageIdComparator(protocol));
        }
    }

    Integer synchronizedAdd(String message) {
        synchronized (listLock) {
            this.publications.add(message);
            return this.publications.size() - 1;
        }
    }

}
