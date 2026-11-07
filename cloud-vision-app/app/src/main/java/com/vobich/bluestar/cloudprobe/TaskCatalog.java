package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stable system routes and user-owned instructions, persisted independently. */
final class TaskCatalog {
    static final String OCR = "ocr";
    static final String VISION = "vision";
    private static final String PREFS = "cloud_vision_tasks";
    private static final String CUSTOM = "custom_tasks";
    private static final String SELECTED = "selected_task_id";
    private static final String ORDER = "task_grid_order_v1";
    private static final String HIDDEN = "task_grid_hidden_presets_v1";
    private static final String OVERRIDE_PREFIX = "override_";

    static final class Task {
        final String id, name, instruction, engine, icon;
        final boolean custom;
        Task(String id, String name, String instruction, String engine, boolean custom, String icon) {
            this.id=id;this.name=name;this.instruction=instruction;
            this.engine=engine;this.custom=custom;this.icon=icon;
        }
        @Override public String toString(){return name;}
    }

    static Task[] defaults(){
        return new Task[]{
            new Task("ocr","朗读文字","识别清晰文字，按正确顺序原样朗读，不解释、不分类",OCR,false,"▤"),
            new Task("menu","餐厅菜单","识别菜名、价格、菜品类别，找出明确标注的招牌菜和推荐菜",VISION,false,"♨"),
            new Task("hospital","医院科室","识别科室名称、楼层、诊室编号、方向指示",VISION,false,"✚"),
            new Task("shop","店铺招牌","重点识别店名、经营项目、招牌文字和明确显示的特色",VISION,false,"▣"),
            new Task("product","商品信息","识别商品名称、品牌、规格、价格、生产日期等可见内容",VISION,false,"⬡")
        };
    }

    static List<Task> all(Context c){
        SharedPreferences p=prefs(c);
        List<Task> out=new ArrayList<>();
        for(Task d:defaults()){
            String saved=p.getString(OVERRIDE_PREFIX+d.id,"");
            out.add(new Task(d.id,d.name,saved==null||saved.isEmpty()?d.instruction:saved,
                d.engine,false,d.icon));
        }
        try{
            JSONArray arr=new JSONArray(p.getString(CUSTOM,"[]"));
            for(int i=0;i<Math.min(arr.length(),80);i++){
                JSONObject o=arr.optJSONObject(i);if(o==null)continue;
                String id=o.optString("id","");
                String name=o.optString("name","");
                String instruction=o.optString("instruction","");
                if(id.startsWith("user-")&&!name.isEmpty()&&!instruction.isEmpty())
                    out.add(new Task(id,name,instruction,VISION,true,
                        cleanIcon(o.optString("icon","✦"))));
            }
        }catch(JSONException ignored){}
        return out;
    }

    /** System presets are hidden, not deleted; settings can restore them. */
    private static Set<String> hiddenPresetIds(Context c){
        return new HashSet<>(prefs(c).getStringSet(HIDDEN,Collections.emptySet()));
    }
    static boolean isPresetHidden(Context c,String id){
        return !defaultInstruction(id).isEmpty()&&hiddenPresetIds(c).contains(id);
    }
    static void restoreHiddenPreset(Context c,String id){
        if(defaultInstruction(id).isEmpty())return;
        Set<String> hidden=hiddenPresetIds(c);
        if(hidden.remove(id))prefs(c).edit().putStringSet(HIDDEN,hidden).apply();
    }
    private static String firstVisibleTaskId(Context c){
        for(Task t:all(c))
            if(!t.custom&&OCR.equals(t.id)&&!isPresetHidden(c,t.id))return t.id;
        for(Task t:all(c))
            if(t.custom||!isPresetHidden(c,t.id))return t.id;
        return null;
    }
    static boolean hidePreset(Context c,String id){
        if(defaultInstruction(id).isEmpty())return false;
        Set<String> hidden=hiddenPresetIds(c);
        if(hidden.contains(id))return true;
        int visible=0;
        for(Task t:all(c))if(t.custom||!hidden.contains(t.id))visible++;
        if(visible<=1)return false;
        String previous=selectedId(c);
        hidden.add(id);
        prefs(c).edit().putStringSet(HIDDEN,hidden).apply();
        if(id.equals(previous)){
            String next=firstVisibleTaskId(c);
            if(next!=null)select(c,next);
        }
        return true;
    }

    /** First launch: five stock tasks + creation tile + user-created tasks. */
    static List<String> displayOrder(Context c,List<Task> tasks){
        List<String> available=new ArrayList<>();
        boolean creationAdded=false;
        for(Task t:tasks){
            if(!t.custom&&isPresetHidden(c,t.id))continue;
            if(t.custom&&!creationAdded){
                available.add(TaskOrder.CREATE);
                creationAdded=true;
            }
            available.add(t.id);
        }
        if(!creationAdded)available.add(TaskOrder.CREATE);
        List<String> saved=new ArrayList<>();
        try{
            JSONArray arr=new JSONArray(prefs(c).getString(ORDER,"[]"));
            for(int i=0;i<Math.min(arr.length(),200);i++){
                String id=arr.optString(i,"");
                if(!id.isEmpty())saved.add(id);
            }
        }catch(JSONException ignored){}
        return TaskOrder.reconcile(saved,available);
    }

