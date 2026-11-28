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
        applicationId 'com.vobich.bluestar.uvcnative'
        minSdk 26
        targetSdk 35
        versionCode 3
        versionName '0.1.2'
        ndk { abiFilters 'arm64-v8a' }
    }
    compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
}
dependencies {
    implementation project(':libuvc')
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation platform('org.jetbrains.kotlin:kotlin-bom:1.8.22')
}
configurations.configureEach {
    resolutionStrategy.force 'org.jetbrains.kotlin:kotlin-stdlib:1.8.22', 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22', 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22'
}
''')
write(app/"src/main/AndroidManifest.xml",'''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
<uses-feature android:name="android.hardware.usb.host" android:required="true"/>
<uses-feature android:name="android.hardware.camera" android:required="false"/>
<uses-permission android:name="android.permission.CAMERA"/>
<application android:allowBackup="false" android:label="蓝星 UVC 原生探测" android:theme="@android:style/Theme.Material.Light.NoActionBar">
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
    private volatile boolean active=false, destroyed=false, deepOnly=true;
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
        status=new TextView(this);status.setTextSize(19);status.setText("蓝星 UVC v0.1.2 — 连接C110后点击原生探测");
        root.addView(status);
        preview=new TextureView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(this);
        start=new Button(this);start.setText("测试最高像素");buttons.addView(start,new LinearLayout.LayoutParams(0,-2,1));
        stop=new Button(this);stop.setText("停止");buttons.addView(stop,new LinearLayout.LayoutParams(0,-2,1));root.addView(buttons);
        Button deep=new Button(this);deep.setText("原生只读探测（无需预览）");root.addView(deep);
        deep.setOnClickListener(v->{if(active){show("请先停止当前测试，再点击原生探测");say("BUSY_DEEP_REQUEST");}else{deepOnly=true;begin();}});
        LinearLayout actions=new LinearLayout(this);
        Button copy=new Button(this);copy.setText("复制完整日志");actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Button clear=new Button(this);clear.setText("清空日志");actions.addView(clear,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        ScrollView sv=new ScrollView(this);logView=new TextView(this);logView.setTextSize(12);logView.setText(log.toString());sv.addView(logView);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        copy.setOnClickListener(v->{synchronized(log){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("UVC log",log.toString()));}Toast.makeText(this,"日志已复制",Toast.LENGTH_SHORT).show();});
        clear.setOnClickListener(v->{if(active)return;synchronized(log){log.setLength(0);logFile.delete();}logView.setText("");});
        start.setOnClickListener(v->{if(!active){deepOnly=false;begin();}});stop.setOnClickListener(v->cancel("USER_STOP"));
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
        say("APP v0.1.2 model="+android.os.Build.MODEL+" sdk="+android.os.Build.VERSION.SDK_INT+" unthrottled_native_callback=1");
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
            if(deepOnly){
                show("正在原生只读探测");
                if(selected.getVendorId()!=0x0603||selected.getProductId()!=0x8612){
                    say("NATIVE_SKIP unexpected_device");show("此实验仅支持C110");return;
                }
                byte[] raw=b.getRawDescriptors();
                UvcProbe.Parsed p=new UvcProbe.Parsed();
                say("NATIVE_DESCRIPTOR bytes="+raw.length+" hex="+UvcProbe.hex(raw,0,raw.length));
                UvcProbe.parse(raw,p,this::say,true);
                if(p.uvcVersion!=0x0100||p.xus!=1||p.vsHeaders!=1||!p.interfaces.contains(0)||!p.interfaces.contains(1)||!p.queries.containsKey("0:5:16")||!p.queries.containsKey("1:0:1")){
                    say("NATIVE_SKIP unexpected_descriptor");show("设备描述符与已验证C110不符");return;
                }
                say("NATIVE_BEGIN GET_ONLY no_SET_CUR no_stream no_vendor_type");
                camera=new UVCCamera();camera.openReadOnly(b);
                say("NATIVE_OPEN_OK");
                for(String line:camera.readOnlyC110Report().split("\\n"))if(!line.isEmpty())say(line);
                say("NATIVE_END");
                if(valid(token))show("原生探测完成，请复制完整日志");
                return;
            }
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

