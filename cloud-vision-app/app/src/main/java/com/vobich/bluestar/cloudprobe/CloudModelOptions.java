package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;

/** Local, per-model controls. Nothing here stores photos, API keys or output. */
final class CloudModelOptions {
    private static final String PREF="cloud_model_options_v1";
    private CloudModelOptions(){}

    static final class Settings {
        final boolean thinking;
        final String ocrPrompt;
        Settings(boolean thinking,String ocrPrompt){
            this.thinking=thinking;
            this.ocrPrompt=ocrPrompt==null?"":ocrPrompt;
        }
    }
    private static String key(String role,String profileId,String model){
        // Editing the model ID yields a NEW independent set of options.
        return profileId==null||profileId.isEmpty()?
            "builtin|"+role+"|"+model:"custom|"+profileId+"|"+model;
    }
    static Settings get(Context ctx,String role,String profileId,String model){
        String prefix=key(role,profileId,model);
        SharedPreferences p=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        return new Settings(p.getBoolean(prefix+".think",false),
            p.getString(prefix+".ocr",""));
    }
    static void saveThinking(Context ctx,String role,String profileId,
                             String model,boolean thinking){
        String k=key(role,profileId,model);
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .edit().putBoolean(k+".think",thinking).apply();
    }
    static void saveOcrPrompt(Context ctx,String role,String profileId,
                              String model,String prompt){
        if(!CloudProviderStore.OCR.equals(role))
            throw new IllegalArgumentException("只允许文字识别任务使用专用提示词");
        String value=prompt==null?"":prompt.trim();
        if(value.length()>2000)throw new IllegalArgumentException("提示词最多2000字");
        String k=key(role,profileId,model);
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .edit().putString(k+".ocr",value).apply();
    }
    static void clearProfile(Context ctx,String profileId){
        if(profileId==null||profileId.isEmpty())return;
        SharedPreferences p=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        SharedPreferences.Editor edit=p.edit();
        String prefix="custom|"+profileId+"|";
        for(String key:p.getAll().keySet())if(key.startsWith(prefix))edit.remove(key);
        edit.apply();
    }
}
