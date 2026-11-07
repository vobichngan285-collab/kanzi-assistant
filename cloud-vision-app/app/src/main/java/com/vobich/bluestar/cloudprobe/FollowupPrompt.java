package com.vobich.bluestar.cloudprobe;

import java.util.List;

/** Builds one bounded, source-grounded text-only request without any image. */
final class FollowupPrompt {
    private FollowupPrompt(){}

    static String build(String task,String observed,List<String> turns,String question){
        return build(task,observed,turns,question,false,false);
    }

    static String build(String task,String observed,List<String> turns,
                        String question,boolean allowWeb){
        return build(task,observed,turns,question,false,allowWeb);
    }

    static String buildForImage(String task,String observed,List<String> turns,String question){
        return build(task,observed,turns,question,true,false);
    }

    static String buildForImage(String task,String observed,List<String> turns,
                                String question,boolean allowWeb){
        return build(task,observed,turns,question,true,allowWeb);
    }

    /** No old photo is necessary for ordinary chat; don't fabricate one. */
    static String buildStandalone(List<String> turns,String question,boolean allowWeb){
        if(question==null||question.trim().isEmpty())
            throw new IllegalArgumentException("请说出要问的问题");
        StringBuilder prompt=new StringBuilder(1800);
        prompt.append("你是中文语音助手。目前没有照片供你查看，不得声称看到了画面。")
            .append("回答用户的问题，简短准确，不用Markdown。");
        appendWebHint(prompt,allowWeb);
        appendTurns(prompt,turns);
        return prompt.append("\n当前用户问题：").append(limit(question,420)).toString();
    }

    private static void appendWebHint(StringBuilder prompt,boolean allowWeb){
        if(allowWeb)
            prompt.append("需要最新外部信息时可联网核对；区分搜索信息与照片内容，")
                .append("没有可靠依据则说明不确定，不得编造来源。");
    }

    private static void appendTurns(StringBuilder prompt,List<String> turns){
        if(turns==null)return;
        int start=Math.max(0,turns.size()-3);
        for(int i=start;i<turns.size();i++)
            prompt.append("\n此前问答：").append(limit(turns.get(i),380));
    }

    private static String build(String task,String observed,List<String> turns,
                                String question,boolean hasImage,boolean allowWeb){
        if(observed==null||observed.trim().isEmpty())
            throw new IllegalArgumentException("没有可分析的文字");
        if(question==null||question.trim().isEmpty())
            throw new IllegalArgumentException("请说出要问的问题");
        StringBuilder prompt=new StringBuilder(4600);
        prompt.append("你是辅助视障者的中文语音助手。以下是此前一次拍照已经识别出的文字或画面摘要，");
        if(hasImage)prompt.append("本次用户明确要求重新查看旧照片，结合此照片与已有文字回答；");
        else prompt.append("现在没有重新查看照片的权限。优先基于已有内容回答；");
        prompt.append("可以计算、整理、解释已识别信息，但不得编造未识别的价格、物品、方位和文字。");
        if(!allowWeb)prompt.append("如果已有材料不足，直接说明哪些信息无法确认，并建议用户明确要求重新看图。");
        else prompt.append("如需辨认模糊细节，仍须明确要求重新看图。");
        prompt.append("只输出简短口语中文，不用Markdown。\n之前的任务：")
            .append(limit(task,48))
            .append("\n此前识别内容：").append(limit(observed,3500));
        appendWebHint(prompt,allowWeb);
        appendTurns(prompt,turns);
        return prompt.append("\n当前用户问题：").append(limit(question,420)).toString();
    }
    private static String limit(String s,int length){
        if(s==null)return "";
        String clean=s.trim();
        return clean.length()>length?clean.substring(0,length):clean;
    }
}
