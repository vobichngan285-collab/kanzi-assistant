package com.vobichngan.kanzi;

/**
 * Display-only USB camera mirror calibration. Camera NV21 frames and
 * OCR quadrilateral coordinates are never changed here.
 *
 * Keep the default USB calibration separate from the optional user mirror:
 * older camera models can have the opposite physical orientation.
 */
public final class UsbPreviewMirror {
    private UsbPreviewMirror() {}

    /** One default correction, optionally cancelled by a second user toggle. */
    public static boolean shouldMirror(boolean correctUsbLeftRight,
                                       boolean userExtraMirror) {
        return correctUsbLeftRight ^ userExtraMirror;
    }

    /** Map an unmodified USB frame point into the displayed TextureView. */
    public static float mapX(float frameX, float displayedWidth,
                             boolean correctUsbLeftRight,
                             boolean userExtraMirror) {
        if (!Float.isFinite(frameX) || !(displayedWidth > 0f))
            throw new IllegalArgumentException("Invalid preview coordinate");
        return shouldMirror(correctUsbLeftRight, userExtraMirror)
            ? displayedWidth - frameX : frameX;
    }
}
