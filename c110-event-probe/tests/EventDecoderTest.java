package com.vobich.bluestar.c110eventprobe;
import java.util.ArrayList;
import java.util.List;
public final class EventDecoderTest{
    public static void main(String[] args){
        List<String> out=new ArrayList<>();
        EventDecoder d=new EventDecoder(out::add);
        for(String s:new String[]{"<?xml version=\"1.0\"?><Fun","ction><Cmd>3020</Cmd><Sta","tus>3</Status></Function><Function><Cmd>3020</Cmd><Status>4</Status></Function>","<Function><Cmd>2020</Cmd><Status>0</Status></Function>"})d.feed(s.toCharArray(),s.length());
        if(out.size()!=3||!out.get(0).contains("Status=3")||!out.get(1).contains("Status=4")||!out.get(2).contains("Cmd=2020"))throw new AssertionError(out);
        String s="<Function><Cmd>3020</Cmd>";
        d.feed(s.toCharArray(),s.length());d.finish();
        if(out.size()!=4)throw new AssertionError(out);
        System.out.println("PASS fragmented, coalesced, noncapture and incomplete frames");
    }
}
