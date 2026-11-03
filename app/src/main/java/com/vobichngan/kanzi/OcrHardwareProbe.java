package com.vobichngan.kanzi;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;

/** Lightweight one-shot memory/thermal read for explicit X200 Pro diagnostics.
 * Thermal status is an Android qualitative signal, NOT measured °C.
 */
public final class OcrHardwareProbe {
    private OcrHardwareProbe() {}

    public static final class Reading {
        public final long availRamMb;
        public final long totalRamMb;
        public final long javaHeapMb;
        public final long nativeHeapMb;
        public final int thermalStatus;
        public final boolean memoryPressure;
        public final String device;
        Reading(long avail, long total, long javaHeap, long nativeHeap, int thermal,
                boolean low, String dev) {
            availRamMb=avail; totalRamMb=total; javaHeapMb=javaHeap;
            nativeHeapMb=nativeHeap; thermalStatus=thermal; memoryPressure=low;
            device=dev;
        }
        public String describe() {
            return device + " | Android热状态=" +
                (thermalStatus<0 ? "不可用" : thermalStatus+"(级别,不是℃)") +
                " | OS剩余RAM=" + availRamMb+"MB/" +totalRamMb+"MB" +
                " | JavaHeap="+javaHeapMb+"MB | NativeHeap="+nativeHeapMb+"MB" +
                (memoryPressure?" | 系统内存压力警告":"");
        }
    }

    public static Reading capture(Context context) {
        long avail=-1,total=-1;
        boolean low=false;
        ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am!=null) {
            ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
            try {
                am.getMemoryInfo(mem);
                avail=mem.availMem/(1024*1024);
                total=mem.totalMem/(1024*1024);
                low=mem.lowMemory;
            } catch (RuntimeException ignored) {}
        }
        int thermal=-1;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) {
            PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if (pm!=null) {
                try {thermal=pm.getCurrentThermalStatus();}
                catch (RuntimeException ignored){}
            }
        }
        Runtime runtime=Runtime.getRuntime();
        long heap=(runtime.totalMemory()-runtime.freeMemory())/(1024*1024);
        long nativeMb=Debug.getNativeHeapAllocatedSize()/(1024*1024);
        return new Reading(avail,total,heap,nativeMb,thermal,low,
            Build.MANUFACTURER+" "+Build.MODEL);
    }

    /** Strict downgrade of extra REC on severe thermal or OS low-memory flags.
     * CPU thread selection is a separate controlled A/B setting.
     */
    public static int safeShadowBudget(OcrPerformanceProfile profile, Reading snapshot) {
        if (profile==null || snapshot==null)return 0;
        if(snapshot.memoryPressure || snapshot.thermalStatus>=PowerManager.THERMAL_STATUS_SEVERE)
            return 0;
        if(snapshot.thermalStatus>=PowerManager.THERMAL_STATUS_MODERATE)
            return Math.min(2,profile.maxShadowRescuesPerDiagnostic);
        return profile.maxShadowRescuesPerDiagnostic;
    }
}
