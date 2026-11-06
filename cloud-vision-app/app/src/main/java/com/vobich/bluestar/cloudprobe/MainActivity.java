package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.InputType;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.camera.CameraUVC;
import com.jiangdg.ausbc.camera.bean.CameraRequest;
import com.jiangdg.ausbc.callback.ICameraStateCallBack;
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack;
import com.jiangdg.ausbc.callback.IPreviewDataCallBack;
import com.jiangdg.ausbc.widget.AspectRatioTextureView;
import com.jiangdg.usb.USBMonitor;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Independent USB single-shot / cloud-task UI probe. NO local OCR models.
 * Phase 0.1 uses an explicitly labelled mock response; never claims AI output.
 * No network permission, no images saved, and no API key in this APK.
 */
public final class MainActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 4101;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicLong rawFrames = new AtomicLong();

    private volatile Frame latestFrame;
    private volatile boolean visible;
    private boolean volumeKeyTestEnabled;
    private volatile boolean speechReady;
    private volatile long lastAcceptedTap;
    private MultiCameraClient client;
    private MultiCameraClient.ICamera camera;
    private TextToSpeech tts;
    private AspectRatioTextureView preview;
    private TextView status;
    private TextView frameStatus;
    private TextView taskDescription;
    private Spinner taskSpinner;
    private List<TaskCatalog.Task> tasks;
    private TaskCatalog.Task selectedTask;
    private Future<?> pending;
    private long lastObservedFrames;
    private long lastObservedAt;

    private static final class Frame {
        final byte[] nv21;
        final int width, height;
        final long timestampMs;
        Frame(byte[] nv21, int width, int height, long timestampMs) {
            this.nv21 = nv21; this.width = width; this.height = height;
            this.timestampMs = timestampMs;
        }
    }

    private final IPreviewDataCallBack frameCallback = new IPreviewDataCallBack() {
        @Override public void onPreviewData(byte[] bytes, int width, int height, DataFormat format) {
            if (!visible || format != DataFormat.NV21 || bytes == null ||
                width < 2 || height < 2 ||
                bytes.length != (long) width * height * 3L / 2L) return;
            // Pinned AUSBC allocates an independent ByteArray per native callback.
            // Simply keep latest reference: no repeated frame copy or OCR inference.
            latestFrame = new Frame(bytes, width, height, SystemClock.elapsedRealtime());
            rawFrames.incrementAndGet();
        }
    };

    private final Runnable healthTicker = new Runnable() {
        @Override public void run() {
            if (isFinishing() || !visible) return;
            long now = SystemClock.elapsedRealtime();
            long count = rawFrames.get();
            long delta = Math.max(1L, now - lastObservedAt);
            double fps = (count - lastObservedFrames) * 1000.0 / delta;
            lastObservedAt = now; lastObservedFrames = count;
            Frame frame = latestFrame;
            String age = frame == null ? "尚无图像" :
                String.format(Locale.US, "%d毫秒前", now - frame.timestampMs);
            frameStatus.setText(String.format(Locale.CHINA,
                "USB 原始回调 %.1f次/秒 · 最新帧：%s", fps, age));
            ui.postDelayed(this, 1000L);
        }
    };

    private final IDeviceConnectCallBack usbCallback = new IDeviceConnectCallBack() {
        @Override public void onAttachDev(UsbDevice device) { requestUsbDevice(device); }
        @Override public void onDetachDec(UsbDevice device) {
            if (camera != null && device != null &&
                device.getDeviceName().equals(camera.getUsbDevice().getDeviceName())) {
                closeCamera(); setStatus("USB 摄像头已拔出");
            }
        }
        @Override public void onConnectDev(UsbDevice device, USBMonitor.UsbControlBlock controlBlock) {
            if (!visible || device == null || controlBlock == null) return;
            closeCamera();
            camera = new CameraUVC(MainActivity.this, device);
            camera.setUsbControlBlock(controlBlock);
            camera.addPreviewDataCallBack(frameCallback);
            camera.setCameraStateCallBack(new ICameraStateCallBack() {
                @Override public void onCameraState(MultiCameraClient.ICamera source,
                                                      State state, String error) {
                    if (source != camera) return; // stale callback from retired session
                    if (state == State.OPENED) setStatus("USB 已连接，点击画面测试单帧取图");
                    else if (state == State.ERROR) setStatus("USB 打开失败：" + (error == null ? "未知错误" : error));
                    else if (state == State.CLOSED) setStatus("USB 会话已关闭");
                }
            });
            openWhenReady();
        }
        @Override public void onDisConnectDec(UsbDevice device, USBMonitor.UsbControlBlock controlBlock) {
            if (camera != null && device != null &&
                device.getDeviceName().equals(camera.getUsbDevice().getDeviceName())) closeCamera();
            setStatus("USB 已断开连接");
        }
        @Override public void onCancelDev(UsbDevice device) { setStatus("USB 使用权限未授权"); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        selectedTask = null;
        refreshTasks(TaskCatalog.selectedId(this));
        client = new MultiCameraClient(this, usbCallback);
        tts = new TextToSpeech(this, result -> {
            speechReady = result == TextToSpeech.SUCCESS;
            if (speechReady) tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        });
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(18));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = label("蓝星向导 · 云端 AI 实验版", 22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView disclaimer = label("0.1 硬件/任务联调版｜无本地OCR｜不联网、不上传｜尚无真实AI识别", 13);
        disclaimer.setTextColor(0xffb45b23);
        root.addView(disclaimer);

        preview = new AspectRatioTextureView(this);
        preview.setBackgroundColor(0xff11151a);
        root.addView(preview, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        preview.setOnClickListener(v -> capture());
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) { openWhenReady(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeCamera(); return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
        });

        frameStatus = label("USB 原始回调：尚无图像", 13);
        root.addView(frameStatus);
        status = label("请连接 USB 摄像头，并同意权限请求", 15);
        root.addView(status);

        TextView taskLabel = label("当前任务（选择一次，自动记住）", 17);
        root.addView(taskLabel);
        taskSpinner = new Spinner(this);
        root.addView(taskSpinner);
        taskDescription = label("", 14);
        root.addView(taskDescription);

        LinearLayout editRow = new LinearLayout(this);
        editRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(editRow);
        addSmallButton(editRow, "新建自定义", v -> editTask(null));
        addSmallButton(editRow, "编辑任务", v -> {
            if (selectedTask != null && selectedTask.custom) editTask(selectedTask);
            else setStatus("系统预设任务不可编辑，请新建自定义任务");
        });
        addSmallButton(editRow, "删除任务", v -> deleteTask());

        Button capture = new Button(this);
        capture.setText("点击拍照 · 模拟朗读");
        root.addView(capture, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        capture.setOnClickListener(v -> capture());

        CheckBox volumeTest = new CheckBox(this);
        volumeTest.setText("仅亮屏前台：允许音量加/减触发拍照（会暂时占用音量键）");
        volumeTest.setChecked(false);
        volumeTest.setOnCheckedChangeListener((b, checked) -> volumeKeyTestEnabled = checked);
        root.addView(volumeTest);

        TextView notes = label("此版本只验证USB当前帧、720P压缩、任务记忆与本机TTS。" +
            "模拟播报不代表已经认出文字。真正熄屏保持摄像头、锁屏音量键和云端AI均未验证。" +
            "\n隐私：不保存照片，不发送照片，不存储识别原文。", 13);
        notes.setTextColor(0xff6b6b6b);
        root.addView(notes);
    }

    private void addSmallButton(LinearLayout parent, String name, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(name);
        b.setTextSize(12);
        parent.addView(b, new LinearLayout.LayoutParams(0, dp(49), 1f));
        b.setOnClickListener(listener);
    }

    private TextView label(String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setPadding(0, dp(7), 0, dp(7));
        return label;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void setStatus(String message) {
        ui.post(() -> { if (!isFinishing()) status.setText(message); });
    }

    private void refreshTasks(String selectId) {
        tasks = TaskCatalog.all(this);
        ArrayAdapter<TaskCatalog.Task> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, tasks);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        taskSpinner.setAdapter(adapter);
        int index = 0;
        for (int i = 0; i < tasks.size(); i++)
            if (tasks.get(i).id.equals(selectId)) index = i;
        taskSpinner.setSelection(index);
        selectedTask = tasks.get(index);
        showTask();
        taskSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TaskCatalog.Task next = tasks.get(position);
                if (selectedTask != null && !next.id.equals(selectedTask.id)) invalidateRequest();
                selectedTask = next;
                TaskCatalog.select(MainActivity.this, next.id);
                showTask();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void showTask() {
        if (selectedTask == null) return;
        taskDescription.setText((TaskCatalog.OCR.equals(selectedTask.engine) ?
            "执行路线：云端普通OCR" : "执行路线：云端视觉大模型") +
            "\n" + selectedTask.instruction);
    }

    private void editTask(TaskCatalog.Task old) {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("任务名称");
        name.setText(old == null ? "" : old.name);
        EditText instruction = new EditText(this);
        instruction.setHint("请用普通中文说明AI需要分析什么、朗读什么");
        instruction.setMinLines(4);
        instruction.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        instruction.setText(old == null ? "" : old.instruction);
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(14), dp(12), dp(14), dp(4));
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(name); form.addView(instruction);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(old == null ? "新建云端AI任务" : "编辑自定义任务")
            .setView(form).setNegativeButton("取消", null)
            .setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            try {
                TaskCatalog.Task task = TaskCatalog.upsert(this,
                    old == null ? null : old.id, name.getText().toString(), instruction.getText().toString());
                invalidateRequest();
                refreshTasks(task.id);
                dialog.dismiss();
            } catch (IllegalArgumentException error) {
                instruction.setError(error.getMessage());
            }
        }));
        dialog.show();
    }

    private void deleteTask() {
        if (selectedTask == null || !selectedTask.custom) {
            setStatus("只能删除自己创建的任务"); return;
        }
        String target = selectedTask.id;
        new AlertDialog.Builder(this).setTitle("删除自定义任务？")
            .setMessage("这会删除手机里保存的任务指令。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", (d, b) -> {
                invalidateRequest();
                TaskCatalog.remove(this, target);
                refreshTasks("ocr");
            }).show();
    }

    private void requestUsbDevice(UsbDevice device) {
        if (client == null || !visible || device == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try { client.requestPermission(device); }
        catch (Exception error) { setStatus("USB 权限请求失败：" + error.getClass().getSimpleName()); }
    }

    private void scanConnectedUsb() {
        if (client == null || !visible) return;
        List<UsbDevice> devices = client.getDeviceList(null);
        if (devices == null || devices.isEmpty()) {
            setStatus("未检测到 USB UVC 摄像头，请插入摄像头");
            return;
        }
        requestUsbDevice(devices.get(0));
    }

    private void openWhenReady() {
        if (!visible || preview == null || !preview.isAvailable() ||
            camera == null || camera.isCameraOpened()) return;
        CameraRequest request = new CameraRequest.Builder()
            .setPreviewWidth(1920).setPreviewHeight(1080)
            .setRenderMode(CameraRequest.RenderMode.NORMAL)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setRawPreviewData(true).setAspectRatioShow(true)
            .create();
        try { camera.openCamera(preview, request); setStatus("正在启动 USB 原生直显…"); }
        catch (Exception error) { setStatus("无法启动USB：" + error.getClass().getSimpleName()); }
    }

    private void closeCamera() {
        invalidateRequest();
        latestFrame = null;
        MultiCameraClient.ICamera retired = camera;
        camera = null;
        if (retired == null) return;
        try {
            retired.removePreviewDataCallBack(frameCallback);
            retired.closeCamera();
        } catch (Exception ignored) { }
    }

    private void invalidateRequest() {
        generation.incrementAndGet();
        if (pending != null) { pending.cancel(true); pending = null; }
        if (tts != null) tts.stop();
    }

    private void capture() {
        long now = SystemClock.elapsedRealtime();
        if (!visible) return;
        // Ignore bounce / accidental rapid taps to avoid future duplicate paid requests.
        if (now - lastAcceptedTap < 1500L) { setStatus("已忽略连续误触（1.5秒节流）"); return; }
        Frame snapshot = latestFrame;
        if (snapshot == null || now - snapshot.timestampMs > 1400L) {
            setStatus("没有最新USB画面，拒绝使用旧照片"); return;
        }
        lastAcceptedTap = now;
        invalidateRequest();
        int requestId = generation.get();
        final TaskCatalog.Task task = selectedTask;
        setStatus("正在压缩当前画面（只在内存中处理）…");
        pending = worker.submit(() -> {
            try {
                byte[] jpeg = FrameCompressor.to720pJpeg(
                    snapshot.nv21, snapshot.width, snapshot.height);
                if (requestId != generation.get()) return;
                String info = "已获取当前 " + snapshot.width + "×" + snapshot.height +
                    " USB 画面，压缩JPEG " + (jpeg.length / 1024) + "KB。" +
                    "任务：" + task.name + "（" + task.engine + "）。";
                ui.post(() -> {
                    if (requestId != generation.get()) return;
                    setStatus(info + " 当前仅模拟联调，无云端请求。");
                    String mockSpeech = "模拟测试。已经拍到一张新照片。" +
                        "现在尚未接入云端，不能朗读照片里的真实文字。";
                    if (speechReady && tts != null)
                        tts.speak(mockSpeech, TextToSpeech.QUEUE_FLUSH, null, "probe-" + requestId);
                });
            } catch (Exception error) {
                if (requestId == generation.get())
                    setStatus("拍照压缩失败：" + error.getClass().getSimpleName());
            }
        });
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (volumeKeyTestEnabled && event.getRepeatCount() == 0 &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            capture(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        if (client != null) client.register();
        lastObservedFrames = rawFrames.get();
        lastObservedAt = SystemClock.elapsedRealtime();
        ui.removeCallbacks(healthTicker);
        ui.postDelayed(healthTicker, 1000);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA_REQUEST);
        } else scanConnectedUsb();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == CAMERA_REQUEST) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
                scanConnectedUsb();
            else setStatus("未授予 CAMERA 权限，无法打开 USB 摄像头");
        }
    }

    @Override protected void onPause() {
        visible = false;
        ui.removeCallbacks(healthTicker);
        closeCamera();
        // This is intentionally NOT a lockscreen background camera implementation.
        // Never claim real screen-off USB capture works without phone evidence.
        super.onPause();
    }

    @Override protected void onStop() {
        if (client != null) client.unRegister();
        super.onStop();
    }

    @Override protected void onDestroy() {
        closeCamera();
        if (client != null) { client.unRegister(); client.destroy(); client = null; }
        worker.shutdownNow();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        super.onDestroy();
    }
}
