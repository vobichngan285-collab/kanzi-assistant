package com.vobichngan.kanzi;

/**
 * Cheap luma-only frame gate: one sampled fingerprint, never a queue of bitmaps.
 *
 * It runs only after the caller has secured the single OCR execution slot.
 * Long reading avoids duplicate full-page OCR until the scene has materially changed.
 * Quick scanning retries the scene periodically to allow subtle product-label changes.
 */
public final class FrameChangeGate {
    private static final int COLS = 30;
    private static final int ROWS = 18;
    private static final long SCAN_RECHECK_MS = 2800L;

    private byte[] previous;
    private int previousWidth;
    private int previousHeight;
    private long lastAcceptedAt;

    public synchronized void reset() {
        previous = null;
        previousWidth = 0;
        previousHeight = 0;
        lastAcceptedAt = 0;
    }

    public synchronized boolean accept(byte[] nv21, int width, int height,
                                       long timestampMs, boolean longReading) {
        if (nv21 == null || width <= 0 || height <= 0 ||
                nv21.length < (long) width * height) return false;
        byte[] current = new byte[COLS * ROWS];
        int index = 0;
        for (int row = 0; row < ROWS; row++) {
            int y = row * (height - 1) / (ROWS - 1);
            for (int col = 0; col < COLS; col++) {
                int x = col * (width - 1) / (COLS - 1);
                current[index++] = nv21[y * width + x];
            }
        }
        if (previous != null && previousWidth == width && previousHeight == height) {
            int substantial = 0;
            long sum = 0L;
            for (int i = 0; i < current.length; i++) {
                int difference = Math.abs((current[i] & 0xff) - (previous[i] & 0xff));
                sum += difference;
                if (difference >= 20) substantial++;
            }
            double changedFraction = substantial / (double) current.length;
            double meanDifference = sum / (double) current.length;
            boolean sceneChanged = meanDifference >= 8.0 ||
                (changedFraction >= 0.045 && meanDifference >= 2.5);
            if (!sceneChanged &&
                (longReading || timestampMs - lastAcceptedAt < SCAN_RECHECK_MS)) {
                return false;
            }
        }
        previous = current;
        previousWidth = width;
        previousHeight = height;
        lastAcceptedAt = timestampMs;
        return true;
    }
}
