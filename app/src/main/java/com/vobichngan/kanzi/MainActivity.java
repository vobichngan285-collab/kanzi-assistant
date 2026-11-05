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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
    private static final String APP_VERSION = "0.9.23-CNLite-Immediate";
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
    private static final String PREF_PREVIEW_MIRROR = "preview_mirror";
    private static final String PREF_USB_PREVIEW_CORRECT_MIRROR = "usb_preview_correct_left_right";
    private static final String PREF_PREVIEW_FLIP = "preview_flip";
    private static final String PREF_ENHANCED_OCR = "enhanced_ocr";
    private static final String PREF_OCR_MODEL = "ocr_model_id";
    private static final String PREF_OCR_PROFILE = "x200_ocr_profile";
    private static final String PREF_PHONE_CAMERA = "phone_camera_source";
    private static final String PREF_FRONT_CAMERA = "phone_camera_front";
    private static final String PREF_QUALITY_PROMPTS = "quality_voice_prompts";
    private static final String PREF_READING_FOCUS = "reading_page_focus";
    private static final String PREF_AI_LAYOUT = "experimental_ai_layout";
    private static final String PREF_VERTICAL_LIVE_READING = "experimental_true_vertical_live";
    private static final String PREF_REALTIME_TEXT_ROTATION = "realtime_text_rotation";
    private static final String PREF_USB_OCR_INPUT_ROTATION = "usb_ocr_input_rotation";
    private static final int REQUEST_PHONE_CAMERA = 4051;
    private static final int REQUEST_SINGLE_FRAME_ZIP = 4916;
    private final AtomicBoolean saveEvidenceNextDiagnostic = new AtomicBoolean(false);
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
    private volatile int phoneSensorNaturalRotation = 90;
    private volatile int phoneRawWidth = 1280, phoneRawHeight = 720;
    private volatile boolean qualityPromptsEnabled = true;
    private long noTextSince = 0L, lastQualityPromptAt = 0L;
    private TextToSpeech tts;
    private DownloadableVoiceStore voiceStore;
    private OfflineVoicePlayback offlineVoicePlayback;
    private PaddleOcrEngine recognizer;
    // Make engine errors visible in the diagnostics instead of only showing
    // the confusing generic "waiting for OCR initialization" string.
    private volatile String lastOcrEngineError = "";
    private volatile boolean running = false;
    private volatile boolean automaticMode = true;
    private volatile long recognitionIntervalMs = 500L;
    private volatile int similarityThreshold = 88;
    private volatile boolean queueSpeech = false;
    private volatile boolean longReadingMode = false;
    private volatile boolean speechPageActive = false;
    private final FrameChangeGate frameChangeGate = new FrameChangeGate();
    // Fast-scan frame identities are tracked even while a previous OCR is busy.
    // A noisy frame identity can never cancel a successfully recognized sentence.
    private final RealtimeSceneTracker realtimeScene = new RealtimeSceneTracker();
    private volatile long lastLiveObservationAt = 0L;
    private volatile long lastQuickScanGeneration = -1L;
    private volatile long lastQuickScanAt = 0L;
    private volatile boolean lastQuickScanReliable = false;
    private final RealtimeSpeechGuard liveSpeechGuard = new RealtimeSpeechGuard();
    private final java.util.concurrent.atomic.AtomicInteger readingEpoch = new java.util.concurrent.atomic.AtomicInteger(0);
    private volatile float speechRate = 1.0f;
    private volatile int previewRotation = 0;
    private volatile boolean previewMirror = false;
    // USB-only display correction; camera NV21 OCR input is NEVER mirrored.
    private volatile boolean usbPreviewCorrectMirror = true;
    private volatile boolean previewVerticalFlip = false;
    private volatile boolean enhancedOcr = false;
    private volatile String ocrModelId = "cnlite_smallrec";
    private volatile String ocrProfileId = OcrPerformanceProfile.defaultId();
    private volatile String readingFocusMode = "AUTO";
    private volatile boolean aiLayoutEnabled = false;
    private volatile boolean verticalLiveReadingEnabled = true;
    private volatile int liveTextRotationDegrees = 0;
    // Independent USB capture->OCR input direction, never touches TextureView preview.
    // -1 = auto camera-frame OCR orientation (default); manual values are diagnostic override.
    private volatile int usbOcrInputRotationDegrees = -1;
    private volatile String lastDiagnosticText = "";
    private volatile String lastCandidateDiagnosticText = "";
    private volatile boolean speechNoEnglish = false;
    private volatile boolean speechNoNumbers = false;
    private volatile int maxSpeechChars = 0;
    private final AtomicBoolean diagnosticCaptureRequested = new AtomicBoolean(false);
    private volatile TextView diagnosticReportView;
    private volatile long diagnosticStartedAt = 0;
    private volatile boolean ttsReady = false;
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
    private long lastSpeechFailureNoticeAt = 0L;

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
                // The obsolete 0.7.0 NV21->JPEG comparison is retired.
                // Keep one consistent low-overhead OpenCV NV21 decoder.
                final boolean legacyForDiagnostic = false;
                final boolean evidenceThisCapture = saveEvidenceNextDiagnostic.getAndSet(false);
                if (evidenceThisCapture) recognizer.requestEvidenceForNextDiagnostic();
                final int effectiveDiagnosticAngle = isPhoneCamera ? 0 : usbOcrInputRotationDegrees;
                recognizer.diagnoseNv21(diagnosticFrame, width, height,
                    enhancedOcr, legacyForDiagnostic, effectiveDiagnosticAngle,
                    report -> {
                        busy.set(false);
                        String result = "诊断完成\n摄像头帧：已获取 " + width + "×" + height
                            + "\nOCR 引擎：已就绪\n检测框数量：" + report.getBoxCount()
                            + "\n实时朗读状态：" + (running ? "已启动" : "未启动，请点开始识别") +
                                "；扫描模式：" + (automaticMode ? "自动" : "手动") +
                                "；中文语音：" + (canSpeak() ? "已就绪" : "尚未就绪") +
                            "\n提示：本页为诊断结果，诊断本身不自动朗读；可点「朗读这次诊断识别出的文字」试听。"
                            + "\n当前模型：" + report.getModelId()
                            + "\n\n" + report.getX200ProfileReport()
                            + "\n摄像头："
                                + (isPhoneCamera ? "手机" : "USB")
                                + "；原始帧=" + width + "×" + height
                            + "\n本次OCR方向请求=" + (effectiveDiagnosticAngle == -1 ? "USB自动校准（真实角度见上方X200报告）" : effectiveDiagnosticAngle + "°")
                                + "（仅OCR；预览画面不参与）"
                            + "\n原版A/B输入方向：以本次实际OCR输入角度为基准；不覆盖原始NV21"
                            + "\n手机实时OCR旋转设置：" + liveTextRotationDegrees
                                + "°；USB OCR方向设置：" + (usbOcrInputRotationDegrees == -1 ? "自动" : usbOcrInputRotationDegrees + "°")
                            + "\n图像解码：OpenCV NV21 方式"
                            + "\n平均置信度：" + String.format(Locale.US, "%.3f", report.getAverageConfidence())
                            + "\n文字流组织方式：" + report.getFocusDescription()
                            + "\n版面 AI 状态：" + report.getLayoutStatus()
                            + "\nAI 有效版面区域：" + report.getLayoutRegions()
                            + "\nAI 推理耗时：" + report.getLayoutElapsedMs() + " ms"
                            + "\n版面缓存统计：" + report.getLayoutReuseStats()
                            + "\n检测输入尺寸：" + report.getDetectorInputShape()
                            + "\n识别输入尺寸：" + report.getRecognitionInputShapes()
                            + "\n检测耗时：" + report.getDetectionTimeMs() + " ms"
                            + "\n识别耗时：" + report.getRecognitionTimeMs() + " ms"
                            + "\n总耗时：" + report.getTotalTimeMs() + " ms"
                            + "\n诊断总用时：" + (System.currentTimeMillis() - started) + " ms"
                            + "\n逐框原始识别（未做低置信度过滤）：\n" + report.getRawDetails()
                            + "\n\n按连续文字流整理的朗读内容：\n" +
                                (report.getText().isEmpty() ? "（未识别到文字）" : report.getText()) +
                            "\n\n实时文字排序（正式快速扫读使用的算法；仅一次 OCR）：\n" +
                                (report.getRealtimeText().isEmpty() ? "（未识别到文字）" : report.getRealtimeText()) +
                            "\n\n正式语音按OCR原文字序播报，不进行上下文猜字或自动替换。" +
                            "\n\n全部可见文字（对照）：\n" + report.getFullPageText()
                            + "\n\n实验候选阅读顺序（仅诊断，不影响当前播报）：\n"
                            + (report.getCandidateText().isEmpty() ? "（无可靠候选）" : report.getCandidateText())
                            + "\n\n候选框序号与归属追溯：\n" + report.getCandidateDetails()
                            + (evidenceThisCapture ? "\n\n单帧图像证据：" + recognizer.lastEvidenceStatus() : "");
                        lastDiagnosticText = report.getText();
                        lastCandidateDiagnosticText = report.getCandidateText();
                        runOnUiThread(() -> { if (diagnosticReportView != null) diagnosticReportView.setText(result); });
                    },
                    error -> {
                        busy.set(false);
                        lastDiagnosticText = "";
                        lastCandidateDiagnosticText = "";
                        runOnUiThread(() -> { if (diagnosticReportView != null) diagnosticReportView.setText(
                            "诊断失败\n摄像头帧：" + width + "×" + height + "\n原因：" + error); });
                    });
                return;
            }
            if (!running) return;
            // Phone Camera2 only converts the frames we request; this timestamp
            // caps cheap scene observation while allowing OCR to run in parallel.
            lastLiveObservationAt = System.currentTimeMillis();
            // Never fingerprint a frame before models are available: the old
            // gate could suppress the first readable page for several seconds.
            if (recognizer == null || !recognizer.isReady()) return;
            // Long reading owns OCR until the entire spoken page has completed.
            // Manual capture remains an explicit override below.
            long now = System.currentTimeMillis();
            final boolean live = !longReadingMode && automaticMode;
            // Observe while OCR/TTS is busy too. A page turn during a slow OCR
            // invalidates that result before it can interrupt newer speech.
            long previousScene = realtimeScene.generation();
            long scene = live ? realtimeScene.observe(data, width, height, now)
                              : previousScene;
            if (live && scene != previousScene) frameChangeGate.reset();
            boolean oneShot = manualCaptureRequested.get();
            if (oneShot) {
                if (!manualCaptureRequested.compareAndSet(true, false)) return;
            } else {
                if (!automaticMode || now - lastProcess < recognitionIntervalMs) return;
                if (longReadingMode && speechPageActive) return;
                // v56: LIVE continues sampling/OCR while TTS is speaking.
                // A newly recognized text target preempts the old utterance.
                // Long-reading mode is still an explicit finish-page option.
                // Static, reliably read text should NOT cause full OCR every
                // 2.8 seconds. A scene generation change can trigger at once.
                // The long safety refresh handles very subtle changes missed
                // by the cheap scene fingerprint without repeating speech.
                if (live && lastQuickScanReliable &&
                    scene == lastQuickScanGeneration &&
                    now - lastQuickScanAt < 12000L) return;
            }
            if (!busy.compareAndSet(false, true)) {
                if (oneShot) manualCaptureRequested.set(true);
                return;
            }
            // One successful OCR per stable page. The old mandatory second
            // full-page pass both delayed TTS and could restart indefinitely
            // when the long OCR strings differed at a few characters.
            boolean samePageLocked = longReadingMode && !lastSpoken.isEmpty();
            if (!oneShot &&
                !frameChangeGate.accept(data, width, height, now, samePageLocked)) {
                busy.set(false);
                return;
            }
            lastProcess = now;
            final int recognizedEpoch = readingEpoch.get();
            final long recognizedScene = scene;
            byte[] frame = data.clone();
            // For very dark AND low-detail frames, one enhanced OCR pass is
            // preferable to reading unreadable pixels. Never add an extra
            // inference to the fast path just because the page moves.
            final FrameQuality.Assessment quality = FrameQuality.assess(frame, width, height);
            final boolean enhance = enhancedOcr || (quality.dark && quality.possiblyBlurry);
            final boolean legacyDecode = false;
            try {
                final int effectiveOcrInputAngle = isPhoneCamera
                    ? (longReadingMode ? 0 : liveTextRotationDegrees)
                    : usbOcrInputRotationDegrees;
                recognizer.recognizeNv21(frame, width, height,
                    enhance, legacyDecode, effectiveOcrInputAngle, found -> {
                    busy.set(false);
                    // Never discard a fully recognized, readable text frame
                    // merely because autofocus, exposure or hand movement
                    // advanced the cheap luma scene counter during the OCR
                    // inference. That caused COMPLETE SILENCE on real phones:
                    // camera callbacks can update the generation much faster
                    // than the ~1-3 second recognition pass can finish.
                    //
                    // The reading epoch still rejects results from an actual
                    // pause, mode change or camera change. New completed OCR
                    // will supersede older TTS as soon as it becomes ready.
                    if (found != null && recognizedEpoch == readingEpoch.get()) {
                        if (live && !oneShot) {
                            // Use the scene token from when this OCR started.
                            // A NEW page shown during a slow recognition must
                            // be eligible for the next scan, not hidden behind
                            // the old result's 12s same-scene cooldown.
                            lastQuickScanGeneration = recognizedScene;
                            lastQuickScanAt = System.currentTimeMillis();
                            lastQuickScanReliable = !found.getText().trim().isEmpty() &&
                                found.getAverageConfidence() >= 0.55;
                        }
                        if (ocrOverlay != null) {
                            ocrOverlay.show(found.getBoxes(), found.getFrameWidth(),
                                found.getFrameHeight(),
                                isPhoneCamera ? phoneTexture : cameraTextureView,
                                isPhoneCamera, isPhoneCamera ? phoneSensorNaturalRotation : 0,
                                isPhoneCamera ? phoneOcrRotation : 0);
                        }
                        onRecognized(found.getText(), oneShot,
                            live ? realtimeScene.generation() : recognizedScene, recognizedEpoch);
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
                ? "长文阅读｜完整读完（后续课题）"
                : "实时文字｜新文字立即打断");
            readingModeButton.setContentDescription(longReadingMode
                ? "当前是长文阅读模式。点击切换到实时文字识别"
                : "当前是实时文字识别模式。点击切换到长文阅读");
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
        queueSpeech = prefs.getBoolean(PREF_QUEUE_SPEECH, false);
        usingPhoneCamera = prefs.getBoolean(PREF_PHONE_CAMERA, false);
        phoneFrontFacing = prefs.getBoolean(PREF_FRONT_CAMERA, false);
        qualityPromptsEnabled = prefs.getBoolean(PREF_QUALITY_PROMPTS, false);
        longReadingMode = prefs.getBoolean(PREF_LONG_READING, queueSpeech);
        speechRate = Math.max(0.5f, Math.min(2.0f, prefs.getFloat(PREF_SPEECH_RATE, 1.0f)));
        previewRotation = prefs.getInt(PREF_PREVIEW_ROTATION, 0);
        if (previewRotation != 0 && previewRotation != 90 && previewRotation != 180 && previewRotation != 270) previewRotation = 0;
        previewMirror = prefs.getBoolean(PREF_PREVIEW_MIRROR, false);
        usbPreviewCorrectMirror = prefs.getBoolean(PREF_USB_PREVIEW_CORRECT_MIRROR, true);
        previewVerticalFlip = prefs.getBoolean(PREF_PREVIEW_FLIP, false);
        enhancedOcr = prefs.getBoolean(PREF_ENHANCED_OCR, false);
        aiLayoutEnabled = prefs.getBoolean(PREF_AI_LAYOUT, false);
        verticalLiveReadingEnabled = prefs.getBoolean(PREF_VERTICAL_LIVE_READING, true);
        liveTextRotationDegrees = prefs.getInt(PREF_REALTIME_TEXT_ROTATION, 0);
        usbOcrInputRotationDegrees = UsbAutoOcrOrientation.normalizePreference(
            prefs.getInt(PREF_USB_OCR_INPUT_ROTATION, -1));
        if (liveTextRotationDegrees != 90 && liveTextRotationDegrees != 180 && liveTextRotationDegrees != 270)
            liveTextRotationDegrees = 0;
        readingFocusMode = prefs.getString(PREF_READING_FOCUS, "AUTO");
        // Retired page-centric ALL setting is equivalent to continuous AUTO.
        if ("ALL".equals(readingFocusMode)) readingFocusMode = "AUTO";
        if (!"AUTO".equals(readingFocusMode) &&
            !"LEFT".equals(readingFocusMode) && !"RIGHT".equals(readingFocusMode))
            readingFocusMode = "AUTO";
        ocrProfileId = OcrPerformanceProfile.forId(prefs.getString(PREF_OCR_PROFILE,
            OcrPerformanceProfile.defaultId())).id;
        ocrModelId = prefs.getString(PREF_OCR_MODEL, "cnlite_smallrec");
        if (!ocrModelId.equals("cnlite_smallrec") && !ocrModelId.equals("ppocrv6_small") && !ocrModelId.equals("ppocrv6_tiny") && !ocrModelId.equals("ppocrv5_mobile")) ocrModelId = "cnlite_smallrec";
        speechNoEnglish = prefs.getBoolean(PREF_NO_ENGLISH, false);
        speechNoNumbers = prefs.getBoolean(PREF_NO_NUMBERS, false);
        maxSpeechChars = Math.max(0, Math.min(5000, prefs.getInt(PREF_MAX_SPEECH_CHARS, 0)));
    }

    private String ocrModelDisplayName(String modelId) {
        if ("cnlite_smallrec".equals(modelId)) return "ChineseOCR-Lite 绿色DET + Small REC";
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
        realtimeScene.reset();
        lastQuickScanGeneration = -1L;
        lastOcrEngineError = "";
        status.setText("正在切换到 " + ocrModelDisplayName(modelId) + "…");
        recognizer = new PaddleOcrEngine(getApplicationContext(), new PaddleOcrEngine.Listener() {
            @Override public void onReady() {
                lastOcrEngineError = "";
                runOnUiThread(() -> {
                    status.setText(ocrModelDisplayName(modelId) + " 已加载，可以开始识别");
                    Toast.makeText(MainActivity.this, ocrModelDisplayName(modelId) + " 已切换", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onError(String message) {
                lastOcrEngineError = message;
                Log.e("KanziOCR", "Model initialization failed: " + message);
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
        }, modelId, OcrPerformanceProfile.forId(ocrProfileId));
        recognizer.setFastScanMode(!longReadingMode);
        recognizer.setLiveTextRotationDegrees(liveTextRotationDegrees);
        recognizer.setReadingFocusMode(readingFocusMode);
        recognizer.setAiLayoutEnabled(aiLayoutEnabled);
        recognizer.setVerticalLiveReadingEnabled(verticalLiveReadingEnabled);
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

        TextView pageFocusTitle = new TextView(this);
        pageFocusTitle.setText("文字连续朗读范围");
        pageFocusTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        pageFocusTitle.setPadding(0, dp(12), 0, dp(4));
        panel.addView(pageFocusTitle);
        RadioGroup pageFocusGroup = new RadioGroup(this);
        pageFocusGroup.setOrientation(RadioGroup.VERTICAL);
        String[][] pageFocusOptions = new String[][] {
            {"AUTO", "连续文字流（推荐，保留所有检测文字）"},
            {"LEFT", "只读左侧文字区域（手动筛选）"},
            {"RIGHT", "只读右侧文字区域（手动筛选）"}
        };
        for (String[] option : pageFocusOptions) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setTag(option[0]);
            radio.setText(option[1]);
            radio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            radio.setChecked(readingFocusMode.equals(option[0]));
            pageFocusGroup.addView(radio);
        }
        panel.addView(pageFocusGroup);
        TextView pageFocusNote = new TextView(this);
        pageFocusNote.setText("快速扫读默认完整朗读已识别文字：普通横排从左到右、从上到下；独立竖排标牌从上到下；可信传统多列竖排从右往左。不会只选择中央区域。书籍长文模式保留原有流程。");
        pageFocusNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pageFocusNote.setTextColor(0xFF667085);
        panel.addView(pageFocusNote);

        CheckBox layoutAiCheck = new CheckBox(this);
        layoutAiCheck.setText("实验功能：AI 版面理解 + 连续文字流规则");
        layoutAiCheck.setChecked(aiLayoutEnabled);
        layoutAiCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        panel.addView(layoutAiCheck);
        TextView layoutAiNote = new TextView(this);
        layoutAiNote.setText("实时文字不运行额外的版面 AI，优先保证速度。此实验选项仅用于长文阅读；不修改原始 OCR 文字。");
        layoutAiNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        layoutAiNote.setTextColor(0xFF667085);
        panel.addView(layoutAiNote);

        CheckBox verticalLiveCheck = new CheckBox(this);
        verticalLiveCheck.setText("自动识别横排／竖排并朗读（默认开启）");
        verticalLiveCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        verticalLiveCheck.setChecked(verticalLiveReadingEnabled);
        panel.addView(verticalLiveCheck);
        TextView verticalLiveNote = new TextView(this);
        verticalLiveNote.setText("无需手动选择文字方向：横排完整朗读；单列竖排从上到下；多列传统竖排从右到左。不猜字、不补字、不按中央截断内容。USB输入方向的自动校准继续独立运行。");
        verticalLiveNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        verticalLiveNote.setTextColor(0xFF667085);
        panel.addView(verticalLiveNote);

        TextView liveRotationTitle = new TextView(this);
        liveRotationTitle.setText("手机摄像头实时文字识别方向（不控制预览）");
        liveRotationTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        liveRotationTitle.setPadding(0, dp(14), 0, dp(4));
        panel.addView(liveRotationTitle);
        RadioGroup rotationGroup = new RadioGroup(this);
        rotationGroup.setOrientation(RadioGroup.VERTICAL);
        String[][] rotationOptions = {
            {"0", "自动纠正整页方向（默认；仅明确异常时追加OCR）"},
            {"90", "手动：将OCR输入向右旋转90°"},
            {"180", "手动：将OCR输入旋转180°"},
            {"270", "手动：将OCR输入向左旋转90°"}
        };
        for (String[] option : rotationOptions) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setTag(option[0]);
            radio.setText(option[1]);
            radio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            radio.setChecked(Integer.toString(liveTextRotationDegrees).equals(option[0]));
            rotationGroup.addView(radio);
        }
        panel.addView(rotationGroup);
        TextView liveRotationNote = new TextView(this);
        liveRotationNote.setText("仅控制手机摄像头的实时OCR；USB摄像头在下方单独设置OCR输入角度。手机独立诊断仍保留原始0°基线。");
        liveRotationNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        liveRotationNote.setTextColor(0xFF667085);
        panel.addView(liveRotationNote);

        TextView usbOcrRotationTitle = new TextView(this);
        usbOcrRotationTitle.setText("USB摄像头的OCR方向（通常无需操作）");
        usbOcrRotationTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        usbOcrRotationTitle.setPadding(0, dp(14), 0, dp(4));
        panel.addView(usbOcrRotationTitle);
        RadioGroup usbOcrRotationGroup = new RadioGroup(this);
        usbOcrRotationGroup.setOrientation(RadioGroup.VERTICAL);
        String[][] usbOcrOptions = {
            {"-1", "自动校准并记住USB摄像头方向（推荐）"},
            {"0", "仅用于故障排查：保持原始0°"},
            {"90", "USB OCR向右旋转90°"},
            {"180", "USB OCR旋转180°"},
            {"270", "USB OCR向左旋转90°（当前刮痧宣传牌建议）"}
        };
        for (String[] option : usbOcrOptions) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setTag(option[0]);
            radio.setText(option[1]);
            radio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            radio.setChecked(Integer.toString(usbOcrInputRotationDegrees).equals(option[0]));
            usbOcrRotationGroup.addView(radio);
        }
        panel.addView(usbOcrRotationGroup);
        TextView usbOcrNote = new TextView(this);
        usbOcrNote.setText("首次遇到原始方向明显错误的文字时，才追加本地OCR对照并缓存方向；后续直接复用。无法确认则保留原文。手动角度仅供排障；预览旋转/镜像仍完全独立。");
        usbOcrNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        panel.addView(usbOcrNote);

        // Users normally need one Start button, not eight orientation radios.
        // Keep manual overrides accessible when needed, but collapse by default.
        CheckBox advancedDirectionCheck = new CheckBox(this);
        advancedDirectionCheck.setText("高级：展开摄像头OCR方向手动设置（故障排查）");
        advancedDirectionCheck.setChecked(false);
        advancedDirectionCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        panel.addView(advancedDirectionCheck);
        TextView autoDirectionSummary = new TextView(this);
        autoDirectionSummary.setText(usbOcrInputRotationDegrees == -1
            ? "USB OCR自动校准已开启；手机和USB分别处理，不需逐页调整。"
            : "USB OCR目前使用手动角度（如需自动，请在高级设置中选择自动校准）。");
        autoDirectionSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        panel.addView(autoDirectionSummary);
        View[] extraDirections = {liveRotationTitle,rotationGroup,liveRotationNote,
            usbOcrRotationTitle,usbOcrRotationGroup,usbOcrNote};
        for(View extra:extraDirections)extra.setVisibility(View.GONE);
        advancedDirectionCheck.setOnCheckedChangeListener((button,checked)-> {
            for(View extra:extraDirections)
                extra.setVisibility(checked?View.VISIBLE:View.GONE);
        });

        TextView modelManagementTitle = new TextView(this);
        modelManagementTitle.setText("OCR 引擎选择");
        modelManagementTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        modelManagementTitle.setPadding(0, dp(14), 0, dp(4));
        panel.addView(modelManagementTitle);
        RadioGroup modelGroup = new RadioGroup(this);
        modelGroup.setOrientation(RadioGroup.VERTICAL);
        String[][] modelOptions = new String[][] {
            {"cnlite_smallrec", "ChineseOCR-Lite 绿色框 + Small REC（本测试版默认）"},
            {"ppocrv6_small", "PP-OCRv6 Small（旧版基线，回退使用）"},
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
        modelManagementInfo.setText("本测试版默认 ChineseOCR-Lite DET + 原 PP-OCRv6 Small REC；其他三个旧模型保留作故障回退。模型随安装包提供，手机识字与朗读全程离线。切换时将重新初始化。");
        modelManagementInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        modelManagementInfo.setTextColor(0xFF667085);
        panel.addView(modelManagementInfo);

        CheckBox qualityCheck = new CheckBox(this);
        qualityCheck.setText("长文模式识别困难时给出语音提示（实时扫描不提示）");
        qualityCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        qualityCheck.setChecked(qualityPromptsEnabled);
        panel.addView(qualityCheck);

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
            if (canSpeak()) {
                float previewRate = 0.5f + speechRateSeek.getProgress() / 100f;
                if (tts != null) tts.setSpeechRate(previewRate);
                clearSpeechQueue(true);
                DownloadableVoiceStore.Voice previewVoice = voiceStore.selectedVoice();
                String sample = "看字助手语音试听，现在是当前设置的朗读速度。";
                if (!previewVoice.isSystem() && voiceStore.isInstalled(previewVoice) &&
                    offlineVoicePlayback != null) {
                    offlineVoicePlayback.speak(previewVoice, sample, previewRate,
                        "settings-preview", new OfflineVoicePlayback.Callback() {
                            @Override public void completed(String id) {}
                            @Override public void failed(String id, String reason) {
                                showSpeechFailure("自然语音试听失败，已使用系统语音");
                                tts.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "settings-preview-system");
                            }
                        });
                } else {
                    tts.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "settings-preview");
                }
            } else {
                Toast.makeText(this, "中文语音尚未准备好", Toast.LENGTH_SHORT).show();
            }
        });
        panel.addView(speechPreviewButton);

        TextView voiceTitle = new TextView(this);
        voiceTitle.setText("离线自然语音 · 点选即可下载安装");
        voiceTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        voiceTitle.setPadding(0, dp(18), 0, dp(4));
        panel.addView(voiceTitle);
        TextView voiceDescription = new TextView(this);
        voiceDescription.setText("首次安装语音包需要网络。下载完成后在手机本地朗读；点击已安装声音即可切换，失败时保留系统语音。");
        voiceDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        voiceDescription.setTextColor(0xFF667085);
        panel.addView(voiceDescription);
        TextView voiceStatus = new TextView(this);
        voiceStatus.setText("当前语音：" + voiceStore.selectedVoice().title);
        voiceStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        panel.addView(voiceStatus);
        LinearLayout voiceOptions = new LinearLayout(this);
        voiceOptions.setOrientation(LinearLayout.VERTICAL);
        panel.addView(voiceOptions);
        final java.util.Map<String, TextView> voiceRows = new java.util.LinkedHashMap<>();
        for (DownloadableVoiceStore.Voice voice : DownloadableVoiceStore.VOICES) {
            TextView entry = new TextView(this);
            entry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            entry.setPadding(dp(10), dp(13), dp(10), dp(13));
            entry.setBackgroundColor(0xFFF1F4F8);
            entry.setClickable(true);
            entry.setFocusable(true);
            entry.setContentDescription("选择语音：" + voice.title + "；未安装时自动下载安装");
            voiceRows.put(voice.id, entry);
            voiceOptions.addView(entry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            entry.setOnLongClickListener(v -> {
                if (voice.isSystem() || !voiceStore.isInstalled(voice)) return false;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("移除离线语音包")
                    .setMessage("确定移除 " + voice.title + " 的模型文件以释放空间？" +
                        ("kokoro".equals(voice.pack) ? "Kokoro 男声与女声共用同一语音包。" : ""))
                    .setNegativeButton("取消", null)
                    .setPositiveButton("移除", (dialog, which) -> {
                        clearSpeechQueue(true);
                        voiceStore.remove(voice, () -> {
                            voiceStatus.setText("当前语音：" + voiceStore.selectedVoice().title);
                            for (DownloadableVoiceStore.Voice item : DownloadableVoiceStore.VOICES) {
                                TextView row=voiceRows.get(item.id);
                                if(row!=null) row.setText(voiceRowLabel(item));
                            }
                            Toast.makeText(this,"已移除离线语音包",Toast.LENGTH_SHORT).show();
                        });
                    }).show();
                return true;
            });
            entry.setOnClickListener(v -> {
                entry.setText(voice.title + " · 正在准备…");
                voiceStore.choose(voice, new DownloadableVoiceStore.Listener() {
                    @Override public void onStatus(DownloadableVoiceStore.Voice target, String message) {
                        TextView row = voiceRows.get(target.id);
                        if (row != null) row.setText(target.title + " · " + message);
                        voiceStatus.setText("准备切换到：" + target.title);
                    }
                    @Override public void onSelected(DownloadableVoiceStore.Voice selected) {
                        // A complete download does NOT establish that the
                        // native model can generate and play audio on this
                        // phone. Validate real output before persisting it.
                        clearSpeechQueue(true);
                        if (selected.isSystem()) {
                            voiceStore.activate(selected);
                            if (offlineVoicePlayback != null)
                                offlineVoicePlayback.releaseModelWhenIdle();
                            voiceStatus.setText("当前语音：系统中文语音");
                            for (DownloadableVoiceStore.Voice item : DownloadableVoiceStore.VOICES) {
                                TextView row = voiceRows.get(item.id);
                                if (row != null) row.setText(voiceRowLabel(item));
                            }
                            return;
                        }
                        voiceStatus.setText("正在生成并试听：" + selected.title);
                        TextView selectedRow = voiceRows.get(selected.id);
                        if (selectedRow != null) selectedRow.setText(
                            selected.title + " · 正在验证声音，请留意手机是否发声…");
                        if (offlineVoicePlayback == null) {
                            onFailure(selected, "本地语音播放引擎尚未初始化");
                            return;
                        }
                        offlineVoicePlayback.speak(selected, "你好，欢迎使用看字助手。",
                            speechRate, "voice-verify-" + System.nanoTime(),
                            new OfflineVoicePlayback.Callback() {
                                @Override public void completed(String id) {
                                    // Do not undo a newer voice choice.
                                    if (!voiceStore.isInstalled(selected)) return;
                                    voiceStore.activate(selected);
                                    voiceStatus.setText("已试听并选用：" + selected.title);
                                    for (DownloadableVoiceStore.Voice item : DownloadableVoiceStore.VOICES) {
                                        TextView row = voiceRows.get(item.id);
                                        if (row != null) row.setText(voiceRowLabel(item));
                                    }
                                    Toast.makeText(MainActivity.this,
                                        "已启用自然语音：" + selected.title,
                                        Toast.LENGTH_SHORT).show();
                                }
                                @Override public void failed(String id, String reason) {
                                    voiceStatus.setText("语音验证失败，保留：" +
                                        voiceStore.selectedVoice().title);
                                    TextView row = voiceRows.get(selected.id);
                                    if (row != null) row.setText(selected.title +
                                        " · 语音播放失败，点击重试");
                                    Toast.makeText(MainActivity.this,
                                        "模型不能正常发声：" + reason,
                                        Toast.LENGTH_LONG).show();
                                }
                            });
                    }
                    @Override public void onFailure(DownloadableVoiceStore.Voice failed, String reason) {
                        voiceStatus.setText("安装未完成，仍使用：" + voiceStore.selectedVoice().title);
                        TextView row = voiceRows.get(failed.id);
                        if (row != null) row.setText(failed.title + " · 下载失败，点击重试");
                        Toast.makeText(MainActivity.this,
                            "语音包安装失败：" + reason, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }
        for (DownloadableVoiceStore.Voice voice : DownloadableVoiceStore.VOICES) {
            voiceRows.get(voice.id).setText(voiceRowLabel(voice));
        }

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
        rotationNote.setText("手机预览默认自动保持正向和16:9等比；0°表示不额外旋转。预览旋转不影响OCR输入图像。");
        rotationNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        panel.addView(rotationNote);


        CheckBox mirrorCheck = new CheckBox(this);
        mirrorCheck.setText("附加水平镜像（USB可反向切换校正）");
        mirrorCheck.setChecked(previewMirror);
        panel.addView(mirrorCheck);
        CheckBox usbCorrectMirrorCheck = new CheckBox(this);
        usbCorrectMirrorCheck.setText("USB画面左右校正（默认开启；吉他在右边应显示在右边）");
        usbCorrectMirrorCheck.setChecked(usbPreviewCorrectMirror);
        panel.addView(usbCorrectMirrorCheck);
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

        transformNote.setText("USB左右校正仅改变USB预览显示和OCR框在预览上的位置；不会水平翻转原始NV21图像或OCR识别输入。手机摄像头预览保持独立。附加水平镜像可以取消USB默认校正。");
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

        TextView profileTitle = new TextView(this);
        profileTitle.setText("X200 Pro / 12GB 横竖排综合A/B实验");
        profileTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        profileTitle.setPadding(0, dp(16), 0, dp(4));
        panel.addView(profileTitle);
        RadioGroup profileGroup = new RadioGroup(this);
        profileGroup.setOrientation(RadioGroup.VERTICAL);
        OcrPerformanceProfile[] profiles = {
            OcrPerformanceProfile.REC_FAST2, OcrPerformanceProfile.BASELINE,
            OcrPerformanceProfile.BALANCED, OcrPerformanceProfile.QUALITY,
            OcrPerformanceProfile.THROUGHPUT
        };
        for (OcrPerformanceProfile profile : profiles) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setTag(profile.id);
            radio.setText(profile.label + "（每类异常候选预算=" +
                profile.maxShadowRescuesPerDiagnostic + "）");
            radio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            radio.setChecked(profile.id.equals(ocrProfileId));
            profileGroup.addView(radio);
        }
        panel.addView(profileGroup);
        TextView profileInfo = new TextView(this);
        profileInfo.setText("v56默认REC批量2、2线程；只是减少每帧逐行ONNX调用次数，不改绿色检测框或识字字典。" +
            " 如识字变差、速度反而慢或发热，可选『对照：2线程/批1』回退。诊断里比较REC预处理/推理/解码耗时。" +
            " 批量处理有填充开销，仍须vivo实机核验，不能提前宣称速度提升。");
        profileInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        profileInfo.setTextColor(0xFF667085);
        panel.addView(profileInfo);

        TextView diagnosticTitle = new TextView(this);
        diagnosticTitle.setText("OCR 诊断");
        diagnosticTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        diagnosticTitle.setPadding(0, dp(18), 0, dp(4));
        panel.addView(diagnosticTitle);
        TextView diagnosticNote = new TextView(this);
        diagnosticNote.setText("原版四路同帧A/B保持不变；新增按需整页方向识别0°/90°/180°/270°独立诊断。只有可靠几何或极低质量信号才追加一次整页OCR，输出候选和耗时，原版A/B不会被覆盖。真实竖排候选仍仅诊断。分享前检查隐私文字。");
        diagnosticNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        diagnosticNote.setTextColor(0xFF667085);
        panel.addView(diagnosticNote);
        diagnosticReportView = new TextView(this);
        diagnosticReportView.setText("尚未运行诊断");
        diagnosticReportView.setTextIsSelectable(true);
        diagnosticReportView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        diagnosticReportView.setPadding(dp(8), dp(8), dp(8), dp(8));
        diagnosticReportView.setBackgroundColor(0xFFF1F3F5);
        // Controls stay above the potentially very long diagnostic output, so
        // users do not need to scroll past hundreds of OCR boxes to compare A/B.
        Button retryOcrButton = createControlButton("重新初始化 OCR");
        retryOcrButton.setOnClickListener(v -> {
            diagnosticReportView.setText("正在重新初始化 " + ocrModelDisplayName(ocrModelId) + "…");
            switchOcrModel(ocrModelId);
        });
        panel.addView(retryOcrButton);
        Button runDiagnosticButton = createControlButton("开始 OCR 诊断");
        runDiagnosticButton.setOnClickListener(v -> {
            if (!(usingPhoneCamera || previewCallbackAdded)) {
                diagnosticReportView.setText("诊断失败：尚未获取摄像头画面。请检查 USB 连接或手机摄像头权限。");
                return;
            }
            if (recognizer == null || !recognizer.isReady()) {
                diagnosticReportView.setText(lastOcrEngineError.isEmpty()
                    ? "OCR 引擎正在初始化。若长时间不就绪，请点击「重新初始化 OCR」并复制诊断报告。"
                    : "OCR 引擎初始化失败：" + lastOcrEngineError +
                        "\n当前模型：" + ocrModelId +
                        "\n中文语音：" + (ttsReady ? "系统语音已就绪" : "系统语音未就绪") +
                        "\n可尝试「重新初始化 OCR」，若仍失败请复制本页文字。");
                return;
            }
            diagnosticStartedAt = System.currentTimeMillis();
            lastDiagnosticText = "";
            lastCandidateDiagnosticText = "";
            diagnosticReportView.setText("正在等待摄像头下一帧并执行 OCR……" +
                "\n提示：OCR 诊断只生成文字，不会自动朗读。需要听诊断结果请点击下方的「朗读这次诊断识别出的文字」。" +
                "\n实时朗读状态：" + (running ? "已启动" : "未启动，请点开始识别") +
                "；扫描模式：" + (automaticMode ? "自动" : "手动") +
                "；中文语音：" + (canSpeak() ? "已就绪" : "尚未就绪"));
            diagnosticCaptureRequested.set(true);
        });
        panel.addView(runDiagnosticButton);
        Button captureImageEvidenceButton = createControlButton("手动采集一帧：图像＋真实裁剪＋输入张量（仅本机）");
        captureImageEvidenceButton.setOnClickListener(v -> {
            if (!(usingPhoneCamera || previewCallbackAdded) ||
                recognizer == null || !recognizer.isReady()) {
                Toast.makeText(this, "先连接摄像头并等待 Small 模型加载", Toast.LENGTH_LONG).show();
                return;
            }
            saveEvidenceNextDiagnostic.set(true);
            runDiagnosticButton.performClick();
        });
        panel.addView(captureImageEvidenceButton);
        Button exportImageEvidenceButton = createControlButton("导出上次单帧诊断 ZIP（手动选择位置）");
        exportImageEvidenceButton.setOnClickListener(v -> {
            java.io.File file = recognizer == null ? null : recognizer.latestEvidenceZip();
            if (file == null) {
                Toast.makeText(this, "请先点击「手动采集一帧」，完成后再导出", Toast.LENGTH_LONG).show();
                return;
            }
            android.content.Intent intent =
                new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/zip");
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.putExtra(android.content.Intent.EXTRA_TITLE, "kanzi-ocr-single-frame.zip");
            startActivityForResult(intent, REQUEST_SINGLE_FRAME_ZIP);
        });
        panel.addView(exportImageEvidenceButton);
        Button speakDiagnosticButton = createControlButton("朗读这次诊断识别出的文字");
        speakDiagnosticButton.setOnClickListener(v -> {
            String textToRead = lastDiagnosticText == null ? "" : lastDiagnosticText.trim();
            if (textToRead.isEmpty()) Toast.makeText(this, "这次诊断没有得到可朗读的文字", Toast.LENGTH_SHORT).show();
            else if (!canSpeak()) Toast.makeText(this, "中文语音尚未准备好", Toast.LENGTH_SHORT).show();
            else {
                queueSpeechText(textToRead, normalizeOcrText(textToRead), true);
            }
        });
        panel.addView(speakDiagnosticButton);
        Button speakCandidateButton = createControlButton("试听实验候选阅读顺序");
        speakCandidateButton.setOnClickListener(v -> {
            // A/B listening is explicitly user-triggered. Live automatic TTS
            // continues to use the established reading-order implementation.
            String textToRead = lastCandidateDiagnosticText == null
                ? "" : lastCandidateDiagnosticText;
            if (textToRead.trim().isEmpty()) {
                Toast.makeText(this, "尚无实验候选文字，请先运行诊断", Toast.LENGTH_SHORT).show();
            } else if (!canSpeak()) {
                Toast.makeText(this, "中文语音尚未准备好", Toast.LENGTH_SHORT).show();
            } else {
                queueSpeechText(textToRead, normalizeOcrText(textToRead), true);
            }
        });
        panel.addView(speakCandidateButton);
        Button copyDiagnosticButton = createControlButton("复制完整 OCR + AI 版面诊断");
        copyDiagnosticButton.setOnClickListener(v -> {
            String contents = diagnosticReportView == null ? ""
                : diagnosticReportView.getText().toString();
            if (contents.isEmpty() || "尚未运行诊断".equals(contents)
                || contents.startsWith("正在等待")) {
                Toast.makeText(this, "请先完成一次诊断", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Toast.makeText(this, "设备剪贴板不可用", Toast.LENGTH_SHORT).show();
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("Kanzi OCR 诊断", contents));
            Toast.makeText(this, "诊断文字已复制，可以粘贴发送", Toast.LENGTH_SHORT).show();
        });
        panel.addView(copyDiagnosticButton);
        panel.addView(diagnosticReportView);

        TextView similarityNote = new TextView(this);
        similarityNote.setText("主界面可切换长文阅读与快速扫读：长文模式优先读完当前内容，快速扫读只在确认为新目标时切换，避免反复从头朗读。相似度阈值仅用于长文去重。");
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
                RadioButton selectedProfile = profileGroup.findViewById(
                    profileGroup.getCheckedRadioButtonId());
                String requestedProfile = selectedProfile != null &&
                    selectedProfile.getTag() != null ?
                    selectedProfile.getTag().toString() : ocrProfileId;
                String safeProfile = OcrPerformanceProfile.forId(requestedProfile).id;
                boolean profileChanged = !ocrProfileId.equals(safeProfile);
                ocrProfileId = safeProfile;
                RadioButton selectedFocus = pageFocusGroup.findViewById(pageFocusGroup.getCheckedRadioButtonId());
                String requestedFocus = selectedFocus != null && selectedFocus.getTag() != null
                    ? selectedFocus.getTag().toString() : readingFocusMode;
                boolean focusChanged = !readingFocusMode.equals(requestedFocus);
                readingFocusMode = requestedFocus;
                RadioButton selectedRotation = rotationGroup.findViewById(rotationGroup.getCheckedRadioButtonId());
                int requestedTextRotation = liveTextRotationDegrees;
                if (selectedRotation != null && selectedRotation.getTag() != null) {
                    try {
                        requestedTextRotation = Integer.parseInt(selectedRotation.getTag().toString());
                    } catch (NumberFormatException ignored) {}
                }
                boolean textRotationChanged = requestedTextRotation != liveTextRotationDegrees;
                liveTextRotationDegrees = requestedTextRotation;
                RadioButton selectedUsbOcrRotation = usbOcrRotationGroup.findViewById(
                    usbOcrRotationGroup.getCheckedRadioButtonId());
                int requestedUsbOcrRotation = usbOcrInputRotationDegrees;
                if (selectedUsbOcrRotation != null && selectedUsbOcrRotation.getTag() != null) {
                    try {
                        requestedUsbOcrRotation = Integer.parseInt(
                            selectedUsbOcrRotation.getTag().toString());
                    } catch (NumberFormatException ignored) {}
                }
                requestedUsbOcrRotation = UsbAutoOcrOrientation.normalizePreference(requestedUsbOcrRotation);
                boolean usbOcrRotationChanged =
                    requestedUsbOcrRotation != usbOcrInputRotationDegrees;
                usbOcrInputRotationDegrees = requestedUsbOcrRotation;
                boolean verticalReadingChanged =
                    verticalLiveReadingEnabled != verticalLiveCheck.isChecked();
                verticalLiveReadingEnabled = verticalLiveCheck.isChecked();
                boolean aiLayoutChanged = aiLayoutEnabled != layoutAiCheck.isChecked();
                aiLayoutEnabled = layoutAiCheck.isChecked();
                similarityThreshold = similaritySeek.getProgress();
                speechRate = 0.5f + speechRateSeek.getProgress() / 100f;
                qualityPromptsEnabled = qualityCheck.isChecked();
                speechNoEnglish = noEnglishCheck.isChecked();
                speechNoNumbers = noNumbersCheck.isChecked();
                try { maxSpeechChars = Math.max(0, Math.min(5000, Integer.parseInt(maxCharsInput.getText().toString().trim()))); }
                catch (NumberFormatException ignored) { maxSpeechChars = 0; }
                previewRotation = rotationSeek.getProgress() * 90;
                previewMirror = mirrorCheck.isChecked();
                usbPreviewCorrectMirror = usbCorrectMirrorCheck.isChecked();
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
                    .putBoolean(PREF_QUALITY_PROMPTS, qualityPromptsEnabled)
                    .putFloat(PREF_SPEECH_RATE, speechRate)
                    .putInt(PREF_PREVIEW_ROTATION, previewRotation)
                    .putBoolean(PREF_PREVIEW_MIRROR, previewMirror)
                    .putBoolean(PREF_USB_PREVIEW_CORRECT_MIRROR, usbPreviewCorrectMirror)
                    .putBoolean(PREF_PREVIEW_FLIP, previewVerticalFlip)
                    .putBoolean(PREF_ENHANCED_OCR, enhancedOcr)
                    .putString(PREF_OCR_MODEL, ocrModelId)
                    .putString(PREF_OCR_PROFILE, ocrProfileId)
                    .putString(PREF_READING_FOCUS, readingFocusMode)
                    .putBoolean(PREF_AI_LAYOUT, aiLayoutEnabled)
                    .putBoolean(PREF_VERTICAL_LIVE_READING, verticalLiveReadingEnabled)
                    .putInt(PREF_REALTIME_TEXT_ROTATION, liveTextRotationDegrees)
                    .putInt(PREF_USB_OCR_INPUT_ROTATION, usbOcrInputRotationDegrees)
                    .putBoolean(PREF_NO_ENGLISH, speechNoEnglish)
                    .putBoolean(PREF_NO_NUMBERS, speechNoNumbers)
                    .putInt(PREF_MAX_SPEECH_CHARS, maxSpeechChars)
                    .apply();
                if (focusChanged || aiLayoutChanged || textRotationChanged || usbOcrRotationChanged || verticalReadingChanged) {
                    readingEpoch.incrementAndGet();
                    frameChangeGate.reset();
                    realtimeScene.reset();
                    lastQuickScanGeneration = -1L;
                    liveSpeechGuard.reset();
                    lastSpoken = "";
                    clearSpeechQueue(true);
                    if (recognizer != null) {
                        if (focusChanged) recognizer.setReadingFocusMode(readingFocusMode);
                        recognizer.setAiLayoutEnabled(aiLayoutEnabled);
                        recognizer.setLiveTextRotationDegrees(liveTextRotationDegrees);
                        recognizer.setVerticalLiveReadingEnabled(verticalLiveReadingEnabled);
                    }
                    Toast.makeText(this, "已更新独立的OCR输入方向（预览方向不变）", Toast.LENGTH_SHORT).show();
                }
                if (modelChanged || profileChanged) switchOcrModel(selectedModelId);
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
        // On the USB GL path, scaling the outer TextureView was not a
        // reliable correction. Mirror the *video contents* through its
        // TextureView matrix instead, never the OCR's source NV21 pixels.
        // Apply after camera open, size changes and switching camera source.
        boolean usbMirror = UsbPreviewMirror.shouldMirror(usbPreviewCorrectMirror, previewMirror);
        cameraTextureView.setScaleX(1.0f);
        cameraTextureView.setScaleY(previewVerticalFlip ? -1.0f : 1.0f);
        if (viewWidth > 0 && viewHeight > 0) {
            Matrix usbDisplay = new Matrix();
            if (usbMirror) usbDisplay.setScale(-1.0f, 1.0f,
                viewWidth * 0.5f, viewHeight * 0.5f);
            cameraTextureView.setTransform(usbDisplay);
        }
        if (ocrOverlay != null) ocrOverlay.setUsbDisplayMirror(usbMirror);
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
                lastOcrEngineError = "";
                frameChangeGate.reset();
                lastProcess = 0L;
                runOnUiThread(() -> status.setText(ocrModelDisplayName(ocrModelId) + " 已加载，可以开始识别"));
            }
            @Override public void onError(String message) {
                lastOcrEngineError = message;
                Log.e("KanziOCR", "OCR initialization failed: " + message);
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
        }, ocrModelId, OcrPerformanceProfile.forId(ocrProfileId));
        recognizer.setFastScanMode(!longReadingMode);
        recognizer.setLiveTextRotationDegrees(liveTextRotationDegrees);
        recognizer.setReadingFocusMode(readingFocusMode);
        recognizer.setAiLayoutEnabled(aiLayoutEnabled);
        recognizer.setVerticalLiveReadingEnabled(verticalLiveReadingEnabled);
        recognizer.initialize();
        phoneController = new InternalCameraController(this, phoneTexture, new InternalCameraController.Listener() {
            @Override public boolean shouldReceiveFrame() {
                if (!usingPhoneCamera || !running) return diagnosticCaptureRequested.get();
                if (manualCaptureRequested.get() || diagnosticCaptureRequested.get()) return true;
                if (!automaticMode || (longReadingMode && speechPageActive)) return false;
                // During real-time speech and even during an ongoing OCR,
                // inspect a few preview frames per second for a NEW target.
                // The lightweight observer never runs the OCR model itself.
                if (!longReadingMode)
                    return System.currentTimeMillis() - lastLiveObservationAt >= 115L;
                return !busy.get() &&
                    System.currentTimeMillis() - lastProcess >= recognitionIntervalMs;
            }
            @Override public void onFrame(byte[] nv21, int width, int height) {
                if (usingPhoneCamera) processCameraFrame(nv21, width, height, true);
            }
            @Override public void onOpened(int rawWidth, int rawHeight,
                                           int sensorNaturalRotation, int sensorInputCorrectionDegrees) {
                phoneRawWidth = rawWidth;
                phoneRawHeight = rawHeight;
                phoneSensorNaturalRotation = sensorNaturalRotation;
                phoneOcrRotation = sensorInputCorrectionDegrees;
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
        // Voice data is downloaded only after a user taps a voice list entry;
        // app initialization never fetches models or contacts the Internet.
        voiceStore = new DownloadableVoiceStore(this);
        offlineVoicePlayback = new OfflineVoicePlayback(voiceStore);
        tts = new TextToSpeech(this, code -> {
            if (code == TextToSpeech.SUCCESS) {
                int language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                // Some Android installations only ship an offline traditional
                // Chinese voice. Both can speak OCR Chinese characters; try a
                // local alternative rather than silently disabling all audio.
                if (language == TextToSpeech.LANG_MISSING_DATA ||
                    language == TextToSpeech.LANG_NOT_SUPPORTED)
                    language = tts.setLanguage(Locale.TRADITIONAL_CHINESE);
                if (language == TextToSpeech.LANG_MISSING_DATA ||
                    language == TextToSpeech.LANG_NOT_SUPPORTED)
                    language = tts.setLanguage(Locale.CHINESE);
                tts.setSpeechRate(speechRate);
                ttsReady = language != TextToSpeech.LANG_MISSING_DATA &&
                    language != TextToSpeech.LANG_NOT_SUPPORTED;
                if (ttsReady) {
                    Log.i("KanziTTS", "Chinese TextToSpeech engine ready");
                    // If the camera already saw this page before TTS was ready,
                    // make it immediately eligible for reading now.
                    frameChangeGate.reset();
                    lastProcess = 0L;
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) {}
                        @Override public void onDone(String utteranceId) {
                            mainHandler.post(() -> finishQueuedUtterance(utteranceId));
                        }
                        @Override public void onError(String utteranceId) {
                            Log.e("KanziTTS", "TTS utterance failed: " + utteranceId);
                            mainHandler.post(() -> {
                                showSpeechFailure("系统语音合成失败，请检查中文 TTS 引擎");
                                finishQueuedUtterance(utteranceId);
                            });
                        }
                        @Override public void onStop(String utteranceId, boolean interrupted) {
                            mainHandler.post(() -> finishQueuedUtterance(utteranceId));
                        }
                    });
                    speakNextQueued();
                } else {
                    status.setText("系统没有可用的中文语音，请先安装中文语音包");
                    Log.e("KanziTTS", "Chinese TTS language unavailable: " + language);
                    Toast.makeText(MainActivity.this,
                        "OCR 已可识别，但系统中文语音不可用。请检查系统文字转语音设置。",
                        Toast.LENGTH_LONG).show();
                }
            } else {
                ttsReady = false;
                Log.e("KanziTTS", "TTS initialize failed: " + code);
                status.setText("系统中文语音引擎初始化失败，状态码：" + code);
                Toast.makeText(MainActivity.this,
                    "系统语音引擎初始化失败，OCR 识别仍可用但不能朗读。",
                    Toast.LENGTH_LONG).show();
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
            readingEpoch.incrementAndGet();
            realtimeScene.reset();
            frameChangeGate.reset();
            lastQuickScanGeneration = -1L;
            liveSpeechGuard.reset();
            manualCaptureRequested.set(false);
            clearSpeechQueue(true);
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
            if (running && !canSpeak()) {
                Toast.makeText(this,
                    "识别已启动，但中文语音尚未就绪。可在设置中点「试听当前语速」检查语音引擎。",
                    Toast.LENGTH_LONG).show();
            }
            if (running && !automaticMode) {
                Toast.makeText(this,
                    "当前是手动模式，需要点击「识别一次」才会朗读。",
                    Toast.LENGTH_LONG).show();
            }
            readingEpoch.incrementAndGet();
            realtimeScene.reset();
            lastQuickScanGeneration = -1L;
            lastQuickScanReliable = false;
            liveSpeechGuard.reset();
            if (!running) {
                manualCaptureRequested.set(false);
                clearSpeechQueue(true);
                if (ocrOverlay != null) ocrOverlay.clear();
            } else {
                frameChangeGate.reset();
                lastProcess = 0L;
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
        realtimeScene.reset();
        lastQuickScanGeneration = -1L;
        liveSpeechGuard.reset();
        clearSpeechQueue(true);
        candidateText = "";
        candidateNormalized = "";
        candidateCount = 0;
        lastSpoken = "";
        noTextSince = 0L;
        if (ocrOverlay != null) ocrOverlay.clear();
        if (recognizer != null) recognizer.setReadingFocusMode(readingFocusMode);
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
        // Camera2's TextureView ALREADY rotates SENSOR_ORIENTATION to device
        // natural orientation. Do not rotate it by phoneOcrRotation AGAIN.
        // Manual 0 degrees means zero extra rotation on the phone preview.
        int displayDegrees = getWindowManager().getDefaultDisplay().getRotation() * 90;
        int angle = PhonePreviewGeometry.rotation(previewRotation, -displayDegrees);
        PhonePreviewGeometry.Layout fit = PhonePreviewGeometry.camera2Preview(
            viewW, viewH, phoneRawWidth, phoneRawHeight,
            phoneSensorNaturalRotation, angle,
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
            " sensor=" + phoneSensorNaturalRotation + " ocr=" + phoneOcrRotation +
            " display=" + displayDegrees + " previewExtra=" + angle +
            " fitted=" + fit.fittedWidth + "x" + fit.fittedHeight);
        if (ocrOverlay != null) {
            ocrOverlay.setPhoneDisplayTransform(angle,
                previewMirror, previewVerticalFlip);
        }
    }

    /** Speak a warning only after the scene remains unclear; never interrupt a page. */
    private void onReadingQuality(OcrReadResult result, FrameQuality.Assessment quality) {
        if (!qualityPromptsEnabled || !running ||
            (!longReadingMode && automaticMode)) return;
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
        realtimeScene.reset();
        lastQuickScanGeneration = -1L;
        lastQuickScanReliable = false;
        liveSpeechGuard.reset();
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
        String announcement = enableLong ? "已切换长文阅读，读完再识别" : "已切换实时文字，读完当前内容再更新";
        Toast.makeText(this, announcement, Toast.LENGTH_SHORT).show();
        if (readingModeButton != null) readingModeButton.announceForAccessibility(announcement);
    }

    /**
     * Both modes read one CURRENT OCR result; only speech scheduling differs.
     * Long-form mode keeps the complete text until all TTS chunks finish.
     */
    private void onRecognized(String found, boolean manualCapture,
                              long sceneGeneration, int resultEpoch) {
        final long now = System.currentTimeMillis();
        final String recognizedText = found == null ? "" : found.trim();
        if (recognizedText.isEmpty()) {
            if (longReadingMode && lastNonEmptyAt > 0 &&
                now - lastNonEmptyAt >= 2800) lastSpoken = "";
            return;
        }
        lastNonEmptyAt = now;
        if (!running || !canSpeak()) {
            Log.w("KanziTTS", "OCR recognized text but speech skipped: running=" +
                running + " availableVoice=" + canSpeak());
            return;
        }
        // One OCR pass feeds full, continuous text flows. Never automatically
        // crop to a guessed page or discard entire shorter lines.
        final String confirmed = recognizedText;
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
            // A scene luma fingerprint is a performance *hint*, never a
            // condition for suppressing successful recognized speech.
            if (!running || !canSpeak() ||
                (!manualCapture && !automaticMode) ||
                resultEpoch != readingEpoch.get()) return;
            queueSpeechText(fullSpeech, normalizedSpeech, manualCapture, sceneGeneration);
        });
    }

    /** Diagnostic/manual listening keeps the same explicit one-shot path. */
    private void queueSpeechText(String text, String normalized, boolean force) {
        queueSpeechText(text,normalized,force,-1L);
    }

    /** Real-time speech preempts on truly new OCR text; long mode remains continuous. */
    private void queueSpeechText(String text, String normalized, boolean force,
                                 long sceneGeneration) {
        if (!canSpeak() || normalized.isEmpty()) return;
        // Never truncate live recognition to only the first few words/lines.
        String spokenText = text;
        String spokenNormalized = normalizeOcrText(spokenText);
        if (spokenNormalized.isEmpty()) return;
        if (!force) {
            if (longReadingMode) {
                // Dedicated book mode keeps its existing one-page queue.
                if (speechPageActive || currentSpeech != null || !speechQueue.isEmpty()) return;
                if (isAlreadyQueuedOrRecentlySpoken(spokenNormalized,
                    similarityThreshold / 100.0f)) return;
            } else {
                // v56 requested rule: ONE successful OCR reading of a
                // genuinely NEW text target is enough. Do not wait for the
                // currently speaking paragraph or a second full OCR pass.
                // The text-based guard suppresses repeated/partial OCR of
                // the SAME page; changing raw camera brightness alone never
                // causes interruption.
                if (!liveSpeechGuard.accept(spokenNormalized)) {
                    Log.d("KanziTTS", "Same live text; keep current utterance");
                    return;
                }
                if (speechPageActive || speakingQueued ||
                    currentSpeech != null || !speechQueue.isEmpty()) {
                    // Stops system and downloaded offline voice, clears all
                    // queued chunks; late onDone callbacks are ignored by
                    // the utteranceId equality check in finishQueuedUtterance.
                    clearSpeechQueue(true);
                    Log.i("KanziTTS", "New recognized text preempts old speech");
                }
            }
        }
        if (force) {
            // Only an EXPLICIT user action may interrupt current speech.
            clearSpeechQueue(true);
        }
        List<String> chunks = SpeechText.chunks(spokenText);
        if (chunks.isEmpty()) return;
        speechPageActive = true;
        // Live target identity is maintained by liveSpeechGuard.
        for (String chunk : chunks) {
            speechQueue.addLast(new SpeechItem(chunk, spokenNormalized, "ocr-" + (++utteranceSequence)));
        }
        // No success beep. The only automatic audio is the text itself.
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
        if (!canSpeak() || speakingQueued || speechQueue.isEmpty()) return;
        currentSpeech = speechQueue.pollFirst();
        speakingQueued = true;
        lastSpoken = currentSpeech.normalized;
        // Always flush the system's previous utterance for the FIRST chunk
        // of a newly confirmed scan target. Subsequent chunks only start after
        // onDone; flushing here cannot remove the rest of our own Java queue.
        DownloadableVoiceStore.Voice voice =
            voiceStore == null ? DownloadableVoiceStore.SYSTEM : voiceStore.selectedVoice();
        if (!voice.isSystem() && voiceStore.isInstalled(voice) && offlineVoicePlayback != null) {
            final SpeechItem item = currentSpeech;
            offlineVoicePlayback.speak(voice, item.text, speechRate, item.utteranceId,
                new OfflineVoicePlayback.Callback() {
                    @Override public void completed(String utteranceId) {
                        mainHandler.post(() -> finishQueuedUtterance(utteranceId));
                    }
                    @Override public void failed(String utteranceId, String reason) {
                        // Offline synthesis finishes on a worker. Queue state,
                        // user notices and TTS fallback must run on UI thread.
                        mainHandler.post(() -> {
                            if (currentSpeech == null ||
                                !currentSpeech.utteranceId.equals(utteranceId)) return;
                            Log.e("KanziTTS", "Offline speech failed, switching to system: " + reason);
                            voiceStore.forceSystem();
                            if (offlineVoicePlayback != null)
                                offlineVoicePlayback.releaseModelWhenIdle();
                            showSpeechFailure("离线自然语音暂不可用，已自动切回系统中文语音");
                            speakSystemItem(item);
                        });
                    }
                });
        } else {
            speakSystemItem(currentSpeech);
        }
    }

    private void speakSystemItem(SpeechItem item) {
        if (tts == null || !ttsReady || item == null) {
            if (item != null) finishQueuedUtterance(item.utteranceId);
            return;
        }
        int result = tts.speak(item.text, TextToSpeech.QUEUE_FLUSH, null,
            item.utteranceId);
        if (result == TextToSpeech.ERROR) {
            showSpeechFailure("系统语音播放失败，请检查中文语音引擎");
            finishQueuedUtterance(item.utteranceId);
        }
    }

    /** A downloaded offline model does not need a system TTS voice. */
    private boolean canSpeak() {
        if (ttsReady && tts != null) return true;
        if (voiceStore == null || offlineVoicePlayback == null) return false;
        DownloadableVoiceStore.Voice voice = voiceStore.selectedVoice();
        return !voice.isSystem() && voiceStore.isInstalled(voice);
    }

    private String voiceRowLabel(DownloadableVoiceStore.Voice voice) {
        boolean chosen = voice.id.equals(voiceStore.selectedId());
        String suffix = voice.isSystem() ? "已就绪" :
            (voiceStore.isInstalled(voice) ? "已安装 · 点击切换" :
                "未安装 · 点击自动下载安装");
        String size = voice.isSystem() ? "" : ("（约 " + voice.approxMb + " MB）");
        return (chosen ? "✓ " : "○ ") + voice.title + size + "　" + suffix;
    }

    private void showSpeechFailure(String message) {
        long now = System.currentTimeMillis();
        if (now - lastSpeechFailureNoticeAt < 8000L) return;
        lastSpeechFailureNoticeAt = now;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
        if (stopCurrent && offlineVoicePlayback != null) offlineVoicePlayback.stop();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SINGLE_FRAME_ZIP || resultCode != Activity.RESULT_OK ||
            data == null || data.getData() == null) return;
        final android.net.Uri destination = data.getData();
        final java.io.File source = recognizer == null ? null : recognizer.latestEvidenceZip();
        if (source == null) {
            Toast.makeText(this, "诊断包已不存在，请重新采集", Toast.LENGTH_LONG).show();
            return;
        }
        // The app never uploads any captured image. Export only after the
        // Android user chooses the precise destination via SAF.
        new Thread(() -> {
            try (java.io.FileInputStream from = new java.io.FileInputStream(source);
                 java.io.OutputStream to = getContentResolver().openOutputStream(destination, "w")) {
                if (to == null) throw new java.io.IOException("无法打开选择的保存位置");
                byte[] buffer = new byte[16384];
                int n;
                while ((n = from.read(buffer)) != -1) to.write(buffer, 0, n);
                to.flush();
                runOnUiThread(() -> Toast.makeText(this,
                    "单帧 ZIP 已保存到你选择的位置；分享前请检查其中可能包含的私人文字",
                    Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e("KanziDiag", "SAF ZIP export failed", e);
                runOnUiThread(() -> Toast.makeText(this,
                    "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "kanzi-single-frame-export").start();
    }

    @Override protected void onPause() {
        // Accessibility safety: a camera that is no longer foreground must
        // neither scan nor continue speaking private surrounding text.
        running = false;
        readingEpoch.incrementAndGet();
        manualCaptureRequested.set(false);
        realtimeScene.reset();
        frameChangeGate.reset();
        lastQuickScanGeneration = -1L;
        clearSpeechQueue(true);
        if (ocrOverlay != null) ocrOverlay.clear();
        updateControlLabels();
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
        if (offlineVoicePlayback != null) offlineVoicePlayback.shutdown();
        if (voiceStore != null) voiceStore.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (recognizer != null) recognizer.release();
        diagnosticHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
