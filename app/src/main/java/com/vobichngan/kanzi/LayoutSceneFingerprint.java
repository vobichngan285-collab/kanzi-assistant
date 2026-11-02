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
        // One pixel at the center of every cell missed labels whose strokes
        // were between the sample points. Sample 3x3 within every tile instead.
        // 32x24x9 = 6912 luma reads per OCR frame; negligible vs ONNX inference.
        byte[] samples = new byte[COLS * ROWS * 9];
        int at = 0;
        for (int row=0;row<ROWS;row++) {
            int y0=(int)((long)row*height/ROWS);
            int y1=(int)((long)(row+1)*height/ROWS);
            for (int col=0;col<COLS;col++) {
                int x0=(int)((long)col*width/COLS);
                int x1=(int)((long)(col+1)*width/COLS);
                for (int sy=0;sy<3;sy++) {
                    int y=Math.min(height-1,y0+(2*sy+1)*(y1-y0)/6);
                    for (int sx=0;sx<3;sx++) {
                        int x=Math.min(width-1,x0+(2*sx+1)*(x1-x0)/6);
                        samples[at++]=nv21[y*width+x];
                    }
                }
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
        long difference=0;
        int changed=0;
        for(int i=0;i<samples.length;i++) {
            int d=Math.abs((samples[i]&0xff)-(other.samples[i]&0xff));
            difference+=d;
            if(d>=12)changed++;
            // A large local stroke change is sufficient to invalidate the
            // layout cache even when the full-page average hardly changes.
            if(d>=60)return false;
        }
        return difference<=samples.length*2L &&
            changed<=Math.max(2,samples.length/200);
    }
}
