package com.vobich.bluestar.c110photosize;

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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal native C110 photo-size trial. No video, cloud, OCR, TTS, SD delete,
 * format, reset, file write to phone or other camera setting commands.
 *
 * Original SJCAM Zone 6.7.3.16 classes5.dex:
 *  - assets/cparams/580-C110+$1.0.7/1.0.8/1.1.2.json
 *  - C110+ Photo Size cmd=1002:
 *    [20M 5888x3312,16M,14M,12M,10M,8M,5M,3M 2560x1440,2M 1920x1088]
 *  - LYCamera.setting -> Command.setCmd(cmd), setPar(value) -> getRequestUrl
 *
 * The list index 7 is a C110+ OEM CONFIG CANDIDATE. Firmware on the
 * user's unit has not been verified. The real 5888x3312 JPEG baseline
 * plus an explicit user confirmation are REQUIRED before cmd1002/par7.
 * The photo's actual SD-resident original JPEG dimensions decide success.
 */
final class C110NativePhotoCamera {
    static final String IP="192.168.1.254";
    static final int PHOTO_2K_CANDIDATE_INDEX=7;
    private boolean verifiedC110Plus20MBaseline;
    private static final int MAX_TEXT=1024*1024, MAX_JPEG=32*1024*1024;

    static final class ProbeResult {
        final boolean c110,cmd1002,cmd1001;
        final Integer settingsStatus;
        final int settingsBytes;
        final boolean settingsHint1002;
        ProbeResult(boolean c110,boolean cmd1002,boolean cmd1001,
                    Integer settingsStatus,int settingsBytes,boolean hint){
            this.c110=c110;this.cmd1002=cmd1002;this.cmd1001=cmd1001;
            this.settingsStatus=settingsStatus;this.settingsBytes=settingsBytes;
            this.settingsHint1002=hint;
        }
    }
    static final class SetResult {
        final int status;
        final long elapsedMs;
        final boolean httpOk;
        SetResult(boolean ok,int code,long ms){httpOk=ok;status=code;elapsedMs=ms;}
    }
    static final class PhotoResult {
        final int width,height,byteCount,baselineCount;
        final long shutterMs,discoverMs,downloadMs,totalMs;
        PhotoResult(int width,int height,int bytes,int baselineCount,
                    long shutterMs,long discoverMs,long downloadMs,long totalMs){
            this.width=width;this.height=height;this.byteCount=bytes;
            this.baselineCount=baselineCount;this.shutterMs=shutterMs;
            this.discoverMs=discoverMs;this.downloadMs=downloadMs;this.totalMs=totalMs;
        }
        boolean exactly2k(){return width==2560&&height==1440;}
    }
    private static final class Reply {
        final int http; final byte[] data;final long ms;
        Reply(int http,byte[] data,long ms){this.http=http;this.data=data;this.ms=ms;}
        String text(){return new String(data,StandardCharsets.UTF_8);}
    }

    private static boolean isC110Family(String text){
        Matcher m=Pattern.compile("<String>([^<]{1,128})</String>",
            Pattern.CASE_INSENSITIVE).matcher(text);
        boolean identified=false;
        while(m.find()){
            String name=m.group(1).toUpperCase(Locale.ROOT);
            // Original app reports C110+ variants as C110_PRO / C110+;
            // some devices only report C110.  Do not infer exact model
            // from this string alone: require a 20M JPEG baseline.
            if(name.contains("C110"))identified=true;
        }
        return identified;
    }
    private static boolean successful(Reply reply){
        return reply.http==200&&Integer.valueOf(0).equals(
            PhotoSizePolicy.integerTag(reply.text(),"Status"));
    }
    private static boolean advertised(String xml,int cmd){
        return Pattern.compile("<Cmd>\\s*"+cmd+"\\s*</Cmd>",Pattern.CASE_INSENSITIVE)
            .matcher(xml).find();
    }
    private static void requireUsableC110Family(Reply identity)throws IOException{
        if(!successful(identity)||!isC110Family(identity.text()))
            throw new IOException("3012未识别C110系列设备，拒绝设置或拍照");
    }
    private static void requirePhotoCommands(Reply list,boolean needSetting)throws IOException{
        if(list.http!=200||!advertised(list.text(),1001)||
           !advertised(list.text(),3001)||
           (needSetting&&!advertised(list.text(),1002)))
            throw new IOException("3002未声明需要的拍照指令；未执行更改");
    }

