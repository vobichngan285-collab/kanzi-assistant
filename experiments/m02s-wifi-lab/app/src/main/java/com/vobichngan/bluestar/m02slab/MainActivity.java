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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
            final String host;
            try { host = ProbePolicy.checkedLocalHost(ipField.getText().toString()); }
            catch (IllegalArgumentException ex) {
                finishHold("IP 地址错误：" + ex.getMessage()); return;
            }
            final Network bound = apNetwork;
            final int sequence = ++probes;
            final long seconds = elapsed / 1000;
            status.setText("保持连接中：" + seconds + " / 120 秒；成功 " + successes + " / " + probes);
            io.execute(() -> probeLocalFileList(host, bound, sequence));
            ui.postDelayed(this, 3000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
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
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        receiverRegistered = true;
        log("M02S v0.1.2 蓝牙协议实验启动：不会遥控拍照、上传图片或修改眼镜文件。");
        log("仅执行 BLE 只读探测、系统 P2P/AP 连接和本地 HTTP 文件列表请求。");
        requestPermissionsIfNeeded();
        updateNetworkStatus();
    }

    private void buildUi() {
        int pad = dp(14);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);
        body.setBackgroundColor(Color.rgb(245, 248, 252));
        scroll.addView(body);

        TextView heading = text("蓝星 M02S 连接实验", 24, true);
        body.addView(heading);
        body.addView(text("独立诊断 APK · 不影响蓝星向导 · 只读取连接状态", 14, false));
        status = text("状态：等待授权", 17, true);
        status.setPadding(0, dp(12), 0, dp(10));
        body.addView(status);

        section(body, "① 蓝牙设备发现（只读）");
        bleSpinner = new Spinner(this);
        bleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        bleAdapter.add("选择扫描到的蓝牙设备");
        bleSpinner.setAdapter(bleAdapter);
        body.addView(bleSpinner);
        addRow(body, button("扫描眼镜 BLE（10秒）", this::scanBle),
                button("连接 LE 1M（15秒）", () -> connectGattReadOnly(false)));
        addRow(body, button("兼容连接（15秒）", () -> connectGattReadOnly(true)),
                button("主动断开 GATT", () -> closeGatt("用户主动断开")));
        body.addView(text("先选 M02S-3584，再点 LE 1M；只有失败才试兼容连接。", 14, false));
        section(body, "② AE00 / AA12 蓝牙协议观察");
        addRow(body, button("查看特征 UUID/权限", this::inspectGattCharacteristics),
                button("监听拍照通知（60秒）", this::startNotificationMonitor));
        addRow(body, button("停止通知记录", () -> stopNotificationMonitor("用户停止")),
                button("复制诊断", this::copyDiagnostic));
        body.addView(text("先连接成功、发现4个服务，再点监听，期间按眼镜上的实体拍照键。"
                + "只对 AE00/AA12 可通知特征写入标准 CCCD 订阅配置；"
                + "不发送拍照、开 Wi-Fi 或任何厂商控制命令。"
                + "日志最多记录每条通知前20字节，请分享前检查敏感信息。", 14, false));

        section(body, "③ Wi-Fi Direct（本轮可跳过）");
        peerSpinner = new Spinner(this);
        peerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        peerAdapter.add("选择扫描到的 Wi-Fi Direct 设备");
        peerSpinner.setAdapter(peerAdapter);
        body.addView(peerSpinner);
        addRow(body, button("扫描 P2P 设备", this::scanP2p),
                button("连接所选 P2P", this::connectPeer));

        section(body, "④ 如果眼镜使用 Wi-Fi AP 热点");
        ssidField = field("眼镜热点 SSID（如果知道）", "");
        passField = field("Wi-Fi 密码（可留空）", "");
        passField.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(ssidField);
        body.addView(passField);
        addRow(body, button("连接已知 AP", this::connectAccessPoint),
                button("查看当前网络", this::updateNetworkStatus));

        section(body, "⑤ 连接保持与只读文件列表（本轮可跳过）");
        body.addView(text("文件列表地址：默认 192.168.49.207；另一个常见候选 192.168.169.1。"
                        + "真实地址需通过诊断确认。", 14, false));
        ipField = field("眼镜 IP", "192.168.49.207");
        body.addView(ipField);
        addRow(body, button("读一次文件列表", () -> {
                    final String host;
                    try { host = ProbePolicy.checkedLocalHost(ipField.getText().toString()); }
                    catch (IllegalArgumentException e) { log(e.getMessage()); return; }
                    io.execute(() -> probeLocalFileList(host, apNetwork, 0));
                }),
                button("保持连接 120 秒", this::startHold));
        addRow(body, button("停止测试", () -> finishHold("用户停止")),
                button("复制诊断", this::copyDiagnostic));

        section(body, "诊断日志（不包含照片内容）");
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
        stopNotificationMonitor("GATT 连接结束");
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
        notificationCount = 0;
        notificationStarted = SystemClock.elapsedRealtime();
        log("监听开始：等待按眼镜实体拍照键；可订阅特征 " + notificationQueue.size()
                + " 个。60秒后自动停止记录。");
        status.setText("蓝牙监听中：按眼镜拍照键");
        notificationTimeout = () -> stopNotificationMonitor("60秒监听期结束");
        ui.postDelayed(notificationTimeout, 60_000);
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
        if (GattProtocolPolicy.shouldLogPayload(notificationCount)) {
            long elapsed = SystemClock.elapsedRealtime() - notificationStarted;
            log("通知#" + notificationCount + " +" + elapsed + "ms；特征 "
                    + c.getUuid() + "；长度 " + value.length
                    + "；HEX " + GattProtocolPolicy.shortHex(value));
        } else if (notificationCount == 41) {
            log("通知数量较多，后续只计数不打印，避免大量日志/敏感数据");
        }
    }

    private void stopNotificationMonitor(String reason) {
        boolean wasActive = notificationMonitor;
        notificationMonitor = false;
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

    private void requestPeers() {
        if (!permitted() || p2p == null || channel == null) return;
        try {
            p2p.requestPeers(channel, (WifiP2pDeviceList list) -> {
                peers.clear();
                peerAdapter.clear();
                peerAdapter.add("选择扫描到的 Wi-Fi Direct 设备");
                for (WifiP2pDevice d : list.getDeviceList()) {
                    peers.add(d);
                    peerAdapter.add((d.deviceName == null ? "未命名" : d.deviceName) +
                            " / " + d.deviceAddress + " / state " + d.status);
                }
                log("P2P 发现 " + peers.size() + " 个设备");
            });
        } catch (SecurityException ex) { log("P2P 设备列表需要授权"); }
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
        try {
            p2p.requestConnectionInfo(channel, (WifiP2pInfo info) -> {
                if (info == null || !info.groupFormed) {
                    log("P2P 当前没有活动连接组"); return;
                }
                String owner = info.groupOwnerAddress == null ? "未知" :
                        info.groupOwnerAddress.getHostAddress();
                long ms = connectionStarted == 0 ? -1 :
                        SystemClock.elapsedRealtime() - connectionStarted;
                log("P2P 已成组；手机是组主=" + info.isGroupOwner +
                        "；组主 IP=" + owner + "；建连计时=" + ms + "ms");
                if (owner != null && owner.startsWith("192.168.")) {
                    ipField.setText(owner);
                    log("已将组主 IP 填入探测地址；如设备作为客户端，应手动改为设备真实 IP");
                }
            });
        } catch (SecurityException ex) { log("无法读取 P2P 连接状态"); }
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
        log("系统网络数量 " + all.length + "；Wi-Fi Network " + wifiCount +
                "；独立 AP 会话 " + (apNetwork == null ? "否" : "是"));
        requestConnectionInfo();
    }
    private void startHold() {
        if (keepRunning) { log("120 秒测试已经在运行"); return; }
        try {
            lastHost = ProbePolicy.checkedLocalHost(ipField.getText().toString());
        } catch (IllegalArgumentException e) { log(e.getMessage()); return; }
        keepRunning = true;
        probes = 0; successes = 0; lastFingerprint = "";
        keepStarted = SystemClock.elapsedRealtime();
        try { wifiLock.acquire(); }
        catch (SecurityException ex) { log("无法获得 WifiLock：" + ex.getMessage()); }
        log("开始 120 秒连接观察：每 3 秒只读 GET http://" + lastHost + "/app/getfilelist");
        log("WifiLock 只阻止手机无线电节能休眠，并不能强制眼镜维持 Wi-Fi。");
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

    private void copyDiagnostic() {
        String payload = "M02S Wi-Fi Lab v0.1.0 / Android " + Build.VERSION.RELEASE +
                "\n" + logData.toString();
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("M02S diagnostic", payload));
        log("诊断已复制。日志可能包含设备地址，请检查后再分享。");
    }

    @Override protected void onDestroy() {
        finishHold("应用关闭");
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