write(app/"src/main/java/com/vobich/bluestar/uvclab/UvcProbe.java",'''package com.vobich.bluestar.uvclab;
import android.hardware.usb.*;
import com.jiangdg.usb.USBMonitor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

final class UvcProbe {
    static int u(byte[] b,int p){return b[p]&255;}
    static int w(byte[] b,int p){return u(b,p)|(u(b,p+1)<<8);}
    static long d(byte[] b,int p){return (long)u(b,p)|((long)u(b,p+1)<<8)|((long)u(b,p+2)<<16)|((long)u(b,p+3)<<24);}
    static String hex(byte[] b,int a,int n){
        StringBuilder s=new StringBuilder();for(int i=a;i<a+n;i++)s.append(String.format(Locale.US,"%02x",u(b,i)));return s.toString();
    }
    static String guid(byte[] b,int p){return String.format(Locale.US,"%08x-%04x-%04x-%s-%s",d(b,p),w(b,p+4),w(b,p+6),hex(b,p+8,2),hex(b,p+10,6));}
    static String four(byte[] b,int p){StringBuilder s=new StringBuilder();for(int i=0;i<4;i++){int c=u(b,p+i);s.append(c>=32&&c<=126?(char)c:'.');}return s.toString();}
    static final class Query {
        final String name; final int iface,unit,selector,knownLength; final boolean xu;
        Query(String n,int i,int e,int s,int len,boolean x){name=n;iface=i;unit=e;selector=s;knownLength=len;xu=x;}
        String key(){return iface+":"+unit+":"+selector;}
    }
    static final class Parsed {
        final Map<String,Query> queries=new LinkedHashMap<>();
        final Set<Integer> interfaces=new LinkedHashSet<>();
        int uvcVersion=0,stillHeaders=0,stillDescriptors=0,xus=0,vsHeaders=0;long maxStill=0,maxVideo=0;
        void add(Query q){queries.putIfAbsent(q.key(),q);}
    }
    static byte[] read(UsbDeviceConnection c,int type,int request,int value,int index,int len){
        if((type&0x80)==0||len<=0||len>16384)throw new IllegalArgumentException("read only");
        byte[] a=new byte[len];int n=c.controlTransfer(type,request,value,index,a,len,500);return n<0?null:Arrays.copyOf(a,n);
    }
    static byte[] get(UsbDeviceConnection c,Query q,int request,int n,Consumer<String> out){
        byte[] a=read(c,0xa1,request,q.selector<<8,(q.unit<<8)|q.iface,n);
        out.accept("GET "+q.name+" iface="+q.iface+" unit="+q.unit+" selector="+q.selector+" req=0x"+Integer.toHexString(request)+" expected="+n+" returned="+(a==null?-1:a.length)+" hex="+(a==null?"STALL_OR_ERROR":hex(a,0,a.length)));return a;
    }
    static void parse(byte[] b,Parsed p,Consumer<String> out,boolean collect){
        int iface=-1,cls=-1,sub=-1,alt=-1,format=-1;
        for(int i=0;i+2<=b.length;){
            int n=u(b,i),t=u(b,i+1);if(n<2||i+n>b.length){out.accept("DESCRIPTOR_MALFORMED offset="+i);break;}
            if(t==1&&n>=18)out.accept("DEVICE bcdUSB=0x"+Integer.toHexString(w(b,i+2))+" bcdDevice=0x"+Integer.toHexString(w(b,i+12))+" configurations="+u(b,i+17)+" (USB version not negotiated speed)");
            if(t==2&&n>=9)out.accept("CONFIG value="+u(b,i+5)+" total="+w(b,i+2)+" interfaces="+u(b,i+4)+" bmAttributes=0x"+Integer.toHexString(u(b,i+7))+" bMaxPower_raw="+u(b,i+8));
            if(t==4&&n>=9){iface=u(b,i+2);alt=u(b,i+3);cls=u(b,i+5);sub=u(b,i+6);format=-1;out.accept("INTERFACE "+iface+" alt="+alt+" class="+cls+" subclass="+sub+" protocol="+u(b,i+7));if(collect&&cls==14&&alt==0)p.interfaces.add(iface);}
            if(t==5&&n>=7){int raw=w(b,i+4);out.accept("ENDPOINT iface="+iface+" alt="+alt+" address=0x"+Integer.toHexString(u(b,i+2))+" transfer_type="+(u(b,i+3)&3)+" wMaxPacketSize_raw="+raw+" packet_bytes="+(raw&2047)+" high_speed_transactions_code="+((raw>>11)&3)+" interval="+u(b,i+6));}
            if(t==48&&n>=6)out.accept("SS_COMPANION iface="+iface+" alt="+alt+" maxBurst="+u(b,i+2)+" attributes="+u(b,i+3)+" bytesPerInterval="+w(b,i+4));
            if(t==36&&cls==14&&n>=3){
                int st=u(b,i+2);
                if(sub==1){
                    if(st==1&&n>=12){int v=w(b,i+3);if(collect)p.uvcVersion=v;out.accept("VC_HEADER uvc=0x"+Integer.toHexString(v)+" total="+w(b,i+5));}
                    if(st==2&&n>=8){int terminal=w(b,i+4);out.accept("INPUT_TERMINAL id="+u(b,i+3)+" type=0x"+Integer.toHexString(terminal));if(terminal==0x0201&&n>=15&&15+u(b,i+14)<=n)out.accept("CAMERA_TERMINAL bmControls="+hex(b,i+15,u(b,i+14)));}
                    if(st==5&&n>=8&&8+u(b,i+7)<=n)out.accept("PROCESSING_UNIT id="+u(b,i+3)+" bmControls="+hex(b,i+8,u(b,i+7)));
                    if(st==6&&n>=24){
                        int unit=u(b,i+3),pins=u(b,i+21),o=22+pins;
                        if(o<n){int size=u(b,i+o);if(o+1+size<n){
                            if(collect)p.xus++;
                            out.accept("EXTENSION_UNIT iface="+iface+" unit="+unit+" guid="+guid(b,i+4)+" numControls="+u(b,i+20)+" bmControls="+hex(b,i+o+1,size));
                            if(collect)for(int k=0;k<size*8&&k<255;k++)if((u(b,i+o+1+k/8)&(1<<(k%8)))!=0)p.add(new Query("XU",iface,unit,k+1,0,true));
                        }else out.accept("XU_INVALID_BITMAP");}
                    }
                }else if(sub==2){
                    if(st==1&&n>=13){
                        int method=u(b,i+9),trigger=u(b,i+10);
                        if(collect){p.vsHeaders++;if(method>0)p.stillHeaders++;int streamLen=p.uvcVersion>=0x0150?48:p.uvcVersion>=0x0110?34:26;
                            p.add(new Query("VS_PROBE",iface,0,1,streamLen,false));p.add(new Query("VS_COMMIT",iface,0,2,streamLen,false));
                            if(method==2||method==3){p.add(new Query("STILL_PROBE",iface,0,3,11,false));p.add(new Query("STILL_COMMIT",iface,0,4,11,false));}}
                        out.accept("VS_HEADER iface="+iface+" formats="+u(b,i+3)+" endpoint=0x"+Integer.toHexString(u(b,i+6))+" still_method="+method+" trigger_support="+trigger+" trigger_usage="+u(b,i+11));
                    }
                    if(st==3&&n>=6){
                        int count=u(b,i+4),end=5+4*count;if(end<n){if(collect)p.stillDescriptors++;out.accept("STILL_DESCRIPTOR iface="+iface+" endpoint=0x"+Integer.toHexString(u(b,i+3))+" patterns="+count);
                            for(int k=0;k<count;k++){int ww=w(b,i+5+k*4),hh=w(b,i+7+k*4);long px=(long)ww*hh;if(collect)p.maxStill=Math.max(p.maxStill,px);out.accept("STILL_SIZE index="+(k+1)+" "+ww+"x"+hh+" pixels="+px);}
                            int c=u(b,i+end);if(end+1+c<=n)out.accept("STILL_COMPRESSION indices="+hex(b,i+end+1,c));
                        }else out.accept("STILL_DESCRIPTOR_INVALID");
                    }
                    if((st==4||st==16)&&n>=27){format=u(b,i+3);out.accept("VIDEO_FORMAT index="+format+" subtype="+st+" guid="+guid(b,i+5)+" fourcc="+four(b,i+5)+" frames="+u(b,i+4)+" bitsPerPixel="+u(b,i+21));}
                    if(st==6&&n>=11){format=u(b,i+3);out.accept("VIDEO_FORMAT index="+format+" MJPEG frames="+u(b,i+4));}
                    if((st==5||st==7||st==17)&&n>=26){
                        int ww=w(b,i+5),hh=w(b,i+7),k=u(b,i+(st==17?21:25));long px=(long)ww*hh;if(collect)p.maxVideo=Math.max(p.maxVideo,px);
                        List<Long> intervals=new ArrayList<>();for(int z=0;z<(k==0?3:k)&&26+4*z+4<=n;z++)intervals.add(d(b,i+26+4*z));
                        out.accept("VIDEO_FRAME format_index="+format+" frame_index="+u(b,i+3)+" "+ww+"x"+hh+" pixels="+px+" caps="+u(b,i+4)+" min_bps="+d(b,i+9)+" max_bps="+d(b,i+13)+" default_interval="+d(b,i+(st==17?17:21))+" intervals_100ns="+intervals+" continuous="+(k==0));
                    }
                }
            }
            i+=n;
        }
    }
    static String speed(UsbDevice dev){
        try{
            String[] parts=dev.getDeviceName().split("/");int bus=Integer.parseInt(parts[parts.length-2]),num=Integer.parseInt(parts[parts.length-1]);
            File[] files=new File("/sys/bus/usb/devices").listFiles();if(files!=null)for(File f:files){
                try{if(Integer.parseInt(txt(f,"busnum"))==bus&&Integer.parseInt(txt(f,"devnum"))==num)return txt(f,"speed")+" Mbps (sysfs)";}catch(Exception ignored){}
            }
        }catch(Exception ignored){}
        return "UNKNOWN (Android sysfs unavailable; not inferred from bcdUSB)";
    }
    static String txt(File f,String name)throws IOException{return new String(java.nio.file.Files.readAllBytes(new File(f,name).toPath()),StandardCharsets.US_ASCII).trim();}
    static void run(USBMonitor.UsbControlBlock block,UsbDevice dev,Consumer<String> out,BooleanSupplier running){
        UsbDeviceConnection c=block.getConnection();
        if(c==null){out.accept("NO_USB_CONNECTION");return;}
        byte[] raw=block.getRawDescriptors();out.accept("DEEP_BEGIN GET_ONLY no_vendor_requests no_SET_CUR no_stream");
        out.accept("LINK_SPEED "+speed(dev));
        out.accept("RAW_DESCRIPTOR bytes="+raw.length+" hex="+hex(raw,0,raw.length));
        Parsed p=new Parsed();parse(raw,p,out,true);
        // Read all standard configuration descriptors, including inactive configurations.
        // Only query class controls from the raw active descriptor view.
        try{
            byte[] device=read(c,0x80,6,0x0100,0,18);
            if(device!=null&&device.length>=18){
                out.accept("GET_DEVICE_DESCRIPTOR "+hex(device,0,device.length));
                for(int k=0;k<u(device,17)&&running.getAsBoolean();k++){
                    byte[] hdr=read(c,0x80,6,0x0200|k,0,9);
                    if(hdr==null||hdr.length<9){out.accept("GET_CONFIG "+k+" unavailable");continue;}
                    int len=w(hdr,2);if(len<9||len>16384){out.accept("GET_CONFIG "+k+" size_out_of_bound="+len);continue;}
                    byte[] full=read(c,0x80,6,0x0200|k,0,len);
                    if(full!=null){out.accept("GET_CONFIG "+k+" bytes="+full.length+" expected="+len+" hex="+hex(full,0,full.length));Parsed other=new Parsed();parse(full,other,out,true);out.accept("CONFIG_SUMMARY index="+k+" max_video_px="+other.maxVideo+" max_still_px="+other.maxStill+" still_headers="+other.stillHeaders+" xu="+other.xus);}
                }
            }
        }catch(Exception e){out.accept("CONFIG_READ_ERROR "+e);}
        out.accept("ACTIVE_SUMMARY max_video_px="+p.maxVideo+" max_still_px="+p.maxStill+" vs_headers="+p.vsHeaders+" still_headers="+p.stillHeaders+" still_descriptors="+p.stillDescriptors+" extension_units="+p.xus+" declared_queries="+p.queries.size());
        List<UsbInterface> claimed=new ArrayList<>();
        try{
            for(int k=0;k<dev.getInterfaceCount()&&running.getAsBoolean();k++){
                UsbInterface f=dev.getInterface(k);
                if(f.getAlternateSetting()==0&&p.interfaces.contains(f.getId())){
                    boolean ok=c.claimInterface(f,false);out.accept("CLAIM_NONFORCE iface="+f.getId()+" ok="+ok);if(ok)claimed.add(f);
                }
            }
            int count=0;
            for(Query q:p.queries.values()){
                if(!running.getAsBoolean())break;
                if(++count>128){out.accept("QUERY_LIMIT_REACHED");break;}
                try{
                    byte[] info=get(c,q,0x86,1,out);
                    byte[] len=get(c,q,0x85,2,out);
                    int l=len!=null&&len.length==2?w(len,0):q.knownLength;
                    boolean readable=info!=null&&info.length==1&&(u(info,0)&1)!=0;
                    // Standard streaming controls are defined by UVC; some devices stall GET_INFO.
                    // For unknown vendor extension payloads only read if GET is explicitly advertised.
                    if(l<1||l>256||(!readable&&q.xu)){out.accept("SKIP_VALUE "+q.name+" length="+l+" read_bit="+readable);continue;}
                    if(!readable&&info!=null&&info.length==1){out.accept("SKIP_VALUE "+q.name+" GET_not_advertised");continue;}
                    int[] reqs=q.xu||q.name.endsWith("PROBE")?new int[]{0x81,0x82,0x83,0x84,0x87}:new int[]{0x81};
                    for(int req:reqs){if(!running.getAsBoolean())break;byte[] a=get(c,q,req,l,out);
                        if(a==null)continue;
                        if(q.name.startsWith("VS_")&&a.length>=26)out.accept("STREAM_FIELDS "+q.name+" req="+req+" format_index="+u(a,2)+" frame_index="+u(a,3)+" interval_100ns="+d(a,4)+" max_frame_bytes="+d(a,18)+" max_payload_bytes="+d(a,22));
                        if(q.name.startsWith("STILL_")&&a.length>=11)out.accept("STILL_FIELDS "+q.name+" req="+req+" format_index="+u(a,0)+" frame_index="+u(a,1)+" compression_index="+u(a,2)+" max_frame_bytes="+d(a,3)+" max_payload_bytes="+d(a,7));
                    }
                }catch(Exception e){out.accept("QUERY_ERROR "+q.name+" "+e);}
            }
        }finally{for(UsbInterface f:claimed)try{c.releaseInterface(f);}catch(Exception ignored){}}
        out.accept("DEEP_END completed="+running.getAsBoolean()+" no_SET_CUR no_vendor_type_requests no_capture_trigger");
    }
}
''')

