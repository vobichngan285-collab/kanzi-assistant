package com.vobich.bluestar.c110photosize;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only DCIM path parsing reused from the already tested C110 photo flow. */
final class PhotoSizePolicy {
    private static final Pattern HREF=Pattern.compile("(?i)href\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern FPATH=Pattern.compile("(?is)<FPATH>\\s*([^<]+)\\s*</FPATH>");
    private static final Pattern DCIM=Pattern.compile("(?i)/?DCIM/[A-Za-z0-9_%./()-]+\\.(?:jpg|jpeg)");
    static String path(String raw,String directory,boolean folder){
        if(raw==null)return null;
        String item=raw.trim().replace("&amp;","&");
        if(item.length()>512||item.contains("..")||item.contains("\\")||
           item.contains("?")||item.contains("#"))return null;
        try{
            URI uri=new URI(item);
            if(uri.isAbsolute()){
                if(!"http".equalsIgnoreCase(uri.getScheme())||
                   !"192.168.1.254".equals(uri.getHost())||
                   (uri.getPort()!=-1&&uri.getPort()!=80))return null;
                item=uri.getRawPath();
            }else if(item.startsWith("//"))return null;
            else if(!item.startsWith("/"))item=directory+item;
            if(!item.startsWith("/"))item="/"+item;
            if(!item.toUpperCase(Locale.ROOT).startsWith("/DCIM/"))return null;
            return folder?(item.endsWith("/")?item:null):
                (item.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g$")?item:null);
        }catch(Exception failure){return null;}
    }
    static Set<String> jpegPaths(String content,String dir){
        LinkedHashSet<String> list=new LinkedHashSet<>();
        for(Pattern p:new Pattern[]{HREF,FPATH,DCIM}){
            Matcher m=p.matcher(content);
            while(m.find()){
                String val=p==DCIM?m.group():m.group(1);
                String cleaned=path(val,dir,false);
                if(cleaned!=null)list.add(cleaned);
            }
        }
        return list;
    }
    static Set<String> directoryPaths(String content,String dir){
        LinkedHashSet<String> dirs=new LinkedHashSet<>();
        Matcher m=HREF.matcher(content);
        while(m.find()){
            String cleaned=path(m.group(1),dir,true);
            if(cleaned!=null&&!cleaned.equals(dir))dirs.add(cleaned);
        }
        return dirs;
    }
    static Integer integerTag(String xml,String tag){
        if(xml==null||!tag.matches("[A-Za-z]{1,16}"))return null;
        Matcher m=Pattern.compile("(?is)<"+tag+">\\s*(-?\\d+)\\s*</"+tag+">").matcher(xml);
        if(!m.find())return null;
        try{return Integer.parseInt(m.group(1));}
        catch(NumberFormatException ignored){return null;}
    }
    static boolean validJpeg(byte[] data){
        return data!=null&&data.length>=256&&(data[0]&255)==255&&
            (data[1]&255)==216&&(data[2]&255)==255&&
            (data[data.length-2]&255)==255&&(data[data.length-1]&255)==217;
    }
    private PhotoSizePolicy(){}
}
