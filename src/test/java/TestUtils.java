import java.util.concurrent.ThreadLocalRandom;

public class TestUtils {
    public static void simulateRandomNetworkDelay(int maxDelay) {
        int randomDelay = ThreadLocalRandom.current().nextInt(0, maxDelay);
        try {
            Thread.sleep(randomDelay);
        } catch (InterruptedException e) {
            System.out.println("Thread interrupting while sleeping in simulateRandomNetworkDelay.");
        }
    }
}
