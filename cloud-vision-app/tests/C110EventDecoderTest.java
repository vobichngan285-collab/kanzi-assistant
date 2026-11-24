package com.vobich.bluestar.cloudprobe;
import java.util.*;
public final class C110EventDecoderTest {
    public static void main(String[] args){
        List<String> out=new ArrayList<>();
        C110EventDecoder d=new C110EventDecoder((c,s)->out.add(c+":"+s));
        for(String s:new String[]{"<?xml version=\"1.0\"?><Fun","ction><Cmd>3020</Cmd><Sta","tus>19</Status></Function><Function><Cmd>3020</Cmd><Status>20</Status></Function>","<Function><Cmd>2020</Cmd><Status>3</Status></Function>"})d.feed(s.toCharArray(),s.length());
        if(!out.equals(Arrays.asList("3020:19","3020:20","2020:3")))throw new AssertionError(out);
        String incomplete="<Function><Cmd>3020</Cmd>";
        d.feed(incomplete.toCharArray(),incomplete.length());d.finish();
        if(!out.get(3).equals("-1:-1"))throw new AssertionError(out);
        System.out.println("PASS fragmented and coalesced notification frames");
    }
}
