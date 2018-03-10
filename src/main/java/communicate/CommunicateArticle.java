package communicate;

import message.Protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public interface CommunicateArticle extends Communicate {
    String NAME = "CommunicateArticle";

    int MAXCLIENTS = 2000;

    int SERVER_LIST_SIZE = 1024;

    String REGISTRY_SERVER_IP = "localhost";
    int REGISTRY_SERVER_PORT = 5104;
    int HEARTBEAT_PORT = 9453;
    int REMOTE_OBJECT_PORT = 1099;

    Protocol ARTICLE_PROTOCOL = new Protocol(
            new String[]{"type", "orginator", "org"},
            new String[][]{
                    new String[]{"", "Sports", "Lifestyle", "Entertainment", "Business", "Technology", "Science", "Politics", "Health"},
                    new String[]{""},
                    new String[]{""},
            },
            ";",
            "",
            120);
}
