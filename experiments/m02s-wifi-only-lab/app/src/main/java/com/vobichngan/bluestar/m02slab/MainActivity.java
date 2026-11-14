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
 * M02S Wi-Fi-only experiment. NEVER sends a Bluetooth command or scans BLE.
 * The user manually presses the physical shutter button on the glasses.
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
    private EditText ssidInput;
    private ImageView photoPreview;
    private Network targetNetwork;
    private String targetHost;
    private String targetSsid; // RAM only, never logged or persisted.
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
        buildUi();
        log("v0.1.13 纯Wi-Fi手动拍照实验启动；零蓝牙权限、零BLE指令、零云上传、零照片写盘");
        log("只连接用户在系统中已手动连接的精确SSID；由Android路由取得私有网关并核对原厂照片列表");
        log("本App无法绕过眼镜固件开启热点：如果手动拍照时热点不存在，会明确显示不可测试");
        status("先在系统Wi-Fi设置里连接眼镜热点，再填写其精确SSID");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setPadding(px(18), px(20), px(18), px(26));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        setContentView(scroll);

        addText(body, "蓝星 M02S 纯 Wi-Fi 自动取图", 24, Color.rgb(28, 59, 94), true);
        addText(body, "v0.1.13 · 手按眼镜拍照键 · 不操作蓝牙", 15, Color.DKGRAY, false);
        statusView = addText(body, "", 18, Color.rgb(12, 99, 75), true);

        addText(body, "1  先用安卓 Wi-Fi 设置连接眼镜热点", 18, Color.BLACK, true);
        addButton(body, "打开系统 Wi-Fi 设置", () ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        addText(body, "如果系统里找不到眼镜热点，说明它可能无法独立开启。此实验不发送蓝牙开热点命令，也不提供未知热点密码。", 14, Color.DKGRAY, false);

        addText(body, "2  填写你在系统中看到的眼镜热点完整名称（SSID）", 18, Color.BLACK, true);
        ssidInput = new EditText(this);
        ssidInput.setTextSize(17);
        ssidInput.setSingleLine(true);
        ssidInput.setHint("输入眼镜热点的完整SSID（不会记录或上传）");
        body.addView(ssidInput);
        addButton(body, "验证当前眼镜 Wi-Fi 和原厂照片接口", this::verifyTarget);
        addText(body, "只有精确SSID匹配、系统提供192.168.x.x私有网关，并且 /app/getfilelist 返回正确原厂JSON，才允许自动读取。", 14, Color.DKGRAY, false);

        addText(body, "3  开始自动取图，再按眼镜实体拍照键", 18, Color.BLACK, true);
        addButton(body, "开始自动取图（先建立旧照片基线）", this::beginWatch);
        addButton(body, "停止自动取图", () -> stopWatch("用户手动停止"));
        counterView = addText(body, "本轮成功 0 / 3 张", 18, Color.rgb(28, 59, 94), true);
        addText(body, "每450毫秒左右检查新照片列表；两次观察到同一张文件及大小稳定后，自动下载高清原图。最多观察120秒或成功3张。", 14, Color.DKGRAY, false);

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

        addText(body, "诊断日志（不含照片、SSID、密码或文件名）", 18, Color.BLACK, true);
        addButton(body, "复制匿名诊断日志", () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("M02S Wi-Fi-only diagnostics", diagnostics.toString()));
            status("已复制诊断日志");
        });
        logView = addText(body, "", 13, Color.rgb(35,44,50), false);
        logView.setGravity(Gravity.START);
        addText(body, "注意：新文件出现时间是手机首次发现的时间，不等于实体拍照按钮按下时间；此版仅验证取图，不接OCR/TTS。", 13, Color.DKGRAY, false);
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
        if (missing.isEmpty()) return true;
        requestPermissions(missing.toArray(new String[0]), REQUEST_WIFI_PERMISSIONS);
        status("请授予本地Wi-Fi诊断权限，再点击验证");
        return false;
    }

    private static final class Target {
        final Network network;
        final String gateway;
        Target(Network n, String h) { network = n; gateway = h; }
    }

    private static String unquote(String text) {
        if (text == null || text.equals("<unknown ssid>")) return "";
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\""))
            return text.substring(1, text.length() - 1);
        return text;
    }

    /** No scanning and no guesses: an ALREADY CONNECTED exact Wi-Fi SSID only. */
    private Target findConnectedTarget(String wanted) {
        if (wanted == null || wanted.isEmpty() || connectivity == null) return null;
        try {
            for (Network net : connectivity.getAllNetworks()) {
                NetworkCapabilities cap = connectivity.getNetworkCapabilities(net);
                if (cap == null || !cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || !(cap.getTransportInfo() instanceof WifiInfo)) continue;
                WifiInfo wifi = (WifiInfo) cap.getTransportInfo();
                if (!wanted.equals(unquote(wifi.getSSID()))) continue;
                LinkProperties properties = connectivity.getLinkProperties(net);
                if (properties == null) continue;
                for (RouteInfo route : properties.getRoutes()) {
                    InetAddress gateway = route.getGateway();
                    if (gateway == null || gateway.getAddress().length != 4) continue;
                    try {
                        String safeHost = ProbePolicy.checkedLocalHost(gateway.getHostAddress());
                        return new Target(net, safeHost);
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        } catch (SecurityException | IllegalStateException problem) {
            log("系统读取已连接Wi-Fi信息受限；错误类型="
                    + problem.getClass().getSimpleName());
        }
        return null;
    }

    private String requestedSsid() {
        String text = ssidInput.getText().toString().trim();
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 32 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            status("请输入系统Wi-Fi设置中完整眼镜SSID（8～32字节）");
            return null;
        }
        return text;
    }

    private static M02sPhotoListReader.Result readPhotoList(Network net, String host)
            throws Exception {
        M02sPhotoTransport.Response response = M02sPhotoTransport.getList(net, host);
        if (response.httpCode != 200) throw new java.io.IOException("HTTP " + response.httpCode);
        JSONObject root = new JSONObject(new String(response.body, StandardCharsets.UTF_8));
        if (root.optInt("result", -1234) != 0 || !root.has("info"))
            throw new java.io.IOException("不是已验证的M02S原厂JSON照片列表");
        return M02sPhotoListReader.parse(response.body);
    }

    private void verifyTarget() {
        if (!ensurePermission()) return;
        if (watching || inFlight || verifying) {
            status("有检查任务进行中，请先停止或等上次验证结束"); return;
        }
        String wanted = requestedSsid();
        if (wanted == null) return;
        Target target = findConnectedTarget(wanted);
        if (target == null) {
            targetNetwork = null;
            targetHost = null;
            targetSsid = null;
            log("未发现与输入SSID完全一致、且路由具有安全网关的已连接Wi-Fi Network；未发HTTP请求");
            status("未发现眼镜热点连接。请在系统Wi-Fi设置连接热点并打开位置服务，再重新验证");
            return;
        }
        final long run = ++epoch;
        verifying = true;
        status("发现精确SSID匹配的Wi-Fi，正在只读验证原厂照片列表");
        io.execute(() -> {
            try {
                M02sPhotoListReader.Result list = readPhotoList(target.network, target.gateway);
                ui.post(() -> {
                    if (destroyed || run != epoch) return;
                    verifying = false;
                    targetNetwork = target.network;
                    targetHost = target.gateway;
                    targetSsid = wanted;
                    log("纯Wi-Fi原厂接口验证通过：HTTP200；图片候选=" + list.eligibleImages
                            + "；未记录SSID、文件名或图片");
                    status("眼镜Wi-Fi已验证。现在可以开始自动取图");
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    if (destroyed || run != epoch) return;
                    verifying = false;
                    targetNetwork = null;
                    targetHost = null;
                    targetSsid = null;
                    log("Wi-Fi已连接，但原厂照片接口验证失败，错误类型="
                            + ex.getClass().getSimpleName() + "；未冒认眼镜AP");
                    status("目标热点未通过原厂照片接口验证；纯Wi-Fi取图暂不可用");
                });
            }
        });
    }

    private boolean targetStillPresent() {
        Target target = findConnectedTarget(targetSsid);
        return target != null && target.network.equals(targetNetwork)
                && target.gateway.equals(targetHost);
    }

    private void beginWatch() {
        if (!ensurePermission()) return;
        if (watching || verifying || inFlight) {
            status("已有Wi-Fi检查任务运行，请勿重复开始"); return;
        }
        if (targetNetwork == null || targetHost == null || targetSsid == null
                || !targetSsid.equals(requestedSsid()) || !targetStillPresent()) {
            status("请先验证当前已连接眼镜Wi-Fi，不能自动猜测目标");
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
        io.shutdownNow();
        photoInMemory = null;
        super.onDestroy();
    }
}
