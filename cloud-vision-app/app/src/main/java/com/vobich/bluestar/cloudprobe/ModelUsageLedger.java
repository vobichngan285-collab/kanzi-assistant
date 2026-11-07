package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local-only nonblocking response usage ledger. One queued write per completed
 * request, never per frame/chunk. No photo, speech, prompt, response text or key.
 * Token totals use ONLY provider usage; -1 means unknown, never estimated as 0.
 */
final class ModelUsageLedger {
    static final class Line {
        final String model;
        final long input, output;
        final int calls, reported, unknown, failed;
        Line(String model,long input,long output,int calls,int reported,int unknown,int failed) {
            this.model=model; this.input=input; this.output=output;
            this.calls=calls; this.reported=reported; this.unknown=unknown; this.failed=failed;
        }
    }
    static final class Snapshot {
        final String day, lastRequested, lastReported, lastOutcome;
        final List<Line> lines;
        final long input, output;
        final int calls, unknown, failed;
        Snapshot(String day,String lastRequested,String lastReported,String lastOutcome,
                 List<Line> lines,long input,long output,int calls,int unknown,int failed) {
            this.day=day;this.lastRequested=lastRequested;this.lastReported=lastReported;
            this.lastOutcome=lastOutcome;this.lines=lines;
            this.input=input;this.output=output;this.calls=calls;
            this.unknown=unknown;this.failed=failed;
        }
    }
    private static final class Mutable {
        String day,model;
        long input,output;
        int calls,reported,unknown,failed;
        JSONObject json() throws Exception {
            return new JSONObject().put("day",day).put("model",model).put("input",input)
                .put("output",output).put("calls",calls).put("reported",reported)
                .put("unknown",unknown).put("failed",failed);
        }
    }
    private static final String PREFS="bluestar_model_usage_v1";
    private static final String DATA="daily";
    private static final String[] SHOW = {
        VisionModelCatalog.FLASH_38, VisionModelCatalog.PLUS,
        VisionModelCatalog.MAX_38, "qwen-vl-ocr", "qwen3-asr-flash"
    };
    private final SharedPreferences prefs;
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,Mutable> rows=new LinkedHashMap<>();
    private String lastRequested="",lastReported="",lastOutcome="";

    ModelUsageLedger(Context app) {
        prefs=app.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        try{
            lastRequested=safe(prefs.getString("last_requested",""));
            lastReported=safe(prefs.getString("last_reported",""));
            lastOutcome=safe(prefs.getString("last_outcome",""));
            JSONArray list=new JSONArray(prefs.getString(DATA,"[]"));
            for(int i=0;i<Math.min(1000,list.length());i++){
                JSONObject o=list.optJSONObject(i);
                if(o==null)continue;
                String day=o.optString("day","");
                String model=safe(o.optString("model",""));
                if(!validDay(day)||model.isEmpty())continue;
                Mutable m=new Mutable();
                m.day=day;m.model=model;
                m.input=Math.max(0,o.optLong("input",0));
                m.output=Math.max(0,o.optLong("output",0));
                m.calls=Math.max(0,o.optInt("calls",0));
                m.reported=Math.max(0,o.optInt("reported",0));
                m.unknown=Math.max(0,o.optInt("unknown",0));
                m.failed=Math.max(0,o.optInt("failed",0));
                rows.put(day+"|"+model,m);
            }
        }catch(Exception ignored){}
        trim();
    }

    /** Called from any thread; returns immediately without disk IO or JSON parsing. */
    void recordAsync(String requested,String returned,int input,int output,boolean success) {
        final String sent=safe(requested);
        final String actual=safe(returned);
        if(sent.isEmpty())return;
        // A separate single thread owns all mutations, aggregation and disk writes.
        try{disk.execute(()->{
            synchronized(this){
                String day=todayKey(),key=day+"|"+sent;
                Mutable m=rows.get(key);
                if(m==null){m=new Mutable();m.day=day;m.model=sent;rows.put(key,m);}
                m.calls++;
                if(!success)m.failed++;
                if(success&&input>=0&&output>=0){
                    m.reported++;m.input+=input;m.output+=output;
                }else m.unknown++;
                lastRequested=sent;
                lastReported=actual;
                lastOutcome=success?"success":"failed";
                trim();
                persist();
            }
        });
        }catch(java.util.concurrent.RejectedExecutionException ignored){
            // A late callback after Activity shutdown must not crash the app.
        }
    }
    synchronized Snapshot today(){
        final String day=todayKey();
        List<Line> lines=new ArrayList<>();
        long in=0,out=0;int calls=0,unknown=0,failed=0;
        for(String model:SHOW){
            Mutable m=rows.get(day+"|"+model);
            if(m==null)m=new Mutable();
            lines.add(new Line(model,m.input,m.output,m.calls,m.reported,m.unknown,m.failed));
            // ASR is time-based and has no trustworthy token count in current code.
            if(!"qwen3-asr-flash".equals(model)){
                in+=m.input;out+=m.output;calls+=m.calls;
                unknown+=m.unknown;failed+=m.failed;
            }
        }
        return new Snapshot(day,lastRequested,lastReported,lastOutcome,
            lines,in,out,calls,unknown,failed);
    }
    void clearAsync(){
        disk.execute(()->{
            synchronized(this){
                rows.clear();lastRequested="";lastReported="";lastOutcome="";
                persist();
            }
        });
    }
    void shutdown(){disk.shutdown();}

    static String todayKey(){
        return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new java.util.Date());
    }
    private static String safe(String s){
        if(s==null||!s.matches("[a-zA-Z0-9._-]{1,50}"))return "";
        return s;
    }
    private static boolean validDay(String s){
        return s!=null&&s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    }
    private void trim(){
        Calendar c=Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH,-29);
        String earliest=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime());
        rows.entrySet().removeIf(e->e.getValue().day.compareTo(earliest)<0);
    }
    private void persist(){
        try{
            JSONArray arr=new JSONArray();
            for(Mutable m:rows.values())arr.put(m.json());
            // On dedicated worker; no recognition/TTS thread ever waits for storage.
            prefs.edit().putString(DATA,arr.toString())
                .putString("last_requested",lastRequested)
                .putString("last_reported",lastReported)
                .putString("last_outcome",lastOutcome).commit();
        }catch(Exception ignored){}
    }
}
