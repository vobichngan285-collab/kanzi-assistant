package com.vobich.bluestar.cloudprobe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Pure Java RIFF PCM encoder. Validated with deterministic JVM tests. */
final class VoiceWav {
    static final int SAMPLE_RATE=16000;
    private VoiceWav(){}
    static byte[] wrap(byte[] pcm) throws IOException {
        if(pcm==null||pcm.length<2||(pcm.length&1)!=0||pcm.length>960000)
            throw new IOException("录音数据长度无效");
        ByteArrayOutputStream out=new ByteArrayOutputStream(pcm.length+44);
        ascii(out,"RIFF");i32(out,36+pcm.length);ascii(out,"WAVE");ascii(out,"fmt ");
        i32(out,16);i16(out,1);i16(out,1);i32(out,SAMPLE_RATE);
        i32(out,SAMPLE_RATE*2);i16(out,2);i16(out,16);
        ascii(out,"data");i32(out,pcm.length);out.write(pcm);
        return out.toByteArray();
    }
    private static void ascii(ByteArrayOutputStream b,String s) throws IOException{
        b.write(s.getBytes("US-ASCII"));
    }
    private static void i32(ByteArrayOutputStream b,int n){
        b.write(n&255);b.write((n>>>8)&255);b.write((n>>>16)&255);b.write((n>>>24)&255);
    }
    private static void i16(ByteArrayOutputStream b,int n){
        b.write(n&255);b.write((n>>>8)&255);
    }
}
