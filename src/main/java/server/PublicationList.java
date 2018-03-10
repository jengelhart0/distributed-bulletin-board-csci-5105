package server;

import java.util.*;
import java.util.logging.Logger;

class PublicationList {
    private static final Logger LOGGER = Logger.getLogger( Coordinator.class.getName() );

    private SortedSet<String> publications;
    private final Object listLock = new Object();

    PublicationList() {
        this.publications = new TreeSet<>();
    }

    SortedSet<String> getPublicationsStartingAt(String lastReceived) {
        synchronized (listLock) {
            if (this.publications.contains(lastReceived)) {
                SortedSet<String> result = new TreeSet<>(this.publications.tailSet(lastReceived));
                result.remove(lastReceived);
                return result;
            }
            if (lastReceived.equals("")) {
                return new TreeSet<>(this.publications);
            }
            return new TreeSet<>();
        }
    }

    Integer synchronizedAdd(String message) {
        synchronized (listLock) {
            this.publications.add(message);
            return this.publications.size() - 1;
        }
    }

}
