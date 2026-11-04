package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Isolated single-column reading policy; NEVER infers or completes characters. */
public final class SingleVerticalFlow {
    private SingleVerticalFlow(){}

    /** Returns null if it cannot prove an upright one-column flow. */
    public static String tryArrange(List<ReadingOrder.Box> boxes) {
        if(boxes==null||boxes.isEmpty())return null;
        if(boxes.size()==1) {
            ReadingOrder.Box b=boxes.get(0);
            if(b==null||b.text==null||b.text.isBlank())return null;
            float h=b.bottom()-b.top(),w=b.right()-b.left();
            int n=b.text.codePointCount(0,b.text.length());
            return h>=110f&&h>=w*2.5f&&n>=2&&b.confidence>=.65f
                ? b.text : null;
        }
        if(boxes.size()<3||boxes.size()>18)return null;
        List<ReadingOrder.Box> sorted=new ArrayList<>(boxes);
        float sumX=0,avgHeight=0,avgWidth=0;
        for(ReadingOrder.Box b:sorted){
            if(b==null||b.text==null||b.text.codePointCount(0,b.text.length())!=1||
               b.confidence<.60f)return null;
            int ch=b.text.codePointAt(0);
            if(Character.UnicodeScript.of(ch)!=Character.UnicodeScript.HAN)return null;
            sumX+=b.centerX();
            avgHeight+=b.bottom()-b.top();
            avgWidth+=b.right()-b.left();
        }
        float center=sumX/sorted.size(),glyphHeight=avgHeight/sorted.size();
        float glyphWidth=avgWidth/sorted.size();
        sorted.sort(Comparator.comparingDouble(ReadingOrder.Box::centerY));
        for(int i=0;i<sorted.size();i++){
            ReadingOrder.Box b=sorted.get(i);
            if(Math.abs(b.centerX()-center)>Math.max(12f,glyphWidth*.42f))return null;
            if(i>0){
                float distance=b.centerY()-sorted.get(i-1).centerY();
                if(distance<glyphHeight*.72f||distance>glyphHeight*2.55f)
                    return null;
            }
        }
        StringBuilder full=new StringBuilder();
        for(ReadingOrder.Box b:sorted)full.append(b.text);
        return full.toString();
    }
}
