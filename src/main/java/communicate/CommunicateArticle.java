package communicate;

import message.Protocol;

import java.util.*;

public interface CommunicateArticle extends Communicate {
    int MAXCLIENTS = 2000;

    int SERVER_LIST_SIZE = 1024;

    String REGISTRY_SERVER_IP = "localhost";
    int REGISTRY_SERVER_PORT = 5105;
    int HEARTBEAT_PORT = 9453;
    int REMOTE_OBJECT_PORT = 1099;

    Protocol ARTICLE_PROTOCOL = new Protocol(
            Arrays.asList("type", "orginator", "org"),
            Arrays.asList(
                    new String[]{"", "Sports", "Lifestyle", "Entertainment", "Business", "Technology", "Science", "Politics", "Health"},
                    new String[]{""},
                    new String[]{""}),

            ";",
            "",
            120);
}
