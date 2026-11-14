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
 * M02S fastest verified high-quality photo transport experiment.
 * BLE stands by for unverified ACTION_SYNC 0x45 physical photo candidate;
 * a manual fallback button works even when the firmware sends no notification.
 * BLE only issues the OEM 0x39/0x30 AP command after a physical shot.
 * No BLE remote shutter command, Wi-Fi scanning delay, continuous polling,
 * file deletion, cloud upload, album write, or OTA.
 */
public final class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 1601;
    // E09 isTakePhoto=1 is "busy" rather than proof that JPEG has been saved.
    // No auto-AP timer: only observed end of shutter activity or explicit user action.
    private static final long PHOTO_DONE_SETTLE_MS = 250L;
    private static final long ONE_SHOT_MAX_MS = 39_000L;
    private static final long LIST_RECHECK_MS = 350L;
    private static final int LIST_MAX_TRIES = 12;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final StringBuilder diagnostics = new StringBuilder();
    private M02sApStarter starter;
    private TextView statusText;
    private TextView logView;
    private TextView resultText;
    private ImageView preview;
    private Network network;
    private String gateway;
    private boolean destroyed;
    private boolean inTransfer;
    private boolean listBusy;
    private boolean nonClearExperimentEnabled; // always OFF at app startup
    private boolean closingSession;
    private long runId;
    private long captureToWifiStartMs;
    private long apRequestAtMs;
    private long wifiReadyAtMs;
    private int listTries;
    private int downloadedCount;
    private byte[] lastJpegInRam;
    private String lastDownloadedRemoteName; // memory only; never log or persist
    private M02sFreshPhotoPolicy.Selection previousList;
    private Runnable transferDeadline;
    private Runnable pendingPhotoAction;
    private boolean waitingPhotoDone;
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
                if (!inTransfer) status("BLE已待机：请直接按眼镜实体快门；若没有自动反应，点取图按钮");
            }
            @Override public void photoActionCandidate() {
                if (destroyed || inTransfer || closingSession || waitingPhotoDone || !starter.standby()) return;
                waitingPhotoDone = true;
                log("收到AA14 0x45拍照活动0→1；只等待1→0结束信号；"
                        + "没有倒计时自动开热点");
                status("等待0x45状态1→0；没结束信号就保持BLE待机。"
                        + "若确认已拍完，可点「我已经拍照」手动取图");
            }
            @Override public void photoActionEnded() {
                if (destroyed || !waitingPhotoDone || inTransfer) return;
                if (pendingPhotoAction != null) ui.removeCallbacks(pendingPhotoAction);
                log("收到0x45拍照状态1→0；等待250ms后启动一次开AP（文件写盘仍需实测）");
                status("拍照结束状态已收到，250ms后打开眼镜Wi-Fi");
                pendingPhotoAction = () -> {
                    pendingPhotoAction = null;
                    if (destroyed || !waitingPhotoDone || inTransfer) return;
                    waitingPhotoDone = false;
                    startPhotoTransfer(true);
                };
                ui.postDelayed(pendingPhotoAction, PHOTO_DONE_SETTLE_MS);
            }
            @Override public void ready(Network net, String host) {
                onWifiReady(net, host);
            }
            @Override public void failed(String message) {
                log("BLE或眼镜Wi-Fi阶段失败：" + message);
                if (closingSession) {
                    closingSession=false;
                    status("实验结束过程中断，固件是否退出AP未知");
                } else if (inTransfer) abortTransfer("取图失败：" + message);
                else status("蓝牙准备失败：" + message);
            }
            @Override public void lost(String message) {
                log(message);
                if (closingSession) {
                    closingSession=false;
                    status("实验结束过程中连接断开，固件退出状态未知");
                } else if (inTransfer) abortTransfer("眼镜网络断开");
                else status("眼镜Wi-Fi已断开：" + message);
            }
        });
        makeUi();
        log("v0.1.20 E09不删除退出指令对照实验；保留已成功实体快门"
                + "0x45结束→250ms开AP→精确Wi-Fi→高清JPEG流程");
        log("GitHub E09逆向：0x44/0x30/0x01是不清除结束帧；"
                + "M02S未验证，默认关闭，必须主动勾选实验");
        log("实验完成后GATT写回执不等于眼镜已退出Wi-Fi；"
                + "绝不会发送删除参数或未知P2P指令");
        log("沿用v0.1.19的连接超时与回调诊断，不扫描12秒、不自动重试");
        log("照片只到手机RAM；不会删除眼镜文件，不上传云端，也不操作OCR/TTS");
        status("点击「准备蓝牙待机」，成功后直接用眼镜实体键拍照");
    }

    private void makeUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(16),dp(18),dp(24));
        scroll.addView(body);
        setContentView(scroll);

        addText(body,"蓝星 M02S 极速高清取图",24,0xff17385b,true);
        addText(body,"v0.1.20 · 可选不删除退出实验（默认关闭）",15,Color.DKGRAY,false);
        statusText = addText(body,"",18,0xff117458,true);

        addText(body,"① 先准备蓝牙待机（这一步不打开Wi-Fi）",17,Color.BLACK,true);
        addButton(body,"准备蓝牙待机",this::prepareStandby);
        addText(body,"② 用实体按键拍照，监听到结束状态后自动取图",
                16,Color.BLACK,true);
        addText(body,"如果没有结束通知就只等待、不自动开启Wi-Fi。"
                + "若已确认拍完，可点下面按钮立即取图；正在取图时只显示当前阶段，不重复发命令。",
                14,Color.DKGRAY,false);
        addButton(body,"我已经拍照，立即取高清原图",() -> startPhotoTransfer(false));
        addButton(body,"取消当前取图（不重试、不删除照片）",this::cancelCurrentTransfer);

        addText(body,"③ 实验：下载后让眼镜退出传输模式",17,Color.BLACK,true);
        addText(body,"E09开源称0x44/0x30/0x01为不删除结束帧；"
                + "M02S未验证。默认关闭；如需对比连续拍照，请主动开启。"
                + "绝不发送清除照片参数。",14,Color.DKGRAY,false);
        CheckBox nonClearCheckbox = new CheckBox(this);
        nonClearCheckbox.setText("启用实验：每次高清取图成功后发送一次不删除结束帧");
        nonClearCheckbox.setTextSize(15);
        nonClearCheckbox.setChecked(false);
        nonClearCheckbox.setOnCheckedChangeListener((button,checked) -> {
            nonClearExperimentEnabled=checked;
            log(checked ? "用户主动开启不删除结束帧实验：仅本次运行生效"
                    : "不删除结束帧实验已关闭，不会自动发送该指令");
        });
        body.addView(nonClearCheckbox,new LinearLayout.LayoutParams(-1,-2));
        addButton(body,"手动尝试退出眼镜传输模式（需先勾选；不删除）",
                this::manualNonClearFinish);

        addText(body,"④ 下载完成后只在内存显示原图",17,Color.BLACK,true);
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
            clipboard.setPrimaryClip(ClipData.newPlainText("M02S v0.1.20 diagnostic",
                    diagnostics.toString()));
            status("诊断日志已复制");
        });
        logView = addText(body,"",13,0xff26343e,false);
        logView.setGravity(Gravity.START);

        addText(body,"说明：初次取图选取可确定的最新照片，"
                + "不把文件时间推断当作快门确认；检测到与上次相同的照片不会重复下载。"
                + "本版只验证传输速度，不接云端OCR或朗读。",13,Color.DKGRAY,false);
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
    private void addButton(LinearLayout parent,String label,Runnable click) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(16);
        parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v -> click.run());
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
            status(starter.standby()?"BLE已待机，请用眼镜实体键拍照":
                    "正在连接M02S，勿重复操作");
            return;
        }
        starter.start();
        status("正在连接M02S BLE并订阅拍照状态；不启动眼镜Wi-Fi");
    }

    private void clearPendingPhotoAction() {
        if (pendingPhotoAction != null) {
            ui.removeCallbacks(pendingPhotoAction);
            pendingPhotoAction = null;
        }
        waitingPhotoDone = false;
    }

    private void cancelCurrentTransfer() {
        if (closingSession) {
            closingSession=false;
            starter.stop();
            status("已取消未确认的退出实验，蓝牙及Wi-Fi请求均已释放；不删除照片");
            log("用户终止未确认的结束帧实验；不再发送BLE指令");
            return;
        }
        if (waitingPhotoDone) {
            clearPendingPhotoAction();
            log("用户取消等待拍照结束：未发送BLE开AP命令，保留蓝牙待机");
            status("已取消本次自动取图；BLE仍在待机，眼镜照片未触碰");
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
        status("目前没有进行中的取图或等待任务；BLE待机不受影响");
    }

    private void startPhotoTransfer(boolean fromActionSync) {
        if (destroyed) return;
        if (closingSession) {
            status("上一张正在完成实验退出；不能重复发新开AP指令");
            return;
        }
        if (inTransfer) {
            log("重复点击取原图：本轮任务仍在运行，未重复发送开AP；阶段=" + transferStage);
            status("已有任务进行中：" + transferStage + "。若不想继续，可点「取消当前取图」");
            return;
        }
        if (waitingPhotoDone) {
            log("用户确认实体照片已经拍好，跳过等待0x45结束状态，立即启动单次取图");
            clearPendingPhotoAction();
        }
        if (!starter.standby()) {
            status("请先准备蓝牙待机；未连接眼镜，不发送Wi-Fi请求");
            return;
        }
        final long id=++runId;
        captureToWifiStartMs=SystemClock.elapsedRealtime();
        apRequestAtMs=captureToWifiStartMs;
        wifiReadyAtMs=0;
        listTries=0;
        previousList=null;
        inTransfer=true;
        listBusy=false;
        network=null;
        gateway=null;
        transferStage="准备发送原厂开AP命令，随后最多5秒等待SSID";
        status(transferStage);
        log((fromActionSync?"候选0x45拍照状态自动触发":"用户确认已实体拍照手动触发")
                +"：取图开始（此时刻不一定等于真实快门时刻）");

        if (!starter.requestPhotoTransfer()) {
            inTransfer=false;
            transferStage="未能提交开AP命令";
            status("无法发送唯一一次开AP指令：检查BLE待机/旧传输状态");
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        transferDeadline=() -> {
            if (inTransfer && id==runId)
                abortTransfer("本轮39秒取图上限已到，不自动重复发送蓝牙/Wi-Fi指令");
        };
        ui.postDelayed(transferDeadline,ONE_SHOT_MAX_MS);
    }

    private void onWifiReady(Network net,String host) {
        if (!inTransfer || destroyed) return;
        wifiReadyAtMs=SystemClock.elapsedRealtime();
        network=net;
        gateway=host;
        transferStage="Wi-Fi就绪，正在验证原厂照片列表";
        log("手机收到真实眼镜AP Network和私有网关；触发到Wi-Fi就绪="
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
                    if (M02sFreshPhotoPolicy.stablePair(previousList,candidate)) {
                        log("照片列表#" +listTries+"：最新候选连续两次稳定，"
                                +"图片总数="+list.eligibleImages+"；开始直接GET原始图片");
                        downloadOriginal(id,net,host,candidate.photo);
                        return;
                    }
                    previousList=candidate;
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

    private void downloadOriginal(long id,Network net,String host,
                                  M02sPhotoPolicy.Candidate candidate) {
        if (!inTransfer || id!=runId || candidate==null) return;
        final long begin=SystemClock.elapsedRealtime();
        transferStage="正在下载原厂高清原图（仅内存）";
        status(transferStage);
        io.execute(() -> {
            try {
                M02sPhotoTransport.Response res=
                        M02sPhotoTransport.getOriginalPhoto(net,host,candidate);
                if(res.httpCode!=200) throw new java.io.IOException("original HTTP non200");
                M02sPhotoPolicy.ImageKind kind=M02sPhotoPolicy.imageKind(res.body);
                if (!M02sPhotoPolicy.extensionMatches(candidate.remoteName,kind))
                    throw new java.io.IOException("wrong original image signature");
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(res.body,0,res.body.length,bounds);
                if (bounds.outWidth<1 || bounds.outHeight<1 || bounds.outWidth>10000
                        || bounds.outHeight>10000
                        || (long)bounds.outWidth*bounds.outHeight>32_000_000L)
                    throw new java.io.IOException("invalid image bounds");
                Bitmap bitmap=BitmapFactory.decodeByteArray(res.body,0,res.body.length);
                if (bitmap==null) throw new java.io.IOException("Bitmap decode failed");
                long doneAt=SystemClock.elapsedRealtime();
                ui.post(() -> {
                    if (destroyed || !inTransfer || id!=runId) {
                        bitmap.recycle();return;
                    }
                    if (!starter.isActive(net,host)) {
                        bitmap.recycle();
                        abortTransfer("读取后检测到眼镜Network已掉线，不把本图误报为取图成功");
                        return;
                    }
                    lastJpegInRam=res.body;
                    lastDownloadedRemoteName=candidate.remoteName;
                    preview.setImageBitmap(bitmap);
                    downloadedCount++;
                    resultText.setText("成功下载"+downloadedCount+"张；最近原图"
                            +bitmap.getWidth()+"×"+bitmap.getHeight());
                    log("已下载最新高清候选：尺寸="+bitmap.getWidth()+"×"+bitmap.getHeight()
                            +"；字节="+res.body.length
                            +"；本次HTTP GET="+res.durationMs+"ms"
                            +"；GET到解码="+(doneAt-begin)+"ms"
                            +"；触发取图到解码="+(doneAt-captureToWifiStartMs)+"ms"
                            +"（不包含未知的真实快门触发延迟）");
                    log("最新候选只代表文件时间排序，不能单独证明它由本次实体快门生成");
                    finishTransfer(true,"高清原图已显示；已释放手机Wi-Fi，BLE保留待机");
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (!inTransfer || destroyed || id!=runId) return;
                    log("高清GET失败，异常类型="+e.getClass().getSimpleName());
                    abortTransfer("原图下载失败；没有云端上传、没有删除眼镜照片");
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
        transferStage=success?"已完成高清取图":"本轮已停止";
        runId++; // invalidate all pending callbacks and old IO completions
        if (transferDeadline!=null) ui.removeCallbacks(transferDeadline);
        transferDeadline=null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        network=null;
        gateway=null;
        listBusy=false;
        if (success && nonClearExperimentEnabled && starter.standby()) {
            closingSession=true;
            transferStage="实验：发送不删除结束帧，等待一次GATT回执";
            status("原图已下载成功，正在尝试E09不删除结束帧（M02S未验证）");
            log("用户已主动启用实验：成功下载原图后先发送0x44/0x30/0x01，"
                    + "再释放Android Wi-Fi请求；无清除参数");
            boolean submitted=starter.requestNonClearFinish(ok -> {
                if (destroyed || !closingSession) return;
                closingSession=false;
                log(ok ? "不删除结束帧GATT写成功，但眼镜退出传输模式尚待下一张验证"
                        : "不删除结束帧没有可靠GATT写回执，退出状态未知；不重发");
                starter.releasePhotoNetwork();
                status(reason+(ok
                        ? "；实验结束帧GATT已写入，眼镜是否退出AP仍待验证"
                        : "；结束帧未获可靠写入回执，已释放手机Wi-Fi"));
            });
            if (submitted) return;
            closingSession=false;
            log("不删除结束帧没有提交：BLE写接口未就绪；保持原版释放手机网络方式");
        }
        starter.releasePhotoNetwork();
        status(reason+(starter.standby()?"。BLE仍在待机":"。BLE已断开"));
        if (!success) log("失败后不自动重发开AP指令，需用户主动触发");
    }

    private void manualNonClearFinish() {
        if (destroyed || closingSession) {
            status("目前有未完成的结束实验，不重复发指令");
            return;
        }
        if (!nonClearExperimentEnabled) {
            status("实验默认关闭：请先主动勾选，才可以发送不删除结束帧");
            return;
        }
        if (inTransfer || waitingPhotoDone) {
            status("正在拍照/下载过程中，不允许手动发送退出指令");
            return;
        }
        if (!starter.standby()) {
            status("请先点「准备蓝牙待机」，蓝牙就绪后才能手动尝试退出");
            return;
        }
        closingSession=true;
        status("手动实验：正在提交一次不删除结束帧，等待3秒内GATT写回执");
        log("用户主动点击手动不删除退出；只发一次0x44/0x30/0x01");
        boolean submitted=starter.requestNonClearFinish(ok -> {
            if (!closingSession || destroyed) return;
            closingSession=false;
            starter.releasePhotoNetwork();
            log(ok ? "手动不删除结束帧GATT写成功；需实测眼镜下一次能否拍照和连接"
                    : "手动不删除结束帧GATT未确认；不重发");
            status(ok ? "手动结束帧已GATT写入，眼镜退出传输模式仍待验证"
                    : "手动结束帧未获可靠回执，固件是否退出未知");
        });
        if (!submitted) {
            closingSession=false;
            status("不删除结束帧未提交：眼镜AA13写接口未就绪");
        }
    }

    private void disconnectAll() {
        if (waitingPhotoDone) clearPendingPhotoAction();
        closingSession=false;
        if (inTransfer) abortTransfer("用户主动结束本轮取图");
        runId++;
        starter.stop();
        network=null;gateway=null;listBusy=false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status("BLE和本次手机Wi-Fi请求已释放；眼镜AP固件退出状态未验证");
        log("用户主动断开手机会话；不发送任何删除或未知结束命令");
    }

    @Override protected void onDestroy() {
        clearPendingPhotoAction();
        closingSession=false;
        if(inTransfer) finishTransfer(false,"应用退出");
        destroyed=true;
        starter.stop();
        io.shutdownNow();
        lastJpegInRam=null;
        super.onDestroy();
    }
}
