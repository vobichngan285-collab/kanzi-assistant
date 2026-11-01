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

    /**
     * Camera2-backed TextureView ALREADY rotates its video to the device's
     * NATURAL orientation. The application must not apply SENSOR_ORIENTATION
     * again. Only correct its aspect ratio and optional user/display rotation.
     */
    public static int[] naturalBufferSize(int bufferWidth, int bufferHeight,
                                           int sensorOrientation) {
        int degrees = ((sensorOrientation % 360) + 360) % 360;
        if (degrees % 90 != 0) throw new IllegalArgumentException("Bad sensor angle");
        return degrees % 180 == 0
            ? new int[]{bufferWidth, bufferHeight}
            : new int[]{bufferHeight, bufferWidth};
    }

    public static Layout camera2Preview(int viewWidth, int viewHeight,
                                        int rawWidth, int rawHeight,
                                        int sensorOrientation, int extraRotation,
                                        boolean mirror, boolean flip) {
        int[] natural = naturalBufferSize(rawWidth, rawHeight, sensorOrientation);
        return fit(viewWidth, viewHeight, natural[0], natural[1],
            extraRotation, mirror, flip);
    }

    /**
     * OCR processes separately rotated sensor NV21 pixels. Convert an OCR
     * polygon into the natural-upright camera preview coordinate space.
     * This is identity when both paths use the sensor's natural orientation.
     */
    public static float[] ocrToNatural(float ocrX, float ocrY,
                                       int sensorWidth, int sensorHeight,
                                       int ocrClockwiseDegrees, int sensorClockwiseDegrees) {
        float rawX, rawY;
        switch ((ocrClockwiseDegrees % 360 + 360) % 360) {
            case 0: rawX = ocrX; rawY = ocrY; break;
            case 90: rawX = ocrY; rawY = sensorHeight - ocrX; break;
            case 180: rawX = sensorWidth - ocrX; rawY = sensorHeight - ocrY; break;
            case 270: rawX = sensorWidth - ocrY; rawY = ocrX; break;
            default: throw new IllegalArgumentException("Bad OCR angle");
        }
        switch ((sensorClockwiseDegrees % 360 + 360) % 360) {
            case 0: return new float[]{rawX, rawY};
            case 90: return new float[]{sensorHeight - rawY, rawX};
            case 180: return new float[]{sensorWidth - rawX, sensorHeight - rawY};
            case 270: return new float[]{rawY, sensorWidth - rawX};
            default: throw new IllegalArgumentException("Bad camera sensor angle");
        }
    }

    public static int rotation(int sensorToUpright, int extraDisplayRotation) {
        return ((sensorToUpright + extraDisplayRotation) % 360 + 360) % 360;
    }
}
