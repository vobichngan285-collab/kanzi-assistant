package com.vobich.kanzi.craftprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * STANDALONE manual Android benchmark, isolated from installed 看字助手.
 * No INTERNET permission, no OCR recognizer, no automatic TTS, no camera streaming.
 * Camera uses the system image-capture intent; gallery via system document picker.
 * All inference runs locally and results only leave the phone if user exports them.
 */
public class ProbeActivity extends Activity {
    private static final String TAG = "CRAFT768INT8Lab";
    private static final String MODEL_ASSET = "craft768_int8.onnx";
    private static final long MODEL_BYTES = 20_946_608L;
    private static final String MODEL_SHA = "9ab885d2f6669b77f70e9c8f68acfbe408c0b051501fd02d94bcaa0c7779ab15";
    private static final int REQUEST_GALLERY = 101;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_SAVE_JSON = 103;
    private static final int LONG_SIDE = 768;
    private static final int FRAME_W = 1920;
    private static final int FRAME_H = 1080;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final List<JSONObject> tests = new ArrayList<>();

    private TextView status;
    private ImageView preview;
    private List<CraftBoxDecoder.Box> currentBoxes = Collections.emptyList();
    private OrtEnvironment environment;
    private OrtSession model;
    private String inputName;
    private Bitmap frame;
    private String frameKind = "内置模拟文字（只用于测速）";
    private String frameSourceSize = "1920x1080 generated";
    private Uri pendingCameraUri;
    private TextToSpeech tts;

    private long copiedModelMs;
    private long shaVerifiedMs;
    private long sessionLoadedMs;
    private long nativePssAfterLoadKb;
    private long nativeHeapAfterLoadKb;
    private String lastReport = "{}";

    interface CheckedAction { void run() throws Exception; }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        scroll.addView(layout);

