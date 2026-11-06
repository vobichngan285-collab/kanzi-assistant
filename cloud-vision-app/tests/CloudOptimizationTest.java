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
        String source="益生菌 Probiotics 60g，≥30000 CFU/盒 ★ 新品推荐！";
        String filtered=SpeechFilter.apply(source,true,true,true);
        check(!filtered.contains("Probiotics")&&!filtered.contains("60")
            &&!filtered.contains("CFU")&&!filtered.contains("★"),
            "TTS must filter Latin, digits and symbols");
        check(source.contains("Probiotics")&&source.contains("30000"),
            "source image text must remain intact");
        check(SpeechFilter.apply(source,false,false,false).equals(source),
            "filters default off retains original verbatim");
        check(SpeechFilter.apply("内科3楼205室",false,true,false).contains("内科"),
            "filtered reading retains Chinese context");
        byte[] special=new byte[8*8*3/2];
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)
            special[y*8+x]=(byte)(y*8+x);
        for(int j=64;j<special.length;j++)special[j]=(byte)(j+31);
        Nv21ImageOps.Image mirror=Nv21ImageOps.orient(special,8,8,0,true,false);
        Nv21ImageOps.Image restored=Nv21ImageOps.orient(mirror.bytes,8,8,0,true,false);
        check(Arrays.equals(special,restored.bytes),"NV21 mirrored twice restores pixels and UV");
        Nv21ImageOps.Image cropped=Nv21ImageOps.centerCrop(
            new Nv21ImageOps.Image(special,8,8),2f);
        check(cropped.width==4&&cropped.height==4&&cropped.bytes.length==24,
            "NV21 zoom must crop valid even dimensions and UV");
        Nv21ImageOps.Image shrunk=Nv21ImageOps.resizeLimit(
            new Nv21ImageOps.Image(special,8,8),4);
        check(shrunk.width==4&&shrunk.height==4&&shrunk.bytes.length==24,
            "NV21 resize must preserve YUV420 format");
        check(Nv21ImageOps.resizeLimit(new Nv21ImageOps.Image(special,8,8),16).bytes==special,
            "camera must never upscale original image");
        // A successful HTTP OCR response with empty content is an expected
        // no-text outcome, never a generic cloud/network failure.
        for(String empty:new String[]{null,"","  ","\\n\\t"}){
            boolean typed=false;
            try{OcrResponseGate.requireReadable(empty);}
            catch(OcrResponseGate.NoReadableTextException expected){typed=true;}
            check(typed,"empty successful OCR returns a typed no-text outcome");
        }
        try{
            check(OcrResponseGate.requireReadable("  患者须知\n第二行  ")
                .equals("患者须知\n第二行"),"valid Chinese text must be preserved");
            check(OcrResponseGate.requireReadable("价格 25.00 元").equals("价格 25.00 元"),
                "OCR must not change amounts or insert text");
        }catch(OcrResponseGate.NoReadableTextException unexpected){
            throw new AssertionError("valid OCR should never be classified as no-text",unexpected);
        }
        System.out.println("OCR_EMPTY_RESPONSE_GATE_TESTS=PASS");
        System.out.println("CLOUD_OPTIMIZATION_UNIT_TESTS=PASS");
    }
}
