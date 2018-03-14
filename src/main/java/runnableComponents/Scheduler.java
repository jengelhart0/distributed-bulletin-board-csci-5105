package runnableComponents;

public abstract class Scheduler implements Runnable {

    private boolean shouldThreadContinue;
    private final Object continueLock = new Object();

    public Scheduler() {
        tellThreadToContinue();
    }

    public void tellThreadToStop() {
        synchronized (this.continueLock) {
            this.shouldThreadContinue = false;
        }
    }

    private void tellThreadToContinue() {
        synchronized (this.continueLock) {
            this.shouldThreadContinue = true;
        }
    }

    protected boolean shouldThreadContinue() {
        synchronized (this.continueLock) {
            return this.shouldThreadContinue;
        }
    }

    @Override
    public abstract void run();
}
