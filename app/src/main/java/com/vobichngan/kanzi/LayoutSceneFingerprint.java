package com.vobichngan.kanzi;

/**
 * Cheap, strictly conservative NV21 luma signature for stationary-frame layout reuse.
 * A similarity match is only a cache hint; this is not motion tracking or OCR.
 * Coordinates can be reused only when the input frame dimensions are identical.
 */
public final class LayoutSceneFingerprint {
    private static final int COLS = 32;
    private static final int ROWS = 24;
    private final int width;
    private final int height;
    private final byte[] samples;

    private LayoutSceneFingerprint(int width, int height, byte[] samples) {
        this.width = width;
        this.height = height;
        this.samples = samples;
    }

    public static LayoutSceneFingerprint fromNv21(byte[] nv21, int width, int height) {
        if (nv21 == null || width < COLS || height < ROWS ||
            (long)width * height > nv21.length)
            throw new IllegalArgumentException("Invalid NV21 luma frame");
        byte[] samples = new byte[COLS * ROWS];
        for (int row = 0; row < ROWS; row++) {
            int y = Math.min(height-1, (row * 2 + 1) * height / (ROWS * 2));
            for (int col = 0; col < COLS; col++) {
                int x = Math.min(width-1, (col * 2 + 1) * width / (COLS * 2));
                samples[row * COLS + col] = nv21[y * width + x];
            }
        }
        return new LayoutSceneFingerprint(width,height,samples);
    }

    /**
     * Do not reuse unless global illumination AND localized text edges stay
     * nearly identical. False negatives cost performance; false positives could
     * put OCR boxes in the wrong region, so we prefer conservative thresholds.
     */
    public boolean isStationaryComparedWith(LayoutSceneFingerprint other) {
        if (other == null || width != other.width || height != other.height)
            return false;
        long difference = 0;
        int changed = 0;
        for (int i=0;i<samples.length;i++) {
            int d = Math.abs((samples[i]&0xff)-(other.samples[i]&0xff));
            difference += d;
            if (d >= 12) changed++;
        }
        return difference <= samples.length * 2L &&
            changed <= samples.length / 100;
    }
}