    /** Persist only stable IDs; never rewrite task names, prompts or selected task. */
    static void saveDisplayOrder(Context c,List<String> preferred,List<Task> tasks){
        List<String> available=new ArrayList<>();
        for(Task t:tasks)if(t.custom||!isPresetHidden(c,t.id))available.add(t.id);
        available.add(TaskOrder.CREATE);
        List<String> canonical=TaskOrder.reconcile(preferred,available);
        JSONArray arr=new JSONArray();
        for(String id:canonical)arr.put(id);
        prefs(c).edit().putString(ORDER,arr.toString()).apply();
    }

    static String selectedId(Context c){
        String id=prefs(c).getString(SELECTED,"ocr");
        // Migrate old experimental selections, never restore removed tasks.
        if("mall".equals(id))return "shop";
        if("scene".equals(id))id=OCR;
        if(id==null||isPresetHidden(c,id)){
            String next=firstVisibleTaskId(c);
            return next==null?OCR:next;
        }
        return id;
    }
    static void select(Context c,String id){prefs(c).edit().putString(SELECTED,id).apply();}

    static String defaultInstruction(String id){
        for(Task t:defaults())if(t.id.equals(id))return t.instruction;
        return "";
    }
    static void updatePreset(Context c,String id,String instruction){
        if(defaultInstruction(id).isEmpty())throw new IllegalArgumentException("不是预设任务");
        String s=instruction==null?"":instruction.trim();
        if(s.isEmpty()||s.length()>1500)
            throw new IllegalArgumentException("提示词须为1–1500字");
        prefs(c).edit().putString(OVERRIDE_PREFIX+id,s).apply();
    }
    static void restorePreset(Context c,String id){
        if(defaultInstruction(id).isEmpty())return;
        prefs(c).edit().remove(OVERRIDE_PREFIX+id).apply();
    }

    static Task upsert(Context c,String id,String name,String instruction){
        return upsert(c,id,name,instruction,"✦");
    }

    static Task upsert(Context c,String existingId,String name,String instruction,String icon){
        String cleanName=name==null?"":name.trim();
        String cleanInstruction=instruction==null?"":instruction.trim();
        if(cleanName.isEmpty()||cleanName.length()>32||
           cleanInstruction.isEmpty()||cleanInstruction.length()>1500)
            throw new IllegalArgumentException("名称须为1–32字；提示词须为1–1500字");
        String id=existingId==null||existingId.isEmpty()?
            "user-"+UUID.randomUUID():existingId;
        if(!id.startsWith("user-"))throw new IllegalArgumentException("不能修改系统任务标识");
        String symbol=cleanIcon(icon);
        JSONArray next=new JSONArray();
        for(Task t:all(c)){
            if(!t.custom||t.id.equals(id))continue;
            next.put(customJson(t));
        }
        Task newTask=new Task(id,cleanName,cleanInstruction,VISION,true,symbol);
        next.put(customJson(newTask));
        prefs(c).edit().putString(CUSTOM,next.toString()).apply();
        return newTask;
    }

    static void remove(Context c,String id){
        if(id==null||!id.startsWith("user-"))return;
        boolean wasSelected=id.equals(selectedId(c));
        JSONArray next=new JSONArray();
        for(Task t:all(c)){
            if(t.custom&&!t.id.equals(id))next.put(customJson(t));
        }
        prefs(c).edit().putString(CUSTOM,next.toString()).apply();
        if(wasSelected){
            String fallback=firstVisibleTaskId(c);
            if(fallback==null){
                restoreHiddenPreset(c,OCR);
                fallback=OCR;
            }
            select(c,fallback);
        }
    }

    private static JSONObject customJson(Task t){
        JSONObject item=new JSONObject();
        try{
            item.put("id",t.id);item.put("name",t.name);
            item.put("instruction",t.instruction);item.put("icon",t.icon);
        }catch(JSONException ignored){}
        return item;
    }
    private static String cleanIcon(String icon){
        if(icon==null||icon.trim().isEmpty())return "✦";
        String trimmed=icon.trim();
        // A fixed user-facing icon palette; prevents oversized arbitrary glyphs.
        String[] allowed={"✦","▤","♨","✚","▣","⬡","☷","♫","◉","★","☕","⚑"};
        for(String s:allowed)if(s.equals(trimmed))return s;
        return "✦";
    }
    static String[] iconPalette(){
        return new String[]{"✦","▤","♨","✚","▣","⬡","☷","♫","◉","★","☕","⚑"};
    }

    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
}
