package server;

class CoordinationState {
    private int nextMessageId;
    private final Object messageIdLock = new Object();
    private int nextClientId;
    private final Object clientIdLock = new Object();

    CoordinationState() {
        this.nextMessageId = 0;
        this.nextClientId = 0;
    }

    String requestNewMessageId() {
        int next;
        synchronized (messageIdLock) {
            next = nextMessageId++;
        }
        return Integer.toString(next);
    }

    String requestNewClientId() {
        int next;
        synchronized (clientIdLock) {
            next = nextClientId++;
        }
        return Integer.toString(next);
    }
}
