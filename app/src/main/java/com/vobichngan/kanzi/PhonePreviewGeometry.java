package com.vobichngan.kanzi;

/**
 * Camera preview geometry independent of OCR orientation and Android UI widgets.
 * Camera2's sensor delivers a landscape 16:9 buffer. TextureView can stretch
 * that buffer over its own full-screen rectangle, so transform the video
 * contents (never the TextureView's layout) with one uniform scale.
 */
public final class PhonePreviewGeometry {
    private PhonePreviewGeometry() {}

    public static final class Layout {
        public final float[] nativeMatrix;
        public final float[] textureMatrix;
        public final float fittedWidth;
        public final float fittedHeight;

        Layout(float[] nativeMatrix, float[] textureMatrix, float fittedWidth, float fittedHeight) {
            this.nativeMatrix = nativeMatrix;
            this.textureMatrix = textureMatrix;
            this.fittedWidth = fittedWidth;
            this.fittedHeight = fittedHeight;
        }

        /** Map one ORIGINAL camera-sensor pixel into the preview view. */
        public float[] mapNative(float x, float y) {
            return new float[]{
                nativeMatrix[0] * x + nativeMatrix[1] * y + nativeMatrix[2],
                nativeMatrix[3] * x + nativeMatrix[4] * y + nativeMatrix[5]
            };
        }
    }

    public static Layout fit(int viewWidth, int viewHeight,
                             int nativeWidth, int nativeHeight,
                             int clockwiseDegrees, boolean mirror, boolean flip) {
        if (viewWidth <= 0 || viewHeight <= 0 || nativeWidth <= 0 || nativeHeight <= 0) {
            throw new IllegalArgumentException("Invalid phone preview dimensions");
        }
        int degrees = ((clockwiseDegrees % 360) + 360) % 360;
        if (degrees % 90 != 0)
            throw new IllegalArgumentException("Preview orientation must be a quarter turn");

        boolean quarter = degrees == 90 || degrees == 270;
        float rotatedW = quarter ? nativeHeight : nativeWidth;
        float rotatedH = quarter ? nativeWidth : nativeHeight;
        // A SINGLE scale factor preserves circles, text glyphs and 16:9 geometry.
        float scale = Math.min(viewWidth / rotatedW, viewHeight / rotatedH);
        float horizontal = mirror ? -scale : scale;
        float vertical = flip ? -scale : scale;
        float a = 0f, b = 0f, d = 0f, e = 0f;
        switch (degrees) {
            case 0:
                a = horizontal;
                e = vertical;
                break;
            case 90:
                b = -horizontal;
                d = vertical;
                break;
            case 180:
                a = -horizontal;
                e = -vertical;
                break;
            case 270:
                b = horizontal;
                d = -vertical;
                break;
            default:
                throw new AssertionError("Angle check failed");
        }
        float tx = viewWidth * .5f - a * nativeWidth * .5f - b * nativeHeight * .5f;
        float ty = viewHeight * .5f - d * nativeWidth * .5f - e * nativeHeight * .5f;
        float[] nativeMatrix = new float[]{
            a, b, tx,
            d, e, ty,
            0f, 0f, 1f
        };
        // TextureView's content naturally spans the full view. Undo the
        // non-uniform view-to-buffer scaling before applying our native FIT.
        float[] textureMatrix = new float[]{
            a * nativeWidth / viewWidth, b * nativeHeight / viewHeight, tx,
            d * nativeWidth / viewWidth, e * nativeHeight / viewHeight, ty,
            0f, 0f, 1f
        };
        return new Layout(nativeMatrix, textureMatrix, rotatedW * scale, rotatedH * scale);
    }

    public static int rotation(int sensorToUpright, int extraDisplayRotation) {
        return ((sensorToUpright + extraDisplayRotation) % 360 + 360) % 360;
    }
}
