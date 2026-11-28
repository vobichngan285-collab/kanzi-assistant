#!/usr/bin/env python3
from pathlib import Path
root=Path("cloud-vision-app")
uvc=root/"third_party/AndroidUSBCamera/libuvc"
def patch(p,a,b):
    s=p.read_text()
    assert s.count(a)==1,(str(p),a,s.count(a))
    p.write_text(s.replace(a,b,1))
def write(p,s):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(s)
# Only this isolated CI checkout changes upstream callback metadata.
patch(uvc/"build.gradle","abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'","abiFilters 'arm64-v8a'")
patch(uvc/"src/main/java/com/jiangdg/uvc/IFrameCallback.java",
      "public void onFrame(ByteBuffer frame);",
      "public void onFrame(ByteBuffer frame);\n\tdefault void onFrame(ByteBuffer frame, int width, int height) { onFrame(frame); }")
p=uvc/"src/main/jni/UVCCamera/UVCPreview.cpp"
patch(p,'"(Ljava/nio/ByteBuffer;)V"','"(Ljava/nio/ByteBuffer;II)V"')
patch(p,"env->CallVoidMethod(mFrameCallbackObj, iframecallback_fields.onFrame, buf);",
      "env->CallVoidMethod(mFrameCallbackObj, iframecallback_fields.onFrame, buf, (jint)callback_frame->width, (jint)callback_frame->height);")
patch(p,"if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode)) {",
      "if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode) || (requestMinFps != min_fps) || (requestMaxFps != max_fps)) {")
