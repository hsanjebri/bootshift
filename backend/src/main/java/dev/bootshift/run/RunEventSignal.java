package dev.bootshift.run;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

/**
 * Wakes SSE streams when a new event is written. The event table stays the source of truth:
 * a stream reads {@link #version()}, queries the table, then waits for the version to move on.
 * Taking the version before the query means an event written in between is never missed.
 */
@Component
public class RunEventSignal {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private long version;

    public long version() {
        lock.lock();
        try {
            return version;
        } finally {
            lock.unlock();
        }
    }

    void fire() {
        lock.lock();
        try {
            version++;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Waits until the version is past {@code seen} or the timeout elapses. Returns true if it changed. */
    public boolean awaitChange(long seen, Duration timeout) throws InterruptedException {
        long nanos = timeout.toNanos();
        lock.lock();
        try {
            while (version == seen && nanos > 0) {
                nanos = changed.awaitNanos(nanos);
            }
            return version != seen;
        } finally {
            lock.unlock();
        }
    }
}
