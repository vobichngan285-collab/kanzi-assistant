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
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
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
        void standbyReady();
        void photoActionCandidate();
        void photoActionEnded();
        void deviceProjectReported(String project);
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
    private boolean bleStandbyReady;
    private boolean lastPhotoFlagHigh;
    private long lastPhotoActionAtMs;
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
    private long wifiRequestAtMs;
    private int wifiRequestCount;
    private boolean previousRequestPinned;
    private long freshScanStartedAtMs;
    private static final long FRESH_SCAN_MAX_MS = 7_000L;
    private static final long FRESH_SCAN_POLL_MS = 500L;
    private int lastImportFlag = -1; // -1 = optional E09 field not observed
    private boolean absentImportReported;
    private boolean projectQueryPending;
    private Runnable projectQueryTimeout;

    private Runnable bleTimeout;
    private Runnable firstDeviceDelay;
    private Runnable overallTimeout;
    private Runnable scanTimer;
    private Runnable ssidTimeout;

    public M02sApStarter(Activity context, Handler mainHandler,
                         ConnectivityManager cm, WifiManager wm, Callback cb) {
        activity = context;
        ui = mainHandler;
        connectivity = cm;
        wifi = wm;
        callback = cb;
    }

    public boolean running() { return running; }
    public boolean standby() { return running && bleStandbyReady && gatt != null; }
    public boolean ready() { return running && targetReady && targetNetwork != null; }

    /** User has physically pressed shutter, or upstream ACTION_SYNC indicates capture.
     * No camera-control command ever leaves the phone. */
    public boolean requestPhotoTransfer() {
        if (!standby() || projectQueryPending || startedApRequest
                || startedWifiNetworkRequest || targetReady)
            return false;
        writeApCommand(generation);
        return startedApRequest;
    }

    /**
     * Explicit user-triggered READ-ONLY OEM 0x64 project query. Never called
     * automatically and never switches AP/P2P mode based on the result.
     */
    public boolean requestReadOnlyProject() {
        if (!standby() || projectQueryPending || startedApRequest
                || startedWifiNetworkRequest || targetReady) return false;
        BluetoothGattService svc = gatt.getService(AA12);
        BluetoothGattCharacteristic ch = svc == null ? null : svc.getCharacteristic(AA13);
        if (ch == null || (ch.getProperties()
                & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) return false;
        byte[] frame = M02sApWire.deviceProjectQuery();
        if (!M02sApWire.authorizedReadOnlyProject(frame)) return false;
        final int token = generation;
        projectQueryPending = true;
        try {
            int code;
            if (Build.VERSION.SDK_INT >= 33) {
                code = gatt.writeCharacteristic(ch, frame,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                @SuppressWarnings("deprecation")
                boolean set = ch.setValue(frame);
                @SuppressWarnings("deprecation")
                boolean ok = set && gatt.writeCharacteristic(ch);
                code = ok ? 0 : -1;
            }
            if (code != 0) {
                cancelProjectQuery();
                callback.log("只读0x64设备项目查询未获GATT提交许可；code=" + code);
                return false;
            }
            callback.log("用户主动发送一次只读0x64项目号查询；不改变Wi-Fi模式");
            projectQueryTimeout = () -> {
                if (valid(token) && projectQueryPending) {
                    callback.log("只读项目查询3秒无可解析应答；不代表眼镜不支持AP或P2P");
                    cancelProjectQuery();
                }
            };
            ui.postDelayed(projectQueryTimeout, 3000L);
            return true;
        } catch (SecurityException | IllegalStateException ex) {
            cancelProjectQuery();
            callback.log("只读项目查询异常=" + ex.getClass().getSimpleName());
            return false;
        }
    }

    private void cancelProjectQuery() {
        projectQueryPending = false;
        if (projectQueryTimeout != null) ui.removeCallbacks(projectQueryTimeout);
        projectQueryTimeout = null;
    }

    /** Passive system-state snapshot: no scan, no new Wi-Fi request, no secret logging.
     * ScanResult.timestamp describes the CACHED scan age, not proof that AP is up. */
    private void observePhoneWifi(String phase) {
        int wifiNetworks = 0;
        try {
            for (Network net : connectivity.getAllNetworks()) {
                NetworkCapabilities caps = connectivity.getNetworkCapabilities(net);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    wifiNetworks++;
            }
        } catch (SecurityException | IllegalStateException ignored) {
            wifiNetworks = -1;
        }
        String cached = "SSID未知或缓存不可读";
        if (expectedSsid != null && wifi != null) {
            try {
                long youngest = Long.MAX_VALUE;
                List<android.net.wifi.ScanResult> entries = wifi.getScanResults();
                long nowUs = SystemClock.elapsedRealtimeNanos() / 1000L;
                if (entries != null) {
                    for (android.net.wifi.ScanResult e : entries) {
                        if (e == null || !expectedSsid.equals(e.SSID)) continue;
                        long ageMs = e.timestamp > 0 && nowUs >= e.timestamp
                                ? (nowUs - e.timestamp) / 1000L : -1L;
                        if (ageMs >= 0 && ageMs < youngest) youngest = ageMs;
                    }
                    cached = youngest == Long.MAX_VALUE
                            ? "缓存未见同SSID（不证明热点未广播）"
                            : "缓存同SSID最小年龄=" + youngest
                            + "ms（只读旧扫描缓存，不证明当前AP可关联）";
                }
            } catch (SecurityException | IllegalStateException ignored) {
                cached = "扫描缓存受系统权限限制";
            }
        }
        callback.log("Wi-Fi只读快照[" + phase + "]：Android当前WiFi Network数="
                + wifiNetworks + "；" + cached);
    }

    /** Release only the phone Wi-Fi request; keep BLE listening for next shutter.
     * This does NOT prove the glasses themselves have exited import mode. */
    public void releasePhotoNetwork() {
        observePhoneWifi("释放前");
        if (networkCallback != null) {
            // Clear ownership BEFORE releasing Android's network request.
            // Any queued callback from this old photo session must be ignored.
            ConnectivityManager.NetworkCallback oldCallback = networkCallback;
            networkCallback = null;
            try { connectivity.unregisterNetworkCallback(oldCallback); }
            catch (IllegalArgumentException | SecurityException ignored) { }
            callback.log("本张照片传输完成/结束：释放手机本轮专属Wi-Fi请求；保留BLE待机");
        }
        wifiRequestAtMs = 0L;
        wifiRequestCount = 0;
        previousRequestPinned = false;
        cancelFreshScan();
        targetNetwork = null;
        targetHost = null;
        targetReady = false;
        expectedSsid = null;
        startedWifiNetworkRequest = false;
        startedApRequest = false;
        apWriteAcknowledged = false;
        cancelSsidTimeout();
        observePhoneWifi("解除请求后瞬时状态-非最终断网证明");
        if (bleStandbyReady) callback.standbyReady();
    }

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
        bleStandbyReady = false;
        lastPhotoFlagHigh = false;
        lastPhotoActionAtMs = 0L;
        lastImportFlag = -1;
        absentImportReported = false;
        cancelProjectQuery();
        startedWifiNetworkRequest = false;
        wifiRequestAtMs = 0L;
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
            callback.log("开始BLE待机连接：只扫描唯一M02S，先监听实体拍照事件，不开启Wi-Fi");
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
                                    bleStandbyReady = false;
                                    if (!targetReady) fail(token, "眼镜BLE待机/建连状态断开");
                                    else callback.log("Wi-Fi仍已连接，但蓝牙待机断开；本轮取图后需要重新准备BLE");
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
                                    bleStandbyReady = true;
                                    cancelEstablishTimeout();
                                    callback.log("AA14被动通知监听已就绪；保持BLE待机。请按眼镜实体快门。");
                                    callback.standbyReady();
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
                                if (projectQueryPending) {
                                    callback.log("只读设备项目号0x64的GATT写回执status=" + status
                                            + "（不代表已收到设备项目号）");
                                    if (status != 0) cancelProjectQuery();
                                    return;
                                }
                                if (status != 0) fail(token, "AA13开热点写入失败 status=" + status);
                                else {
                                    apWriteAcknowledged = true;
                                    callback.log("AA13唯一一次0x39/0x30写入成功；等待5秒内收到有效AA14 0x25 SSID");
                                    // A successful BLE write only means the command was delivered.
                                    // Never leave the UI silently waiting for a firmware reply.
                                    cancelSsidTimeout();
                                    ssidTimeout = () -> {
                                        if (valid(token) && startedApRequest && apWriteAcknowledged
                                                && expectedSsid == null && !targetReady) {
                                            callback.log("AA13写入成功但5秒内没有校验通过的AA14 0x25 SSID；停止本轮，不重复发命令");
                                            fail(token, "眼镜未返回Wi-Fi SSID（0x25），不是Android连接慢；请检查拍照后AP是否真正启动");
                                        }
                                    };
                                    ui.postDelayed(ssidTimeout, 5000L);
                                    if (expectedSsid != null && !startedWifiNetworkRequest && !targetReady) {
                                        cancelSsidTimeout();
                                        if (!tryReuseConnectedTarget(token)) beginFreshApScan(token, false);
                                    }
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
        if (!valid(token) || !bleStandbyReady || gatt == null || startedApRequest) return;
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
            observePhoneWifi("本轮开AP命令之前");
            startedApRequest = true; // no repeats even if the ACK is lost
            bleCommandAtMs = SystemClock.elapsedRealtime();
            scanRequestedAtUs = SystemClock.elapsedRealtimeNanos() / 1000L;
            wifiRequestCount = 0;
            previousRequestPinned = false;
            cancelFreshScan();
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
                || !AA14.equals(ch.getUuid())) return;

        // E09 ACTION_SYNC flags: payload[0]=isTakePhoto, optional
        // payload[9]=isImport. Never infer false from a missing tenth byte.
        int photoFlag = M02sApWire.actionSyncPhotoFlag(bytes);
        if (photoFlag >= 0 && bleStandbyReady) {
            int importFlag = M02sApWire.actionSyncImportFlag(bytes);
            if (importFlag >= 0 && importFlag != lastImportFlag) {
                callback.log("AA14 0x45被动isImport状态="
                        + importFlag + "（E09含义；M02S固件待实测；"
                        + "0=仅报告不活跃，1=仅报告活跃）");
                lastImportFlag = importFlag;
            } else if (importFlag < 0 && !absentImportReported) {
                absentImportReported = true;
                callback.log("AA14 0x45本次未携带合法的第10位isImport；"
                        + "缺失不等于导入状态=0");
            }
            boolean rising = photoFlag == 1 && !lastPhotoFlagHigh;
            boolean falling = photoFlag == 0 && lastPhotoFlagHigh;
            lastPhotoFlagHigh = photoFlag == 1;
            long now = SystemClock.elapsedRealtime();
            if (falling) {
                callback.log("被动AA14 0x45 isTakePhoto标志1→0，照片活动结束候选（并非保证已写盘）");
                callback.photoActionEnded();
            }
            if (rising && now - lastPhotoActionAtMs > 3500L) {
                lastPhotoActionAtMs = now;
                callback.log("被动AA14 0x45拍照状态标志出现0→1（开源E09推断，M02S待验证）");
                if (!startedApRequest && !targetReady && !startedWifiNetworkRequest) {
                    callback.photoActionCandidate();
                }
            }
            return;
        }

        if (projectQueryPending) {
            String project = M02sApWire.decodeReadOnlyProject(bytes);
            if (project != null) {
                cancelProjectQuery();
                callback.log("AA14 0x64收到格式/校验合法的设备项目号（只读，不记录客户信息）");
                callback.deviceProjectReported(project);
                return;
            }
        }

        if (!bleStandbyReady || !startedApRequest || expectedSsid != null
                || SystemClock.elapsedRealtime() - bleCommandAtMs > 30_000L)
            return;
        String ssid = M02sApWire.decodeWifiSsid(bytes);
        if (ssid == null) return;
        expectedSsid = ssid; // RAM only
        observePhoneWifi("本轮收到眼镜SSID");
        cancelSsidTimeout();
        callback.log("AA14 0x25真实SSID校验成功；开AP指令至SSID耗时="
                + (SystemClock.elapsedRealtime() - bleCommandAtMs)
                + "ms；本实验先检查目标热点新鲜广播（不能单凭SSID判定已就绪）");
        if (!apWriteAcknowledged) {
            callback.log("SSID先于AA13写入回执到达；待status=0后立即请求网络");
            return;
        }
        if (!tryReuseConnectedTarget(token)) beginFreshApScan(token, false);
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


    /**
     * Isolated Android-Wi-Fi experiment. The already validated BLE AP command
     * and original JPEG HTTP path are untouched. A BLE SSID response alone is
     * not proof that this round's AP is transmitting. Android scan APIs may
     * be throttled, so inability to observe a fresh scan is UNKNOWN, not OFF.
     */
    @SuppressWarnings("deprecation")
    private void beginFreshApScan(int token, boolean recovery) {
        if (!valid(token) || !apWriteAcknowledged
                || !M02sWifiPolicy.isConnectableSsid(expectedSsid)) return;
        cancelFreshScan();
        firstFreshSeenAtMs = 0L;
        freshBssid = null;
        scanChecks = 0;
        freshScanStartedAtMs = SystemClock.elapsedRealtime();
        if (recovery) {
            // Require another genuine scan timestamp AFTER the first failure.
            scanRequestedAtUs = SystemClock.elapsedRealtimeNanos() / 1000L;
        }
        boolean accepted = false;
        try {
            if (wifi != null) accepted = wifi.startScan();
        } catch (SecurityException | IllegalStateException ignored) {
            // Permission, OS throttling and location-service policy are not
            // evidence that the target is switched off.
        }
        callback.log((recovery ? "失败后唯一一次恢复检查" : "本轮首次连接前检查")
                + "：Android主动扫描受理=" + accepted
                + "；只认本轮BLE精确SSID和新鲜时间戳；"
                + "系统拒绝扫描不等于眼镜热点关闭");
        pollFreshApScan(token, recovery);
    }

    private void pollFreshApScan(int token, boolean recovery) {
        if (!valid(token) || !apWriteAcknowledged || targetReady
                || startedWifiNetworkRequest || expectedSsid == null) return;
        final long nowUs = SystemClock.elapsedRealtimeNanos() / 1000L;
        final long nowMs = SystemClock.elapsedRealtime();
        List<M02sApScanPolicy.Beacon> beacons = new ArrayList<>();
        try {
            if (wifi != null) {
                List<android.net.wifi.ScanResult> results = wifi.getScanResults();
                if (results != null) {
                    for (android.net.wifi.ScanResult result : results) {
                        if (result == null || !expectedSsid.equals(result.SSID)) continue;
                        beacons.add(new M02sApScanPolicy.Beacon(
                                result.SSID, result.BSSID, result.timestamp));
                    }
                }
            }
        } catch (SecurityException | IllegalStateException ignored) {
            // No active re-scan, no guessed BSSID, and no forged fresh beacon.
        }
        M02sApReadinessPolicy.Observation observation =
                M02sApReadinessPolicy.inspect(expectedSsid, beacons,
                        scanRequestedAtUs, nowUs);
        scanChecks++;
        if (observation.kind == M02sApReadinessPolicy.Kind.FRESH_UNIQUE) {
            if (freshBssid == null || !freshBssid.equalsIgnoreCase(observation.uniqueBssid)) {
                freshBssid = observation.uniqueBssid;
                firstFreshSeenAtMs = nowMs;
            }
            if (M02sApReadinessPolicy.canConnect(
                    observation, freshBssid, firstFreshSeenAtMs, nowMs)) {
                if (recovery && !M02sApRecoveryPolicy.allowOnePinnedRetry(
                        wifiRequestCount, previousRequestPinned,
                        observation.kind, freshBssid)) {
                    fail(token, "未满足唯一恢复条件，拒绝第二次Android网络请求");
                    return;
                }
                callback.log("本轮新鲜目标广播已核实：唯一BSSID，"
                        + "观察稳定等待=" + (nowMs - firstFreshSeenAtMs)
                        + "ms；" + (recovery ? "第二次改用BSSID精确匹配" : "第一次保持原SSID-only策略")
                        + "；未记录SSID/BSSID值");
                cancelFreshScan();
                connectWifi(token, recovery);
                return;
            }
        } else {
            firstFreshSeenAtMs = 0L;
            freshBssid = null;
        }
        if (nowMs - freshScanStartedAtMs >= FRESH_SCAN_MAX_MS) {
            cancelFreshScan();
            callback.log("扫描结束：结果=" + observation.kind
                    + "；缓存匹配数=" + observation.cachedCount
                    + "；本轮新鲜数=" + observation.freshCount
                    + "；检查次数=" + scanChecks
                    + "；未获新鲜广播不等于眼镜AP关闭");
            fail(token, recovery
                    ? "首次Wi-Fi申请失败后没有新的、唯一可核实广播；拒绝盲目重试"
                    : "未确认本轮目标热点新鲜广播，未向Android发起Wi-Fi连接请求");
            return;
        }
        scanTimer = () -> pollFreshApScan(token, recovery);
        ui.postDelayed(scanTimer, FRESH_SCAN_POLL_MS);
    }

    private void cancelFreshScan() {
        if (scanTimer != null) ui.removeCallbacks(scanTimer);
        scanTimer = null;
        firstFreshSeenAtMs = 0L;
        freshBssid = null;
    }

    /**
     * Attempt 1 is the verified SSID+WPA2 specifier, but only after a fresh AP
     * beacon. Attempt 2 is optional: only once and ONLY after a new unique
     * beacon allows a materially narrower, BSSID-pinned specifier.
     */
    private void connectWifi(int token, boolean pinBssid) {
        if (!valid(token) || startedWifiNetworkRequest || expectedSsid == null
                || !apWriteAcknowledged
                || !M02sWifiPolicy.isConnectableSsid(expectedSsid)
                || wifiRequestCount >= 2
                || (pinBssid && (wifiRequestCount != 1
                        || !M02sApScanPolicy.validBssid(freshBssid)))) return;
        startedWifiNetworkRequest = true;
        wifiRequestCount++;
        previousRequestPinned = pinBssid;
        wifiRequestAtMs = SystemClock.elapsedRealtime();
        observePhoneWifi("Android精确SSID申请前");
        callback.log("准确网络请求第" + wifiRequestCount + "次："
                + (pinBssid ? "SSID+WPA2+BSSID（仅一次恢复）"
                             : "原有SSID+WPA2（首次）")
                + "；Android回调/真实网关/原厂HTTP均需后续验证");
        try {
            WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder()
                    .setSsid(expectedSsid)
                    .setWpa2Passphrase(OEM_AP_PASSPHRASE);
            if (pinBssid) builder.setBssid(MacAddress.fromString(freshBssid));
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
                        observePhoneWifi("本轮onAvailable");
                        callback.log("Android本轮第" + wifiRequestCount
                                + "次准确SSID请求→onAvailable耗时="
                                + (SystemClock.elapsedRealtime() - wifiRequestAtMs)
                                + "ms；继续确认真实本地路由");
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
                        long elapsed = wifiRequestAtMs > 0L
                                ? SystemClock.elapsedRealtime() - wifiRequestAtMs : -1L;
                        observePhoneWifi("本轮onUnavailable");
                        callback.log("Android本轮第" + wifiRequestCount
                                + "次准确SSID请求onUnavailable；等待="
                                + elapsed + "ms；仅表示网络请求未满足，"
                                + "不能据此判定系统限制重复连接或眼镜热点坏了");
                        // Android releases an unavailable request automatically.
                        // Its late callbacks cannot control a newer request.
                        networkCallback = null;
                        if (tryReuseConnectedTarget(token)) {
                            callback.log("系统请求失败时发现已有同SSID目标网络；"
                                    + "无需重复申请，交由原厂HTTP验证");
                            return;
                        }
                        if (wifiRequestCount == 1 && !previousRequestPinned) {
                            startedWifiNetworkRequest = false;
                            callback.log("首次失败：只做一次重新扫描和策略变更检查；"
                                    + "无新鲜唯一广播就拒绝第二次申请；不再次发送BLE开AP");
                            beginFreshApScan(token, true);
                            return;
                        }
                        fail(token, "第" + wifiRequestCount
                                + "次Android Wi-Fi请求仍未成功（onUnavailable，"
                                + elapsed + "ms）；已达最大请求次数，停止");
                    });
                }
            };
            connectivity.requestNetwork(request, networkCallback, 25_000);
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
            observePhoneWifi("已验证网关");
            callback.ready(net, host);
            return;
        }
    }

    /** The initial AP connection deadline must not interrupt 120-second photo watching. */
    private void cancelSsidTimeout() {
        if (ssidTimeout != null) {
            ui.removeCallbacks(ssidTimeout);
            ssidTimeout = null;
        }
    }

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
        bleStandbyReady = false;
        lastPhotoFlagHigh = false;
        startedApRequest = false;
        apWriteAcknowledged = false;
        startedWifiNetworkRequest = false;
        expectedSsid = null;
        targetNetwork = null;
        targetHost = null;
        if (bleTimeout != null) ui.removeCallbacks(bleTimeout);
        if (firstDeviceDelay != null) ui.removeCallbacks(firstDeviceDelay);
        if (overallTimeout != null) ui.removeCallbacks(overallTimeout);
        cancelFreshScan();
        cancelSsidTimeout();
        cancelProjectQuery();
        lastImportFlag = -1;
        absentImportReported = false;
        bleTimeout = null;
        firstDeviceDelay = null;
        overallTimeout = null;
        scanTimer = null;
        stopScanner();
        if (networkCallback != null) {
            ConnectivityManager.NetworkCallback oldCallback = networkCallback;
            networkCallback = null;
            try { connectivity.unregisterNetworkCallback(oldCallback); }
            catch (IllegalArgumentException | SecurityException ignored) { }
        }
        wifiRequestAtMs = 0L;
        wifiRequestCount = 0;
        previousRequestPinned = false;
        if (gatt != null) {
            try { gatt.disconnect(); }
            catch (SecurityException | IllegalStateException ignored) { }
            try { gatt.close(); }
            catch (Exception ignored) { }
            gatt = null;
        }
    }
}
