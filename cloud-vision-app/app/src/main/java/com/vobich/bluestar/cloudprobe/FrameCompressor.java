package com.vobich.bluestar.cloudprobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import java.io.ByteArrayOutputStream;

/** Only encodes frames after an explicit user action. Never writes to disk. */
final class FrameCompressor {
    static byte[] to720pJpeg(byte[] nv21, int width, int height) {
        if (nv21 == null || width < 2 || height < 2 || (width & 1) != 0 ||
            (height & 1) != 0 || (long) nv21.length != (long) width * height * 3 / 2) {
            throw new IllegalArgumentException("无效的摄像头NV21帧");
        }
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        boolean ok = new YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(new Rect(0, 0, width, height), 83, original);
        if (!ok) throw new IllegalStateException("相机JPEG编码失败");
        int longest = Math.max(width, height);
        if (longest <= 1280) return original.toByteArray();
        Bitmap decoded = BitmapFactory.decodeByteArray(original.toByteArray(), 0, original.size());
        if (decoded == null) throw new IllegalStateException("JPEG解析失败");
        int newW = Math.max(2, (int) Math.round(width * 1280.0 / longest));
        int newH = Math.max(2, (int) Math.round(height * 1280.0 / longest));
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, newW, newH, true);
        ByteArrayOutputStream small = new ByteArrayOutputStream();
        boolean success = scaled.compress(Bitmap.CompressFormat.JPEG, 80, small);
        if (scaled != decoded) scaled.recycle();
        decoded.recycle();
        if (!success) throw new IllegalStateException("照片缩放失败");
        return small.toByteArray();
    }
}
