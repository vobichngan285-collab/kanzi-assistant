package com.vobich.bluestar.cloudprobe;

import android.net.Network;

/** Process-memory only: latest actual submitted photo shared by phone, USB, and C110.
 * Does not save photos, prompts, or transcripts on disk. */
final class RecentPhotoSession {
    static final class Snapshot {
        final byte[] jpeg;
        final Network cloudNetwork;
        final String task,observed;
        Snapshot(byte[] data,Network network,String t,String o){
            jpeg=data;cloudNetwork=network;task=t;observed=o;
        }
    }
    private static byte[] photo;
    private static Network network;
    private static String task="",observed="";
    private RecentPhotoSession(){}
    static synchronized void captured(byte[] jpeg,Network cloudNetwork,String taskName){
        if(jpeg==null||jpeg.length<256)return;
        photo=jpeg;
        network=cloudNetwork;
        task=taskName==null?"":taskName;
        observed="";
    }
    /** Do not let an older background response overwrite the newest shot. */
    static synchronized void analyzed(byte[] jpeg,String text){
        if(photo==jpeg)observed=text==null?"":text;
    }
    static synchronized Snapshot latest(){
        return photo==null?null:new Snapshot(photo,network,task,observed);
    }
}
