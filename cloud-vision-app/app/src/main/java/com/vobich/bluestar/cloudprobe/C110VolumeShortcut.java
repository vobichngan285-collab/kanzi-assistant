package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Foreground-only C110+ shutter shortcut, shared by Home, preview and C110 page.
 * Short single presses continue to adjust music volume after the gesture window.
 * No accessibility service, background interception or camera start on app launch.
 */
final class C110VolumeShortcut {
    static final String PREFS="kanzi_settings";
    static final String KEY="c110_quick_photo_volume_gesture";
    static final String[] VALUES={"down_double","up_double","down_hold","up_hold","off"};
    static final String[] LABELS={"双击音量下键","双击音量上键","长按音量下键","长按音量上键","关闭实体快捷键"};
    private static final long DOUBLE_MS=600L,HOLD_MS=650L;
    private static final String DIAG_PREFIX="c110_volume_diag_";
    private final Activity activity;
    private final Runnable shutter;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Runnable pendingVolume,holdTask;
    private long lastUp;
    private boolean down,holdFired,ignoreUp;
    private int activeKey=-1;

    C110VolumeShortcut(Activity activity,Runnable shutter){
        this.activity=activity;this.shutter=shutter;
    }
    static String selected(Context c){
        String saved=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(KEY,VALUES[0]);
        for(String v:VALUES)if(v.equals(saved))return saved;
        return VALUES[0];
    }
    /** Foreground-only key event counters, never record audio, photo, keys or IDs. */
    static String diagnostic(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        int downs=p.getInt(DIAG_PREFIX+"down",0);
        int ups=p.getInt(DIAG_PREFIX+"up",0);
        int fired=p.getInt(DIAG_PREFIX+"shutter",0);
        long interval=p.getLong(DIAG_PREFIX+"interval_ms",-1);
        String last=p.getString(DIAG_PREFIX+"last","还没收到音量键事件");
        String selected=selected(c);
        String label=LABELS[0];
        for(int i=0;i<VALUES.length;i++)if(VALUES[i].equals(selected)){label=LABELS[i];break;}
        return "当前手势："+label+"\n按下事件："+downs+
               "；抬起事件："+ups+"；成功触发："+fired+
               " 次\n最近："+last+
               (interval<0?"":"；两次按下间隔："+interval+"ms");
    }
    static void clearDiagnostic(Context c){
        SharedPreferences.Editor e=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit();
        for(String x:new String[]{"down","up","shutter","interval_ms","last"})
            e.remove(DIAG_PREFIX+x);
        e.apply();
    }
    private void trace(String type,int key,long interval){
        SharedPreferences p=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        SharedPreferences.Editor e=p.edit();
        String k=DIAG_PREFIX+("down".equals(type)?"down":
            "up".equals(type)?"up":"shutter");
        if("down".equals(type)||"up".equals(type)||"shutter".equals(type))
            e.putInt(k,p.getInt(k,0)+1);
        e.putString(DIAG_PREFIX+"last",type+
            "，"+(key==KeyEvent.KEYCODE_VOLUME_DOWN?"音量下":"音量上"));
        if(interval>=0)e.putLong(DIAG_PREFIX+"interval_ms",interval);
        e.apply();
    }
    static void select(Context c,String gesture){
        for(String v:VALUES)if(v.equals(gesture)){
            c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(KEY,gesture).apply();return;
        }
    }
    void cancel(){
        if(pendingVolume!=null)main.removeCallbacks(pendingVolume);
        if(holdTask!=null)main.removeCallbacks(holdTask);
        pendingVolume=null;holdTask=null;down=false;lastUp=0;
        ignoreUp=false;holdFired=false;activeKey=-1;
    }
    private void changeVolume(int key){
        AudioManager audio=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
        if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            key==KeyEvent.KEYCODE_VOLUME_UP?AudioManager.ADJUST_RAISE:
                AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);
    }
    boolean dispatch(KeyEvent event){
        int key=event.getKeyCode();
        if(key!=KeyEvent.KEYCODE_VOLUME_UP&&key!=KeyEvent.KEYCODE_VOLUME_DOWN)return false;
        String mode=selected(activity);
        if("off".equals(mode))return false;
        int configured=mode.startsWith("up_")?KeyEvent.KEYCODE_VOLUME_UP:
            KeyEvent.KEYCODE_VOLUME_DOWN;
        if(key!=configured)return false;
        boolean longMode=mode.endsWith("_hold");
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getRepeatCount()>0)return true;
            long now=event.getEventTime()>0?event.getEventTime():SystemClock.uptimeMillis();
            trace("down",key,-1L);
            if(!longMode&&pendingVolume!=null&&
                C110VolumeTiming.secondDownAfterFirstUp(lastUp,now,DOUBLE_MS)){
                trace("shutter",key,now-lastUp);
                main.removeCallbacks(pendingVolume);pendingVolume=null;
                ignoreUp=true;down=true;activeKey=key;lastUp=0;
                shutter.run();
                return true;
            }
            down=true;holdFired=false;ignoreUp=false;activeKey=key;
            if(longMode){
                if(holdTask!=null)main.removeCallbacks(holdTask);
                holdTask=()->{
                    holdTask=null;
                    if(down&&!holdFired){holdFired=true;trace("shutter",key,-1L);shutter.run();}
                };
                main.postDelayed(holdTask,HOLD_MS);
            }
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_UP){
            trace("up",key,-1L);
            if(activeKey!=key)return true;
            down=false;activeKey=-1;
            if(longMode){
                if(holdTask!=null){main.removeCallbacks(holdTask);holdTask=null;}
                if(!holdFired)changeVolume(key);
            }else if(ignoreUp){ignoreUp=false;}
            else{
                if(pendingVolume!=null)main.removeCallbacks(pendingVolume);
                lastUp=event.getEventTime()>0?event.getEventTime():SystemClock.uptimeMillis();
                pendingVolume=()->{pendingVolume=null;lastUp=0;changeVolume(key);};
                main.postDelayed(pendingVolume,DOUBLE_MS);
            }
            return true;
        }
        return false;
    }
}
