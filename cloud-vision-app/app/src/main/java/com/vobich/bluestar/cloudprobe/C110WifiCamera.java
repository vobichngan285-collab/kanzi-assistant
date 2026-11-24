package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trial integration of the C110 Novatek photo channel, based on six field-verified
 * successful 2026-11-18 shutter/complete-JPEG transfers.
 *
 * No process binding: ONLY local camera HTTP uses the Wi-Fi Network object.
 * A separate cellular Network must be chosen for paid cloud requests.
 * Does not write to the camera SD card except the one user-triggered photo;
 * no delete, firmware changes, media-mode changes or live-preview command.
 */
final class C110WifiCamera {
    static final String CAMERA_IP="192.168.1.254";
    private static final int MAX_INDEX=1024*1024, MAX_JPEG=32*1024*1024;
    private Network heldNetwork;
    private boolean photoModeReady;

    static final class Photo {
        final byte[] jpeg;
        final int width,height;
        final long modeMs,shutterMs,discoverMs,downloadMs,totalMs;
        final boolean wifiStillPresent;
        final String sourceCameraPath;
        final boolean strictOneFileDelta;
        Photo(byte[] jpeg,int width,int height,long modeMs,long shutterMs,
              long discoverMs,long downloadMs,long totalMs,boolean wifiStillPresent){
            this(jpeg,width,height,modeMs,shutterMs,discoverMs,downloadMs,
                 totalMs,wifiStillPresent,null,false);
        }
        Photo(byte[] jpeg,int width,int height,long modeMs,long shutterMs,
              long discoverMs,long downloadMs,long totalMs,boolean wifiStillPresent,
              String sourceCameraPath,boolean strictOneFileDelta){
            this.jpeg=jpeg;this.width=width;this.height=height;
            this.modeMs=modeMs;this.shutterMs=shutterMs;this.discoverMs=discoverMs;
            this.downloadMs=downloadMs;this.totalMs=totalMs;
            this.wifiStillPresent=wifiStillPresent;
            this.sourceCameraPath=sourceCameraPath;
            this.strictOneFileDelta=strictOneFileDelta;
        }
    }