with (root/"settings.gradle").open("a") as f: f.write("\ninclude ':uvclab'\n")
app=root/"uvclab"
write(app/"build.gradle",'''plugins { id 'com.android.application' }
android {
    namespace 'com.vobich.bluestar.uvclab'
    compileSdk 36
    defaultConfig {
        applicationId 'com.vobich.bluestar.uvclab'
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName '0.1.0'
        ndk { abiFilters 'arm64-v8a' }
    }
    compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
}
dependencies { implementation project(':libuvc'); implementation 'androidx.appcompat:appcompat:1.7.0' }
''')
write(app/"src/main/AndroidManifest.xml",'''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
<uses-feature android:name="android.hardware.usb.host" android:required="true"/>
<uses-feature android:name="android.hardware.camera" android:required="false"/>
<uses-permission android:name="android.permission.CAMERA"/>
<application android:allowBackup="false" android:label="蓝星 UVC 分辨率实验" android:theme="@android:style/Theme.Material.Light.NoActionBar">
<activity android:name=".UvcActivity" android:exported="true" android:screenOrientation="portrait">
<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
</activity></application></manifest>''')
write(app/"src/main/java/com/vobich/bluestar/uvclab/UvcActivity.java",'''package com.vobich.bluestar.uvclab;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.usb.*;
import android.view.*;
import android.widget.*;
import com.jiangdg.usb.USBMonitor;
import com.jiangdg.uvc.UVCCamera;
import com.jiangdg.uvc.IFrameCallback;
import com.jiangdg.utils.Size;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class UvcActivity extends Activity {
    private USBMonitor monitor;
    private TextureView preview;
    private TextView status, logView;
    private Button start, stop;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger epoch=new AtomicInteger();
    private volatile boolean active=false, destroyed=false;
    private volatile USBMonitor.UsbControlBlock block;
    private volatile UVCCamera camera;
    private volatile UsbDevice selected;
    private final StringBuilder log=new StringBuilder();
    private File logFile;

    static final class Mode {
        int w,h,format,lo,hi;
        Mode(int w,int h,int format,int lo,int hi){this.w=w;this.h=h;this.format=format;this.lo=lo;this.hi=hi;}
        long pixels(){return (long)w*h;}
        public String toString(){return w+"x"+h+" px="+pixels()+" "+(format==1?"MJPEG":"YUYV")+" fps_request="+lo+".."+hi;}
    }
    static final class Stats {
        int count, mismatch, minBytes=Integer.MAX_VALUE,maxBytes,w,h;
        long first,last,maxGap;
        synchronized void add(ByteBuffer b,int width,int height,Mode m){
            long now=SystemClock.elapsedRealtime();
            if(width!=m.w||height!=m.h||b==null||b.remaining()!=(long)width*height*2){mismatch++;return;}
            w=width;h=height;
            if(count==0) first=now; else maxGap=Math.max(maxGap,now-last);
            last=now;count++;minBytes=Math.min(minBytes,b.remaining());maxBytes=Math.max(maxBytes,b.remaining());
        }
        synchronized String summary(long elapsed){
            return "actual="+w+"x"+h+" frames="+count+" callback_fps="+String.format(Locale.US,"%.2f",count*1000.0/Math.max(1,elapsed))+" max_gap_ms="+maxGap+" mismatch="+mismatch+" bytes="+(count==0?0:minBytes)+".."+maxBytes;
        }
        synchronized boolean passed(long now){return count>=10 && mismatch==0 && last-first>=8000 && maxGap<=2500 && now-last<=2500;}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        logFile=new File(getFilesDir(),"uvc-diagnostic.txt");
        try{if(logFile.exists()){byte[] b=java.nio.file.Files.readAllBytes(logFile.toPath());log.append(new String(b,java.nio.charset.StandardCharsets.UTF_8));}}catch(Exception ignored){}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,12,12,12);
        status=new TextView(this);status.setTextSize(19);status.setText("蓝星 UVC v0.1.0 — 连接C110后点击开始");
        root.addView(status);
        preview=new TextureView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(this);
        start=new Button(this);start.setText("测试最高像素");buttons.addView(start,new LinearLayout.LayoutParams(0,-2,1));
        stop=new Button(this);stop.setText("停止");buttons.addView(stop,new LinearLayout.LayoutParams(0,-2,1));root.addView(buttons);
        LinearLayout actions=new LinearLayout(this);
        Button copy=new Button(this);copy.setText("复制完整日志");actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Button clear=new Button(this);clear.setText("清空日志");actions.addView(clear,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        ScrollView sv=new ScrollView(this);logView=new TextView(this);logView.setTextSize(12);logView.setText(log.toString());sv.addView(logView);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        copy.setOnClickListener(v->{synchronized(log){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("UVC log",log.toString()));}Toast.makeText(this,"日志已复制",Toast.LENGTH_SHORT).show();});
        clear.setOnClickListener(v->{if(active)return;synchronized(log){log.setLength(0);logFile.delete();}logView.setText("");});
        start.setOnClickListener(v->begin());stop.setOnClickListener(v->cancel("USER_STOP"));
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){}
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s){cancel("SURFACE_DESTROYED");return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture s){}
        });
        monitor=USBMonitor.getInstance(this);
        monitor.setOnDeviceConnectListener(new USBMonitor.OnDeviceConnectListener(){
            public void onAttach(UsbDevice d){if(isVideo(d))say("ATTACH "+device(d));}
            public void onDetach(UsbDevice d){if(selected!=null&&selected.equals(d)){block=null;cancel("DETACH");selected=null;}}
            public void onConnect(UsbDevice d,USBMonitor.UsbControlBlock b,boolean fresh){
                if(!active||selected==null||!selected.equals(d))return;
                block=b;int token=epoch.get();runOnUiThread(()->{if(valid(token)&&preview.isAvailable()){SurfaceTexture s=preview.getSurfaceTexture();worker.submit(()->test(b,s,token));}else cancel("SURFACE_NOT_READY");});
            }
            public void onDisconnect(UsbDevice d,USBMonitor.UsbControlBlock b){say("USB_DISCONNECT "+device(d));}
            public void onCancel(UsbDevice d){if(active)cancel("USB_PERMISSION_DENIED");}
        });
        monitor.register();
        say("APP v0.1.0 model="+android.os.Build.MODEL+" sdk="+android.os.Build.VERSION.SDK_INT+" unthrottled_native_callback=1");
    }
    private boolean valid(int t){return active&&!destroyed&&epoch.get()==t;}
    private void show(String s){runOnUiThread(()->{if(!destroyed)status.setText(s);});}
    private void say(String s){
        synchronized(log){
            String line=System.currentTimeMillis()+" "+s+"\\n";log.append(line);
            try(FileOutputStream out=new FileOutputStream(logFile,true)){out.write(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();}catch(Exception ignored){}
            String display=log.substring(Math.max(0,log.length()-16000));
            runOnUiThread(()->{if(!destroyed)logView.setText(display);});
        }
    }
    private static String device(UsbDevice d){return d==null?"null":String.format(Locale.US,"vid=%04x pid=%04x",d.getVendorId(),d.getProductId());}
    private static boolean isVideo(UsbDevice d){if(d==null)return false;if(d.getDeviceClass()==14)return true;for(int i=0;i<d.getInterfaceCount();i++)if(d.getInterface(i).getInterfaceClass()==14)return true;return false;}
    private void begin(){
        if(active||destroyed)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},10);return;}
        if(!preview.isAvailable()){show("预览尚未就绪，请重试");return;}
        List<UsbDevice> ds=new ArrayList<>();
        for(UsbDevice d:((UsbManager)getSystemService(USB_SERVICE)).getDeviceList().values())if(isVideo(d))ds.add(d);
        if(ds.size()!=1){say("VIDEO_DEVICE_COUNT="+ds.size());show("请只连接一台USB视频相机");return;}
        selected=ds.get(0);active=true;epoch.incrementAndGet();start.setEnabled(false);
        say("BEGIN "+device(selected));show("等待USB授权");
        try{monitor.requestPermission(selected);}catch(Exception e){cancel("PERMISSION_ERROR "+e);}
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)begin();}
    private void cancel(String reason){
        if(!active)return;active=false;epoch.incrementAndGet();say(reason);show("已停止："+reason);
        worker.submit(()->{release();runOnUiThread(()->{if(!destroyed)start.setEnabled(true);});});
    }
    private void release(){UVCCamera c=camera;camera=null;if(c!=null){try{c.destroy();}catch(Throwable e){say("RELEASE_ERROR "+e);}}}
    private static int u(byte[] b,int i){return b[i]&255;}
    private static int u16(byte[] b,int i){return u(b,i)|(u(b,i+1)<<8);}
    private static long u32(byte[] b,int i){return (long)u(b,i)|((long)u(b,i+1)<<8)|((long)u(b,i+2)<<16)|((long)u(b,i+3)<<24);}
    private static void add(List<Mode> ms,Mode m){if(m.w<=0||m.h<=0||m.lo<=0||m.hi<m.lo)return;for(Mode a:ms)if(a.w==m.w&&a.h==m.h&&a.format==m.format&&a.lo==m.lo&&a.hi==m.hi)return;ms.add(m);}
    private List<Mode> descriptors(byte[] b){
        List<Mode> modes=new ArrayList<>();int format=-1,iface=-1,sub=-1;long declaredMax=0;
        for(int p=0;p+2<=b.length;){
            int len=u(b,p),type=u(b,p+1);if(len<2||p+len>b.length){say("DESCRIPTOR_TRUNCATED offset="+p);break;}
            if(type==4&&len>=9){iface=u(b,p+5);sub=u(b,p+6);format=-1;}
            if(type==36&&iface==14&&sub==2&&len>=3){
                int st=u(b,p+2);
                if(st==4&&len>=21){String guid=new String(b,p+5,4,java.nio.charset.StandardCharsets.US_ASCII);format=guid.equals("YUY2")||guid.equals("YUYV")?0:-1;say("FORMAT uncompressed guid="+guid);}
                else if(st==6){format=1;say("FORMAT MJPEG");}
                else if(st==0x10||st==0x12){format=-1;say("FORMAT unsupported_frame_based_or_stream subtype="+st);}
                if((st==5||st==7||st==0x11)&&len>=26){
                    int w=u16(b,p+5),h=u16(b,p+7);
                    if(w>0&&h>0)declaredMax=Math.max(declaredMax,(long)w*h);
                    if(st==0x11){say("DECLARED unsupported frame_based "+w+"x"+h);p+=len;continue;}
                    int n=u(b,p+25);List<Long> ints=new ArrayList<>();
                    if(n==0){if(len>=38){ints.add(u32(b,p+26));ints.add(u32(b,p+30));say("INTERVAL_RANGE step_100ns="+u32(b,p+34));}}
                    else for(int j=0;j<n&&26+4*j+4<=len;j++)ints.add(u32(b,p+26+4*j));
                    say("DECLARED "+w+"x"+h+" format="+format+" intervals_100ns="+ints);
                    if(format>=0){
                        for(long interval:ints)if(interval>0){double rate=10000000.0/interval;int lo=Math.max(1,(int)Math.floor(rate)),hi=Math.max(lo,(int)Math.ceil(rate));add(modes,new Mode(w,h,format,lo,hi));}
                        if(n==0&&!ints.isEmpty()){long fast=ints.get(0),slow=ints.get(ints.size()-1);if(fast>0&&slow>0)add(modes,new Mode(w,h,format,Math.max(1,(int)Math.floor(10000000.0/slow)),Math.max(1,(int)Math.ceil(10000000.0/fast))));}
                    }
                }
            }
            p+=len;
        }
        say("DECLARED_MAX_PIXELS_PARSED="+declaredMax+" raw_descriptor_bytes="+b.length);
        return modes;
    }
    private void test(USBMonitor.UsbControlBlock b,SurfaceTexture surface,int token){
        if(!valid(token))return;
        boolean keep=false;
        try{
            List<Mode> modes=descriptors(b.getRawDescriptors());
            camera=new UVCCamera();camera.open(b);
            say("LIBUVC_FORMATS "+camera.getSupportedSize());
            for(int f=0;f<=1;f++)for(Size s:camera.getSupportedSizeList(f)){
                boolean found=false;for(Mode m:modes)if(m.w==s.width&&m.h==s.height&&m.format==f)found=true;
                if(!found){say("LIBUVC_ONLY "+s.width+"x"+s.height+" format="+f+" rates_unknown");add(modes,new Mode(s.width,s.height,f,1,61));}
            }
            modes.sort(Comparator.comparingLong(Mode::pixels).reversed().thenComparing(Comparator.comparingInt((Mode m)->m.format).reversed()).thenComparing(Comparator.comparingInt((Mode m)->m.hi).reversed()));
            say("CANDIDATES="+modes.size());
            for(Mode m:modes){
                if(!valid(token))break;
                say("TRY "+m);show("正在验证 "+m.w+"×"+m.h+" "+(m.format==1?"MJPEG":"YUYV"));
                Stats stats=new Stats();final boolean[] measuring={false};
                try{
                    camera.setPreviewSize(m.w,m.h,m.lo,m.hi,m.format,1.0f);
                    camera.setPreviewTexture(surface);
                    camera.setFrameCallback(new IFrameCallback(){
                        public void onFrame(ByteBuffer f){}
                        public void onFrame(ByteBuffer f,int w,int h){synchronized(stats){if(measuring[0]&&valid(token))stats.add(f,w,h,m);}}
                    },UVCCamera.PIXEL_FORMAT_YUV);
                    camera.startPreview();
                    for(int i=0;i<30&&valid(token);i++)Thread.sleep(100);
                    long begin=SystemClock.elapsedRealtime();
                    synchronized(stats){measuring[0]=true;}
                    while(valid(token)&&SystemClock.elapsedRealtime()-begin<10000)Thread.sleep(100);
                    long now=SystemClock.elapsedRealtime();
                    boolean pass=valid(token)&&stats.passed(now);
                    say("RESULT "+(pass?"PASS":"FAIL")+" "+m+" "+stats.summary(now-begin));
                    if(pass){
                        keep=true;show("最高已通过： "+m.w+"×"+m.h+"，"+m.pixels()+" 像素");
                        say("HIGHEST_TESTED_PASS "+m+" "+stats.summary(now-begin));return;
                    }
                }catch(Throwable e){say("MODE_ERROR "+m+" "+e);}
                finally{if(!keep){try{camera.stopPreview();}catch(Throwable e){say("STOP_ERROR "+e);}}}
            }
            if(valid(token)){say("NO_STABLE_SUPPORTED_MODE");show("未找到稳定规格，请复制日志");}
        }catch(Throwable e){say("TEST_ERROR "+e);show("实验失败，请复制日志");}
        finally{if(!keep){release();if(epoch.get()==token){active=false;runOnUiThread(()->{if(!destroyed)start.setEnabled(true);});}}}
    }
    @Override protected void onDestroy(){destroyed=true;active=false;epoch.incrementAndGet();worker.submit(()->{release();if(monitor!=null)monitor.destroy();});worker.shutdown();super.onDestroy();}
}
''')
print("Generated isolated UVC diagnostic module; no OCR or network dependency")
