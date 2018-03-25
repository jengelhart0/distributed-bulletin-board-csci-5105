package bulletinboard;

import message.Protocol;

import java.util.Arrays;

public interface BulletinBoard {
            Protocol BBPROTOCOL =  new Protocol(
            Arrays.asList("replyTo", "title"),
                Arrays.asList(new String[]{""}, new String[]{""}),
            ";",
            "",
            256);

            int NUM_TO_DISPLAY = 10;
}
