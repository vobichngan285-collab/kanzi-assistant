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
    private TextView folderText, statusText, recentText, noteText;
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

        text(column, "蓝星 · 照片监听实验", 27, true, 2);
        text(column, "独立小工具｜仅检测新增照片，不读取图像内容、不联网、不接 OCR。",
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

        noteText = text(column,
                "使用说明：\n" +
                "1. 选择与手机相机保存路径一致的文件夹，通常是 DCIM/Camera。\n" +
                "2. 点「开始自动监听」，建立旧照片基线。\n" +
                "3. 点「打开手机相机」拍一张新照片，然后返回。\n" +
                "4. 软件发现新照片后会朗读「发现新照片」，并显示检测时间。\n\n" +
                "注意：系统文件夹授权因机型而异；只扫描所选目录的直接图片，不递归子文件夹。\n" +
                "估计延迟来自照片文件时间戳，不能代表按下快门到传输完成的精确延迟。\n" +
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
        String time = lastScan > 0 ?
                new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(lastScan))
                : "尚未扫描";
        String delayText = delay < 0 ? "不可估算" : "约 " + delay + " 毫秒";
        recentText.setText("发现新照片：" + count + " 张\n" +
                "最近照片：" + latest + "\n" +
                "估计文件保存到检测：" + delayText + "\n" +
                "最近扫描时间：" + time);
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
