package com.vobich.bluestar.cloudprobe;

/** Main-thread one-slot handoff: voice blocks dispatch, never capture. */
final class C110DeferredPhoto<T> {
    private T latest;
    void offer(T photo){latest=photo;}
    T takeIfReady(boolean voiceBusy){
        if(voiceBusy)return null;
        T result=latest;latest=null;return result;
    }
    void clear(){latest=null;}
}
