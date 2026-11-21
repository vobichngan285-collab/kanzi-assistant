package com.vobich.bluestar.cloudprobe;

/** Spoken target grammar used identically by Home and Preview. */
public final class VoiceActionSharedTest {
    private static void match(String input,VoiceAction.Type expected,String target){
        VoiceAction action=VoiceAction.classify(input,"");
        if(action.type!=expected||!target.equals(action.target))
            throw new AssertionError(input+" -> "+action.type+"/"+action.target+
                ", expected "+expected+"/"+target);
    }
    public static void main(String[] args){
        match("帮我找医院的招牌",VoiceAction.Type.START_FINDING,"医院的招牌");
        match("帮我找一个招牌",VoiceAction.Type.START_FINDING,"招牌");
        match("如果看到医院的招牌立马告诉我",
              VoiceAction.Type.START_FINDING,"医院的招牌");
        match("如果你看到医院招牌就告诉我",
              VoiceAction.Type.START_FINDING,"医院招牌");
        match("发现药店提醒我",VoiceAction.Type.START_FINDING,"药店");
        match("看到医院的招牌，马上告诉我！",
              VoiceAction.Type.START_FINDING,"医院的招牌");
        match("医院在哪里",VoiceAction.Type.QUESTION,"");
        match("医院的招牌是什么意思",VoiceAction.Type.QUESTION,"");
        VoiceAction stop=VoiceAction.classify("停止寻找","医院的招牌");
        if(stop.type!=VoiceAction.Type.STOP_FINDING)
            throw new AssertionError("STOP_FINDING regression");
        if(VoiceAction.classify("不要停止找医院","医院").type==
           VoiceAction.Type.STOP_FINDING)
            throw new AssertionError("Negative cancellation");
        System.out.println("HOME_PREVIEW_SHARED_VOICE_INTENTS=PASS");
    }
}
