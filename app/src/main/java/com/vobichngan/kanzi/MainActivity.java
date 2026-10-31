package com.vobichngan.kanzi;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.TextureView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.util.TypedValue;

import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.base.CameraActivity;
import com.jiangdg.ausbc.camera.bean.CameraRequest;
import com.jiangdg.ausbc.callback.ICameraStateCallBack;
import com.jiangdg.ausbc.callback.IPreviewDataCallBack;
import com.jiangdg.ausbc.widget.AspectRatioTextureView;
import com.jiangdg.ausbc.widget.IAspectRatio;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends CameraActivity {
    private static final String APP_VERSION = "0.7.0";
    private static final String PREFS_NAME = "kanzi_settings";
    private static final String PREF_AUTO_MODE = "automatic_mode";
    private static final String PREF_INTERVAL = "recognition_interval_ms";
    private static final String PREF_SIMILARITY = "similarity_threshold";
    private static final String PREF_QUEUE_SPEECH = "queue_speech";
    private static final String PREF_SPEECH_RATE = "speech_rate";
    private static final String PREF_PREVIEW_ROTATION = "preview_rotation";
    private static final String PREF_PREVIEW_MIRROR = "preview_mirror";
    private static final String PREF_PREVIEW_FLIP = "preview_flip";
    private static final String PREF_ENHANCED_OCR = "enhanced_ocr";
    private final Handler diagnosticHandler = new Handler(Looper.getMainLooper());
    private int diagnosticAttempts = 0;
    private final Runnable diagnosticRunnable = new Runnable() {
        @Override public void run() { diagnoseUsbDevices(); }
    };
    private FrameLayout previewContainer;
    private TextureView cameraTextureView;
    // Kept as non-visible diagnostic targets so existing USB diagnostics remain available.
    private TextView status, diagnostic;
    private Button modeButton, captureButton, startButton, settingsButton;
    private TextToSpeech tts;
    private PaddleOcrEngine recognizer;
    private volatile boolean running = false;
    private volatile boolean automaticMode = true;
    private volatile long recognitionIntervalMs = 500L;
    private volatile int similarityThreshold = 88;
    private volatile boolean queueSpeech = true;
    private volatile float speechRate = 1.0f;
    private volatile int previewRotation = 0;
    private volatile boolean previewMirror = false;
    private volatile boolean previewVerticalFlip = false;
    private volatile boolean enhancedOcr = false;
    private boolean ttsReady = false;
    private boolean previewCallbackAdded = false;
    private String lastSpoken = "";
    private String candidateText = "";
    private String candidateNormalized = "";
    private int candidateCount = 0;
    private long lastNonEmptyAt = 0;
    private long lastProcess = 0;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean manualCaptureRequested = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<SpeechItem> speechQueue = new ArrayDeque<>();
    private SpeechItem currentSpeech;
    private boolean speakingQueued = false;
    private long utteranceSequence = 0;

    private static final class SpeechItem {
        final String text;
        final String normalized;
        final String utteranceId;
        SpeechItem(String text, String normalized, String utteranceId) {
            this.text = text;
            this.normalized = normalized;
            this.utteranceId = utteranceId;
        }
    }

    private final IPreviewDataCallBack previewCallback = new IPreviewDataCallBack() {
        @Override public void onPreviewData(byte[] data, int width, int height, DataFormat format) {
            if (!running || data == null || format != DataFormat.NV21) return;
            long now = System.currentTimeMillis();
            boolean oneShot = manualCaptureRequested.get();
            if (oneShot) {
                if (!manualCaptureRequested.compareAndSet(true, false)) return;
            } else {
                if (!automaticMode || now - lastProcess < recognitionIntervalMs) return;
            }
            if (!busy.compareAndSet(false, true)) {
                if (oneShot) manualCaptureRequested.set(true);
                return;
            }
            lastProcess = now;
            byte[] frame = data.clone();
            final int rotation = previewRotation;
            final boolean mirror = previewMirror;
            final boolean flip = previewVerticalFlip;
            final boolean enhance = enhancedOcr;
            recognizer.recognizeNv21(frame, width, height, rotation, mirror, flip, enhance, found -> {
                busy.set(false);
                if (found != null) onRecognized(found);
            }, error -> {
                busy.set(false);
                runOnUiThread(() -> status.setText("飞桨 OCR 识别异常：" + error));
            });
        }
    };

    @Override protected View getRootView(LayoutInflater inflater) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundColor(Color.BLACK);

        // Keep diagnostic TextViews alive for internal status reporting, but do not show them.
        status = new TextView(this);
        status.setVisibility(View.GONE);
        diagnostic = new TextView(this);
        diagnostic.setVisibility(View.GONE);

        // The camera preview owns all available space above the bottom control strip.
        previewContainer = new FrameLayout(this);
        previewContainer.setBackgroundColor(Color.BLACK);
        root.addView(previewContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4), dp(4), dp(4), dp(4));
        controls.setBackgroundColor(0xFF151515);

        modeButton = createControlButton("模式：自动");
        controls.addView(modeButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        captureButton = createControlButton("识别一次");
        controls.addView(captureButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        startButton = createControlButton("开始识别");
        controls.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        settingsButton = createControlButton("设置");
        controls.addView(settingsButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        root.addView(controls, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(62)
        ));
        return root;
    }

    private Button createControlButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setTextColor(Color.WHITE);
        button.setMinHeight(0);
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(0xFF303030));
        return button;
    }

    private void updateControlLabels() {
        if (modeButton != null) modeButton.setText(automaticMode ? "模式：自动" : "模式：手动");
        if (startButton != null) startButton.setText(running ? "暂停识别" : "开始识别");
        if (captureButton != null) captureButton.setEnabled(previewCallbackAdded && !automaticMode);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        automaticMode = prefs.getBoolean(PREF_AUTO_MODE, true);
        recognitionIntervalMs = Math.max(1L, Math.min(10000L,
            prefs.getLong(PREF_INTERVAL, 500L)));
        similarityThreshold = Math.max(0, Math.min(100,
            prefs.getInt(PREF_SIMILARITY, 88)));
        queueSpeech = prefs.getBoolean(PREF_QUEUE_SPEECH, true);
        speechRate = Math.max(0.5f, Math.min(2.0f, prefs.getFloat(PREF_SPEECH_RATE, 1.0f)));
        previewRotation = prefs.getInt(PREF_PREVIEW_ROTATION, 0);
        if (previewRotation != 0 && previewRotation != 90 && previewRotation != 180 && previewRotation != 270) previewRotation = 0;
        previewMirror = prefs.getBoolean(PREF_PREVIEW_MIRROR, false);
        previewVerticalFlip = prefs.getBoolean(PREF_PREVIEW_FLIP, false);
        enhancedOcr = prefs.getBoolean(PREF_ENHANCED_OCR, false);
    }

    private void showSettingsDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(4));

        CheckBox enhancedCheck = new CheckBox(this);
        enhancedCheck.setText("增强图像处理（降噪、对比度增强、锐化）");
        enhancedCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        enhancedCheck.setChecked(enhancedOcr);
        panel.addView(enhancedCheck);

        TextView enhancedNote = new TextView(this);
        enhancedNote.setText("适合背景杂乱、文字与背景颜色接近的画面。处理会增加耗时；如果画面本身清晰，可关闭以保持速度。");
        enhancedNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        enhancedNote.setTextColor(0xFF667085);
        panel.addView(enhancedNote);

        CheckBox queueCheck = new CheckBox(this);
        queueCheck.setText("完整读完当前文字，再读下一段");
        queueCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        queueCheck.setChecked(queueSpeech);
        panel.addView(queueCheck);

        TextView intervalTitle = new TextView(this);
        intervalTitle.setText("自动 OCR 识别间隔（毫秒）");
        intervalTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        intervalTitle.setPadding(0, dp(14), 0, dp(4));
        panel.addView(intervalTitle);

        EditText intervalInput = new EditText(this);
        intervalInput.setSingleLine(true);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalInput.setText(String.valueOf(recognitionIntervalMs));
        intervalInput.setHint("输入 1 到 10000 毫秒");
        panel.addView(intervalInput);

        TextView intervalNote = new TextView(this);
        intervalNote.setText("例如 1000 毫秒约为每秒一次；设为 1 毫秒也不会强制每秒识别 1000 次，实际速度受单次 OCR 耗时限制，程序不会堆积识别任务。");
        intervalNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        intervalNote.setTextColor(0xFF667085);
        panel.addView(intervalNote);

        TextView similarityTitle = new TextView(this);
        similarityTitle.setText("两次识别文字相似度阈值：" + similarityThreshold + "%");
        similarityTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        similarityTitle.setPadding(0, dp(16), 0, dp(4));
        panel.addView(similarityTitle);

        SeekBar similaritySeek = new SeekBar(this);
        similaritySeek.setMax(100);
        similaritySeek.setProgress(similarityThreshold);
        similaritySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                similarityTitle.setText("两次识别文字相似度阈值：" + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(similaritySeek);

        TextView speechTitle = new TextView(this);
        speechTitle.setText("朗读语速：" + String.format(Locale.US, "%.2f", speechRate) + " 倍");
        speechTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        speechTitle.setPadding(0, dp(18), 0, dp(4));
        panel.addView(speechTitle);

        SeekBar speechRateSeek = new SeekBar(this);
        speechRateSeek.setMax(150);
        speechRateSeek.setProgress(Math.round((speechRate - 0.5f) * 100f));
        speechRateSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 0.5f + progress / 100f;
                speechTitle.setText("朗读语速：" + String.format(Locale.US, "%.2f", value) + " 倍");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(speechRateSeek);

        Button speechPreviewButton = createControlButton("试听当前语速");
        speechPreviewButton.setOnClickListener(v -> {
            if (tts != null && ttsReady) {
                tts.setSpeechRate(0.5f + speechRateSeek.getProgress() / 100f);
                tts.speak("看字助手语音试听，现在是当前设置的朗读速度。", TextToSpeech.QUEUE_FLUSH, null, "settings-preview");
            } else {
                Toast.makeText(this, "中文语音尚未准备好", Toast.LENGTH_SHORT).show();
            }
        });
        panel.addView(speechPreviewButton);

        TextView rotationTitle = new TextView(this);
        rotationTitle.setText("摄像头画面旋转：" + previewRotation + "°");
        rotationTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        rotationTitle.setPadding(0, dp(18), 0, dp(4));
        panel.addView(rotationTitle);

        SeekBar rotationSeek = new SeekBar(this);
        rotationSeek.setMax(3);
        rotationSeek.setProgress(previewRotation / 90);
        rotationSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rotationTitle.setText("摄像头画面旋转：" + (progress * 90) + "°");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(rotationSeek);

        CheckBox mirrorCheck = new CheckBox(this);
        mirrorCheck.setText("水平镜像");
        mirrorCheck.setChecked(previewMirror);
        panel.addView(mirrorCheck);
        CheckBox flipCheck = new CheckBox(this);
        flipCheck.setText("垂直翻转");
        flipCheck.setChecked(previewVerticalFlip);
        panel.addView(flipCheck);

        TextView transformNote = new TextView(this);
        transformNote.setText("画面方向设置会同时应用于预览和 OCR 识别，不会改变 USB 摄像头采集格式。");
        transformNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        transformNote.setTextColor(0xFF667085);
        panel.addView(transformNote);

        TextView similarityNote = new TextView(this);
        similarityNote.setText("数值越高，判断越严格；设为 0 时关闭相似度去重，但仍要求连续两次识别后才朗读。");
        similarityNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        similarityNote.setTextColor(0xFF667085);
        panel.addView(similarityNote);

        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.addView(panel);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("识别与朗读设置")
            .setView(settingsScroll)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", (dialog, which) -> {
                long interval = 500L;
                try {
                    interval = Long.parseLong(intervalInput.getText().toString().trim());
                } catch (NumberFormatException ignored) {}
                recognitionIntervalMs = Math.max(1L, Math.min(10000L, interval));
                similarityThreshold = similaritySeek.getProgress();
                boolean wasQueueEnabled = queueSpeech;
                queueSpeech = queueCheck.isChecked();
                speechRate = 0.5f + speechRateSeek.getProgress() / 100f;
                previewRotation = rotationSeek.getProgress() * 90;
                previewMirror = mirrorCheck.isChecked();
                previewVerticalFlip = flipCheck.isChecked();
                enhancedOcr = enhancedCheck.isChecked();
                if (tts != null) tts.setSpeechRate(speechRate);
                applyPreviewTransform();
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_AUTO_MODE, automaticMode)
                    .putLong(PREF_INTERVAL, recognitionIntervalMs)
                    .putInt(PREF_SIMILARITY, similarityThreshold)
                    .putBoolean(PREF_QUEUE_SPEECH, queueSpeech)
                    .putFloat(PREF_SPEECH_RATE, speechRate)
                    .putInt(PREF_PREVIEW_ROTATION, previewRotation)
                    .putBoolean(PREF_PREVIEW_MIRROR, previewMirror)
                    .putBoolean(PREF_PREVIEW_FLIP, previewVerticalFlip)
                    .putBoolean(PREF_ENHANCED_OCR, enhancedOcr)
                    .apply();
                if (wasQueueEnabled && !queueSpeech) clearSpeechQueue(true);
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    @Override protected IAspectRatio getCameraView() {
        cameraTextureView = new AspectRatioTextureView(this);
        applyPreviewTransform();
        return (IAspectRatio) cameraTextureView;
    }

    private void applyPreviewTransform() {
        if (cameraTextureView == null) return;
        cameraTextureView.setRotation(previewRotation);
        cameraTextureView.setScaleX(previewMirror ? -1.0f : 1.0f);
        cameraTextureView.setScaleY(previewVerticalFlip ? -1.0f : 1.0f);
    }

    @Override protected ViewGroup getCameraViewContainer() { return previewContainer; }
    @Override protected int getGravity() { return Gravity.CENTER; }

    @Override protected CameraRequest getCameraRequest() {
        return new CameraRequest.Builder()
            .setPreviewWidth(1280)
            .setPreviewHeight(720)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setRawPreviewData(true)
            .setAspectRatioShow(true)
            .create();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadSettings();
        applyPreviewTransform();
        updateControlLabels();
        diagnosticAttempts = 0;
        diagnosticHandler.postDelayed(diagnosticRunnable, 1200);
        recognizer = new PaddleOcrEngine(getApplicationContext(), new PaddleOcrEngine.Listener() {
            @Override public void onReady() {
                runOnUiThread(() -> status.setText("PP-OCRv6 已加载，等待 USB 摄像头…"));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> status.setText("飞桨 OCR 初始化失败：" + message));
            }
        });
        recognizer.initialize();
        tts = new TextToSpeech(this, code -> {
            if (code == TextToSpeech.SUCCESS) {
                int language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                tts.setSpeechRate(speechRate);
                ttsReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED;
                if (ttsReady) {
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) {}
                        @Override public void onDone(String utteranceId) {
                            mainHandler.post(() -> finishQueuedUtterance(utteranceId));
                        }
                        @Override public void onError(String utteranceId) {
                            mainHandler.post(() -> finishQueuedUtterance(utteranceId));
                        }
                        @Override public void onStop(String utteranceId, boolean interrupted) {
                            mainHandler.post(() -> finishQueuedUtterance(utteranceId));
                        }
                    });
                    speakNextQueued();
                } else {
                    status.setText("系统没有可用的中文语音，请先安装中文语音包");
                }
            }
        });

        modeButton.setOnClickListener(v -> {
            automaticMode = !automaticMode;
            manualCaptureRequested.set(false);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_AUTO_MODE, automaticMode).apply();
            updateControlLabels();
            Toast.makeText(this, automaticMode ? "已切换为自动识别" : "已切换为手动识别",
                Toast.LENGTH_SHORT).show();
        });

        captureButton.setOnClickListener(v -> {
            if (!previewCallbackAdded) {
                Toast.makeText(this, "请先连接并打开摄像头", Toast.LENGTH_SHORT).show();
                return;
            }
            running = true;
            manualCaptureRequested.set(true);
            updateControlLabels();
            Toast.makeText(this, "正在抓取当前画面识别", Toast.LENGTH_SHORT).show();
        });

        startButton.setOnClickListener(v -> {
            if (!previewCallbackAdded) {
                Toast.makeText(this, "请先连接并打开摄像头", Toast.LENGTH_SHORT).show();
                return;
            }
            running = !running;
            if (!running) {
                manualCaptureRequested.set(false);
                clearSpeechQueue(true);
            }
            updateControlLabels();
        });

        settingsButton.setOnClickListener(v -> showSettingsDialog());
    }

    @Override protected UsbDevice getDefaultCamera() {
        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        if (manager == null) return null;
        Map<String, UsbDevice> devices = manager.getDeviceList();
        if (devices == null) return null;
        for (UsbDevice device : devices.values()) {
            if (device.getVendorId() == 0xEBA4 && device.getProductId() == 0x6579) return device;
        }
        return null;
    }

    @Override public void onCameraState(MultiCameraClient.ICamera self, ICameraStateCallBack.State code, String msg) {
        if (code == ICameraStateCallBack.State.OPENED) {
            if (!previewCallbackAdded) {
                addPreviewDataCallBack(previewCallback);
                previewCallbackAdded = true;
            }
            runOnUiThread(() -> {
                applyPreviewTransform();
                updateControlLabels();
            });
        } else if (code == ICameraStateCallBack.State.CLOSED) {
            previewCallbackAdded = false;
            manualCaptureRequested.set(false);
            runOnUiThread(() -> updateControlLabels());
        } else if (code == ICameraStateCallBack.State.ERROR) {
            runOnUiThread(() -> status.setText("USB 摄像头启动失败：" + (msg == null ? "请重新插拔摄像头" : msg)));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        diagnosticAttempts = 0;
        diagnosticHandler.removeCallbacks(diagnosticRunnable);
        diagnosticHandler.post(diagnosticRunnable);
    }

    private void diagnoseUsbDevices() {
        diagnosticAttempts++;
        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        Map<String, UsbDevice> systemDevices = manager == null ? null : manager.getDeviceList();
        List<UsbDevice> uvcDevices = null;
        try {
            uvcDevices = getDeviceList();
        } catch (Exception e) {
            diagnostic.setText(APP_VERSION + "：UVC 设备列表读取异常：" + e.getClass().getSimpleName());
            return;
        }

        int systemCount = systemDevices == null ? 0 : systemDevices.size();
        int uvcCount = uvcDevices == null ? -1 : uvcDevices.size();
        StringBuilder info = new StringBuilder(APP_VERSION)
            .append("：安卓 USB 设备 ").append(systemCount)
            .append(" 个；UVC 库设备列表 ")
            .append(uvcCount < 0 ? "尚未初始化" : uvcCount + " 个");

        if (systemDevices != null) {
            for (UsbDevice device : systemDevices.values()) appendDeviceInfo(info, "安卓", device);
        }
        if (uvcDevices != null) {
            for (UsbDevice device : uvcDevices) appendDeviceInfo(info, "UVC", device);
        }

        if (systemCount == 0 && uvcCount <= 0) {
            info.append("\n暂时没有读到设备。正在等待 USB 监控初始化/设备插入…");
            info.append("\n持续监测中：每 2 秒重新读取系统 USB 设备列表；插拔后无需重启应用。");
            diagnostic.setText(info.toString());
            diagnosticHandler.postDelayed(diagnosticRunnable, 2000);
            return;
        }
        boolean permission = false;
        if (systemDevices != null) {
            for (UsbDevice device : systemDevices.values()) {
                if (device.getVendorId() == 0xEBA4 && device.getProductId() == 0x6579) {
                    permission = manager != null && manager.hasPermission(device);
                }
            }
        }
        boolean cameraOpened = false;
        try { cameraOpened = isCameraOpened(); } catch (Exception ignored) {}
        info.append("\nUSB权限：").append(permission ? "已授予" : "未授予");
        info.append("\n预览容器：").append(previewContainer == null ? "未创建" :
            previewContainer.getWidth() + "×" + previewContainer.getHeight() +
            "，子视图 " + previewContainer.getChildCount() + " 个");
        info.append("\n摄像头状态：").append(cameraOpened ? "已打开" : "未打开");
        info.append("\n预览回调：").append(previewCallbackAdded ? "已注册" : "未注册");
        info.append("\n摄像头视图：").append(cameraTextureView == null ? "未创建" :
            cameraTextureView.getWidth() + "×" + cameraTextureView.getHeight());
        info.append("\nTextureView表面：").append(cameraTextureView == null ? "未知" :
            cameraTextureView.isAvailable() ? "已就绪" : "未就绪");
        if (uvcCount > 0 && !permission) {
            info.append("\n判断：设备已枚举，但 USB 权限未授予，重点检查授权阶段。");
        } else if (uvcCount > 0 && permission && !cameraOpened) {
            info.append("\n判断：USB 权限已具备，但摄像头未打开，重点检查连接/打开阶段。");
        } else if (cameraOpened) {
            info.append("\n判断：摄像头已打开，继续检查预览数据回调。");
        } else if (systemCount > 0) {
            info.append("\n判断：安卓已发现设备，UVC 库连接状态仍需确认。");
        }
        diagnostic.setText(info.toString());
        diagnosticHandler.postDelayed(diagnosticRunnable, 2000);
    }

    private void appendDeviceInfo(StringBuilder info, String source, UsbDevice device) {
        info.append("\n[").append(source).append("] ")
            .append(device.getDeviceName())
            .append(" VID:").append(String.format(Locale.US, "%04X", device.getVendorId()))
            .append(" PID:").append(String.format(Locale.US, "%04X", device.getProductId()))
            .append(" class:").append(device.getDeviceClass())
            .append("/").append(device.getDeviceSubclass())
            .append(" interfaces:").append(device.getInterfaceCount());
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            android.hardware.usb.UsbInterface intf = device.getInterface(i);
            info.append(" [").append(i)
                .append(":class=").append(intf.getInterfaceClass())
                .append(",sub=").append(intf.getInterfaceSubclass())
                .append(",proto=").append(intf.getInterfaceProtocol())
                .append(",eps=").append(intf.getEndpointCount()).append("]");
        }
    }

    private void onRecognized(String found) {
        final long now = System.currentTimeMillis();
        final String recognizedText = found == null ? "" : found.trim();

        if (recognizedText.isEmpty()) {
            candidateText = "";
            candidateNormalized = "";
            candidateCount = 0;
            if (lastNonEmptyAt > 0 && now - lastNonEmptyAt >= 2800) {
                lastSpoken = "";
            }
            return;
        }

        lastNonEmptyAt = now;
        final String normalized = normalizeOcrText(recognizedText);
        if (!running || !ttsReady || normalized.length() < 2) return;

        final float threshold = similarityThreshold / 100.0f;
        if (!candidateNormalized.isEmpty() && (threshold <= 0f || textSimilarity(candidateNormalized, normalized) >= threshold)) {
            candidateCount++;
            if (threshold <= 0f || recognizedText.length() > candidateText.length()) candidateText = recognizedText;
            candidateNormalized = normalized;
        } else {
            candidateText = recognizedText;
            candidateNormalized = normalized;
            candidateCount = 1;
        }

        // Confirm the same content in two consecutive OCR passes before speaking.
        if (candidateCount < 2) return;
        final String textToSpeak = candidateText;
        candidateCount = 0;

        runOnUiThread(() -> {
            if (!running || tts == null || !ttsReady) return;
            String id = "ocr-" + (++utteranceSequence);
            if (queueSpeech) {
                if (isAlreadyQueuedOrRecentlySpoken(normalized, threshold)) return;
                speechQueue.addLast(new SpeechItem(textToSpeak, normalized, id));
                speakNextQueued();
            } else {
                if (threshold > 0f && !lastSpoken.isEmpty() && textSimilarity(lastSpoken, normalized) >= threshold) return;
                lastSpoken = normalized;
                tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, id);
            }
        });
    }

    private boolean isAlreadyQueuedOrRecentlySpoken(String normalized, float threshold) {
        if (threshold <= 0f) return false;
        if (!lastSpoken.isEmpty() && textSimilarity(lastSpoken, normalized) >= threshold) return true;
        if (currentSpeech != null &&
            textSimilarity(currentSpeech.normalized, normalized) >= threshold) return true;
        for (SpeechItem item : speechQueue) {
            if (textSimilarity(item.normalized, normalized) >= threshold) return true;
        }
        return false;
    }

    private void speakNextQueued() {
        if (!queueSpeech || !ttsReady || tts == null || speakingQueued || speechQueue.isEmpty()) return;
        currentSpeech = speechQueue.pollFirst();
        speakingQueued = true;
        lastSpoken = currentSpeech.normalized;
        int result = tts.speak(currentSpeech.text, TextToSpeech.QUEUE_FLUSH, null,
            currentSpeech.utteranceId);
        if (result == TextToSpeech.ERROR) finishQueuedUtterance(currentSpeech.utteranceId);
    }

    private void finishQueuedUtterance(String utteranceId) {
        if (currentSpeech == null || !currentSpeech.utteranceId.equals(utteranceId)) return;
        currentSpeech = null;
        speakingQueued = false;
        speakNextQueued();
    }

    private void clearSpeechQueue(boolean stopCurrent) {
        speechQueue.clear();
        currentSpeech = null;
        speakingQueued = false;
        if (stopCurrent && tts != null) tts.stop();
    }

    private String normalizeOcrText(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\p{Punct}，。！？、；：…（）【】《》“”‘’·]+", "");
    }

    private float textSimilarity(String left, String right) {
        if (left.equals(right)) return 1.0f;
        if (left.isEmpty() || right.isEmpty()) return 0.0f;
        int shorter = Math.min(left.length(), right.length());
        int longer = Math.max(left.length(), right.length());
        if (shorter < 2 || (float) shorter / longer < 0.70f) return 0.0f;

        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return 1.0f - (float) previous[right.length()] / longer;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        running = false;
        manualCaptureRequested.set(false);
        speechQueue.clear();
        currentSpeech = null;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (recognizer != null) recognizer.release();
        diagnosticHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
