package com.vobich.bluestar.cloudprobe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative spoken-command router. Unclear speech remains a photo follow-up,
 * never silently overwrites a saved search target.
 */
final class VoiceAction {
    enum Type { START_FINDING, STOP_FINDING, QUESTION }
    final Type type;
    final String target;
    private VoiceAction(Type type,String target){this.type=type;this.target=target;}
    static VoiceAction classify(String utterance){
        String v=utterance==null?"":utterance.trim()
            .replaceAll("[。？！!?,，；;\\s]+$","");
        v=v.replace("帮我寻找","帮我找").replace("找一下","找")
             .replace("找一找","找").replace("查找","找")
             .replace("帮我看看","帮我看");
        if(v.isEmpty())return new VoiceAction(Type.QUESTION,"");
        // When user says "不找超市了，改找药店", the replacement wins.
        int change=v.lastIndexOf("改找");
        if(change<0)change=v.lastIndexOf("换成找");
        if(change>=0)v=v.substring(change);
        if(v.matches(".*(停止寻找|取消寻找|结束寻找|暂停寻找|不找了|不用找了|别找了|停止找|不需要找了|不找.{1,16}了).*"))
            return new VoiceAction(Type.STOP_FINDING,"");
        Matcher command=Pattern.compile(
            "^(?:现在|以后|接下来|麻烦|请|能不能|我想|我要|你能|给我)?(?:帮我|替我|给我)?"+
            "(?:改找|换成找|换找|继续找|找|寻找|留意|注意找|帮忙找|看有没有|看看有没有|看一下|看看|看)"+
            "(?:一下|一处|一个|一家|附近的|周围的|前面的|身边的|周边的|附近|周围|前面|身边|周边|有无|有没有)*"+
            "(.+)$").matcher(v);
        if(!command.matches())
            return new VoiceAction(Type.QUESTION,"");
        String target=command.group(1);
        target=target.replaceAll("^(?:有没有|一[家个处]?(?:卖|叫)|的|到|个|家|一个|一家|附近的|周围的)+","");
        target=target.replaceAll("(?:在哪里|在哪儿|在哪|的位置|有没有|在什么地方|的招牌|招牌|吧|呀|好吗|可以吗|谢谢|一下)+$","");
        target=target.trim();
        if(target.length()<1||target.length()>28 ||
            target.contains("这张")||target.contains("这个")||target.contains("那张")||target.contains("刚才")||target.contains("是什么")||target.contains("那是什么"))
            return new VoiceAction(Type.QUESTION,"");
        return new VoiceAction(Type.START_FINDING,target);
    }
}
