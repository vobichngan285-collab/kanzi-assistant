package com.vobich.bluestar.cloudprobe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only Novatek battery response; the C110 firmware mapping requires device validation. */
final class C110BatteryPolicy {
    private C110BatteryPolicy(){}
    static final class Reading {
        final String label,raw;
        final boolean available;
        Reading(String label,String raw,boolean available){
            this.label=label;this.raw=raw;this.available=available;
        }
    }
    static Reading unknown(){return new Reading("电量未知","",false);}
    static Reading parse(String xml){
        if(xml==null||xml.length()>8192||!xml.contains("<Function>")||
                !xml.contains("</Function>"))return unknown();
        if(!"3019".equals(tag(xml,"Cmd"))||!"0".equals(tag(xml,"Status")))return unknown();
        String raw=tag(xml,"Value");
        if(raw==null||!raw.matches("[0-9]{1,3}%?"))return unknown();
        boolean percent=raw.endsWith("%");
        int n=Integer.parseInt(percent?raw.substring(0,raw.length()-1):raw);
        // Do not guess a percentage from an unqualified integer or enum.
        if(percent)return n<=100?new Reading("电量 "+n+"%",raw,true):unknown();
        String label;
        switch(n){
            case 0:label="高";break;
            case 1:label="中";break;
            case 2:label="低";break;
            case 3:label="极低";break;
            case 4:label="耗尽";break;
            case 5:label="充电中";break;
            default:return new Reading("电量读数 "+n+"（待确认）",raw,false);
        }
        return new Reading("电量 "+label+"（参考）",raw,true);
    }
    private static String tag(String xml,String name){
        Matcher m=Pattern.compile("<"+name+">\\s*([^<>]{1,32}?)\\s*</"+name+">").matcher(xml);
        if(!m.find())return null;
        String result=m.group(1).trim();
        return m.find()?null:result;
    }
}
