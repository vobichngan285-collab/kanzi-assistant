package com.vobich.bluestar.cloudprobe;

import java.util.List;

/** Builds one bounded, source-grounded text-only request without any image. */
final class FollowupPrompt {
    private FollowupPrompt(){}

    static String build(String task,String observed,List<String> turns,String question){
        if(observed==null||observed.trim().isEmpty())
            throw new IllegalArgumentException("没有可分析的文字");
        if(question==null||question.trim().isEmpty())
            throw new IllegalArgumentException("请说出要问的问题");
        StringBuilder prompt=new StringBuilder(4600);
        prompt.append("你是辅助视障者的中文语音助手。以下是此前一次拍照已经识别出的文字或画面摘要，")
            .append("现在没有重新查看照片的权限。优先基于已有内容回答；")
            .append("可以计算、整理、解释已识别信息，但不得编造未识别的价格、物品、方位和文字。")
            .append("如果已有材料不足，直接说明哪些信息无法确认，并建议用户明确要求重新看图。")
            .append("只输出简短口语中文，不用Markdown。\n之前的任务：")
            .append(limit(task,48))
            .append("\n此前识别内容：").append(limit(observed,3500));
        if(turns!=null){
            int start=Math.max(0,turns.size()-3);
            for(int i=start;i<turns.size();i++)
                prompt.append("\n此前问答：").append(limit(turns.get(i),380));
        }
        return prompt.append("\n当前用户问题：").append(limit(question,420)).toString();
    }
    private static String limit(String s,int length){
        if(s==null)return "";
        String clean=s.trim();
        return clean.length()>length?clean.substring(0,length):clean;
    }
}