    static Network requireCellular(Context context) throws IOException {
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)throw new IOException("网络服务不可用");
        Network fallback=null;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities c=cm.getNetworkCapabilities(n);
            if(c==null||!c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)||
                !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))continue;
            if(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))return n;
            fallback=n;
        }
        if(fallback!=null)return fallback;
        throw new IOException("未找到移动数据网络。请开启手机移动数据后再使用C110云端识别");
    }

    synchronized void resetPhotoMode(){photoModeReady=false;}

    /** The caller explicitly taps Preview. This is NOT called by camera shoot(). */
    static Network previewNetwork(Context context) throws IOException {
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)throw new IOException("Android网络服务不可用");
        return findCameraNetwork(cm);
    }

    static final class PreviewInit {
        final int modeStatus,startStatus,deviceKind,currentMode;
        final long modeMs,startMs;
        PreviewInit(int modeStatus,int startStatus,long modeMs,long startMs,
                    int deviceKind,int currentMode){
            this.modeStatus=modeStatus;this.startStatus=startStatus;
            this.modeMs=modeMs;this.startMs=startMs;
            this.deviceKind=deviceKind;this.currentMode=currentMode;
        }
    }

    private static int c110DeviceKind(String reply){
        Matcher m=Pattern.compile("<String>([^<]{0,128})</String>",
            Pattern.CASE_INSENSITIVE).matcher(reply);
        boolean ordinary=false;
        while(m.find()){
            String hint=m.group(1).toUpperCase(Locale.ROOT);
            if(hint.contains("C110_PRO"))return 2;
            if(hint.contains("C110"))ordinary=true;
        }
        return ordinary?1:0;
    }

    /**
     * The user's first successful C110 OEM minimal startup:
     * read-only 3012 -> read-only 3016 -> mode 3001/1 -> stream 2015/1.
     * No custom RTSP probe or extra 3002 before video playback.
     */
    synchronized PreviewInit startPreview(Context context,Network wifi) throws IOException {
        Reply identity=get(wifi,"/?custom=1&cmd=3012",MAX_INDEX);
        if(identity.http!=200||
           !Integer.valueOf(0).equals(C110PhotoPolicy.tagInt(identity.text(),"Status")))
            throw new IOException("C110设备信息读取失败");
        int deviceKind=c110DeviceKind(identity.text());
        if(deviceKind==2)
            throw new IOException("C110_PRO使用其他视频分支，本次仅验证标准版C110");
        Reply current=get(wifi,"/?custom=1&cmd=3016",MAX_INDEX);
        if(current.http!=200)throw new IOException("C110模式信息读取失败");
        Integer value=C110PhotoPolicy.tagInt(current.text(),"Value");
        int currentMode=value==null?-4096:value;
        photoModeReady=false;
        Reply mode=get(wifi,"/?custom=1&cmd=3001&par=1",MAX_INDEX);
        Integer m=C110PhotoPolicy.tagInt(mode.text(),"Status");
        int modeStatus=mode.http==200?(m==null?-4096:m):-3000-mode.http;
        if(modeStatus!=0)
            return new PreviewInit(modeStatus,-4095,mode.ms,0,deviceKind,currentMode);
        Reply stream=get(wifi,"/?custom=1&cmd=2015&par=1",MAX_INDEX);
        Integer v=C110PhotoPolicy.tagInt(stream.text(),"Status");
        int startStatus=stream.http==200?(v==null?-4096:v):-3000-stream.http;
        return new PreviewInit(modeStatus,startStatus,mode.ms,stream.ms,
            deviceKind,currentMode);
    }

    /** Read-only bounded probe of the alternate OEM HTTP 8192 candidate. */
    static int probeHttp8192(Network wifi) {
        HttpURLConnection con=null;
        try {
            con=(HttpURLConnection)wifi.openConnection(new URL("http",CAMERA_IP,8192,"/"));
            con.setRequestMethod("GET");
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(1500);con.setReadTimeout(1000);
            int status=con.getResponseCode();
            if(status==200){
                try(InputStream input=con.getInputStream()){
                    byte[] first=new byte[32];
                    input.read(first);
                }
            }
            return status;
        } catch(Exception failure){return -1;}
        finally {if(con!=null)con.disconnect();}
    }

    synchronized Photo shoot(Context context) throws Exception {
        return shootInternal(context,false);
    }

    /**
     * A C110 live video session needs photo mode BEFORE reading the fresh-JPEG
     * baseline. The existing proven shoot(Context) order stays unchanged.
     * The caller must release VLC before invoking this method.
     */
    synchronized Photo shootAfterLivePreview(Context context) throws Exception {
        photoModeReady=false;
        return shootInternal(context,true);
    }

    private Photo shootInternal(Context context,boolean fromLiveVideo) throws Exception {
        long begin=SystemClock.elapsedRealtime();
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)throw new IOException("网络服务不可用");
        Network wifi=findCameraNetwork(cm);
        if(heldNetwork==null||!heldNetwork.equals(wifi)){
            photoModeReady=false;heldNetwork=wifi;
        }
        Reply identity=get(wifi,"/?custom=1&cmd=3012",MAX_INDEX);
        if(identity.http!=200||identity.body.length==0||
           !Integer.valueOf(0).equals(C110PhotoPolicy.tagInt(identity.text(),"Status")))
            throw new IOException("C110设备探测失败，禁止发送快门");
        Reply commands=get(wifi,"/?custom=1&cmd=3002",MAX_INDEX);
        if(commands.http!=200||!commands.text().contains("<Cmd>1001</Cmd>")||
           !commands.text().contains("<Cmd>3001</Cmd>"))
            throw new IOException("当前相机未返回已验证的C110拍照指令列表");
        long modeMs=0;
        if(fromLiveVideo){
            // A running RTSP session leaves firmware in video mode. Perform the
            // single validated 3001/par=0 transition first; only then read
            // the photo baseline, avoiding directory errors in video mode.
            Reply photoMode=get(wifi,"/?custom=1&cmd=3001&par=0",MAX_INDEX);
            modeMs=photoMode.ms;
            if(photoMode.http!=200||
               !Integer.valueOf(0).equals(C110PhotoPolicy.tagInt(photoMode.text(),"Status")))
                throw new IOException("C110视频切换照片模式失败，未发送快门");
            photoModeReady=true;
        }
        Set<String> previous=list(wifi);
        if(!photoModeReady){
            Reply mode=get(wifi,"/?custom=1&cmd=3001&par=0",MAX_INDEX);
            modeMs=mode.ms;
            if(mode.http!=200||!Integer.valueOf(0).equals(C110PhotoPolicy.tagInt(mode.text(),"Status")))
                throw new IOException("切换C110照片模式失败："+C110PhotoPolicy.tagInt(mode.text(),"Status"));
            photoModeReady=true;
        }
        // A single explicit user tap causes exactly one shutter; no silent retry.
        long shutterAt=SystemClock.elapsedRealtime();
        Reply shutter=get(wifi,"/?custom=1&cmd=1001",MAX_INDEX);
        Integer status=C110PhotoPolicy.tagInt(shutter.text(),"Status");
        if(shutter.http!=200||!Integer.valueOf(0).equals(status)){
            photoModeReady=false;
            throw new IOException("C110快门失败："+status+"（不会自动重复拍照）");
        }
        long firstSeen=-1;
        while(SystemClock.elapsedRealtime()-shutterAt<20000){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("已取消C110取图");
            Set<String> added=list(wifi);
            added.removeAll(previous);
            if(!added.isEmpty()){
                if(firstSeen<0)firstSeen=SystemClock.elapsedRealtime();
                // Deletion safety only: never offer a candidate if the original
                // baseline was empty/uncertain or multiple entries appeared.
                // This does NOT affect normal JPEG capture or its timing.
                boolean strictOneFileDelta=!previous.isEmpty()&&added.size()==1;
                ArrayList<String> sorted=new ArrayList<>(added);
                Collections.sort(sorted);
                String candidate=sorted.get(sorted.size()-1);
                try{
                    long fetchStart=SystemClock.elapsedRealtime();
                    Photo ready=download(wifi,candidate,modeMs,shutter.ms,
                        firstSeen-shutterAt,begin,cm);
                    return new Photo(ready.jpeg,ready.width,ready.height,
                        modeMs,shutter.ms,firstSeen-shutterAt,
                        SystemClock.elapsedRealtime()-fetchStart,
                        SystemClock.elapsedRealtime()-begin,
                        cm.getNetworkCapabilities(wifi)!=null,candidate,strictOneFileDelta);
                }catch(IOException stillWriting){
                    // The HTML file may appear before the SD write has finished.
                    // Only retry read-only JPEG fetch; NEVER retry the shutter.
                }
            }
            Thread.sleep(350);
        }
        throw new IOException("C110拍照已应答，但20秒内没有出现完整新JPEG");
    }


    private static Network findCameraNetwork(ConnectivityManager cm) throws IOException {
        // A WifiNetworkSpecifier connection can be Android's secondary
        // local-only STA without a default route. The 3012-verified Network
        // from the explicit Wi-Fi request is preferred over gateway discovery.
        Network verified=C110WifiBootstrap.preferredNetwork(cm);
        if(verified!=null)return verified;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
            LinkProperties lp=cm.getLinkProperties(n);
            if(lp==null)continue;
            for(RouteInfo route:lp.getRoutes()){
                if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address&&
                   CAMERA_IP.equals(route.getGateway().getHostAddress()))return n;
            }
        }
        throw new IOException("未连接到C110 Wi-Fi（网关应为192.168.1.254）");
    }

    private static final class Reply {
        final int http;
        final byte[] body;
        final long ms;
        Reply(int http,byte[] body,long ms){this.http=http;this.body=body;this.ms=ms;}
        String text(){return new String(body,StandardCharsets.UTF_8);}
    }

    private static Reply get(Network wifi,String path,int limit) throws IOException {
        boolean command=path.equals("/?custom=1&cmd=3012")||
            path.equals("/?custom=1&cmd=3002")||path.equals("/?custom=1&cmd=3016")||
            path.equals("/?custom=1&cmd=3015")||
            path.equals("/?custom=1&cmd=1001")||
            path.equals("/?custom=1&cmd=3001&par=0")||
            path.equals("/?custom=1&cmd=3001&par=1")||
            path.equals("/?custom=1&cmd=2015&par=1");
        boolean media=path.startsWith("/DCIM/")&&!path.contains("..")&&
            !path.contains("\\")&&!path.contains("?")&&!path.contains("#");
        if(!command&&!media)throw new IOException("C110请求不在严格白名单内");
        long started=SystemClock.elapsedRealtime();
        HttpURLConnection con=null;
        try{
            con=(HttpURLConnection)wifi.openConnection(new URL("http",CAMERA_IP,80,path));
            con.setConnectTimeout(3000);con.setReadTimeout(7000);
            con.setUseCaches(false);con.setInstanceFollowRedirects(false);
            con.setRequestProperty("Accept-Encoding","identity");
            int status=con.getResponseCode();
            if(status!=200)return new Reply(status,new byte[0],SystemClock.elapsedRealtime()-started);
            try(InputStream stream=con.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buf=new byte[16384];int n;
                while((n=stream.read(buf))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("C110读取已取消");
                    if(output.size()+n>limit)throw new IOException("C110响应超过大小限制");
                    output.write(buf,0,n);
                }
                return new Reply(status,output.toByteArray(),SystemClock.elapsedRealtime()-started);
            }
        }finally{if(con!=null)con.disconnect();}
    }

    /** Physical shutter: only read complete directory snapshots. Never calls 1001 or changes mode. */
    static final class PhysicalSnapshot {
        final Set<String> paths,dirs;
        PhysicalSnapshot(Set<String> paths,Set<String> dirs){this.paths=paths;this.dirs=dirs;}
    }
    private static boolean physicalDirectory(String dir){
        return dir!=null&&dir.matches("/DCIM/(?:[A-Za-z0-9_-]+/){0,4}");
    }
    private static Reply physicalListing(Network wifi,String dir)throws IOException{
        if(!physicalDirectory(dir))throw new IOException("目录不符合监听规则");
        Reply r=get(wifi,dir,MAX_INDEX);
        String body=r.text().toLowerCase(Locale.ROOT);
        if(r.http!=200||(!body.contains("<html")&&!body.contains("<a ")&&!body.contains("index of")))
            throw new IOException("照片目录未返回有效列表");
        return r;
    }
    static PhysicalSnapshot physicalSnapshot(Network wifi)throws IOException{
        Set<String> paths=new LinkedHashSet<>(),dirs=new LinkedHashSet<>();
        dirs.add("/DCIM/");
        ArrayList<String> todo=new ArrayList<>(dirs);
        for(int i=0;i<todo.size();i++){
            String dir=todo.get(i);
            Reply r=physicalListing(wifi,dir);
            for(String path:C110PhotoPolicy.jpegs(r.text(),dir,CAMERA_IP)){
                String exact=C110DeletePolicy.exactJpegPath(path);
                if(exact!=null)paths.add(exact);
            }
            for(String child:C110PhotoPolicy.dirs(r.text(),dir,CAMERA_IP)){
                if(!physicalDirectory(child)||!child.startsWith(dir)||child.equals(dir))continue;
                if(dirs.add(child)){
                    if(dirs.size()>12)throw new IOException("照片目录过多，未建立完整基线");
                    todo.add(child);
                }
            }
        }
        if(dirs.size()==1&&paths.isEmpty()){
            // The proven firmware exposes /DCIM/PHOTO/ even on some sparse root listings.
            Reply photo=physicalListing(wifi,"/DCIM/PHOTO/");
            dirs.add("/DCIM/PHOTO/");
            for(String path:C110PhotoPolicy.jpegs(photo.text(),"/DCIM/PHOTO/",CAMERA_IP)){
                String exact=C110DeletePolicy.exactJpegPath(path);
                if(exact!=null)paths.add(exact);
            }
        }
        return new PhysicalSnapshot(paths,dirs);
    }
    static Photo physicalDownload(Context context,Network wifi,String path,long discovered)throws IOException{
        if(C110DeletePolicy.exactJpegPath(path)==null)throw new IOException("无效照片路径");
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null||cm.getNetworkCapabilities(wifi)==null)throw new IOException("相机已断开");
        return download(wifi,path,0,0,0,discovered,cm);
    }
    static byte[] physicalDigest(byte[] bytes){
        try{return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    /** Exact single new JPEG only, after successful OCR, with same-network and session guard.
     * Verify contents again immediately before the only write; never retry a delete. */
    static boolean physicalDelete(Context context,Network wifi,String path,byte[] digest,
                                  java.util.function.BooleanSupplier allowed)throws Exception{
        if(!allowed.getAsBoolean()||C110DeletePolicy.exactJpegPath(path)==null)return false;
        Reply identity=get(wifi,"/?custom=1&cmd=3012",MAX_INDEX);
        String info=identity.text().toUpperCase(Locale.ROOT);
        if(identity.http!=200||!Integer.valueOf(0).equals(C110PhotoPolicy.tagInt(identity.text(),"Status"))
            ||c110DeviceKind(identity.text())!=1||info.contains("C110+")||info.contains("C110 PLUS")
            ||info.contains("C110PRO"))return false;
        Reply capabilities=get(wifi,"/?custom=1&cmd=3002",MAX_INDEX);
        if(capabilities.http!=200||!capabilities.text().contains("<Cmd>4003</Cmd>"))return false;
        String dir=path.substring(0,path.lastIndexOf('/')+1);
        if(!C110PhotoPolicy.jpegs(physicalListing(wifi,dir).text(),dir,CAMERA_IP).contains(path))return false;
        Photo check=physicalDownload(context,wifi,path,SystemClock.elapsedRealtime());
        if(!java.security.MessageDigest.isEqual(digest,physicalDigest(check.jpeg)))return false;
        if(!allowed.getAsBoolean()||!wifi.equals(C110WifiBootstrap.connected(context)))return false;
        // Strict endpoint built only from exact validated path. No bulk, no format, no retry.
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)wifi.openConnection(new URL("http",CAMERA_IP,80,
                C110DeletePolicy.oneFileDeleteEndpoint(path)));
            conn.setRequestMethod("GET");conn.setConnectTimeout(2800);conn.setReadTimeout(3500);
            conn.setUseCaches(false);conn.setInstanceFollowRedirects(false);
            if(conn.getResponseCode()!=200)return false;
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[1024];int n;
                while((n=in.read(b))!=-1){
                    if(out.size()+n>16384)return false;
                    out.write(b,0,n);
                }
                if(!Integer.valueOf(0).equals(C110PhotoPolicy.tagInt(out.toString("UTF-8"),"Status")))return false;
            }
        }finally{if(conn!=null)conn.disconnect();}
        return !C110PhotoPolicy.jpegs(physicalListing(wifi,dir).text(),dir,CAMERA_IP).contains(path);
    }

    private static Set<String> list(Network wifi) throws IOException {
        LinkedHashSet<String> found=new LinkedHashSet<>();
        // 3015 returned 0 candidates on actual C110 firmware; directory HTML is proven.
        try{
            Reply r=get(wifi,"/?custom=1&cmd=3015",MAX_INDEX);
            if(r.http==200)found.addAll(C110PhotoPolicy.jpegs(r.text(),"/DCIM/",CAMERA_IP));
        }catch(IOException ignored){}
        LinkedHashSet<String> scan=new LinkedHashSet<>(
            Arrays.asList("/DCIM/PHOTO/","/DCIM/"));
        for(String dir:new ArrayList<>(scan)){
            Reply reply=get(wifi,dir,MAX_INDEX);
            if(reply.http!=200)continue;
            found.addAll(C110PhotoPolicy.jpegs(reply.text(),dir,CAMERA_IP));
            if(dir.equals("/DCIM/"))
                for(String sub:C110PhotoPolicy.dirs(reply.text(),dir,CAMERA_IP))
                    if(scan.size()<7)scan.add(sub);
        }
        int extra=0;
        for(String dir:scan){
            if(dir.equals("/DCIM/PHOTO/")||dir.equals("/DCIM/"))continue;
            if(extra++>=5)break;
            Reply reply=get(wifi,dir,MAX_INDEX);
            if(reply.http==200)found.addAll(C110PhotoPolicy.jpegs(reply.text(),dir,CAMERA_IP));
        }
        return found;
    }

    private static Photo download(Network wifi,String path,long modeMs,long shutterMs,
                                  long discoverMs,long start,ConnectivityManager cm) throws IOException {
        if(C110PhotoPolicy.path(path,"/DCIM/",CAMERA_IP,false)==null)
            throw new IOException("不允许下载DCIM之外的文件");
        Reply r=get(wifi,path,MAX_JPEG);
        if(r.http!=200||r.body.length<256)throw new IOException("C110 JPEG未准备好");
        byte[] image=r.body;
        int tail=((image[image.length-2]&255)<<8)|(image[image.length-1]&255);
        if(!C110PhotoPolicy.jpegHeaderTail(image,tail))
            throw new IOException("JPEG首尾校验失败");
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(image,0,image.length,opts);
        if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("JPEG尺寸校验失败");
        return new Photo(image,opts.outWidth,opts.outHeight,modeMs,shutterMs,
            discoverMs,r.ms,SystemClock.elapsedRealtime()-start,
            cm.getNetworkCapabilities(wifi)!=null);
    }
}
