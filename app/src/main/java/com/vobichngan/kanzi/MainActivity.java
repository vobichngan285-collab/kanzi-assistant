package com.vobichngan.kanzi;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.TypedValue;
import android.util.Log;

import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.base.CameraActivity;
import com.jiangdg.ausbc.camera.bean.CameraRequest;
import com.jiangdg.ausbc.callback.ICameraStateCallBack;
import com.jiangdg.ausbc.callback.IPreviewDataCallBack;
import com.jiangdg.ausbc.camera.bean.PreviewSize;
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
    private static final String APP_VERSION = "0.9.3";
    private static final String PREF_NO_ENGLISH = "speech_no_english";
    private static final String PREF_NO_NUMBERS = "speech_no_numbers";
    private static final String PREF_MAX_SPEECH_CHARS = "speech_max_chars";
    private static final String PREFS_NAME = "kanzi_settings";
    private static final String PREF_AUTO_MODE = "automatic_mode";
    private static final String PREF_INTERVAL = "recognition_interval_ms";
    private static final String PREF_SIMILARITY = "similarity_threshold";
    private static final String PREF_QUEUE_SPEECH = "queue_speech";
    private static final String PREF_LONG_READING = "reading_mode_long";
    private static final String PREF_SPEECH_RATE = "speech_rate";
    private static final String PREF_PREVIEW_ROTATION = "preview_rotation";
    private static final String PREF_LEGACY_NV21 = "ocr_legacy_nv21_jpeg";
    private static final String PREF_PREVIEW_MIRROR = "preview_mirror";
    private static final String PREF_PREVIEW_FLIP = "preview_flip";
    private static final String PREF_ENHANCED_OCR = "enhanced_ocr";
    private static final String PREF_OCR_MODEL = "ocr_model_id";
    private static final String PREF_PHONE_CAMERA = "phone_camera_source";
    private static final String PREF_FRONT_CAMERA = "phone_camera_front";
    private static final String PREF_QUALITY_PROMPTS = "quality_voice_prompts";
    private static final int REQUEST_PHONE_CAMERA = 4051;
    private final Handler diagnosticHandler = new Handler(Looper.getMainLooper());
    private int diagnosticAttempts = 0;
    private final Runnable diagnosticRunnable = new Runnable() {
        @Override public void run() { diagnoseUsbDevices(); }
    };
    private FrameLayout previewContainer;
    private TextureView cameraTextureView;
    // Kept as non-visible diagnostic targets so existing USB diagnostics remain available.
    private TextView status, diagnostic;
    private Button modeButton, captureButton, startButton, settingsButton, readingModeButton;
    private Button cameraSourceButton, lensButton;
    private TextureView phoneTexture;
    private TextView phonePreviewStatus;
    private int phoneStartSequence = 0;
    private boolean phonePreviewReceived = false;
    private InternalCameraController phoneController;
    private OcrOverlayView ocrOverlay;
    private volatile boolean usingPhoneCamera = false;
    private volatile boolean phoneFrontFacing = false;
    private volatile int phoneOcrRotation = 0;
    private volatile int phoneRawWidth = 1280, phoneRawHeight = 720;
    private volatile boolean qualityPromptsEnabled = true;
    private long noTextSince = 0L, lastQualityPromptAt = 0L;
    private TextToSpeech tts;
    private PaddleOcrEngine recognizer;
    private volatile boolean running = false;
    private volatile boolean automaticMode = true;
    private volatile long recognitionIntervalMs = 500L;
    private volatile int similarityThreshold = 88;
    private volatile boolean queueSpeech = true;
    private volatile boolean longReadingMode = true;
    private volatile boolean speechPageActive = false;
    private final FrameChangeGate frameChangeGate = new FrameChangeGate();
    private final java.util.concurrent.atomic.AtomicInteger readingEpoch = new java.util.concurrent.atomic.AtomicInteger(0);
    private volatile float speechRate = 1.0f;
    private volatile int previewRotation = 0;
    private volatile boolean legacyNv21Jpeg = false;
    private volatile boolean previewMirror = false;
    private volatile boolean previewVerticalFlip = false;
    private volatile boolean enhancedOcr = false;
    private volatile String ocrModelId = "ppocrv6_small";
    private volatile String lastDiagnosticText = "";
    private volatile boolean speechNoEnglish = false;
    private volatile boolean speechNoNumbers = false;
    private volatile int maxSpeechChars = 0;
    private final AtomicBoolean diagnosticCaptureRequested = new AtomicBoolean(false);
    private volatile TextView diagnosticReportView;
    private volatile long diagnosticStartedAt = 0;
    private boolean ttsReady = false;
    private boolean previewCallbackAdded = false;
    private volatile String lastSpoken = "";
    private String candidateText = "";
    private String candidateNormalized = "";
    private volatile int candidateCount = 0;
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
            if (format == DataFormat.NV21 && !usingPhoneCamera)
                processCameraFrame(data, width, height, false);
        }
    };

    /** Both camera drivers feed one single-slot OCR pipeline. */
    private void processCameraFrame(byte[] data, int width, int height, boolean isPhoneCamera) {
            if (data == null || (isPhoneCamera != usingPhoneCamera)) return;
            if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0 ||
                data.length != ((long) width * height * 3L) / 2L) {
                Log.w("KanziCamera", "Discard invalid NV21 frame: " + width + "x" + height +
                    ", bytes=" + data.length);
                return;
            }
            if (diagnosticCaptureRequested.compareAndSet(true, false)) {
                if (!busy.compareAndSet(false, true)) {
                    diagnosticCaptureRequested.set(true);
                    return;
                }
                final byte[] diagnosticFrame = data.clone();
                final long started = diagnosticStartedAt;
                final boolean legacyForDiagnostic = legacyNv21Jpeg;
                recognizer.diagnoseNv21(diagnosticFrame, width, height, enhancedOcr, legacyForDiagnostic,
                    report -> {
                        busy.set(false);
                        String result = "诊断完成\n摄像头帧：已获取 " + width + "×" + height
                            + "\nOCR 引擎：已就绪\n检测框数量：" + report.getBoxCount()
                            + "\n当前模型：" + report.getModelId()
                            + "\nOCR 输入方向：原始摄像头帧（固定 0°）"
                            + "\n图像解码：" + (legacyForDiagnostic ? "0.7.0 JPEG 兼容方式" : "OpenCV NV21 方式")
                            + "\n平均置信度：" + String.format(Locale.US, "%.3f", report.getAverageConfidence())
                            + "\n检测输入尺寸：" + report.getDetectorInputShape()
                            + "\n识别输入尺寸：" + report.getRecognitionInputShapes()
                            + "\n检测耗时：" + report.getDetectionTimeMs() + " ms"
                            + "\n识别耗时：" + report.getRecognitionTimeMs() + " ms"
                            + "\n总耗时：" + report.getTotalTimeMs() + " ms"
                            + "\n诊断总用时：" + (System.currentTimeMillis() - started) + " ms"
                            + "\n逐框原始识别（未做低置信度过滤）：\n" + report.getRawDetails()
                            + "\n\n整理后的识别结果：\n" + (report.getText().isEmpty() ? "（未识别到文字）" : report.getText());
                        lastDiagnosticText = report.getText();
                        runOnUiThread(() -> { if (diagnosticReportView != null) diagnosticReportView.setText(result); });
                    },
                    error -> {
                        busy.set(false);
                        runOnUiThread(() -> { if (diagnosticReportView != null) diagnosticReportView.setText(
                            "诊断失败\n摄像头帧：" + width + "×" + height + "\n原因：" + error); });
                    });
                return;
            }
            if (!running) return;
            // Long reading owns OCR until the entire spoken page has completed.
            // Manual capture remains an explicit override below.
            long now = System.currentTimeMillis();
            boolean oneShot = manualCaptureRequested.get();
            if (oneShot) {
                if (!manualCaptureRequested.compareAndSet(true, false)) return;
            } else {
                if (!automaticMode || now - lastProcess < recognitionIntervalMs) return;
                if (longReadingMode && speechPageActive) return;
            }
            if (!busy.compareAndSet(false, true)) {
                if (oneShot) manualCaptureRequested.set(true);
                return;
            }
            // Long reading needs a SECOND OCR pass to confirm the first candidate,
            // even when the image has not moved. Once spoken, avoid re-reading it.
            boolean needsConfirmation = longReadingMode && candidateCount > 0;
            boolean samePageLocked = longReadingMode && !lastSpoken.isEmpty();
            if (!oneShot && !needsConfirmation &&
                !frameChangeGate.accept(data, width, height, now, samePageLocked)) {
                busy.set(false);
                return;
            }
            lastProcess = now;
            final int recognizedEpoch = readingEpoch.get();
            byte[] frame = data.clone();
            final boolean enhance = enhancedOcr;
            final FrameQuality.Assessment quality = FrameQuality.assess(frame, width, height);
            final boolean legacyDecode = legacyNv21Jpeg;
            try {
                recognizer.recognizeNv21(frame, width, height, enhance, legacyDecode, found -> {
                    busy.set(false);
                    if (found != null && recognizedEpoch == readingEpoch.get()) {
                        if (ocrOverlay != null) {
                            ocrOverlay.show(found.getBoxes(), found.getFrameWidth(),
                                found.getFrameHeight(),
                                isPhoneCamera ? phoneTexture : cameraTextureView,
                                isPhoneCamera, isPhoneCamera ? phoneOcrRotation : 0);
                        }
                        onRecognized(found.getText(), oneShot);
                        onReadingQuality(found, quality);
                    }
                }, error -> {
                    busy.set(false);
                    Log.e("KanziOCR", "OCR failed for " + width + "x" + height, new RuntimeException(error));
                    runOnUiThread(() -> status.setText("飞桨 OCR 识别异常：" + error));
                });
            } catch (Throwable error) {
                busy.set(false);
                Log.e("KanziOCR", "Could not submit OCR frame", error);
            }
    }

    @Override protected View getRootView(LayoutInflater inflater) {
        // Use a full-screen overlay root: the control row must not consume preview height.
        // This lets a quarter-turned 16:9 camera image use the full portrait screen area.
        FrameLayout root = new FrameLayout(this);
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundColor(Color.BLACK);

        // Keep diagnostic TextViews alive for internal status reporting, but do not show them.
        status = new TextView(this);
        status.setVisibility(View.GONE);
        diagnostic = new TextView(this);
        diagnostic.setVisibility(View.GONE);

        previewContainer = new FrameLayout(this);
        previewContainer.setBackgroundColor(Color.BLACK);
        previewContainer.setClipChildren(true);
        previewContainer.setClipToPadding(true);
        root.addView(previewContainer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Floating controls sit on top of the preview instead of taking away its height.
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4), dp(4), dp(4), dp(4));
        controls.setBackgroundColor(0xAA151515);

        phoneTexture = new TextureView(this);
        // Letterbox areas show the previewContainer's black background.
        phoneTexture.setOpaque(false);
        phoneTexture.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (l != ol || t != ot || r != or || b != ob)
                applyPhonePreviewTransform();
        });
        phoneTexture.setVisibility(View.GONE);
        // Do NOT add the phone preview to previewContainer here.
        // CameraActivity.initView() invokes previewContainer.removeAllViews()
        // after getRootView() returns; attaching here silently removes this
        // TextureView and causes both phone lenses to remain black.
        // attachPhonePreviewAfterUsbInit() adds it after super.onCreate().
        ocrOverlay = new OcrOverlayView(this);
        root.addView(ocrOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        phonePreviewStatus = new TextView(this);
        phonePreviewStatus.setTextColor(Color.WHITE);
        phonePreviewStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        phonePreviewStatus.setGravity(Gravity.CENTER);
        phonePreviewStatus.setPadding(dp(20), dp(10), dp(20), dp(10));
        phonePreviewStatus.setBackgroundColor(0xA9000000);
        phonePreviewStatus.setVisibility(View.GONE);
        phonePreviewStatus.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        phonePreviewStatus.setClickable(false);
        FrameLayout.LayoutParams phoneStatusParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(104), Gravity.CENTER);
        root.addView(phonePreviewStatus, phoneStatusParams);

        cameraSourceButton = createControlButton("摄像头：USB 外接");
        lensButton = createControlButton("切换前后");
        lensButton.setContentDescription("切换手机前置和后置摄像头");
        LinearLayout cameraRow = new LinearLayout(this);
        cameraRow.setOrientation(LinearLayout.HORIZONTAL);
        cameraRow.addView(cameraSourceButton, new LinearLayout.LayoutParams(0, dp(48), 2f));
        cameraRow.addView(lensButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        readingModeButton = createControlButton("阅读：长文（完整读）");
        readingModeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        readingModeButton.setMinHeight(dp(50));
        readingModeButton.setContentDescription("切换长文阅读和快速扫读模式");
        LinearLayout controlDock = new LinearLayout(this);
        controlDock.setOrientation(LinearLayout.VERTICAL);
        readingModeButton.setBackgroundTintList(ColorStateList.valueOf(0xFF154A5B));
        controlDock.addView(cameraRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ));
        controlDock.addView(readingModeButton, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));
        modeButton = createControlButton("模式：自动");
        controls.addView(modeButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        captureButton = createControlButton("识别一次");
        controls.addView(captureButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        startButton = createControlButton("开始识别");
        controls.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        settingsButton = createControlButton("设置");
        controls.addView(settingsButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        controlDock.addView(controls, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(62)
        ));
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(162), Gravity.BOTTOM
        );
        root.addView(controlDock, controlsParams);
        controlDock.bringToFront();
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
        if (cameraSourceButton != null) {
            cameraSourceButton.setText(usingPhoneCamera ? "摄像头：手机" : "摄像头：USB 外接");
            cameraSourceButton.setContentDescription(usingPhoneCamera
                ? "当前手机摄像头，点击切换到USB外接摄像头"
                : "当前USB摄像头，点击切换到手机摄像头");
        }
        if (lensButton != null) {
            lensButton.setVisibility(usingPhoneCamera ? View.VISIBLE : View.GONE);
            lensButton.setText(phoneFrontFacing ? "前置→后置" : "后置→前置");
        }
        if (readingModeButton != null) {
            readingModeButton.setText(longReadingMode
                ? "长文阅读｜完整读完，不打断"
                : "快速扫读｜移动即读新内容");
            readingModeButton.setContentDescription(longReadingMode
                ? "当前是长文阅读模式。点击切换到快速扫读"
                : "当前是快速扫读模式。点击切换到长文阅读");
        }
        if (modeButton != null) modeButton.setText(automaticMode ? "模式：自动" : "模式：手动");
        if (startButton != null) startButton.setText(running ? "暂停识别" : "开始识别");
        if (captureButton != null) captureButton.setEnabled((usingPhoneCamera || previewCallbackAdded) && !automaticMode);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        automaticMode = prefs.getBoolean(PREF_AUTO_MODE, true);
        recognitionIntervalMs = Math.max(1L, Math.min(10000L,
            prefs.getLong(PREF_INTERVAL, 500L)));
        similarityThreshold = Math.max(0, Math.min(100,
            prefs.getInt(PREF_SIMILARITY, 88)));
        queueSpeech = prefs.getBoolean(PREF_QUEUE_SPEECH, true);
        usingPhoneCamera = prefs.getBoolean(PREF_PHONE_CAMERA, false);
        phoneFrontFacing = prefs.getBoolean(PREF_FRONT_CAMERA, false);
        qualityPromptsEnabled = prefs.getBoolean(PREF_QUALITY_PROMPTS, true);
        longReadingMode = prefs.getBoolean(PREF_LONG_READING, queueSpeech);
        speechRate = Math.max(0.5f, Math.min(2.0f, prefs.getFloat(PREF_SPEECH_RATE, 1.0f)));
        previewRotation = prefs.getInt(PREF_PREVIEW_ROTATION, 0);
        if (previewRotation != 0 && previewRotation != 90 && previewRotation != 180 && previewRotation != 270) previewRotation = 0;
        legacyNv21Jpeg = prefs.getBoolean(PREF_LEGACY_NV21, false);
        previewMirror = prefs.getBoolean(PREF_PREVIEW_MIRROR, false);
        previewVerticalFlip = prefs.getBoolean(PREF_PREVIEW_FLIP, false);
        enhancedOcr = prefs.getBoolean(PREF_ENHANCED_OCR, false);
        ocrModelId = prefs.getString(PREF_OCR_MODEL, "ppocrv6_small");
        if (!ocrModelId.equals("ppocrv6_small") && !ocrModelId.equals("ppocrv6_tiny") && !ocrModelId.equals("ppocrv5_mobile")) ocrModelId = "ppocrv6_small";
        speechNoEnglish = prefs.getBoolean(PREF_NO_ENGLISH, false);
        speechNoNumbers = prefs.getBoolean(PREF_NO_NUMBERS, false);
        maxSpeechChars = Math.max(0, Math.min(5000, prefs.getInt(PREF_MAX_SPEECH_CHARS, 0)));
    }

    private String ocrModelDisplayName(String modelId) {
        if ("ppocrv6_tiny".equals(modelId)) return "PP-OCRv6 Tiny";
        if ("ppocrv5_mobile".equals(modelId)) return "PP-OCRv5 Mobile";
        return "PP-OCRv6 Small";
    }

    private void switchOcrModel(String modelId) {
        readingEpoch.incrementAndGet();
        frameChangeGate.reset();
        running = false;
        manualCaptureRequested.set(false);
        clearSpeechQueue(true);
        candidateText = "";
        candidateNormalized = "";
        candidateCount = 0;
        lastSpoken = "";
        lastDiagnosticText = "";
        busy.set(false);
        if (recognizer != null) recognizer.release();
        status.setText("正在切换到 " + ocrModelDisplayName(modelId) + "…");
        recognizer = new PaddleOcrEngine(getApplicationContext(), new PaddleOcrEngine.Listener() {
            @Override public void onReady() {
                runOnUiThread(() -> {
                    status.setText(ocrModelDisplayName(modelId) + " 已加载，可以开始识别");
                    Toast.makeText(MainActivity.this, ocrModelDisplayName(modelId) + " 已切换", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (!"ppocrv6_small".equals(modelId)) {
                        Toast.makeText(MainActivity.this, ocrModelDisplayName(modelId) + " 初始化失败，已自动回退到 PP-OCRv6 Small。", Toast.LENGTH_LONG).show();
                        ocrModelId = "ppocrv6_small";
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putString(PREF_OCR_MODEL, ocrModelId).apply();
                        switchOcrModel("ppocrv6_small");
                    } else {
                        status.setText(ocrModelDisplayName(modelId) + " 初始化失败：" + message);
                        Toast.makeText(MainActivity.this, "默认 OCR 模型初始化失败：" + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, modelId);
        recognizer.setFastScanMode(!longReadingMode);
        recognizer.initialize();
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

        TextView modelManagementTitle = new TextView(this);
        modelManagementTitle.setText("OCR 引擎选择");
        modelManagementTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        modelManagementTitle.setPadding(0, dp(14), 0, dp(4));
        panel.addView(modelManagementTitle);
        RadioGroup modelGroup = new RadioGroup(this);
        modelGroup.setOrientation(RadioGroup.VERTICAL);
        String[][] modelOptions = new String[][] {
            {"ppocrv6_small", "PP-OCRv6 Small（默认，优先推荐）"},
            {"ppocrv6_tiny", "PP-OCRv6 Tiny（轻量，速度优先）"},
            {"ppocrv5_mobile", "PP-OCRv5 Mobile（上一代移动端模型）"}
        };
        for (String[] option : modelOptions) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setTag(option[0]);
            radio.setText(option[1]);
            radio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            radio.setChecked(ocrModelId.equals(option[0]));
            modelGroup.addView(radio);
        }
        panel.addView(modelGroup);
        TextView modelManagementInfo = new TextView(this);
        modelManagementInfo.setText("三套模型均使用 PaddleOCR 官方 Android SDK 支持的 ONNX 模型格式，随安装包一起提供，无需在手机上另行下载。切换后会重新初始化 OCR 引擎；若新模型初始化失败，会显示明确错误。");
        modelManagementInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        modelManagementInfo.setTextColor(0xFF667085);
        panel.addView(modelManagementInfo);

        CheckBox qualityCheck = new CheckBox(this);
        qualityCheck.setText("识别失败或画面模糊时给出简短语音提示");
        qualityCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        qualityCheck.setChecked(qualityPromptsEnabled);
        panel.addView(qualityCheck);

        CheckBox queueCheck = new CheckBox(this);
        queueCheck.setText("默认使用长文阅读（读完再识别）；快速扫读会自动打断旧内容");
        queueCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        queueCheck.setChecked(longReadingMode);
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

        TextView rotationNote = new TextView(this);
        rotationNote.setText("手机摄像头默认16:9等比显示。旋转、镜像、翻转仅改变预览，不会拉伸文字；USB摄像头的OCR仍使用原始0°画面。");
        rotationNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        panel.addView(rotationNote);


        CheckBox legacyDecodeCheck = new CheckBox(this);
        legacyDecodeCheck.setText("使用 0.7.0 的 NV21→JPEG 图像解码方式（对照测试）");
        legacyDecodeCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        legacyDecodeCheck.setChecked(legacyNv21Jpeg);
        panel.addView(legacyDecodeCheck);
        TextView legacyNote = new TextView(this);
        legacyNote.setText("只改变图像转换过程，不改变摄像头分辨率、模型、语音或预览。可与目前的 OpenCV 解码方式对比；JPEG 方式可能更慢。");
        legacyNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        panel.addView(legacyNote);

        CheckBox mirrorCheck = new CheckBox(this);
        mirrorCheck.setText("水平镜像");
        mirrorCheck.setChecked(previewMirror);
        panel.addView(mirrorCheck);
        CheckBox flipCheck = new CheckBox(this);
        flipCheck.setText("垂直翻转");
        flipCheck.setChecked(previewVerticalFlip);
        panel.addView(flipCheck);

        TextView transformNote = new TextView(this);
        PreviewSize currentSize = previewCallbackAdded ? getCurrentPreviewSize() : null;
        String actualSizeText = currentSize == null
            ? "当前未连接摄像头"
            : currentSize.getWidth() + "×" + currentSize.getHeight();
        TextView previewQualityNote = new TextView(this);
        previewQualityNote.setText("当前实际预览分辨率：" + actualSizeText
            + "。固定请求 1920×1080；若摄像头不支持，会选择 1080p 以下最合适的模式；如果没有符合上限的模式则明确报错，不会偷偷切换到 4K。");
        previewQualityNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        previewQualityNote.setTextColor(0xFF667085);
        panel.addView(previewQualityNote);

        transformNote.setText("旋转、镜像和翻转会同时作用于当前选择的预览画面；不改变USB摄像头原始OCR输入，也不改变手机摄像头为识别而进行的传感器方向校正。");
        transformNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        transformNote.setTextColor(0xFF667085);
        panel.addView(transformNote);

        TextView speechFilterTitle = new TextView(this);
        speechFilterTitle.setText("朗读内容筛选（只影响语音，不改变 OCR 识别结果）");
        speechFilterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        speechFilterTitle.setPadding(0, dp(18), 0, dp(4));
        panel.addView(speechFilterTitle);

        CheckBox noEnglishCheck = new CheckBox(this);
        noEnglishCheck.setText("不读英文（移除英文字母及英文单词）");
        noEnglishCheck.setChecked(speechNoEnglish);
        panel.addView(noEnglishCheck);
        CheckBox noNumbersCheck = new CheckBox(this);
        noNumbersCheck.setText("不读数字（移除阿拉伯数字）");
        noNumbersCheck.setChecked(speechNoNumbers);
        panel.addView(noNumbersCheck);

        TextView maxCharsTitle = new TextView(this);
        maxCharsTitle.setText("单次最多朗读字符数（建议设为 0，全文不截断）");
        maxCharsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        maxCharsTitle.setPadding(0, dp(8), 0, dp(3));
        panel.addView(maxCharsTitle);
        EditText maxCharsInput = new EditText(this);
        maxCharsInput.setSingleLine(true);
        maxCharsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        maxCharsInput.setText(String.valueOf(maxSpeechChars));
        maxCharsInput.setHint("0 = 不限，最大 5000");
        panel.addView(maxCharsInput);

        TextView diagnosticTitle = new TextView(this);
        diagnosticTitle.setText("OCR 诊断");
        diagnosticTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        diagnosticTitle.setPadding(0, dp(18), 0, dp(4));
        panel.addView(diagnosticTitle);
        TextView diagnosticNote = new TextView(this);
        diagnosticNote.setText("点击后抓取摄像头下一帧并执行一次 OCR。诊断不受自动识别间隔和连续识别条件影响；完成后可让中文语音实际朗读这次识别结果。请先连接外接摄像头。");
        diagnosticNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        diagnosticNote.setTextColor(0xFF667085);
        panel.addView(diagnosticNote);
        diagnosticReportView = new TextView(this);
        diagnosticReportView.setText("尚未运行诊断");
        diagnosticReportView.setTextIsSelectable(true);
        diagnosticReportView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        diagnosticReportView.setPadding(dp(8), dp(8), dp(8), dp(8));
        diagnosticReportView.setBackgroundColor(0xFFF1F3F5);
        panel.addView(diagnosticReportView);
        Button runDiagnosticButton = createControlButton("开始 OCR 诊断");
        runDiagnosticButton.setOnClickListener(v -> {
            if (!(usingPhoneCamera || previewCallbackAdded)) {
                diagnosticReportView.setText("诊断失败：尚未获取摄像头画面。请检查 USB 连接或手机摄像头权限。");
                return;
            }
            if (recognizer == null || !recognizer.isReady()) {
                diagnosticReportView.setText("正在等待 OCR 引擎初始化，或引擎初始化失败。请稍后重试；如果持续如此，请检查应用日志。");
                return;
            }
            diagnosticStartedAt = System.currentTimeMillis();
            diagnosticReportView.setText("正在等待摄像头下一帧并执行 OCR……");
            diagnosticCaptureRequested.set(true);
        });
        panel.addView(runDiagnosticButton);
        Button speakDiagnosticButton = createControlButton("朗读这次诊断识别出的文字");
        speakDiagnosticButton.setOnClickListener(v -> {
            String textToRead = lastDiagnosticText == null ? "" : lastDiagnosticText.trim();
            if (textToRead.isEmpty()) Toast.makeText(this, "这次诊断没有得到可朗读的文字", Toast.LENGTH_SHORT).show();
            else if (tts == null || !ttsReady) Toast.makeText(this, "中文语音尚未准备好", Toast.LENGTH_SHORT).show();
            else {
                queueSpeechText(textToRead, normalizeOcrText(textToRead), true);
            }
        });
        panel.addView(speakDiagnosticButton);

        TextView similarityNote = new TextView(this);
        similarityNote.setText("自动模式需连续两帧相近才朗读，手动抓取立即朗读一次；0 关闭相似度去重。长文本自动分段，不会因系统单次朗读限制被截断。若设置了最大朗读字数或不读英文/数字，仍会按这些设置筛选。");
        similarityNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        similarityNote.setTextColor(0xFF667085);
        panel.addView(similarityNote);

        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.addView(panel);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("识别与朗读设置")
            .setView(settingsScroll)
            .setNegativeButton("取消", (dialog, which) -> {
                if (tts != null && ttsReady) tts.setSpeechRate(speechRate);
            })
            .setPositiveButton("保存", (dialog, which) -> {
                long interval = 500L;
                try {
                    interval = Long.parseLong(intervalInput.getText().toString().trim());
                } catch (NumberFormatException ignored) {}
                recognitionIntervalMs = Math.max(1L, Math.min(10000L, interval));
                RadioButton selectedModel = modelGroup.findViewById(modelGroup.getCheckedRadioButtonId());
                String selectedModelId = selectedModel != null && selectedModel.getTag() != null ? selectedModel.getTag().toString() : ocrModelId;
                boolean modelChanged = !selectedModelId.equals(ocrModelId);
                ocrModelId = selectedModelId;
                similarityThreshold = similaritySeek.getProgress();
                boolean wasQueueEnabled = queueSpeech;
                queueSpeech = queueCheck.isChecked();
                boolean changeReadingMode = longReadingMode != queueSpeech;
                speechRate = 0.5f + speechRateSeek.getProgress() / 100f;
                qualityPromptsEnabled = qualityCheck.isChecked();
                speechNoEnglish = noEnglishCheck.isChecked();
                speechNoNumbers = noNumbersCheck.isChecked();
                try { maxSpeechChars = Math.max(0, Math.min(5000, Integer.parseInt(maxCharsInput.getText().toString().trim()))); }
                catch (NumberFormatException ignored) { maxSpeechChars = 0; }
                previewRotation = rotationSeek.getProgress() * 90;
                legacyNv21Jpeg = legacyDecodeCheck.isChecked();
                previewMirror = mirrorCheck.isChecked();
                previewVerticalFlip = flipCheck.isChecked();
                enhancedOcr = enhancedCheck.isChecked();
                if (tts != null) tts.setSpeechRate(speechRate);
                applyPreviewTransform();
                applyPhonePreviewTransform();
                if (ocrOverlay != null) ocrOverlay.clear();
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_AUTO_MODE, automaticMode)
                    .putLong(PREF_INTERVAL, recognitionIntervalMs)
                    .putInt(PREF_SIMILARITY, similarityThreshold)
                    .putBoolean(PREF_QUEUE_SPEECH, queueSpeech)
                    .putBoolean(PREF_LONG_READING, queueSpeech)
                    .putBoolean(PREF_QUALITY_PROMPTS, qualityPromptsEnabled)
                    .putFloat(PREF_SPEECH_RATE, speechRate)
                    .putInt(PREF_PREVIEW_ROTATION, previewRotation)
                    .putBoolean(PREF_LEGACY_NV21, legacyNv21Jpeg)
                    .putBoolean(PREF_PREVIEW_MIRROR, previewMirror)
                    .putBoolean(PREF_PREVIEW_FLIP, previewVerticalFlip)
                    .putBoolean(PREF_ENHANCED_OCR, enhancedOcr)
                    .putString(PREF_OCR_MODEL, ocrModelId)
                    .putBoolean(PREF_NO_ENGLISH, speechNoEnglish)
                    .putBoolean(PREF_NO_NUMBERS, speechNoNumbers)
                    .putInt(PREF_MAX_SPEECH_CHARS, maxSpeechChars)
                    .apply();
                if (changeReadingMode) setLongReadingMode(queueSpeech);
                else if (wasQueueEnabled && !queueSpeech) clearSpeechQueue(true);
                if (modelChanged) switchOcrModel(selectedModelId);
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    @Override protected IAspectRatio getCameraView() {
        cameraTextureView = new AspectRatioTextureView(this);
        cameraTextureView.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> applyPreviewTransform());
        applyPreviewTransform();
        return (IAspectRatio) cameraTextureView;
    }

    private void applyPreviewTransform() {
        if (cameraTextureView == null) return;
        int containerWidth = previewContainer == null ? 0 : previewContainer.getWidth();
        int containerHeight = previewContainer == null ? 0 : previewContainer.getHeight();
        if (containerWidth <= 0 || containerHeight <= 0) return;
        boolean quarterTurn = previewRotation == 90 || previewRotation == 270;

        // Keep the entire camera frame visible. Use the actual negotiated camera ratio,
        // so even a fallback mode is letterboxed rather than stretched or cropped.
        PreviewSize actualSize = previewCallbackAdded ? getCurrentPreviewSize() : null;
        float sourceAspect = actualSize != null && actualSize.getHeight() > 0
            ? actualSize.getWidth() / (float) actualSize.getHeight()
            : (16.0f / 9.0f);
        float displayAspect = quarterTurn ? (1.0f / sourceAspect) : sourceAspect;
        float displayWidth = Math.min(containerWidth, containerHeight * displayAspect);
        float displayHeight = displayWidth / displayAspect;
        int wantedWidth = Math.max(1, Math.round(quarterTurn ? displayHeight : displayWidth));
        int wantedHeight = Math.max(1, Math.round(quarterTurn ? displayWidth : displayHeight));

        ViewGroup.LayoutParams raw = cameraTextureView.getLayoutParams();
        if (raw instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
            if (lp.width != wantedWidth || lp.height != wantedHeight || lp.gravity != Gravity.CENTER) {
                lp.width = wantedWidth;
                lp.height = wantedHeight;
                lp.gravity = Gravity.CENTER;
                cameraTextureView.setLayoutParams(lp);
            }
        }

        cameraTextureView.setRotation(previewRotation);
        int viewWidth = cameraTextureView.getWidth();
        int viewHeight = cameraTextureView.getHeight();

        cameraTextureView.setPivotX(viewWidth / 2.0f);
        cameraTextureView.setPivotY(viewHeight / 2.0f);
        // No zoom/fill scaling: this is a strict full-frame FIT transform.
        cameraTextureView.setScaleX(previewMirror ? -1.0f : 1.0f);
        cameraTextureView.setScaleY(previewVerticalFlip ? -1.0f : 1.0f);
    }

    @Override protected ViewGroup getCameraViewContainer() { return previewContainer; }
    @Override protected int getGravity() { return Gravity.CENTER; }

    @Override protected CameraRequest getCameraRequest() {
        return new CameraRequest.Builder()
            .setPreviewWidth(1920)
            .setPreviewHeight(1080)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setRawPreviewData(true)
            .setAspectRatioShow(true)
            .create();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Upstream CameraActivity.initView() has now completed its removeAllViews()
        // and added the USB preview. The phone view must be attached afterward.
        attachPhonePreviewAfterUsbInit();
        loadSettings();
        applyPreviewTransform();
        updateControlLabels();
        diagnosticAttempts = 0;
        diagnosticHandler.postDelayed(diagnosticRunnable, 1200);
        recognizer = new PaddleOcrEngine(getApplicationContext(), new PaddleOcrEngine.Listener() {
            @Override public void onReady() {
                runOnUiThread(() -> status.setText(ocrModelDisplayName(ocrModelId) + " 已加载，等待 USB 摄像头…"));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (!"ppocrv6_small".equals(ocrModelId)) {
                        Toast.makeText(MainActivity.this, ocrModelDisplayName(ocrModelId) + " 启动失败，自动恢复 PP-OCRv6 Small。", Toast.LENGTH_LONG).show();
                        ocrModelId = "ppocrv6_small";
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putString(PREF_OCR_MODEL, ocrModelId).apply();
                        switchOcrModel("ppocrv6_small");
                    } else {
                        status.setText("默认 PP-OCRv6 Small 初始化失败：" + message);
                    }
                });
            }
        }, ocrModelId);
        recognizer.setFastScanMode(!longReadingMode);
        recognizer.initialize();
        phoneController = new InternalCameraController(this, phoneTexture, new InternalCameraController.Listener() {
            @Override public boolean shouldReceiveFrame() {
                if (!usingPhoneCamera || !running) return diagnosticCaptureRequested.get();
                if (manualCaptureRequested.get() || diagnosticCaptureRequested.get()) return true;
                if (!automaticMode || busy.get() || (longReadingMode && speechPageActive)) return false;
                return System.currentTimeMillis() - lastProcess >= recognitionIntervalMs;
            }
            @Override public void onFrame(byte[] nv21, int width, int height) {
                if (usingPhoneCamera) processCameraFrame(nv21, width, height, true);
            }
            @Override public void onOpened(int rawWidth, int rawHeight, int rotation) {
                phoneRawWidth = rawWidth;
                phoneRawHeight = rawHeight;
                phoneOcrRotation = rotation;
                runOnUiThread(() -> {
                    applyPhonePreviewTransform();
                    updateControlLabels();
                });
            }
            @Override public void onPreviewStarted() {
                runOnUiThread(() -> {
                    if (!usingPhoneCamera) return;
                    phonePreviewReceived = true;
                    if (phonePreviewStatus != null) phonePreviewStatus.setVisibility(View.GONE);
                });
            }
            @Override public void onError(String message) {
                Log.w("KanziPhoneCamera", message);
                runOnUiThread(() -> {
                    if (!usingPhoneCamera) return;
                    showPhonePreviewStatus("手机摄像头启动失败：" + message +
                        "\n请尝试切换前后摄像头。", true);
                });
            }
        });
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
            } else {
                ttsReady = false;
                status.setText("系统中文语音引擎初始化失败，状态码：" + code);
            }
        });

        cameraSourceButton.setOnClickListener(v -> switchCameraSource(!usingPhoneCamera));
        lensButton.setOnClickListener(v -> {
            phoneFrontFacing = !phoneFrontFacing;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_FRONT_CAMERA, phoneFrontFacing).apply();
            switchCameraSource(true);
        });
        readingModeButton.setOnClickListener(v -> setLongReadingMode(!longReadingMode));

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
            if (!(usingPhoneCamera || previewCallbackAdded)) {
                Toast.makeText(this, "请先连接并打开摄像头", Toast.LENGTH_SHORT).show();
                return;
            }
            running = true;
            manualCaptureRequested.set(true);
            readingEpoch.incrementAndGet();
            // Explicit capture replaces old speech even during a long reading session.
            clearSpeechQueue(true);
            frameChangeGate.reset();
            updateControlLabels();
            Toast.makeText(this, "正在抓取当前画面识别", Toast.LENGTH_SHORT).show();
        });

        startButton.setOnClickListener(v -> {
            if (!(usingPhoneCamera || previewCallbackAdded)) {
                Toast.makeText(this, "请先连接并打开摄像头", Toast.LENGTH_SHORT).show();
                return;
            }
            running = !running;
            if (!running) {
                manualCaptureRequested.set(false);
                clearSpeechQueue(true);
                readingEpoch.incrementAndGet();
            } else {
                frameChangeGate.reset();
                lastSpoken = ""; // Explicit resume must be able to re-read the current paper.
                candidateCount = 0;
                candidateNormalized = "";
                candidateText = "";
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
        if (usingPhoneCamera && phoneController != null) {
            // Restore the saved camera choice to the actual view state too.
            startPhoneCamera();
        }
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
        PreviewSize actualPreviewSize = cameraOpened ? getCurrentPreviewSize() : null;
        info.append("\n实际摄像头预览分辨率：").append(actualPreviewSize == null ? "尚未获取" :
            actualPreviewSize.getWidth() + "×" + actualPreviewSize.getHeight());
        info.append("\n预览显示：完整画面 FIT（无裁切、无缩放）");
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

    /**
     * Switch modes immediately, without reinitializing the OCR models or camera.
     * The epoch invalidates results from the previous mode that finish asynchronously.
     */
    private void switchCameraSource(boolean phone) {
        readingEpoch.incrementAndGet();
        frameChangeGate.reset();
        clearSpeechQueue(true);
        candidateText = "";
        candidateNormalized = "";
        candidateCount = 0;
        lastSpoken = "";
        noTextSince = 0L;
        if (ocrOverlay != null) ocrOverlay.clear();
        usingPhoneCamera = phone;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(PREF_PHONE_CAMERA, phone).apply();
        phoneStartSequence++;
        if (phoneController != null) phoneController.stop();
        if (phoneTexture != null) phoneTexture.setVisibility(phone ? View.VISIBLE : View.GONE);
        if (cameraTextureView != null)
            cameraTextureView.setVisibility(phone ? View.INVISIBLE : View.VISIBLE);
        if (phone) {
            startPhoneCamera();
        } else {
            phonePreviewReceived = false;
            if (phonePreviewStatus != null) phonePreviewStatus.setVisibility(View.GONE);
            applyPreviewTransform();
        }
        updateControlLabels();
        String message = phone ? (phoneFrontFacing ? "已切换手机前置摄像头" : "已切换手机后置摄像头")
                               : "已切换USB外接摄像头";
        if (cameraSourceButton != null) cameraSourceButton.announceForAccessibility(message);
    }

    /**
     * Called only after CameraActivity.initView() has finished replacing the USB
     * preview container's children. This is the root fix for the perpetual
     * "正在打开手机前置摄像头" black screen in 0.9.0/0.9.1.
     */
    private void attachPhonePreviewAfterUsbInit() {
        if (previewContainer == null || phoneTexture == null) return;
        if (phoneTexture.getParent() instanceof ViewGroup) {
            ((ViewGroup) phoneTexture.getParent()).removeView(phoneTexture);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        previewContainer.addView(phoneTexture, params);
        phoneTexture.setVisibility(View.GONE);
        Log.i("KanziPhoneCamera", "Phone TextureView attached AFTER USB init; parent=" +
            phoneTexture.getParent().getClass().getSimpleName() +
            ", childCount=" + previewContainer.getChildCount());
    }

    private void showPhonePreviewStatus(String message, boolean speakError) {
        if (!usingPhoneCamera || phonePreviewStatus == null) return;
        phonePreviewStatus.setText(message);
        phonePreviewStatus.setVisibility(View.VISIBLE);
        if (speakError) {
            phonePreviewStatus.announceForAccessibility(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void startPhoneCamera() {
        if (!usingPhoneCamera || phoneController == null || phoneTexture == null) return;
        if (!phoneController.hasPermission()) {
            showPhonePreviewStatus("请允许看字助手使用手机摄像头", false);
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_PHONE_CAMERA);
            return;
        }
        // Visibility must be established BEFORE Camera2 waits for SurfaceTexture.
        phoneTexture.setVisibility(View.VISIBLE);
        phoneTexture.bringToFront();
        if (cameraTextureView != null) cameraTextureView.setVisibility(View.INVISIBLE);
        phonePreviewReceived = false;
        showPhonePreviewStatus(phoneFrontFacing
            ? "正在打开手机前置摄像头…" : "正在打开手机后置摄像头…", false);
        final int sequence = ++phoneStartSequence;
        // The watchdog must be independent of the TextureView.post() callback.
        // If its view is detached, posting to that view can never execute.
        mainHandler.postDelayed(() -> {
            if (sequence != phoneStartSequence || !usingPhoneCamera || phonePreviewReceived) return;
            String detail = phoneTexture.getParent() == null ? "预览控件未挂载"
                : !phoneTexture.isAttachedToWindow() ? "预览控件尚未连接到窗口"
                : !phoneTexture.isAvailable() ? "预览SurfaceTexture未就绪"
                : "摄像头没有产生预览画面";
            showPhonePreviewStatus("手机摄像头启动超时：" + detail +
                "。请切换前后摄像头重试。", true);
        }, 5500L);

        // A normal attached, visible TextureView is the only valid Camera2 target.
        phoneTexture.post(() -> {
            if (!usingPhoneCamera || phoneController == null || sequence != phoneStartSequence) return;
            if (phoneTexture.getParent() != previewContainer) {
                attachPhonePreviewAfterUsbInit();
                phoneTexture.setVisibility(View.VISIBLE);
            }
            if (!phoneTexture.isAttachedToWindow()) {
                Log.w("KanziPhoneCamera", "Phone TextureView unattached; waiting for attach");
                phoneTexture.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View v) {
                        v.removeOnAttachStateChangeListener(this);
                        if (sequence == phoneStartSequence && usingPhoneCamera)
                            phoneController.start(phoneFrontFacing);
                    }
                    @Override public void onViewDetachedFromWindow(View v) {}
                });
                return;
            }
            phoneController.start(phoneFrontFacing);
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PHONE_CAMERA) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startPhoneCamera();
        else Toast.makeText(this, "请允许相机权限后使用手机摄像头", Toast.LENGTH_LONG).show();
    }

    /**
     * Full-screen camera SURFACE with a content-only FIT_CENTER matrix.
     * Unlike rotating/resizing the TextureView itself, this cannot squeeze
     * a 16:9 camera image into the phone screen's taller proportions.
     * Only the preview transforms; OCR's source pixel orientation is untouched.
     */
    private void applyPhonePreviewTransform() {
        if (phoneTexture == null || !usingPhoneCamera) return;
        final int viewW = phoneTexture.getWidth();
        final int viewH = phoneTexture.getHeight();
        if (viewW <= 0 || viewH <= 0 || phoneRawWidth <= 0 || phoneRawHeight <= 0) return;
        int angle = PhonePreviewGeometry.rotation(phoneOcrRotation, previewRotation);
        PhonePreviewGeometry.Layout fit = PhonePreviewGeometry.fit(
            viewW, viewH, phoneRawWidth, phoneRawHeight, angle,
            previewMirror, previewVerticalFlip);
        Matrix transform = new Matrix();
        transform.setValues(fit.textureMatrix);
        // Keep the outer TextureView fixed to full-screen; only its image moves.
        phoneTexture.setPivotX(viewW / 2f);
        phoneTexture.setPivotY(viewH / 2f);
        phoneTexture.setRotation(0f);
        phoneTexture.setScaleX(1f);
        phoneTexture.setScaleY(1f);
        phoneTexture.setTransform(transform);
        Log.d("KanziPhoneCamera", "FIT 16:9 preview frame=" + phoneRawWidth + "x" +
            phoneRawHeight + " view=" + viewW + "x" + viewH +
            " angle=" + angle + " fitted=" + fit.fittedWidth + "x" +
            fit.fittedHeight);
        if (ocrOverlay != null) {
            ocrOverlay.setPhoneDisplayTransform(previewRotation,
                previewMirror, previewVerticalFlip);
        }
    }

    /** Speak a warning only after the scene remains unclear; never interrupt a page. */
    private void onReadingQuality(OcrReadResult result, FrameQuality.Assessment quality) {
        if (!qualityPromptsEnabled || !running) return;
        if (!result.getText().trim().isEmpty()) {
            noTextSince = 0L;
            if (result.getAverageConfidence() < 0.50 && quality.possiblyBlurry)
                tryVoiceHint("文字可能模糊，请保持摄像头稳定");
            return;
        }
        final long now = System.currentTimeMillis();
        if (noTextSince == 0L) noTextSince = now;
        mainHandler.postDelayed(() -> {
            if (!running || !qualityPromptsEnabled || noTextSince == 0L ||
                System.currentTimeMillis() - noTextSince < 1300L) return;
            String warning = quality.dark ? "光线不足，请增加照明" : "未拾取到文字";
            tryVoiceHint(warning);
        }, 1350L);
    }

    private void tryVoiceHint(String message) {
        long now = System.currentTimeMillis();
        if (!ttsReady || tts == null || speechPageActive || speakingQueued ||
            currentSpeech != null || !speechQueue.isEmpty() ||
            now - lastQualityPromptAt < 6000L) return;
        lastQualityPromptAt = now;
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "quality-" + (++utteranceSequence));
    }

    private void setLongReadingMode(boolean enableLong) {
        if (longReadingMode == enableLong) return;
        readingEpoch.incrementAndGet();
        longReadingMode = enableLong;
        queueSpeech = enableLong; // keep legacy preference consistent
        if (recognizer != null) recognizer.setFastScanMode(!enableLong);
        manualCaptureRequested.set(false);
        frameChangeGate.reset();
        candidateText = "";
        candidateNormalized = "";
        candidateCount = 0;
        lastSpoken = "";
        clearSpeechQueue(true);
        noTextSince = 0L;
        if (ocrOverlay != null) ocrOverlay.clear();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(PREF_LONG_READING, enableLong)
            .putBoolean(PREF_QUEUE_SPEECH, enableLong)
            .apply();
        updateControlLabels();
        String announcement = enableLong ? "已切换长文阅读，读完再识别" : "已切换快速扫读，新内容会打断旧内容";
        Toast.makeText(this, announcement, Toast.LENGTH_SHORT).show();
        if (readingModeButton != null) readingModeButton.announceForAccessibility(announcement);
    }

    /**
     * Full reading confirms two matching frames; rapid scanning acts on one
     * current frame. An explicit manual request reads its own complete capture.
     */
    private void onRecognized(String found, boolean manualCapture) {
        final long now = System.currentTimeMillis();
        final String recognizedText = found == null ? "" : found.trim();
        if (recognizedText.isEmpty()) {
            candidateText = "";
            candidateNormalized = "";
            candidateCount = 0;
            if (lastNonEmptyAt > 0 && now - lastNonEmptyAt >= 2800) lastSpoken = "";
            return;
        }
        lastNonEmptyAt = now;
        if (!running || !ttsReady) return;
        String confirmed;
        if (manualCapture || !longReadingMode) {
            candidateText = "";
            candidateNormalized = "";
            candidateCount = 0;
            confirmed = recognizedText;
        } else {
            final String normalized = normalizeOcrText(recognizedText);
            if (normalized.isEmpty()) return;
            final float threshold = similarityThreshold / 100.0f;
            if (!candidateNormalized.isEmpty() &&
                    (threshold <= 0f || textSimilarity(candidateNormalized, normalized) >= threshold)) {
                candidateCount++;
                // Use the more complete frame; the old code silently spoke the LAST frame.
                if (recognizedText.length() > candidateText.length()) candidateText = recognizedText;
            } else {
                candidateText = recognizedText;
                candidateCount = 1;
            }
            candidateNormalized = normalized;
            if (candidateCount < 2) return;
            confirmed = candidateText;
            candidateCount = 0;
        }

        String speechText = confirmed;
        if (speechNoEnglish) speechText = speechText.replaceAll("[A-Za-z]+", " ");
        if (speechNoNumbers) speechText = speechText.replaceAll("[0-9]+", " ");
        // Keep line/paragraph boundaries for natural reading instead of flattening to spaces.
        speechText = speechText.replaceAll("[\\t ]+", " ").replaceAll(" *\\n *", "\n").trim();
        if (maxSpeechChars > 0 && speechText.length() > maxSpeechChars)
            speechText = speechText.substring(0, maxSpeechChars).trim();
        if (speechText.isEmpty()) return;
        final String fullSpeech = speechText;
        final String normalizedSpeech = normalizeOcrText(fullSpeech);
        if (normalizedSpeech.isEmpty()) return;
        runOnUiThread(() -> {
            if (!running || tts == null || !ttsReady) return;
            queueSpeechText(fullSpeech, normalizedSpeech, manualCapture);
        });
    }

    /** Read one complete frame, splitting into bounded utterances but never dropping its tail. */
    private void queueSpeechText(String text, String normalized, boolean force) {
        if (tts == null || !ttsReady || normalized.isEmpty()) return;
        // Compare the ACTUAL text being spoken, not unspoken tail content.
        String spokenText = (!force && !longReadingMode) ? SpeechText.scanExcerpt(text) : text;
        String spokenNormalized = normalizeOcrText(spokenText);
        if (spokenNormalized.isEmpty()) return;
        if (!force) {
            if (longReadingMode && (speechPageActive || currentSpeech != null || !speechQueue.isEmpty())) return;
            if (isAlreadyQueuedOrRecentlySpoken(spokenNormalized, similarityThreshold / 100.0f)) return;
        }
        if (force || !longReadingMode) {
            // Latest-content-wins in scan mode. A manual request always takes priority.
            clearSpeechQueue(true);
        }
        List<String> chunks = SpeechText.chunks(spokenText);
        if (chunks.isEmpty()) return;
        speechPageActive = true;
        for (String chunk : chunks) {
            speechQueue.addLast(new SpeechItem(chunk, spokenNormalized, "ocr-" + (++utteranceSequence)));
        }
        speakNextQueued();
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
        if (!ttsReady || tts == null || speakingQueued || speechQueue.isEmpty()) return;
        currentSpeech = speechQueue.pollFirst();
        speakingQueued = true;
        lastSpoken = currentSpeech.normalized;
        int result = tts.speak(currentSpeech.text, TextToSpeech.QUEUE_ADD, null,
            currentSpeech.utteranceId);
        if (result == TextToSpeech.ERROR) finishQueuedUtterance(currentSpeech.utteranceId);
    }

    private void finishQueuedUtterance(String utteranceId) {
        if (currentSpeech == null || !currentSpeech.utteranceId.equals(utteranceId)) return;
        currentSpeech = null;
        speakingQueued = false;
        if (speechQueue.isEmpty()) speechPageActive = false;
        speakNextQueued();
    }

    private void clearSpeechQueue(boolean stopCurrent) {
        speechPageActive = false;
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

    @Override protected void onPause() {
        phoneStartSequence++;
        if (phoneController != null) phoneController.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        phoneStartSequence++;
        if (phoneController != null) phoneController.stop();
        running = false;
        manualCaptureRequested.set(false);
        speechQueue.clear();
        currentSpeech = null;
        speechPageActive = false;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (recognizer != null) recognizer.release();
        diagnosticHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
