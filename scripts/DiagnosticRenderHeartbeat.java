package com.jiangdg.ausbc.render;

import java.util.concurrent.atomic.AtomicLong;

/** Passive, lock-free USB EGL heartbeat; never controls the camera or renderer. */
public final class DiagnosticRenderHeartbeat {
    private static final AtomicLong swaps = new AtomicLong();
    private static final AtomicLong errors = new AtomicLong();
    private static volatile long lastSwapMs;

    private DiagnosticRenderHeartbeat() {}
    public static void noteSwapAttempt() {
        lastSwapMs = System.nanoTime()/1_000_000L;
        swaps.incrementAndGet();
    }
    public static void noteEglError() { errors.incrementAndGet(); }
    public static long totalSwapAttempts() { return swaps.get(); }
    public static long lastSwapAttemptMs() { return lastSwapMs; }
    public static long totalEglErrors() { return errors.get(); }
}
