package com.vobichngan.bluestar.m02slab;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
            if (name == null || name.isEmpty()) name = "(未广播名称)";
            final String label = name + " / " + address + " / RSSI " + result.getRssi();
            ui.post(() -> {
                if (destroyed || !seenBle.add(address)) return;
                bleDevices.add(device);
                bleAdapter.add(label);
                log("发现蓝牙设备：" + label);
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
        log("M02S v0.1.0 实验已启动。不会遥控拍照、上传图片或修改眼镜文件。");
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
        addRow(body, button("扫描 BLE（10秒）", this::scanBle),
                button("读取 GATT 服务", this::connectGattReadOnly));

        section(body, "② Wi-Fi Direct");
        peerSpinner = new Spinner(this);
        peerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        peerAdapter.add("选择扫描到的 Wi-Fi Direct 设备");
        peerSpinner.setAdapter(peerAdapter);
        body.addView(peerSpinner);
        addRow(body, button("扫描 P2P 设备", this::scanP2p),
                button("连接所选 P2P", this::connectPeer));

        section(body, "③ 如果眼镜使用 Wi-Fi AP 热点");
        ssidField = field("眼镜热点 SSID（如果知道）", "");
        passField = field("Wi-Fi 密码（可留空）", "");
        passField.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(ssidField);
        body.addView(passField);
        addRow(body, button("连接已知 AP", this::connectAccessPoint),
                button("查看当前网络", this::updateNetworkStatus));

        section(body, "④ 连接保持与只读文件列表");
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
            bleAdapter.clear();
            bleAdapter.add("选择扫描到的蓝牙设备");
            bleScanner.startScan(bleCallback);
            log("开始 BLE 扫描 10 秒；只记录广播信息，不发送命令");
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
    private void connectGattReadOnly() {
        if (!ensurePermission()) return;
        int index = bleSpinner.getSelectedItemPosition() - 1;
        if (index < 0 || index >= bleDevices.size()) { log("请先选中一个蓝牙设备"); return; }
        if (gatt != null) { gatt.close(); gatt = null; }
        try {
            log("尝试 GATT 连接，只发现服务、不读取账号或写入特征");
            gatt = bleDevices.get(index).connectGatt(this, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt g, int status, int next) {
                    log("GATT 连接状态 " + next + "，结果代码 " + status);
                    if (status == BluetoothGatt.GATT_SUCCESS && next == BluetoothProfile.STATE_CONNECTED) {
                        try { g.discoverServices(); } catch (SecurityException ex) { log("GATT 服务发现无权限"); }
                    }
                }
                @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        log("GATT 服务读取失败 " + status); return;
                    }
                    List<BluetoothGattService> services = g.getServices();
                    log("GATT 服务个数：" + services.size());
                    for (BluetoothGattService s : services) {
                        log("服务 " + s.getUuid() + "，特征个数 " + s.getCharacteristics().size());
                    }
                }
            });
        } catch (SecurityException ex) { log("GATT 连接权限不足"); }
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
        if (gatt != null) {
            try { gatt.close(); } catch (Exception ignored) {}
        }
        if (apCallback != null) {
            try { connectivity.unregisterNetworkCallback(apCallback); }
            catch (Exception ignored) {}
        }
        io.shutdownNow();
        super.onDestroy();
    }
}
