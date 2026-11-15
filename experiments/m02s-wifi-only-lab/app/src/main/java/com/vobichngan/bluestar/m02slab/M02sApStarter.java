package com.vobichngan.bluestar.m02slab;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.PatternMatcher;
import android.os.SystemClock;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One-time BLE AP-enablement only. It has intentionally NO shutter command
 * and no BLE file transfer. The independent Wi-Fi-only code performs all
 * subsequent physical-shutter image detection via read-only HTTP.
 *
 * Protocol copied from the same repo's M02S real-world-proven AA13
 * 0x39/0x30 -> AA14 0x25 -> Android WiFiNetworkSpecifier flow, cross-checked
 * with MIT E09 reference Constants.swift and WifiMediaManager.swift.
 */
public final class M02sApStarter {
    public interface Callback {
        void log(String message);
        void ready(Network network, String host);
        void failed(String message);
        void lost(String message);
    }

    private static final UUID AA12 = UUID.fromString("0000aa12-0000-1000-8000-00805f9b34fb");
    private static final UUID AA13 = UUID.fromString("0000aa13-0000-1000-8000-00805f9b34fb");
    private static final UUID AA14 = UUID.fromString("0000aa14-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    // Same observed OEM-app passphrase as the original, successful M02S AP test.
    private static final String OEM_AP_PASSPHRASE = "12345678";

    private final Activity activity;
    private final Handler ui;
    private final ConnectivityManager connectivity;
    private final WifiManager wifi;
    private final Callback callback;

    private final List<BluetoothDevice> matchingDevices = new ArrayList<>();
    private final List<String> matchedAddresses = new ArrayList<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private ConnectivityManager.NetworkCallback networkCallback;
    private int generation;
    private boolean running;
    private boolean startedApRequest;
    private boolean apWriteAcknowledged;
    private boolean startedWifiNetworkRequest;
    private boolean targetReady;
    private int scanChecks;
    private String expectedSsid;
    private Network targetNetwork;
    private String targetHost;
    private long scanRequestedAtUs;
    private long firstFreshSeenAtMs;
    private String freshBssid;
    private long bleCommandAtMs;
    private long wifiRequestStartedAtMs;

    private Runnable bleTimeout;
    private Runnable firstDeviceDelay;
    private Runnable overallTimeout;
    private Runnable scanTimer;

    public M02sApStarter(Activity context, Handler mainHandler,
                         ConnectivityManager cm, WifiManager wm, Callback cb) {
        activity = context;
        ui = mainHandler;
        connectivity = cm;
        wifi = wm;
        callback = cb;
    }

    public boolean running() { return running; }
    public boolean ready() { return running && targetReady && targetNetwork != null; }

    public boolean isActive(Network network, String host) {
        if (!ready() || !targetNetwork.equals(network) || !targetHost.equals(host))
            return false;
        try {
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                return false;
            LinkProperties lp = connectivity.getLinkProperties(network);
            if (lp == null) return false;
            for (RouteInfo route : lp.getRoutes()) {
                InetAddress address = route.getGateway();
                if (address == null || address.getAddress().length != 4) continue;
                try {
                    if (host.equals(ProbePolicy.checkedLocalHost(address.getHostAddress())))
                        return true;
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (SecurityException | IllegalStateException ex) {
            return false;
        }
        return false;
    }

    /** User gesture only. Scans a M02S-advertised BLE peripheral, never other devices. */
    public void start() {
        if (running) {
            callback.log("眼镜Wi-Fi启动流程已经进行中，不重复发送蓝牙请求");
            return;
        }
        stop();
        running = true;
        final int token = generation;
        startedApRequest = false;
        apWriteAcknowledged = false;
        startedWifiNetworkRequest = false;
        matchingDevices.clear();
        matchedAddresses.clear();

        BluetoothManager manager = (BluetoothManager) activity.getSystemService(
                Activity.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            fail(token, "手机蓝牙未开启，无法给眼镜发送一次开热点命令");
            return;
        }
        if (wifi == null || !wifi.isWifiEnabled()) {
            fail(token, "手机WLAN未开启，无法连接眼镜热点");
            return;
        }
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                fail(token, "系统BLE扫描器不可用");
                return;
            }
            callback.log("开始发现M02S眼镜：只向唯一匹配目标发送原厂开AP指令，不发送拍照命令");
            scanner.startScan(scanCallback);
            bleTimeout = () -> finishBleScan(token);
            ui.postDelayed(bleTimeout, 8000);
            overallTimeout = () -> fail(token, "90秒内未建立眼镜AP网络，停止本轮；不重试开热点指令");
            ui.postDelayed(overallTimeout, 90_000L);
        } catch (SecurityException | IllegalStateException ex) {
            fail(token, "蓝牙扫描失败：" + ex.getClass().getSimpleName());
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            String name = result.getScanRecord() == null ? null
                    : result.getScanRecord().getDeviceName();
            if (name == null || !name.toUpperCase(Locale.ROOT).startsWith("M02S"))
                return;
            BluetoothDevice device = result.getDevice();
            String address;
            try { address = device.getAddress(); }
            catch (SecurityException ex) { return; }
            final int token = generation;
            ui.post(() -> {
                if (!valid(token) || scanner == null
                        || matchedAddresses.contains(address)) return;
                matchedAddresses.add(address);
                matchingDevices.add(device);
                callback.log("发现目标M02S候选眼镜，第" + matchingDevices.size()
                        + "台；地址和SSID均不写日志");
                if (firstDeviceDelay == null) {
                    firstDeviceDelay = () -> finishBleScan(token);
                    ui.postDelayed(firstDeviceDelay, 1800L);
                }
            });
        }
        @Override public void onScanFailed(int errorCode) {
            final int token = generation;
            ui.post(() -> fail(token, "BLE扫描失败，错误码=" + errorCode));
        }
    };

    private boolean valid(int token) { return running && token == generation; }

    private void finishBleScan(int token) {
        if (!valid(token)) return;
        stopScanner();
        if (bleTimeout != null) ui.removeCallbacks(bleTimeout);
        bleTimeout = null;
        if (firstDeviceDelay != null) ui.removeCallbacks(firstDeviceDelay);
        firstDeviceDelay = null;
        if (matchingDevices.size() != 1) {
            fail(token, matchingDevices.isEmpty()
                    ? "找不到M02S蓝牙广播；未发送任何命令"
                    : "扫描到多台M02S眼镜，为避免操控错误设备，未自动连接");
            return;
        }
        BluetoothDevice target = matchingDevices.get(0);
        callback.log("蓝牙目标唯一，连接LE1M，准备订阅AA14回执，仅用于启动AP");
        try {
            gatt = target.connectGatt(activity, false,
                    new BluetoothGattCallback() {
                        @Override public void onConnectionStateChange(
                                BluetoothGatt connection, int code, int state) {
                            ui.post(() -> {
                                if (!valid(token) || connection != gatt) return;
                                if (code != BluetoothGatt.GATT_SUCCESS) {
                                    fail(token, "GATT连接失败 status=" + code);
                                } else if (state == BluetoothProfile.STATE_CONNECTED) {
                                    callback.log("BLE GATT连接成功，开始发现AA12服务");
                                    try {
                                        if (!connection.discoverServices())
                                            fail(token, "系统拒绝GATT服务发现");
                                    } catch (SecurityException ex) {
                                        fail(token, "GATT发现服务权限不足");
                                    }
                                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                                    if (!targetReady) fail(token, "Wi-Fi建立前眼镜BLE断开");
                                    else callback.log("Wi-Fi已经建立，BLE断开；只要AP Network仍在线继续HTTP监听");
                                }
                            });
                        }
                        @Override public void onServicesDiscovered(
                                BluetoothGatt connection, int status) {
                            ui.post(() -> {
                                if (!valid(token) || connection != gatt) return;
                                if (status != 0) {
                                    fail(token, "AA12服务发现失败=" + status);
                                    return;
                                }
                                subscribeAA14(token);
                            });
                        }
                        @Override public void onDescriptorWrite(
                                BluetoothGatt connection, BluetoothGattDescriptor descriptor,
                                int status) {
                            ui.post(() -> {
                                if (!valid(token) || connection != gatt
                                        || descriptor == null
                                        || !AA14.equals(descriptor.getCharacteristic().getUuid()))
                                    return;
                                if (status != 0) fail(token, "AA14通知订阅失败=" + status);
                                else {
                                    callback.log("AA14通知订阅成功，准备唯一一次官方0x39/0x30开AP");
                                    writeApCommand(token);
                                }
                            });
                        }
                        @Override public void onCharacteristicWrite(
                                BluetoothGatt connection, BluetoothGattCharacteristic characteristic,
                                int status) {
                            ui.post(() -> {
                                if (!valid(token) || connection != gatt
                                        || characteristic == null
                                        || !AA13.equals(characteristic.getUuid())) return;
                                if (status != 0) fail(token, "AA13开热点写入失败 status=" + status);
                                else {
                                    apWriteAcknowledged = true;
                                    callback.log("AA13 0x39/0x30写入成功；等待眼镜AA14 0x25 SSID信息");
                                }
                            });
                        }
                        @Override public void onCharacteristicChanged(
                                BluetoothGatt connection, BluetoothGattCharacteristic ch,
                                byte[] value) {
                            byte[] copy = value == null ? new byte[0] : value.clone();
                            ui.post(() -> acceptNotification(token, connection, ch, copy));
                        }
                        @Override @SuppressWarnings("deprecation")
                        public void onCharacteristicChanged(
                                BluetoothGatt connection, BluetoothGattCharacteristic ch) {
                            byte[] copy = ch.getValue() == null ? new byte[0] : ch.getValue().clone();
                            ui.post(() -> acceptNotification(token, connection, ch, copy));
                        }
                    }, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M);
            if (gatt == null) fail(token, "connectGatt返回空对象");
        } catch (SecurityException | IllegalStateException ex) {
            fail(token, "无法建立蓝牙连接：" + ex.getClass().getSimpleName());
        }
    }

    private void subscribeAA14(int token) {
        if (!valid(token) || gatt == null) return;
        BluetoothGattService svc = gatt.getService(AA12);
        BluetoothGattCharacteristic ch = svc == null ? null : svc.getCharacteristic(AA14);
        BluetoothGattCharacteristic write = svc == null ? null : svc.getCharacteristic(AA13);
        if (ch == null || write == null || (write.getProperties()
                & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
            fail(token, "眼镜缺少AA13可写或AA14通知特征，不能开启AP");
            return;
        }
        BluetoothGattDescriptor descriptor = ch.getDescriptor(CCCD);
        if (descriptor == null) {
            fail(token, "AA14通知无标准CCCD描述符");
            return;
        }
        try {
            if (!gatt.setCharacteristicNotification(ch, true)) {
                fail(token, "Android拒绝AA14通知");
                return;
            }
            boolean queued;
            if (Build.VERSION.SDK_INT >= 33) {
                queued = gatt.writeDescriptor(descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == 0;
            } else {
                @SuppressWarnings("deprecation")
                boolean accepted = descriptor.setValue(
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                @SuppressWarnings("deprecation")
                boolean success = accepted && gatt.writeDescriptor(descriptor);
                queued = success;
            }
            if (!queued) fail(token, "AA14 CCCD订阅写入未受理");
        } catch (SecurityException | IllegalStateException ex) {
            fail(token, "AA14订阅异常：" + ex.getClass().getSimpleName());
        }
    }

    private void writeApCommand(int token) {
        if (!valid(token) || gatt == null || startedApRequest) return;
        byte[] command = M02sApWire.apRequest();
        if (!M02sApWire.authorized(command)) {
            fail(token, "BLE命令不在精确白名单");
            return;
        }
        BluetoothGattService svc = gatt.getService(AA12);
        BluetoothGattCharacteristic ch = svc == null ? null : svc.getCharacteristic(AA13);
        if (ch == null) {
            fail(token, "眼镜无AA13写接口");
            return;
        }
        try {
            startedApRequest = true; // no repeats even if the ACK is lost
            bleCommandAtMs = SystemClock.elapsedRealtime();
            int result;
            if (Build.VERSION.SDK_INT >= 33) {
                result = gatt.writeCharacteristic(ch, command,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                @SuppressWarnings("deprecation")
                boolean okSet = ch.setValue(command);
                @SuppressWarnings("deprecation")
                boolean accepted = okSet && gatt.writeCharacteristic(ch);
                result = accepted ? 0 : -1;
            }
            if (result != 0) {
                fail(token, "系统拒绝提交0x39/0x30 code=" + result);
                return;
            }
            callback.log("仅发送一次原厂BLE 0x39/0x30开启Wi-Fi AP；没有发送蓝牙拍照指令");
        } catch (SecurityException | IllegalStateException ex) {
            fail(token, "AA13开AP异常：" + ex.getClass().getSimpleName());
        }
    }

    private void acceptNotification(int token, BluetoothGatt connection,
                                    BluetoothGattCharacteristic ch, byte[] bytes) {
        if (!valid(token) || connection != gatt || ch == null
                || !AA14.equals(ch.getUuid()) || expectedSsid != null
                || !startedApRequest) return;
        if (SystemClock.elapsedRealtime() - bleCommandAtMs > 30_000L) return;
        String ssid = M02sApWire.decodeWifiSsid(bytes);
        if (ssid == null) return;
        expectedSsid = ssid; // only in RAM
        callback.log("收到AA14 0x25准确SSID；先查是否已连接该眼镜网络，未连接则扫描；SSID与凭证不写日志");
        if (tryReuseConnectedTarget(token)) return;
        // A beacon emitted between the actual BLE AP wake command and the 0x25
        // SSID reply belongs to this AP session. The old post-SSID scan anchor
        // incorrectly rejected this already-available new hotspot as cached.
        scanRequestedAtUs = bleCommandAtMs * 1000L;
        callback.log("热点扫描新鲜度从本轮BLE开AP命令开始计算，而不是收到SSID之后；防漏判最早的新广播");
        firstFreshSeenAtMs = 0;
        freshBssid = null;
        scanChecks = 0;
        try {
            wifi.startScan();
        } catch (SecurityException | IllegalStateException ex) {
            callback.log("Android主动Wi-Fi扫描受限：" + ex.getClass().getSimpleName());
        }
        scanForAp(token);
    }

    /** If Android is already connected to exactly the BLE-confirmed SSID,
     * reuse that Network, but NEVER assume SSID alone proves real eyewear:
     * MainActivity performs an OEM HTTP200 /app/getfilelist check next.
     */
    private boolean tryReuseConnectedTarget(int token) {
        if (!valid(token) || !M02sWifiPolicy.isConnectableSsid(expectedSsid)) return false;
        try {
            for (Network network : connectivity.getAllNetworks()) {
                NetworkCapabilities cap = connectivity.getNetworkCapabilities(network);
                if (cap == null
                        || !cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || !(cap.getTransportInfo() instanceof WifiInfo)) continue;
                WifiInfo info = (WifiInfo) cap.getTransportInfo();
                if (!expectedSsid.equals(M02sApScanPolicy.unquoteWifiSsid(info.getSSID())))
                    continue;
                LinkProperties properties = connectivity.getLinkProperties(network);
                if (properties == null) continue;
                for (RouteInfo route : properties.getRoutes()) {
                    InetAddress gateway = route.getGateway();
                    if (gateway == null || gateway.getAddress().length != 4) continue;
                    String host;
                    try { host = ProbePolicy.checkedLocalHost(gateway.getHostAddress()); }
                    catch (IllegalArgumentException ignored) { continue; }
                    targetNetwork = network;
                    targetHost = host;
                    targetReady = true;
                    cancelEstablishTimeout();
                    callback.log("已复用系统中同SSID且有私有网关的Wi-Fi Network；"
                            + "不再请求新连接，随后必须通过原厂HTTP接口验证眼镜身份");
                    callback.ready(network, host);
                    return true;
                }
            }
        } catch (SecurityException | IllegalStateException ex) {
            callback.log("无法复用现有目标Wi-Fi，按原厂AP精准建连方式继续；原因类型="
                    + ex.getClass().getSimpleName());
        }
        return false;
    }

    private void scanForAp(int token) {
        if (!valid(token) || expectedSsid == null || startedWifiNetworkRequest) return;
        scanChecks++;
        List<M02sApScanPolicy.Beacon> beacons = new ArrayList<>();
        try {
            List<android.net.wifi.ScanResult> all = wifi.getScanResults();
            if (all != null) {
                for (android.net.wifi.ScanResult item : all) {
                    if (item != null && expectedSsid.equals(item.SSID))
                        beacons.add(new M02sApScanPolicy.Beacon(
                                item.SSID, item.BSSID, item.timestamp));
                }
            }
        } catch (SecurityException | IllegalStateException ex) {
            if (scanChecks == 1)
                callback.log("系统扫描结果受限：" + ex.getClass().getSimpleName());
        }
        long now = SystemClock.elapsedRealtime();
        M02sApReadinessPolicy.Observation observed =
                M02sApReadinessPolicy.inspect(expectedSsid, beacons,
                        scanRequestedAtUs, now * 1000L);
        if (observed.kind == M02sApReadinessPolicy.Kind.FRESH_AMBIGUOUS) {
            fail(token, "出现多个不同BSSID但同名的目标热点；为避免误连接已停止");
            return;
        }
        if (observed.kind == M02sApReadinessPolicy.Kind.FRESH_UNIQUE) {
            if (freshBssid == null
                    || !freshBssid.equalsIgnoreCase(observed.uniqueBssid)) {
                freshBssid = observed.uniqueBssid;
                firstFreshSeenAtMs = now;
                callback.log("检测到本次启动后的唯一眼镜AP广播，等待短暂稳定");
            }
            if (M02sApReadinessPolicy.canConnect(observed, freshBssid,
                    firstFreshSeenAtMs, now)) {
                connectWifi(token, true);
                return;
            }
        } else {
            freshBssid = null;
            firstFreshSeenAtMs = 0;
        }
        long elapsedUs = now * 1000L - scanRequestedAtUs;
        if (elapsedUs >= M02sApConnectPolicy.SCAN_WAIT_LIMIT_US) {
            if (M02sApConnectPolicy.allowExactSsidFallback(
                    apWriteAcknowledged, expectedSsid, startedWifiNetworkRequest,
                    observed.kind, elapsedUs)) {
                callback.log("Android扫描12秒未提供持续有效的本次新热点证据；"
                        + "依据已校验AA14 0x25准确SSID，仅向该目标申请一次系统Wi-Fi连接。"
                        + "不把旧缓存当热点就绪，不连接其它SSID；以onAvailable、真实网关和原厂HTTP结果为准");
                connectWifi(token, false);
            } else {
                fail(token, "12秒扫描仍不能连接：开AP写入未确认或目标SSID未校验；拒绝未知Wi-Fi请求");
            }
            return;
        }
        if (scanChecks == 1 || scanChecks % 5 == 0)
            callback.log("等待本次眼镜AP新扫描，第" + scanChecks
                    + "次；目标状态=" + observed.kind);
        scanTimer = () -> scanForAp(token);
        ui.postDelayed(scanTimer, 650L);
    }

    /**
     * Reuses the OEM-tested exact SSID + WPA2 request path. The caller only
     * changes when the one request is allowed, never its destination.
     */
    private void connectWifi(int token, boolean observedFreshBeacon) {
        if (!valid(token) || startedWifiNetworkRequest || expectedSsid == null
                || !apWriteAcknowledged
                || !M02sWifiPolicy.isConnectableSsid(expectedSsid)) return;
        startedWifiNetworkRequest = true;
        wifiRequestStartedAtMs = SystemClock.elapsedRealtime();
        if (scanTimer != null) {
            ui.removeCallbacks(scanTimer);
            scanTimer = null;
        }
        callback.log(observedFreshBeacon
                ? "检测到目标AP本次新广播并完成稳定等待：申请一次Android精准SSID+WPA2连接"
                : "仅对BLE证实的精确SSID申请一次Android连接；扫描受限不等于设备已上线，待真实网关和HTTP校验");
        try {
            // On vivo, mirror the actual OEM-style pattern+BSSID request that
            // succeeded on this M02S in the separate E09 v0.1.13 live test.
            // Match ONLY the BLE-confirmed SSID, NEVER an unknown nearby AP.
            WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder()
                    .setWpa2Passphrase(OEM_AP_PASSPHRASE);
            if ("vivo".equalsIgnoreCase(Build.BRAND)) {
                builder.setSsidPattern(new PatternMatcher(
                        expectedSsid, PatternMatcher.PATTERN_LITERAL));
                if (observedFreshBeacon) {
                    if (!M02sApScanPolicy.validBssid(freshBssid)) {
                        fail(token, "热点BSSID证据无效，禁止猜测目标连接");
                        return;
                    }
                    builder.setBssid(MacAddress.fromString(freshBssid));
                    callback.log("vivo原厂式Wi-Fi首次申请：SSID字面量Pattern + 本轮唯一新鲜BSSID；不重试");
                } else {
                    callback.log("vivo原厂式Wi-Fi扫描受限回退：BLE确认SSID字面量Pattern；不猜BSSID，不重试");
                }
            } else {
                builder.setSsid(expectedSsid);
                callback.log("非vivo手机保持原SSID精确匹配；没有扩展连接权限");
            }
            WifiNetworkSpecifier spec = builder.build();
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(spec)
                    .build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network net) {
                    ui.post(() -> {
                        if (!valid(token) || networkCallback != this) return;
                        targetNetwork = net;
                        callback.log("Android眼镜AP Network onAvailable；验证真实本地路由");
                        considerGateway(token, net, connectivity.getLinkProperties(net));
                    });
                }
                @Override public void onLinkPropertiesChanged(Network net, LinkProperties lp) {
                    ui.post(() -> {
                        if (valid(token) && networkCallback == this)
                            considerGateway(token, net, lp);
                    });
                }
                @Override public void onLost(Network net) {
                    ui.post(() -> {
                        if (!valid(token) || networkCallback != this
                                || targetNetwork == null || !targetNetwork.equals(net)) return;
                        boolean wasReady = targetReady;
                        stop();
                        if (wasReady) callback.lost("眼镜专属Wi-Fi Network断开，停止自动取图");
                        else callback.failed("Android Wi-Fi连接中途丢失");
                    });
                }
                @Override public void onUnavailable() {
                    ui.post(() -> {
                        if (!valid(token) || networkCallback != this) return;
                        long elapsed = SystemClock.elapsedRealtime() - wifiRequestStartedAtMs;
                        callback.log("本轮Android精确Wi-Fi申请onUnavailable，耗时="
                                + elapsed + "ms；不代表BLE失败，也不等于眼镜固件拒绝");
                        fail(token, "本轮Wi-Fi请求未满足，耗时=" + elapsed
                                + "ms；需要核对Android关联状态，未盲目重试");
                    });
                }
            };
            connectivity.requestNetwork(request, networkCallback, 48_000);
        } catch (SecurityException | IllegalArgumentException | IllegalStateException ex) {
            fail(token, "Android网络连接请求失败：" + ex.getClass().getSimpleName());
        }
    }

    private void considerGateway(int token, Network net, LinkProperties lp) {
        if (!valid(token) || targetReady || lp == null) return;
        if (targetNetwork != null && !targetNetwork.equals(net)) return;
        for (RouteInfo route : lp.getRoutes()) {
            InetAddress gw = route.getGateway();
            if (gw == null || gw.getAddress().length != 4) continue;
            final String host;
            try { host = ProbePolicy.checkedLocalHost(gw.getHostAddress()); }
            catch (IllegalArgumentException ex) { continue; }
            targetNetwork = net;
            targetHost = host;
            targetReady = true;
            cancelEstablishTimeout();
            callback.log("Android眼镜专属AP和可信私有网关已建立；下一步用原厂HTTP列表验真");
            callback.ready(net, host);
            return;
        }
    }

    /** The initial AP connection deadline must not interrupt 120-second photo watching. */
    private void cancelEstablishTimeout() {
        if (overallTimeout != null) {
            ui.removeCallbacks(overallTimeout);
            overallTimeout = null;
        }
    }

    private void fail(int token, String reason) {
        if (!valid(token)) return;
        stop();
        callback.failed(reason);
    }

    private void stopScanner() {
        if (scanner == null) return;
        try { scanner.stopScan(scanCallback); }
        catch (SecurityException | IllegalStateException ignored) { }
        scanner = null;
    }

    public void stop() {
        generation++;
        running = false;
        targetReady = false;
        startedApRequest = false;
        apWriteAcknowledged = false;
        startedWifiNetworkRequest = false;
        wifiRequestStartedAtMs = 0L;
        expectedSsid = null;
        targetNetwork = null;
        targetHost = null;
        if (bleTimeout != null) ui.removeCallbacks(bleTimeout);
        if (firstDeviceDelay != null) ui.removeCallbacks(firstDeviceDelay);
        if (overallTimeout != null) ui.removeCallbacks(overallTimeout);
        if (scanTimer != null) ui.removeCallbacks(scanTimer);
        bleTimeout = null;
        firstDeviceDelay = null;
        overallTimeout = null;
        scanTimer = null;
        stopScanner();
        if (networkCallback != null) {
            try { connectivity.unregisterNetworkCallback(networkCallback); }
            catch (IllegalArgumentException | SecurityException ignored) { }
            networkCallback = null;
        }
        if (gatt != null) {
            try { gatt.disconnect(); }
            catch (SecurityException | IllegalStateException ignored) { }
            try { gatt.close(); }
            catch (Exception ignored) { }
            gatt = null;
        }
    }
}
