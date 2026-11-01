package com.vobichngan.kanzi;

/** Tiny, low-cost image quality indicators from NV21 Y samples. */
public final class FrameQuality {
    private FrameQuality() {}
    public static final class Assessment {
        public final boolean dark;
        public final boolean possiblyBlurry;
        public final int brightness;
        public final double edgeStrength;
        Assessment(int brightness, double edgeStrength) {
            this.brightness = brightness;
            this.edgeStrength = edgeStrength;
            dark = brightness < 47;
            // Conservative. Lack of edges also occurs on a blank surface.
            possiblyBlurry = edgeStrength < 7.0;
        }
    }
    public static Assessment assess(byte[] nv21, int width, int height) {
        if (nv21 == null || width < 4 || height < 4) return new Assessment(128, 100);
        int stepX = Math.max(2, width / 64), stepY = Math.max(2, height / 40);
        long sum = 0, gradient = 0;
        int count = 0;
        for (int y = 1; y + stepY < height; y += stepY) {
            int row = y * width;
            for (int x = 1; x + stepX < width; x += stepX) {
                int v = nv21[row + x] & 0xff;
                sum += v;
                gradient += Math.abs(v - (nv21[row + x + stepX] & 0xff));
                gradient += Math.abs(v - (nv21[(y + stepY) * width + x] & 0xff));
                count++;
            }
        }
        return count == 0 ? new Assessment(128, 100) :
            new Assessment((int)(sum / count), gradient / (2.0 * count));
    }
}
