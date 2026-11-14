package com.vobichngan.bluestar.m02slab;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * M02S manual physical shutter + Wi-Fi auto-fetch experiment.
 * One-time verified BLE 0x39/0x30 command enables the glasses AP ONLY.
 * This app never sends BLE photo/shutter commands.
 *
 * Uses the OEM AP list /app/getfilelist + original-photo read-only GET that
 * already worked on the user's device, with conservative fresh-file policy.
 * The photo remains in RAM only; no cloud upload, gallery write or delete.
 */
public final class MainActivity extends Activity {
    private static final long POLL_DELAY_MS = 450L;
    private static final long MAX_WATCH_MS = 120_000L;
    private static final int MAX_SHOTS = 3;
    private static final int REQUEST_WIFI_PERMISSIONS = 1301;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final StringBuilder diagnostics = new StringBuilder();
    private ConnectivityManager connectivity;
    private TextView statusView;
    private TextView logView;
    private TextView counterView;
    private M02sApStarter apStarter;
    private ImageView photoPreview;
    private Network targetNetwork;
    private String targetHost;
    private boolean verifying;
    private boolean watching;
    private boolean inFlight;
    private boolean destroyed;
    private long epoch;
    private long firstNewObservedAt = -1;
    private int shots;
    private int polls;
    private int failures;
    private byte[] photoInMemory; // RAM only.
    private List<M02sPhotoPolicy.Candidate> known = new ArrayList<>();
    private M02sFreshPhotoPolicy.Selection previous;
    private Runnable watchDeadline;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)
                getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        apStarter = new M02sApStarter(this, ui, connectivity, wifi,
                new M02sApStarter.Callback() {
                    @Override public void log(String message) { MainActivity.this.log(message); }
                    @Override public void ready(Network net, String host) { onApConnected(net, host); }
                    @Override public void failed(String message) {
                        MainActivity.this.log("眼镜Wi-Fi建连未成功：" + message);
                        status("连接失败：" + message);
                    }
                    @Override public void lost(String message) {
                        MainActivity.this.log(message);
                        stopWatch(message);
                        targetNetwork = null;
                        targetHost = null;
                    }
                });
        buildUi();
        log("v0.1.15 扫描失败回退修复：蓝牙只开AP，已连接目标复用，否则12秒后仅对准确SSID请求一次Wi-Fi；不遥控拍照");
        log("继续拍前基线/450ms照片列表比较/高清JPEG下载；不保存相册、上传云端、删除眼镜照片");
        status("按「连接眼镜并启动Wi-Fi」；无需手动猜热点SSID或密码");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setPadding(px(18), px(20), px(18), px(26));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        setContentView(scroll);

        addText(body, "蓝星 M02S 手动快门 Wi-Fi 取图", 24, Color.rgb(28, 59, 94), true);
        addText(body, "v0.1.15 · 蓝牙只开热点 · 实体手动拍照", 15, Color.DKGRAY, false);
        statusView = addText(body, "", 18, Color.rgb(12, 99, 75), true);

        addText(body, "① 用蓝牙仅开启眼镜Wi-Fi（不遥控拍照）", 18, Color.BLACK, true);
        addButton(body, "连接M02S并开启Wi-Fi、自动等待照片", this::connectApAndWatch);
        addButton(body, "停止监听并断开本次眼镜Wi-Fi连接", this::disconnectAll);
        addText(body, "程序只对唯一M02S发送一次已验证的0x39/0x30开热点命令，"
                + "收到眼镜SSID后按此前成功的Wi-Fi连接方法自动建连；不发送任何0x22拍照指令。", 14,
                Color.DKGRAY, false);
        addText(body, "② 看到“基线已建立”后，直接按眼镜实体快门", 18, Color.BLACK, true);
        addText(body, "如果眼镜在Wi-Fi模式下无法实体拍照，说明可能存在固件模式互斥；"
                + "请直接反馈，不重复发送开热点或拍照命令。", 14, Color.DKGRAY, false);
        addText(body, "③ 手机Wi-Fi自动读取新的高清原图", 18, Color.BLACK, true);
        counterView = addText(body, "本轮成功 0 / 3 张", 18, Color.rgb(28, 59, 94), true);
        addText(body, "Wi-Fi保持连接，每约450毫秒检查一次新增文件；连续两次稳定后自动下载原图。最多120秒或成功3张。", 14, Color.DKGRAY, false);

        addText(body, "最新高清照片（仅内存预览）", 18, Color.BLACK, true);
        photoPreview = new ImageView(this);
        photoPreview.setAdjustViewBounds(true);
        photoPreview.setMinimumHeight(px(150));
        photoPreview.setBackgroundColor(Color.rgb(239,243,246));
        body.addView(photoPreview, new LinearLayout.LayoutParams(-1, -2));
        addButton(body, "清除内存照片", () -> {
            photoInMemory = null;
            photoPreview.setImageDrawable(null);
            log("已清除手机内存照片预览；不会触碰眼镜文件");
        });

        addText(body, "诊断日志（不含照片、SSID、密码、文件名或设备地址）", 18, Color.BLACK, true);
        addButton(body, "复制匿名诊断日志", () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("M02S Wi-Fi-only diagnostics", diagnostics.toString()));
            status("已复制诊断日志");
        });
        logView = addText(body, "", 13, Color.rgb(35,44,50), false);
        logView.setGravity(Gravity.START);
        addText(body, "注意：手机无法精准获得实体快门按下的时间；报告的“首次发现新文件到解码”并非整个拍照耗时。此版仅测试高清Wi-Fi取图，不接OCR/TTS。", 13, Color.DKGRAY, false);
    }

    private TextView addText(LinearLayout body, String value, int sp, int color, boolean strong) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (strong) text.setTypeface(null, 1);
        text.setPadding(0, px(7), 0, px(7));
        body.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private void addButton(LinearLayout body, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setAllCaps(false);
        body.addView(b, new LinearLayout.LayoutParams(-1, -2));
        b.setOnClickListener(v -> action.run());
    }
    private int px(int dp) {
        return (int)(getResources().getDisplayMetrics().density * dp + 0.5f);
    }
    private void status(String message) {
        if (statusView != null) statusView.setText(message);
    }
    private void log(String message) {
        String row = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
                + " " + message + "\n";
        diagnostics.append(row);
        if (diagnostics.length() > 16000)
            diagnostics.delete(0, Math.min(4000, diagnostics.length() - 14000));
        if (logView != null) logView.setText(diagnostics.toString());
    }

    private boolean ensurePermission() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (missing.isEmpty()) return true;
        requestPermissions(missing.toArray(new String[0]), REQUEST_WIFI_PERMISSIONS);
        status("请授予蓝牙与附近Wi-Fi权限，然后再次点击连接");
        return false;
    }

    /** Active only for the one BLE-confirmed, exact-SSID Android Network. */
    private boolean targetStillPresent() {
        return apStarter != null && apStarter.isActive(targetNetwork, targetHost);
    }

    private void connectApAndWatch() {
        if (!ensurePermission()) return;
        if (watching || verifying || inFlight) {
            status("当前已有照片监听或验证，请勿重复开启眼镜AP");
            return;
        }
        if (targetStillPresent()) {
            log("此前已确认的眼镜AP连接仍然存在，直接重新建立拍前基线，不再发送蓝牙命令");
            beginWatch();
            return;
        }
        if (apStarter != null && apStarter.running()) {
            status("正在建立眼镜Wi-Fi连接；不重复开热点");
            return;
        }
        apStarter.start();
        status("正在蓝牙连接眼镜并开启Wi-Fi，请观察连接授权");
    }

    /** Network is proven BLE-SSID-bound by the starter; double-check OEM list. */
    private void onApConnected(Network net, String host) {
        if (destroyed) return;
        if (watching || verifying) return;
        final long run = ++epoch;
        verifying = true;
        targetNetwork = net;
        targetHost = host;
        status("已拿到眼镜真实AP网关，正在验证原厂照片列表");
        io.execute(() -> {
            try {
                M02sPhotoListReader.Result list = readPhotoList(net, host);
                ui.post(() -> {
                    if (destroyed || run != epoch) return;
                    verifying = false;
                    if (!targetStillPresent()) {
                        status("验证中眼镜Wi-Fi已断开，不能自动取图");
                        return;
                    }
                    log("BLE唤醒眼镜AP + Android专属Wi-Fi + 原厂HTTP200 JSON验证成功；"
                            + "旧照片数量=" + list.eligibleImages + "，准备自动建立基线");
                    beginWatch();
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    if (destroyed || run != epoch) return;
                    verifying = false;
                    log("目标AP连上，但原厂HTTP照片列表校验失败；类型="
                            + ex.getClass().getSimpleName() + "；不冒认其它设备");
                    status("原厂照片列表不可用。请复制日志，别重复点击拍照");
                    // Do not keep a non-validated connection silently.
                    if (apStarter != null) apStarter.stop();
                    targetNetwork = null;
                    targetHost = null;
                });
            }
        });
    }

    private static M02sPhotoListReader.Result readPhotoList(Network net, String host)
            throws Exception {
        M02sPhotoTransport.Response response = M02sPhotoTransport.getList(net, host);
        if (response.httpCode != 200)
            throw new java.io.IOException("照片列表HTTP非200");
        org.json.JSONObject root = new org.json.JSONObject(
                new String(response.body, StandardCharsets.UTF_8));
        if (root.optInt("result", -1234) != 0 || !root.has("info"))
            throw new java.io.IOException("不是已验证的M02S原厂JSON照片列表");
        return M02sPhotoListReader.parse(response.body);
    }

    private void disconnectAll() {
        stopWatch("用户停止自动取图");
        if (apStarter != null) apStarter.stop();
        targetNetwork = null;
        targetHost = null;
        log("已释放本次眼镜Wi-Fi网络请求和BLE控制连接；眼镜固件何时关闭热点未知");
        status("眼镜Wi-Fi控制会话已停止；原图仍只在本机内存预览");
    }

    private void beginWatch() {
        if (!ensurePermission()) return;
        if (watching || verifying || inFlight) {
            status("已有Wi-Fi检查任务运行，请勿重复开始"); return;
        }
        if (targetNetwork == null || targetHost == null || !targetStillPresent()) {
            status("请先点击连接眼镜并开启Wi-Fi，验证专属网络后才可监听");
            return;
        }
        final long run = ++epoch;
        inFlight = true;
        shots = 0;
        polls = 0;
        failures = 0;
        firstNewObservedAt = -1;
        previous = null;
        counterView.setText("本轮成功 0 / 3 张");
        status("正在读取拍照前旧照片基线；完成后再按实体拍照键");
        final Network net = targetNetwork;
        final String host = targetHost;
        io.execute(() -> {
            try {
                M02sPhotoListReader.Result list = readPhotoList(net, host);
                ui.post(() -> {
                    if (destroyed || run != epoch) return;
                    inFlight = false;
                    if (!targetStillPresent()) {
                        status("建立基线期间Wi-Fi已断开，拒绝开始监听"); return;
                    }
                    known = new ArrayList<>(list.candidates);
                    watching = true;
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    log("新实验：只读照片基线已建立，旧图片数=" + known.size()
                            + "；开始每约450ms检查新增文件；本应用绝不发蓝牙指令");
                    status("正在监听新照片：现在请按眼镜的实体拍照键");
                    watchDeadline = () -> {
                        if (watching && run == epoch)
                            stopWatch("120秒观察到期；已成功 " + shots + " 张");
                    };
                    ui.postDelayed(watchDeadline, MAX_WATCH_MS);
                    schedulePoll(run, POLL_DELAY_MS);
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    if (destroyed || run != epoch) return;
                    inFlight = false;
                    log("拍照前基线失败：原厂照片列表不可用；异常类型="
                            + ex.getClass().getSimpleName());
                    status("无法建立拍照前基线；不会自动取旧照片");
                });
            }
        });
    }

    private void schedulePoll(long run, long delayMs) {
        ui.postDelayed(() -> poll(run), delayMs);
    }

    private void poll(long run) {
        if (!watching || destroyed || run != epoch || inFlight) return;
        if (!targetStillPresent()) {
            stopWatch("目标眼镜Wi-Fi已断开/切换或无安全网关；不会切换其他网络");
            return;
        }
        inFlight = true;
        polls++;
        final Network net = targetNetwork;
        final String host = targetHost;
        io.execute(() -> {
            try {
                M02sPhotoListReader.Result list = readPhotoList(net, host);
                ui.post(() -> {
                    if (!watching || destroyed || run != epoch) return;
                    inFlight = false;
                    failures = 0;
                    M02sFreshPhotoPolicy.Selection selected =
                            M02sFreshPhotoPolicy.select(known, list.candidates);
                    if (selected.status != M02sFreshPhotoPolicy.Status.NO_NEW_FILE
                            && firstNewObservedAt < 0) {
                        firstNewObservedAt = SystemClock.elapsedRealtime();
                        log("检测到旧照片基线之外的文件变化：状态=" + selected.status
                                + "；候选数量=" + selected.freshCount + "；等待稳定");
                    }
                    if (M02sFreshPhotoPolicy.stablePair(previous, selected)) {
                        inFlight = true;
                        log("检测到同一新文件连续两次大小稳定；开始只读下载原图");
                        downloadNewPhoto(run, net, host, selected.photo);
                        return;
                    }
                    if (polls % 8 == 0 || (previous != null
                            && previous.status != selected.status)) {
                        log("Wi-Fi列表观察 #" + polls + "：状态=" + selected.status
                                + "；新候选数=" + selected.freshCount);
                    }
                    previous = selected;
                    schedulePoll(run, POLL_DELAY_MS);
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    if (!watching || destroyed || run != epoch) return;
                    inFlight = false;
                    failures++;
                    log("Wi-Fi列表查询失败 #" + failures
                            + "；异常类型=" + ex.getClass().getSimpleName());
                    if (failures >= 3) {
                        stopWatch("连续3次只读列表请求失败；不自动重连或扫描");
                    } else schedulePoll(run, POLL_DELAY_MS);
                });
            }
        });
    }

    private void downloadNewPhoto(long run, Network net, String host,
                                  M02sPhotoPolicy.Candidate candidate) {
        final long begin = SystemClock.elapsedRealtime();
        io.execute(() -> {
            try {
                M02sPhotoTransport.Response response =
                        M02sPhotoTransport.getOriginalPhoto(net, host, candidate);
                if (response.httpCode != 200)
                    throw new java.io.IOException("原图HTTP状态非200");
                M02sPhotoPolicy.ImageKind type =
                        M02sPhotoPolicy.imageKind(response.body);
                if (!M02sPhotoPolicy.extensionMatches(candidate.remoteName, type))
                    throw new java.io.IOException("原图签名和扩展名不匹配");
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(response.body, 0,
                        response.body.length, bounds);
                if (bounds.outWidth < 1 || bounds.outHeight < 1
                        || bounds.outWidth > 10000 || bounds.outHeight > 10000
                        || (long)bounds.outWidth * bounds.outHeight > 32_000_000L)
                    throw new java.io.IOException("原图尺寸无效或过大");
                Bitmap bitmap = BitmapFactory.decodeByteArray(
                        response.body, 0, response.body.length);
                if (bitmap == null)
                    throw new java.io.IOException("原图Bitmap解码失败");
                long total = SystemClock.elapsedRealtime() - begin;
                ui.post(() -> {
                    if (!watching || destroyed || run != epoch) {
                        bitmap.recycle();
                        return;
                    }
                    inFlight = false;
                    if (!targetStillPresent()) {
                        bitmap.recycle();
                        stopWatch("下载后发现已切换网络；放弃本次结果");
                        return;
                    }
                    photoInMemory = response.body;
                    photoPreview.setImageBitmap(bitmap);
                    known.add(candidate); // Mark ONLY downloaded file as seen.
                    previous = null;
                    shots++;
                    counterView.setText("本轮成功 " + shots + " / 3 张");
                    long discoveryToDecode = firstNewObservedAt < 0 ? -1
                            : SystemClock.elapsedRealtime() - firstNewObservedAt;
                    log("高清原图自动下载成功 #" + shots
                            + "：分辨率=" + bitmap.getWidth() + "×" + bitmap.getHeight()
                            + "；字节=" + response.body.length
                            + "；HTTP下载=" + response.durationMs + "ms"
                            + "；请求到解码=" + total + "ms"
                            + "；首次发现新文件到解码=" + discoveryToDecode
                            + "ms；仅手机内存预览");
                    firstNewObservedAt = -1;
                    if (shots >= MAX_SHOTS) {
                        stopWatch("连续3张新高清照片自动下载成功；不代表端到端拍照耗时达标");
                    } else {
                        status("第" + shots + "张高清照片已显示，请再次按眼镜实体拍照键");
                        schedulePoll(run, POLL_DELAY_MS);
                    }
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    if (!watching || destroyed || run != epoch) return;
                    inFlight = false;
                    log("发现新照片，但原图下载/解码失败；类型="
                            + ex.getClass().getSimpleName() + "；不会使用旧图冒充");
                    stopWatch("原图下载失败，停止监听并保留诊断");
                });
            }
        });
    }

    private void stopWatch(String why) {
        boolean was = watching;
        watching = false;
        epoch++; // Invalidate all scheduled callbacks and in-flight results.
        inFlight = false;
        verifying = false;
        if (watchDeadline != null) ui.removeCallbacks(watchDeadline);
        watchDeadline = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (was) log("Wi-Fi自动取图停止：" + why
                + "；成功张数=" + shots + "；列表查询次数=" + polls);
        status("已停止：" + why);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        stopWatch("应用退出");
        if (apStarter != null) apStarter.stop();
        io.shutdownNow();
        photoInMemory = null;
        super.onDestroy();
    }
}
