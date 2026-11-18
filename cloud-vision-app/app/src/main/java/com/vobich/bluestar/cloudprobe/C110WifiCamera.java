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
        Photo(byte[] jpeg,int width,int height,long modeMs,long shutterMs,
              long discoverMs,long downloadMs,long totalMs,boolean wifiStillPresent){
            this.jpeg=jpeg;this.width=width;this.height=height;
            this.modeMs=modeMs;this.shutterMs=shutterMs;this.discoverMs=discoverMs;
            this.downloadMs=downloadMs;this.totalMs=totalMs;
            this.wifiStillPresent=wifiStillPresent;
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

    /** One OEM LY start-live-video command, not repeated automatically on error. */
    synchronized int startPreview(Context context,Network wifi) throws IOException {
        Reply id=get(wifi,"/?custom=1&cmd=3012",MAX_INDEX);
        if(id.http!=200||!Integer.valueOf(0).equals(C110PhotoPolicy.tagInt(id.text(),"Status")))
            throw new IOException("不是已识别的C110相机，禁止发送预览启动指令");
        Reply commands=get(wifi,"/?custom=1&cmd=3002",MAX_INDEX);
        if(commands.http!=200||!commands.text().contains("<Cmd>1001</Cmd>"))
            throw new IOException("不符合已验证的C110能力集");
        // Starting a live-video session may change firmware camera mode.
        photoModeReady=false;
        Reply response=get(wifi,"/?custom=1&cmd=2015&par=1",MAX_INDEX);
        if(response.http!=200)throw new IOException("预览指令2015 HTTP "+response.http);
        Integer status=C110PhotoPolicy.tagInt(response.text(),"Status");
        return status==null?-4096:status; // -4096 means missing XML Status, not a device error
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
        Set<String> previous=list(wifi);
        long modeMs=0;
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
                        cm.getNetworkCapabilities(wifi)!=null);
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
            path.equals("/?custom=1&cmd=3002")||path.equals("/?custom=1&cmd=3015")||
            path.equals("/?custom=1&cmd=1001")||
            path.equals("/?custom=1&cmd=3001&par=0")||
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
