package com.vobich.bluestar.cloudprobe;

/**
 * One USB orientation plan, shared by on-screen preview and AI JPEG capture.
 *
 * User-facing mirror/flip controls always mean the FINAL DISPLAY axes.
 * The UVC TextureView transforms its content in view-local coordinates BEFORE
 * View.setRotation, so 90/270 degrees requires swapping local mirror axes.
 * NV21 image orientation already rotates first, then flips display axes.
 *
 * No Android dependencies: every rotation/switch combination is JVM-testable.
 */
final class UsbFrameOrientation {
    static final class Plan {
        final int rotation;
        final boolean quarterTurn;
        final boolean displayMirror,displayFlip;
        final boolean previewLocalMirror,previewLocalFlip;
        private Plan(int rotation,boolean quarterTurn,boolean displayMirror,
                     boolean displayFlip,boolean previewLocalMirror,
                     boolean previewLocalFlip){
            this.rotation=rotation;
            this.quarterTurn=quarterTurn;
            this.displayMirror=displayMirror;
            this.displayFlip=displayFlip;
            this.previewLocalMirror=previewLocalMirror;
            this.previewLocalFlip=previewLocalFlip;
        }
    }

    private UsbFrameOrientation(){}

    static Plan forSettings(int rotation,boolean horizontalMirror,
                            boolean verticalFlip,boolean usbLeftRightCorrection){
        if(rotation!=0&&rotation!=90&&rotation!=180&&rotation!=270)
            throw new IllegalArgumentException("USB 旋转必须为0/90/180/270度");
        // Both horizontal switches act on screen-left/screen-right, not raw UVC X.
        boolean displayMirror=horizontalMirror^usbLeftRightCorrection;
        boolean displayFlip=verticalFlip;
        boolean quarterTurn=(rotation==90||rotation==270);
        return new Plan(rotation,quarterTurn,displayMirror,displayFlip,
            quarterTurn?displayFlip:displayMirror,
            quarterTurn?displayMirror:displayFlip);
    }
}
