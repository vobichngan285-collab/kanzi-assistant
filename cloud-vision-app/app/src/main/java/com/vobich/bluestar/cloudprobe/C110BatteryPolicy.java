package com.vobich.bluestar.cloudprobe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SJCAM X LY battery enum from original APK; no estimated percentages. */
final class C110BatteryPolicy {
    private C110BatteryPolicy(){}
    static final class Reading {
        final String label,raw;
        final boolean available,charging;
        final int bars;
        Reading(String label,String raw,boolean available,int bars,boolean charging){
            this.label=label;this.raw=raw;this.available=available;
            this.bars=bars;this.charging=charging;
        }
    }
    static Reading unknown(){return new Reading("电量未知","",false,-1,false);}
    // SJCAM X LY camera: 3019 Value 0=FULL,1=THREE_BARS,2=TWO_BARS,
    // 3=ONE_BAR,4=LOW,5=CHARGING. No percent inference.
    static Reading parse(String xml){
        if(xml==null||xml.length()>8192||!xml.contains("<Function>")||
                !xml.contains("</Function>"))return unknown();
        if(!"3019".equals(tag(xml,"Cmd"))||!"0".equals(tag(xml,"Status")))return unknown();
        String raw=tag(xml,"Value");
        if(raw==null)return unknown();
        switch(raw){
            case "0":return new Reading("电量四格",raw,true,4,false);
            case "1":return new Reading("电量三格",raw,true,3,false);
            case "2":return new Reading("电量两格",raw,true,2,false);
            case "3":return new Reading("电量一格",raw,true,1,false);
            case "4":return new Reading("电量低",raw,true,0,false);
            case "5":return new Reading("充电中",raw,true,-1,true);
            default:return unknown();
        }
    }
    private static String tag(String xml,String name){
        Matcher m=Pattern.compile("<"+name+">\\s*([^<>]{1,32}?)\\s*</"+name+">").matcher(xml);
        if(!m.find())return null;
        String result=m.group(1).trim();
        return m.find()?null:result;
    }
}
