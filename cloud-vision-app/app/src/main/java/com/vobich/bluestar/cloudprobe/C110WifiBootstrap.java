package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstructed workflow from the user's SJCAM Zone 6.7.3.16 classes5.dex:
 * WifiBottomPopupV2 -> WifiHelper.connectWifi -> WifiHelper$1.run ->
 * (vivo) WifiHelper.setCurrentConnectWifiNetWork ->
 * WifiNetworkSpecifier.setSsid(EXACT), optional setBssid, WPA2,
 * NetworkRequest WIFI + NOT_RESTRICTED + TRUSTED - INTERNET ->
 * ConnectivityManager.requestNetwork -> NetworkCallback.onAvailable.
 *
 * Camera communication still uses our read-only 3012 identity check and
 * proven original 3012/3016/3001/2015 video path. The actual SSID/password
 * must be selected from Wi-Fi scanning or entered; never guess "C110" prefix.
 */
final class C110WifiBootstrap {
    static final int PERMISSION_REQUEST=4096;
    static final int SCAN_PERMISSION_REQUEST=4097;
    private static final String CAMERA_IP="192.168.1.254";
    private static final String WIFI_PREF="c110_selected_wifi_v1";
    private static final String KEY_SSID="ssid";
    private static final String KEY_PASSWORD="password";
    private static final String KEY_BSSID="bssid";
    private static WeakReference<C110WifiBootstrap> mainOwner=new WeakReference<>(null);
    private static volatile Network verifiedNetwork;

    private final Activity owner;
    private final ConnectivityManager cm;
    private final WifiManager wifiManager;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService verifier=Executors.newSingleThreadExecutor();
    private final Consumer<String> updates;
    private ConnectivityManager.NetworkCallback requestedCallback;
    private WeakReference<Activity> currentScreen=new WeakReference<>(null);
    private volatile boolean closed;
    private volatile int generation;
    private boolean attempted;
    private boolean chooserOpen;
    private volatile Network verifying;

    C110WifiBootstrap(Activity activity,Consumer<String> updates){
        owner=activity;
        this.updates=updates;
        cm=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        wifiManager=(WifiManager)activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(activity instanceof MainActivity)mainOwner=new WeakReference<>(this);
    }

    private SharedPreferences prefs(){
        return owner.getSharedPreferences(WIFI_PREF,Context.MODE_PRIVATE);
    }

    private Activity screen(){
        Activity a=currentScreen.get();
        return a!=null&&!a.isFinishing()&&!a.isDestroyed()?a:owner;
    }

    /**
     * The separate C110 VLC Activity reuses the MainActivity NetworkCallback,
     * so starting a second request cannot silently invalidate the first one.
     */
    static boolean requestFromCameraPage(Activity foreground,boolean force){
        C110WifiBootstrap target=mainOwner.get();
        if(target==null||target.closed)return false;
        target.currentScreen=new WeakReference<>(foreground);
        target.main.post(()->{
            if(force)target.retryForCameraMode();
            else target.requestForCameraMode();
        });
        return true;
    }

    static void onScanPermissionFromCameraPage(Activity foreground,boolean granted){
        C110WifiBootstrap target=mainOwner.get();
        if(target!=null&&!target.closed)target.onScanPermissionResult(foreground,granted);
    }

    static void onWifiPermissionFromCameraPage(Activity foreground,boolean granted){
        C110WifiBootstrap target=mainOwner.get();
        if(target!=null&&!target.closed){
            target.currentScreen=new WeakReference<>(foreground);
            target.onPermissionResult(granted);
        }
    }

    /** On Android dual-STA, the local-only C110 Wi-Fi can lack a default route. */
    static Network preferredNetwork(ConnectivityManager manager){
        Network candidate=verifiedNetwork;
        return candidate!=null&&isWifi(manager,candidate)?candidate:null;
    }

