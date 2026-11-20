package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User-owned OpenAI-compatible cloud configurations, independently selected for
 * OCR, vision and voice ASR. No API key is ever stored in metadata or exported.
 * Unselected and fresh-install roles use the EXACT previous Bailian integration.
 */
final class CloudProviderStore {
    static final String OCR=CloudProviderPolicy.OCR, VISION=CloudProviderPolicy.VISION;
    static final String ASR=CloudProviderPolicy.ASR;
    static final String CHAT=CloudProviderPolicy.CHAT;
    static final String ASR_QWEN=CloudProviderPolicy.ASR_QWEN;
    static final String ASR_MULTIPART=CloudProviderPolicy.ASR_MULTIPART;
    static final String ASR_CHAT_AUDIO=CloudProviderPolicy.ASR_CHAT_AUDIO;
    static final String BAILIAN_URL=
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String PREF="kanzi_settings";
    private static final String LIST="cloud_custom_profiles_v1";
    private static final String SELECT="cloud_custom_selected_v1_";
    private static final int MAX_PROFILES=20;
    private CloudProviderStore(){}

    static final class Profile {
        final String id,name,url,model,protocol;
        final boolean stream;
        Profile(String id,String name,String url,String model,String protocol,boolean stream){
            this.id=id==null?"":id;this.name=name;this.url=url;
            this.model=model;this.protocol=protocol;this.stream=stream;
        }
    }
    static final class Config {
        final String name,url,model,key,protocol;
        final boolean custom,stream;
        Config(String name,String url,String model,String key,
               String protocol,boolean custom,boolean stream){
            this.name=name;this.url=url;this.model=model;this.key=key;
            this.protocol=protocol;this.custom=custom;this.stream=stream;
        }
    }
    private static SharedPreferences pref(Context ctx){
        return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static List<Profile> list(Context ctx){
        ArrayList<Profile> items=new ArrayList<>();
        String json=pref(ctx).getString(LIST,"[]");
        try{
            JSONArray saved=new JSONArray(json);
            for(int i=0;i<saved.length()&&i<MAX_PROFILES;i++){
                JSONObject p=saved.optJSONObject(i);
                if(p==null)continue;
                String id=p.optString("id","");
                String protocol=p.optString("protocol","");
                if(!id.matches("[a-zA-Z0-9_-]{1,64}")||
                   !(CHAT.equals(protocol)||ASR_QWEN.equals(protocol)||
                     ASR_MULTIPART.equals(protocol)||ASR_CHAT_AUDIO.equals(protocol)))
                    continue;
                items.add(new Profile(id,p.optString("name",""),
                    p.optString("url",""),p.optString("model",""),
                    protocol,p.optBoolean("stream",false)));
            }
        }catch(Exception ignored){
            // Do not turn a corrupted selected profile into silent paid fallback.
            // resolve() reports the missing selected profile instead.
        }
        return items;
    }
    static Profile find(Context ctx,String id){
        for(Profile p:list(ctx))if(p.id.equals(id))return p;
        return null;
    }
    static String selectedId(Context ctx,String role){
        if(!CloudProviderPolicy.validRole(role))throw new IllegalArgumentException("无效服务类别");
        return pref(ctx).getString(SELECT+role,"");
    }
    static String activeName(Context ctx,String role){
        String id=selectedId(ctx,role);
        if(id==null||id.isEmpty())return "原有百炼（默认）";
        Profile p=find(ctx,id);
        return p==null?"配置已丢失，请重新选择":p.name;
    }
    static Config resolve(Context ctx,String role,String oldModel)throws Exception{
        return resolveFor(ctx,role,selectedId(ctx,role),oldModel);
    }
    static Config resolveFor(Context ctx,String role,String profileId,String oldModel)throws Exception{
        if(!CloudProviderPolicy.validRole(role))throw new IllegalArgumentException("无效云端类别");
        if(profileId==null||profileId.isEmpty()){
            String key=CloudKeyStore.load(ctx);
            if(key==null||key.isEmpty())
                throw new IllegalArgumentException("请先在云端 AI 中配置原有百炼 API Key");
            String model=role.equals(ASR)?"qwen3-asr-flash":oldModel;
            return new Config("原有百炼",BAILIAN_URL,model,key,
                role.equals(ASR)?ASR_QWEN:CHAT,false,false);
        }
        Profile chosen=find(ctx,profileId);
        if(chosen==null)throw new IllegalArgumentException("当前自定义服务商配置不存在，请重新选择");
        if(!CloudProviderPolicy.accepts(role,chosen.protocol))
            throw new IllegalArgumentException("所选协议不支持当前任务类型");
        String key=CloudKeyStore.loadNamed(ctx,chosen.id);
        if(key.isEmpty())throw new IllegalArgumentException("此服务商尚未保存 API Key");
        return new Config(chosen.name,CloudProviderPolicy.validatedUrl(chosen.url),
            CloudProviderPolicy.cleanModel(chosen.model),key,
            chosen.protocol,true,chosen.stream);
    }
    /** A permission or key check only, without issuing any paid cloud request. */
    static boolean hasKey(Context ctx,String role)throws Exception {
        try{return !resolve(ctx,role,
            role.equals(OCR)?"qwen-vl-ocr":"qwen3.8-flash").key.isEmpty();}
        catch(IllegalArgumentException ex){return false;}
    }
    static void select(Context ctx,String role,String id){
        if(!CloudProviderPolicy.validRole(role))throw new IllegalArgumentException("无效任务类型");
        if(id==null)id="";
        if(!id.isEmpty()){
            Profile p=find(ctx,id);
            if(p==null||!CloudProviderPolicy.accepts(role,p.protocol))
                throw new IllegalArgumentException("该配置不支持这个任务");
        }
        pref(ctx).edit().putString(SELECT+role,id).apply();
    }
    static Profile save(Context ctx,Profile raw,String newKey)throws Exception{
        String protocol=raw.protocol;
        if(!(CHAT.equals(protocol)||ASR_QWEN.equals(protocol)||
            ASR_MULTIPART.equals(protocol)||ASR_CHAT_AUDIO.equals(protocol)))
            throw new IllegalArgumentException("不支持的兼容协议");
        String name=CloudProviderPolicy.cleanName(raw.name);
        String url=CloudProviderPolicy.validatedUrl(raw.url);
        String model=CloudProviderPolicy.cleanModel(raw.model);
        ArrayList<Profile> all=new ArrayList<>(list(ctx));
        boolean fresh=raw.id==null||raw.id.isEmpty();
        String id=fresh?UUID.randomUUID().toString():raw.id;
        int where=-1;
        for(int i=0;i<all.size();i++)if(all.get(i).id.equals(id)){where=i;break;}
        if(!fresh&&where<0)throw new IllegalArgumentException("编辑的配置已不存在");
        if(fresh&&all.size()>=MAX_PROFILES)throw new IllegalArgumentException("最多保存20套配置");
        for(String role:new String[]{OCR,VISION,ASR}){
            if(id.equals(selectedId(ctx,role))&&!CloudProviderPolicy.accepts(role,protocol))
                throw new IllegalArgumentException("此配置正在被"+role+"使用，不能修改为不兼容协议");
        }
        String key=newKey==null?"":newKey.trim();
        if(fresh&&key.isEmpty())throw new IllegalArgumentException("请填写此配置的 API Key");
        if(!key.isEmpty())CloudKeyStore.saveNamed(ctx,id,key);
        Profile p=new Profile(id,name,url,model,protocol,raw.stream&&CHAT.equals(protocol));
        if(where<0)all.add(p);else all.set(where,p);
        JSONArray saved=new JSONArray();
        for(Profile item:all){
            saved.put(new JSONObject()
                .put("id",item.id).put("name",item.name).put("url",item.url)
                .put("model",item.model).put("protocol",item.protocol)
                .put("stream",item.stream));
        }
        pref(ctx).edit().putString(LIST,saved.toString()).apply();
        return p;
    }
    static void delete(Context ctx,String id){
        if(id==null||id.isEmpty())return;
        ArrayList<Profile> all=new ArrayList<>(list(ctx));
        JSONArray saved=new JSONArray();
        for(Profile p:all)if(!p.id.equals(id)){
            JSONObject o=new JSONObject();
            try{o.put("id",p.id).put("name",p.name).put("url",p.url)
                .put("model",p.model).put("protocol",p.protocol).put("stream",p.stream);}
            catch(Exception ignored){}
            saved.put(o);
        }
        SharedPreferences.Editor changes=pref(ctx).edit().putString(LIST,saved.toString());
        for(String role:new String[]{OCR,VISION,ASR})
            if(id.equals(selectedId(ctx,role)))changes.putString(SELECT+role,"");
        changes.apply();
        CloudKeyStore.clearNamed(ctx,id);
    }
}
