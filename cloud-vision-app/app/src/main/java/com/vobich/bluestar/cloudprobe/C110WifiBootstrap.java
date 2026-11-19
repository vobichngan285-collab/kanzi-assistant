package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.PatternMatcher;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One foreground, user-approved Android local Wi-Fi request for standard C110.
 * Never toggles Wi-Fi, connects to a different camera type, or sends photo/mode commands.
 * The Network lease remains owned by MainActivity while C110CameraActivity is on screen.
 */
final class C110WifiBootstrap {
    static final int PERMISSION_REQUEST=4096;
    private static final String CAMERA_GATEWAY="192.168.1.254";
    private static volatile Network verifiedNetwork;
    private static WeakReference<C110WifiBootstrap> mainOwner=new WeakReference<>(null);

    /** C110 page uses the existing MainActivity Wi-Fi request lease, not a second competing request. */
    static boolean requestFromCameraPage(){
        C110WifiBootstrap controller=mainOwner.get();
        if(controller==null||controller.closed)return false;
        controller.main.post(controller::requestForCameraMode);
        return true;
    }

    /** A local-only secondary Wi-Fi may have NO default gateway while home Wi-Fi stays active. */
    static Network preferredNetwork(ConnectivityManager service){
        Network preferred=verifiedNetwork;
        return preferred!=null&&isWifiNetwork(service,preferred)?preferred:null;
    }
    private final Activity owner;
    private final ConnectivityManager cm;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService verifier=Executors.newSingleThreadExecutor();
    private final Consumer<String> updates;
    private ConnectivityManager.NetworkCallback requestedCallback;
    private volatile boolean closed;
    private volatile int generation;
    private boolean attempted;

    C110WifiBootstrap(Activity activity,Consumer<String> updates){
        owner=activity;this.updates=updates;
        cm=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(activity instanceof MainActivity)mainOwner=new WeakReference<>(this);
    }

    static Network connected(Context context){
        ConnectivityManager service=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(service==null)return null;
        // The verified onAvailable Network is authoritative even without a
        // default route (Android dual-STA / local-only network case).
        Network preferred=preferredNetwork(service);
        if(preferred!=null)return preferred;
        for(Network network:service.getAllNetworks())
            if(validGateway(service,network))return network;
        return null;
    }

    private static boolean isWifiNetwork(ConnectivityManager cm,Network network){
        if(cm==null||network==null)return false;
        NetworkCapabilities caps=cm.getNetworkCapabilities(network);
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            &&cm.getLinkProperties(network)!=null;
    }

    private static boolean validGateway(ConnectivityManager cm,Network network){
        if(network==null)return false;
        NetworkCapabilities caps=cm.getNetworkCapabilities(network);
        LinkProperties props=cm.getLinkProperties(network);
        if(caps==null||props==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return false;
        for(RouteInfo route:props.getRoutes())
            if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address
                &&CAMERA_GATEWAY.equals(route.getGateway().getHostAddress()))return true;
        return false;
    }

