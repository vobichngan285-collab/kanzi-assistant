package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;

/** Single target state shared by Home, Preview and C110, without any camera
 * ownership, background capture or extra upload. Uses the existing preference
 * key and therefore preserves tasks previously saved in MainActivity. */
final class FindingTargetStore {
    private static final String PREFS="kanzi_settings";
    private static final String KEY="continuous_find_target";
    private FindingTargetStore(){}

    static String current(Context context){
        String value=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(KEY,"");
        return value==null?"":value.trim();
    }
    static void set(Context context,String target){
        String normalized=target==null?"":target.trim();
        if(normalized.length()>28)
            throw new IllegalArgumentException("寻找目标不能超过28字");
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString(KEY,normalized).apply();
    }
    /** An older photo may not clear a newer user command. */
    static synchronized boolean clearIfCurrent(Context context,String expected){
        if(expected==null||expected.isEmpty()||!expected.equals(current(context)))
            return false;
        set(context,"");
        return true;
    }
}
