package com.vobich.bluestar.cloudprobe;

import java.util.ArrayList;
import java.util.List;

public final class CloudOptimizationTest {
    private static void check(boolean good,String why){
        if(!good)throw new AssertionError(why);
    }
    public static void main(String[] args){
        List<String> lines=new ArrayList<>();
        SpeechChunker chunks=new SpeechChunker();
        chunks.append("墙上贴着一张刮痧宣传",lines::add);
        check(lines.isEmpty(),"incomplete clause must wait");
        chunks.append("单，上面写有中医养生和专业学校毕业。",lines::add);
        chunks.finish(lines::add);
        check(lines.size()==2,"early comma boundary and complete final sentence");
        check(String.join("",lines).equals("墙上贴着一张刮痧宣传单，上面写有中医养生和专业学校毕业。"),
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
        System.out.println("CLOUD_OPTIMIZATION_UNIT_TESTS=PASS");
    }
}
