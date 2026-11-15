package com.vobichngan.bluestar.m02slab;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * M02S vendor-style manual-import comparison: physical shutter and
 * Wi-Fi/HTTP media import are deliberately separate user actions.
 * BLE passively detects ACTION_SYNC 0x45 photo candidate / finish.
 * Only a deliberate click on "导入高清原图" may send OEM 0x39/0x30.
 * No BLE remote shutter command, Wi-Fi scanning delay, continuous polling,
 * no full-file-clear command, cloud upload, album write, or OTA.
 */
public final class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 1601;
    // E09 0x45 falling edge is a photo-finished candidate, NOT proof of a saved file.
    // No automatic AP opening, no fixed delay, no retry by waiting.
    private static final long ONE_SHOT_MAX_MS = 300_000L;
    private static final long LIST_RECHECK_MS = 350L;
    private static final int LIST_MAX_TRIES = 12;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final StringBuilder diagnostics = new StringBuilder();
    private M02sApStarter starter;
    private TextView statusText;
    private TextView logView;
    private TextView resultText;
    private TextView projectText;
    private Button manualImportButton;
    /** Explicit opt-in; manual v0.1.29 path stays the default. */
    private boolean fastAutoImportEnabled;
    private long lastShutterStartedAtMs;
    private long shutterStartedForTransferMs;
    private long shutterFinishedForTransferMs;
    private long latestOriginalReadyAtMs;
    private ImageView preview;
    private Network network;
    private String gateway;
    private boolean destroyed;
    private volatile boolean inTransfer;
    private boolean listBusy;
    // Count actual downloaded original files, NOT just picture candidates.
    private int originalOemTotal;
    private int originalsCompletedThisRound;
    private M02sPhotoListReader.Result previousFullList;
    private boolean closingSession;
    private int lastVisibleImageCount; // only a validated list's image count
    private volatile long runId;
    private long captureToWifiStartMs;
    private long apRequestAtMs;
    private long wifiReadyAtMs;
    private int listTries;
    private int downloadedCount;
    private int photoAttempts; // session-local attempt number, including failed attempts
    private byte[] lastJpegInRam;
    private String lastDownloadedRemoteName; // memory only; never log or persist
    private M02sFreshPhotoPolicy.Selection previousList;
    private Runnable transferDeadline;
    private boolean waitingPhotoDone; // 0x45 photo activity high, waiting for low
    private boolean photoReadyForManualImport; // local UI marker, not a real file listing
    private long lastPhotoFinishedAtMs; // 0 if only user-confirmed / no 0x45 falling edge
    private int observedPhotoFinishes;
    private String transferStage = "空闲";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wifi = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        starter = new M02sApStarter(this, ui, cm, wifi, new M02sApStarter.Callback() {
            @Override public void log(String message) {
                MainActivity.this.log(message);
                if (!inTransfer) return;
                if (message.contains("AA13唯一一次0x39/0x30写入成功")) {
                    transferStage = "蓝牙开AP命令已写入，最多等待5秒的SSID回执";
                    status(transferStage);
                } else if (message.contains("AA14 0x25真实SSID校验成功")
                        || message.contains("快路径：")) {
                    transferStage = "眼镜SSID已确认，正在等待Android Wi-Fi连接";
                    status(transferStage);
                }
            }
            @Override public void standbyReady() {
                if (destroyed) return;
                if (!inTransfer) {
                    if (photoReadyForManualImport) {
                        status("已记录待导入照片候选；请手动点击「导入高清原图」");
                    } else {
                        status("BLE已待机：先按实体快门拍照，随后手动导入");
                    }
                    updateImportButton();
                }
            }
            @Override public void photoActionCandidate() {
                if (destroyed || inTransfer || closingSession || waitingPhotoDone || !starter.standby()) return;
                waitingPhotoDone = true;
                photoReadyForManualImport = false;
                lastShutterStartedAtMs = SystemClock.elapsedRealtime();
                lastPhotoFinishedAtMs = 0L;
                updateImportButton();
                log("收到AA14 0x45拍照活动0→1；仅监听后续1→0；"
                        + "不会自动开AP；若先前有待导入标记，本轮以最新照片候选为准");
                status("眼镜正在拍照；等待结束通知。未收到结束通知时可手动确认拍完");
            }
            @Override public void photoActionEnded() {
                if (destroyed || !waitingPhotoDone || inTransfer || closingSession) return;
                waitingPhotoDone = false;
                photoReadyForManualImport = true;
                lastPhotoFinishedAtMs = SystemClock.elapsedRealtime();
                observedPhotoFinishes++;
                log("收到AA14 0x45拍照活动1→0；第" + observedPhotoFinishes
                        + "次拍照结束候选已标记待导入；没有发送BLE开AP命令，"
                        + "也尚未证实眼镜上文件已写盘");
                status(fastAutoImportEnabled
                        ? "拍照结束候选：极速模式尝试一次原厂AP取图，真实文件仍须HTTP验真"
                        : "检测到拍照结束；请手动点「导入高清原图」开始连接眼镜");
                updateImportButton();
                if (fastAutoImportEnabled && starter.standby()) {
                    // Trigger strictly AFTER the complete 0x45 1→0 shutter event,
                    // not 250ms after shutter start. No timer or automatic retry.
                    log("极速自动导入已由用户启用：仅在实体快门结束候选后触发一次原厂开AP；"
                            + "照片是否写盘仍由两次稳定HTTP列表检查确认");
                    ui.post(() -> {
                        if (!destroyed && fastAutoImportEnabled
                                && photoReadyForManualImport && starter.standby()
                                && !inTransfer && !closingSession)
                            startPhotoTransfer(true);
                    });
                }
            }
            @Override public void deviceProjectReported(String project) {
                if (destroyed) return;
                String normalized = project.toUpperCase(java.util.Locale.ROOT);
                String hint;
                if (normalized.contains("SK")) {
                    hint="CyanBridge参考：SK系列通常为AP；不代表本机传输协议已验证";
                } else if (normalized.contains("T")) {
                    hint="CyanBridge参考：T系列通常为AP；本机实际连接方式不改变";
                } else {
                    hint="CyanBridge对其他项目号有P2P默认分支，"
                            + "但这绝不证明当前M02S支持P2P";
                }
                log("只读设备项目标识=" + normalized + "；" + hint);
                if (projectText != null)
                    projectText.setText("设备只读项目标识：" + normalized + "。 " + hint);
            }
            @Override public void ready(Network net, String host) {
                onWifiReady(net, host);
            }
            @Override public void failed(String message) {
                log("BLE或眼镜Wi-Fi阶段失败：" + message);
                if (closingSession) {
                    closingSession=false;
                    starter.releasePhotoNetwork();
                    updateImportButton();
                    status("结束会话期间BLE/Wi-Fi故障，眼镜是否复位未知，请勿连续重试");
                } else if (inTransfer) abortTransfer("取图失败：" + message);
                else status("蓝牙准备失败：" + message);
            }
            @Override public void lost(String message) {
                log(message);
                if (closingSession) {
                    closingSession=false;
                    starter.releasePhotoNetwork();
                    updateImportButton();
                    status("结束会话时眼镜网络断开，已保留BLE；是否可进行下一轮仍待真机验证");
                } else if (inTransfer) abortTransfer("眼镜网络断开");
                else status("眼镜Wi-Fi已断开：" + message);
            }
            @Override public void recoverableImportFailure(String message) {
                if (destroyed || !inTransfer) return;
                // A failed Wi-Fi request did not download the current photo.
                // Restore the SAME capture candidate for a new manual import.
                inTransfer=false;
                runId++;
                if (transferDeadline!=null) ui.removeCallbacks(transferDeadline);
                transferDeadline=null;
                network=null;
                gateway=null;
                listBusy=false;
                closingSession=false;
                waitingPhotoDone=false;
                photoReadyForManualImport=true;
                lastPhotoFinishedAtMs=0L;
                transferStage="Wi-Fi失败已按原厂ZERO收尾，原照片仍待导入";
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                log("v0.1.29恢复完成："+message
                        +"；保留原拍照候选，不用重新拍照；只需再次手动点导入");
                status("本轮Wi-Fi未连上；已保留照片待导入和BLE，"
                        +"眼镜退出导入模式后可再次手动点导入");
                updateImportButton();
            }
        });
        makeUi();
        log("v0.1.30 极速首帧试验：基于v0.1.29已验证的原厂循环和Wi-Fi失败ZERO恢复；"
                + "默认仍为手动导入，用户可自行打开极速自动取图开关");
        log("保留0x45拍照监听、0x39/0x30开AP、0x25真实SSID、vivo Pattern+BSSID与SSID-only恢复；"
                + "不保持Wi-Fi常连；新改动仅优先取最新高清原图，再继续处理旧照片与原厂FULL结束");
        log("极速自动导入默认关闭，开启后只在实体快门0x45 1→0结束候选触发一次；"
                + "不使用原来的250ms定时开AP，不跳过原厂HTTP稳定列表检查，不无限重试");
        log("继续保留0x45 isImport被动观察、手动0x64项目号查询、"
                + "旧Wi-Fi缓存年龄与分段耗时日志，帮助区分第1～5轮导入");
        log("保留v0.1.28已真机验证的原厂HTTP全量下载、FULL 0x44结束与网络断开；"
                + "新增首次BSSID锁定请求onUnavailable时准确SSID-only最多一次补救；"
                + "仍失败则用原厂ZERO 0x44/0x30/0x01退出并保留BLE与本次待导入照片");
        status("请先准备BLE待机，再按眼镜实体快门拍照，最后手动点导入");
    }

    private void makeUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(16),dp(18),dp(24));
        scroll.addView(body);
        setContentView(scroll);

        addText(body,"蓝星 M02S 原厂循环导入",24,0xff17385b,true);
        addText(body,"v0.1.30 · 最新高清先就绪 + 原厂FULL安全收尾",15,Color.DKGRAY,false);
        statusText = addText(body,"",18,0xff117458,true);

        addText(body,"① 先准备蓝牙待机（这一步不打开Wi-Fi）",17,Color.BLACK,true);
        addButton(body,"准备蓝牙待机",this::prepareStandby);
        addText(body,"可选：只读查询眼镜项目号，不改连接模式，不包含个人数据。",
                14,Color.DKGRAY,false);
        addButton(body,"只读查询设备项目号（不打开Wi-Fi）",this::requestDeviceProject);
        projectText=addText(body,"设备项目号：未查询；只有你主动点击才发0x64只读查询",
                14,0xff17385b,false);
        addText(body,"② 按眼镜实体快门拍照；只记录待导入候选",
                17,Color.BLACK,true);
        addText(body,"默认只标记待导入；可选极速自动导入在实体快门结束候选后一次开启AP。"
                + "这不是已写盘证明，仍必须通过原厂HTTP列表两次稳定验证。",
                14,Color.DKGRAY,false);
        CheckBox fastMode = new CheckBox(this);
        fastMode.setText("极速自动导入实验（默认关闭，需主动勾选）");
        fastMode.setTextSize(16);
        fastMode.setChecked(false);
        fastMode.setOnCheckedChangeListener((v,enabled) -> {
            fastAutoImportEnabled=enabled;
            log(enabled ? "用户开启极速自动导入：实体快门结束通知后立刻请求一次原厂AP"
                    : "用户关闭极速自动导入：继续使用原有手动导入");
            status(enabled
                    ? "极速实验已开启：按实体快门后自动取最新高清；网络/硬件限速仍可能超过5秒"
                    : "已恢复原有手动导入；0x45只标记候选");
        });
        body.addView(fastMode,new LinearLayout.LayoutParams(-1,-2));
        addButton(body,"已拍照但没收到结束通知？手动标记待导入",
                this::confirmPhotoFinishedManually);
        addText(body,"③ 确认照片已经拍好后，再手动导入原图",
                17,Color.BLACK,true);
        manualImportButton=addButton(body,"导入高清原图（手动连接眼镜）",
                this::startPhotoTransfer);
        manualImportButton.setEnabled(false);
        addButton(body,"取消当前任务／清除待导入标记（不删除照片）",
                this::cancelCurrentTransfer);

        addText(body,"④ 原厂式传输结束（已启用）",17,Color.BLACK,true);
        addText(body,"你已明确允许原厂删除镜端照片。"
                + "列表全为图片且不超过24张时按原顺序下载全部原图并发送0x44/0x30/0x00；"
                + "如果不符合整批条件，只取最新照片并使用原厂部分下载结束指令。"
                + "原厂结束指令可能清空眼镜中的图片，不可恢复。",14,Color.DKGRAY,false);
        addText(body,"⑤ 最新高清原图优先到RAM并显示；旧图随后传输以维持原厂FULL收尾",
                17,Color.BLACK,true);
        resultText = addText(body,"本轮已完成0张；等待实体拍照",16,0xff17385b,true);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setMinimumHeight(dp(150));
        preview.setBackgroundColor(0xffeef2f5);
        body.addView(preview,new LinearLayout.LayoutParams(-1,-2));

        addButton(body,"停止并断开手机连接",this::disconnectAll);
        addButton(body,"清除内存图片",() -> {
            lastJpegInRam=null;
            preview.setImageDrawable(null);
            log("手机内存预览已清空；未操作眼镜媒体");
        });

        addText(body,"匿名诊断（不包含SSID、密码、MAC、文件名或照片）",
                17,Color.BLACK,true);
        addButton(body,"复制诊断日志",() -> {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("M02S v0.1.30 diagnostic",
                    diagnostics.toString()));
            status("诊断日志已复制");
        });
        logView = addText(body,"",13,0xff26343e,false);
        logView.setGravity(Gravity.START);

        addText(body,"实验目的：按原厂“先拍照→再点导入”的顺序，"
                + "连续测试最多5次手动导入。成功须以手机连接真实AP、原厂HTTP200和原始JPEG解码为准。"
                + "文件时间排序不是本次快门身份认证，已导入过的同名原图不会重复下载。",
                13,Color.DKGRAY,false);
    }

    private TextView addText(LinearLayout parent,String value,int sp,int color,boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null,1);
        t.setPadding(0,dp(7),0,dp(7));
        parent.addView(t,new LinearLayout.LayoutParams(-1,-2));
        return t;
    }
    private Button addButton(LinearLayout parent,String label,Runnable click) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(16);
        parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v -> click.run());
        return b;
    }

    private void updateImportButton() {
        if (manualImportButton != null)
            manualImportButton.setEnabled(!destroyed && starter != null
                    && starter.standby() && photoReadyForManualImport
                    && !waitingPhotoDone && !inTransfer && !closingSession);
    }
    private int dp(int unit) {
        return (int)(getResources().getDisplayMetrics().density*unit+0.5f);
    }
    private void status(String message) {
        if (statusText!=null) statusText.setText(message);
    }
    private void log(String message) {
        if (destroyed) return;
        String row=android.text.format.DateFormat.format("HH:mm:ss",
                System.currentTimeMillis())+" "+message+"\n";
        diagnostics.append(row);
        if (diagnostics.length()>22000)
            diagnostics.delete(0,Math.min(5000,diagnostics.length()-19000));
        if (logView!=null) logView.setText(diagnostics.toString());
    }

    private boolean ensurePermissions() {
        java.util.ArrayList<String> missing=new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT>=31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    !=PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    !=PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                !=PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (missing.isEmpty()) return true;
        requestPermissions(missing.toArray(new String[0]),PERMISSION_REQUEST);
        status("请授予蓝牙和Wi-Fi附近设备权限，再点准备蓝牙待机");
        return false;
    }

    private void prepareStandby() {
        if (!ensurePermissions()) return;
        if (starter.running()) {
            status(starter.standby()
                    ? (photoReadyForManualImport
                        ? "BLE已待机且有待导入标记；无需重连，直接手动点击导入"
                        : "BLE已待机；按实体快门拍照后再点击导入")
                    : "正在连接M02S，勿重复操作");
            updateImportButton();
            return;
        }
        starter.start();
        status("正在连接M02S BLE并订阅拍照状态；不启动眼镜Wi-Fi");
    }

    private void requestDeviceProject() {
        if (!starter.standby() || inTransfer || waitingPhotoDone) {
            status("先准备蓝牙待机，且只能在没有拍照/取图任务时查询项目号");
            return;
        }
        if (starter.requestReadOnlyProject()) {
            status("已提交一次只读设备项目查询，最多等待3秒回应；不触碰Wi-Fi");
        } else {
            status("只读查询未提交：等待上一项BLE操作完成，不自动重发");
        }
    }

    /** User confirmation is a UI candidate only; does NOT claim OEM file exists. */
    private void confirmPhotoFinishedManually() {
        if (destroyed || inTransfer || closingSession || !starter.standby()) {
            status("手动标记被拒绝：先完成BLE待机且不能正在导入");
            return;
        }
        if (photoReadyForManualImport && !waitingPhotoDone) {
            status("已有待导入候选；不重复标记，直接点「导入高清原图」");
            return;
        }
        waitingPhotoDone = false;
        photoReadyForManualImport = true;
        lastPhotoFinishedAtMs = 0L;
        log("用户确认已实体拍照：仅标记待导入候选，"
                + "未收到完整拍照结束通知不等于已经写盘；没有发送AP命令");
        status("已手动标记待导入候选；请点击「导入高清原图」");
        updateImportButton();
    }

    private void clearPendingPhotoAction() {
        // Kept as a state-only cleanup: there is NO scheduled auto-import runnable.
        waitingPhotoDone = false;
    }

    private void cancelCurrentTransfer() {
        if (closingSession) {
            status("部分导入结束正在等待GATT回执；请勿触发下一轮");
            return;
        }
        if (!inTransfer && (waitingPhotoDone || photoReadyForManualImport)) {
            clearPendingPhotoAction();
            photoReadyForManualImport = false;
            lastPhotoFinishedAtMs = 0L;
            log("用户清除本地待导入状态；没有连接Wi-Fi，眼镜文件未触碰");
            status("待导入标记已清空；眼镜照片没有删除，可以重新按实体快门");
            updateImportButton();
            return;
        }
        if (inTransfer) {
            abortTransfer("用户取消本轮Wi-Fi取图；不重发命令");
            // Discard late 0x25 from the cancelled AP request; one fresh BLE session
            // is safer than accidentally accepting a previous session's SSID.
            starter.stop();
            log("已停止BLE会话，丢弃本轮可能迟到的SSID；下次需点准备蓝牙待机");
            status("已取消并断开本轮会话；下一次点「准备蓝牙待机」");
            return;
        }
        status("目前没有进行中的导入任务或待导入标记；BLE待机不受影响");
    }

    /** Existing user-button path, kept intact. */
    private void startPhotoTransfer() { startPhotoTransfer(false); }

    /** Fast opt-in path can ONLY follow a confirmed 0x45 shutter 1→0 edge.
     * Both paths use the same OEM AP, HTTP and completion handling.
     */
    private void startPhotoTransfer(boolean automaticFromShutter) {
        if (automaticFromShutter && !fastAutoImportEnabled) return;
        if (destroyed || closingSession) {
            status("上一轮正在结束传输会话，不能重新开启Wi-Fi");
            return;
        }
        if (inTransfer) {
            log("导入按钮重复操作被忽略：已有任务在运行，阶段=" + transferStage);
            status("已有导入任务进行中：" + transferStage + "，不重复开AP");
            return;
        }
        if (waitingPhotoDone || !photoReadyForManualImport) {
            status("还没有完成的拍照候选；先按实体快门，结束后再手动点导入");
            return;
        }
        if (!starter.standby()) {
            status("请先准备BLE待机；未连接眼镜，不发送Wi-Fi请求");
            updateImportButton();
            return;
        }
        final long id=++runId;
        photoAttempts++;
        long clickedAt=SystemClock.elapsedRealtime();
        log((automaticFromShutter
                    ? "用户启用的极速自动取图：" : "用户亲自点击「导入高清原图」：")
                + "第"+photoAttempts+"次导入；"
                + (lastPhotoFinishedAtMs > 0
                    ? "拍照结束候选→启动耗时="+(clickedAt-lastPhotoFinishedAtMs)+"ms"
                    : "未得到可靠的拍照结束时间，启动前等待时长未知"));
        captureToWifiStartMs=clickedAt;
        shutterStartedForTransferMs=lastShutterStartedAtMs;
        shutterFinishedForTransferMs=lastPhotoFinishedAtMs;
        latestOriginalReadyAtMs=0L;
        apRequestAtMs=captureToWifiStartMs;
        wifiReadyAtMs=0;
        listTries=0;
        previousList=null;
        previousFullList=null;
        lastVisibleImageCount=0;
        originalOemTotal=0;
        originalsCompletedThisRound=0;
        inTransfer=true;
        listBusy=false;
        network=null;
        gateway=null;
        transferStage="准备发送原厂开AP命令，随后最多5秒等待SSID";
        status(transferStage);
        log(automaticFromShutter
                ? "本次AP/HTTP启动来自用户开启的极速模式，严格由实体快门0x45 1→0触发一次"
                : "本次AP/HTTP启动仅来自用户按钮；没有自动开启AP");

        if (!starter.requestPhotoTransfer()) {
            inTransfer=false;
            transferStage="未能提交开AP命令";
            status("无法发送唯一一次开AP指令；保留本地待导入标记，检查BLE状态");
            updateImportButton();
            return;
        }
        photoReadyForManualImport = false;
        lastPhotoFinishedAtMs = 0L;
        updateImportButton();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        transferDeadline=() -> {
            if (inTransfer && id==runId)
                abortTransfer("本轮300秒完整导入安全上限已到，不自动重复发送蓝牙/Wi-Fi指令");
        };
        ui.postDelayed(transferDeadline,ONE_SHOT_MAX_MS);
    }

    private void onWifiReady(Network net,String host) {
        if (!inTransfer || destroyed) return;
        wifiReadyAtMs=SystemClock.elapsedRealtime();
        network=net;
        gateway=host;
        transferStage="Wi-Fi就绪，正在验证原厂照片列表";
        log("手机收到真实眼镜AP Network和私有网关；手动导入点击到Wi-Fi就绪="
                +(wifiReadyAtMs-captureToWifiStartMs)+"ms，开始原厂只读HTTP");
        status(transferStage);
        readLatestList(runId);
    }

    private static M02sPhotoListReader.Result readList(Network net,String host) throws Exception {
        M02sPhotoTransport.Response res=M02sPhotoTransport.getList(net,host);
        if (res.httpCode!=200) throw new java.io.IOException("OEM照片列表HTTP非200");
        JSONObject root=new JSONObject(new String(res.body,StandardCharsets.UTF_8));
        if (root.optInt("result",-9)!=0 || !root.has("info"))
            throw new java.io.IOException("OEM照片列表JSON未经验证");
        return M02sPhotoListReader.parse(res.body);
    }

    private void readLatestList(long id) {
        if (destroyed || !inTransfer || id!=runId || listBusy || network==null)
            return;
        if (!starter.isActive(network,gateway)) {
            abortTransfer("眼镜专属Wi-Fi连接已丢失，不能尝试其他网络");
            return;
        }
        if (listTries>=LIST_MAX_TRIES) {
            abortTransfer("照片列表未出现唯一且连续稳定的最新候选；不盲目下载旧照片");
            return;
        }
        listBusy=true;
        listTries++;
        final Network net=network;
        final String host=gateway;
        io.execute(() -> {
            try {
                M02sPhotoListReader.Result list=readList(net,host);
                ui.post(() -> {
                    if (destroyed || !inTransfer || id!=runId) return;
                    listBusy=false;
                    M02sFreshPhotoPolicy.Selection candidate=
                            M02sFreshPhotoPolicy.selectLatestUnverified(list.candidates);
                    if (candidate.status==M02sFreshPhotoPolicy.Status.READY
                            && candidate.photo != null
                            && candidate.photo.remoteName.equals(lastDownloadedRemoteName)) {
                        log("列表最新候选与本App刚下载的同一张一致，拒绝重复取旧图");
                        abortTransfer("没有检测到比上次成功取图更新的照片");
                        return;
                    }
                    if (M02sFreshPhotoPolicy.stablePair(previousList,candidate)
                            && M02sPhotoListReader.sameSnapshot(previousFullList,list)) {
                        originalOemTotal=list.entriesObserved;
                        lastVisibleImageCount=list.eligibleImages;
                        log("原厂HTTP完整列表连续两次稳定：原始条目=" + originalOemTotal
                                + "；可安全GET图片=" + list.eligibleImages
                                + "；将按照原厂成功计数选择结束指令");
                        downloadOemSession(id,net,host,list,candidate.photo);
                        return;
                    }
                    previousList=candidate;
                    previousFullList=list;
                    if (listTries==1 || listTries%3==0)
                        log("照片列表#"+listTries+"：总图片数="+list.eligibleImages
                                +"；最新候选状态="+candidate.status+"；350ms后复核稳定性");
                    ui.postDelayed(() -> readLatestList(id),LIST_RECHECK_MS);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (destroyed || !inTransfer || id!=runId) return;
                    listBusy=false;
                    log("OEM列表请求失败，异常类型="+e.getClass().getSimpleName());
                    abortTransfer("原厂HTTP照片列表不可用；不重复BLE或猜测IP");
                });
            }
        });
    }

    /** Follow the OEM original-list completion semantics. Full-image lists
     * are fetched in original server order, not reverse-sorted; one newer
     * image is previewed, and earlier files are discarded from phone RAM.
     * In a mixed list, fetch only the newest and report partial success N=1.
     */
    private void downloadOemSession(long id,Network net,String host,
                                    M02sPhotoListReader.Result list,
                                    M02sPhotoPolicy.Candidate latest) {
        if (!inTransfer || id!=runId || latest==null || list==null
                || list.entriesObserved<=0) return;
        final boolean fullBatch = M02sOemImportPolicy.canCompleteWholeList(
                list.entriesObserved,list.eligibleImages);
        final java.util.List<M02sPhotoPolicy.Candidate> files = fullBatch
                ? list.candidates
                : java.util.Collections.singletonList(latest);
        transferStage = fullBatch ? "正在按原厂列表顺序下载全部原图" : "正在下载单张原图";
        status(transferStage);
        log(fullBatch
                ? "原厂整批模式：逐张只读GET"+files.size()+"张并验证，全部成功才发完整结束/清理帧"
                : "原厂部分模式：混合文件/数量超限，仅取最新一张并按完成数发送部分结束帧");
        io.execute(() -> {
            int completed=0;
            Bitmap newestBitmap=null;
            byte[] newestBytes=null;
            long totalHttpMs=0;
            try {
                for (M02sPhotoPolicy.Candidate item : files) {
                    if (destroyed || !inTransfer || runId!=id) {
                        if(newestBitmap!=null) newestBitmap.recycle();
                        return; // user cancellation; MainActivity handles network release
                    }
                    M02sPhotoTransport.Response response =
                            M02sPhotoTransport.getOriginalPhoto(net,host,item);
                    if(response.httpCode!=200)
                        throw new java.io.IOException("OEM original HTTP code not 200");
                    M02sPhotoPolicy.ImageKind kind=M02sPhotoPolicy.imageKind(response.body);
                    if (!M02sPhotoPolicy.extensionMatches(item.remoteName,kind))
                        throw new java.io.IOException("OEM original kind/extension mismatch");
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(response.body,0,response.body.length,bounds);
                    if (bounds.outWidth<1 || bounds.outHeight<1
                            || bounds.outWidth>10000 || bounds.outHeight>10000
                            || (long)bounds.outWidth*bounds.outHeight>32_000_000L)
                        throw new java.io.IOException("OEM original image bounds invalid");
                    if(item.remoteName.equals(latest.remoteName)) {
                        newestBytes=response.body;
                        newestBitmap=BitmapFactory.decodeByteArray(
                                response.body,0,response.body.length);
                        if(newestBitmap==null) throw new java.io.IOException("latest image decode failed");
                    }
                    completed++;
                    totalHttpMs+=response.durationMs;
                    final int count=completed;
                    ui.post(() -> {
                        if (!destroyed && inTransfer && id==runId) {
                            originalsCompletedThisRound=count;
                            resultText.setText("已导入原图 "+count+"/"+originalOemTotal);
                        }
                    });
                }
                final int count=completed;
                final long httpMs=totalHttpMs;
                final Bitmap display=newestBitmap;
                final byte[] raw=newestBytes;
                ui.post(() -> {
                    if (destroyed || !inTransfer || runId!=id) {
                        if(display!=null) display.recycle();
                        return;
                    }
                    originalsCompletedThisRound=count;
                    if (!starter.isActive(net,host)) {
                        if(display!=null) display.recycle();
                        abortTransfer("下载结束时眼镜专属Wi-Fi已断开，保留真实完成数");
                        return;
                    }
                    if (display==null || raw==null) {
                        abortTransfer("原厂原图批次内未得到本次最新图片，不能误报取图成功");
                        return;
                    }
                    lastJpegInRam=raw;
                    lastDownloadedRemoteName=latest.remoteName;
                    preview.setImageBitmap(display);
                    downloadedCount++;
                    resultText.setText("成功导入"+count+"张；本次最新原图"
                            +display.getWidth()+"×"+display.getHeight());
                    log("原厂批次原图已逐张下载：真实完成数="+count
                            +"/"+originalOemTotal+"；最新原图="+display.getWidth()
                            +"×"+display.getHeight()+"；整批HTTP="+httpMs+"ms"
                            +"；手动点击到导入完成="+(SystemClock.elapsedRealtime()-captureToWifiStartMs)+"ms");
                    finishTransfer(true,"原厂原图下载已完成，正在发送传输结束指令");
                });
            } catch (Exception e) {
                final int count=completed;
                final Bitmap display=newestBitmap;
                ui.post(() -> {
                    if (display!=null) display.recycle();
                    if (!destroyed && inTransfer && id==runId) {
                        originalsCompletedThisRound=count;
                        log("原厂原图在第"+(count+1)+"张失败，类型="
                                +e.getClass().getSimpleName()
                                +"；将以真实成功数="+count+"执行原厂部分/零下载结束");
                        abortTransfer("原厂整批下载未全部完成，仍执行原厂对应结束分支");
                    }
                });
            }
        });
    }

    private void abortTransfer(String reason) {
        if (!inTransfer) {
            status(reason);
            return;
        }
        log("本轮取图停止："+reason);
        finishTransfer(false,reason);
    }

    private void finishTransfer(boolean success,String reason) {
        inTransfer=false;
        transferStage=success?"下载完成，正在按原厂协议退出":"本轮下载中断";
        runId++; // invalidate old HTTP and delayed callbacks
        if (transferDeadline!=null) ui.removeCallbacks(transferDeadline);
        transferDeadline=null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        network=null;
        gateway=null;
        listBusy=false;

        final int originalCount=originalOemTotal;
        final int done=originalsCompletedThisRound;
        // The OEM 0x44 sequence is only valid after receiving a verified file
        // inventory and an AP-connected session. Never guess missing counts.
        if (originalCount>0 && starter.ready() && starter.standby()) {
            closingSession=true;
            updateImportButton();
            final M02sOemImportPolicy.Branch branch;
            try {
                branch=M02sOemImportPolicy.branch(originalCount,done);
            } catch (IllegalArgumentException ex) {
                closingSession=false;
                log("原厂完成计数非法，不发送任何未知0x44帧");
                starter.releasePhotoNetwork();
                updateImportButton();
                status("完成计数异常，手机请求已释放，镜端状态未知");
                return;
            }
            log("原厂退出分支="+branch+"，本轮原始条目="+originalCount
                    +"，真实完成原图="+done+"；完整成功可能清空眼镜照片（已获用户授权）");
            status("原厂"+branch+"结束指令提交中；完成之前禁止下一轮");
            final boolean requested=starter.requestOemFinish(originalCount,done,ok -> {
                if(destroyed || !closingSession) return;
                log(ok ? "原厂0x44指令GATT写回执成功；准备释放手机Wi-Fi"
                        : "原厂0x44指令未获得成功写回执；仍释放手机Wi-Fi，不自动重发");
                closingSession=false;
                starter.releasePhotoNetwork();
                updateImportButton();
                status(ok ? "已发送原厂传输结束帧；正断开Wi-Fi，断开后可进行下一轮拍照"
                        : "原厂结束帧未确认，眼镜可能仍在导入状态");
            });
            if (requested) return;
            closingSession=false;
            log("原厂0x44结束帧未能提交；手机Wi-Fi仍需释放");
        } else {
            log("尚无完整原厂列表或BLE/AP连接已丢失，不推断结束指令参数");
        }
        starter.releasePhotoNetwork();
        updateImportButton();
        status(reason+"；已释放手机网络，镜端状态需在下一轮验证");
    }

    private void disconnectAll() {
        closingSession=false;
        clearPendingPhotoAction();
        photoReadyForManualImport=false;
        lastPhotoFinishedAtMs=0L;
        if(inTransfer) {
            inTransfer=false;
            log("用户主动停止导入，取消未完成的原图下载；不再发送原厂0x44结束帧");
        }
        if(transferDeadline!=null) ui.removeCallbacks(transferDeadline);
        transferDeadline=null;
        runId++;
        starter.stop();
        updateImportButton();
        network=null;gateway=null;listBusy=false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status("BLE和本次Android Wi-Fi请求已停止；下次需重新准备BLE");
        log("用户手动断开；不重复BLE、Wi-Fi或删除指令");
    }

    @Override protected void onDestroy() {
        closingSession=false;
        clearPendingPhotoAction();
        photoReadyForManualImport = false;
        lastPhotoFinishedAtMs = 0L;
        destroyed=true;
        inTransfer=false;
        runId++;
        if(transferDeadline!=null) ui.removeCallbacks(transferDeadline);
        transferDeadline=null;
        starter.stop();
        io.shutdownNow();
        lastJpegInRam=null;
        super.onDestroy();
    }
}
