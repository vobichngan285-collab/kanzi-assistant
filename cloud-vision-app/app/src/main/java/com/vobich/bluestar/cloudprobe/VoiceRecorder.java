package com.vobich.bluestar.cloudprobe;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.content.Context;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit press-to-talk: no voice activity gate, no 6.5s silence timeout.
 * Only a real ACTION_UP can request a send; ACTION_CANCEL discards.
 * All AudioRecord ownership and release stays on one worker thread.
 */
final class VoiceRecorder {
    interface Listener {
        void onReady(String inputDescription);
        void onFinished(byte[] wav,int durationMs,int peak,int rms,String inputDescription);
        void onFailure(String reason);
    }
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final AtomicBoolean finishRequested=new AtomicBoolean(false);
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private volatile AudioRecord active;
    boolean isBusy(){return busy.get();}

    boolean begin(Context context,Listener listener){
        if(!busy.compareAndSet(false,true))return false;
        finishRequested.set(false);cancelled.set(false);
        new Thread(()->capture(context,listener),"BlueStarHoldToTalk").start();
        return true;
    }
    /** Called only from ACTION_UP: keep whatever PCM was actually captured. */
    void finish(){finishRequested.set(true);}
    /** Called from ACTION_CANCEL, lifecycle teardown or swipe-to-cancel. */
    void cancel(){cancelled.set(true);finishRequested.set(true);}

    private void capture(Context context,Listener listener){
        AudioRecord record=null;
        ByteArrayOutputStream pcm=new ByteArrayOutputStream(16000);
        int samples=0,peak=0;
        long squared=0;
        String input="默认手机麦克风";
        try{
            AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if(manager!=null&&manager.isMicrophoneMute())
                throw new IOException("系统麦克风已静音，请先取消静音");
            int min=AudioRecord.getMinBufferSize(VoiceWav.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IOException("设备不支持16kHz单声道录音："+min);
            int capacity=Math.max(min*2,VoiceWav.SAMPLE_RATE/2);
            record=makeRecorder(MediaRecorder.AudioSource.MIC,capacity);
            if(record==null)
                record=makeRecorder(MediaRecorder.AudioSource.VOICE_RECOGNITION,capacity);
            if(record==null)throw new IOException("手机麦克风没有初始化成功");
            active=record;
            record.startRecording();
            if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IOException("麦克风未进入录音状态");
            AudioDeviceInfo routed=record.getRoutedDevice();
            if(routed!=null){
                String product=routed.getProductName()==null?"":routed.getProductName().toString();
                input=product.length()>55?product.substring(0,55):product;
            }
            final String chosenInput=input;
            listener.onReady(chosenInput);
            short[] buffer=new short[1600]; // 100ms; release stops within one block
            long start=SystemClock.elapsedRealtime();
            while(!finishRequested.get()&&!cancelled.get() &&
                    SystemClock.elapsedRealtime()-start<25000){
                int n=record.read(buffer,0,buffer.length,AudioRecord.READ_BLOCKING);
                if(n<=0)throw new IOException("录音数据读取失败，错误码："+n);
                for(int i=0;i<n;i++){
                    int v=buffer[i];
                    int magnitude=v==Short.MIN_VALUE?32768:Math.abs(v);
                    if(magnitude>peak)peak=magnitude;
                    squared+=(long)v*v;
                    pcm.write(v&255);pcm.write((v>>8)&255);
                }
                samples+=n;
            }
            if(cancelled.get())return;
            if(!finishRequested.get())
                throw new IOException("按住录音超过25秒，请分段提问");
            int durationMs=samples*1000/VoiceWav.SAMPLE_RATE;
            if(durationMs<350)throw new IOException("按住时间太短，请按住说话再松开");
            if(peak==0)throw new IOException("麦克风返回全零音频，请检查录音权限和输入设备");
            int rms=(int)Math.sqrt((double)squared/Math.max(1,samples));
            // Never discard quiet but valid audio by an arbitrary threshold.
            listener.onFinished(VoiceWav.wrap(pcm.toByteArray()),durationMs,
                peak,rms,chosenInput);
        }catch(SecurityException e){
            if(!cancelled.get())listener.onFailure("没有麦克风权限");
        }catch(Exception e){
            if(!cancelled.get())listener.onFailure(e.getMessage()==null?"录音失败":e.getMessage());
        }finally{
            if(record!=null){
                try{if(record.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)record.stop();}
                catch(RuntimeException ignored){}
                record.release();
            }
            active=null;
            busy.set(false);
        }
    }
    private AudioRecord makeRecorder(int source,int capacity){
        try{
            AudioRecord candidate=new AudioRecord(source,VoiceWav.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,capacity);
            if(candidate.getState()==AudioRecord.STATE_INITIALIZED)return candidate;
            candidate.release();
        }catch(RuntimeException ignored){}
        return null;
    }
}
