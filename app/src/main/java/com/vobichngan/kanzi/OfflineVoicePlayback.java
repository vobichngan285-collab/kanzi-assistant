package com.vobichngan.kanzi;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Play downloaded offline voices without involving Android system TTS.
 * One generation token guards interruption so page changes never cause an
 * earlier native inference to play a stale cover from its first line.
 */
public final class OfflineVoicePlayback {
    public interface Callback {
        void completed(String utteranceId);
        void failed(String utteranceId, String reason);
    }

    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(r,"KanziOfflineTts");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicInteger generation=new AtomicInteger(0);
    private final OfflineVoiceSynthesizer synthesizer;
    private volatile AudioTrack activeTrack;
    private volatile boolean closed;

    public OfflineVoicePlayback(DownloadableVoiceStore store) {
        synthesizer=new OfflineVoiceSynthesizer(store);
    }

    public void speak(DownloadableVoiceStore.Voice voice,String text,float speed,
                      String utteranceId,Callback callback) {
        int epoch=generation.incrementAndGet();
        stopTrack();
        worker.execute(() -> {
            if(closed||epoch!=generation.get())return;
            AudioTrack track=null;
            try {
                // Loading the neural model may take seconds on a low-memory
                // phone; do it off the main thread and reject stale results.
                OfflineVoiceSynthesizer.Audio audio=synthesizer.synthesize(voice,text,speed);
                if(closed||epoch!=generation.get())return;
                int sampleRate=audio.getSampleRate();
                int min=AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
                if(min<0)throw new IllegalStateException("音频设备不支持所选采样率");
                track=new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(min,32768))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
                if(track.getState()!=AudioTrack.STATE_INITIALIZED)
                    throw new IllegalStateException("离线语音播放器无法初始化");
                activeTrack=track;
                track.play();
                float[] samples=audio.getSamples();
                short[] pcm=new short[4096];
                int pos=0;
                while(pos<samples.length&&!closed&&epoch==generation.get()){
                    int n=Math.min(pcm.length,samples.length-pos);
                    for(int i=0;i<n;i++){
                        float value=Math.max(-1f,Math.min(1f,samples[pos+i]));
                        pcm[i]=(short)Math.round(value*32767f);
                    }
                    int sent=0;
                    while(sent<n&&!closed&&epoch==generation.get()){
                        int wrote=track.write(pcm,sent,n-sent,AudioTrack.WRITE_BLOCKING);
                        if(wrote<=0)throw new IllegalStateException("离线音频输出失败："+wrote);
                        sent+=wrote;
                    }
                    pos+=n;
                }
                if(closed||epoch!=generation.get())return;
                // A PCM stream can still have buffered audio after write().
                // Wait for its actual playback head before advancing the
                // Java speech queue, or the end of a sentence is cut off.
                long started=System.currentTimeMillis();
                while(track.getPlaybackHeadPosition()<samples.length &&
                    epoch==generation.get()&&!closed &&
                    System.currentTimeMillis()-started<120000L){
                    try { Thread.sleep(25); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); return;
                    }
                }
                if(epoch==generation.get()&&!closed)
                    main.post(() -> { if(epoch==generation.get())callback.completed(utteranceId); });
            } catch(Throwable error){
                Log.e("KanziOfflineTts","Local voice failed",error);
                if(epoch==generation.get()&&!closed)
                    main.post(() -> { if(epoch==generation.get())callback.failed(
                        utteranceId,error.getMessage()==null?"离线语音生成失败":error.getMessage()); });
            } finally {
                if(activeTrack==track)activeTrack=null;
                if(track!=null){
                    try{track.stop();}catch(Throwable ignored){}
                    try{track.release();}catch(Throwable ignored){}
                }
            }
        });
    }

    private void stopTrack() {
        AudioTrack old=activeTrack;
        activeTrack=null;
        if(old!=null) {
            try{old.pause();old.flush();old.stop();}catch(Throwable ignored){}
        }
    }

    public void stop() {
        generation.incrementAndGet();
        stopTrack();
    }
    public void shutdown() {
        closed=true;
        stop();
        // Release the native engine on its OWN worker after any synthesis
        // currently in progress has finished. Never free it from the UI
        // thread while JNI could be generating another audio chunk.
        worker.execute(synthesizer::release);
        worker.shutdown();
    }
}
