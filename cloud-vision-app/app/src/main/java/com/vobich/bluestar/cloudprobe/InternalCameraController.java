package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
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
        void onOpened(int rawWidth, int rawHeight, int sensorNaturalRotation, int clockwiseOcrRotation);
        void onPreviewStarted();
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
    private Surface previewSurface;
    private int generation;
    private boolean openRequested;
    private boolean previewDelivered;
    private float wantedZoom=1f;
    private float supportedZoom=3f;
    private Rect sensorActiveArray;
    private CaptureRequest.Builder repeatBuilder;

    public InternalCameraController(Activity activity, TextureView textureView, Listener listener) {
        this.activity = activity;
        this.textureView = textureView;
        this.listener = listener;
    }

    public boolean hasPermission() {
        return activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public void start(boolean frontFacing) {
        if (!hasPermission()) { listener.onError("未获得手机摄像头权限"); return; }
        stop();
        front = frontFacing;
        active = true;
        final int token = generation;
        openRequested = false;
        previewDelivered = false;
        worker = new HandlerThread("KanziCamera2");
        worker.start();
        handler = new Handler(worker.getLooper());

        // TextureView has to be VISIBLE and attached before any Camera2 output
        // surface can be allocated. This listener waits for real availability.
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
                if (active && generation == token) openCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (active && generation == token) stop();
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                if (active && generation == token && !previewDelivered) {
                    previewDelivered = true;
                    listener.onPreviewStarted();
                    Log.i(TAG, "Camera2 preview frame displayed");
                }
            }
        });
        if (textureView.isAvailable()) {
            // Let layout, visibility and the phone preview transform settle
            // before opening the camera. Surface remains attached during normal
            // frame delivery, regardless of OCR's recognition interval.
            textureView.post(() -> {
                if (active && generation == token) openCamera();
            });
        }
    }

    private void openCamera() {
        if (!active || handler == null || !textureView.isAvailable() || openRequested) return;
        if (textureView.getWidth() < 2 || textureView.getHeight() < 2) return;
        openRequested = true;
        final int token = generation;
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
                listener.onError(front ? "没有找到前置摄像头设备" : "没有找到后置摄像头设备");
                openRequested = false;
                return;
            }
            Log.i(TAG, "Opening phone camera ID=" + selectedId + " front=" + front);
            Integer orientation = selected.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int sensor = orientation == null ? 90 : orientation;
            sensorActiveArray=selected.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Float max=selected.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            supportedZoom=max==null?1f:Math.max(1f,Math.min(3f,max));
            int display = activity.getWindowManager().getDefaultDisplay().getRotation() * 90;
            // Portrait phone sensor orientation. Only internal camera pixels
            // are normalized; USB still gives OCR its untouched original frame.
            // OCR operates on YUV sensor pixels (not the automatically oriented
            // TextureView). Rotate those pixels by the sensor/display relation.
            // The preview itself must NOT rotate by this value a second time.
            rotationDegrees = front ? (sensor - display + 360) % 360
                                    : (sensor + display) % 360;
            StreamConfigurationMap map = selected.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) { listener.onError("手机摄像头没有可用的图像流"); openRequested = false; return; }
            Size[] yuv = map.getOutputSizes(ImageFormat.YUV_420_888);
            Size[] surface = map.getOutputSizes(SurfaceTexture.class);
            if (yuv == null || yuv.length == 0 || surface == null || surface.length == 0) {
                openRequested = false;
                listener.onError("摄像头不支持预览及图像分析格式");
                return;
            }
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
            Log.i(TAG, "Selected phone camera size " + activeWidth + "x" + activeHeight +
                ", sensor rotation=" + rotationDegrees);
            listener.onOpened(activeWidth, activeHeight, sensor, rotationDegrees);
            // Preview layout may change after onOpened: wait for the UI to apply
            // the new aspect ratio before binding Camera2 to the SurfaceTexture.
            final String cameraId = selectedId;
            textureView.post(() -> {
                if (!active || token != generation || !textureView.isAvailable()) return;
                try {
                    manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                        @Override public void onOpened(CameraDevice device) {
                            if (!active || token != generation) { device.close(); return; }
                            camera = device;
                            Log.i(TAG, "Phone Camera2 device opened");
                            configureSession(token);
                        }
                        @Override public void onDisconnected(CameraDevice device) {
                            device.close();
                            if (token != generation) return;
                            camera = null;
                            listener.onError("摄像头连接中断");
                        }
                        @Override public void onError(CameraDevice device, int error) {
                            device.close();
                            if (token != generation) return;
                            camera = null;
                            listener.onError("相机设备错误码 " + error);
                        }
                    }, handler);
                } catch (CameraAccessException | SecurityException | IllegalArgumentException ex) {
                    if (token == generation)
                        listener.onError("无法打开摄像头：" + ex.getMessage());
                }
            });
        } catch (CameraAccessException | SecurityException | IllegalArgumentException error) {
            openRequested = false;
            listener.onError("摄像头初始化失败：" + error.getMessage());
        }
    }

    private static Size chooseCommonSize(Size[] yuv, Size[] surface) {
        Size bestWide = null;
        double bestWideScore = Double.POSITIVE_INFINITY;
        Size fallback = null;
        double fallbackScore = Double.POSITIVE_INFINITY;
        for (Size y : yuv) {
            if ((y.getWidth() & 1) != 0 || (y.getHeight() & 1) != 0) continue;
            boolean shared = false;
            for (Size s : surface) if (y.equals(s)) { shared = true; break; }
            if (!shared) continue;
            double aspect = y.getWidth() / (double) y.getHeight();
            double deviation = Math.abs(aspect - 16.0 / 9.0);
            // OCR was frequently given only 8-10 source pixels per Chinese
            // character at 720p. Prefer a SHARED 1080p preview/YUV stream
            // to preserve fine strokes before the detector downsamples it.
            // Never silently choose 4K or a mismatched preview/analysis size.
            double area = y.getWidth() * (double)y.getHeight();
            double targetArea = 1920.0 * 1080.0;
            if (area > targetArea * 1.05) continue;
            double areaScore = Math.abs(area - targetArea) / targetArea;
            if (deviation < .03 && areaScore < bestWideScore) {
                bestWide = y;
                bestWideScore = areaScore;
            }
            double score = deviation * 1000 + areaScore;
            if (score < fallbackScore) { fallback = y; fallbackScore = score; }
        }
        if (bestWide != null) return bestWide;
        if (fallback != null) {
            Log.w(TAG, "16:9 unsupported; preserving camera's native " +
                fallback.getWidth() + "x" + fallback.getHeight() + " without distortion");
            return fallback;
        }
        throw new IllegalArgumentException("摄像头没有可同时用于预览和OCR的图像尺寸");
    }

    private void configureSession(final int token) {
        if (camera == null || reader == null || !active || token != generation) return;
        SurfaceTexture st = textureView.getSurfaceTexture();
        if (st == null || !textureView.isAvailable()) {
            listener.onError("预览SurfaceTexture未准备好");
            return;
        }
        previewSurface = new Surface(st);
        Surface analysis = reader.getSurface();
        try {
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            builder.addTarget(analysis);
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON);
            // Handle flickering indoor lighting without expensive
            // per-frame enhancement or disturbing the continuous AF loop.
            builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
            repeatBuilder=builder;
            applyZoomCrop();
            camera.createCaptureSession(Arrays.asList(previewSurface, analysis),
                new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(CameraCaptureSession configured) {
                        if (!active || camera == null || token != generation) {
                            configured.close();
                            return;
                        }
                        session = configured;
                        try {
                            configured.setRepeatingRequest(builder.build(), null, handler);
                            Log.i(TAG, "Camera2 repeating preview request active");
                        } catch (CameraAccessException | IllegalStateException ex) {
                            listener.onError("无法启动手机摄像头预览：" + ex.getMessage());
                        }
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession failed) {
                        failed.close();
                        if (token == generation)
                            listener.onError("Camera2预览会话配置失败，请尝试切换摄像头");
                    }
                }, handler);
        } catch (CameraAccessException | IllegalArgumentException | IllegalStateException ex) {
            listener.onError("手机摄像头预览配置异常：" + ex.getMessage());
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

    /** Apply the same Camera2 crop to both preview and ImageReader in one repeating request. */
    public void setZoom(float zoom){
        wantedZoom=Math.max(1f,Math.min(3f,zoom));
        Handler h=handler;
        if(h!=null)h.post(()->{
            applyZoomCrop();
            if(session!=null&&repeatBuilder!=null){
                try{session.setRepeatingRequest(repeatBuilder.build(),null,handler);}
                catch(CameraAccessException|IllegalStateException e){Log.w(TAG,"Phone camera zoom unavailable",e);}
            }
        });
    }
    public float zoomLimit(){return supportedZoom;}
    private void applyZoomCrop(){
        if(repeatBuilder==null||sensorActiveArray==null)return;
        float factor=Math.max(1f,Math.min(supportedZoom,wantedZoom));
        int w=Math.max(2,(int)(sensorActiveArray.width()/factor));
        int h=Math.max(2,(int)(sensorActiveArray.height()/factor));
        int left=sensorActiveArray.centerX()-w/2;
        int top=sensorActiveArray.centerY()-h/2;
        Rect crop=new Rect(left,top,left+w,top+h);
        repeatBuilder.set(CaptureRequest.SCALER_CROP_REGION,crop);
    }

    public void stop() {
        generation++;
        active = false;
        openRequested = false;
        previewDelivered = false;
        repeatBuilder=null;
        if (session != null) { try { session.close(); } catch (Exception ignored) {} session = null; }
        if (camera != null) { try { camera.close(); } catch (Exception ignored) {} camera = null; }
        if (reader != null) { try { reader.close(); } catch (Exception ignored) {} reader = null; }
        if (previewSurface != null) {
            try { previewSurface.release(); } catch (Exception ignored) {}
            previewSurface = null;
        }
        if (worker != null) { worker.quitSafely(); worker = null; }
        handler = null;
    }
}
