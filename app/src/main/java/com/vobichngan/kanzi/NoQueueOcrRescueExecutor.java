package com.vobichngan.kanzi;

import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Experimental, strictly single-flight extra-OCR scheduler; not connected to the app.
 *
 * No queued camera frames: SynchronousQueue has zero buffering capacity.
 * Deadlines are monotonic and checked before work AND before publishing results.
 * Invalidated frame results never reach TTS/UI. The caller MUST own/close crop data.
 *
 * WARNING: Java interruption and post-hoc deadline checks CANNOT terminate a native
 * ONNX inference already running. A past-deadline inference can still cost CPU.
 * This executor is not a proof of UVC safety: device benchmarking is mandatory.
 * Successful callbacks run on THIS WORKER THREAD; consumers must post to Android
 * main thread and recheck epoch before modifying app state.
 */
public final class NoQueueOcrRescueExecutor implements AutoCloseable {
    public enum Submission { ACCEPTED, DISABLED, CLOSED, BUSY, COOLDOWN, DEADLINE }

    public static final class Config {
        public final boolean enabled;
        public final long minimumRemainingMs;
        public final long maximumAdditionalMs;
        public final long overrunCooldownMs;

        public Config(boolean enabled) {
            this(enabled, 35L, 25L, 8000L);
        }

        public Config(boolean enabled, long minimumRemainingMs,
                      long maximumAdditionalMs, long overrunCooldownMs) {
            if (minimumRemainingMs < 1L || maximumAdditionalMs < 1L ||
                maximumAdditionalMs > 1000L || overrunCooldownMs < 0L ||
                overrunCooldownMs > 600000L) {
                throw new IllegalArgumentException("Invalid rescue executor budgets");
            }
            this.enabled = enabled;
            this.minimumRemainingMs = minimumRemainingMs;
            this.maximumAdditionalMs = maximumAdditionalMs;
            this.overrunCooldownMs = overrunCooldownMs;
        }
    }

    private final Config config;
    private final ThreadPoolExecutor executor; // null when off: no worker thread.
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final AtomicLong epoch = new AtomicLong(0L);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile long blockedUntilNanos = Long.MIN_VALUE;

    public NoQueueOcrRescueExecutor() {
        this(new Config(false));
    }

    public NoQueueOcrRescueExecutor(Config config) {
        if (config == null) throw new IllegalArgumentException("config");
        this.config = config;
        executor = !config.enabled ? null : new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<Runnable>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "kanzi-selective-ocr-rescue");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Invalidates old-frame results; it never waits for an in-flight native call. */
    public void invalidate() {
        epoch.incrementAndGet();
    }

    /**
     * Fast, nonblocking submit. deadlineNanos MUST use System.nanoTime() clock domain.
     * This does not make a native OCR call cancellable.
     *
     * The work function and callback must NOT reference mutable camera buffers
     * or access Android UI directly. At most one call can be in flight.
     */
    public Submission trySubmit(final Callable<String> work,
                                final Consumer<String> onSuccess,
                                final long deadlineNanos) {
        if (!config.enabled) return Submission.DISABLED;
        if (closed.get()) return Submission.CLOSED;
        if (work == null || onSuccess == null)
            throw new IllegalArgumentException("work and onSuccess are required");
        final long now = System.nanoTime();
        if (blockedUntilNanos != Long.MIN_VALUE &&
            now - blockedUntilNanos < 0L) return Submission.COOLDOWN;
        final long minimumNs = TimeUnit.MILLISECONDS.toNanos(config.minimumRemainingMs);
        if (deadlineNanos - now < minimumNs) return Submission.DEADLINE;
        if (!inFlight.compareAndSet(false, true)) return Submission.BUSY;

        final long expectedEpoch = epoch.get();
        final long budgetNs = TimeUnit.MILLISECONDS.toNanos(config.maximumAdditionalMs);
        try {
            executor.execute(() -> {
                final long startNs = System.nanoTime();
                try {
                    // An accepted task may begin too late due to scheduler load.
                    if (closed.get() || epoch.get() != expectedEpoch ||
                        deadlineNanos - startNs < minimumNs) return;
                    String result = work.call();
                    long finishedNs = System.nanoTime();
                    long elapsedNs = finishedNs - startNs;
                    if (elapsedNs > budgetNs) {
                        blockedUntilNanos = saturatedAdd(finishedNs,
                                TimeUnit.MILLISECONDS.toNanos(config.overrunCooldownMs));
                        return;
                    }
                    if (!closed.get() && epoch.get() == expectedEpoch &&
                        finishedNs < deadlineNanos && result != null) {
                        onSuccess.accept(result);
                    }
                } catch (Exception ignored) {
                    // Isolated optional optimization must never fail primary OCR.
                } finally {
                    inFlight.set(false);
                }
            });
            return Submission.ACCEPTED;
        } catch (RejectedExecutionException ignored) {
            inFlight.set(false);
            return Submission.BUSY;
        }
    }

    /** Nonblocking status for diagnostics; never spins waiting for the camera. */
    public boolean isInFlight() {
        return inFlight.get();
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        invalidate();
        if (executor != null) executor.shutdownNow();
        // Native inference may disregard interruption; do not await termination.
    }

    private static long saturatedAdd(long x, long y) {
        return y > 0 && x > Long.MAX_VALUE - y ? Long.MAX_VALUE : x + y;
    }
}
