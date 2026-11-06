package com.vobich.bluestar.cloudprobe;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public final class CloudOptimizationTest {
    private static void check(boolean good,String why){
        if(!good)throw new AssertionError(why);
    }
    public static void main(String[] args){
        List<String> lines=new ArrayList<>();
        SpeechChunker chunks=new SpeechChunker();
        chunks.append("墙上贴着一张完整的刮痧宣传",lines::add);
        check(lines.isEmpty(),"incomplete clause must wait");
        chunks.append("单，上面写有中医养生和专业学校毕业。",lines::add);
        chunks.finish(lines::add);
        check(lines.size()==2,"early comma boundary and complete final sentence");
        check(String.join("",lines).equals("墙上贴着一张完整的刮痧宣传单，上面写有中医养生和专业学校毕业。"),
            "no dropped/duplicated characters");
        List<String> shortText=new ArrayList<>();
        SpeechChunker other=new SpeechChunker();
        other.append("菜名：鱼香肉丝，28元",shortText::add);
        check(shortText.isEmpty(),"short comma must not stutter");
        other.finish(shortText::add);
        check(shortText.size()==1,"short output one utterance");
        check(ImageQualityPolicy.pick(1920,"auto",0.002,0.002,90,40)==1280,
            "flat wall is 720p");
        check(ImageQualityPolicy.pick(1920,"auto",0.008,0.082,110,900)==1920,
            "soft but visible details are 1080p");
        check(ImageQualityPolicy.pick(1920,"auto",0.060,0.090,100,800)==1280,
            "clear details are 720p");
        check(ImageQualityPolicy.pick(1920,"auto",0.001,0.081,25,900)==1280,
            "dark photo stays 720p");
        check(ImageQualityPolicy.pick(1280,"auto",0.008,0.081,110,900)==1280,
            "small native source must not upscale");
        check(ImageQualityPolicy.pick(1920,"720",0.008,0.081,110,900)==1280,
            "fixed 720p");
        check(ImageQualityPolicy.pick(1920,"1080",0.060,0.090,100,800)==1920,
            "fixed 1080p");
        // Round-trip every quarter-turn including interleaved VU chroma.
        byte[] frame=new byte[4*2*3/2];
        for(int i=0;i<frame.length;i++)frame[i]=(byte)(i*7+3);
        for(int degrees:new int[]{90,180,270}){
            int w=degrees%180==0?4:2,h=degrees%180==0?2:4;
            byte[] rotated=Nv21Rotation.rotate(frame,4,2,degrees);
            byte[] inverse=Nv21Rotation.rotate(rotated,w,h,360-degrees);
            check(Arrays.equals(frame,inverse),"NV21 orientation roundtrip "+degrees);
        }
        check(Nv21Rotation.rotate(frame,4,2,0)==frame,"0 degree avoids copying");
        check(!Arrays.equals(frame,Nv21Rotation.rotate(frame,4,2,90)),
            "90 degree actually changes sensor data");
        System.out.println("CLOUD_OPTIMIZATION_UNIT_TESTS=PASS");
    }
}
