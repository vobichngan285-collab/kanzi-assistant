package com.vobich.bluestar.c110eventprobe;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded streaming decoder. No XML entity processing and no outbound protocol. */
final class EventDecoder {
    static final int MAX_FRAME=16384;
    private static final String START="<Function>", END="</Function>";
    private final StringBuilder buf=new StringBuilder();
    private final Consumer<String> report;
    EventDecoder(Consumer<String> report){this.report=report;}
    void feed(char[] data,int n){
        if(n<0||n>data.length)throw new IllegalArgumentException();
        buf.append(data,0,n);
        while(true){
            int first=buf.indexOf(START);
            if(first<0){
                if(buf.length()>MAX_FRAME) {report.accept("未识别帧，已丢弃");buf.delete(0,buf.length()-START.length());}
                return;
            }
            if(first>0)buf.delete(0,first);
            int end=buf.indexOf(END,START.length());
            int next=buf.indexOf(START,START.length());
            if(next>=0&&(end<0||next<end)){report.accept("不完整帧");buf.delete(0,next);continue;}
            if(end<0){
                if(buf.length()>MAX_FRAME){report.accept("超长帧，已丢弃");buf.setLength(0);}
                return;
            }
            end+=END.length();
            if(end<=MAX_FRAME)parse(buf.substring(0,end));
            else report.accept("超长帧，已丢弃");
            buf.delete(0,end);
        }
    }
    private static String value(String s,String tag){
        Matcher m=Pattern.compile("<"+tag+">\\s*(-?\\d{1,10})\\s*</"+tag+">").matcher(s);
        return m.find()?m.group(1):"?";
    }
    private void parse(String s){
        String cmd=value(s,"Cmd"), status=value(s,"Status"), cmd2=value(s,"Cmd2"), v=value(s,"Value");
        String label="";
        if("3020".equals(cmd)){
            if("3".equals(status))label=" 拍照开始候选";
            else if("4".equals(status))label=" 拍照结束候选";
        }
        report.accept("Cmd="+cmd+" Status="+status+" Cmd2="+cmd2+" Value="+v+label);
    }
    void finish(){if(buf.toString().contains(START))report.accept("连接结束时有未完整帧");}
}
