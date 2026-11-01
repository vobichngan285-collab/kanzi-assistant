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
    private static final String APP_VERSION = "0.8.0";
    private static final String PREF_NO_ENGLISH = "speech_no_english";
    private static final String PREF_NO_NUMBERS = "speech_no_numbers";
    private static final String PREF_MAX_SPEECH_CHARS = "speech_max_chars";
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
    private static final String PREF_OCR_MODEL = "ocr_model_id";
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
            if (data == null || format != DataFormat.NV21) return;
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
                final int rotationForDiagnostic = previewRotation;
                recognizer.diagnoseNv21(diagnosticFrame, width, height, rotationForDiagnostic, enhancedOcr,
                    report -> {
                        busy.set(false);
                        String result = "诊断完成\n摄像头帧：已获取 " + width + "×" + height
                            + "\nOCR 引擎：已就绪\n检测框数量：" + report.getBoxCount()
                            + "\n平均置信度：" + String.format(Locale.US, "%.3f", report.getAverageConfidence())
                            + "\n检测耗时：" + report.getDetectionTimeMs() + " ms"
                            + "\n识别耗时：" + report.getRecognitionTimeMs() + " ms"
                            + "\n总耗时：" + report.getTotalTimeMs() + " ms"
                            + "\n诊断总用时：" + (System.currentTimeMillis() - started) + " ms"
                            + "\n识别结果（低置信度及纯符号片段已过滤）：\n" + (report.getText().isEmpty() ? "（未识别到文字）" : report.getText());
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
            final boolean enhance = enhancedOcr;
            try {
                recognizer.recognizeNv21(frame, width, height, rotation, enhance, found -> {
                    busy.set(false);
                    if (found != null) onRecognized(found);
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
    };

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

        modeButton = createControlButton("模式：自动");
        controls.addView(modeButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        captureButton = createControlButton("识别一次");
        controls.addView(captureButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        startButton = createControlButton("开始识别");
        controls.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        settingsButton = createControlButton("设置");
        controls.addView(settingsButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(62), Gravity.BOTTOM
        );
        root.addView(controls, controlsParams);
        controls.bringToFront();
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

        transformNote.setText("画面始终按摄像头实际比例等比显示；旋转 90°/270°后完整显示，不裁切、不拉伸，屏幕比例不一致时保留黑边。镜像和翻转只影响预览，不会把文字镜像后送入 OCR。OCR 使用原始摄像头帧并单独校正旋转方向。");
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
        maxCharsTitle.setText("单次最多朗读字符数（0 表示不限）");
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
            if (!previewCallbackAdded) {
                diagnosticReportView.setText("诊断失败：尚未检测到外接摄像头预览回调。请检查 USB 连接和摄像头权限。");
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
                int result = tts.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, "diagnostic-read-" + (++utteranceSequence));
                if (result == TextToSpeech.ERROR) Toast.makeText(this, "语音引擎拒绝了这次朗读请求", Toast.LENGTH_SHORT).show();
            }
        });
        panel.addView(speakDiagnosticButton);

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
                speechRate = 0.5f + speechRateSeek.getProgress() / 100f;
                speechNoEnglish = noEnglishCheck.isChecked();
                speechNoNumbers = noNumbersCheck.isChecked();
                try { maxSpeechChars = Math.max(0, Math.min(5000, Integer.parseInt(maxCharsInput.getText().toString().trim()))); }
                catch (NumberFormatException ignored) { maxSpeechChars = 0; }
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
                    .putString(PREF_OCR_MODEL, ocrModelId)
                    .putBoolean(PREF_NO_ENGLISH, speechNoEnglish)
                    .putBoolean(PREF_NO_NUMBERS, speechNoNumbers)
                    .putInt(PREF_MAX_SPEECH_CHARS, maxSpeechChars)
                    .apply();
                if (wasQueueEnabled && !queueSpeech) clearSpeechQueue(true);
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
            } else {
                ttsReady = false;
                status.setText("系统中文语音引擎初始化失败，状态码：" + code);
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
        String speechText = recognizedText;
        if (speechNoEnglish) speechText = speechText.replaceAll("[A-Za-z]+", " ");
        if (speechNoNumbers) speechText = speechText.replaceAll("[0-9]+", " ");
        speechText = speechText.replaceAll("\\s+", " ").trim();
        if (maxSpeechChars > 0 && speechText.length() > maxSpeechChars)
            speechText = speechText.substring(0, maxSpeechChars).trim();
        if (speechText.length() < 1) return;
        final String filteredSpeechText = speechText;

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
        final String textToSpeak = filteredSpeechText;
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
        int result = tts.speak(currentSpeech.text, TextToSpeech.QUEUE_ADD, null,
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
