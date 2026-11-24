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
        // An explicitly named percent field is unambiguous even without '%'.
        String explicit=tag(xml,"BatteryPercent");
        if(explicit==null)explicit=tag(xml,"BatteryPercentage");
        if(explicit!=null){
            if(!explicit.matches("[0-9]{1,3}%?"))return unknown();
            int pct=Integer.parseInt(explicit.replace("%",""));
            return pct<=100?new Reading("电量 "+pct+"%",pct+"%",true):unknown();
        }
        String raw=tag(xml,"Value");
        if(raw==null||!raw.matches("[0-9]{1,3}%?"))return unknown();
        boolean percent=raw.endsWith("%");
        int n=Integer.parseInt(percent?raw.substring(0,raw.length()-1):raw);
        // Real percentages require an explicit unit or named percent field.
        if(percent)return n<=100?new Reading("电量 "+n+"%",raw,true):unknown();
        // Published SJ5000X reference mapping, not a measured C110 percentage.
        // Always mark these coarse estimates as approximate; never convert 4/5.
        switch(n){
            case 0:return new Reading("电量约100%",raw,true);
            case 1:return new Reading("电量约75%",raw,true);
            case 2:return new Reading("电量约50%",raw,true);
            case 3:return new Reading("电量约25%",raw,true);
            case 4:return new Reading("电量低",raw,true);
            case 5:return new Reading("充电中",raw,true);
            default:return new Reading("读数 "+n+"（待确认）",raw,false);
        }
    }
    private static String tag(String xml,String name){
        Matcher m=Pattern.compile("<"+name+">\\s*([^<>]{1,32}?)\\s*</"+name+">").matcher(xml);
        if(!m.find())return null;
        String result=m.group(1).trim();
        return m.find()?null:result;
    }
}
