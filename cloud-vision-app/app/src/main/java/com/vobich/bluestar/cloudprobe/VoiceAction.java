package com.vobich.bluestar.cloudprobe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative spoken-command router. Unclear speech remains a photo follow-up,
 * never silently overwrites a saved search target.
 */
final class VoiceAction {
    enum Type { START_FINDING, STOP_FINDING, OBSERVE_SCENE, READ_TEXT, REVIEW_IMAGE, QUESTION }
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
        // Explicit intent must take precedence over the broad historical
        // "帮我看..." target-search grammar. These commands capture a NEW frame.
        if(isReadTextRequest(v))return new VoiceAction(Type.READ_TEXT,"");
        if(isReviewPhotoRequest(v))return new VoiceAction(Type.REVIEW_IMAGE,"");
        if(isObserveRequest(v))return new VoiceAction(Type.OBSERVE_SCENE,"");
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

    private static boolean isReadTextRequest(String v){
        // Do not silently recapture a historical OCR transcript.
        if(v.contains("刚才")||v.contains("上一张")||v.contains("上张"))return false;
        boolean text=v.matches(".*(文字|字迹|汉字|字牌|标签|说明书|菜单上的字|路牌上的字).*");
        boolean read=v.matches(".*(读|念|朗读|识字|识别文字|看字|看一下|看看|看).*");
        return text&&read||v.matches(".*(念一下|读一下|帮我读|朗读|读出来|读给我听).*");
    }

    private static boolean isReviewPhotoRequest(String v){
        boolean photo=v.matches(".*(刚才|上一张|上张|原图|这张照片|那张照片|之前拍的|之前那张).*");
        boolean explicit=v.matches(".*(重新看|重新分析|重新识别|再看|再检查|仔细看|重新检查|重新观察).*");
        return photo&&explicit || v.matches(".*(重新看图|重新看照片|再仔细看图).*");
    }

    private static boolean isObserveRequest(String v){
        if(v.contains("有没有")||v.contains("帮我找")||v.contains("寻找")||
           v.contains("改找")||v.contains("换找"))return false;
        if(v.matches(".*(眼前|前面|周围|身边|四周|环境|场景|这里|这儿).*"+
            "(有什么|有些什么|有哪些|什么东西|是什么样|什么情况|什么事物|事物|景象|物体|场景).*"))
            return true;
        if(v.matches(".*(看|观察|描述|分析|识别).*"+
            "(眼前|前面|周围|身边|四周|环境|场景|周边|这里|这儿).*"))
            return true;
        return v.matches(".*(再看看|再看|看看|看一下).*(左边|右边|前方|后面).*(有什么|有些什么|是什么).*");
    }
}
