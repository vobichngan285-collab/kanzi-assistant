package com.vobichngan.kanzi;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Camera2 source for built-in front/back cameras. Owns only camera acquisition;
 * recognition engine, text layout and speech are shared with the USB source.
 */
public final class InternalCameraController {
    public interface Listener {
        boolean shouldReceiveFrame();
        void onFrame(byte[] nv21, int width, int height);
        void onOpened(int rawWidth, int rawHeight, int clockwiseOcrRotation);
        void onError(String message);
    }

    private static final String TAG = "KanziPhoneCamera";
    private final Activity activity;
    private final TextureView textureView;
    private final Listener listener;
    private HandlerThread worker;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private boolean front;
    private boolean active;
    private int rotationDegrees;
    private int activeWidth, activeHeight;
    private final Object mutex = new Object();

    public InternalCameraController(Activity activity, TextureView textureView, Listener listener) {
        this.activity = activity;
        this.textureView = textureView;
        this.listener = listener;
    }

    public boolean hasPermission() {
        return activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public void start(boolean frontFacing) {
        if (!hasPermission()) { listener.onError("需要授予手机摄像头权限"); return; }
        stop();
        front = frontFacing;
        active = true;
        worker = new HandlerThread("KanziCamera2");
        worker.start();
        handler = new Handler(worker.getLooper());
        if (textureView.isAvailable()) openCamera();
        else textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
                if (active) openCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { stop(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
    }

    private void openCamera() {
        if (!active || handler == null || !textureView.isAvailable()) return;
        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) { listener.onError("没有找到系统摄像头管理器"); return; }
            String selectedId = null;
            CameraCharacteristics selected = null;
            int desiredFacing = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer direction = c.get(CameraCharacteristics.LENS_FACING);
                if (direction != null && direction == desiredFacing) {
                    selectedId = id; selected = c; break;
                }
            }
            if (selectedId == null || selected == null) {
                listener.onError(front ? "这台设备没有可用的前置摄像头" : "这台设备没有可用的后置摄像头");
                return;
            }
            Integer orientation = selected.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int sensor = orientation == null ? 90 : orientation;
            int display = activity.getWindowManager().getDefaultDisplay().getRotation() * 90;
            // Portrait phone sensor orientation. Only internal camera pixels
            // are normalized; USB still gives OCR its untouched original frame.
            rotationDegrees = front ? (sensor + display) % 360 : (sensor - display + 360) % 360;
            StreamConfigurationMap map = selected.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) { listener.onError("手机摄像头没有可用的图像流"); return; }
            Size[] yuv = map.getOutputSizes(ImageFormat.YUV_420_888);
            Size[] surface = map.getOutputSizes(SurfaceTexture.class);
            if (yuv == null || surface == null) { listener.onError("手机摄像头不支持实时分析图像"); return; }
            Size chosen = chooseCommonSize(yuv, surface);
            activeWidth = chosen.getWidth(); activeHeight = chosen.getHeight();
            reader = ImageReader.newInstance(activeWidth, activeHeight, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(source -> {
                Image image = source.acquireLatestImage();
                if (image == null) return;
                try {
                    if (!active || !listener.shouldReceiveFrame()) return;
                    byte[] nv21 = yuvToNv21(image);
                    byte[] upright = Nv21Rotation.rotate(nv21, activeWidth, activeHeight, rotationDegrees);
                    int w = rotationDegrees % 180 == 0 ? activeWidth : activeHeight;
                    int h = rotationDegrees % 180 == 0 ? activeHeight : activeWidth;
                    listener.onFrame(upright, w, h);
                } catch (Throwable err) {
                    Log.w(TAG, "Could not convert phone camera frame", err);
                } finally { image.close(); }
            }, handler);
            SurfaceTexture st = textureView.getSurfaceTexture();
            if (st == null) { listener.onError("手机摄像头预览未就绪"); return; }
            st.setDefaultBufferSize(activeWidth, activeHeight);
            listener.onOpened(activeWidth, activeHeight, rotationDegrees);
            manager.openCamera(selectedId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    if (!active) { device.close(); return; }
                    camera = device;
                    configureSession();
                }
                @Override public void onDisconnected(CameraDevice device) {
                    device.close();
                    camera = null;
                    listener.onError("手机摄像头已断开");
                }
                @Override public void onError(CameraDevice device, int error) {
                    device.close();
                    camera = null;
                    listener.onError("手机摄像头启动失败，错误码：" + error);
                }
            }, handler);
        } catch (CameraAccessException | SecurityException error) {
            listener.onError("无法打开手机摄像头：" + error.getMessage());
        }
    }

    private static Size chooseCommonSize(Size[] yuv, Size[] surface) {
        Size best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Size y : yuv) {
            if ((y.getWidth() & 1) != 0 || (y.getHeight() & 1) != 0) continue;
            boolean shared = false;
            for (Size s : surface) if (y.equals(s)) { shared = true; break; }
            if (!shared) continue;
            // Prefer 1280x720: enough for ordinary text and less CPU than 1080p.
            double aspect = y.getWidth() / (double)y.getHeight();
            double score = Math.abs(y.getWidth() * y.getHeight() - 1280.0 * 720.0) / 10000.0;
            score += Math.abs(aspect - 16.0 / 9.0) * 500;
            if (score < bestScore) { bestScore = score; best = y; }
        }
        if (best != null) return best;
        return yuv[0];
    }

    private void configureSession() {
        if (camera == null || reader == null || !active) return;
        SurfaceTexture st = textureView.getSurfaceTexture();
        if (st == null) return;
        Surface video = new Surface(st);
        Surface analysis = reader.getSurface();
        try {
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(video);
            builder.addTarget(analysis);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            camera.createCaptureSession(Arrays.asList(video, analysis), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (!active || camera == null) { configured.close(); video.release(); return; }
                    session = configured;
                    try { configured.setRepeatingRequest(builder.build(), null, handler); }
                    catch (CameraAccessException ex) { listener.onError("手机摄像头无法启动预览：" + ex.getMessage()); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) {
                    failed.close();
                    listener.onError("手机摄像头预览配置失败");
                }
            }, handler);
        } catch (CameraAccessException ex) {
            video.release();
            listener.onError("手机摄像头配置异常：" + ex.getMessage());
        }
    }

    private static byte[] yuvToNv21(Image image) {
        int width = image.getWidth(), height = image.getHeight();
        byte[] output = new byte[width * height * 3 / 2];
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer y = planes[0].getBuffer();
        int yStride = planes[0].getRowStride(), yPixel = planes[0].getPixelStride();
        for (int row = 0; row < height; row++)
            for (int col = 0; col < width; col++)
                output[row * width + col] = y.get(row * yStride + col * yPixel);
        ByteBuffer u = planes[1].getBuffer(), v = planes[2].getBuffer();
        int uStride = planes[1].getRowStride(), uPixel = planes[1].getPixelStride();
        int vStride = planes[2].getRowStride(), vPixel = planes[2].getPixelStride();
        int start = width * height;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int offset = start + row * width + 2 * col;
                output[offset] = v.get(row * vStride + col * vPixel);
                output[offset + 1] = u.get(row * uStride + col * uPixel);
            }
        }
        return output;
    }

    public void stop() {
        active = false;
        if (session != null) { try { session.close(); } catch (Exception ignored) {} session = null; }
        if (camera != null) { try { camera.close(); } catch (Exception ignored) {} camera = null; }
        if (reader != null) { try { reader.close(); } catch (Exception ignored) {} reader = null; }
        if (worker != null) { worker.quitSafely(); worker = null; }
        handler = null;
    }
}
