package com.jiangdg.ausbc.render;

import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayDeque;

/** Passive, lock-free USB EGL heartbeat; never controls the camera or renderer. */
public final class DiagnosticRenderHeartbeat {
    private static final AtomicLong nativeFrames = new AtomicLong();
    private static final AtomicLong glFrames = new AtomicLong();
    private static volatile long lastNativeMs, lastGlArrivalMs;
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    private static final int MAX_EVENTS = 48;
    private static final AtomicLong swaps = new AtomicLong();
    private static final AtomicLong errors = new AtomicLong();
    private static volatile long lastSwapMs;

    private DiagnosticRenderHeartbeat() {}
    /** Called at the upstream UVC Java callback entrance, before app-frame delivery. */
    public static void noteNativeFrame() {
        lastNativeMs = System.nanoTime()/1_000_000L;
        nativeFrames.incrementAndGet();
    }
    /** Independent EGL input SurfaceTexture signal, before any draw attempt. */
    public static void noteGlFrameArrived() {
        lastGlArrivalMs = System.nanoTime()/1_000_000L;
        glFrames.incrementAndGet();
    }
    public static long totalNativeFrames() { return nativeFrames.get(); }
    public static long lastNativeFrameMs() { return lastNativeMs; }
    public static long totalGlFrameArrivals() { return glFrames.get(); }
    public static long lastGlFrameArrivedMs() { return lastGlArrivalMs; }
    public static synchronized void noteEvent(String event) {
        if (events.size() >= MAX_EVENTS) events.removeFirst();
        events.addLast((System.nanoTime()/1_000_000L) + " " + event);
    }
    public static synchronized String recentEvents() {
        StringBuilder out = new StringBuilder();
        for (String line: events) out.append(line).append('\n');
        return out.toString();
    }
    public static void noteSwapAttempt() {
        lastSwapMs = System.nanoTime()/1_000_000L;
        swaps.incrementAndGet();
    }
    public static void noteEglError() { errors.incrementAndGet(); }
    public static long totalSwapAttempts() { return swaps.get(); }
    public static long lastSwapAttemptMs() { return lastSwapMs; }
    public static long totalEglErrors() { return errors.get(); }
}