    void startAtLaunch(){
        if(closed||attempted||cm==null)return;
        // An already manually connected C110 must not cause another Android prompt.
        if(connected(owner)!=null){
            attempted=true;
            report("已检测到C110 Wi-Fi，打开模式即可预览");
            return;
        }
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q){
            attempted=true;
            report("当前Android版本需在系统Wi-Fi页面选择C110热点");
            return;
        }
        String permission=Build.VERSION.SDK_INT>=33?
            Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
        if(owner.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){
            report("准备请求连接C110，需要允许附近设备权限");
            owner.requestPermissions(new String[]{permission},PERMISSION_REQUEST);
            return;
        }
        requestCamera();
    }

    /** Explicitly entering/refreshing C110 mode may retry a previously failed startup request. */
    void requestForCameraMode(){
        if(closed||cm==null)return;
        if(connected(owner)!=null){
            report("C110 Wi-Fi已就绪");
            return;
        }
        // Keep only one system chooser active. It must be confirmed by Android.
        if(requestedCallback!=null){
            report("正在等待系统确认连接C110，无需进入手机Wi-Fi设置");
            return;
        }
        attempted=false;
        startAtLaunch();
    }

    void onPermissionResult(boolean granted){
        if(closed)return;
        if(granted)startAtLaunch();
        else {
            attempted=true;
            report("未获得连接权限；可从系统Wi-Fi手动连接C110");
        }
    }

    private void requestCamera(){
        if(closed||attempted||cm==null)return;
        attempted=true;
        final int id=++generation;
        report("正在请求连接已开机的C110，系统可能要求确认");
        try{
            // Official C110 manual: SSID starts C110, default WPA2 passphrase 12345678.
            // Android presents its own approval dialog; no hidden or silent association.
            WifiNetworkSpecifier spec=new WifiNetworkSpecifier.Builder()
                .setSsidPattern(new PatternMatcher("C110",PatternMatcher.PATTERN_PREFIX))
                .setWpa2Passphrase("12345678")
                .build();
            NetworkRequest request=new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(spec)
                .build();
            ConnectivityManager.NetworkCallback cb=new ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(Network network){
                    verifyAsync(id,network);
                }
                @Override public void onLinkPropertiesChanged(Network network,LinkProperties properties){
                    verifyAsync(id,network);
                }
                @Override public void onUnavailable(){
                    main.post(()->{
                        if(id!=generation||closed)return;
                        report("C110自动连接未成功；可点击相机页左上角重新连接");
                        clearFailedRequest(cb);
                    });
                }
                @Override public void onLost(Network network){
                    if(network.equals(verifiedNetwork))verifiedNetwork=null;
                    main.post(()->{
                        if(id!=generation||closed)return;
                        report("C110 Wi-Fi已断开；点击重新连接可再次弹出系统连接请求");
                        clearFailedRequest(cb);
                    });
                }
            };
            requestedCallback=cb;
            cm.requestNetwork(request,cb,30000);
        }catch(RuntimeException error){
            if(requestedCallback!=null){
                try{cm.unregisterNetworkCallback(requestedCallback);}
                catch(RuntimeException ignored){}
            }
            requestedCallback=null;
            attempted=false;
            report("C110连接请求未发起："+error.getClass().getSimpleName());
        }
    }

    private void clearFailedRequest(ConnectivityManager.NetworkCallback cb){
        if(requestedCallback!=cb)return;
        try{cm.unregisterNetworkCallback(cb);}catch(RuntimeException ignored){}
        requestedCallback=null;
        attempted=false;
    }

    private volatile Network verifying;
    private void verifyAsync(int id,Network network){
        if(closed||id!=generation||network==null||network.equals(verifying)||
            network.equals(verifiedNetwork)||!isWifiNetwork(cm,network))return;
        verifying=network;
        verifier.execute(()->{
            boolean standard=false;
            // onAvailable may precede full DHCP routes. Recheck this strictly
            // read-only identity query, never switch modes or take a photo.
            for(int attempt=0;attempt<3&&!standard&&id==generation&&!closed;attempt++){
              if(attempt>0)try{Thread.sleep(350L);}catch(InterruptedException ex){
                  Thread.currentThread().interrupt();return;
              }
              try{
                HttpURLConnection conn=(HttpURLConnection)network.openConnection(
                    new URL("http",CAMERA_GATEWAY,80,"/?custom=1&cmd=3012"));
                try{
                    conn.setConnectTimeout(2200);conn.setReadTimeout(2200);
                    if(conn.getResponseCode()==200){
                        try(InputStream in=conn.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                            byte[] buf=new byte[512];int n;
                            while((n=in.read(buf))!=-1){
                                if(bytes.size()+n>8192)break;
                                bytes.write(buf,0,n);
                            }
                            String reply=bytes.toString(StandardCharsets.UTF_8.name());
                            Matcher names=Pattern.compile("<String>([^<]{1,128})</String>",
                                Pattern.CASE_INSENSITIVE).matcher(reply);
                            boolean found=false,invalid=false;
                            while(names.find()){
                                String name=names.group(1).toUpperCase(Locale.ROOT);
                                if(name.contains("C110_PRO")||name.contains("C110+")||
                                   name.contains("C110 PLUS"))invalid=true;
                                else if(name.contains("C110"))found=true;
                            }
                            standard=!invalid&&found&&
                                Pattern.compile("<Status>0</Status>",Pattern.CASE_INSENSITIVE)
                                    .matcher(reply).find();
                        }
                    }
                }finally{conn.disconnect();}
              }catch(Exception ignored){}
            }
            if(id!=generation||closed)return;
            // Validate the standard C110 via its real 3012 response rather than
            // assuming a default route on secondary Android local-only Wi-Fi.
            if(standard&&isWifiNetwork(cm,network)){
                verifiedNetwork=network;
                report("标准C110已连接，进入运动相机模式即可预览");
            }else report("网络已连上但3012尚未确认标准C110");
            if(verifying==network)verifying=null;
        });
    }

    private void report(String message){
        main.post(()->{if(!closed)updates.accept(message);});
    }

    void shutdown(){
        closed=true;
        generation++;
        verifying=null;
        if(requestedCallback!=null&&cm!=null){
            try{cm.unregisterNetworkCallback(requestedCallback);}catch(RuntimeException ignored){}
        }
        requestedCallback=null;verifiedNetwork=null;
        if(mainOwner.get()==this)mainOwner.clear();
        verifier.shutdownNow();
    }
}
