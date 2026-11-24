package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Home foreground only. One serial camera I/O lane; no shutter or video command. */
final class C110PhysicalWatcher {
    private final Activity owner;
    private final C110QuickShotController shots;
    private final BooleanSupplier manualBusy,voiceBusy;
    private final Runnable yieldVoiceSpeech;
    private final Consumer<String> report;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final C110PhysicalPolicy policy=new C110PhysicalPolicy();
    private volatile boolean active,closed,suspended;
    private volatile int generation,serial;
    private boolean inFlight,waitingReported;
    private Network network;
    private Set<String> directories;
    private String pending;
    private long discoveredAt,queryMs;
    private boolean pendingUnique;
    private DeleteJob deleteJob;
    private final C110DeferredPhoto<ReadyPhoto> deferred=new C110DeferredPhoto<>();
    private final Runnable tick=this::poll;
    private static final class ReadyPhoto {
        final Network wifi;
        final String path;
        final C110WifiCamera.Photo photo;
        final byte[] digest;
        final int generation,serial,interval;
        final long queryMs,downloadMs,readyAt;
        final boolean unique,clean;
        ReadyPhoto(Network wifi,String path,C110WifiCamera.Photo photo,byte[] digest,
                   int generation,int serial,int interval,long queryMs,long downloadMs,
                   boolean unique,boolean clean){
            this.wifi=wifi;this.path=path;this.photo=photo;this.digest=digest;
            this.generation=generation;this.serial=serial;this.interval=interval;
            this.queryMs=queryMs;this.downloadMs=downloadMs;
            this.unique=unique;this.clean=clean;this.readyAt=SystemClock.elapsedRealtime();
        }
    }
    private static final class DeleteJob {
        final Network wifi;
        final String path;
        final byte[] digest;
        final int generation,serial;
        DeleteJob(Network wifi,String path,byte[] digest,int generation,int serial){
            this.wifi=wifi;this.path=path;this.digest=digest;
            this.generation=generation;this.serial=serial;
        }
    }
    C110PhysicalWatcher(Activity owner,C110QuickShotController shots,
                       BooleanSupplier manualBusy,BooleanSupplier voiceBusy,
                       Runnable yieldVoiceSpeech,Consumer<String> report){
        this.owner=owner;this.shots=shots;this.manualBusy=manualBusy;this.voiceBusy=voiceBusy;
        this.yieldVoiceSpeech=yieldVoiceSpeech;this.report=report;
        prefs=owner.getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
    }
    private boolean enabled(){return prefs.getBoolean(C110PhysicalPolicy.ENABLE,false);}
    private boolean deleteEnabled(){return prefs.getBoolean(C110PhysicalPolicy.DELETE,false);}
    private int interval(){return C110PhysicalPolicy.interval(
        prefs.getInt(C110PhysicalPolicy.INTERVAL,300));}
    void start(){
        if(closed)return;
        active=enabled();
        suspended=false;waitingReported=false;
        invalidate();
        if(active)main.post(tick);
    }
    void stop(){
        active=false;suspended=true;main.removeCallbacks(tick);invalidate();
    }
    void shutdown(){stop();closed=true;worker.shutdownNow();}
    boolean cameraIoBusy(){return inFlight;}
    /** Voice is NOT a camera session change. Keep directory baseline and polling.
     * Drop a prior task and its cleanup; new photos during ASR are retained. */
    void beforeVoiceAction(){
        if(!active)return;
        serial++;
        pending=null;deleteJob=null;deferred.clear();
        shots.cancelPhysicalRecognition();
    }
    /** Called before a manual phone-side shutter or a different screen operation. */
    void beforeManualAction(){
        if(!active)return;
        suspended=true;
        invalidate();
        schedule(500);
    }
    private void invalidate(){
        generation++;serial++;
        policy.reset();directories=null;network=null;
        pending=null;deleteJob=null;deferred.clear();
        shots.cancelPhysicalRecognition();
    }
    private void schedule(long delay){
        main.removeCallbacks(tick);
        if(active&&!closed)main.postDelayed(tick,delay);
    }
    private boolean current(int token){
        return active&&!closed&&!suspended&&generation==token&&enabled();
    }
    private boolean canDelete(DeleteJob job){
        return current(job.generation)&&serial==job.serial&&deleteEnabled()
            &&!Thread.currentThread().isInterrupted()
            &&job.wifi.equals(C110WifiBootstrap.connected(owner));
    }
    private void poll(){
        if(!active||closed)return;
        if(!enabled()){stop();return;}
        if(manualBusy.getAsBoolean()){
            if(!suspended){suspended=true;invalidate();}
            schedule(300);return;
        }
        if(suspended){suspended=false;invalidate();}
        Network wifi=C110WifiBootstrap.connected(owner);
        if(!java.util.Objects.equals(wifi,network)){
            invalidate();network=wifi;waitingReported=false;
        }
        if(wifi==null){
            if(!waitingReported){report.accept("实体拍照监听：等待连接相机");waitingReported=true;}
            schedule(1000);return;
        }
        if(inFlight){schedule(interval());return;}
        final int token=generation;
        final int chosenInterval=interval();
        final boolean verifyIdentity=directories==null;
        inFlight=true;
        // No queued requests: the next round begins only after this one finishes.
        try{
            worker.execute(()->{
                long start=SystemClock.elapsedRealtime();
                try{
                    if(verifyIdentity)C110WifiCamera.verifyPhysicalCamera(wifi);
                    C110WifiCamera.PhysicalSnapshot snapshot=C110WifiCamera.physicalSnapshot(wifi);
                    long ms=SystemClock.elapsedRealtime()-start;
                    main.post(()->handleSnapshot(wifi,token,chosenInterval,snapshot,ms));
                }catch(Exception e){
                    main.post(()->failed(token,"实体拍照监听：目录读取失败，重新建立基线"));
                }
            });
        }catch(RuntimeException e){inFlight=false;schedule(1000);}
    }
    private boolean stillCurrent(Network wifi,int token){
        return current(token)&&wifi.equals(network)&&wifi.equals(C110WifiBootstrap.connected(owner))
            &&!manualBusy.getAsBoolean();
    }
    private void failed(int token,String message){
        inFlight=false;
        if(current(token)){
            invalidate();
            report.accept(message);
        }
        schedule(1000);
    }
    private void handleSnapshot(Network wifi,int token,int chosenInterval,
                                C110WifiCamera.PhysicalSnapshot snapshot,long ms){
        if(!stillCurrent(wifi,token)){inFlight=false;schedule(300);return;}
        if(directories!=null&&!directories.equals(snapshot.dirs)){
            // A changed folder tree may reveal pre-existing images. Re-arm rather than guess.
            invalidate();network=wifi;
            inFlight=false;schedule(chosenInterval);return;
        }
        directories=snapshot.dirs;
        boolean wasReady=policy.ready();
        C110PhysicalPolicy.Delta delta=policy.observe(snapshot.paths);
        if(!wasReady&&policy.ready())report.accept("实体拍照监听已就绪，请按相机快门");
        if(delta.latest!=null){
            serial++;
            shots.cancelPhysicalRecognition();
            deleteJob=null;deferred.clear();
            pending=delta.latest;
            pendingUnique=delta.unique;
            discoveredAt=SystemClock.elapsedRealtime();
            queryMs=ms;
        }
        if(pending!=null){
            if(SystemClock.elapsedRealtime()-discoveredAt>20000){
                pending=null;
                report.accept("新照片未能完整读取，保留相机原图");
            }else if(snapshot.paths.contains(pending)){
                download(wifi,token,chosenInterval);
                return;
            }
        }
        // Dispatch only after checking for a newer directory delta in this round.
        dispatchDeferred();
        // Keep camera polling during voice work, never delete while recording/ASR.
        // Prefer a newer photo over any old cleanup. Only one pending delete at a time.
        if(deleteJob!=null&&!voiceBusy.getAsBoolean()){
            DeleteJob job=deleteJob;deleteJob=null;
            if(canDelete(job)&&snapshot.paths.contains(job.path)){
                delete(job,chosenInterval);
                return;
            }
        }
        inFlight=false;
        schedule(chosenInterval);
    }
    private void download(Network wifi,int token,int chosenInterval){
        final String path=pending;
        final int photoSerial=serial;
        final boolean unique=pendingUnique;
        final long first=discoveredAt,qms=queryMs;
        try{
            worker.execute(()->{
                long start=SystemClock.elapsedRealtime();
                try{
                    C110WifiCamera.Photo photo=C110WifiCamera.physicalDownload(owner,wifi,path,first);
                    long dms=SystemClock.elapsedRealtime()-start;
                    byte[] digest=unique?C110WifiCamera.physicalDigest(photo.jpeg):null;
                    main.post(()->{
                        inFlight=false;
                        if(stillCurrent(wifi,token)&&photoSerial==serial&&path.equals(pending)){
                            pending=null;
                            // At most one complete JPEG in RAM; no upload or sound during ASR.
                            deferred.offer(new ReadyPhoto(wifi,path,photo,digest,token,photoSerial,
                                chosenInterval,qms,dms,unique,unique&&deleteEnabled()));
                            if(voiceBusy.getAsBoolean())
                                report.accept("已获取相机新照片，等待AI语音指令完成");
                            else dispatchDeferred();
                        }
                        schedule(chosenInterval);
                    });
                }catch(Exception notReady){
                    // The filename can appear before SD writes finish. Retry GET only, never shutter.
                    main.post(()->{inFlight=false;schedule(chosenInterval);});
                }
            });
        }catch(RuntimeException e){inFlight=false;schedule(chosenInterval);}
    }
    private void dispatchDeferred(){
        ReadyPhoto ready=deferred.takeIfReady(voiceBusy.getAsBoolean());
        if(ready==null)return;
        if(!stillCurrent(ready.wifi,ready.generation)||ready.serial!=serial)return;
        // ASR has committed the new target before opening this gate. A physical
        // shutter can preempt the old confirmation speech, never the microphone.
        yieldVoiceSpeech.run();
        final boolean clean=ready.clean&&ready.unique&&deleteEnabled();
        boolean accepted=shots.acceptPhysicalPhoto(ready.photo,ready.queryMs,ready.downloadMs,
            ready.interval,SystemClock.elapsedRealtime()-ready.readyAt,()->{
                if(clean&&ready.digest!=null&&stillCurrent(ready.wifi,ready.generation)
                    &&ready.serial==serial&&!voiceBusy.getAsBoolean()&&deleteEnabled()){
                    deleteJob=new DeleteJob(ready.wifi,ready.path,ready.digest,ready.generation,ready.serial);
                }
            });
        if(!accepted)report.accept("新照片未提交识别，保留相机原图");
    }
    private void delete(DeleteJob job,int chosenInterval){
        try{
            worker.execute(()->{
                boolean gone=false;
                try{
                    gone=C110WifiCamera.physicalDelete(owner,job.wifi,job.path,job.digest,
                        ()->canDelete(job));
                }catch(Exception ignored){}
                final boolean verified=gone;
                main.post(()->{
                    inFlight=false;
                    if(current(job.generation)&&serial==job.serial){
                        if(verified){
                            policy.deleted(job.path);
                            report.accept("本次识别原图已从相机内存卡删除");
                        }else report.accept("未确认单张删图成功，不再重试");
                    }
                    schedule(chosenInterval);
                });
            });
        }catch(RuntimeException e){inFlight=false;schedule(chosenInterval);}
    }
}
