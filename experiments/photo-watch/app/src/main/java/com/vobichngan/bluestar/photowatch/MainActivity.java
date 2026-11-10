package com.vobichngan.bluestar.photowatch;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A deliberately separate test APK. No access to Blue Star Guide's private data.
 * All controls are standard Android widgets for screen-reader accessibility.
 */
public final class MainActivity extends Activity {
    private static final int CHOOSE_FOLDER = 501;
    private static final int ASK_NOTIFICATIONS = 502;
    private static final int BLUE = Color.rgb(17, 79, 157);
    private static final int INK = Color.rgb(26, 41, 62);

    private SharedPreferences prefs;
    private TextView folderText, statusText, recentText, readProbeText, noteText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextToSpeech testTts;
    private boolean ttsReady;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            render();
            mainHandler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PhotoWatchService.PREFS, MODE_PRIVATE);
        makeScreen();
        testTts = new TextToSpeech(this, code -> {
            if (code == TextToSpeech.SUCCESS && testTts != null) {
                int result = testTts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                           result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
        askNotificationPermission();
        render();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(LinearLayout group, String text, int size,
                          boolean strong, int topMargin) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(INK);
        view.setPadding(0, dp(topMargin), 0, dp(5));
        if (strong) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        group.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private Button button(LinearLayout group, String label, boolean primary,
                           View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(19);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : BLUE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? BLUE : Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(2), BLUE);
        button.setBackground(background);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        group.addView(button, params);
        button.setOnClickListener(action);
        return button;
    }

    private void makeScreen() {
        ScrollView scrolling = new ScrollView(this);
        scrolling.setFillViewport(true);
        scrolling.setBackgroundColor(Color.rgb(246, 250, 255));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(24), dp(20), dp(24), dp(28));
        scrolling.addView(column);
        setContentView(scrolling);

        text(column, "蓝星 · 照片快速读取测速", 26, true, 2);
        text(column, "独立小工具｜保留快速语音提示，同时本地验证照片何时可以完整读取。不联网，不上传图片。",
                17, false, 8);

        folderText = text(column, "照片文件夹：尚未选择", 19, true, 18);
        button(column, "① 选择照片文件夹", true, v -> chooseFolder());
        button(column, "② 开始自动监听（30分钟）", true, v -> startWatch());
        button(column, "③ 打开手机相机拍一张", false, v -> openCamera());
        button(column, "停止监听", false, v -> stopWatch());
        button(column, "试听「发现新照片」", false, v -> testSpeech());

        statusText = text(column, "监听状态：未启动", 19, true, 22);
        statusText.setTextIsSelectable(true);
        recentText = text(column, "检测结果：暂无", 18, false, 10);
        recentText.setTextIsSelectable(true);
        text(column, "照片读取速度（修复实验）", 21, true, 17);
        readProbeText = text(column, "请先拍一张新照片", 18, false, 7);
        readProbeText.setTextIsSelectable(true);

        noteText = text(column,
                "使用说明：\n" +
                "1. 选择与手机相机保存路径一致的文件夹，通常是 DCIM/Camera。\n" +
                "2. 点「开始自动监听」，建立旧照片基线。\n" +
                "3. 点「打开手机相机」拍一张新照片，然后返回。\n" +
                "4. 监听到新照片时，先照旧快速播报，然后另开后台任务测试本地读取。\n\n" +
                "注意：系统文件夹授权因机型而异；只扫描所选目录的直接图片，不递归子文件夹。\n" +
                "估计延迟来自照片文件时间戳，不能代表按下快门到传输完成的精确延迟。\n" +
                "新版会显示：第一次能打开、第一次抽样解码、字节完整读取且文件稳定的耗时。\n" +
                "JPEG动态照片允许在图像后面附加视频，不再强制整份 JPG 以 FF D9 结尾。\n" +
                "结果是安全的本地读取候选时间，仍不能保证拍照程序将来不会再次修改。\n" +
                "解码成功并不意味着之后文件绝不会变化，不会自动将照片上传到云端。\n" +
                "继续保留以前的 FileObserver 直达语音和600毫秒回退方案。\n" +
                "监听期间会出现常驻通知，并临时保持 CPU 活跃以测试息屏，最多30分钟；可以随时停止。",
                16, false, 22);
    }

    private void chooseFolder() {
        if (PhotoWatchService.isRunning) {
            statusText.setText("请先停止监听，再更换文件夹。");
            return;
        }
        Intent chooser = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                         Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(chooser, CHOOSE_FOLDER);
        } catch (ActivityNotFoundException failure) {
            statusText.setText("手机缺少系统文件夹选择器。");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CHOOSE_FOLDER || result != RESULT_OK ||
                data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            prefs.edit().putString(PhotoWatchService.KEY_TREE, tree.toString())
                    .putString(PhotoWatchService.KEY_STATUS,
                            "新文件夹已绑定。点击开始监听，再拍摄照片。")
                    .putLong(PhotoWatchService.KEY_LAST_SCAN, 0)
                    .putInt(PhotoWatchService.KEY_COUNT, 0)
                    .putString(PhotoWatchService.KEY_LAST_NAME, "暂无")
                    .putLong(PhotoWatchService.KEY_DELAY, -1)
                    .putLong(PhotoWatchService.KEY_LOCAL_TO_TTS_MS, -1)
                    .putString(PhotoWatchService.KEY_TTS_REQUEST, "尚未触发")
                    .putString(PhotoWatchService.KEY_LAST_SOURCE, "尚未触发")
                    .putInt(PhotoWatchService.KEY_DIRECT_EVENTS, 0)
                    .putString(PhotoWatchService.KEY_DIRECT_STATUS, "尚未启动")
                    .putLong(PhotoWatchService.KEY_TTS_ONSTART_MS, -1)
                    .putString(PhotoWatchService.KEY_PROBE_FILE, "暂无")
                    .putString(PhotoWatchService.KEY_PROBE_STATUS, "尚未触发")
                    .putString(PhotoWatchService.KEY_PROBE_SOURCE, "尚未触发")
                    .putString(PhotoWatchService.KEY_PROBE_ACCESS_PATH, "尚未打开")
                    .putLong(PhotoWatchService.KEY_PROBE_OPEN_MS, -1)
                    .putLong(PhotoWatchService.KEY_PROBE_DECODE_MS, -1)
                    .putLong(PhotoWatchService.KEY_PROBE_READY_MS, -1)
                    .putLong(PhotoWatchService.KEY_PROBE_SIZE, 0)
                    .putInt(PhotoWatchService.KEY_PROBE_ATTEMPTS, 0)
                    .putString(PhotoWatchService.KEY_PROBE_DIMENSIONS, "未知")
                    .putString(PhotoWatchService.KEY_PROBE_MARKER, "尚未判断")
                    .apply();
        } catch (SecurityException e) {
            prefs.edit().putString(PhotoWatchService.KEY_STATUS,
                    "文件夹授权失败，请重新选择。").apply();
        }
        render();
    }

    private void startWatch() {
        if (prefs.getString(PhotoWatchService.KEY_TREE, "").isEmpty()) {
            statusText.setText("请先选择照片文件夹。");
            return;
        }
        if (PhotoWatchService.isRunning) {
            statusText.setText("已经在监听，请直接拍新照片。");
            return;
        }
        Intent start = new Intent(this, PhotoWatchService.class)
                .setAction(PhotoWatchService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(start);
            else startService(start);
            statusText.setText("正在启动监听，准备建立初始照片基线。");
        } catch (RuntimeException e) {
            statusText.setText("无法启动后台监听：" + e.getClass().getSimpleName());
        }
    }

    private void stopWatch() {
        Intent stop = new Intent(this, PhotoWatchService.class)
                .setAction(PhotoWatchService.ACTION_STOP);
        try { startService(stop); }
        catch (RuntimeException e) { stopService(new Intent(this, PhotoWatchService.class)); }
        statusText.setText("已请求停止监听。");
    }

    private void openCamera() {
        Intent camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        try {
            startActivity(camera);
        } catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE)); }
            catch (ActivityNotFoundException second) {
                statusText.setText("没有可用的系统相机；也可通过文件管理器向所选文件夹新增图片。");
            }
        }
    }

    private void testSpeech() {
        if (testTts != null && ttsReady) {
            testTts.speak("发现新照片", TextToSpeech.QUEUE_FLUSH, null,
                    "test_photo_watch");
        } else {
            statusText.setText("本机中文 TTS 暂不可用，请检查系统语音服务。");
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    ASK_NOTIFICATIONS);
        }
    }

    private static String elapsed(long millis) {
        return millis < 0 ? "尚未完成" : millis + " 毫秒";
    }

    private void render() {
        String tree = prefs.getString(PhotoWatchService.KEY_TREE, "");
        if (tree.isEmpty()) {
            folderText.setText("照片文件夹：尚未选择");
        } else {
            String display;
            try { display = DocumentsContract.getTreeDocumentId(Uri.parse(tree)); }
            catch (RuntimeException e) { display = "已授权目录"; }
            folderText.setText("照片文件夹：" + display);
        }
        String status = prefs.getString(PhotoWatchService.KEY_STATUS, "尚未启动。");
        statusText.setText("监听状态：" +
                (PhotoWatchService.isRunning ? "运行中\n" : "未运行\n") + status);

        int count = prefs.getInt(PhotoWatchService.KEY_COUNT, 0);
        String latest = prefs.getString(PhotoWatchService.KEY_LAST_NAME, "暂无");
        long delay = prefs.getLong(PhotoWatchService.KEY_DELAY, -1);
        long lastScan = prefs.getLong(PhotoWatchService.KEY_LAST_SCAN, 0);
        long requestDelay = prefs.getLong(PhotoWatchService.KEY_LOCAL_TO_TTS_MS, -1);
        String ttsStatus = prefs.getString(PhotoWatchService.KEY_TTS_REQUEST, "尚未触发");
        String source = prefs.getString(PhotoWatchService.KEY_LAST_SOURCE, "尚未触发");
        String direct = prefs.getString(PhotoWatchService.KEY_DIRECT_STATUS, "尚未启动");
        int directHits = prefs.getInt(PhotoWatchService.KEY_DIRECT_EVENTS, 0);
        long ttsOnStart = prefs.getLong(PhotoWatchService.KEY_TTS_ONSTART_MS, -1);
        String time = lastScan > 0 ?
                new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(lastScan))
                : "尚未扫描";
        String delayText = delay < 0 ? "不可估算" : "约 " + delay + " 毫秒";
        recentText.setText("发现新照片：" + count + " 张\n" +
                "最近照片：" + latest + "\n" +
                "估计文件时间戳到检测：" + delayText + "\n" +
                "直接监听状态：" + direct + "\n" +
                "直接捕获的新照片：" + directHits + " 张\n" +
                "本次发现渠道：" + source + "\n" +
                "事件/扫描开始到语音请求：" +
                    (requestDelay >= 0 ? requestDelay + " 毫秒" : "尚未测量") + "\n" +
                "语音引擎 onStart 反馈：" +
                    (ttsOnStart >= 0 ? ttsOnStart + " 毫秒" : "尚未收到") + "\n" +
                "语音请求状态：" + ttsStatus + "\n" +
                "最近扫描时间：" + time);

        long opened = prefs.getLong(PhotoWatchService.KEY_PROBE_OPEN_MS, -1);
        long decoded = prefs.getLong(PhotoWatchService.KEY_PROBE_DECODE_MS, -1);
        long ready = prefs.getLong(PhotoWatchService.KEY_PROBE_READY_MS, -1);
        String probeName = prefs.getString(PhotoWatchService.KEY_PROBE_FILE, "暂无");
        String probeStatus = prefs.getString(PhotoWatchService.KEY_PROBE_STATUS, "尚未触发");
        String probeSource = prefs.getString(PhotoWatchService.KEY_PROBE_SOURCE, "尚未触发");
        String probeRoute = prefs.getString(PhotoWatchService.KEY_PROBE_ACCESS_PATH, "尚未打开");
        long bytes = prefs.getLong(PhotoWatchService.KEY_PROBE_SIZE, 0);
        int attempts = prefs.getInt(PhotoWatchService.KEY_PROBE_ATTEMPTS, 0);
        String dimensions = prefs.getString(PhotoWatchService.KEY_PROBE_DIMENSIONS, "未知");
        String marker = prefs.getString(PhotoWatchService.KEY_PROBE_MARKER, "尚未判断");
        readProbeText.setText("测试照片：" + probeName + "\n" +
                "发现方式：" + probeSource + "\n" +
                "读取渠道：" + probeRoute + "\n" +
                "首次打开文件：" + elapsed(opened) + "\n" +
                "首次成功抽样解码：" + elapsed(decoded) + "\n" +
                "完整字节读完 + 稳定候选：" + elapsed(ready) + "\n" +
                "图像分辨率：" + dimensions + "\n" +
                "文件大小：" + (bytes > 0 ? (bytes / 1024) + " KB" : "未知") + "\n" +
                "结束标志分析：" + marker + "\n" +
                "检测轮数：" + attempts + "\n" +
                "读取状态：" + probeStatus);
    }

    @Override protected void onResume() {
        super.onResume();
        mainHandler.removeCallbacks(refresher);
        mainHandler.post(refresher);
    }

    @Override protected void onPause() {
        mainHandler.removeCallbacks(refresher);
        super.onPause();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacks(refresher);
        if (testTts != null) { testTts.stop(); testTts.shutdown(); }
        super.onDestroy();
    }
}
