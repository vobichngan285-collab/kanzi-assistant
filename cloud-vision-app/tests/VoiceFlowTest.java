package com.vobich.bluestar.cloudprobe;

import java.nio.charset.StandardCharsets;

public final class VoiceFlowTest {
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    private static void assertIntent(String utterance,VoiceAction.Type type,String target){
        VoiceAction action=VoiceAction.classify(utterance);
        check(action.type==type,"Wrong intent for '"+utterance+"': "+action.type);
        check(action.target.equals(target),"Wrong target for '"+utterance+"': '"+action.target+"'");
    }
    private static int u32(byte[] a,int offset){
        return (a[offset]&255)|((a[offset+1]&255)<<8)|
            ((a[offset+2]&255)<<16)|((a[offset+3]&255)<<24);
    }
    private static String ascii(byte[] a,int off,int length){
        return new String(a,off,length,StandardCharsets.US_ASCII);
    }
    public static void main(String[] args)throws Exception{
        assertIntent("帮我找超市。",VoiceAction.Type.START_FINDING,"超市");
        assertIntent("帮我找附近的超市",VoiceAction.Type.START_FINDING,"超市");
        assertIntent("帮我看超市",VoiceAction.Type.START_FINDING,"超市");
        assertIntent("我想找药店",VoiceAction.Type.START_FINDING,"药店");
        assertIntent("改找药店",VoiceAction.Type.START_FINDING,"药店");
        assertIntent("不找超市了，改找药店",VoiceAction.Type.START_FINDING,"药店");
        assertIntent("帮我找一家超市",VoiceAction.Type.START_FINDING,"超市");
        assertIntent("停止寻找",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("取消找门",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("取消掉找门",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("停止找门",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("结束找门任务",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("把找门任务取消",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("不要再找门了",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("不找门了",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("取消当前寻找任务",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("取消找门，改找超市",VoiceAction.Type.START_FINDING,"超市");
        assertIntent("不找超市了，改找药店",VoiceAction.Type.START_FINDING,"药店");
        assertIntent("不要取消寻找",VoiceAction.Type.QUESTION,"");
        assertIntent("别停止寻找",VoiceAction.Type.QUESTION,"");
        assertIntent("停止寻找是什么意思",VoiceAction.Type.QUESTION,"");
        // Active target assists short cancellation, without extra AI calls.
        check(VoiceAction.classify("取消这个任务","门").type==VoiceAction.Type.STOP_FINDING,
            "cancel this task if a search is active");
        check(VoiceAction.classify("取消掉掌门","门").type==VoiceAction.Type.STOP_FINDING,
            "conservative correction when 找门 ASR is misheard as 掌门");
        check(VoiceAction.classify("取消掉掌门","").type==VoiceAction.Type.QUESTION,
            "do not infer a target if no finding is active");
        check(VoiceAction.classify("不要取消寻找","门").type==VoiceAction.Type.QUESTION,
            "negative cancellation must not clear target");
        check(VoiceAction.classify("取消订单","门").type==VoiceAction.Type.QUESTION,
            "unrelated cancellation must not stop navigation");
        check(VoiceAction.classify("找门了吗","门").type==VoiceAction.Type.QUESTION,
            "target-status questions are not cancellations");
        assertIntent("不找超市了",VoiceAction.Type.STOP_FINDING,"");
        assertIntent("这是什么",VoiceAction.Type.QUESTION,"");
        assertIntent("帮我看这个是什么",VoiceAction.Type.QUESTION,"");
        assertIntent("刚才那张照片里有什么",VoiceAction.Type.QUESTION,"");

        // No saved image: voice should directly trigger fresh-scene capture.
        assertIntent("帮我看看眼前的事物",VoiceAction.Type.OBSERVE_SCENE,"");
        assertIntent("帮我看看眼前有什么",VoiceAction.Type.OBSERVE_SCENE,"");
        assertIntent("看看前面有什么",VoiceAction.Type.OBSERVE_SCENE,"");
        assertIntent("帮我观察一下周围环境",VoiceAction.Type.OBSERVE_SCENE,"");
        assertIntent("再看看左边有什么",VoiceAction.Type.OBSERVE_SCENE,"");
        assertIntent("眼前的场景是什么样的",VoiceAction.Type.OBSERVE_SCENE,"");
        // RC18 reported regression: "我眼前是什么" fell through to text-only
        // web chat, so the model said it cannot see and asked for a photo.
        for(String spoken:new String[]{
            "我眼前是什么","我眼前是什么？","眼前是什么","眼前是什么呀",
            "现在我眼前是什么","前面是什么","我前面是什么东西",
            "我眼前是啥","帮我看看眼前是什么","看看眼前是什么"}) {
            assertIntent(spoken,VoiceAction.Type.OBSERVE_SCENE,"");
        }
        // Do not turn historical-image discussion or live web Q&A into a
        // new photo or override the existing user's explicit task type.
        assertIntent("刚才我眼前是什么",VoiceAction.Type.QUESTION,"");
        assertIntent("上一张照片眼前是什么",VoiceAction.Type.QUESTION,"");
        assertIntent("今天有什么最新新闻",VoiceAction.Type.QUESTION,"");
        assertIntent("这家店的营业时间是什么",VoiceAction.Type.QUESTION,"");
        assertIntent("请读一下眼前的文字",VoiceAction.Type.READ_TEXT,"");
        assertIntent("帮我读一下文字",VoiceAction.Type.READ_TEXT,"");
        assertIntent("念一下这张标签",VoiceAction.Type.READ_TEXT,"");
        // Explicit old-photo recheck is the ONLY follow-up that re-uploads image.
        assertIntent("重新看一下刚才那张照片",VoiceAction.Type.REVIEW_IMAGE,"");
        assertIntent("再仔细看这张照片",VoiceAction.Type.REVIEW_IMAGE,"");
        // Preserve existing target-finding behavior; no broad "帮我看" hijack.
        assertIntent("看看有没有超市",VoiceAction.Type.START_FINDING,"超市");
        assertIntent("帮我找附近的超市",VoiceAction.Type.START_FINDING,"超市");
        assertIntent("刚才的菜单一共多少钱",VoiceAction.Type.QUESTION,"");
        assertIntent("哪道菜最便宜",VoiceAction.Type.QUESTION,"");

        VoicePressState m=new VoicePressState();
        m.down(false);
        check(m.up()==VoicePressState.Decision.NONE,
            "permission denied must never send voice");
        m.down(true);
        check(m.isHeld(),"hold should be active");
        check(m.up()==VoicePressState.Decision.SEND,"release must send");
        check(m.up()==VoicePressState.Decision.NONE,"duplicate up must not send again");
        m.down(true);
        check(m.move(-110,-100),"swipe-to-cancel should activate");
        check(m.up()==VoicePressState.Decision.CANCEL,"cancelled swipe cannot send");
        m.down(true);
        check(m.interrupt()==VoicePressState.Decision.CANCEL,
            "Android ACTION_CANCEL must discard");
        check(m.up()==VoicePressState.Decision.NONE,
            "canceled voice must not send during late ACTION_UP");

        byte[] pcm=new byte[16000*2];
        for(int i=0;i<pcm.length;i++)pcm[i]=(byte)(i%223);
        byte[] wav=VoiceWav.wrap(pcm);
        check(wav.length==pcm.length+44,"RIFF size must be PCM+44 bytes");
        check(ascii(wav,0,4).equals("RIFF"),"not a WAV RIFF header");
        check(ascii(wav,8,4).equals("WAVE"),"not a WAVE header");
        check(u32(wav,4)==pcm.length+36,"incorrect RIFF data length");
        check(u32(wav,24)==16000&&u32(wav,28)==32000,
            "WAV sample rate or byte rate incorrect");
        check(u32(wav,40)==pcm.length,"WAV data chunk length incorrect");
        for(int i=0;i<pcm.length;i++)
            if(wav[i+44]!=pcm[i])throw new AssertionError("PCM byte mismatch "+i);
        boolean threw=false;
        try{VoiceWav.wrap(new byte[3]);}catch(Exception expected){threw=true;}
        check(threw,"odd-size PCM must be rejected");
        System.out.println("VOICE_PRESS_TARGET_AND_WAV_TESTS=PASS");
    }
}
