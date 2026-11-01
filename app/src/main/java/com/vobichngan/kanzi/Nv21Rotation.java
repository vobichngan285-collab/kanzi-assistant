package com.vobichngan.kanzi;

/** Phone camera sensor orientation normalization; USB OCR never calls this class. */
public final class Nv21Rotation {
    private Nv21Rotation() {}
    public static byte[] rotate(byte[] src, int width, int height, int degrees) {
        if (degrees == 0) return src;
        if (degrees != 90 && degrees != 180 && degrees != 270)
            throw new IllegalArgumentException("Unsupported camera sensor angle: " + degrees);
        int outW = (degrees % 180 == 0) ? width : height;
        int outH = (degrees % 180 == 0) ? height : width;
        byte[] dst = new byte[src.length];
        // Transform luma and chroma independently in 2x2 pixel blocks.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int dx, dy;
                if (degrees == 90) { dx = height - y - 1; dy = x; }
                else if (degrees == 180) { dx = width - x - 1; dy = height - y - 1; }
                else { dx = y; dy = width - x - 1; }
                dst[dy * outW + dx] = src[y * width + x];
            }
        }
        int startSrc = width * height, startDst = outW * outH;
        for (int y = 0; y < height / 2; y++) {
            for (int x = 0; x < width / 2; x++) {
                int dx, dy;
                if (degrees == 90) { dx = height / 2 - y - 1; dy = x; }
                else if (degrees == 180) { dx = width / 2 - x - 1; dy = height / 2 - y - 1; }
                else { dx = y; dy = width / 2 - x - 1; }
                int i = startSrc + y * width + 2 * x;
                int o = startDst + dy * outW + 2 * dx;
                dst[o] = src[i]; dst[o + 1] = src[i + 1];
            }
        }
        return dst;
    }
}