    static Network connected(Context context){
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager==null)return null;
        Network known=preferredNetwork(manager);
        if(known!=null)return known;
        for(Network n:manager.getAllNetworks()){
            if(n==null||!isWifi(manager,n))continue;
            LinkProperties props=manager.getLinkProperties(n);
            if(props==null)continue;
            for(RouteInfo route:props.getRoutes())
                if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address
                    &&CAMERA_IP.equals(route.getGateway().getHostAddress()))
                    return n;
        }
        return null;
    }

    private static boolean isWifi(ConnectivityManager manager,Network n){
        if(manager==null||n==null)return false;
        NetworkCapabilities caps=manager.getNetworkCapabilities(n);
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /** Auto-connect only AFTER an exact target has been selected once. */
    void startAtLaunch(){
        if(closed||cm==null||attempted)return;
        if(connected(owner)!=null){
            report("已检测到相机Wi-Fi，进入C110模式即可预览");
            return;
        }
        if(prefs().getString(KEY_SSID,"").isEmpty()){
            report("未保存C110热点名称：打开C110模式，从附近热点列表选择一次");
            return;
        }
        Activity current=screen();
        requestSelected(current,false);
    }

    void requestForCameraMode(){
        if(closed||cm==null)return;
        if(connected(owner)!=null){
            report("C110相机Wi-Fi已就绪");
            return;
        }
        if(prefs().getString(KEY_SSID,"").isEmpty()){
            showOriginalStyleWifiPicker(screen());
            return;
        }
        if(requestedCallback!=null){
            report("正在等待Android连接已选中的C110热点");
            return;
        }
        requestSelected(screen(),false);
    }

    private void retryForCameraMode(){
        if(closed||cm==null)return;
        if(connected(owner)!=null){
            report("C110相机Wi-Fi已连接");
            return;
        }
        if(requestedCallback!=null)clearCallback(requestedCallback);
        // Refresh the actual scanned SSID/BSSID instead of blindly retrying
        // the same assumption that may never match the physical C110 hotspot.
        showOriginalStyleWifiPicker(screen());
    }

    void onPermissionResult(boolean granted){
        if(closed)return;
        if(granted)requestSelected(screen(),false);
        else report("未允许附近Wi-Fi设备权限，无法发起原厂式连接");
    }

    void onScanPermissionResult(Activity foreground,boolean granted){
        if(closed)return;
        currentScreen=new WeakReference<>(foreground);
        if(granted)showOriginalStyleWifiPicker(foreground);
        else showManualEntry(foreground,
            "未授权扫描附近Wi-Fi，请输入相机实际热点名称");
    }

    private void showOriginalStyleWifiPicker(Activity foreground){
        if(closed||chooserOpen||foreground==null||foreground.isFinishing())return;
        currentScreen=new WeakReference<>(foreground);
        if(wifiManager==null||!wifiManager.isWifiEnabled()){
            showManualEntry(foreground,"Wi-Fi尚未开启。可输入相机热点名称，开启Wi-Fi后连接");
            return;
        }
        // Original WifiBottomPopupV2 checks FINE_LOCATION before scanning.
        // Android 13+ still requires it for getScanResults()/startScan().
        if(foreground.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED){
            report("原厂步骤：需要位置权限扫描实际C110热点，不再猜SSID");
            foreground.requestPermissions(
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                SCAN_PERMISSION_REQUEST);
            return;
        }
        report("正在读取附近真实Wi-Fi热点（原厂扫描流程）");
        try{wifiManager.startScan();}catch(RuntimeException ignored){}
        main.postDelayed(()->{
            if(closed||foreground.isFinishing()||foreground.isDestroyed())return;
            showScanList(foreground);
        },1100L);
    }

    private static final class Target {
        final String ssid,bssid;
        Target(String ssid,String bssid){this.ssid=ssid;this.bssid=bssid;}
    }

    private void showScanList(Activity foreground){
        List<Target> found=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        try{
            for(ScanResult result:wifiManager.getScanResults()){
                String ssid=result.SSID;
                if(ssid==null||ssid.isEmpty())continue;
                String bssid=result.BSSID==null?"":result.BSSID;
                if(seen.add(ssid))found.add(new Target(ssid,bssid));
            }
        }catch(SecurityException noPermission){
            report("扫描权限未生效，请检查系统定位开关");
        }
        Collections.sort(found,Comparator.comparingInt(t->{
            String value=t.ssid.toUpperCase(Locale.ROOT);
            return value.contains("C110")||value.startsWith("SJ")?0:1;
        }));
        if(found.isEmpty()){
            showManualEntry(foreground,
                "未读取到热点。请确认C110已开机且Wi-Fi开启，并检查手机定位开关");
            return;
        }
        String[] labels=new String[found.size()];
        for(int i=0;i<found.size();i++){
            Target item=found.get(i);
            String u=item.ssid.toUpperCase(Locale.ROOT);
            labels[i]=(u.contains("C110")||u.startsWith("SJ")?"📷 ":"")+item.ssid;
        }
        chooserOpen=true;
        new AlertDialog.Builder(foreground)
            .setTitle("选择C110实际Wi-Fi热点（原厂方式）")
            .setItems(labels,(dialog,which)->{
                chooserOpen=false;
                Target selected=found.get(which);
                showPasswordDialog(foreground,selected.ssid,selected.bssid);
            })
            .setNeutralButton("手动输入",(dialog,which)->{
                chooserOpen=false;showManualEntry(foreground,"手动输入C110实际热点名称");
            })
            .setNegativeButton("取消",(dialog,which)->chooserOpen=false)
            .setOnCancelListener(dialog->chooserOpen=false)
            .show();
    }

    private void showManualEntry(Activity foreground,String message){
        if(closed||foreground.isFinishing()||chooserOpen)return;
        LinearLayout layout=new LinearLayout(foreground);
        layout.setPadding(38,18,38,8);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView hint=new TextView(foreground);
        hint.setText(message+"\n（首次填写后会保留，以后启动自动请求）");
        layout.addView(hint);
        EditText input=new EditText(foreground);
        input.setSingleLine(true);
        input.setHint("C110热点SSID（区分大小写）");
        input.setText(prefs().getString(KEY_SSID,""));
        layout.addView(input);
        chooserOpen=true;
        new AlertDialog.Builder(foreground)
            .setTitle("连接真实C110热点")
            .setView(layout)
            .setPositiveButton("下一步",(d,which)->{
                chooserOpen=false;
                String ssid=input.getText().toString().trim();
                if(ssid.isEmpty()){report("未填写热点名称，未发连接请求");return;}
                showPasswordDialog(foreground,ssid,"");
            })
            .setNegativeButton("取消",(d,which)->chooserOpen=false)
            .setOnCancelListener(d->chooserOpen=false)
            .show();
    }

    private void showPasswordDialog(Activity foreground,String exactSsid,String actualBssid){
        if(closed||foreground.isFinishing()||chooserOpen)return;
        String storedSsid=prefs().getString(KEY_SSID,"");
        String storedPwd=prefs().getString(KEY_PASSWORD,"");
        String suggested=storedSsid.equals(exactSsid)&&!storedPwd.isEmpty()?
            storedPwd:"12345678";
        EditText input=new EditText(foreground);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(suggested);
        input.setSelectAllOnFocus(true);
        input.setHint("相机热点密码（如未修改可用默认值）");
        chooserOpen=true;
        new AlertDialog.Builder(foreground)
            .setTitle("确认C110热点："+exactSsid)
            .setMessage("原厂按所选SSID和密码连接；请确认密码。不会将密码写进诊断日志。")
            .setView(input)
            .setPositiveButton("连接",(dialog,which)->{
                chooserOpen=false;
                String password=input.getText().toString();
                if(password.length()<8||password.length()>63){
                    report("相机Wi-Fi密码必须是8～63位，未发连接请求");
                    return;
                }
                prefs().edit().putString(KEY_SSID,exactSsid)
                    .putString(KEY_PASSWORD,password)
                    .putString(KEY_BSSID,actualBssid).apply();
                report("已选择真实热点 "+exactSsid+"；请求Android连接");
                requestSelected(foreground,true);
            })
            .setNegativeButton("取消",(dialog,which)->chooserOpen=false)
            .setOnCancelListener(d->chooserOpen=false)
            .show();
    }

    private void requestSelected(Activity foreground,boolean replaceOld){
        if(closed||cm==null||foreground==null||foreground.isFinishing())return;
        String ssid=prefs().getString(KEY_SSID,"");
        String password=prefs().getString(KEY_PASSWORD,"");
        String bssid=prefs().getString(KEY_BSSID,"");
        if(ssid.isEmpty()||password.length()<8){
            if(!foreground.isFinishing())showOriginalStyleWifiPicker(foreground);
            return;
        }
        if(connected(owner)!=null){
            report("C110网络已连接，省略重复系统请求");return;
        }
        if(replaceOld&&requestedCallback!=null)clearCallback(requestedCallback);
        if(requestedCallback!=null){report("已有相机Wi-Fi请求，等待系统反馈");return;}
        if(Build.VERSION.SDK_INT<29){report("当前Android需使用系统Wi-Fi页面连接");return;}
        String permission=Build.VERSION.SDK_INT>=33?
            Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
        if(foreground.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){
            currentScreen=new WeakReference<>(foreground);
            foreground.requestPermissions(new String[]{permission},PERMISSION_REQUEST);
            return;
        }
        // The OEM WifiHelper$1.run explicitly disconnects old Wi-Fi before
        // its vivo WifiNetworkSpecifier request. For a normal app on newer
        // Android, WifiManager.disconnect() may be ignored by system policy.
        if(wifiManager!=null&&Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("vivo")){
            try{wifiManager.disconnect();}catch(RuntimeException ignored){}
        }
        attempted=true;
        final int ticket=++generation;
        report("原厂式连接：精确热点 "+ssid+"，等待Android系统选择/授权");
        try{
            WifiNetworkSpecifier.Builder exact=new WifiNetworkSpecifier.Builder()
                .setSsid(ssid).setWpa2Passphrase(password);
            if(bssid!=null&&!bssid.isEmpty()){
                try{exact.setBssid(MacAddress.fromString(bssid));}
                catch(IllegalArgumentException wrongBssid){report("无效BSSID，使用精确SSID");}
            }
            NetworkRequest request=new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(exact.build()).build();
            ConnectivityManager.NetworkCallback cb=new ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(Network network){
                    if(closed||ticket!=generation)return;
                    report("Android报告C110 Wi-Fi已接入，正在核验相机3012");
                    verifyAsync(ticket,network);
                }
                @Override public void onLinkPropertiesChanged(Network network,LinkProperties info){
                    verifyAsync(ticket,network);
                }
                @Override public void onUnavailable(){
                    main.post(()->{
                        if(closed||ticket!=generation)return;
                        report("Android未完成该热点的连接；请核对实际SSID/密码并点↻重新选择");
                        clearCallback(this);
                    });
                }
                @Override public void onLost(Network network){
                    if(network.equals(verifiedNetwork))verifiedNetwork=null;
                    main.post(()->{
                        if(closed||ticket!=generation)return;
                        report("相机Wi-Fi已断开；可点击↻重新选择");
                        clearCallback(this);
                    });
                }
            };
            requestedCallback=cb;
            cm.requestNetwork(request,cb,30000);
        }catch(RuntimeException ex){
            if(requestedCallback!=null)clearCallback(requestedCallback);
            attempted=false;
            report("原厂式Wi-Fi申请失败："+ex.getClass().getSimpleName());
        }
    }

    private void clearCallback(ConnectivityManager.NetworkCallback cb){
        if(requestedCallback!=cb||cm==null)return;
        try{cm.unregisterNetworkCallback(cb);}catch(RuntimeException ignored){}
        requestedCallback=null;
        attempted=false;
    }

    private void verifyAsync(int ticket,Network network){
        if(closed||ticket!=generation||!isWifi(cm,network)||
            network.equals(verifiedNetwork)||network.equals(verifying))return;
        verifying=network;
        verifier.execute(()->{
            boolean standard=false;
            for(int n=0;n<3&&!standard&&ticket==generation&&!closed;n++){
                if(n>0)try{Thread.sleep(300);}catch(InterruptedException ex){
                    Thread.currentThread().interrupt();return;
                }
                HttpURLConnection conn=null;
                try{
                    conn=(HttpURLConnection)network.openConnection(
                        new URL("http",CAMERA_IP,80,"/?custom=1&cmd=3012"));
                    conn.setConnectTimeout(2200);conn.setReadTimeout(2200);
                    if(conn.getResponseCode()!=200)continue;
                    try(InputStream input=conn.getInputStream();
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                        byte[] data=new byte[512];int len;
                        while((len=input.read(data))!=-1){
                            if(bytes.size()+len>8192)break;
                            bytes.write(data,0,len);
                        }
                        String reply=bytes.toString(StandardCharsets.UTF_8.name());
                        boolean ok=Pattern.compile("<Status>0</Status>",
                            Pattern.CASE_INSENSITIVE).matcher(reply).find();
                        Matcher m=Pattern.compile("<String>([^<]{1,128})</String>",
                            Pattern.CASE_INSENSITIVE).matcher(reply);
                        boolean found=false,other=false;
                        while(m.find()){
                            String v=m.group(1).toUpperCase(Locale.ROOT);
                            if(v.contains("C110_PRO")||v.contains("C110+")||
                                v.contains("C110 PLUS"))other=true;
                            else if(v.contains("C110"))found=true;
                        }
                        standard=ok&&found&&!other;
                    }
                }catch(Exception ignored){
                }finally{if(conn!=null)conn.disconnect();}
            }
            if(ticket!=generation||closed)return;
            verifying=null;
            if(standard&&isWifi(cm,network)){
                verifiedNetwork=network;
                report("原厂式热点连接成功：3012标准C110已验证，可以播放画面");
            }else{
                report("已连上选定热点，但3012未验证标准C110；请检查相机Wi-Fi和热点选择");
            }
        });
    }

    private void report(String value){
        main.post(()->{if(!closed)updates.accept(value);});
    }

    void shutdown(){
        closed=true;
        generation++;
        verifying=null;
        if(requestedCallback!=null&&cm!=null)clearCallback(requestedCallback);
        requestedCallback=null;
        verifiedNetwork=null;
        if(mainOwner.get()==this)mainOwner.clear();
        verifier.shutdownNow();
    }
}
