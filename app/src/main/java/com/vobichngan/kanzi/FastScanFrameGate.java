package com.vobichngan.kanzi;

/**
 * Short-text scanning uses a more detailed, low-cost scene fingerprint than
 * the long-reading gate. This is NOT another OCR pass and never queues frames.
 *
 * A new visual scene may be processed as soon as the existing single OCR slot
 * and user-configured recognition interval permit. An unchanged scene is
 * retried infrequently, to recover text that was temporarily blurry.
 * The first acceptable frame after reset always passes.
 */
public final class FastScanFrameGate {
    private static final int COLS = 64;
    private static final int ROWS = 36;
    // Never poll the SAME static label every 500 ms; protect UVC/CPU load.
    private static final long UNCHANGED_RETRY_MS = 2000L;
    private byte[] last;
    private int lastWidth, lastHeight;
    private long lastAcceptedMs;

    public synchronized void reset() {
        last = null;
        lastWidth = lastHeight = 0;
        lastAcceptedMs = 0;
    }

    public synchronized boolean accept(byte[] nv21, int width, int height, long nowMs) {
        if (nv21 == null || width < 16 || height < 16 ||
                (long) width * height > nv21.length) return false;

        // Sampling only happens AFTER the caller owns the single OCR slot.
        byte[] sample = new byte[COLS * ROWS];
        int offset = 0;
        for (int r = 0; r < ROWS; r++) {
            int y = r * (height - 1) / (ROWS - 1);
            for (int c = 0; c < COLS; c++) {
                int x = c * (width - 1) / (COLS - 1);
                sample[offset++] = nv21[y * width + x];
            }
        }

        if (last != null && width == lastWidth && height == lastHeight &&
                nowMs >= lastAcceptedMs) {
            int meaningfulDifferences = 0;
            long totalDifference = 0;
            for (int i = 0; i < sample.length; i++) {
                int delta = Math.abs((sample[i] & 0xff) - (last[i] & 0xff));
                totalDifference += delta;
                if (delta >= 14) meaningfulDifferences++;
            }
            double changedFraction = meaningfulDifferences / (double) sample.length;
            double averageDifference = totalDifference / (double) sample.length;
            boolean sceneChanged = averageDifference >= 5.0 ||
                (changedFraction >= 0.027 && averageDifference >= 1.5);
            if (!sceneChanged && nowMs - lastAcceptedMs < UNCHANGED_RETRY_MS)
                return false;
        }
        last = sample;
        lastWidth = width;
        lastHeight = height;
        lastAcceptedMs = nowMs;
        return true;
    }
}
