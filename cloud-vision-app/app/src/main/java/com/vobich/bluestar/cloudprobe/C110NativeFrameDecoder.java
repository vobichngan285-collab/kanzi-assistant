package com.vobich.bluestar.cloudprobe;
import android.graphics.Bitmap;
/** Owns one independent LibVLC memory-output player; no Surface in B mode. */
final class C110NativeFrameDecoder implements AutoCloseable {
    private long handle;
    static { System.loadLibrary("c110rawframe"); }
    private static native long open();
    private static native int[] capture(long handle);
    private static native void dispose(long handle);
    C110NativeFrameDecoder(){
        handle=open();
        if(handle==0)throw new IllegalStateException("原生内存输出启动失败");
    }
    static final class Shot {
        final Bitmap bitmap;
        final int frameNumber;
        Shot(Bitmap bitmap,int frameNumber){this.bitmap=bitmap;this.frameNumber=frameNumber;}
    }
    synchronized Shot take(){
        if(handle==0)return null;
        int[] data=capture(handle);
        if(data==null||data.length<7)return null;
        int w=data[0],h=data[1];
        if(w<2||h<2||(long)w*h>9_000_000L||(long)w*h+3!=data.length)return null;
        return new Shot(Bitmap.createBitmap(data,3,w,w,h,Bitmap.Config.ARGB_8888),data[2]);
    }
    @Override public synchronized void close(){
        long old=handle;handle=0;
        if(old!=0)dispose(old);
    }
}