        TextView title = new TextView(this);
        title.setText("CRAFT 768 INT8 真机性能实验");
        title.setTextSize(23);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 6, 0, 12);
        layout.addView(title);

        TextView intro = new TextView(this);
        intro.setText("独立安装，不替换看字助手。离线测试20.95MB量化模型，固定768检测输入。\n" +
                "新版保留真实照片的横竖方向和宽高比，不再强制横向1920×1080留白。" +
                "可查看CRAFT文字检测框，但不包含百度飞桨Small文字识别及完整语音朗读。");
        intro.setTextSize(17);
        layout.addView(intro);

        addButton(layout, "① 校验并加载 INT8 模型", () -> background("模型校验与加载", this::loadModel));
        addButton(layout, "② 使用内置1920×1080模拟文字图", () -> background("准备模拟图", () -> {
            Bitmap built = demoFrame();
            replaceFrame(built, "内置模拟文字，仅做性能测试", "1920x1080");
            note("模拟文字画面已准备。不能根据它判断真实文字准确率。");
        }));
        addButton(layout, "③ 从相册选真实图片", this::pickGallery);
        addButton(layout, "④ 使用系统相机拍摄真实图片", this::pickCamera);
        addButton(layout, "⑤ 当前图片单次推理", () -> background("单次推理", () -> runRepeated(1)));
        addButton(layout, "⑥ 当前图片连续24次推理（含两次预热）", () -> background("连续24次推理", () -> runRepeated(24)));
        addButton(layout, "⑦ 可选：独立试听系统语音（非OCR朗读）", this::testTts);
        addButton(layout, "⑧ 导出匿名性能数据 JSON", this::exportJson);
        addButton(layout, "⑨ 当前图片重建并显示文字检测框", () ->
                background("文字框重建", this::runBoxPreview));

        status = new TextView(this);
        status.setTextSize(17);
        status.setTextColor(Color.BLACK);
        status.setTextIsSelectable(true);
        status.setPadding(0, 20, 0, 38);
        status.setText("准备就绪。\n先加载模型，再拍照/选图，执行单次或连续测试。\n" +
                "推荐纸面小字、包装、菜单、复杂背景三类图片分别测试。\n" +
                "本工具不修改系统语音引擎设置。");
        layout.addView(status);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setContentDescription("已选照片与CRAFT检测框预览，黄色轮廓表示找到的候选文字区域");
        preview.setMinimumHeight((int)(120*getResources().getDisplayMetrics().density));
        layout.addView(preview);
        setContentView(scroll);
    }

    private void addButton(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setContentDescription(label);
        button.setOnClickListener(v -> action.run());
        root.addView(button);
    }

    private void note(String message) {
        Log.i(TAG, message);
        ui.post(() -> {
            String existing = status.getText().toString();
            if (existing.length() > 5000) existing = existing.substring(0, 5000);
            status.setText(message + "\n\n" + existing);
            status.announceForAccessibility(message);
        });
    }

    private void background(String label, CheckedAction task) {
        if (!busy.compareAndSet(false, true)) {
            Toast.makeText(this, "正在运行其它测试", Toast.LENGTH_SHORT).show();
            return;
        }
        note(label + "：执行中…");
        executor.execute(() -> {
            try { task.run(); }
            catch (Throwable error) {
                Log.e(TAG, label + " failed", error);
                note(label + "失败：" + error.getClass().getSimpleName() + "：" + error.getMessage());
            } finally {
                busy.set(false);
            }
        });
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }

    private void loadModel() throws Exception {
        if (model != null) {
            note("模型已经加载。再次测速度直接使用⑤或⑥。");
            return;
        }
        long started = SystemClock.elapsedRealtimeNanos();
        File file = new File(getFilesDir(), MODEL_ASSET);
        if (!file.isFile() || file.length() != MODEL_BYTES || !MODEL_SHA.equals(sha256(file))) {
            File temp = new File(getFilesDir(), MODEL_ASSET + ".tmp");
            try (InputStream in = getAssets().open(MODEL_ASSET);
                 OutputStream out = new FileOutputStream(temp)) {
                byte[] bytes = new byte[65536];
                int n;
                while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            }
            if (!temp.renameTo(file)) throw new IllegalStateException("无法写入应用内部模型文件");
        }
        copiedModelMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L;

        long checkStart = SystemClock.elapsedRealtimeNanos();
        if (file.length() != MODEL_BYTES || !MODEL_SHA.equals(sha256(file)))
            throw new SecurityException("本地CRAFT INT8模型校验失败，不执行模型");
        shaVerifiedMs = (SystemClock.elapsedRealtimeNanos() - checkStart) / 1_000_000L;

        environment = OrtEnvironment.getEnvironment();
        long loadStarted = SystemClock.elapsedRealtimeNanos();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            model = environment.createSession(file.getAbsolutePath(), options);
            if (model.getInputNames().size() != 1) {
                model.close(); model = null;
                throw new IllegalStateException("CRAFT模型输入数量不正确");
            }
            inputName = model.getInputNames().iterator().next();
            TensorInfo ti = (TensorInfo) model.getInputInfo().get(inputName).getInfo();
            long[] shape = ti.getShape();
            if (shape.length != 4 || shape[1] != 3) {
                model.close(); model = null;
                throw new IllegalStateException("CRAFT输入张量维度不正确");
            }
        }
        sessionLoadedMs = (SystemClock.elapsedRealtimeNanos() - loadStarted) / 1_000_000L;
        nativePssAfterLoadKb = Debug.getPss();
        nativeHeapAfterLoadKb = Debug.getNativeHeapAllocatedSize() / 1024L;
        JSONObject entry = new JSONObject();
        entry.put("kind", "model_load");
        entry.put("modelSha256", MODEL_SHA);
        entry.put("modelBytes", MODEL_BYTES);
        entry.put("modelPreparationMs", copiedModelMs);
        entry.put("shaVerificationMs", shaVerifiedMs);
        entry.put("ortSessionLoadMs", sessionLoadedMs);
        entry.put("pssAfterLoadKb", nativePssAfterLoadKb);
        entry.put("nativeHeapAfterLoadKb", nativeHeapAfterLoadKb);
        addReport(entry);
        note("模型校验与加载成功。\nONNX加载：" + sessionLoadedMs + "ms，" +
                "首次文件准备：" + copiedModelMs + "ms；检查SHA：" + shaVerifiedMs +
                "ms；PSS：" + nativePssAfterLoadKb + "KB。");
    }

    private static long millis(long nanos) { return Math.round(nanos / 1_000_000.0); }

    private static class Inference {
        long preprocessMs;
        long forwardMs;
        long totalMs;
        int inWidth;
        int inHeight;
        float sample;
        long boxPostprocessingMs;
        int rawComponents;
        int survivingComponents;
        List<CraftBoxDecoder.Box> boxes = Collections.emptyList();
    }

    private Inference one(Bitmap src) throws Exception {
        return one(src, false);
    }

    private Inference one(Bitmap src, boolean reconstructBoxes) throws Exception {
        if (model == null) throw new IllegalStateException("模型尚未加载");
        long start = SystemClock.elapsedRealtimeNanos();
        double scale = Math.min(1.0, (double) LONG_SIDE / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.min(LONG_SIDE, Math.max(32, (int) Math.ceil(src.getWidth() * scale / 32.0) * 32));
        int h = Math.min(LONG_SIDE, Math.max(32, (int) Math.ceil(src.getHeight() * scale / 32.0) * 32));
        Bitmap scaled = Bitmap.createScaledBitmap(src, w, h, true);
        int[] pixels = new int[w * h];
        scaled.getPixels(pixels, 0, w, 0, 0, w, h);
        if (scaled != src) scaled.recycle();
        int plane = w * h;
        float[] values = new float[plane * 3];
        for (int i = 0; i < pixels.length; i++) {
            int pix = pixels[i];
            values[i] = (((pix >>> 16) & 255) / 255f - .485f) / .229f;
            values[plane + i] = (((pix >>> 8) & 255) / 255f - .456f) / .224f;
            values[2 * plane + i] = ((pix & 255) / 255f - .406f) / .225f;
        }
        Inference result = new Inference();
        result.inWidth = w;
        result.inHeight = h;
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(values), new long[]{1L,3L,h,w})) {
            long startedForward = SystemClock.elapsedRealtimeNanos();
            result.preprocessMs = millis(startedForward - start);
            try (OrtSession.Result raw = model.run(Collections.singletonMap(inputName, tensor))) {
                OnnxTensor output = (OnnxTensor) raw.get(0);
                TensorInfo info = (TensorInfo) output.getInfo();
                long[] shape = info.getShape();
                if (shape.length != 4 || shape[0] != 1 || shape[3] != 2)
                    throw new IllegalStateException("CRAFT输出形状错误");
                result.sample = output.getFloatBuffer().get(0);
                if (!Float.isFinite(result.sample))
                    throw new IllegalStateException("CRAFT输出出现NaN或无穷");
                result.forwardMs = millis(SystemClock.elapsedRealtimeNanos() - startedForward);
                if (reconstructBoxes) {
                    long boxStart = SystemClock.elapsedRealtimeNanos();
                    CraftBoxDecoder.Result decoded = CraftBoxDecoder.decode(
                            output.getFloatBuffer(), (int)shape[1], (int)shape[2],
                            src.getWidth(), src.getHeight(), w, h);
                    result.boxes = decoded.boxes;
                    result.rawComponents = decoded.rawComponents;
                    result.survivingComponents = decoded.survivingComponents;
                    result.boxPostprocessingMs = millis(SystemClock.elapsedRealtimeNanos() - boxStart);
                }
            }
        }
        result.totalMs = millis(SystemClock.elapsedRealtimeNanos() - start);
        return result;
    }

    private static double p(List<Long> values, double q) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get((int) Math.round((sorted.size() - 1) * q));
    }

    private int batteryTemperatureC10() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
    }

    private int thermalStatus() {
        if (Build.VERSION.SDK_INT < 29) return -1;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm == null ? -1 : pm.getCurrentThermalStatus();
    }

    private void runRepeated(int count) throws Exception {
        if (model == null) throw new IllegalStateException("请先按①加载模型");
        if (frame == null) replaceFrame(demoFrame(), "内置模拟文字，仅做性能测试", "1920x1080");
        Bitmap src = frame; // Fixed source frame; not a live camera stream.
        if (src == null || src.isRecycled()) throw new IllegalStateException("没有可用图片");
        int tempStart = batteryTemperatureC10();
        int thermalStart = thermalStatus();
        List<Long> inferenceTimes = new ArrayList<>();
        List<Long> totals = new ArrayList<>();
        List<Long> preprocessTimes = new ArrayList<>();
        int inW = 0, inH = 0;
        if (count > 1) { one(src); one(src); }
        for (int i = 0; i < count; i++) {
            Inference measure = one(src);
            inferenceTimes.add(measure.forwardMs);
            preprocessTimes.add(measure.preprocessMs);
            totals.add(measure.totalMs);
            inW = measure.inWidth;
            inH = measure.inHeight;
            if (count > 1 && (i+1) % 6 == 0)
                note("连续测试进行中：" + (i+1) + "/" + count + "次");
        }

        int tempEnd = batteryTemperatureC10();
        int thermalEnd = thermalStatus();
        JSONObject entry = new JSONObject();
        entry.put("kind", "benchmark");
        entry.put("modelSha256", MODEL_SHA);
        entry.put("imageKind", frameKind);
        entry.put("sourceImageDimensions", frameSourceSize);
        entry.put("benchmarkFrame", src.getWidth() + "x" + src.getHeight());
        entry.put("preservesSourceOrientation", true);
        entry.put("forced1920x1080Letterbox", false);
        entry.put("fixedImageRepeatedNotCameraStreaming", true);
        entry.put("frames", count);
        entry.put("warmupFrames", count > 1 ? 2 : 0);
        entry.put("craftInputWidth", inW);
        entry.put("craftInputHeight", inH);
        entry.put("inferenceP50Ms", p(inferenceTimes, .50));
        entry.put("inferenceP95Ms", p(inferenceTimes, .95));
        entry.put("preprocessingP50Ms", p(preprocessTimes, .50));
        entry.put("totalForwardAndPreprocessingP50Ms", p(totals, .50));
        entry.put("totalForwardAndPreprocessingP95Ms", p(totals, .95));
        entry.put("pssKb", Debug.getPss());
        entry.put("nativeHeapKb", Debug.getNativeHeapAllocatedSize() / 1024L);
        entry.put("batteryTemperatureStartC10", tempStart);
        entry.put("batteryTemperatureEndC10", tempEnd);
        entry.put("androidThermalStatusStart", thermalStart);
        entry.put("androidThermalStatusEnd", thermalEnd);
        entry.put("smallRECIncluded", false);
        entry.put("craftTextBoxesIncluded", false);
        entry.put("ttsIncluded", false);
        addReport(entry);
        note(count + "次测试完成（同一张图片重复运行，不是实时摄像头流）。\n" +
                "图像：" + frameKind + "；" + src.getWidth() + "×" + src.getHeight() +
                "→CRAFT " + inW + "×" + inH +
                "\n纯模型P50：" + entry.get("inferenceP50Ms") + "ms，P95：" + entry.get("inferenceP95Ms") +
                "ms\n前处理+推理P50：" + entry.get("totalForwardAndPreprocessingP50Ms") +
                "ms，P95：" + entry.get("totalForwardAndPreprocessingP95Ms") + "ms" +
                "\n进程PSS：" + entry.get("pssKb") + "KB；电池温度：" +
                (tempStart < 0 ? "未知" : (tempStart / 10.0) + "℃") + " → " +
                (tempEnd < 0 ? "未知" : (tempEnd / 10.0) + "℃") +
                "；Android热状态：" + thermalStart + "→" + thermalEnd +
                "\n注意：未包含文字框重建、Small REC及语音朗读。");
    }

    private synchronized void addReport(JSONObject entry) throws Exception {
        entry.put("timestampEpochMs", System.currentTimeMillis());
        tests.add(entry);
        JSONObject root = new JSONObject();
        root.put("title", "看字助手 CRAFT768 INT8 独立真机性能实验");
        root.put("deviceModel", Build.MODEL); // user-exported file only
        root.put("deviceManufacturer", Build.MANUFACTURER);
        root.put("androidRelease", Build.VERSION.RELEASE);
        root.put("apiLevel", Build.VERSION.SDK_INT);
        root.put("supportedAbis", new JSONArray(Build.SUPPORTED_ABIS));
        root.put("appPackage", getPackageName());
        root.put("modelSha256", MODEL_SHA);
        root.put("privacy", "No image content or actual photo path exported. No network permission.");
        root.put("caveat", "Orientation-preserving CRAFT INT8 inference; optional text box visualization is approximate Java CRAFT geometry, not OCR transcription. No Small REC/TTS first word. Camera uses ACTION_IMAGE_CAPTURE.");
        root.put("measurements", new JSONArray(tests));
        lastReport = root.toString(2);
    }

    private void replaceFrame(Bitmap next, String kind, String sourceDimensions) {
        Bitmap old = frame;
        frame = next;
        frameKind = kind;
        frameSourceSize = sourceDimensions;
        currentBoxes = Collections.emptyList();
        if (preview != null && next != null) {
            Bitmap localPreview = drawPreview(next, currentBoxes);
            ui.post(() -> preview.setImageBitmap(localPreview));
        }
        if (old != null && old != next && !old.isRecycled()) old.recycle();
    }

    private Bitmap drawPreview(Bitmap original, List<CraftBoxDecoder.Box> boxes) {
        final int maxSide = 1200;
        float scale = Math.min(1f, maxSide / (float)Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, Math.round(original.getWidth() * scale));
        int height = Math.max(1, Math.round(original.getHeight() * scale));
        Bitmap resized = Bitmap.createScaledBitmap(original, width, height, true);
        Bitmap annotated = resized.copy(Bitmap.Config.ARGB_8888, true);
        if (resized != original) resized.recycle();
        Canvas canvas = new Canvas(annotated);
        Paint outline = new Paint(3);
        outline.setColor(Color.YELLOW);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(Math.max(2f, width/250f));
        outline.setShadowLayer(2f, 1f, 1f, Color.BLACK);
        float sx = width/(float)original.getWidth();
        float sy = height/(float)original.getHeight();
        for (CraftBoxDecoder.Box box : boxes) {
            float[] q = box.quad;
            Path path = new Path();
            path.moveTo(q[0]*sx, q[1]*sy);
            for (int i=1; i<4; ++i) path.lineTo(q[2*i]*sx,q[2*i+1]*sy);
            path.close();
            canvas.drawPath(path, outline);
        }
        return annotated;
    }

    private void runBoxPreview() throws Exception {
        if (model == null) throw new IllegalStateException("请先按①加载模型");
        Bitmap src=frame;
        if (src==null || src.isRecycled())
            throw new IllegalStateException("请先选择一张真实照片或模拟测试图");
        Inference infer=one(src,true);
        currentBoxes = infer.boxes;
        Bitmap overlay=drawPreview(src,currentBoxes);
        ui.post(() -> preview.setImageBitmap(overlay));
        JSONObject report = new JSONObject();
        report.put("kind","text_box_visualization");
        report.put("modelSha256",MODEL_SHA);
        report.put("imageKind",frameKind);
        report.put("sourceImageDimensions",frameSourceSize);
        report.put("preservesSourceOrientation",true);
        report.put("craftInputWidth",infer.inWidth);
        report.put("craftInputHeight",infer.inHeight);
        report.put("rawComponents",infer.rawComponents);
        report.put("survivingComponents",infer.survivingComponents);
        report.put("displayedBoxes",infer.boxes.size());
        report.put("preprocessingMs",infer.preprocessMs);
        report.put("forwardMs",infer.forwardMs);
        report.put("boxPostprocessingMs",infer.boxPostprocessingMs);
        report.put("totalMs",infer.totalMs);
        report.put("pssKb",Debug.getPss());
        report.put("noTextTranscription",true);
        report.put("geometryDecoder","pure-Java component/dilation/minAreaRect equivalent, visualization-only");
        // Avoid exporting photo pixels, actual text, or box coordinates.
        addReport(report);
        note("文字框重建完成：找到 "+infer.boxes.size()+
                " 个候选框（初始连通区域 "+infer.rawComponents+" 个）。"+
                "\n图片 "+src.getWidth()+"×"+src.getHeight()+" → 输入 "+
                infer.inWidth+"×"+infer.inHeight+
                "\nCRAFT推理 "+infer.forwardMs+"ms；文字框后处理 "+
                infer.boxPostprocessingMs+"ms；总计 "+infer.totalMs+"ms。"+
                "\n下方黄框只表示可能含文字，尚未做真正的汉字内容识别。");
    }

    private Bitmap demoFrame() {
        Bitmap next = Bitmap.createBitmap(FRAME_W, FRAME_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(next);
        c.drawColor(Color.WHITE);
        Paint paint = new Paint(3);
        paint.setColor(Color.BLACK);
        paint.setTextSize(67f);
        for (int i = 0; i < 7; i++) {
            paint.setTextSize(Math.max(18f, 67f-i*7f));
            c.drawText("看字助手 · 模型推理测试 " + (i+1) + " ABC 123", 65, 150 + i*130, paint);
        }
        return next;
    }

    private void pickGallery() {
        if (busy.get()) { note("请先结束当前推理"); return; }
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        startActivityForResult(pick, REQUEST_GALLERY);
    }

    private void pickCamera() {
        if (busy.get()) { note("请先结束当前推理"); return; }
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法建立拍照缓存目录");
            File photo = new File(dir, "craft-test-" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("CRAFT test photo", pendingCameraUri));
            if (intent.resolveActivity(getPackageManager()) == null)
                throw new IllegalStateException("手机没有提供兼容的系统拍照应用；请用相册选图");
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (Exception failure) {
            note("无法打开相机：" + failure.getMessage());
        }
    }

    private Bitmap readPhoto(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw new IllegalArgumentException("不支持的图片内容");
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 2600) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap decoded;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(in, null, opts);
        }
        if (decoded == null) throw new IllegalArgumentException("图片解码失败");
        int rotation = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
        } catch (Exception ignored) { /* Some image formats have no EXIF. */ }
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap corrected = Bitmap.createBitmap(decoded, 0, 0,
                    decoded.getWidth(), decoded.getHeight(), m, true);
            if (corrected != decoded) decoded.recycle();
            decoded = corrected;
        }
        return decoded;
    }

    private Bitmap letterbox1080(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(FRAME_W, FRAME_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        float ratio = Math.min(FRAME_W / (float) src.getWidth(), FRAME_H / (float) src.getHeight());
        float w = src.getWidth() * ratio, h = src.getHeight() * ratio;
        canvas.drawBitmap(src, null,
                new RectF((FRAME_W-w)/2f, (FRAME_H-h)/2f, (FRAME_W+w)/2f, (FRAME_H+h)/2f),
                new Paint(3));
        return out;
    }

    private void loadImage(Uri uri, String kind) {
        background("读取真实图片", () -> {
            Bitmap decoded = readPhoto(uri);
            String dims = decoded.getWidth() + "x" + decoded.getHeight();
            replaceFrame(decoded, kind, dims);
            note(kind + "：已保留原图方向和宽高比 (" + dims + ")，"+
                    "不再强制横屏留白。\n现在可运行⑤、⑥或⑨。图片只保存在本机，不上传。");
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK) { if (request != REQUEST_SAVE_JSON) note("已取消操作"); return; }
        if (request == REQUEST_GALLERY) {
            if (data != null && data.getData() != null)
                loadImage(data.getData(), "相册真实照片");
        } else if (request == REQUEST_CAMERA) {
            if (pendingCameraUri != null)
                loadImage(pendingCameraUri, "手机系统相机拍摄照片");
        } else if (request == REQUEST_SAVE_JSON) {
            if (data == null || data.getData() == null) return;
            Uri target = data.getData();
            background("导出数据", () -> {
                try (OutputStream out = getContentResolver().openOutputStream(target, "w")) {
                    if (out == null) throw new IllegalStateException("无法写入选定文档");
                    out.write(lastReport.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                note("JSON性能数据已导出。可通过聊天上传该文件供分析（不含原始图片）。");
            });
        }
    }

    private void exportJson() {
        if (busy.get()) { note("请先完成正在进行的测试"); return; }
        if (tests.isEmpty()) { note("还没有数据，请先校验模型并运行一次测试"); return; }
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.setType("application/json");
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.putExtra(Intent.EXTRA_TITLE, "craft768_int8_phone_results.json");
        startActivityForResult(save, REQUEST_SAVE_JSON);
    }

    private void testTts() {
        if (busy.get()) { note("请先完成正在进行的测试"); return; }
        note("系统语音测试为独立通道，只播报固定短句，不代表OCR首字延迟，也不修改默认语音引擎。");
        if (tts != null) {
            speakFixedPhrase();
            return;
        }
        tts = new TextToSpeech(getApplicationContext(), statusCode -> {
            if (statusCode == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                speakFixedPhrase();
            } else note("系统TTS初始化失败（状态" + statusCode + "），不影响OCR测试。");
        });
    }

    private void speakFixedPhrase() {
        final long requested = SystemClock.elapsedRealtimeNanos();
        final String id = "CRAFT_LAB_" + requested;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                if (!id.equals(utteranceId)) return;
                long callbackMs = millis(SystemClock.elapsedRealtimeNanos() - requested);
                note("TTS引擎开始回调：" + callbackMs +
                        "ms（不是扬声器实际第一字的声学延迟）。");
                try {
                    JSONObject entry = new JSONObject();
                    entry.put("kind", "tts_fixed_phrase");
                    entry.put("engineStartCallbackMs", callbackMs);
                    entry.put("actualAudioFirstWordMeasured", false);
                    entry.put("associatedWithOcrText", false);
                    addReport(entry);
                } catch (Exception ex) { Log.e(TAG, "tts report", ex); }
            }
            @Override public void onDone(String utteranceId) { }
            @Override public void onError(String utteranceId) { note("语音引擎播报失败"); }
        });
        tts.speak("看字助手，语音测试完成。", TextToSpeech.QUEUE_FLUSH, null, id);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        executor.shutdown();
        // Session is process-private and cannot mutate existing app.
        if (model != null && executor.isTerminated()) {
            try { model.close(); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }
}
