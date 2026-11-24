package com.vobich.bluestar.cloudprobe;

import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded streaming decoder. No XML entity processing and no outbound protocol. */
final class C110EventDecoder {
    static final int MAX_FRAME=16384;
    private static final String START="<Function>", END="</Function>";
    private final StringBuilder buf=new StringBuilder();
    private final BiConsumer<Integer,Integer> report;
    C110EventDecoder(BiConsumer<Integer,Integer> report){this.report=report;}
    void feed(char[] data,int n){
        if(n<0||n>data.length)throw new IllegalArgumentException();
        buf.append(data,0,n);
        while(true){
            int first=buf.indexOf(START);
            if(first<0){
                if(buf.length()>MAX_FRAME) {report.accept(-1,-1);buf.delete(0,buf.length()-START.length());}
                return;
            }
            if(first>0)buf.delete(0,first);
            int end=buf.indexOf(END,START.length());
            int next=buf.indexOf(START,START.length());
            if(next>=0&&(end<0||next<end)){report.accept(-1,-1);buf.delete(0,next);continue;}
            if(end<0){
                if(buf.length()>MAX_FRAME){report.accept(-1,-1);buf.setLength(0);}
                return;
            }
            end+=END.length();
            if(end<=MAX_FRAME)parse(buf.substring(0,end));
            else report.accept(-1,-1);
            buf.delete(0,end);
        }
    }
    private static String value(String s,String tag){
        Matcher m=Pattern.compile("<"+tag+">\\s*(-?\\d{1,10})\\s*</"+tag+">").matcher(s);
        return m.find()?m.group(1):"?";
    }
    private void parse(String s){
        try{report.accept(Integer.parseInt(value(s,"Cmd")),Integer.parseInt(value(s,"Status")));}
        catch(NumberFormatException ignored){report.accept(-1,-1);}
    }
    void finish(){if(buf.toString().contains(START))report.accept(-1,-1);}
}
