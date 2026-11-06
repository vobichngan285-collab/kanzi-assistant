package com.vobich.bluestar.cloudprobe;

import java.util.function.Consumer;

/** Streaming speech clauses without dropping or duplicating model text. */
final class SpeechChunker {
    private final StringBuilder pending=new StringBuilder();
    void append(String delta,Consumer<String> consumer){
        if(delta==null||delta.isEmpty())return;
        pending.append(delta);
        String chunk;
        while((chunk=take(false))!=null)consumer.accept(chunk);
    }
    void finish(Consumer<String> consumer){
        String chunk;
        while((chunk=take(true))!=null)consumer.accept(chunk);
    }
    private String take(boolean finishing){
        if(pending.length()==0)return null;
        int split=-1;
        for(int i=0;i<pending.length();i++){
            char c=pending.charAt(i);
            boolean sentence=c=='。'||c=='！'||c=='？'||c=='!'||c=='?'||c=='；'||c==';'||c=='\n';
            boolean clause=c=='，'||c==',';
            if((sentence&&i>=5)||(clause&&i>=14)){split=i+1;break;}
        }
        if(split<0){
            if(!finishing)return null;
            split=pending.length();
        }
        String text=pending.substring(0,split);
        pending.delete(0,split);
        text=text.replace("**","").replace("##","").replace("•","").trim();
        if(text.startsWith("- "))text=text.substring(2).trim();
        if(text.isEmpty())return take(finishing);
        return text;
    }
}
