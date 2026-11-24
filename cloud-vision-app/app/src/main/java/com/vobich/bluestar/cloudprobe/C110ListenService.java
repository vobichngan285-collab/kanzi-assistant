package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** User-controlled connected-device session. Never opens the phone camera or microphone. */
public final class C110ListenService extends Service implements Application.ActivityLifecycleCallbacks {
    private static final String CHANNEL="c110_listening";
    private static final String STOP="com.vobich.bluestar.cloudprobe.STOP_C110_LISTEN";
    private static final int NOTICE=197;
    private static C110ListenService instance;
    private static WeakReference<HomeActivity> home=new WeakReference<>(null);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Activity> otherScreens=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Runnable resumeWatch=this::reconcile;
    private C110PhysicalWatcher watcher;
    private C110FrameReader reader;
    private CaptureDingFeedback ding;
    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLock;
    private SharedPreferences prefs;
    private boolean watching,closing;
    private boolean registered;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener=(p,k)->{
        if(C110PhysicalPolicy.ENABLE.equals(k)&&!enabled())stopSelf();
    };

    static void attachHome(HomeActivity activity){home=new WeakReference<>(activity);}
    static void detachHome(HomeActivity activity){if(home.get()==activity)home.clear();}
    static void ensureStarted(HomeActivity activity){
        if(instance!=null&&!instance.closing)return;
        try{activity.startForegroundService(new Intent(activity,C110ListenService.class));}
        catch(RuntimeException failure){
            activity.getSharedPreferences("kanzi_settings",MODE_PRIVATE).edit()
                .putBoolean(C110PhysicalPolicy.ENABLE,false).apply();
            activity.physicalStatus("监听启动失败，请重试");
        }
    }
    static void stopListening(Context context){context.stopService(new Intent(context,C110ListenService.class));}
    static boolean cameraIoBusy(){return instance!=null&&instance.watcher!=null&&instance.watcher.cameraIoBusy();}
    static void beforeManualAction(){
        C110ListenService s=instance;
        if(s!=null&&s.watcher!=null){
            s.watcher.beforeManualAction();
            s.reader.releaseSpeechForOtherScreen();
        }
    }
    static void beforeVoiceAction(){
        C110ListenService s=instance;
        if(s!=null&&s.watcher!=null){
            s.watcher.beforeVoiceAction();
            s.reader.releaseSpeechForOtherScreen();
        }
    }
    private boolean enabled(){return prefs!=null&&prefs.getBoolean(C110PhysicalPolicy.ENABLE,false);}
    private boolean manualBusy(){
        HomeActivity h=home.get();
        return h!=null&&h.physicalManualBusy();
    }
    private boolean voiceBusy(){
        HomeActivity h=home.get();
        return h!=null&&h.physicalVoiceBusy();
    }
    private void yieldSpeech(){
        HomeActivity h=home.get();
        if(h!=null)h.yieldForPhysical();
    }
    private void report(String message){
        HomeActivity h=home.get();
        if(h!=null)h.physicalStatus(message);
    }
    @Override public void onCreate(){
        super.onCreate();
        instance=this;
        prefs=getSharedPreferences("kanzi_settings",MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }
    private Notification notification(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"相机监听",NotificationManager.IMPORTANCE_LOW));
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,HomeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),flags);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,C110ListenService.class).setAction(STOP),flags);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("蓝星向导").setContentText("相机监听已开启").setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(new Notification.Action.Builder(null,"关闭监听",stop).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        try{startForeground(NOTICE,notification());}
        catch(RuntimeException error){
            prefs.edit().putBoolean(C110PhysicalPolicy.ENABLE,false).apply();
            report("监听服务启动失败");stopSelf();return START_NOT_STICKY;
        }
        if(intent!=null&&STOP.equals(intent.getAction())){
            prefs.edit().putBoolean(C110PhysicalPolicy.ENABLE,false).apply();
            stopSelf();return START_NOT_STICKY;
        }
        if(!enabled()){stopSelf();return START_NOT_STICKY;}
        if(watcher==null){
            reader=new C110FrameReader(this,this::report);
            ding=new CaptureDingFeedback(this);
            watcher=new C110PhysicalWatcher(this,new C110PhysicalWatcher.PhotoSink(){
                @Override public void cancelPhysicalRecognition(){reader.cancelRecognition();}
                @Override public boolean acceptPhysicalPhoto(C110WifiCamera.Photo photo,long q,long d,int interval,long voiceWait,Runnable done){
                    reader.cancelRecognition();
                    reader.syncSpeechSettings();
                    ding.photoSucceeded();
                    return reader.recognizePhysicalJpeg(photo,q,d,interval,voiceWait,done);
                }
            },this::manualBusy,this::voiceBusy,this::yieldSpeech,this::report);
            getApplication().registerActivityLifecycleCallbacks(this);
            registered=true;
        }
        reconcile();
        return START_NOT_STICKY;
    }
    private void holdLocks(){
        try{
            if(cpuLock==null){
                PowerManager pm=getSystemService(PowerManager.class);
                cpuLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"BlueStar:C110Listen");
                cpuLock.setReferenceCounted(false);
            }
            if(!cpuLock.isHeld())cpuLock.acquire();
        }catch(RuntimeException ignored){}
        try{
            if(wifiLock==null){
                WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                wifiLock=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"BlueStar:C110Listen");
                wifiLock.setReferenceCounted(false);
            }
            if(!wifiLock.isHeld())wifiLock.acquire();
        }catch(RuntimeException ignored){}
    }
    private void releaseLocks(){
        if(cpuLock!=null&&cpuLock.isHeld())cpuLock.release();
        if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();
    }
    private void reconcile(){
        if(closing||watcher==null)return;
        // Keep the same baseline across Home lock/unlock and external app switches.
        // Temporarily yield to Blue Star's own camera/settings/reading screens.
        boolean want=enabled()&&otherScreens.isEmpty();
        if(want&&!watching){holdLocks();watcher.start();watching=true;}
        else if(!want&&watching){
            watching=false;watcher.stop();reader.releaseSpeechForOtherScreen();releaseLocks();
        }
    }
    @Override public void onActivityStarted(Activity activity){
        if(!(activity instanceof HomeActivity)){
            otherScreens.add(activity);main.removeCallbacks(resumeWatch);reconcile();
        }
    }
    @Override public void onActivityStopped(Activity activity){
        if(otherScreens.remove(activity)){main.removeCallbacks(resumeWatch);main.postDelayed(resumeWatch,500);}
    }
    @Override public void onActivityDestroyed(Activity activity){onActivityStopped(activity);}
    @Override public void onActivityCreated(Activity a,Bundle b){}
    @Override public void onActivityResumed(Activity a){}
    @Override public void onActivityPaused(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        closing=true;
        main.removeCallbacks(resumeWatch);
        if(registered)getApplication().unregisterActivityLifecycleCallbacks(this);
        otherScreens.clear();
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        if(watcher!=null){watcher.shutdown();watcher=null;}
        if(reader!=null){reader.shutdown();reader=null;}
        if(ding!=null){ding.shutdown();ding=null;}
        releaseLocks();
        stopForeground(STOP_FOREGROUND_REMOVE);
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
