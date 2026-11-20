package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Exactly one user-requested C110+ native still photo -> the existing
 * CloudVisionClient / shared model / TTS pipeline, without opening ANY page.
 * Owned by HomeActivity or the normal phone/USB preview Activity.
 *
 * No phone camera, UVC, VLC view, diagnosis page, auto retry or file deletion.
 */
final class C110QuickShotController {
    private static final long WIFI_WAIT_MS=45000L;
    private final Activity owner;
    private final Consumer<String> status;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final C110WifiCamera camera=new C110WifiCamera();
    private final C110FrameReader reader;
    private C110WifiBootstrap bootstrap;
    private Runnable waiting;
    private boolean active,destroyed,shutterPending;
    private int generation;
    private long waitingSince;

    C110QuickShotController(Activity activity,Consumer<String> updates){
        owner=activity;status=updates;
        reader=new C110FrameReader(owner,this::emit);
    }
    private void emit(String message){
        main.post(()->{if(!destroyed)status.accept(message);});
    }
    void onResume(){
        active=true;
        // Home has no reason to hold a second third-party TTS session until
        // the user actually requests a photo. trigger() synchronizes voice.
        if(shutterPending&&waiting!=null)main.post(waiting);
    }
    void beforeOpenAnotherScreen(){
        // Prevent Home's retained C110 voice instance competing with the
        // separate MainActivity TTS used by Settings/phone/USB.
        reader.releaseSpeechForOtherScreen();
    }
    void onPause(){
        active=false;
        // Android's Wi-Fi consent can pause the screen. Keep the SAME
        // pending shutter but do not keep polling while the app is paused.
        if(waiting!=null)main.removeCallbacks(waiting);
    }
    void trigger(){
        if(destroyed||!active)return;
        reader.syncSpeechSettings(); // also covers changes made while Home remains alive
        if(shutterPending||reader.isBusy()){
            emit("上一张正在拍摄或识别，请勿重复触发");
            return;
        }
        final int token=++generation;
        shutterPending=true;
        emit("正在连接C110+，准备拍摄一张高清原图…");
        if(C110WifiBootstrap.connected(owner)!=null){
            takeOnce(token);
            return;
        }
        // Borrow MainActivity's existing Wi-Fi lease when possible. Do NOT
        // create a second callback that could drop a verified camera network.
        if(!C110WifiBootstrap.requestFromCameraPage(owner,false)){
            if(bootstrap==null)bootstrap=new C110WifiBootstrap(owner,this::emit);
            bootstrap.requestForCameraMode();
        }
        waitingSince=SystemClock.elapsedRealtime();
        waiting=new Runnable(){
            @Override public void run(){
                if(destroyed||token!=generation||!shutterPending||!active)return;
                if(C110WifiBootstrap.connected(owner)!=null){
                    waiting=null;
                    takeOnce(token);
                    return;
                }
                if(SystemClock.elapsedRealtime()-waitingSince>=WIFI_WAIT_MS){
                    waiting=null;shutterPending=false;
                    emit("相机Wi-Fi连接失败，本次没有拍照；请检查C110+连接");
                    return;
                }
                main.postDelayed(this,450L);
            }
        };
        main.postDelayed(waiting,450L);
    }
    private void takeOnce(int token){
        if(destroyed||token!=generation||!shutterPending)return;
        emit("C110+已连接，正在拍一张高清原图…");
        // Only this one worker can dispatch the single user-triggered 1001.
        worker.execute(()->{
            try{
                final C110WifiCamera.Photo photo=camera.shootAfterLivePreview(owner);
                main.post(()->{
                    if(destroyed||token!=generation)return;
                    shutterPending=false;
                    emit("已拍照 "+photo.width+"×"+photo.height+"，正在识别朗读…");
                    // Same cloud model/key/task as Home settings; no test
                    // prompts and no re-encoding at native 1x.
                    if(!reader.recognizeOriginalJpeg(photo.jpeg,photo.width,photo.height))
                        emit("已拍摄原图，但朗读任务仍忙，请稍后再试");
                });
            }catch(Exception error){
                String detail=error.getMessage()==null?
                    error.getClass().getSimpleName():error.getMessage();
                main.post(()->{
                    if(destroyed||token!=generation)return;
                    shutterPending=false;
                    emit("C110+拍照失败："+detail+"；不会自动重拍");
                });
            }
        });
    }
    void onPermissionResult(int requestCode,int[] results){
        boolean granted=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;
        if(requestCode==C110WifiBootstrap.SCAN_PERMISSION_REQUEST){
            if(bootstrap!=null)bootstrap.onScanPermissionResult(owner,granted);
            else C110WifiBootstrap.onScanPermissionFromCameraPage(owner,granted);
        }else if(requestCode==C110WifiBootstrap.PERMISSION_REQUEST){
            if(bootstrap!=null)bootstrap.onPermissionResult(granted);
            else C110WifiBootstrap.onWifiPermissionFromCameraPage(owner,granted);
        }
    }
    void shutdown(){
        destroyed=true;++generation;shutterPending=false;
        if(waiting!=null){main.removeCallbacks(waiting);waiting=null;}
        if(bootstrap!=null){bootstrap.shutdown();bootstrap=null;}
        reader.shutdown();
        worker.shutdownNow();
    }
}
