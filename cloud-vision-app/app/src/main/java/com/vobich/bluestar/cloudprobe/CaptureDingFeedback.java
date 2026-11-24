package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * One phone-side metal "ding" for each genuinely completed C110 JPEG.
 *
 * Independent from C110's OEM 9011 body buzzer and the cloud/TTS pipeline.
 * Preloads the SAME asset and sound preference as MainActivity's signalCapture.
 * Never rings for network failures, failed shutters, retries or activity teardown.
 */
final class CaptureDingFeedback {
    private static final String TAG="BlueStarCaptureDing";
    private static final String SETTINGS="kanzi_settings";
    private static final String ENABLED="shutter_feedback_sound";
    private final Context context;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private SoundPool pool;
    private boolean loaded,closed,pending;
    private int sampleId;

    CaptureDingFeedback(Context owner){
        context=owner.getApplicationContext();
        try{
            AudioAttributes attributes=new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            pool=new SoundPool.Builder().setMaxStreams(1)
                .setAudioAttributes(attributes).build();
            pool.setOnLoadCompleteListener((p,id,status)->ui.post(()->{
                if(closed||pool==null||id!=sampleId)return;
                loaded=status==0;
                if(status!=0)Log.w(TAG,"Ding asset could not load: "+status);
                if(pending){
                    pending=false;
                    if(loaded&&soundEnabled())play();
                }
            }));
            sampleId=pool.load(owner,R.raw.c_metal_clack_ding,1);
        }catch(RuntimeException ex){
            Log.w(TAG,"Ding initialization failed",ex);
            if(pool!=null){pool.release();pool=null;}
        }
    }

    /** Call exactly once after receiving a genuine new C110 original JPEG. */
    void photoSucceeded(){
        if(closed||!soundEnabled()||pool==null||sampleId<=0)return;
        if(loaded)play();
        else pending=true;  // Fast captures should not be silently dropped.
    }

    private boolean soundEnabled(){
        SharedPreferences p=context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
        return p.getBoolean(ENABLED,true);
    }

    private void play(){
        if(pool==null||closed||!loaded)return;
        if(pool.play(sampleId,1f,1f,2,0,1f)==0)
            Log.w(TAG,"SoundPool did not accept completed-photo ding");
    }

    void shutdown(){
        closed=true;pending=false;
        if(pool!=null){pool.release();pool=null;}
    }
}
