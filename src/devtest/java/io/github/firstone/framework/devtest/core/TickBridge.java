package io.github.firstone.framework.devtest.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Runs the test thread in step with the game's client ticks, so a test reads like a plain script
 * ({@code hold forward; wait 100 ticks; check the speed})
 *
 * <p>Only one of the two threads runs at a time. At the end of a client tick the client thread hands over to the test
 * thread and waits; the test thread runs until it waits for ticks again ({@link #waitTicks}), then the game goes on.
 * So test code sees the game paused between two ticks and may read and change client state directly. Code that must
 * run on the client thread itself (rendering, screenshots, world loading) goes through {@link #onClientThread}.</p>
 */
final class TickBridge {

    /** Marks the end of the test thread's turn */
    private static final Runnable YIELD = () -> {};

    /** Longest time the game waits for the test thread to finish its turn */
    private static final long TURN_TIMEOUT_SECONDS = 120;

    private final BlockingQueue<Runnable> clientTasks = new LinkedBlockingQueue<>();
    private final Semaphore testTurn = new Semaphore(0);

    /** Ticks left until the test thread's next turn */
    private volatile int ticksToWait;

    /** The test thread has finished: the game no longer hands over */
    private volatile boolean closed;

    /** The client thread is inside {@link #onClientTick} (world loading runs ticks inside a task) */
    private boolean inTurn;

    /**
     * Called by the client thread at the end of every client tick: hands over to the test thread when its wait is
     * over, and runs the tasks it sends until it waits again
     *
     * @return false if the test thread did not finish its turn in time
     */
    boolean onClientTick() {
        if (this.inTurn || this.closed || --this.ticksToWait > 0) {
            return true;
        }
        this.inTurn = true;
        try {
            this.testTurn.release();
            while (true) {
                Runnable task = this.clientTasks.poll(TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (task == null) {
                    return false;
                }
                if (task == YIELD) {
                    return true;
                }
                task.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            this.inTurn = false;
        }
    }

    /** Called by the test thread once at its start: waits for its first turn */
    void awaitFirstTurn() {
        this.testTurn.acquireUninterruptibly();
    }

    /**
     * Called by the test thread: lets the game run the given number of client ticks
     *
     * @param ticks ticks to wait, at least 1
     */
    void waitTicks(int ticks) {
        this.ticksToWait = Math.max(1, ticks);
        this.clientTasks.add(YIELD);
        this.testTurn.acquireUninterruptibly();
    }

    /**
     * Called by the test thread: runs code on the client thread and returns its result
     *
     * @param task the code
     * @param <T>  type of the result
     * @return the result
     */
    <T> T onClientThread(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        this.clientTasks.add(() -> {
            try {
                result.complete(task.get());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return join(result);
    }

    /**
     * Waits for a result and throws the task's own exception if it failed (not the wrapping one)
     *
     * @param future the result
     * @param <T>    type of the result
     * @return the result
     */
    static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw e;
        }
    }

    /** Called by the test thread when it has finished: the game runs on without it */
    void close() {
        this.closed = true;
        this.clientTasks.add(YIELD);
    }
}
