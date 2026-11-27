package com.vobich.bluestar.openocrlab;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
public final class C110PhotoPolicy {
 private C110PhotoPolicy() {}
 private static final Pattern HREF=Pattern.compile("(?i)href\\s*=\\s*[\"']([^\"']+)[\"']");
 private static final Pattern FPATH=Pattern.compile("(?is)<FPATH>\\s*([^<]+)\\s*</FPATH>");
 private static final Pattern DCIM=Pattern.compile("(?i)/?DCIM/[A-Za-z0-9_%./()-]+\\.(?:jpg|jpeg)");
 public static String path(String input,String dir,String host,boolean folder) {
   if(input==null)return null;
   String v=input.trim().replace("&amp;","&");
   if(v.length()>512 || v.contains("..") || v.contains("\\") || v.contains("?") || v.contains("#"))return null;
   try {
     URI u=new URI(v);
     if(u.isAbsolute()){
       if(!"http".equalsIgnoreCase(u.getScheme())||!host.equalsIgnoreCase(u.getHost())||(u.getPort()!=-1&&u.getPort()!=80))return null;
       v=u.getRawPath();
     } else if(v.startsWith("//"))return null;
     else if(!v.startsWith("/"))v=dir+v;
     if(!v.startsWith("/"))v="/"+v;
     if(!v.toUpperCase(Locale.ROOT).startsWith("/DCIM/"))return null;
     return folder ? (v.endsWith("/")?v:null) : (v.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g$")?v:null);
   } catch(Exception e){return null;}
 }
 public static Set<String> jpegs(String text,String dir,String host) {
   LinkedHashSet<String> files=new LinkedHashSet<>();
   for(Pattern p:new Pattern[]{HREF,FPATH,DCIM}) {
     Matcher m=p.matcher(text);
     while(m.find()) {
       String val=p==DCIM?m.group():m.group(1);
       String f=path(val,dir,host,false);
       if(f!=null)files.add(f);
     }
   }
   return files;
 }
 public static Set<String> dirs(String text,String dir,String host) {
   LinkedHashSet<String> result=new LinkedHashSet<>();
   Matcher m=HREF.matcher(text);
   while(m.find()){
     String p=path(m.group(1),dir,host,true);
     if(p!=null&&!p.equals(dir))result.add(p);
   }
   return result;
 }
 /** Parse only a numeric protocol tag; never log FPATH or camera IDs. */
 public static Integer tagInt(String xml,String tag) {
   if(xml==null||tag==null||!tag.matches("[A-Za-z]{1,16}"))return null;
   Matcher m=Pattern.compile("(?is)<"+tag+">\\s*(-?\\d+)\\s*</"+tag+">").matcher(xml);
   if(!m.find())return null;
   try{return Integer.valueOf(m.group(1));}catch(NumberFormatException e){return null;}
 }
 /** Familiar Novatek codes from NT96660 reference manual; device firmware may differ. */
 public static String statusName(Integer value){
   if(value==null)return "未返回";
   switch(value){
    case 0:return "成功";
    case -13:return "执行失败";
    case -256:return "命令不存在";
    case -11:return "存储已满";
    case -12:return "目录已满";
    default:return "未映射";
   }
 }
 public static boolean jpegHeaderTail(byte[] start,int tail) {
   return start!=null && start.length>=3 && (start[0]&255)==255 && (start[1]&255)==216 && (start[2]&255)==255 && tail==0xffd9;
 }
}