# Isolated JNI read-only route. Existing preview open() still behaves identically.
java=uvc/"src/main/java/com/jiangdg/uvc/UVCCamera.java"
patch(java,"public synchronized void open(final UsbControlBlock ctrlBlock) {",'''public synchronized void open(final UsbControlBlock ctrlBlock) {
        openInternal(ctrlBlock, false);
    }

    public synchronized void openReadOnly(final UsbControlBlock ctrlBlock) {
        openInternal(ctrlBlock, true);
    }

    public synchronized String readOnlyC110Report() {
        if (mNativePtr == 0) return "NATIVE_NO_HANDLE";
        return nativeReadOnlyC110Report(mNativePtr);
    }
    private native String nativeReadOnlyC110Report(long ptr);

    private void openInternal(final UsbControlBlock ctrlBlock, boolean readOnly) {''')
patch(java,"\t\tmCurrentFrameFormat = FRAME_FORMAT_MJPEG;\n    \tif (mNativePtr != 0 && TextUtils.isEmpty(mSupportedSize)) {","\t\tif (readOnly) return;\n\t\tmCurrentFrameFormat = FRAME_FORMAT_MJPEG;\n    \tif (mNativePtr != 0 && TextUtils.isEmpty(mSupportedSize)) {")
header=uvc/"src/main/jni/UVCCamera/UVCCamera.h"
patch(header,"public:\n\tUVCCamera();","public:\n\tvoid readOnlyC110Report(char *out, int capacity);\n\tUVCCamera();")
cpp=uvc/"src/main/jni/UVCCamera/UVCCamera.cpp"
with cpp.open("a") as f: f.write(r'''
#include <stdio.h>
#include <stdarg.h>
namespace {
struct C110ReadLog {
    char *buf; int capacity; int used;
    C110ReadLog(char *p,int n):buf(p),capacity(n),used(0){if(n>0)buf[0]=0;}
    void line(const char *fmt, ...) {
        if(used>=capacity-1)return;
        va_list ap;va_start(ap,fmt);
        int n=vsnprintf(buf+used,capacity-used,fmt,ap);
        va_end(ap);
        if(n>0)used+=(n<capacity-used?n:capacity-used-1);
    }
    int get(libusb_device_handle *h,const char *name,int iface,int unit,int selector,int req,int len) {
        // All transfers here are class/interface/device-to-host GETs.
        if((req!=0x81&&req!=0x82&&req!=0x83&&req!=0x84&&req!=0x85&&req!=0x86&&req!=0x87)||len<1||len>48)return -99;
        unsigned char data[48]={0};
        int rc=libusb_control_transfer(h,0xa1,req,selector<<8,(unit<<8)|iface,data,len,500);
        line("NATIVE_GET %s iface=%d unit=%d selector=%d req=0x%02x expected=%d rc=%d hex=",name,iface,unit,selector,req,len,rc);
        if(rc>=0){for(int k=0;k<rc&&k<len;k++)line("%02x",data[k]);}else line("ERROR");
        line("\n");
        if(rc==26&&iface==1&&unit==0&&(selector==1||selector==2)){
            unsigned long interval=(unsigned long)data[4]|((unsigned long)data[5]<<8)|((unsigned long)data[6]<<16)|((unsigned long)data[7]<<24);
            unsigned long frame=(unsigned long)data[18]|((unsigned long)data[19]<<8)|((unsigned long)data[20]<<16)|((unsigned long)data[21]<<24);
            unsigned long payload=(unsigned long)data[22]|((unsigned long)data[23]<<8)|((unsigned long)data[24]<<16)|((unsigned long)data[25]<<24);
            line("NATIVE_STREAM_FIELDS selector=%d req=0x%02x format=%u frame=%u interval_100ns=%lu max_frame_bytes=%lu max_payload_bytes=%lu\n",selector,req,data[2],data[3],interval,frame,payload);
        }
        if(unit==5&&rc==1&&req==0x86)line("XU_INFO selector=%d get=%d set=%d raw=0x%02x\n",selector,data[0]&1,(data[0]>>1)&1,data[0]);
        if(unit==5&&rc==2&&req==0x85)line("XU_LENGTH selector=%d bytes=%u\n",selector,(unsigned)(data[0]|(data[1]<<8)));
        return rc;
    }
};
}
void UVCCamera::readOnlyC110Report(char *out,int capacity) {
    C110ReadLog l(out,capacity);
    if(!mDeviceHandle){l.line("NATIVE_NO_DEVICE\n");return;}
    libusb_device_handle *h=uvc_get_libusb_handle(mDeviceHandle);
    if(!h){l.line("NATIVE_NO_USB_HANDLE\n");return;}
    libusb_device *dev=libusb_get_device(h);
    struct libusb_device_descriptor desc;
    int dr=dev?libusb_get_device_descriptor(dev,&desc):-1;
    if(dr!=0||desc.idVendor!=0x0603||desc.idProduct!=0x8612){l.line("NATIVE_SKIP wrong_device rc=%d\n",dr);return;}
    l.line("NATIVE_LINK_SPEED_ENUM=%d (0 unknown,1 low,2 full,3 high,4 super)\n",libusb_get_device_speed(dev));
    // UVC open already owns the control interface (0). Known descriptor advertises brightness and AE mode.
    int b=l.get(h,"BRIGHTNESS",0,4,2,0x81,2);
    int ae=l.get(h,"AE_MODE",0,1,2,0x81,1);
    bool vc=(b==2||ae==1);
    l.line("NATIVE_VC_POSITIVE_CONTROL=%d\n",vc?1:0);
    // Claim streaming interface using the same helper as the actual streaming implementation.
    int claim=uvc_claim_if(mDeviceHandle,1);
    l.line("NATIVE_CLAIM_VS rc=%d\n",claim);
    bool vs=false;
    if(claim==0){
        const int reqs[]={0x81,0x82,0x83,0x87};
        for(int i=0;i<4;i++){int n=l.get(h,"VS_PROBE",1,0,1,reqs[i],26);if(n==26)vs=true;}
        l.get(h,"VS_COMMIT",1,0,2,0x81,26);
        l.line("NATIVE_RELEASE_VS rc=%d\n",uvc_release_if(mDeviceHandle,1));
    }
    l.line("NATIVE_VS_POSITIVE_CONTROL=%d\n",vs?1:0);
    // Unknown XU payloads may be firmware-specific; only request standardized capability/length metadata.
    if(vc){
        int info=0,len=0;
        for(int s=1;s<=16;s++){
            if(l.get(h,"XU",0,5,s,0x86,1)==1)info++;
            if(l.get(h,"XU",0,5,s,0x85,2)==2)len++;
        }
        l.line("XU_METADATA_SUMMARY info_ok=%d len_ok=%d total=16\n",info,len);
    }else l.line("XU_SKIPPED no_positive_vc_control\n");
    l.line("NATIVE_REPORT_DONE no_SET_CUR no_XU_payload no_firmware_command no_stream\n");
}
''')
jni=uvc/"src/main/jni/UVCCamera/serenegiant_usb_UVCCamera.cpp"
patch(jni,"static JNINativeMethod methods[] = {",'''static jstring nativeReadOnlyC110Report(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
    UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
    char report[16384] = {0};
    if(camera) camera->readOnlyC110Report(report, sizeof(report));
    else return env->NewStringUTF("NATIVE_NO_CAMERA");
    return env->NewStringUTF(report);
}

static JNINativeMethod methods[] = {
    { "nativeReadOnlyC110Report", "(J)Ljava/lang/String;", (void *) nativeReadOnlyC110Report },''')

# libuvc internal declarations are outside its public extern "C" block.
# Match the linkage of the C implementations in src/device.c.
patch(uvc/"src/main/jni/libuvc/include/libuvc/libuvc_internal.h",
      "uvc_error_t uvc_claim_if(uvc_device_handle_t *devh, int idx);\nuvc_error_t uvc_release_if(uvc_device_handle_t *devh, int idx);",
      '''#ifdef __cplusplus
extern "C" {
#endif
uvc_error_t uvc_claim_if(uvc_device_handle_t *devh, int idx);
uvc_error_t uvc_release_if(uvc_device_handle_t *devh, int idx);
#ifdef __cplusplus
}
#endif''')
