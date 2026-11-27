package com.vobich.bluestar.openocrlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One passive event socket plus one serial event-triggered read-only camera I/O lane. */
final class C110PhysicalWatcher {
    interface PhotoSink {
        void cancelPhysicalRecognition();
        boolean acceptPhysicalPhoto(C110WifiCamera.Photo photo,long queryMs,long downloadMs,int eventGap,long voiceWaitMs,Runnable onSuccess);
    }
    private final Context owner;
    private final PhotoSink shots;
    private final BooleanSupplier manualBusy,voiceBusy;
    private final Runnable yieldVoiceSpeech;
    private final Consumer<String> report;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService socketWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final C110DeferredPhoto<ReadyPhoto> deferred=new C110DeferredPhoto<>();
    private final Runnable monitor=this::monitorLocalState;
    private volatile boolean active,closed;
    private volatile Session session;
    private volatile int serial;
    private int ioCount;
    private boolean waitingReported;
    private long resumeAt;

    private static final class Session {
        final Network wifi;
        final C110PhysicalPolicy policy=new C110PhysicalPolicy();
        volatile Socket socket;
        volatile boolean cancelled;
        boolean fetching;
        int uncommitted;
        Shot queued;
        Session(Network wifi){this.wifi=wifi;}
        void close(){
            cancelled=true;
            Socket s=socket;
            if(s!=null)try{s.close();}catch(IOException ignored){}
        }
    }
    private static final class Shot {
        final int serial,gap,expected;
        final long at;
        Shot(int serial,int gap,long at,int expected){this.serial=serial;this.gap=gap;this.at=at;this.expected=expected;}
    }
    private static final class ReadyPhoto {
        final Session session;
        final Shot shot;
        final C110WifiCamera.Photo photo;
        final long queryMs,downloadMs,readyAt;
        ReadyPhoto(Session s,Shot shot,C110WifiCamera.Photo p,long q,long d){
            this.session=s;this.shot=shot;photo=p;queryMs=q;downloadMs=d;
            readyAt=SystemClock.elapsedRealtime();
        }
    }
    C110PhysicalWatcher(Context owner,PhotoSink shots,BooleanSupplier manualBusy,
                       BooleanSupplier voiceBusy,Runnable yieldVoiceSpeech,Consumer<String> report){
        this.owner=owner;this.shots=shots;this.manualBusy=manualBusy;this.voiceBusy=voiceBusy;
        this.yieldVoiceSpeech=yieldVoiceSpeech;this.report=report;
        prefs=owner.getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
    }
    private boolean enabled(){return prefs.getBoolean(C110PhysicalPolicy.ENABLE,false);}
    private boolean current(Session s){return active&&!closed&&session==s&&!s.cancelled;}
    private boolean latest(Session s,Shot shot){return current(s)&&serial==shot.serial;}
    boolean cameraIoBusy(){return ioCount>0;}
    void start(){
        if(closed)return;
        active=enabled();waitingReported=false;resumeAt=0;
        dropSession();
        if(active)main.post(monitor);
    }
    void stop(){active=false;main.removeCallbacks(monitor);dropSession();}
    void shutdown(){stop();closed=true;socketWorker.shutdownNow();worker.shutdownNow();}
    void beforeVoiceAction(){
        if(!active)return;
        serial++;deferred.clear();shots.cancelPhysicalRecognition();
        if(session!=null)session.queued=null;
    }
    void beforeManualAction(){
        if(!active)return;
        resumeAt=SystemClock.elapsedRealtime()+500;
        dropSession();
    }
    private void dropSession(){
        Session old=session;session=null;
        if(old!=null)old.close();
        serial++;deferred.clear();shots.cancelPhysicalRecognition();
    }
    private void scheduleLocal(long delay){
        main.removeCallbacks(monitor);
        if(active&&!closed)main.postDelayed(monitor,delay);
    }
    /** Local Android network/voice state only. Never calls camera HTTP or SD listing. */
    private void monitorLocalState(){
        if(!active||closed)return;
        if(!enabled()){stop();return;}
        if(manualBusy.getAsBoolean()||SystemClock.elapsedRealtime()<resumeAt){
            if(session!=null)dropSession();
            scheduleLocal(250);return;
        }
        Network wifi=C110WifiBootstrap.connected(owner);
        if(session!=null&&!session.wifi.equals(wifi))dropSession();
        if(wifi==null){
            if(!waitingReported){report.accept("实体拍照监听：等待连接相机");waitingReported=true;}
        }else if(session==null&&ioCount==0){
            waitingReported=false;open(wifi);
        }
        dispatchDeferred();
        scheduleLocal(250);
    }
    private void open(Network wifi){
        Session s=new Session(wifi);session=s;
        report.accept("正在连接相机通知");
        try{socketWorker.execute(()->listen(s));}
        catch(RuntimeException e){fail(s,"通知连接启动失败");}
    }
    private void listen(Session s){
        try(Socket socket=s.wifi.getSocketFactory().createSocket()){
            s.socket=socket;
            if(!current(s))return;
            socket.connect(new InetSocketAddress(C110WifiCamera.CAMERA_IP,3333),3000);
            socket.setSoTimeout(1000);
            main.post(()->arm(s));
            C110EventDecoder decoder=new C110EventDecoder((cmd,status)->{
                long now=SystemClock.elapsedRealtime();
                if(cmd==3020&&(status==19||status==20))
                    main.post(()->onEvent(s,cmd,status,now));
            });
            InputStreamReader in=new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8);
            char[] buf=new char[1024];
            while(current(s)){
                try{
                    int n=in.read(buf);
                    if(n<0)throw new IOException("EOF");
                    if(n>0)decoder.feed(buf,n);
                }catch(SocketTimeoutException idle){/* Local wait only; no ping or outgoing payload. */}
            }
        }catch(IOException|RuntimeException ex){
            main.post(()->{
                if(!current(s))return;
                if(!s.wifi.equals(C110WifiBootstrap.connected(owner))){
                    dropSession();scheduleLocal(500);
                }else fail(s,"相机通知连接异常，已暂停，请确认相机正常后重新开启监听");
            });
        }finally{s.socket=null;}
    }
    private void arm(Session s){
        if(!current(s))return;
        ioCount++;
        try{worker.execute(()->{
            Set<String> history=null;
            try{
                if(current(s)){
                    C110WifiCamera.verifyPhysicalCamera(s.wifi);
                    if(current(s))history=C110WifiCamera.physicalSnapshot(s.wifi).paths;
                }
            }catch(IOException|RuntimeException ignored){}
            final Set<String> result=history;
            main.post(()->{
                ioCount--;
                if(!current(s))return;
                if(result==null){fail(s,"相机初始目录读取失败，已暂停监听");return;}
                s.policy.arm(result);
                report.accept("实体拍照通知已就绪，请按相机快门");
            });
        });}catch(RuntimeException e){ioCount--;fail(s,"监听初始化失败");}
    }
    private void onEvent(Session s,int cmd,int status,long now){
        if(!current(s)||manualBusy.getAsBoolean()||!s.policy.ready())return;
        if(s.policy.begin(cmd,status,now)){
            serial++;s.queued=null;deferred.clear();shots.cancelPhysicalRecognition();
            return;
        }
        int gap=s.policy.finish(cmd,status,now);
        if(gap<0)return;
        if(++s.uncommitted>16){fail(s,"相机通知积压，已暂停监听");return;}
        s.queued=new Shot(serial,gap,now,s.uncommitted);
        launchQueued(s);
    }
    private void launchQueued(Session s){
        if(!current(s)||s.fetching||s.queued==null)return;
        Shot shot=s.queued;s.queued=null;
        if(!latest(s,shot))return;
        Set<String> history=s.policy.history();
        s.fetching=true;ioCount++;
        try{worker.execute(()->fetch(s,shot,history));}
        catch(RuntimeException e){s.fetching=false;ioCount--;fail(s,"照片读取启动失败");}
    }
    private void fetch(Session s,Shot shot,Set<String> history){
        C110WifiCamera.Photo photo=null;
        Set<String> found=null;
        long q=0,d=0;
        boolean hardFailure=false;
        try{
            // First attempt immediately. Only not-yet-visible/incomplete JPEG gets two bounded retries.
            for(int attempt=0;attempt<3&&latest(s,shot);attempt++){
                if(attempt>0)Thread.sleep(attempt*100L);
                if(!latest(s,shot))break;
                long begin=SystemClock.elapsedRealtime();
                C110WifiCamera.PhysicalSnapshot snapshot=C110WifiCamera.physicalSnapshot(s.wifi);
                q+=SystemClock.elapsedRealtime()-begin;
                if(!latest(s,shot))break;
                int newCount=0;
                for(String path:snapshot.paths)if(!history.contains(path))newCount++;
                if(newCount<shot.expected)continue; // Avoid reading an older uncommitted shot in a burst.
                String path=C110PhysicalPolicy.latest(snapshot.paths,history);
                if(path==null)continue;
                long downloading=SystemClock.elapsedRealtime();
                try{
                    photo=C110WifiCamera.physicalDownload(owner,s.wifi,path,shot.at);
                    d+=SystemClock.elapsedRealtime()-downloading;
                    found=snapshot.paths;
                    break;
                }catch(IOException ex){
                    d+=SystemClock.elapsedRealtime()-downloading;
                    String msg=ex.getMessage();
                    if(!"C110 JPEG未准备好".equals(msg)&&!"JPEG首尾校验失败".equals(msg)&&
                       !"JPEG尺寸校验失败".equals(msg))throw ex;
                }
            }
        }catch(InterruptedException ex){Thread.currentThread().interrupt();}
        catch(IOException|RuntimeException ex){hardFailure=true;}
        final C110WifiCamera.Photo result=photo;
        final Set<String> paths=found;
        final long query=q,download=d;
        final boolean failed=hardFailure;
        main.post(()->{
            ioCount--;s.fetching=false;
            if(!current(s))return;
            if(failed){fail(s,"相机读取异常，已暂停监听，避免重复访问");return;}
            if(latest(s,shot)){
                if(result==null){
                    fail(s,"通知已收到，但未取得新原图，已暂停监听");return;
                }
                s.policy.remember(paths);s.uncommitted=0;
                deferred.offer(new ReadyPhoto(s,shot,result,query,download));
                if(voiceBusy.getAsBoolean())report.accept("已获取相机新照片，等待AI语音指令完成");
                dispatchDeferred();
            }
            // A newer paired event supersedes an in-flight task; no queued historical tasks.
            launchQueued(s);
        });
    }
    private void dispatchDeferred(){
        ReadyPhoto ready=deferred.takeIfReady(voiceBusy.getAsBoolean());
        if(ready==null||!latest(ready.session,ready.shot)||manualBusy.getAsBoolean())return;
        yieldVoiceSpeech.run();
        boolean accepted=shots.acceptPhysicalPhoto(ready.photo,ready.queryMs,ready.downloadMs,
            ready.shot.gap,SystemClock.elapsedRealtime()-ready.readyAt,null);
        if(!accepted)report.accept("新照片未提交识别");
    }
    private void fail(Session s,String message){
        if(!current(s))return;
        stop();report.accept(message);
        // Fail closed. No repeated TCP reconnect or HTTP retries after abnormal camera response.
        prefs.edit().putBoolean(C110PhysicalPolicy.ENABLE,false).apply();
    }
}