    private static Network findCameraWifi(Context activity)throws IOException{
        ConnectivityManager cm=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)throw new IOException("Android网络服务不可用");
        ArrayList<Network> candidates=new ArrayList<>();
        for(Network network:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
            LinkProperties props=cm.getLinkProperties(network);
            if(props!=null){
                for(RouteInfo route:props.getRoutes())
                    if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address&&
                       IP.equals(route.getGateway().getHostAddress())){
                        candidates.add(0,network);break;
                    }
            }
            if(!candidates.contains(network))candidates.add(network);
        }
        // A secondary local-only Android Wi-Fi may have no default route:
        // verify on its own Network using the harmless read-only 3012.
        for(Network network:candidates){
            try{
                Reply response=request(network,"/?custom=1&cmd=3012",MAX_TEXT);
                if(successful(response)&&isC110Family(response.text()))
                    return network;
            }catch(IOException ignored){}
        }
        throw new IOException("未找到真实C110+相机网络：请先在系统Wi-Fi连接相机热点");
    }

    ProbeResult probe(Context ctx)throws Exception {
        Network camera=findCameraWifi(ctx);
        Reply identity=request(camera,"/?custom=1&cmd=3012",MAX_TEXT);
        requireUsableC110Family(identity);
        Reply support=request(camera,"/?custom=1&cmd=3002",MAX_TEXT);
        boolean sizeCommand=support.http==200&&advertised(support.text(),1002);
        boolean shutter=support.http==200&&advertised(support.text(),1001);
        Reply values=null;
        try{
            // OEM LYCamera.getSettingDefaultValue uses cmd3014 to read settings.
            values=request(camera,"/?custom=1&cmd=3014",MAX_TEXT);
        }catch(IOException ignored){}
        return new ProbeResult(true,sizeCommand,shutter,
            values==null?null:PhotoSizePolicy.integerTag(values.text(),"Status"),
            values==null?0:values.data.length,
            values!=null&&advertised(values.text(),1002));
    }

    PhotoResult takeBaselineOriginal(Context ctx)throws Exception{
        verifiedC110Plus20MBaseline=false;
        PhotoResult baseline=takeOneOriginal(ctx);
        verifiedC110Plus20MBaseline=baseline.width==5888&&baseline.height==3312;
        return baseline;
    }

    SetResult select2kCandidate(Context ctx)throws Exception{
        if(!verifiedC110Plus20MBaseline)
            throw new IOException("尚未从本机SD卡读到5888×3312基线，禁止发送C110+尺寸设置");
        verifiedC110Plus20MBaseline=false; // one-use authorization
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        Network camera=findCameraWifi(ctx);
        requireUsableC110Family(request(camera,"/?custom=1&cmd=3012",MAX_TEXT));
        requirePhotoCommands(request(camera,"/?custom=1&cmd=3002",MAX_TEXT),true);
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        // THE ONLY setting write permitted in this entire experimental app.
        // Deliberate one-shot, original C110+ LYCamera.setting cmd/par format.
        Reply result=request(camera,"/?custom=1&cmd=1002&par=7",MAX_TEXT);
        Integer status=PhotoSizePolicy.integerTag(result.text(),"Status");
        return new SetResult(result.http==200,status==null?-4096:status,result.ms);
    }

    PhotoResult takeOneOriginal(Context ctx)throws Exception {
        long started=SystemClock.elapsedRealtime();
        Network wifi=findCameraWifi(ctx);
        requireUsableC110Family(request(wifi,"/?custom=1&cmd=3012",MAX_TEXT));
        requirePhotoCommands(request(wifi,"/?custom=1&cmd=3002",MAX_TEXT),false);
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        // Proven C110 flow: switch photo mode before baseline directory scan.
        Reply mode=request(wifi,"/?custom=1&cmd=3001&par=0",MAX_TEXT);
        if(!successful(mode))throw new IOException("3001/par0切照片模式失败；未拍照");
        Set<String> before=listJpegs(wifi);
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        final long shutterAt=SystemClock.elapsedRealtime();
        // Exactly one REAL hardware shutter. Never retry 1001.
        Reply shutter=request(wifi,"/?custom=1&cmd=1001",MAX_TEXT);
        if(!successful(shutter))throw new IOException("1001真实快门失败，不会重拍");
        long discoverMs=-1;
        while(SystemClock.elapsedRealtime()-shutterAt<20000){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            Set<String> delta=listJpegs(wifi);
            delta.removeAll(before);
            if(delta.size()>1)
                throw new IOException("出现多于一张新图，无法确认本次照片；停止而不猜测");
            if(delta.size()==1){
                if(discoverMs<0)discoverMs=SystemClock.elapsedRealtime()-shutterAt;
                String fresh=delta.iterator().next();
                Reply jpeg=null;
                try{
                    jpeg=request(wifi,fresh,MAX_JPEG);
                    if(jpeg.http==200&&PhotoSizePolicy.validJpeg(jpeg.data)){
                        BitmapFactory.Options bounds=new BitmapFactory.Options();
                        bounds.inJustDecodeBounds=true; // do not allocate full bitmap
                        BitmapFactory.decodeByteArray(jpeg.data,0,jpeg.data.length,bounds);
                        if(bounds.outWidth>0&&bounds.outHeight>0)
                            return new PhotoResult(bounds.outWidth,bounds.outHeight,
                                jpeg.data.length,before.size(),shutter.ms,
                                discoverMs,jpeg.ms,SystemClock.elapsedRealtime()-started);
                    }
                }catch(IOException notWrittenYet){
                    // The only retry is reading the SAME JPEG as it is written.
                    // The shutter and 1002 setting command are NEVER retried.
                }
            }
            Thread.sleep(350);
        }
        throw new IOException("20秒内未读到完整新JPEG；没有重复拍照");
    }

    private static Set<String> listJpegs(Network wifi)throws IOException{
        LinkedHashSet<String> files=new LinkedHashSet<>();
        try{
            Reply media=request(wifi,"/?custom=1&cmd=3015",MAX_TEXT);
            if(media.http==200)files.addAll(PhotoSizePolicy.jpegPaths(
                media.text(),"/DCIM/"));
        }catch(IOException ignored){}
        LinkedHashSet<String> scan=new LinkedHashSet<>(
            Arrays.asList("/DCIM/PHOTO/","/DCIM/"));
        for(String dir:new ArrayList<>(scan)){
            Reply contents=request(wifi,dir,MAX_TEXT);
            if(contents.http!=200)continue;
            files.addAll(PhotoSizePolicy.jpegPaths(contents.text(),dir));
            if(dir.equals("/DCIM/"))
                for(String sub:PhotoSizePolicy.directoryPaths(contents.text(),dir))
                    if(scan.size()<7)scan.add(sub);
        }
        int extras=0;
        for(String dir:scan){
            if(dir.equals("/DCIM/PHOTO/")||dir.equals("/DCIM/"))continue;
            if(extras++>=5)break;
            Reply contents=request(wifi,dir,MAX_TEXT);
            if(contents.http==200)files.addAll(PhotoSizePolicy.jpegPaths(contents.text(),dir));
        }
        return files;
    }

    private static Reply request(Network wifi,String path,int maxBytes)throws IOException{
        boolean permitted=path.equals("/?custom=1&cmd=3012")||
            path.equals("/?custom=1&cmd=3002")||
            path.equals("/?custom=1&cmd=3014")||
            path.equals("/?custom=1&cmd=3015")||
            path.equals("/?custom=1&cmd=3001&par=0")||
            path.equals("/?custom=1&cmd=1001")||
            path.equals("/?custom=1&cmd=1002&par=7");
        boolean file=path.startsWith("/DCIM/")&&
            !path.contains("..")&&!path.contains("\\")&&
            !path.contains("?")&&!path.contains("#");
        if(!permitted&&!file)throw new IOException("相机URL不在本实验白名单");
        long start=SystemClock.elapsedRealtime();
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)wifi.openConnection(
                new URL("http",IP,80,path));
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept-Encoding","identity");
            int code=connection.getResponseCode();
            if(code!=200)return new Reply(code,new byte[0],
                SystemClock.elapsedRealtime()-start);
            try(InputStream in=connection.getInputStream();
                ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] chunk=new byte[16384];int length;
                while((length=in.read(chunk))!=-1){
                    if(Thread.currentThread().isInterrupted())
                        throw new IOException("本次实验已取消");
                    if(bytes.size()+length>maxBytes)
                        throw new IOException("相机响应超出安全长度限制");
                    bytes.write(chunk,0,length);
                }
                return new Reply(code,bytes.toByteArray(),
                    SystemClock.elapsedRealtime()-start);
            }
        }finally{if(connection!=null)connection.disconnect();}
    }
}
