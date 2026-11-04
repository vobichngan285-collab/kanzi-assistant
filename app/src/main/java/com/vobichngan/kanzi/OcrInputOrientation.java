package com.vobichngan.kanzi;

/**
 * Independent camera-frame -> OCR input orientation.
 * This class has no preview view, TextureView matrix, USB display mirror,
 * OCR model, inference, or speech dependency.
 */
public final class OcrInputOrientation {
    private OcrInputOrientation() {}

    public static int normalize(int angle) {
        return (angle == 90 || angle == 180 || angle == 270) ? angle : 0;
    }

    /** Width and height of the exact OCR bitmap after rotation. */
    public static int[] inputSize(int frameWidth, int frameHeight, int angle) {
        if (frameWidth <= 0 || frameHeight <= 0)
            throw new IllegalArgumentException("Invalid original frame size");
        int value=normalize(angle);
        return value == 90 || value == 270
            ? new int[]{frameHeight,frameWidth}
            : new int[]{frameWidth,frameHeight};
    }

    /** The source frame itself is never mirrored or overwritten. */
    public static float[] sourceToInput(float x, float y, int width, int height, int angle) {
        switch (normalize(angle)) {
            case 90: return new float[]{height-y,x};
            case 180: return new float[]{width-x,height-y};
            case 270: return new float[]{y,width-x};
            default: return new float[]{x,y};
        }
    }

    /** OCR result quadrilateral back to original unrotated camera coordinates. */
    public static float[] inputToSource(float x, float y, int width, int height, int angle) {
        switch (normalize(angle)) {
            case 90: return new float[]{y,height-x};
            case 180: return new float[]{width-x,height-y};
            case 270: return new float[]{width-y,x};
            default: return new float[]{x,y};
        }
    }
}
