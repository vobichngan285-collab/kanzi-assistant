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
    private static final String KEY_AUTO="auto_connect";
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
    private boolean autoLaunchRequested;
    private Credentials pendingPermission;

    private static final class Credentials {
        final String ssid,password,bssid;
        final boolean freshSelection;
        Credentials(String ssid,String password,String bssid,boolean freshSelection){
            this.ssid=ssid;this.password=password;this.bssid=bssid;
            this.freshSelection=freshSelection;
        }
    }
    static String savedSsid(Context context){
        return context.getSharedPreferences(WIFI_PREF,Context.MODE_PRIVATE)
            .getString(KEY_SSID,"");
    }
    static boolean automatic(Context context){
        return context.getSharedPreferences(WIFI_PREF,Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO,true);
    }
    static void setAutomatic(Context context,boolean enabled){
        context.getSharedPreferences(WIFI_PREF,Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO,enabled).apply();
    }

    C110WifiBootstrap(Activity activity,Consumer<String> updates){
        owner=activity;
        this.updates=updates;
        cm=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        wifiManager=(WifiManager)activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        // Home owns one long-lived NetworkCallback across the other Activities.
        C110WifiBootstrap existing=mainOwner.get();
        if(activity instanceof HomeActivity||existing==null||existing.closed)
            mainOwner=new WeakReference<>(this);
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

    static boolean onScanPermissionFromCameraPage(Activity foreground,boolean granted){
        C110WifiBootstrap target=mainOwner.get();
        if(target==null||target.closed)return false;
        target.onScanPermissionResult(foreground,granted);
        return true;
    }
    static boolean onWifiPermissionFromCameraPage(Activity foreground,boolean granted){
        C110WifiBootstrap target=mainOwner.get();
        if(target==null||target.closed)return false;
        target.currentScreen=new WeakReference<>(foreground);
        target.onPermissionResult(granted);
        return true;
    }
    /** Allow changing physical cameras even when another C110 is connected. */
    static boolean chooseFromCameraPage(Activity foreground){
        C110WifiBootstrap target=mainOwner.get();
        if(target==null||target.closed)return false;
        target.currentScreen=new WeakReference<>(foreground);
        target.main.post(()->target.chooseCameraFromScreen(foreground));
        return true;
    }
    void chooseCameraFromScreen(Activity foreground){
        if(closed)return;
        currentScreen=new WeakReference<>(foreground);
        showOriginalStyleWifiPicker(foreground);
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

    /** Home makes one automatic request on its foreground entry, if paired. */
    void startAtLaunch(){
        if(closed||cm==null)return;
        String ssid=prefs().getString(KEY_SSID,"");
        String password=prefs().getString(KEY_PASSWORD,"");
        if(!C110WifiPairingPolicy.shouldAutoConnect(automatic(owner),
                autoLaunchRequested||attempted,connected(owner)!=null,ssid,password))
            return;
        autoLaunchRequested=true;
        report("正在自动连接已记住的相机 "+ssid);
        requestSelected(screen(),false);
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
        if(connected(owner)!=null){report("相机Wi-Fi已连接");return;}
        if(requestedCallback!=null)clearCallback(requestedCallback);
        requestSelected(screen(),true);
    }

    void onPermissionResult(boolean granted){
        if(closed)return;
        Credentials pending=pendingPermission;
        pendingPermission=null;
        if(!granted){report("未授权附近Wi-Fi，无法连接相机");return;}
        if(pending!=null)requestTarget(screen(),pending,true);
        else requestSelected(screen(),false);
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
        new BlueStarDialogChoices.PlatformBuilder(foreground)
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
        new BlueStarDialogChoices.PlatformBuilder(foreground)
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
        // Never silently guess the password for a different camera.
        String suggested=storedSsid.equals(exactSsid)&&!storedPwd.isEmpty()?
            storedPwd:"";
        EditText input=new EditText(foreground);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(suggested);
        input.setSelectAllOnFocus(true);
        input.setHint("相机热点密码（如未修改可用默认值）");
        chooserOpen=true;
        new BlueStarDialogChoices.PlatformBuilder(foreground)
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
                // Do not replace the remembered device until verified by 3012.
                report("正在验证新相机 "+exactSsid+"；成功后才会记住");
                requestTarget(foreground,
                    new Credentials(exactSsid,password,actualBssid,true),true);
            })
            .setNegativeButton("取消",(dialog,which)->chooserOpen=false)
            .setOnCancelListener(d->chooserOpen=false)
            .show();
    }

    private void requestSelected(Activity foreground,boolean replaceOld){
        if(closed||foreground==null||foreground.isFinishing())return;
        String ssid=prefs().getString(KEY_SSID,"");
        String password=prefs().getString(KEY_PASSWORD,"");
        if(!C110WifiPairingPolicy.validTarget(ssid,password)){
            showOriginalStyleWifiPicker(foreground);return;
        }
        // Remember exact SSID; never pin the old BSSID after camera reboot.
        requestTarget(foreground,new Credentials(ssid,password,"",false),replaceOld);
    }

    private void requestTarget(Activity foreground,Credentials target,boolean replaceOld){
        if(closed||cm==null||foreground==null||foreground.isFinishing())return;
        currentScreen=new WeakReference<>(foreground);
        if(target==null||!C110WifiPairingPolicy.validTarget(target.ssid,target.password)){
            report("相机Wi-Fi名称或密码无效，未发起连接");return;
        }
        if(!target.freshSelection&&connected(owner)!=null){
            report("相机Wi-Fi已连接，不再重复申请");return;
        }
        if(replaceOld&&requestedCallback!=null){
            clearCallback(requestedCallback);
            verifiedNetwork=null;
        }
        if(requestedCallback!=null){
            report("已有Wi-Fi连接请求，请等待结果");return;
        }
        if(Build.VERSION.SDK_INT<29){
            report("此Android版本需要在系统Wi-Fi中手动连接");return;
        }
        String permission=Build.VERSION.SDK_INT>=33?
            Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
        if(foreground.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){
            pendingPermission=target;
            foreground.requestPermissions(new String[]{permission},PERMISSION_REQUEST);
            return;
        }
        pendingPermission=null;
        // Let Android switch networks; do not disconnect home Wi-Fi before
        // knowing the camera can connect. User has disabled dual-WLAN.
        attempted=true;
        final int ticket=++generation;
        report("正在请求连接相机 "+target.ssid+"；首次需按Android提示授权");
        try{
            WifiNetworkSpecifier.Builder exact=new WifiNetworkSpecifier.Builder()
                .setSsid(target.ssid).setWpa2Passphrase(target.password);
            String bssid=C110WifiPairingPolicy.effectiveBssid(target.bssid,target.freshSelection);
            if(!bssid.isEmpty()){
                try{exact.setBssid(MacAddress.fromString(bssid));}
                catch(IllegalArgumentException bad){
                    report("无效相机BSSID，改用SSID连接");
                }
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
                    report("Android已接入相机Wi-Fi，正在核验3012身份");
                    verifyAsync(ticket,network,target);
                }
                @Override public void onLinkPropertiesChanged(Network network,LinkProperties info){
                    verifyAsync(ticket,network,target);
                }
                @Override public void onUnavailable(){
                    main.post(()->{
                        if(closed||ticket!=generation)return;
                        report("未连接成功；确认相机热点已开启，或重新扫描选择");
                        clearCallback(this);
                        autoLaunchRequested=false;
                    });
                }
                @Override public void onLost(Network network){
                    if(network.equals(verifiedNetwork))verifiedNetwork=null;
                    main.post(()->{
                        if(closed||ticket!=generation)return;
                        report("相机Wi-Fi断开；下次打开软件时重新尝试");
                        clearCallback(this);
                        autoLaunchRequested=false;
                    });
                }
            };
            requestedCallback=cb;
            cm.requestNetwork(request,cb,30000);
        }catch(RuntimeException ex){
            if(requestedCallback!=null)clearCallback(requestedCallback);
            attempted=false;autoLaunchRequested=false;
            report("Wi-Fi请求失败："+ex.getClass().getSimpleName());
        }
    }

    private void clearCallback(ConnectivityManager.NetworkCallback cb){
        if(requestedCallback!=cb||cm==null)return;
        try{cm.unregisterNetworkCallback(cb);}catch(RuntimeException ignored){}
        requestedCallback=null;
        attempted=false;
    }

    private void verifyAsync(int ticket,Network network,Credentials target){
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
                if(C110WifiPairingPolicy.commitNewTarget(true,target.freshSelection)){
                    prefs().edit().putString(KEY_SSID,target.ssid)
                        .putString(KEY_PASSWORD,target.password)
                        .putString(KEY_BSSID,target.bssid)
                        .putBoolean(KEY_AUTO,true).apply();
                    report("相机已验证并记住 "+target.ssid+"；下次打开软件自动连接");
                }else report("已自动连接并验证相机 "+target.ssid);
            }else{
                report("3012未验证当前热点为受支持的C110，旧相机记录未覆盖");
                main.post(()->{
                    if(ticket!=generation)return;
                    if(requestedCallback!=null)clearCallback(requestedCallback);
                    autoLaunchRequested=false;
                });
            }
        });
    }

    private void report(String value){
        main.post(()->{
            if(closed)return;
            updates.accept(value);
            Activity foreground=currentScreen.get();
            // One Home network lease, even when Settings is on top.
            if(foreground instanceof MainActivity&&foreground!=owner)
                ((MainActivity)foreground).onC110WifiStatusFromShared(value);
        });
    }

    void shutdown(){
        closed=true;
        generation++;
        verifying=null;
        if(requestedCallback!=null&&cm!=null)clearCallback(requestedCallback);
        requestedCallback=null;
        if(mainOwner.get()==this){
            verifiedNetwork=null;
            mainOwner.clear();
        }
        verifier.shutdownNow();
    }
}
