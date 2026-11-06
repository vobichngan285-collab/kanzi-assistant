package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Privacy-first, bounded local timing ledger. No photos, text, transcript, keys or audio.
 * Stores the latest 50 user capture sessions, each with at most 24 numeric/status events,
 * and expires sessions older than 7 days. Writes only at user operations, never per frame.
 */
final class RuntimeDiagnostics {
    private static final String PREFS="kanzi_runtime_diagnostics";
    private static final String KEY="sessions_v1";
    private static final int MAX_SESSIONS=50;
    private static final int MAX_EVENTS=24;
    private static final long SEVEN_DAYS=7L*24L*60L*60L*1000L;
    private final SharedPreferences prefs;
    private JSONArray sessions;

    RuntimeDiagnostics(Context context){
        prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        try{
            String saved=prefs.getString(KEY,"[]");
            sessions=new JSONArray(saved==null?"[]":saved);
        }catch(Exception bad){sessions=new JSONArray();}
        prune();
    }

    synchronized void begin(String source){
        prune();
        JSONObject session=new JSONObject();
        try{
            session.put("at",System.currentTimeMillis());
            session.put("source",safe(source));
            session.put("events",new JSONArray());
            sessions.put(session);
            prune();
            persist();
        }catch(Exception ignored){}
    }

    /** All event names and outcomes are constrained identifiers, and values are numeric. */
    synchronized void event(String category,String stage,long value,String outcome){
        try{
            if(sessions.length()==0)begin("app");
            JSONObject session=sessions.optJSONObject(sessions.length()-1);
            if(session==null)return;
            JSONArray events=session.optJSONArray("events");
            if(events==null||events.length()>=MAX_EVENTS)return;
            JSONObject item=new JSONObject();
            item.put("at",System.currentTimeMillis());
            item.put("kind",safe(category));
            item.put("stage",safe(stage));
            item.put("value",Math.max(-1,Math.min(10000000,value)));
            item.put("result",safe(outcome));
            events.put(item);
            persist();
        }catch(Exception ignored){}
    }

    synchronized String summary(){
        prune();
        StringBuilder b=new StringBuilder(4096);
        b.append("蓝星向导 运行诊断（本机匿名记录）\n")
         .append("最近50次操作或7天，先到先删；每次最多24条事件\n")
         .append("只含时间、阶段、数值和结果；不含照片、文字、音频或密钥\n")
         .append("TTS onStart/onAudioAvailable并不表示扬声器已出声\n\n");
        SimpleDateFormat sdf=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA);
        for(int i=sessions.length()-1;i>=0;i--){
            JSONObject session=sessions.optJSONObject(i);
            if(session==null)continue;
            b.append("【").append(sdf.format(new Date(session.optLong("at",0))))
             .append("  ").append(safe(session.optString("source"))).append("】\n");
            JSONArray ev=session.optJSONArray("events");
            if(ev!=null)for(int j=0;j<ev.length();j++){
                JSONObject item=ev.optJSONObject(j);
                if(item==null)continue;
                b.append("  ").append(safe(item.optString("kind"))).append(".")
                 .append(safe(item.optString("stage"))).append(" = ")
                 .append(item.optLong("value",-1)).append(" [")
                 .append(safe(item.optString("result"))).append("]\n");
            }
            if(b.length()>52000){b.append("（导出内容已截断）\n");break;}
        }
        if(sessions.length()==0)b.append("暂无运行记录。\n");
        return b.toString();
    }

    synchronized void clear(){
        sessions=new JSONArray();
        persist();
    }

    private void prune(){
        JSONArray retained=new JSONArray();
        long earliest=System.currentTimeMillis()-SEVEN_DAYS;
        if(sessions==null)sessions=new JSONArray();
        int start=Math.max(0,sessions.length()-MAX_SESSIONS);
        for(int i=start;i<sessions.length();i++){
            JSONObject s=sessions.optJSONObject(i);
            if(s!=null&&s.optLong("at",0)>=earliest)retained.put(s);
        }
        sessions=retained;
    }
    private static String safe(String raw){
        if(raw==null||!raw.matches("[a-zA-Z0-9_]{1,32}"))return "unknown";
        return raw;
    }
    private void persist(){prefs.edit().putString(KEY,sessions.toString()).apply();}
}
