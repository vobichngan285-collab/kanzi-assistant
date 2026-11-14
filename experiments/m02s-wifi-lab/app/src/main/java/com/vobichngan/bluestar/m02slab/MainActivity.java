package com.vobichngan.bluestar.m02slab;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.MacAddress;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.view.SurfaceView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Independent, non-destructive M02S connectivity experiment.
 *
 * BLE: discovery and read-only service discovery ONLY. No guesswork BLE writes.
 * Wi-Fi: P2P or AP manual connection, 120s local read-only HTTP probes.
 * Never replaces Blue Star Guide, the HeyCyan app, or the glasses firmware.
 */
public final class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<WifiP2pDevice> peers = new ArrayList<>();
    private final List<BluetoothDevice> bleDevices = new ArrayList<>();
    private final List<Boolean> bleCanConnect = new ArrayList<>();
    private final Set<String> seenBle = new HashSet<>();
    private final StringBuilder logData = new StringBuilder();

    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;
    private ConnectivityManager connectivity;
    private WifiManager wifiManager;
    private ConnectivityManager.NetworkCallback apCallback;
    private volatile Network apNetwork;
    private WifiManager.WifiLock wifiLock;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt gatt;
    private boolean gattConnecting = false;
    private boolean gattConnected = false;
    private boolean servicesReady = false;
    private boolean notificationMonitor = false;
    private int notificationCount = 0;
    private long notificationStarted = 0L;
    private Runnable notificationTimeout;
    private Runnable descriptorTimeout;
    private BluetoothGattCharacteristic pendingSubscription;
    private final ArrayDeque<BluetoothGattCharacteristic> notificationQueue = new ArrayDeque<>();
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID COMMAND_SERVICE = UUID.fromString("0000aa12-0000-1000-8000-00805f9b34fb");
    private static final UUID COMMAND_WRITE = UUID.fromString("0000aa13-0000-1000-8000-00805f9b34fb");
    private static final UUID COMMAND_NOTIFY = UUID.fromString("0000aa14-0000-1000-8000-00805f9b34fb");
    private static final UUID PHOTO_NOTIFY = UUID.fromString("0000aa15-0000-1000-8000-00805f9b34fb");
    private boolean commandNotifyReady;
    private String writeInFlight;
    private Runnable writeTimeout;
    private long writeStartedAt;
    private long lastSentAt;

    /** Session-scoped values only: SSID and OEM pairing key never enter logs. */
    private enum WifiMode { NONE, P2P, AP }
    private volatile WifiMode wifiMode = WifiMode.NONE;
    private String targetWifiSsid;
    private boolean awaitingSsid;
    private boolean wifiConnecting;
    private volatile boolean wifiVerified;
    private boolean matchedPeerConnectStarted;
    private volatile String verifiedHost;
    private long wifiStarted;
    private int wifiSessionToken;
    private int p2pPollNumber;
    private Runnable wifiDeadline;
    private Runnable p2pPoll;
    private Runnable apScanTimer;
    private long apScanStartedAt;
    private int apScanPolls;
    private int apNetworkRequests;
    private long apScanRequestedAtUs;
    private boolean apScanResultsUpdated;
    private long apFreshFirstSeenAtMs;
    private String apFreshFirstBssid;
    private WifiManager.LocalOnlyConnectionFailureListener apFailureListener;
    private int apLocalFailureReason = -1;
    private static final String OEM_WIFI_KEY = "12345678"; // observed OEM APK constant

    private int gattAttempt = 0;
    private long gattStarted = 0;
    private Runnable gattTimeout;
    private static final long GATT_TIMEOUT_MS = 15_000L;
    private boolean receiverRegistered = false;
    private boolean keepRunning = false;
    private volatile boolean destroyed = false;
    private int probes = 0, successes = 0;
    private long keepStarted;
    private long connectionStarted;
    private String lastFingerprint = "";
    private String lastHost = "";
    private TextView status, logView;
    private EditText ipField, ssidField, passField;
    private Spinner peerSpinner, bleSpinner;
    private ArrayAdapter<String> peerAdapter, bleAdapter;
    private Spinner photoSpinner;
    private ArrayAdapter<String> photoAdapter;
    private ImageView photoPreview;
    private List<M02sPhotoPolicy.Candidate> photoCandidates = Collections.emptyList();
    /** The original image is held in RAM only, never saved to disk or cloud. */
    private byte[] lastPhotoInMemory;
    private boolean photoBusy;
    /** v0.1.8: shutter first without AP, then request AP for read-only transfer. */
    private boolean oneClickActive;
    private int oneClickGeneration;
    private int oneClickPollNumber;
    private boolean oneClickWaitingWrite;
    private boolean oneClickAwaitingAp;
    private boolean oneClickApOwned;
    private boolean oneClickHasBaseline;
    private int oneClickPhotoNotices;
    private long oneClickPollStartedAt;
    private long oneClickShutterAt;
    private Runnable oneClickTimer;
    private M02sFreshPhotoPolicy.Selection oneClickPrevious;
    private List<M02sPhotoPolicy.Candidate> oneClickBefore = Collections.emptyList();
    private List<M02sPhotoPolicy.Candidate> oneClickCapturedList = Collections.emptyList();
    /** In-RAM only: the previous successful AP snapshot can be NEXT shot's baseline. */
    private List<M02sPhotoPolicy.Candidate> previousFinishedList = Collections.emptyList();
    private boolean previousFinishedListValid;
    private Network oneClickBoundNetwork;
    private String oneClickBoundHost;

    /** Experimental E09 family protocol; NOT yet proven on M02S. */
    private boolean e09BlePhotoActive;
    private final M02sE09PhotoAssembler e09Assembler = new M02sE09PhotoAssembler();
    private long e09PhotoStartedAt;
    private Runnable e09PhotoTimeout;
    private boolean e09LiveRequested;
    private boolean e09LiveProbing;
    private boolean e09ProcessBound;
    private MediaPlayer e09Player;
    private SurfaceView e09Surface;
    private String e09LiveLastHost;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(intent.getAction())) {
                requestPeers();
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(intent.getAction())) {
                log("系统 Wi-Fi Direct 连接状态发生变化");
                requestConnectionInfo();
            } else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                log("系统 Wi-Fi Direct：" + (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED ? "已启用" : "未启用"));
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                if (wifiMode == WifiMode.AP && wifiConnecting
                        && apScanRequestedAtUs > 0 && apNetworkRequests == 0) {
                    boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    apScanResultsUpdated |= updated;
                    log(updated
                            ? "AP阶段1：系统Wi-Fi扫描完成并报告结果更新，核对是否出现请求后的目标时间戳"
                            : "AP阶段1：系统扫描未更新（可能受限流或硬件影响），旧缓存不算AP就绪");
                    if (apScanTimer != null) ui.removeCallbacks(apScanTimer);
                    scanForTargetAp(wifiSessionToken);
                }
            }
        }
    };

    private final ScanCallback bleCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            String name = result.getScanRecord() != null ?
                    result.getScanRecord().getDeviceName() : null;
            try {
                if ((name == null || name.isEmpty()) && permitted()) name = device.getName();
            } catch (SecurityException ignored) { }
            if (name == null) name = "";
            name = name.trim();
            // Keep the diagnostic focused: the previous version listed >30 unrelated
            // nearby appliances and made selecting the actual glasses difficult.
            String upper = name.toUpperCase(Locale.ROOT);
            if (!(upper.startsWith("M02S") || upper.startsWith("HEYCYAN")
                    || upper.startsWith("STARGLASSES"))) return;
            final boolean connectable = result.isConnectable();
            final String label = name + " / " + address + " / RSSI " + result.getRssi()
                    + " / 可连接=" + connectable;
            ui.post(() -> {
                if (destroyed || !seenBle.add(address)) return;
                bleDevices.add(device);
                bleCanConnect.add(connectable);
                bleAdapter.add(label);
                log("发现目标眼镜：" + label);
            });
        }
        @Override public void onScanFailed(int errorCode) {
            log("BLE 扫描失败，代码 " + errorCode);
        }
    };

    private final Runnable holdTick = new Runnable() {
        @Override public void run() {
            if (!keepRunning || destroyed) return;
            long elapsed = SystemClock.elapsedRealtime() - keepStarted;
            if (elapsed >= 120_000L) {
                finishHold("120 秒观察结束");
                return;
            }
            if (!wifiVerified || verifiedHost == null || wifiMode == WifiMode.NONE) {
                finishHold("目标网络状态丢失，已停止探测");
                return;
            }
            final String host = verifiedHost;
            final Network bound = wifiMode == WifiMode.AP ? apNetwork : null;
            final int token = wifiSessionToken;
            final int sequence = ++probes;
            final long seconds = elapsed / 1000;
            status.setText("保持连接中：" + seconds + " / 120 秒；成功 " + successes + " / " + probes);
            io.execute(() -> {
                if (token != wifiSessionToken || !wifiVerified) return;
                probeLocalFileList(host, bound, sequence);
            });
            ui.postDelayed(this, 3000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        wifiManager = manager;
        wifiLock = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BlueStarM02S:Keepalive");
        wifiLock.setReferenceCounted(false);
        p2p = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        if (p2p != null) channel = p2p.initialize(this, Looper.getMainLooper(),
                () -> log("P2P 通道断开；可重新扫描"));
        buildUi();
        IntentFilter f = new IntentFilter();
        f.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        receiverRegistered = true;
        log("v0.1.11 开源E09实验：AA15蓝牙JPEG直传+APP_LIVE 0x67/0x30 RTSP；旧AP拍照测试保留");
        log("照片只从眼镜AP按原厂接口GET至内存；不保存相册、不上传云端、不删文件");
        requestPermissionsIfNeeded();
        updateNetworkStatus();
    }

    private void buildUi() {
        int pad = dp(14);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);
        body.setBackgroundColor(Color.rgb(245, 248, 252));
        scroll.addView(body);

        body.addView(text("蓝星 M02S 开源E09直传实验 v0.1.11", 23, true));
        body.addView(text("独立实验，不修改正式蓝星向导。Wi-Fi 网络名称/密码不会写进日志。", 14, false));
        status = text("蓝牙未连接 / 眼镜 Wi-Fi 未建立", 17, true);
        status.setPadding(0, dp(12), 0, dp(10));
        body.addView(status);

        section(body, "① 蓝牙：连接眼镜并开启回执监听");
        bleSpinner = new Spinner(this);
        bleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        bleAdapter.add("选择扫描到的 M02S 眼镜");
        bleSpinner.setAdapter(bleAdapter);
        body.addView(bleSpinner);
        addRow(body, button("扫描 M02S", this::scanBle),
                button("连接 LE 1M", () -> connectGattReadOnly(false)));
        addRow(body, button("开启蓝牙监听（120秒）", this::startNotificationMonitor),
                button("断开蓝牙", () -> closeGatt("用户主动断开")));

        section(body, "② 目标 Wi-Fi：点一次后自动识别 SSID 并尝试连接");
        addRow(body, button("P2P 精准连接", () -> requestTargetWifi(WifiMode.P2P)),
                button("AP 热点精准连接", () -> requestTargetWifi(WifiMode.AP)));
        addRow(body, button("取消 Wi-Fi 连接", () -> stopWifiSession("用户取消")),
                button("查看网络状态", this::updateNetworkStatus));
        body.addView(text("先等 AA14 订阅 status=0，再选择一种方式。程序只匹配眼镜蓝牙"
                + "返回的目标 SSID，绝不连接附近惠普打印机；AP 方式会弹系统授权。"
                + "当前 M02S 的 AP/P2P 模式未知，两种方式不会自动轮流尝试。", 14, false));

        section(body, "③ 确认 Wi-Fi 已建立后，读取本地文件列表");
        ipField = field("等待系统确认设备 IP", "");
        ipField.setEnabled(false);
        body.addView(ipField);
        addRow(body, button("只读文件列表", this::readOnceVerified),
                button("保持连接观察 120秒", this::startHold));
        addRow(body, button("停止观察", () -> finishHold("用户停止")),
                button("复制诊断", this::copyDiagnostic));
        body.addView(text("仅在目标 AP 系统确认连通后才能访问文件接口；不读未知地址、不自动断开Wi-Fi。", 14, false));

        section(body, "④ 原始照片下载（内存中验证，不保存相册）");
        addRow(body, button("获取最新照片（自动）", () -> loadPhotoList(true)),
                button("只解析照片列表", () -> loadPhotoList(false)));
        photoSpinner = new Spinner(this);
        photoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        photoAdapter.add("选择候选照片（自动判断失败时手动选）");
        photoSpinner.setAdapter(photoAdapter);
        body.addView(photoSpinner);
        addRow(body, button("下载选中照片（手动）", this::downloadChosenPhoto),
                button("清除内存预览", this::clearPhotoUi));
        body.addView(text("优先按官方JSON列表的创建时间选最新照片；时间不可靠时不猜，"
                + "可手动选候选编号。仅用已连接的眼镜AP下载JPEG/PNG/WebP原图，"
                + "检查图片格式与分辨率，限制最大32MB。不发完成通知、不删照片、不上传。", 14, false));
        photoPreview = new ImageView(this);
        photoPreview.setContentDescription("下载到内存的眼镜照片预览");
        photoPreview.setAdjustViewBounds(true);
        photoPreview.setMinimumHeight(dp(90));
        photoPreview.setMaxHeight(dp(280));
        photoPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        body.addView(photoPreview);

        section(body, "⑤ v0.1.10 拍照→新扫描验证→AP取图");
        addRow(body, button("拍照→验证AP→取原图", this::startOneClick),
                button("取消本次实验", () -> stopOneClick("用户取消本次拍照取图")));
        body.addView(text("先连蓝牙并开启AA14通知，不预先连接AP。只拍照一次，"
                + "等待本次AP信息请求后的新扫描时间戳；同一目标热点再等待1.8秒后仅发一次SSID连接请求。"
                + "Android16会尽量记录具体失败原因；不立刻重连、不猜热点、不重复拍照。"
                + "首次下载的最新图片仍需人工核对是否刚拍摄。", 14, false));

        section(body, "⑥ E09开源协议独立测试｜免相册导入");
        body.addView(text("下面是按开源 ai-smart-glasses-e09-reverse 移植的独立实验："
                + "蓝牙0x22/0x31尝试从AA15直接接收JPEG；0x67/0x30尝试打开AP直播RTSP。"
                + "与M02S的兼容性尚未确认，不会自动循环发命令。", 14, false));
        addRow(body, button("E09 蓝牙直传照片", this::startE09BlePhoto),
                button("取消蓝牙接收", this::stopE09BlePhoto));
        addRow(body, button("E09 RTSP直播建连", this::startE09Live),
                button("播放RTSP画面", this::playE09Preview));
        addRow(body, button("停止直播并安全退出", this::endE09Live),
                button("单独发送不删除结束指令", this::sendE09SafeFinish));
        body.addView(text("直播仅访问眼镜专属已验证AP网关 /h264；收到RTSP控制应答不等于能播放画面。"
                + "结束通知仅允许0x44/0x30/0x01，不提供删除媒体的0x44/0x30/0x00。"
                + "无照片保存、无云上传、无固件命令。", 14, false));
        e09Surface = new SurfaceView(this);
        e09Surface.setMinimumHeight(dp(190));
        body.addView(e09Surface, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));

        // Retained internally for legacy diagnostics; never displayed, so
        // unrelated nearby P2P devices cannot be selected accidentally.
        peerSpinner = new Spinner(this);
        peerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        peerAdapter.add("已禁用人工选择附近 P2P 设备");
        peerSpinner.setAdapter(peerAdapter);
        ssidField = field("内部网络标识（不显示）", "");
        passField = field("内部密钥（不显示）", "");

        section(body, "本地诊断日志");
        logView = text("", 14, false);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logView.setBackgroundColor(Color.WHITE);
        body.addView(logView);
        setContentView(scroll);
    }

    private int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int sp, boolean strong) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(15, 35, 60));
        if (strong) v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setLineSpacing(dp(2), 1f);
        return v;
    }
    private void section(LinearLayout parent, String title) {
        TextView v = text(title, 18, true);
        v.setPadding(0, dp(14), 0, dp(6));
        parent.addView(v);
    }
    private EditText field(String hint, String initial) {
        EditText v = new EditText(this);
        v.setSingleLine(true);
        v.setTextSize(16);
        v.setHint(hint);
        v.setText(initial);
        v.setContentDescription(hint);
        return v;
    }
    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void addRow(LinearLayout parent, Button a, Button b) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        r.addView(a, p);
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        parent.addView(r);
    }

    private void log(String line) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui.post(() -> log(line)); return;
        }
        if (destroyed) return;
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT)
                .format(new java.util.Date());
        logData.append(time).append(" ").append(line).append("\n");
        if (logData.length() > 26_000) logData.delete(0, 8_000);
        if (logView != null) logView.setText(logData.toString());
    }

    private boolean permitted() {
        if (Build.VERSION.SDK_INT >= 31 &&
                (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) return false;
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) return false;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED;
    }
    private void requestPermissionsIfNeeded() {
        if (permitted()) return;
        ArrayList<String> list = new ArrayList<>();
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        requestPermissions(list.toArray(new String[0]), 10);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == 10) log(permitted() ? "蓝牙/Wi-Fi 诊断权限已授权" :
                "尚有权限未授权，蓝牙与 P2P 扫描可能失败");
    }
    private boolean ensurePermission() {
        if (permitted()) return true;
        log("请先授予蓝牙、附近 Wi-Fi 设备和位置权限，再重试");
        requestPermissionsIfNeeded();
        return false;
    }

    private void scanBle() {
        if (!ensurePermission()) return;
        if (gattConnecting) {
            log("已有 GATT 连接尝试，先等待结果或点主动断开");
            return;
        }
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter a = bm == null ? null : bm.getAdapter();
        if (a == null || !a.isEnabled()) {
            log("未开启蓝牙，先在系统设置打开蓝牙"); return;
        }
        try {
            if (bleScanner != null) bleScanner.stopScan(bleCallback);
            bleScanner = a.getBluetoothLeScanner();
            if (bleScanner == null) { log("当前设备没有 BLE Scanner"); return; }
            seenBle.clear();
            bleDevices.clear();
            bleCanConnect.clear();
            bleAdapter.clear();
            bleAdapter.add("选择扫描到的蓝牙设备");
            bleScanner.startScan(bleCallback);
            log("开始目标眼镜 BLE 扫描 10 秒；隐藏无关设备，不发送命令");
            ui.postDelayed(() -> {
                if (bleScanner == null) return;
                try { bleScanner.stopScan(bleCallback); }
                catch (SecurityException ignored) {}
                bleScanner = null;
                log("BLE 扫描结束，共发现 " + bleDevices.size() + " 台设备");
            }, 10_000);
        } catch (SecurityException | IllegalStateException ex) {
            log("BLE 扫描异常：" + ex.getClass().getSimpleName());
        }
    }
    private void stopBleScanForConnection() {
        if (bleScanner == null) return;
        try { bleScanner.stopScan(bleCallback); }
        catch (SecurityException | IllegalStateException ex) {
            log("停止 BLE 扫描返回：" + ex.getClass().getSimpleName());
        }
        bleScanner = null;
        log("扫描已停止；避免和 GATT 建连竞争");
    }

    /** Only called from the main thread; late GATT callbacks are ignored by token. */
    private void closeGatt(String reason) {
        stopOneClick("蓝牙连接结束，本次一键拍照任务取消");
        stopNotificationMonitor("GATT 连接结束");
        if (wifiConnecting && !wifiVerified) stopWifiSession("蓝牙连接已断开");
        clearWritePending();
        lastSentAt = 0L;
        gattAttempt++;
        gattConnecting = false;
        gattConnected = false;
        servicesReady = false;
        if (gattTimeout != null) {
            ui.removeCallbacks(gattTimeout);
            gattTimeout = null;
        }
        BluetoothGatt prior = gatt;
        gatt = null;
        if (prior != null) {
            try { prior.disconnect(); } catch (Exception ignored) { }
            try { prior.close(); } catch (Exception ignored) { }
        }
        if (!destroyed) {
            log("GATT 释放：" + reason);
            status.setText("蓝牙：" + reason);
        }
    }

    /**
     * First try explicit BLE/1M. Optional manual control compares legacy
     * Android default transport, without automatic retries or vendor writes.
     */
    private void connectGattReadOnly(boolean compat) {
        if (!ensurePermission()) return;
        int index = bleSpinner.getSelectedItemPosition() - 1;
        if (index < 0 || index >= bleDevices.size()) {
            log("先扫描并选中 M02S 眼镜");
            return;
        }
        BluetoothDevice device = bleDevices.get(index);
        if (!bleCanConnect.get(index)) {
            log("注意：该设备广播不标记为可连接；GATT 可能失败");
        }
        stopBleScanForConnection();
        closeGatt("准备新建连");
        int token = ++gattAttempt;
        gattConnecting = true;
        gattStarted = SystemClock.elapsedRealtime();
        String mode = compat ? "Android 默认传输（兼容）" : "TRANSPORT_LE + PHY_LE_1M";
        try {
            log("GATT 发起：" + mode + "；autoConnect=false；仅发现服务");
            log("设备类型=" + device.getType() + "；配对状态=" + device.getBondState()
                    + "；扫描广播可连接=" + bleCanConnect.get(index));
            status.setText("蓝牙连接中：" + mode + "（最多15秒）");
            BluetoothGattCallback cb = new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt connection,
                                                               int code, int state) {
                    long cost = SystemClock.elapsedRealtime() - gattStarted;
                    ui.post(() -> {
                        if (destroyed || token != gattAttempt || connection != gatt) return;
                        log("GATT 回调：status=" + code + "（"
                                + GattDiagnosticPolicy.statusDescription(code) + "），newState="
                                + state + "，耗时=" + cost + "ms");
                        if (code != BluetoothGatt.GATT_SUCCESS) {
                            closeGatt("连接失败 status=" + code);
                            return;
                        }
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            gattConnected = true;
                            gattConnecting = false;
                            if (gattTimeout != null) ui.removeCallbacks(gattTimeout);
                            gattTimeout = null;
                            status.setText("蓝牙已连接，正在发现服务");
                            log("GATT 已连接；开始只读发现服务，不发送特征写入");
                            try {
                                if (!connection.discoverServices()) {
                                    closeGatt("系统拒绝启动服务发现");
                                }
                            } catch (SecurityException ex) {
                                closeGatt("服务发现权限被拒绝");
                            }
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            closeGatt("设备已断开");
                        }
                    });
                }
                @Override public void onServicesDiscovered(BluetoothGatt connection, int code) {
                    ui.post(() -> {
                        if (destroyed || token != gattAttempt || connection != gatt) return;
                        log("GATT 服务发现：status=" + code);
                        if (code != BluetoothGatt.GATT_SUCCESS) {
                            closeGatt("服务发现失败 " + code);
                            return;
                        }
                        try {
                            List<BluetoothGattService> services = connection.getServices();
                            servicesReady = true;
                            log("GATT 服务发现成功，总数 " + services.size());
                            for (BluetoothGattService svc : services) {
                                log("服务 UUID " + svc.getUuid()
                                        + "；特征数量 " + svc.getCharacteristics().size());
                            }
                            status.setText("蓝牙已连接：GATT 服务 " + services.size() + " 个");
                            log("BLE 只读测试成功。可点击「查看特征」和「监听拍照通知」进行下一步。");
                            inspectGattCharacteristics();
                        } catch (SecurityException ex) {
                            closeGatt("无法读取 GATT 服务列表");
                        }
                    });
                }
                @Override public void onCharacteristicWrite(BluetoothGatt connection,
                                                            BluetoothGattCharacteristic c,
                                                            int code) {
                    ui.post(() -> onCommandWriteResult(connection, c, code, token));
                }
                @Override public void onDescriptorWrite(BluetoothGatt connection,
                                                        BluetoothGattDescriptor descriptor,
                                                        int code) {
                    ui.post(() -> handleDescriptorWrite(connection, descriptor, code, token));
                }
                @Override public void onCharacteristicChanged(BluetoothGatt connection,
                                               BluetoothGattCharacteristic characteristic,
                                               byte[] value) {
                    final byte[] copy = value == null ? new byte[0] : value.clone();
                    ui.post(() -> onGattNotification(connection, characteristic, copy, token));
                }
                @Override @SuppressWarnings("deprecation")
                public void onCharacteristicChanged(BluetoothGatt connection,
                                               BluetoothGattCharacteristic characteristic) {
                    final byte[] value = characteristic.getValue();
                    final byte[] copy = value == null ? new byte[0] : value.clone();
                    ui.post(() -> onGattNotification(connection, characteristic, copy, token));
                }
            };
            BluetoothGatt created = compat
                    ? device.connectGatt(this, false, cb)
                    : device.connectGatt(this, false, cb,
                                         BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M);
            if (created == null) {
                closeGatt("connectGatt 立即返回 null");
                return;
            }
            gatt = created;
            gattTimeout = () -> {
                if (token != gattAttempt || !gattConnecting) return;
                long ms = SystemClock.elapsedRealtime() - gattStarted;
                log("GATT 15秒超时，至今没有成功连接回调；实际耗时=" + ms + "ms");
                closeGatt("15秒超时（与此前60秒等待相比提前结束）");
            };
            ui.postDelayed(gattTimeout, GATT_TIMEOUT_MS);
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
            log("GATT 建连立即抛错：" + e.getClass().getSimpleName() + " " + e.getMessage());
            closeGatt("无法发起连接");
        }
    }
    private void inspectGattCharacteristics() {
        if (!gattConnected || !servicesReady || gatt == null) {
            log("请先连接 M02S 并等待 GATT 服务发现成功");
            return;
        }
        try {
            for (BluetoothGattService service : gatt.getServices()) {
                String serviceId = service.getUuid().toString();
                if (!GattProtocolPolicy.isVendorService(serviceId)) continue;
                log("---- 厂商服务 " + serviceId + " ----");
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    String p = GattProtocolPolicy.properties(c.getProperties());
                    boolean hasCccd = c.getDescriptor(CCCD) != null;
                    log("特征 UUID " + c.getUuid() + "；能力 " + p
                            + "；CCCD=" + hasCccd + "；描述符数="
                            + c.getDescriptors().size());
                    for (BluetoothGattDescriptor d : c.getDescriptors()) {
                        log("  描述符 " + d.getUuid()
                                + (CCCD.equals(d.getUuid()) ? "（标准订阅配置）" : ""));
                    }
                }
            }
            log("特征权限检查完成。仅列举元数据，未读取或写入厂商特征值。");
        } catch (SecurityException ex) {
            log("读取特征元数据失败：权限不足");
        }
    }

    private void startNotificationMonitor() {
        if (!gattConnected || !servicesReady || gatt == null) {
            log("无法开始监听：请先完成 LE 1M 连接和服务发现");
            return;
        }
        if (notificationMonitor) {
            log("已经在监听；无需重复订阅");
            return;
        }
        notificationQueue.clear();
        for (BluetoothGattService service : gatt.getServices()) {
            if (!GattProtocolPolicy.isVendorService(service.getUuid().toString())) continue;
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                if (GattProtocolPolicy.canSubscribe(
                        c.getProperties(), c.getDescriptor(CCCD) != null)) {
                    notificationQueue.addLast(c);
                }
            }
        }
        if (notificationQueue.isEmpty()) {
            log("AE00/AA12 没有带标准 CCCD 的 NOTIFY/INDICATE 特征；仅能记录属性");
            return;
        }
        notificationMonitor = true;
        commandNotifyReady = false;
        notificationCount = 0;
        notificationStarted = SystemClock.elapsedRealtime();
        log("监听开始：等待眼镜反馈；可订阅特征 " + notificationQueue.size()
                + " 个。120秒后自动停止记录。");
        status.setText("蓝牙监听中：按眼镜拍照键");
        notificationTimeout = () -> stopNotificationMonitor("120秒监听期结束");
        ui.postDelayed(notificationTimeout, 120_000);
        subscribeNextCharacteristic();
    }

    private void subscribeNextCharacteristic() {
        if (!notificationMonitor || gatt == null) return;
        if (descriptorTimeout != null) {
            ui.removeCallbacks(descriptorTimeout);
            descriptorTimeout = null;
        }
        while (!notificationQueue.isEmpty()) {
            BluetoothGattCharacteristic c = notificationQueue.removeFirst();
            BluetoothGattDescriptor descriptor = c.getDescriptor(CCCD);
            if (descriptor == null) continue;
            try {
                if (!gatt.setCharacteristicNotification(c, true)) {
                    log("本机拒绝通知订阅：" + c.getUuid());
                    continue;
                }
                byte[] value = (c.getProperties() &
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                pendingSubscription = c;
                boolean queued;
                if (Build.VERSION.SDK_INT >= 33) {
                    queued = gatt.writeDescriptor(descriptor, value) == 0; // BluetoothStatusCodes.SUCCESS
                } else {
                    @SuppressWarnings("deprecation")
                    boolean accepted = descriptor.setValue(value);
                    @SuppressWarnings("deprecation")
                    boolean written = accepted && gatt.writeDescriptor(descriptor);
                    queued = written;
                }
                if (!queued) {
                    log("CCCD 订阅写入未受理：" + c.getUuid());
                    pendingSubscription = null;
                    continue;
                }
                log("申请标准 CCCD 通知订阅：" + c.getUuid());
                final BluetoothGattCharacteristic expected = c;
                descriptorTimeout = () -> {
                    if (!notificationMonitor || pendingSubscription != expected) return;
                    pendingSubscription = null;
                    notificationQueue.clear(); // avoid overlapping GATT descriptor operations
                    log("CCCD 写入 5 秒没有回调；停止后续订阅，避免并发 GATT 操作");
                };
                ui.postDelayed(descriptorTimeout, 5000);
                return;
            } catch (SecurityException | IllegalStateException e) {
                pendingSubscription = null;
                log("标准通知订阅失败：" + c.getUuid() + " / "
                        + e.getClass().getSimpleName());
            }
        }
        log("订阅申请已结束；请在监听期间按一次眼镜实体拍照键。");
    }

    private void handleDescriptorWrite(BluetoothGatt connection,
                                       BluetoothGattDescriptor descriptor, int code, int token) {
        if (destroyed || token != gattAttempt || connection != gatt
                || !notificationMonitor || pendingSubscription == null) return;
        if (descriptor == null || !CCCD.equals(descriptor.getUuid())
                || descriptor.getCharacteristic() != pendingSubscription) return;
        if (descriptorTimeout != null) ui.removeCallbacks(descriptorTimeout);
        descriptorTimeout = null;
        log("CCCD 订阅回调：" + pendingSubscription.getUuid() + " status=" + code);
        if (code == BluetoothGatt.GATT_SUCCESS
                && COMMAND_NOTIFY.equals(pendingSubscription.getUuid())) {
            commandNotifyReady = true;
            log("AA14 指令响应通知已就绪：允许手动发送白名单内官方命令");
        }
        pendingSubscription = null;
        if (code != BluetoothGatt.GATT_SUCCESS) {
            log("当前特征订阅失败，不推断眼镜固件故障");
        }
        subscribeNextCharacteristic();
    }

    private void onGattNotification(BluetoothGatt connection,
                                    BluetoothGattCharacteristic c, byte[] value, int token) {
        if (destroyed || token != gattAttempt || connection != gatt
                || !notificationMonitor || c == null) return;
        if (c.getService() == null
                || !GattProtocolPolicy.isVendorService(c.getService().getUuid().toString())) return;
        notificationCount++;
        if (PHOTO_NOTIFY.equals(c.getUuid()) && e09BlePhotoActive) {
            M02sE09PhotoAssembler.Step step = e09Assembler.receive(value);
            if (step == M02sE09PhotoAssembler.Step.START) {
                log("E09 AA15 PHOTO_START：收到宣布长度="
                        + e09Assembler.declaredLength() + "字节（尚未证实可解码）");
            } else if (step == M02sE09PhotoAssembler.Step.ERROR) {
                log("E09 AA15 JPEG接收中止：" + e09Assembler.lastError());
                stopE09BlePhoto();
            } else if (step == M02sE09PhotoAssembler.Step.COMPLETE) {
                byte[] jpeg = e09Assembler.result();
                Bitmap bitmap = jpeg == null ? null : BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
                if (bitmap != null) {
                    lastPhotoInMemory = jpeg;
                    photoPreview.setImageBitmap(bitmap);
                    log("E09 AA15 JPEG直传成功："
                            + bitmap.getWidth() + "×" + bitmap.getHeight()
                            + "；" + jpeg.length + "字节；分片包数=" + e09Assembler.packets()
                            + "；用时=" + (SystemClock.elapsedRealtime()-e09PhotoStartedAt)
                            + "ms；仅内存预览");
                    status.setText("E09蓝牙JPEG已成功解码；清晰度请人工核对");
                } else {
                    log("E09 AA15 收到完整分片但无法JPEG解码，未宣称照片成功");
                }
                stopE09BlePhoto();
            }
            return;
        }
        M02sBleWire.ParsedFrame parsed = M02sBleWire.parseIncoming(value);
        if (parsed != null && parsed.command == 0x25 && parsed.checksumOk) {
            acceptWifiSsidFrame(parsed);
        }
        // Never hide shot-related responses behind the generic first-40 log limit.
        boolean importantPhotoNotice = oneClickActive && parsed != null && parsed.checksumOk
                && (parsed.command == 0x22 || parsed.command == 0x42)
                && (oneClickWaitingWrite || oneClickShutterAt > 0);
        if (importantPhotoNotice) {
            oneClickPhotoNotices++;
            if (oneClickPhotoNotices <= 8) {
                log("拍照阶段AA14照片通知#" + oneClickPhotoNotices
                        + "：" + parsed.safeSummary()
                        + "（设备响应不等于实际拍照，需核对实物）");
            }
        }
        if (GattProtocolPolicy.shouldLogPayload(notificationCount) && !importantPhotoNotice) {
            long elapsed = SystemClock.elapsedRealtime() - notificationStarted;
            String detail = parsed == null
                    ? "未组成完整 AC55 帧（内容隐藏，可能是分片）"
                    : parsed.safeSummary();
            log("通知#" + notificationCount + " +" + elapsed + "ms；特征 "
                    + c.getUuid() + "；长度 " + value.length + "；" + detail);
        } else if (notificationCount == 41) {
            log("通知数量较多，后续只计数不打印，避免大量日志/敏感数据");
        }
    }

    private void stopNotificationMonitor(String reason) {
        boolean wasActive = notificationMonitor;
        notificationMonitor = false;
        commandNotifyReady = false;
        notificationQueue.clear();
        pendingSubscription = null;
        if (notificationTimeout != null) ui.removeCallbacks(notificationTimeout);
        if (descriptorTimeout != null) ui.removeCallbacks(descriptorTimeout);
        notificationTimeout = null;
        descriptorTimeout = null;
        if (wasActive && !destroyed) {
            log("通知监听结束：" + reason + "；收到 " + notificationCount
                    + " 次通知。停止本地记录；断开 GATT 时取消订阅。");
            status.setText("蓝牙通知监听已停止；共 " + notificationCount + " 次");
        }
    }

    private void clearWritePending() {
        writeInFlight = null;
        if (writeTimeout != null) ui.removeCallbacks(writeTimeout);
        writeTimeout = null;
    }

    /** A real GATT WRITE to AA13, only from one of three official-app commands. */
    /** Only a known OEM packet is allowed, and only after AA14 notifications are ready. */
    private boolean sendVerifiedCommand(byte[] frame) {
        if (!M02sBleWire.isExactAuthorizedCommand(frame)) {
            log("安全拦截：不在原厂指令白名单内"); return false;
        }
        if (!gattConnected || !servicesReady || gatt == null) {
            log("请先扫描 M02S 并连接 LE 1M"); return false;
        }
        if (!notificationMonitor || !commandNotifyReady
                || pendingSubscription != null || !notificationQueue.isEmpty()) {
            log("请先开启120秒监听，等 AA14 CCCD status=0"); return false;
        }
        if (writeInFlight != null) {
            log("上一条命令仍在等待 GATT 回执"); return false;
        }
        long now = SystemClock.elapsedRealtime();
        if (lastSentAt != 0L && now - lastSentAt < 5000L) {
            log("指令间隔至少5秒，禁止重复发送"); return false;
        }
        BluetoothGattService service = gatt.getService(COMMAND_SERVICE);
        BluetoothGattCharacteristic ch = service == null ? null :
                service.getCharacteristic(COMMAND_WRITE);
        if (ch == null || (ch.getProperties() &
                BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
            log("找不到 AA13 有回执写入接口，未发送"); return false;
        }
        String label = M02sBleWire.outgoingDescription(frame);
        try {
            writeInFlight = label;
            writeStartedAt = now;
            lastSentAt = now;
            ch.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            int result;
            if (Build.VERSION.SDK_INT >= 33) {
                result = gatt.writeCharacteristic(ch, frame,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                @SuppressWarnings("deprecation")
                boolean accepted = ch.setValue(frame);
                @SuppressWarnings("deprecation")
                boolean queued = accepted && gatt.writeCharacteristic(ch);
                result = queued ? 0 : -1;
            }
            if (result != 0) {
                clearWritePending();
                log("系统拒绝提交蓝牙命令，code=" + result);
                return false;
            }
            log("AA13 已发送：" + label + "；等待设备回执，不能据此判定 Wi-Fi 已连通");
            writeTimeout = () -> {
                if (writeInFlight == null) return;
                log("AA13 写入4秒无回调，结果未知；不自动重发");
                clearWritePending();
                if (oneClickActive && oneClickWaitingWrite) {
                    stopOneClick("遥控拍照指令写入无回执，不能确认拍照，不重试");
                }
            };
            ui.postDelayed(writeTimeout, 4000);
            return true;
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
            clearWritePending();
            log("AA13 写入异常：" + e.getClass().getSimpleName());
            return false;
        }
    }

    private void onCommandWriteResult(BluetoothGatt connection,
                                      BluetoothGattCharacteristic characteristic,
                                      int code, int token) {
        if (destroyed || token != gattAttempt || connection != gatt
                || characteristic == null || !COMMAND_WRITE.equals(characteristic.getUuid())
                || writeInFlight == null) return;
        String label = writeInFlight;
        long elapsed = SystemClock.elapsedRealtime() - writeStartedAt;
        clearWritePending();
        log("AA13 写入回执：" + label + "；status=" + code + "；耗时="
                + elapsed + "ms；这仅代表 BLE 写入结果，需观察 AA14 返回确认设备动作");
        if (oneClickActive && oneClickWaitingWrite
                && label.equals(M02sBleWire.outgoingDescription(M02sBleWire.photoThumbnail()))) {
            oneClickWaitingWrite = false;
            if (code == 0) {
                oneClickShutterAt = SystemClock.elapsedRealtime();
                log("拍照AA13写入status=0；尚不能断言真实快门成功，等待拍照相关AA14通知");
                final int run = oneClickGeneration;
                long cooldown = Math.max(0L,
                        (lastSentAt + 5500L) - SystemClock.elapsedRealtime());
                oneClickTimer = () -> beginOneClickApStage(run);
                ui.postDelayed(oneClickTimer, cooldown);
            } else {
                stopOneClick("眼镜拍照命令GATT写入失败，status=" + code + "；不重发");
            }
        }
    }

    private void requestTargetWifi(WifiMode mode) {
        requestTargetWifi(mode, null);
    }

    private void requestTargetWifi(WifiMode mode, byte[] sourceCommand) {
        if (e09BlePhotoActive) { log("E09蓝牙照片传输中，不能切换Wi-Fi模式"); return; }
        if (oneClickActive && (!oneClickAwaitingAp || mode != WifiMode.AP)) {
            log("拍照任务中禁止手动改变Wi-Fi顺序"); return;
        }
        if (wifiMode != WifiMode.NONE || wifiConnecting || wifiVerified) {
            log("已有 Wi-Fi 会话或请求；先点「取消 Wi-Fi 连接」再试另一模式");
            return;
        }
        if (mode == WifiMode.NONE) return;
        byte[] command = sourceCommand != null ? sourceCommand
                : (mode == WifiMode.P2P
                ? M02sBleWire.wifiP2pInfo() : M02sBleWire.wifiApInfo());
        if (!sendVerifiedCommand(command)) return;
        wifiSessionToken++;
        wifiMode = mode;
        wifiConnecting = true;
        wifiVerified = false;
        awaitingSsid = true;
        matchedPeerConnectStarted = false;
        targetWifiSsid = null;
        verifiedHost = null;
        ipField.setText("");
        wifiStarted = SystemClock.elapsedRealtime();
        apNetworkRequests = 0;
        apScanPolls = 0;
        apScanStartedAt = 0L;
        apScanRequestedAtUs = 0L;
        apScanResultsUpdated = false;
        apFreshFirstSeenAtMs = 0L;
        apFreshFirstBssid = null;
        apLocalFailureReason = -1;
        int token = wifiSessionToken;
        wifiDeadline = () -> {
            if (token == wifiSessionToken && wifiConnecting && !wifiVerified) {
                log("目标 Wi-Fi 在70秒内未完成建连；不重试，不连接未知设备");
                stopWifiSession("等待连接超时");
            }
        };
        ui.postDelayed(wifiDeadline, 70_000L);
        String label = mode == WifiMode.P2P ? "P2P" : "AP 热点";
        status.setText("等待 M02S 经蓝牙返回 " + label + " 目标 SSID");
        log(label + "：已发送原厂 Wi-Fi 信息请求；等待 AA14 的 0x25 SSID 返回");
    }

    private void acceptWifiSsidFrame(M02sBleWire.ParsedFrame frame) {
        if (!awaitingSsid || !wifiConnecting || wifiMode == WifiMode.NONE) {
            log("收到 0x25 SSID 信息，但当前未发起连接实验；内容已隐藏");
            return;
        }
        if (SystemClock.elapsedRealtime() - wifiStarted > 30_000) {
            log("丢弃过期 SSID 帧，避免误连接旧网络");
            return;
        }
        String value = frame.wifiSsidInMemory();
        if (!M02sWifiPolicy.isConnectableSsid(value)) {
            log("0x25 校验成功，但 SSID 格式无效或过短；拒绝自动连接");
            stopWifiSession("SSID 不可用于安全匹配");
            return;
        }
        awaitingSsid = false;
        targetWifiSsid = value;
        log(M02sWifiPolicy.safeSsidReport(value));
        if (wifiMode == WifiMode.P2P) {
            log("按原厂模式匹配 Wi-Fi Direct 设备名称（必须包含完整目标SSID）");
            startMatchedP2pDiscovery();
        } else {
            log("已解析精确目标SSID；先核对原厂式Wi-Fi扫描结果，再请求Android连接；SSID不写日志");
            startApTargetPreflight();
        }
    }

    private void startMatchedP2pDiscovery() {
        if (!ensurePermission()) {
            stopWifiSession("缺少 P2P 系统授权");
            return;
        }
        if (p2p == null || channel == null) {
            stopWifiSession("手机不支持 P2P 发现");
            return;
        }
        int token = wifiSessionToken;
        p2pPollNumber = 0;
        try {
            p2p.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    ui.post(() -> {
                        if (token != wifiSessionToken || wifiMode != WifiMode.P2P) return;
                        log("P2P 系统扫描已启动：仅连接与眼镜SSID匹配的设备");
                        scheduleP2pPoll(token);
                    });
                }
                @Override public void onFailure(int reason) {
                    ui.post(() -> {
                        if (token == wifiSessionToken) {
                            log("P2P discoverPeers 被系统拒绝，code=" + reason);
                            stopWifiSession("P2P 发现失败");
                        }
                    });
                }
            });
        } catch (SecurityException | IllegalStateException e) {
            log("P2P 扫描异常：" + e.getClass().getSimpleName());
            stopWifiSession("P2P 扫描不可用");
        }
    }

    private void scheduleP2pPoll(int token) {
        if (token != wifiSessionToken || wifiMode != WifiMode.P2P
                || !wifiConnecting || matchedPeerConnectStarted) return;
        if (p2pPoll != null) ui.removeCallbacks(p2pPoll);
        p2pPoll = () -> {
            if (token != wifiSessionToken || wifiMode != WifiMode.P2P
                    || !wifiConnecting || matchedPeerConnectStarted) return;
            p2pPollNumber++;
            requestPeers();
            if (p2pPollNumber < 22) scheduleP2pPoll(token);
        };
        ui.postDelayed(p2pPoll, 2000L);
    }

    private void connectMatchedPeer(WifiP2pDevice device) {
        if (device == null || !M02sWifiPolicy.matchesPeerName(device.deviceName,
                targetWifiSsid) || matchedPeerConnectStarted || wifiMode != WifiMode.P2P) return;
        if (!wifiConnecting || !ensurePermission()) return;
        matchedPeerConnectStarted = true;
        int token = wifiSessionToken;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        log("只找到 1 台名称匹配眼镜 SSID 的 P2P 设备；尝试建立连接（不显示设备标识）");
        status.setText("已匹配眼镜 P2P，等待系统确认连接");
        try {
            p2p.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    ui.post(() -> {
                        if (token != wifiSessionToken) return;
                        log("P2P connect() 请求已被 Android 接受；等待 groupFormed，尚不算连接成功");
                        ui.postDelayed(() -> { if (token == wifiSessionToken) requestConnectionInfo(); }, 4000L);
                        ui.postDelayed(() -> { if (token == wifiSessionToken) requestConnectionInfo(); }, 10000L);
                    });
                }
                @Override public void onFailure(int reason) {
                    ui.post(() -> {
                        if (token != wifiSessionToken) return;
                        log("目标设备 P2P 连接请求被拒绝 code=" + reason);
                        stopWifiSession("目标 P2P 连接失败");
                    });
                }
            });
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
            stopWifiSession("P2P 连接异常：" + e.getClass().getSimpleName());
        }
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * OEM-inspired AP preflight. A BLE 0x25 response means "SSID known", not
     * "the eyewear has already started broadcasting an access point".
     * Read foreground scan results for a bounded time, and use the verified
     * v0.1.6 SSID-only connection as a fallthrough when Android scan data is
     * missing or throttled. Never scan unrelated P2P devices.
     */
    private void startApTargetPreflight() {
        final int token = wifiSessionToken;
        if (destroyed || wifiMode != WifiMode.AP || !wifiConnecting
                || !M02sWifiPolicy.isConnectableSsid(targetWifiSsid)) return;
        if (tryReuseExistingTargetAp()) return;
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            stopWifiSession("手机WLAN未开启，无法连接眼镜AP"); return;
        }
        apScanPolls = 0;
        apFreshFirstSeenAtMs = 0L;
        apFreshFirstBssid = null;
        apScanStartedAt = SystemClock.elapsedRealtime();
        requestOneAndroidWifiScan();
        log("AP阶段1：仅接受本次AP信息请求后产生的目标热点扫描记录；"
                + "旧缓存即使只有几秒也不能当作已就绪；最多等待12秒");
        scanForTargetAp(token);
    }

    private boolean tryReuseExistingTargetAp() {
        try {
            for (Network network : connectivity.getAllNetworks()) {
                NetworkCapabilities nc = connectivity.getNetworkCapabilities(network);
                if (nc == null || !nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || !(nc.getTransportInfo() instanceof WifiInfo)) continue;
                WifiInfo info = (WifiInfo) nc.getTransportInfo();
                if (!targetWifiSsid.equals(M02sApScanPolicy.unquoteWifiSsid(info.getSSID()))) {
                    continue;
                }
                LinkProperties lp = connectivity.getLinkProperties(network);
                if (lp == null) continue;
                boolean hasTrustedGateway = false;
                for (RouteInfo route : lp.getRoutes()) {
                    InetAddress gw = route.getGateway();
                    if (gw == null || gw.getAddress().length != 4) continue;
                    try {
                        ProbePolicy.checkedLocalHost(gw.getHostAddress());
                        hasTrustedGateway = true;
                        break;
                    } catch (IllegalArgumentException ignored) { }
                }
                if (!hasTrustedGateway) continue;
                apNetwork = network;
                wifiVerified = true;
                wifiConnecting = false;
                cancelWifiTimers();
                log("AP阶段1：手机已连接精确目标SSID且有可信网关，复用现有Network，免除重新连接");
                updateApGateway(lp);
                return true;
            }
        } catch (SecurityException | IllegalStateException ex) {
            log("已有网络检查受系统限制，仅继续常规AP精准连接；类型="
                    + ex.getClass().getSimpleName());
        }
        return false;
    }

    private void requestOneAndroidWifiScan() {
        apScanRequestedAtUs = SystemClock.elapsedRealtime() * 1000L;
        apScanResultsUpdated = false;
        try {
            boolean accepted = wifiManager.startScan();
            log(accepted
                    ? "AP阶段1：已请求新的Android Wi-Fi扫描；等待本次请求后的ScanResult时间戳"
                    : "AP阶段1：Android拒绝主动扫描（可能限流）；旧缓存不能作为新热点证据");
        } catch (SecurityException | IllegalStateException ex) {
            log("AP阶段1：主动扫描不可用，类型=" + ex.getClass().getSimpleName()
                    + "；只接受系统随后产生的新扫描结果");
        }
    }

    private M02sApReadinessPolicy.Observation currentTargetApScan() {
        long nowUs = SystemClock.elapsedRealtime() * 1000L;
        if (wifiManager == null) return M02sApReadinessPolicy.inspect(
                targetWifiSsid, Collections.emptyList(), apScanRequestedAtUs, nowUs);
        try {
            List<android.net.wifi.ScanResult> results = wifiManager.getScanResults();
            List<M02sApScanPolicy.Beacon> exact = new ArrayList<>();
            if (results != null) {
                for (android.net.wifi.ScanResult item : results) {
                    if (item == null || !targetWifiSsid.equals(item.SSID)) continue;
                    exact.add(new M02sApScanPolicy.Beacon(
                            item.SSID, item.BSSID, item.timestamp));
                }
            }
            return M02sApReadinessPolicy.inspect(
                    targetWifiSsid, exact, apScanRequestedAtUs, nowUs);
        } catch (SecurityException | IllegalStateException ex) {
            if (apScanPolls <= 2) {
                log("AP阶段1：读取扫描结果受Android限制，类型="
                        + ex.getClass().getSimpleName()
                        + "；不记录SSID/BSSID，不能认定热点就绪");
            }
            return M02sApReadinessPolicy.inspect(
                    targetWifiSsid, Collections.emptyList(), apScanRequestedAtUs, nowUs);
        }
    }

    private void scanForTargetAp(int token) {
        if (destroyed || token != wifiSessionToken || wifiMode != WifiMode.AP
                || !wifiConnecting || apNetworkRequests != 0) return;
        apScanPolls++;
        long nowMs = SystemClock.elapsedRealtime();
        M02sApReadinessPolicy.Observation sample = currentTargetApScan();
        if (sample.kind == M02sApReadinessPolicy.Kind.FRESH_AMBIGUOUS) {
            log("AP阶段1：本次新扫描存在多个不同BSSID的同名热点，"
                    + "为避免连接错误设备，不进行Wi-Fi连接");
            stopWifiSession("同名目标热点不唯一");
            return;
        }
        if (sample.kind == M02sApReadinessPolicy.Kind.FRESH_UNIQUE) {
            if (apFreshFirstBssid == null
                    || !apFreshFirstBssid.equalsIgnoreCase(sample.uniqueBssid)) {
                apFreshFirstSeenAtMs = nowMs;
                apFreshFirstBssid = sample.uniqueBssid;
                log("AP阶段1：已看到BLE目标SSID在本次请求后的新扫描记录；"
                        + "不记录BSSID，保守等待1.8秒后使用v0.1.6已成功的SSID-only方式");
            }
            if (M02sApReadinessPolicy.canConnect(sample,
                    apFreshFirstBssid, apFreshFirstSeenAtMs, nowMs)) {
                log("AP阶段1：已完成新扫描证据及1.8秒等待；扫描完成回调="
                        + apScanResultsUpdated
                        + "；只发一次Android精准SSID网络请求；"
                        + "注意扫描成功并不保证AP会接受连接");
                connectTargetAp();
                return;
            }
        } else {
            apFreshFirstBssid = null;
            apFreshFirstSeenAtMs = 0L;
        }
        if (nowMs - apScanStartedAt >= 12_000L) {
            log("AP阶段1：12秒内没有持续有效的本次新扫描目标证据；旧缓存候选="
                    + sample.cachedCount
                    + "；系统扫描完成回调=" + apScanResultsUpdated
                    + "；不盲目发起Wi-Fi连接，不能据此断定眼镜未广播");
            stopWifiSession("没有拍照后新扫描证据，停止AP申请");
            return;
        }
        if (apScanPolls == 1 || apScanPolls % 5 == 0) {
            log("AP阶段1：第" + apScanPolls
                    + "次观察；目标状态=" + sample.kind
                    + "；旧缓存数=" + sample.cachedCount
                    + "；本次新扫描数=" + sample.freshCount);
        }
        apScanTimer = () -> scanForTargetAp(token);
        ui.postDelayed(apScanTimer, 650L);
    }

    private String apFailureDescription(int code) {
        if (Build.VERSION.SDK_INT < 34) return "不支持细分诊断";
        switch (code) {
            case WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_ASSOCIATION:
                return "关联阶段失败";
            case WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_AUTHENTICATION:
                return "认证阶段失败";
            case WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_IP_PROVISIONING:
                return "IP地址分配失败";
            case WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_NOT_FOUND:
                return "Android未找到匹配目标";
            case WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_NO_RESPONSE:
                return "目标AP连接无响应";
            case WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_USER_REJECT:
                return "系统用户拒绝连接";
            case WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_UNKNOWN:
            default:
                return "未知或未分类";
        }
    }

    private void detachApFailureListener() {
        if (apFailureListener == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 34 && wifiManager != null) {
                wifiManager.removeLocalOnlyConnectionFailureListener(apFailureListener);
            }
        } catch (SecurityException | IllegalStateException ignored) { }
        apFailureListener = null;
    }

    private void connectTargetAp() {
        if (!ensurePermission() || wifiMode != WifiMode.AP ||
                !M02sWifiPolicy.isConnectableSsid(targetWifiSsid)) {
            stopWifiSession("无法申请AP连接，权限或SSID无效"); return;
        }
        if (apNetworkRequests != 0) {
            log("AP阶段2：已申请过本次目标网络；不重复请求，不重发拍照"); return;
        }
        final int token = wifiSessionToken;
        ApRequestDiagnostic.Stage stage = ApRequestDiagnostic.Stage.PREPARE_SPECIFIER;
        final boolean changeNetwork = hasPermission(Manifest.permission.CHANGE_NETWORK_STATE);
        final boolean accessNetwork = hasPermission(Manifest.permission.ACCESS_NETWORK_STATE);
        final boolean nearbyWifi = Build.VERSION.SDK_INT < 33
                || hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES);
        final boolean fineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        log(ApRequestDiagnostic.permissionSummary(
                changeNetwork, accessNetwork, nearbyWifi, fineLocation));
        if (!changeNetwork || !accessNetwork) {
            stopWifiSession("缺少Android精准连接所需权限"); return;
        }
        if (apCallback != null) {
            try { connectivity.unregisterNetworkCallback(apCallback); }
            catch (Exception ignored) { }
            apCallback = null;
        }
        detachApFailureListener();
        apLocalFailureReason = -1;
        try {
            // The v0.1.6 SSID-only configuration already worked on this phone.
            // This experiment adjusts scan readiness and records *why* Android
            // fails; it does not change passwords, invent AP modes or pin BSSID.
            WifiNetworkSpecifier spec = new WifiNetworkSpecifier.Builder()
                    .setSsid(targetWifiSsid).setWpa2Passphrase(OEM_WIFI_KEY).build();
            stage = ApRequestDiagnostic.Stage.BUILD_NETWORK_REQUEST;
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(spec).build();

            if (Build.VERSION.SDK_INT >= 34 && wifiManager != null) {
                try {
                    final WifiManager.LocalOnlyConnectionFailureListener listener =
                            new WifiManager.LocalOnlyConnectionFailureListener() {
                        @Override public void onConnectionFailed(
                                WifiNetworkSpecifier failed, int reason) {
                            if (destroyed || token != wifiSessionToken
                                    || wifiMode != WifiMode.AP
                                    || apFailureListener != this) return;
                            apLocalFailureReason = reason;
                            log("AP阶段2：Android报告本地设备网络失败原因="
                                    + apFailureDescription(reason) + "；状态码=" + reason
                                    + "；SSID/BSSID/口令不记录");
                        }
                    };
                    apFailureListener = listener;
                    wifiManager.addLocalOnlyConnectionFailureListener(
                            getMainExecutor(), listener);
                    log("AP阶段2：已开启Android 14+本地设备连接失败原因监听");
                } catch (SecurityException | IllegalStateException ex) {
                    apFailureListener = null;
                    log("AP阶段2：失败原因监听不可用，类型="
                            + ex.getClass().getSimpleName() + "；仍以Network回调为准");
                }
            }
            apNetworkRequests = 1;
            apCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    ui.post(() -> {
                        if (destroyed || token != wifiSessionToken
                                || apCallback != this || wifiMode != WifiMode.AP) return;
                        apNetwork = network;
                        wifiVerified = true;
                        wifiConnecting = false;
                        cancelWifiTimers();
                        log("AP阶段2：Android目标网络onAvailable；单次SSID-only申请成功，待真实网关");
                        status.setText("目标AP网络已连接；获取真实网关中");
                        LinkProperties lp = connectivity.getLinkProperties(network);
                        if (lp != null) updateApGateway(lp);
                    });
                }
                @Override public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
                    ui.post(() -> {
                        if (destroyed || token != wifiSessionToken
                                || apCallback != this || wifiMode != WifiMode.AP
                                || apNetwork == null || !apNetwork.equals(network)) return;
                        updateApGateway(lp);
                    });
                }
                @Override public void onLost(Network network) {
                    ui.post(() -> {
                        if (token != wifiSessionToken || apCallback != this
                                || apNetwork == null || !apNetwork.equals(network)) return;
                        log("AP阶段2：已连接目标Network后来断开");
                        if (oneClickActive) stopOneClick("AP网络在下载阶段断开");
                        else stopWifiSession("已连接目标AP网络断开");
                    });
                }
                @Override public void onUnavailable() {
                    ui.post(() -> {
                        if (destroyed || token != wifiSessionToken || apCallback != this
                                || wifiMode != WifiMode.AP) return;
                        log("AP阶段2：Android onUnavailable；只申请过一次网络；"
                                + "稍后读取本地连接失败原因，不再立即用旧缓存重新连接");
                        ui.postDelayed(() -> {
                            if (destroyed || token != wifiSessionToken || apCallback != this
                                    || wifiMode != WifiMode.AP || wifiVerified) return;
                            log("AP阶段3：最终未连接；本地失败原因="
                                    + (apLocalFailureReason < 0 ? "系统未提供细分原因"
                                    : apFailureDescription(apLocalFailureReason))
                                    + "；本轮不自动重试、不重新拍照、不下载旧图");
                            stopWifiSession("单次AP连接未成功");
                        }, 1000L);
                    });
                }
            };
            stage = ApRequestDiagnostic.Stage.REQUEST_NETWORK;
            connectivity.requestNetwork(request, apCallback, 48_000);
            log("AP阶段2：已提交单次精确SSID、原厂WPA2密钥的Android连接请求；等待系统授权和onAvailable");
        } catch (SecurityException | IllegalArgumentException | IllegalStateException ex) {
            // Exception messages may contain private credentials or SSID.
            log(ApRequestDiagnostic.safeFailure(stage, ex));
            stopWifiSession("AP请求异常：" + ex.getClass().getSimpleName());
        }
    }

    private void updateApGateway(LinkProperties properties) {
        if (properties == null) return;
        for (RouteInfo route : properties.getRoutes()) {
            InetAddress gateway = route.getGateway();
            if (gateway == null || gateway.getAddress().length != 4) continue;
            try {
                String host = ProbePolicy.checkedLocalHost(gateway.getHostAddress());
                verifiedHost = host;
                ipField.setText(host);
                log("从目标 AP 的 Android 路由发现私有网关IP；只读文件列表可手动测试");
                status.setText("目标 AP 已建立；网关IP已获取（HTTP 服务待验证）");
                if (e09LiveRequested && !e09LiveProbing
                        && apNetwork != null && wifiMode == WifiMode.AP && wifiVerified
                        && !host.equals(e09LiveLastHost)) {
                    e09LiveProbing = true;
                    e09LiveLastHost = host;
                    final Network net = apNetwork;
                    log("E09 APP_LIVE AP已连通，开始只读RTSP OPTIONS/DESCRIBE诊断");
                    io.execute(() -> {
                        try {
                            M02sE09RtspProbe.Result r = M02sE09RtspProbe.probe(net,host);
                            ui.post(() -> {
                                if (destroyed || !e09LiveRequested
                                        || apNetwork != net || !host.equals(verifiedHost)) return;
                                log("E09 RTSP探测完成：OPTIONS200=" + r.optionsOk
                                        + "；DESCRIBE200=" + r.describeOk
                                        + "；SDP含video=" + r.videoSdp
                                        + "；耗时=" + r.durationMs
                                        + "ms；视频帧是否可解码须点播放验证");
                                status.setText(r.describeOk && r.videoSdp
                                        ? "RTSP会话描述可读取；可尝试播放实时画面"
                                        : "RTSP未确认视频流，具体码流未验证");
                            });
                        } catch (Exception ex) {
                            ui.post(() -> {
                                if (!destroyed && e09LiveRequested)
                                    log("E09 RTSP探测失败，类型="
                                            + ex.getClass().getSimpleName()
                                            + "；不能断定M02S支持RTSP");
                            });
                        }
                    });
                }
                if (oneClickActive && oneClickAwaitingAp) {
                    oneClickAwaitingAp = false;
                    oneClickBoundNetwork = apNetwork;
                    oneClickBoundHost = host;
                    oneClickPollStartedAt = SystemClock.elapsedRealtime();
                    final int id = oneClickGeneration;
                    log("拍照后AP连接成功，开始核对照片列表；此前AA14拍照通知数="
                            + oneClickPhotoNotices);
                    oneClickTimer = () -> beginOneClickPolling(id);
                    ui.postDelayed(oneClickTimer, 300L);
                }
                return;
            } catch (IllegalArgumentException ignored) { }
        }
        log("目标 AP 已连接，但 Android 尚未提供 192.168.x.x 网关，禁止猜测服务地址");
    }

    private void cancelWifiTimers() {
        if (wifiDeadline != null) ui.removeCallbacks(wifiDeadline);
        if (p2pPoll != null) ui.removeCallbacks(p2pPoll);
        if (apScanTimer != null) ui.removeCallbacks(apScanTimer);
        wifiDeadline = null;
        p2pPoll = null;
        apScanTimer = null;
    }

    private void stopWifiSession(String reason) {
        boolean wasUsed = wifiMode != WifiMode.NONE || wifiConnecting || wifiVerified;
        boolean wasP2p = wifiMode == WifiMode.P2P && matchedPeerConnectStarted;
        wifiSessionToken++;
        cancelWifiTimers();
        finishHold("Wi-Fi 会话结束");
        clearPhotoUi();
        awaitingSsid = false;
        wifiConnecting = false;
        wifiVerified = false;
        targetWifiSsid = null;
        verifiedHost = null;
        matchedPeerConnectStarted = false;
        wifiMode = WifiMode.NONE;
        if (ipField != null) ipField.setText("");
        apNetwork = null;
        if (apCallback != null) {
            try { connectivity.unregisterNetworkCallback(apCallback); }
            catch (Exception ignored) { }
            apCallback = null;
        }
        detachApFailureListener();
        apScanRequestedAtUs = 0L;
        apFreshFirstSeenAtMs = 0L;
        apFreshFirstBssid = null;
        if (wasP2p && p2p != null && channel != null) {
            try { p2p.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { log("已释放本实验创建的 P2P 连接组"); }
                @Override public void onFailure(int reasonCode) { log("P2P 释放未完成 code=" + reasonCode); }
            }); } catch (Exception ignored) { }
        }
        if (wasUsed && !destroyed) {
            log("实验 Wi-Fi 会话已停止：" + reason);
            status.setText("Wi-Fi 未连接 / " + reason);
        }
    }

    private void readOnceVerified() {
        if (!wifiVerified || verifiedHost == null || wifiMode == WifiMode.NONE) {
            log("安全拦截：尚未确认眼镜 Wi-Fi 和设备IP，不能发 HTTP 请求");
            return;
        }
        final String host = verifiedHost;
        final Network network = wifiMode == WifiMode.AP ? apNetwork : null;
        log("目标网络已确认；只读测试 GET /app/getfilelist，原始文件内容不写日志");
        io.execute(() -> probeLocalFileList(host, network, 0));
    }

    private void requestPeers() {
        if (!permitted() || p2p == null || channel == null) return;
        final int token = wifiSessionToken;
        try {
            p2p.requestPeers(channel, (WifiP2pDeviceList list) -> {
                if (destroyed || token != wifiSessionToken) return;
                peers.clear();
                peerAdapter.clear();
                peerAdapter.add("未展示附近设备，避免误点打印机");
                WifiP2pDevice target = null;
                int total = 0, matches = 0;
                for (WifiP2pDevice device : list.getDeviceList()) {
                    total++;
                    if (wifiMode == WifiMode.P2P && targetWifiSsid != null &&
                            M02sWifiPolicy.matchesPeerName(device.deviceName, targetWifiSsid)) {
                        matches++;
                        if (target == null) target = device;
                    }
                }
                if (wifiMode == WifiMode.P2P && wifiConnecting && !matchedPeerConnectStarted) {
                    log("P2P 已发现设备 " + total + " 台；匹配眼镜目标SSID=" + matches
                            + " 台（不打印附近设备标识）");
                    if (matches == 1) connectMatchedPeer(target);
                    else if (matches > 1) {
                        log("同名目标设备超过1台，拒绝自动连接以避免连错设备");
                        stopWifiSession("P2P 匹配不唯一");
                    }
                }
            });
        } catch (SecurityException | IllegalStateException ex) {
            log("读取 P2P 设备列表失败：" + ex.getClass().getSimpleName());
        }
    }
    private void scanP2p() {
        if (!ensurePermission()) return;
        if (p2p == null || channel == null) { log("系统不提供 Wi-Fi Direct"); return; }
        connectionStarted = SystemClock.elapsedRealtime();
        try {
            p2p.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    log("P2P 发现请求已提交；等待设备公告");
                    ui.postDelayed(MainActivity.this::requestPeers, 2500);
                    ui.postDelayed(MainActivity.this::requestPeers, 6000);
                }
                @Override public void onFailure(int reason) { log("P2P 搜索失败 code=" + reason); }
            });
        } catch (SecurityException ex) { log("P2P 搜索需要授权"); }
    }
    private void connectPeer() {
        if (!ensurePermission()) return;
        int index = peerSpinner.getSelectedItemPosition() - 1;
        if (index < 0 || index >= peers.size()) { log("请先扫描并选择 P2P 设备"); return; }
        WifiP2pDevice peer = peers.get(index);
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = peer.deviceAddress;
        connectionStarted = SystemClock.elapsedRealtime();
        try {
            p2p.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { log("已向 " + peer.deviceName + " 发起 P2P 连接；等待系统结果"); }
                @Override public void onFailure(int reason) { log("P2P 连接失败 code=" + reason); }
            });
        } catch (SecurityException | IllegalArgumentException e) { log("P2P 连接异常：" + e.getClass().getSimpleName()); }
    }
    private void requestConnectionInfo() {
        if (!permitted() || p2p == null || channel == null) return;
        final int token = wifiSessionToken;
        try {
            p2p.requestConnectionInfo(channel, (WifiP2pInfo info) -> {
                if (destroyed || token != wifiSessionToken) return;
                if (info == null || !info.groupFormed) {
                    if (wifiMode == WifiMode.P2P) log("P2P 尚未形成连接组");
                    return;
                }
                if (wifiMode != WifiMode.P2P || !matchedPeerConnectStarted) {
                    log("系统存在 P2P 连接组，但没有本实验的眼镜匹配证据，不使用它");
                    return;
                }
                if (info.isGroupOwner) {
                    log("P2P 成组，但手机是组主；无法推断眼镜服务器IP，禁止盲目HTTP探测");
                    return;
                }
                if (info.groupOwnerAddress == null) {
                    log("P2P 成组但组主 IP 未获取，等待系统网络信息");
                    return;
                }
                String host;
                try { host = ProbePolicy.checkedLocalHost(
                        info.groupOwnerAddress.getHostAddress()); }
                catch (IllegalArgumentException ex) {
                    log("P2P 组主IP不满足本地安全范围，无法开放文件列表测试"); return;
                }
                if (wifiVerified && host.equals(verifiedHost)) return;
                wifiVerified = true;
                wifiConnecting = false;
                awaitingSsid = false;
                verifiedHost = host;
                ipField.setText(host);
                cancelWifiTimers();
                long ms = SystemClock.elapsedRealtime() - wifiStarted;
                log("M02S 目标 P2P 已成组；手机非组主，设备IP来自Android组主地址；用时="
                        + ms + "ms。HTTP文件接口尚未验证");
                status.setText("目标 P2P 已连接，设备IP已取得（HTTP 待测试）");
            });
        } catch (SecurityException | IllegalStateException ex) {
            log("读取 P2P 成组状态失败：" + ex.getClass().getSimpleName());
        }
    }

    private void connectAccessPoint() {
        if (!ensurePermission()) return;
        String ssid = ssidField.getText().toString().trim();
        String pass = passField.getText().toString();
        if (ssid.isEmpty()) { log("请填写从官方 App 或系统信息确认的眼镜热点 SSID"); return; }
        if (!pass.isEmpty() && pass.length() < 8) {
            log("WPA2 密码需至少 8 个字符；不知道密码就不要猜测"); return;
        }
        if (apCallback != null) {
            try { connectivity.unregisterNetworkCallback(apCallback); }
            catch (Exception ignored) {}
            apCallback = null; apNetwork = null;
        }
        try {
            WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder().setSsid(ssid);
            if (!pass.isEmpty()) builder.setWpa2Passphrase(pass);
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(builder.build()).build();
            connectionStarted = SystemClock.elapsedRealtime();
            apCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    apNetwork = network;
                    long ms = SystemClock.elapsedRealtime() - connectionStarted;
                    log("专用 AP Wi-Fi 网络已可用；建连 " + ms + "ms；其他网络不切换");
                }
                @Override public void onLost(Network network) {
                    if (network.equals(apNetwork)) apNetwork = null;
                    log("专用 AP Wi-Fi 网络断开");
                }
                @Override public void onUnavailable() { log("AP Wi-Fi 请求超时或被用户拒绝"); }
            };
            connectivity.requestNetwork(request, apCallback, 60_000);
            log("已请求 Android 连接 AP；请留意系统 Wi-Fi 授权弹窗");
        } catch (SecurityException | IllegalArgumentException ex) {
            log("申请 AP 失败：" + ex.getClass().getSimpleName() + " " + ex.getMessage());
        }
    }

    private void updateNetworkStatus() {
        if (!permitted()) { log("需要授权后才能获取完整网络信息"); return; }
        Network[] all = connectivity.getAllNetworks();
        int wifiCount = 0;
        for (Network n : all) {
            NetworkCapabilities cap = connectivity.getNetworkCapabilities(n);
            if (cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifiCount++;
        }
        log("系统网络数量 " + all.length + "；Wi-Fi Network " + wifiCount
                + "；实验模式 " + wifiMode + "；眼镜目标连接确认=" + wifiVerified
                + "；本地设备IP可用=" + (verifiedHost != null));
        requestConnectionInfo();
    }
    private void startHold() {
        if (keepRunning) { log("120秒观察已经运行"); return; }
        if (!wifiVerified || verifiedHost == null || wifiMode == WifiMode.NONE) {
            log("安全拦截：目标眼镜Wi-Fi未连接或IP未验证，不能开始HTTP观察");
            return;
        }
        lastHost = verifiedHost;
        keepRunning = true;
        probes = 0;
        successes = 0;
        lastFingerprint = "";
        keepStarted = SystemClock.elapsedRealtime();
        try { wifiLock.acquire(); }
        catch (SecurityException ex) { log("无法获得WifiLock"); }
        log("开始 120秒连接观察：仅访问已确认网络的私有设备地址，每3秒只读文件列表");
        log("WifiLock 仅影响手机，不保证眼镜固件持续开启Wi-Fi");
        ui.post(holdTick);
    }

    private void finishHold(String reason) {
        boolean wasRunning = keepRunning;
        keepRunning = false;
        ui.removeCallbacks(holdTick);
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (wasRunning) log(reason + "。探测 " + probes + " 次；成功 " + successes + " 次");
        if (status != null) status.setText("状态：" + reason + "；成功 " + successes + " / " + probes);
    }

    private void probeLocalFileList(String host, Network bound, int sequence) {
        if (!wifiVerified || verifiedHost == null || !verifiedHost.equals(host)) {
            log("安全拦截：HTTP 请求目标不是已确认眼镜网络地址");
            return;
        }
        long begin = SystemClock.elapsedRealtime();
        int code = -1;
        int bytes = 0;
        String digest = "";
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + host + "/app/getfilelist");
            conn = (HttpURLConnection) (bound == null ? url.openConnection() : bound.openConnection(url));
            conn.setConnectTimeout(1800);
            conn.setReadTimeout(1800);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false); // never leave expected local address.
            code = conn.getResponseCode();
            InputStream input = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (input != null) {
                try (InputStream stream = input) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while (out.size() < 262144 && (n = stream.read(buffer, 0,
                            Math.min(buffer.length, 262144 - out.size()))) > 0) {
                        out.write(buffer, 0, n);
                    }
                    byte[] body = out.toByteArray();
                    bytes = body.length;
                    digest = ProbePolicy.fingerprint(body);
                }
            }
        } catch (Exception ex) {
            final String error = ex.getClass().getSimpleName() +
                    (ex.getMessage() == null ? "" : ": " + ex.getMessage());
            log("探测 " + (sequence == 0 ? "单次" : sequence) + "：失败 " + error);
            return;
        } finally {
            if (conn != null) conn.disconnect();
        }
        long ms = SystemClock.elapsedRealtime() - begin;
        final int resultCode = code, resultBytes = bytes;
        final String resultDigest = digest;
        log("探测 " + (sequence == 0 ? "单次" : sequence) + "：HTTP " +
                resultCode + " / " + ms + "ms / " + resultBytes +
                " 字节 / 摘要 " + resultDigest);
        if (resultCode == 200) {
            ui.post(() -> {
                if (destroyed) return;
                successes++;
                if (!lastFingerprint.isEmpty() && !lastFingerprint.equals(resultDigest)) {
                    log("文件列表摘要变化（可能有新照片；未读取照片内容）");
                }
                lastFingerprint = resultDigest;
            });
        }
    }

    /** File/photo experiments are bound to the already authenticated AP Network. */
    private boolean photoNetworkReady() {
        if (!wifiVerified || wifiMode != WifiMode.AP || verifiedHost == null
                || apNetwork == null) {
            log("照片操作被拦截：必须先由Android确认M02S AP连接并获取设备网关");
            return false;
        }
        return true;
    }

    private void clearPhotoUi() {
        stopOneClick("Wi-Fi会话结束或清除预览，本次一键任务取消");
        photoCandidates = Collections.emptyList();
        photoBusy = false;
        lastPhotoInMemory = null;
        if (photoAdapter != null) {
            photoAdapter.clear();
            photoAdapter.add("选择候选照片（自动判断失败时手动选）");
        }
        if (photoPreview != null) photoPreview.setImageBitmap(null);
    }

    private void fillPhotoCandidates(M02sPhotoListReader.Result list) {
        photoCandidates = list.candidates;
        photoAdapter.clear();
        photoAdapter.add("选择候选照片（不展示文件名）");
        int dates = 0;
        for (int i = 0; i < list.candidates.size(); i++) {
            M02sPhotoPolicy.Candidate item = list.candidates.get(i);
            if (item.sortableTime != null) dates++;
            photoAdapter.add("照片候选 " + (i + 1) + " · 时间"
                    + (item.sortableTime == null ? "不明确" : "可排序")
                    + " · 文件大小" + (item.declaredBytes < 0 ? "未知" : "已知"));
        }
        log("AP JSON文件列表解析成功：总条目=" + list.entriesObserved
                + "；可安全识别的图片候选=" + list.eligibleImages
                + "；可排序时间=" + dates + "（内容、名称和时间值不进日志）");
    }

    private void loadPhotoList(boolean downloadLatest) {
        if (oneClickActive) { log("一键拍照流程进行中，请勿同时手动读取列表"); return; }
        if (!photoNetworkReady()) return;
        if (photoBusy) { log("正在进行照片实验，请等待当前请求完成"); return; }
        finishHold("执行照片单次查询，停止周期探测");
        photoBusy = true;
        final int token = wifiSessionToken;
        final Network dedicatedNetwork = apNetwork;
        final String host = verifiedHost;
        status.setText("读取眼镜AP的JSON照片列表中");
        log("开始读取官方AP JSON列表 /app/getfilelist（最多256KB）；不会打印任何文件名");
        io.execute(() -> {
            try {
                M02sPhotoTransport.Response response =
                        M02sPhotoTransport.getList(dedicatedNetwork, host);
                if (response.httpCode != 200) {
                    ui.post(() -> {
                        if (destroyed || token != wifiSessionToken) return;
                        photoBusy = false;
                        log("AP JSON列表HTTP状态=" + response.httpCode + "；停止，不尝试原图");
                        status.setText("照片列表HTTP失败 " + response.httpCode);
                    });
                    return;
                }
                M02sPhotoListReader.Result list =
                        M02sPhotoListReader.parse(response.body);
                M02sPhotoPolicy.Candidate latest =
                        M02sPhotoPolicy.uniqueLatestOrNull(list.candidates);
                ui.post(() -> {
                    if (destroyed || token != wifiSessionToken || !photoNetworkReady()) return;
                    fillPhotoCandidates(list);
                    if (!downloadLatest) {
                        photoBusy = false;
                        status.setText("照片列表已解析，待手动选择或自动下载");
                        return;
                    }
                    if (latest == null) {
                        photoBusy = false;
                        if (list.eligibleImages == 0) {
                            log("没有符合安全图片规则的候选；原图未下载");
                            status.setText("未找到可安全下载的图片");
                        } else {
                            log("无法唯一确定最新照片（时间缺失或相同）；拒绝按列表顺序猜测。可选择候选编号手动下载");
                            status.setText("无法确认最新照片，请手动选择候选编号");
                        }
                        return;
                    }
                    log("已唯一确认最新图像候选，准备只读下载原图；不会保存相册");
                    beginDownload(latest, token, host, dedicatedNetwork);
                });
            } catch (Exception ex) {
                final String kind = ex.getClass().getSimpleName();
                ui.post(() -> {
                    if (destroyed || token != wifiSessionToken) return;
                    photoBusy = false;
                    log("AP文件列表读取/解析失败：" + kind
                            + "；为隐私不记录响应内容和原始异常消息");
                    status.setText("文件列表解析未通过验证");
                });
            }
        });
    }

    private void downloadChosenPhoto() {
        if (oneClickActive) { log("一键拍照流程进行中，请勿同时手动下载"); return; }
        if (!photoNetworkReady()) return;
        if (photoBusy) { log("有照片请求正在进行，请稍等"); return; }
        int index = photoSpinner.getSelectedItemPosition() - 1;
        if (index < 0 || index >= photoCandidates.size()) {
            log("先点击「只解析照片列表」，再手动选择一个候选编号");
            return;
        }
        M02sPhotoPolicy.Candidate item = photoCandidates.get(index);
        log("用户手动选择了候选序号 " + (index + 1)
                + "；仅下载这一张，不认定它一定是最新照片");
        photoBusy = true;
        finishHold("开始选中照片下载");
        beginDownload(item, wifiSessionToken, verifiedHost, apNetwork);
    }

    private static final class CheckedImage {
        final Bitmap preview;
        final int originalWidth;
        final int originalHeight;
        final M02sPhotoPolicy.ImageKind kind;

        CheckedImage(Bitmap bmp, int w, int h, M02sPhotoPolicy.ImageKind k) {
            preview = bmp;
            originalWidth = w;
            originalHeight = h;
            kind = k;
        }
    }

    private static CheckedImage checkImage(byte[] bytes,
                                           M02sPhotoPolicy.Candidate selected)
            throws IOException {
        if (bytes == null || bytes.length < 24 ||
                bytes.length > M02sPhotoPolicy.MAX_IMAGE_BYTES)
            throw new IOException("Image byte length failed validation");
        M02sPhotoPolicy.ImageKind kind = M02sPhotoPolicy.imageKind(bytes);
        if (!M02sPhotoPolicy.extensionMatches(selected.remoteName, kind))
            throw new IOException("Image signature and extension disagree");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || (long) bounds.outWidth * bounds.outHeight > 80_000_000L) {
            throw new IOException("Image dimensions failed validation");
        }
        int sample = 1;
        while (bounds.outWidth / sample > 1200 ||
                bounds.outHeight / sample > 1200) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (preview == null) throw new IOException("Image could not be decoded");
        return new CheckedImage(preview, bounds.outWidth, bounds.outHeight, kind);
    }

    private void beginDownload(M02sPhotoPolicy.Candidate item, int token,
                               String host, Network dedicatedNetwork) {
        beginDownload(item, token, host, dedicatedNetwork, -1);
    }

    private void beginDownload(M02sPhotoPolicy.Candidate item, int token,
                               String host, Network dedicatedNetwork, int oneClickId) {
        if (M02sPhotoPolicy.safePhotoUrl(host, item.remoteName) == null) {
            if (oneClickId >= 0) stopOneClick("新照片原图路径未通过安全校验");
            photoBusy = false;
            log("原始照片的路径未通过本地安全检查，拒绝下载；不猜测其它地址");
            status.setText("照片下载路径不符合安全约束");
            return;
        }
        if (!wifiVerified || wifiMode != WifiMode.AP ||
                dedicatedNetwork == null || !host.equals(verifiedHost)
                || (oneClickId >= 0 && !oneClickReady(oneClickId))) {
            if (oneClickId >= 0) stopOneClick("原图下载前AP网络或本次任务已失效");
            photoBusy = false;
            log("目标眼镜AP状态发生变化，取消原图下载");
            return;
        }
        status.setText("正从眼镜AP只读下载一张原始图片");
        io.execute(() -> {
            try {
                M02sPhotoTransport.Response response =
                        M02sPhotoTransport.getOriginalPhoto(dedicatedNetwork, host, item);
                if (response.httpCode != 200) {
                    ui.post(() -> {
                        if (destroyed || token != wifiSessionToken
                                || (oneClickId >= 0 && !oneClickReady(oneClickId))) return;
                        photoBusy = false;
                        if (oneClickId >= 0) stopOneClick("新照片原图HTTP请求未成功");
                        log("原图GET返回 HTTP " + response.httpCode
                                + "；未下载到有效图片，不会重试或猜测URL");
                        status.setText("原始照片HTTP " + response.httpCode);
                    });
                    return;
                }
                CheckedImage image = checkImage(response.body, item);
                String digest = ProbePolicy.fingerprint(response.body);
                ui.post(() -> {
                    if (destroyed || token != wifiSessionToken ||
                            !wifiVerified || wifiMode != WifiMode.AP
                            || apNetwork != dedicatedNetwork
                            || (oneClickId >= 0 && !oneClickReady(oneClickId))) {
                        image.preview.recycle();
                        return;
                    }
                    lastPhotoInMemory = response.body;
                    photoPreview.setImageBitmap(image.preview);
                    photoBusy = false;
                    log("原图下载并解码成功：HTTP 200；"
                            + response.body.length + "字节；"
                            + image.originalWidth + "×" + image.originalHeight
                            + "；格式=" + image.kind + "；下载="
                            + response.durationMs + "ms；摘要=" + digest
                            + "。仅在手机内存预览，无相册文件/云上传");
                    status.setText("眼镜原图已下载至内存并显示预览");
                    if (oneClickId >= 0) finishOneClickSuccess();
                });
            } catch (Exception ex) {
                final String errorType = ex.getClass().getSimpleName();
                ui.post(() -> {
                    if (destroyed || token != wifiSessionToken
                            || (oneClickId >= 0 && !oneClickReady(oneClickId))) return;
                    photoBusy = false;
                    if (oneClickId >= 0) stopOneClick("刚拍原图下载或解码失败");
                    log("原始图片GET或格式验证失败：" + errorType
                            + "；未保存文件，不输出原始响应或文件名");
                    status.setText("原始照片验证未成功");
                });
            } catch (OutOfMemoryError error) {
                ui.post(() -> {
                    if (destroyed || token != wifiSessionToken
                            || (oneClickId >= 0 && !oneClickReady(oneClickId))) return;
                    photoBusy = false;
                    if (oneClickId >= 0) stopOneClick("新照片过大，无法安全解码");
                    lastPhotoInMemory = null;
                    log("图片解码超过手机内存限制，拒绝保留");
                    status.setText("原图过大，已终止预览");
                });
            }
        });
    }

    /** All stages change state on the UI thread; the HTTP work uses io. */
    private boolean oneClickReady(int id) {
        return oneClickActive && oneClickGeneration == id && !destroyed
                && gattConnected && gatt != null;
    }

    /**
     * Release only the AP request created by this shot-first experiment.
     * Preserve the in-memory preview; phone release does NOT prove that the
     * eyewear exits its AP transfer mode.
     */
    private void releaseOneClickAp() {
        if (!oneClickApOwned) return;
        oneClickApOwned = false;
        wifiSessionToken++;
        cancelWifiTimers();
        if (apCallback != null) {
            try { connectivity.unregisterNetworkCallback(apCallback); }
            catch (Exception ignored) { }
            apCallback = null;
        }
        detachApFailureListener();
        apScanRequestedAtUs = 0L;
        apFreshFirstSeenAtMs = 0L;
        apFreshFirstBssid = null;
        apNetwork = null;
        wifiMode = WifiMode.NONE;
        wifiConnecting = false;
        wifiVerified = false;
        awaitingSsid = false;
        targetWifiSsid = null;
        verifiedHost = null;
        if (ipField != null) ipField.setText("");
        log("已释放手机的眼镜AP网络请求；眼镜自身是否退出AP传输模式仍待真机验证");
    }

    private void stopOneClick(String reason) {
        if (!oneClickActive) return;
        oneClickActive = false;
        oneClickGeneration++;
        oneClickWaitingWrite = false;
        oneClickAwaitingAp = false;
        if (oneClickTimer != null) ui.removeCallbacks(oneClickTimer);
        oneClickTimer = null;
        oneClickPrevious = null;
        oneClickBefore = Collections.emptyList();
        oneClickCapturedList = Collections.emptyList();
        oneClickBoundNetwork = null;
        oneClickBoundHost = null;
        photoBusy = false;
        releaseOneClickAp();
        log("v0.1.10 顺序取图停止：" + reason);
        if (status != null) status.setText("任务停止：" + reason);
    }

    private void finishOneClickSuccess() {
        if (!oneClickActive) return;
        boolean hasBaseline = oneClickHasBaseline;
        boolean sawPhotoNotice = oneClickPhotoNotices > 0;
        previousFinishedList = new ArrayList<>(oneClickCapturedList);
        previousFinishedListValid = true;
        oneClickActive = false;
        oneClickGeneration++;
        if (oneClickTimer != null) ui.removeCallbacks(oneClickTimer);
        oneClickTimer = null;
        oneClickPrevious = null;
        oneClickBefore = Collections.emptyList();
        oneClickCapturedList = Collections.emptyList();
        oneClickBoundNetwork = null;
        oneClickBoundHost = null;
        oneClickWaitingWrite = false;
        oneClickAwaitingAp = false;
        photoBusy = false;
        releaseOneClickAp();
        if (hasBaseline) {
            log("BLE先拍照→AP取图完成：相对上次APP的AP快照有新文件，连续两次稳定并成功解码。"
                    + "拍照AA14通知=" + sawPhotoNotice
                    + "；仍不能仅凭文件名断定由本次指令产生");
            status.setText("原图已显示：相对于上次列表的新文件，仍需核对场景");
        } else {
            log("BLE先拍照→AP取图完成：已显示唯一最新原图；没有拍照前基线！"
                    + "须人工核对是否为本次拍摄，不宣称已证实新照片。"
                    + "拍照AA14通知=" + sawPhotoNotice);
            status.setText("原图已显示：首次无拍前基线，请人工核对是不是刚拍的");
        }
    }

    /** E09 MIT-source TAKE_PHOTO 0x22/0x31: only AA15 carries the JPEG. */
    private void startE09BlePhoto() {
        if (e09BlePhotoActive || oneClickActive || photoBusy || e09LiveRequested
                || wifiMode != WifiMode.NONE || wifiConnecting) {
            log("E09 BLE直传：请先停止Wi-Fi/直播和其他拍照任务"); return;
        }
        if (!gattConnected || !commandNotifyReady || !notificationMonitor
                || pendingSubscription != null || !notificationQueue.isEmpty()) {
            log("E09 BLE直传：需要先连接蓝牙并完成AA14/AA15通知订阅"); return;
        }
        BluetoothGattService svc = gatt == null ? null : gatt.getService(COMMAND_SERVICE);
        if (svc == null || svc.getCharacteristic(PHOTO_NOTIFY) == null) {
            log("E09 BLE直传：没有AA15特征，M02S不支持此接收路径"); return;
        }
        e09Assembler.reset();
        e09BlePhotoActive = true; // Arm BEFORE triggering: start frame may arrive immediately.
        e09PhotoStartedAt = SystemClock.elapsedRealtime();
        if (!sendVerifiedCommand(M02sBleWire.e09BlePhoto())) {
            e09BlePhotoActive = false;
            log("E09 BLE直传：写入未提交；不会重发"); return;
        }
        lastPhotoInMemory = null;
        photoPreview.setImageBitmap(null);
        log("E09 BLE直传：已发送唯一0x22/0x31；只监听AA15 0x97/0x98/0x99，"
                + "最大2MB、最多15秒，无二次拍照");
        e09PhotoTimeout = () -> {
            if (!e09BlePhotoActive) return;
            log("E09 BLE直传超时：AA15数据已收到=" + e09Assembler.receivedLength()
                    + "字节；分片=" + e09Assembler.packets()
                    + "；不把AA14的拍照回执当作图片");
            stopE09BlePhoto();
        };
        ui.postDelayed(e09PhotoTimeout, 15_000L);
    }

    private void stopE09BlePhoto() {
        if (e09PhotoTimeout != null) ui.removeCallbacks(e09PhotoTimeout);
        e09PhotoTimeout = null;
        boolean was = e09BlePhotoActive;
        e09BlePhotoActive = false;
        if (was) log("E09 AA15接收结束；无照片写盘、无云上传");
    }

    /** APP_LIVE(0x67/0x30), not OPEN_WIFI(0x39/0x30). */
    private void startE09Live() {
        if (e09LiveRequested || e09BlePhotoActive || oneClickActive || photoBusy
                || wifiMode != WifiMode.NONE || wifiConnecting || wifiVerified) {
            log("E09 RTSP：已有传输任务，先安全退出上一个模式"); return;
        }
        if (!gattConnected || !commandNotifyReady || !notificationMonitor) {
            log("E09 RTSP：先连接蓝牙并订阅AA14反馈"); return;
        }
        e09LiveRequested = true;
        e09LiveProbing = false;
        e09LiveLastHost = null;
        // Overrides ONLY the OEM AP-information request with the exact E09
        // APP_LIVE command. Existing 0x25/SSID handling, dedicated Network
        // validation and Android16 failure diagnosis remain unchanged.
        requestTargetWifi(WifiMode.AP, M02sBleWire.e09AppLiveAp());
        if (!wifiConnecting || wifiMode != WifiMode.AP) {
            e09LiveRequested = false;
            log("E09 RTSP：未能提交0x67/0x30，实验未启动");
        } else {
            log("E09 RTSP：已提交0x67/0x30直播AP请求；等待AA14 0x25和真实Wi-Fi Network。"
                    + "与M02S兼容性仍待实际响应判定");
        }
    }

    private void playE09Preview() {
        if (!e09LiveRequested || !wifiVerified || wifiMode != WifiMode.AP
                || apNetwork == null || verifiedHost == null) {
            log("E09 RTSP：未建立该眼镜的专属AP Network和真实网关，禁止播放"); return;
        }
        stopE09Preview();
        // Only this experimental app process is bound temporarily. Other
        // apps keep their default internet routes. Always unbind on stop.
        try {
            if (!connectivity.bindProcessToNetwork(apNetwork)) {
                log("E09 RTSP：Android拒绝应用级网络绑定，未尝试播放"); return;
            }
            e09ProcessBound = true;
            final String host = ProbePolicy.checkedLocalHost(verifiedHost);
            MediaPlayer player = new MediaPlayer();
            e09Player = player;
            player.setDisplay(e09Surface.getHolder());
            player.setVolume(0f,0f);
            player.setDataSource("rtsp://" + host + "/h264");
            player.setOnVideoSizeChangedListener((mp,w,h) ->
                    log("E09 RTSP收到视频尺寸回调：" + w + "×" + h
                            + "（仍须观察画面是否持续）"));
            player.setOnPreparedListener(mp -> {
                if (mp != e09Player || !e09LiveRequested) return;
                try {
                    mp.start();
                    log("E09 RTSP播放器已启动；请观察是否确有实时画面及持续帧更新");
                    status.setText("RTSP播放器已启动，画面效果待人工验证");
                } catch (IllegalStateException ex) {
                    log("E09 RTSP播放器无法启动，类型=" + ex.getClass().getSimpleName());
                }
            });
            player.setOnErrorListener((mp,what,extra) -> {
                log("E09 RTSP播放器报错：what=" + what + "；extra=" + extra
                        + "；可能不支持该设备或Android媒体栈");
                ui.post(this::stopE09Preview);
                return true;
            });
            player.prepareAsync();
            log("E09 RTSP Android MediaPlayer准备中（源仅使用眼镜AP私有网关/h264）");
        } catch (Exception e) {
            log("E09 RTSP启动失败，类型=" + e.getClass().getSimpleName()
                    + "；不推断设备一定不支持");
            stopE09Preview();
        }
    }

    private void stopE09Preview() {
        if (e09Player != null) {
            try { e09Player.setOnErrorListener(null); e09Player.stop(); }
            catch (IllegalStateException ignored) { }
            try { e09Player.release(); } catch (Exception ignored) { }
            e09Player = null;
        }
        if (e09ProcessBound) {
            try { connectivity.bindProcessToNetwork(null); }
            catch (Exception ignored) { }
            e09ProcessBound = false;
        }
    }

    /** Explicitly verified NON-DELETING E09/HeyCyan finish packet. */
    private void sendE09SafeFinish() {
        if (e09BlePhotoActive || oneClickActive || photoBusy) {
            log("E09安全结束通知：当前拍照/下载尚在进行，禁止提前发送"); return;
        }
        if (sendVerifiedCommand(M02sBleWire.e09SafeTransferFinish())) {
            log("已提交E09不删除文件结束指令0x44/0x30/0x01；"
                    + "仅GATT写入，能否恢复M02S拍照需真机验证");
        } else {
            log("E09不删除结束指令未发送；检查蓝牙通知订阅与5秒指令间隔");
        }
    }

    private void endE09Live() {
        boolean was = e09LiveRequested;
        stopE09Preview();
        e09LiveRequested = false;
        e09LiveProbing = false;
        e09LiveLastHost = null;
        if (was && !oneClickActive) {
            sendE09SafeFinish();
        }
        if (wifiMode == WifiMode.AP && !oneClickActive) {
            stopWifiSession("E09 RTSP结束；手机已释放专属AP Network");
        }
        log("E09 RTSP已停止；不删除眼镜文件。"
                + "如果安全结束命令因5秒间隔未发送，可稍后单独点击安全结束按钮");
    }

    private void startOneClick() {
        if (oneClickActive || photoBusy || e09BlePhotoActive || e09LiveRequested) {
            log("已有照片或E09直播任务进行中，不允许重复拍照"); return;
        }
        if (wifiMode != WifiMode.NONE || wifiConnecting || wifiVerified) {
            log("本实验必须在手机AP未连接时启动；先取消现有Wi-Fi连接并检查眼镜状态");
            return;
        }
        if (!gattConnected || !servicesReady || gatt == null) {
            log("请先连接M02S蓝牙LE1M，暂时不要连接AP"); return;
        }
        finishHold("开始v0.1.10蓝牙先拍照与AP就绪实验");
        if (!notificationMonitor) startNotificationMonitor();
        if (!notificationMonitor) {
            log("AA14监听不可用，未发送拍照指令"); return;
        }
        oneClickActive = true;
        photoBusy = true;
        final int id = ++oneClickGeneration;
        oneClickBefore = previousFinishedListValid
                ? new ArrayList<>(previousFinishedList) : Collections.emptyList();
        oneClickHasBaseline = previousFinishedListValid;
        oneClickPrevious = null;
        oneClickCapturedList = Collections.emptyList();
        oneClickBoundNetwork = null;
        oneClickBoundHost = null;
        oneClickApOwned = false;
        oneClickAwaitingAp = false;
        oneClickPollNumber = 0;
        oneClickPollStartedAt = 0L;
        oneClickWaitingWrite = false;
        oneClickShutterAt = 0L;
        oneClickPhotoNotices = 0;
        lastPhotoInMemory = null;
        photoPreview.setImageBitmap(null);
        log("v0.1.10：蓝牙拍照一次，等待本次AP请求后的新扫描结果和就绪间隔；只发一次Wi-Fi网络申请");
        log(oneClickHasBaseline
                ? "已找到本应用上一次成功下载后的内存列表，用于新文件名比对"
                : "首次没有拍照前基线：后续唯一最新图片只用于人工核对，不自动认定为新拍");
        status.setText("蓝牙拍照：等待AA14通知就绪");
        awaitOneClickNotifications(id, 0);
    }

    private void awaitOneClickNotifications(int id, int attempts) {
        if (!oneClickReady(id)) return;
        if (!notificationMonitor) {
            stopOneClick("蓝牙通知不可用，未发送拍照指令");
            return;
        }
        if (commandNotifyReady && pendingSubscription == null
                && notificationQueue.isEmpty()) {
            if (!sendVerifiedCommand(M02sBleWire.photoThumbnail())) {
                stopOneClick("蓝牙拍照命令未提交，检查AA13和5秒命令间隔");
                return;
            }
            oneClickWaitingWrite = true;
            log("0x22/0x30已提交一次；等待AA13回执/0x22与0x42响应，不会重复拍照");
            status.setText("已发送蓝牙拍照命令，观察是否有真实快门");
            return;
        }
        if (attempts >= 30) {
            stopOneClick("AA14通知6秒内未就绪，未发送拍照指令");
            return;
        }
        oneClickTimer = () -> awaitOneClickNotifications(id, attempts + 1);
        ui.postDelayed(oneClickTimer, 200L);
    }

    private void beginOneClickApStage(int id) {
        if (!oneClickReady(id)) return;
        if (oneClickShutterAt == 0L || oneClickWaitingWrite) {
            stopOneClick("蓝牙拍照未得到成功写入回执，不启动AP"); return;
        }
        if (wifiMode != WifiMode.NONE) {
            stopOneClick("拍照后发现已有Wi-Fi会话，拒绝变更模式"); return;
        }
        log("拍照阶段AA14相关通知数=" + oneClickPhotoNotices
                + "（设备通知不能代替真实快门声或画面判断）");
        log("已满足AA13指令间隔，发送一次官方0x39/0x30并精准连接眼镜AP");
        oneClickAwaitingAp = true;
        oneClickApOwned = true;
        status.setText("拍照后连接眼镜AP：等待系统授权及网关");
        requestTargetWifi(WifiMode.AP);
        if (oneClickActive && wifiMode != WifiMode.AP) {
            stopOneClick("官方AP信息指令未成功提交；不重试");
        }
    }

    private void beginOneClickPolling(int id) {
        if (!oneClickReady(id)) return;
        if (oneClickPollStartedAt == 0L || oneClickBoundNetwork == null
                || wifiMode != WifiMode.AP || !wifiVerified
                || apNetwork != oneClickBoundNetwork || verifiedHost == null
                || !verifiedHost.equals(oneClickBoundHost)) {
            stopOneClick("拍照后目标AP未就绪或网络已改变"); return;
        }
        if (SystemClock.elapsedRealtime() - oneClickPollStartedAt > 45_000L) {
            stopOneClick("拍照后AP列表45秒无可确认的稳定照片，不冒用旧图");
            return;
        }
        final int poll = ++oneClickPollNumber;
        final Network network = oneClickBoundNetwork;
        final String host = oneClickBoundHost;
        status.setText("拍照后AP列表核对：" + poll + " 次（最多45秒）");
        io.execute(() -> {
            try {
                M02sPhotoTransport.Response response = M02sPhotoTransport.getList(network, host);
                if (response.httpCode != 200) {
                    ui.post(() -> { if (oneClickReady(id))
                        stopOneClick("AP文件列表HTTP " + response.httpCode); });
                    return;
                }
                M02sPhotoListReader.Result list = M02sPhotoListReader.parse(response.body);
                M02sFreshPhotoPolicy.Selection selected = oneClickHasBaseline
                        ? M02sFreshPhotoPolicy.select(oneClickBefore, list.candidates)
                        : M02sFreshPhotoPolicy.selectLatestUnverified(list.candidates);
                ui.post(() -> {
                    if (!oneClickReady(id)) return;
                    if (M02sFreshPhotoPolicy.stablePair(oneClickPrevious, selected)) {
                        if (SystemClock.elapsedRealtime() - oneClickPollStartedAt > 45_000L) {
                            stopOneClick("AP两次稳定列表超过45秒期限"); return;
                        }
                        oneClickCapturedList = new ArrayList<>(list.candidates);
                        log(oneClickHasBaseline
                                ? "相对上一次AP列表有新文件，连续两次稳定；开始只读下载"
                                : "没有拍前列表：唯一最新候选连续两次稳定；仅供人工确认拍照场景");
                        if (oneClickTimer != null) ui.removeCallbacks(oneClickTimer);
                        oneClickTimer = null;
                        beginDownload(selected.photo, wifiSessionToken, host, network, id);
                        return;
                    }
                    if (poll <= 2 || poll % 5 == 0 || oneClickPrevious == null
                            || selected.status != oneClickPrevious.status) {
                        log("AP照片轮询 " + poll + "：状态=" + selected.status
                                + "；候选数量=" + selected.freshCount
                                + "；存在上次AP基线=" + oneClickHasBaseline);
                    }
                    oneClickPrevious = selected;
                    oneClickTimer = () -> beginOneClickPolling(id);
                    ui.postDelayed(oneClickTimer, 1200L);
                });
            } catch (Exception ex) {
                final String type = ex.getClass().getSimpleName();
                ui.post(() -> { if (oneClickReady(id))
                    stopOneClick("AP照片列表失败：" + type + "；不改用不明照片"); });
            }
        });
    }

    private void copyDiagnostic() {
        String payload = "M02S AP新扫描与失败诊断 v0.1.10 / Android " + Build.VERSION.RELEASE +
                "\n" + logData.toString();
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("M02S diagnostic", payload));
        log("诊断已复制。日志可能包含设备地址，请检查后再分享。");
    }

    @Override protected void onDestroy() {
        finishHold("应用关闭");
        stopWifiSession("应用关闭");
        destroyed = true;
        if (receiverRegistered) unregisterReceiver(receiver);
        if (bleScanner != null) {
            try { bleScanner.stopScan(bleCallback); } catch (SecurityException ignored) {}
        }
        closeGatt("应用关闭");
        ui.removeCallbacksAndMessages(null);
        if (apCallback != null) {
            try { connectivity.unregisterNetworkCallback(apCallback); }
            catch (Exception ignored) {}
        }
        io.shutdownNow();
        super.onDestroy();
    }
}
