package com.vobich.bluestar.cloudprobe;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single short push-on voice clip; auto-end after a natural silence. */
final class VoiceRecorder {
    interface Listener {
        void onListening();
        void onSilence();
        void onFinished(byte[] wav);
        void onFailure(String message);
    }
    private static final int SAMPLE_RATE=16000;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private volatile AudioRecord current;
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private volatile Thread worker;

    boolean isRunning(){return running.get();}

    void begin(Listener listener){
        if(!running.compareAndSet(false,true))return;
        cancelled.set(false);
        worker=new Thread(()->capture(listener),"BlueStarVoiceCapture");
        worker.start();
    }

    /** A second tap finishes immediately; lifecycle pause discards the sample. */
    void cancel(){cancelled.set(true);running.set(false);}

    private void capture(Listener listener){
        AudioRecord record=null;
        ByteArrayOutputStream pcm=new ByteArrayOutputStream();
        int voicedMs=0,silentMs=0,totalMs=0;
        boolean speaking=false;
        boolean endedSilence=false;
        try{
            int buffer=Math.max(6400,AudioRecord.getMinBufferSize(
                SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)*2);
            if(buffer<=0)throw new IOException("设备不支持16000Hz录音");
            record=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);
            if(record.getState()!=AudioRecord.STATE_INITIALIZED)
                throw new IOException("麦克风未能初始化");
            current=record;
            record.startRecording();
            listener.onListening();
            short[] samples=new short[1600]; // 100ms
            long began=SystemClock.elapsedRealtime();
            while(running.get() && SystemClock.elapsedRealtime()-began<16000){
                int n=record.read(samples,0,samples.length,AudioRecord.READ_BLOCKING);
                if(n<=0)throw new IOException("麦克风读取失败（"+n+"）");
                long sum=0;
                for(int k=0;k<n;k++){
                    int value=samples[k];
                    sum+=(long)value*value;
                    pcm.write(value&255);pcm.write((value>>8)&255);
                }
                int ms=n*1000/SAMPLE_RATE;
                totalMs+=ms;
                double rms=Math.sqrt((double)sum/n);
                // Conservative noise-gate; stop only after sustained voiced audio.
                if(rms>420){
                    voicedMs+=ms; silentMs=0;
                    if(voicedMs>=250)speaking=true;
                }else if(speaking){
                    silentMs+=ms;
                    if(silentMs>=1250 && voicedMs>=300){
                        endedSilence=true;
                        break;
                    }
                }else{
                    voicedMs=0;
                }
                if(!speaking&&totalMs>=6500)throw new IOException("没有听到语音，请靠近手机麦克风重试");
            }
            if(cancelled.get())return;
            if(!speaking||voicedMs<300)throw new IOException("没有听清语音，请重试");
            if(endedSilence)listener.onSilence();
            byte[] payload=pcm.toByteArray();
            listener.onFinished(wav(payload));
        }catch(SecurityException ex){
            listener.onFailure("麦克风权限不足，请检查应用录音权限");
        }catch(Exception ex){
            if(!cancelled.get())listener.onFailure(ex.getMessage()==null?"录音失败":ex.getMessage());
        }finally{
            running.set(false);
            current=null;
            if(record!=null){
                try{record.stop();}catch(IllegalStateException ignored){}
                record.release();
            }
        }
    }

    private static byte[] wav(byte[] pcm) throws IOException {
        if(pcm.length<2||pcm.length>16*16000*2+1000)
            throw new IOException("录音大小异常");
        ByteArrayOutputStream output=new ByteArrayOutputStream(pcm.length+44);
        writeTag(output,"RIFF");writeInt(output,36+pcm.length);
        writeTag(output,"WAVE");writeTag(output,"fmt ");
        writeInt(output,16);
        writeShort(output,1); // PCM
        writeShort(output,1); // mono
        writeInt(output,SAMPLE_RATE);
        writeInt(output,SAMPLE_RATE*2);
        writeShort(output,2);writeShort(output,16);
        writeTag(output,"data");writeInt(output,pcm.length);
        output.write(pcm);
        return output.toByteArray();
    }
    private static void writeTag(ByteArrayOutputStream output,String text)throws IOException{
        output.write(text.getBytes("US-ASCII"));
    }
    private static void writeInt(ByteArrayOutputStream out,int value){
        out.write(value&255);out.write((value>>8)&255);
        out.write((value>>16)&255);out.write((value>>24)&255);
    }
    private static void writeShort(ByteArrayOutputStream out,int value){
        out.write(value&255);out.write((value>>8)&255);
    }
}
