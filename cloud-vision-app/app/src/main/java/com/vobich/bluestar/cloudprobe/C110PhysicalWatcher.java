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
    private final BooleanSupplier manualBusy;
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
    private final Runnable tick=this::poll;
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
                       BooleanSupplier manualBusy,Consumer<String> report){
        this.owner=owner;this.shots=shots;this.manualBusy=manualBusy;this.report=report;
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
    /** Called before a manual phone-side shutter or a different screen/voice operation. */
    void beforeManualAction(){
        if(!active)return;
        suspended=true;
        invalidate();
        schedule(500);
    }
    private void invalidate(){
        generation++;serial++;
        policy.reset();directories=null;network=null;
        pending=null;deleteJob=null;
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
            deleteJob=null;
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
        // Prefer a newer photo over any old cleanup. Only one pending delete at a time.
        if(deleteJob!=null){
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
                            // Only the single unique newly appeared JPEG can be deleted, and only
                            // if cleanup was enabled at acceptance AND remains enabled on success.
                            final boolean clean=unique&&deleteEnabled();
                            boolean accepted=shots.acceptPhysicalPhoto(photo,qms,dms,chosenInterval,()->{
                                if(clean&&digest!=null&&stillCurrent(wifi,token)&&photoSerial==serial
                                    &&deleteEnabled()){
                                    deleteJob=new DeleteJob(wifi,path,digest,token,photoSerial);
                                }
                            });
                            if(!accepted)report.accept("新照片未提交识别，保留相机原图");
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
