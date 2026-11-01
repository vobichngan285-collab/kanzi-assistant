package com.vobichngan.kanzi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Draw OCR quadrilaterals from actual model coordinates, not decorative boxes. */
public final class OcrOverlayView extends View {
    private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<float[]> polygons = new ArrayList<>();
    private int frameWidth, frameHeight;
    private View videoView;
    private boolean phoneCamera;
    private int phoneSensorRotation;
    private int phoneDisplayExtraRotation;
    private boolean phoneDisplayMirror;
    private boolean phoneDisplayFlip;

    public OcrOverlayView(Context context) {
        super(context);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        white.setColor(0xFFF9FAFC);
        white.setStyle(Paint.Style.STROKE);
        white.setStrokeWidth(2.5f * getResources().getDisplayMetrics().density);
        background.setColor(0x8A000000);
        background.setStyle(Paint.Style.STROKE);
        background.setStrokeWidth(4f * getResources().getDisplayMetrics().density);
    }

    public void show(List<float[]> boxes, int width, int height, View video,
                     boolean isPhone, int sensorRotation) {
        polygons = boxes == null ? new ArrayList<>() : new ArrayList<>(boxes);
        frameWidth = width;
        frameHeight = height;
        videoView = video;
        phoneCamera = isPhone;
        phoneSensorRotation = sensorRotation;
        invalidate();
    }

    public void setPhoneDisplayTransform(int extraRotation, boolean mirror, boolean flip) {
        phoneDisplayExtraRotation = extraRotation;
        phoneDisplayMirror = mirror;
        phoneDisplayFlip = flip;
        invalidate();
    }

    public void clear() {
        polygons.clear();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (frameWidth <= 0 || frameHeight <= 0 || videoView == null || polygons.isEmpty()) return;
        float cx = videoView.getX() + videoView.getWidth() / 2f;
        float cy = videoView.getY() + videoView.getHeight() / 2f;
        float rotation = videoView.getRotation();
        float sx = videoView.getScaleX(), sy = videoView.getScaleY();
        float videoW = videoView.getWidth(), videoH = videoView.getHeight();
        if (videoW <= 0 || videoH <= 0) return;
        PhonePreviewGeometry.Layout phoneFit = null;

        // USB: frame coordinates match pre-rotation TextureView.
        // Phone: OCR normalized the sensor picture, invert its sensor rotation
        // before applying the preview TextureView's display rotation.
        int nativeWidth = frameWidth, nativeHeight = frameHeight;
        if (phoneCamera && phoneSensorRotation % 180 != 0) {
            nativeWidth = frameHeight;
            nativeHeight = frameWidth;
        }
        if (phoneCamera) {
            // Apply exactly the same native-camera -> view geometry as the
            // TextureView content matrix, not the USB view-rotation formula.
            phoneFit = PhonePreviewGeometry.fit(
                (int) videoW, (int) videoH, nativeWidth, nativeHeight,
                PhonePreviewGeometry.rotation(phoneSensorRotation, phoneDisplayExtraRotation),
                phoneDisplayMirror, phoneDisplayFlip);
        }
        for (float[] coords : polygons) {
            if (coords == null || coords.length != 8) continue;
            Path path = new Path();
            for (int i = 0; i < 4; i++) {
                float x = coords[i * 2], y = coords[i * 2 + 1];
                float nx = x, ny = y;
                if (phoneCamera) {
                    if (phoneSensorRotation == 90) { nx = y; ny = nativeHeight - x; }
                    else if (phoneSensorRotation == 180) { nx = nativeWidth - x; ny = nativeHeight - y; }
                    else if (phoneSensorRotation == 270) { nx = nativeWidth - y; ny = x; }
                }
                float px, py;
                if (phoneCamera) {
                    float[] mapped = phoneFit.mapNative(nx, ny);
                    px = videoView.getX() + mapped[0];
                    py = videoView.getY() + mapped[1];
                } else {
                    float vx = (nx / nativeWidth - .5f) * videoW * sx;
                    float vy = (ny / nativeHeight - .5f) * videoH * sy;
                    double radians = Math.toRadians(rotation);
                    px = cx + (float)(vx * Math.cos(radians) - vy * Math.sin(radians));
                    py = cy + (float)(vx * Math.sin(radians) + vy * Math.cos(radians));
                }
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            path.close();
            canvas.drawPath(path, background);
            canvas.drawPath(path, white);
        }
    }
}
