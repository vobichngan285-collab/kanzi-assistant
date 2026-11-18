package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;

/**
 * Proven 2026-11-19 OEM C110 video-only player integrated without changing
 * its start, VideoHelper / VLCVideoLayout or native RTSP negotiation sequence.
 * The only addition is a one-shot PixelCopy request against its displayed
 * SurfaceView. PixelCopy never triggers shutter, media recording or RTSP restart.
 */
final class C110LivePreview {
    static final String VIDEO_URL="rtsp://192.168.1.254:554/xxx.mp4";
    interface Listener {
        void routeBound(int status);
        void state(int state);
        void videoOutput(int count,long elapsedMs);
        void error(int code);
    }
    interface FrameListener {
        /** Android PixelCopy.SUCCESS=0; negative values are local preconditions. */
        void captured(int status,Bitmap bitmap);
    }
    private final VLCVideoLayout view;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ConnectivityManager cm;
    private Network previousNetwork,cameraNetwork;
    private boolean changedBinding;
    private LibVLC vlc;
    private MediaPlayer player;
    private Media media;
    private Runnable delayedPlay;
    private int generation;
    private boolean hasVout;

    C110LivePreview(Context context){
        view=new VLCVideoLayout(context);
        view.setKeepScreenOn(true);
        // The successful standalone OEM mini's view was visible since creation.
        // Do not set GONE/INVISIBLE or add a custom Surface readiness gate.
    }
    VLCVideoLayout view(){return view;}
    boolean hasVideoOutput(){return player!=null&&hasVout;}

    void start(Context context,Network wifi,Listener listener){
        stop();
        if(wifi==null)throw new IllegalArgumentException("Missing C110 Wi-Fi");
        final int id=generation;
        final long startedAt=SystemClock.elapsedRealtime();
        try{
            cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(cm==null)throw new IllegalStateException("No connectivity service");
            previousNetwork=cm.getBoundNetworkForProcess();
            cameraNetwork=wifi;
            if(!wifi.equals(previousNetwork)){
                if(!cm.bindProcessToNetwork(wifi))
                    throw new IllegalStateException("Native VLC Wi-Fi routing failed");
                changedBinding=true;
            }
            listener.routeBound(1);
            // Exactly the parameters used in the first real-device success.
            ArrayList<String> options=new ArrayList<>();
            options.add("--network-caching=300");
            options.add("--live-caching=300");
            options.add("--no-audio");
            vlc=new LibVLC(context.getApplicationContext(),options);
            final MediaPlayer current=new MediaPlayer(vlc);
            player=current;
            current.attachViews(view,null,false,false);
            current.setEventListener(event->main.post(()->{
                if(id!=generation||player!=current)return;
                switch(event.type){
                    case MediaPlayer.Event.Opening:
                        listener.state(1);break;
                    case MediaPlayer.Event.Playing:
                        listener.state(3);break;
                    case MediaPlayer.Event.Vout:
                        if(event.getVoutCount()>0&&!hasVout){
                            hasVout=true;
                            listener.videoOutput(event.getVoutCount(),
                                SystemClock.elapsedRealtime()-startedAt);
                        }
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        listener.error(1);break;
                    case MediaPlayer.Event.EndReached:
                        listener.error(2);break;
                    default:break;
                }
            }));
            media=new Media(vlc,Uri.parse(VIDEO_URL));
            media.addOption(":no-audio");
            media.addOption(":network-caching=300");
            media.addOption(":live-caching=300");
            media.addOption(":file-caching=300");
            media.addOption(":sout-mux-caching=300");
            media.addOption(":codec=mediacodec,iomx,all");
            current.setMedia(media);
            current.setVolume(0);
            // The successful mini delegates Surface scheduling to VideoHelper
            // and calls play once after OEM 300ms. Never gate it ourselves.
            delayedPlay=()->{
                if(id!=generation||player!=current)return;
                current.play();
            };
            main.postDelayed(delayedPlay,300);
        }catch(RuntimeException|LinkageError ex){
            stop();
            throw ex;
        }
    }

    /**
     * Copy the actual composited video output Surface (NOT the Android screen,
     * phone-camera texture, or a stale cached image) into a new in-RAM Bitmap.
     * Caller immediately recycles it after producing JPEG for cloud OCR.
     */
    void copyVisibleFrame(FrameListener result){
        if(player==null||!hasVout){result.captured(-1,null);return;}
        SurfaceView video=findSurface(view);
        if(video==null||!video.isAttachedToWindow()){
            result.captured(-2,null);return;
        }
        Surface surface=video.getHolder().getSurface();
        if(surface==null||!surface.isValid()||video.getWidth()<2||video.getHeight()<2){
            result.captured(-3,null);return;
        }
        final int w=video.getWidth(),h=video.getHeight();
        // No upscaling or arbitrary frame reuse. Guard memory for large displays.
        if((long)w*h>9_000_000L){result.captured(-4,null);return;}
        Bitmap bitmap=null;
        try{
            bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            final Bitmap copy=bitmap;
            PixelCopy.request(video,copy,status->{
                if(status!=PixelCopy.SUCCESS){
                    copy.recycle();
                    result.captured(status,null);
                }else result.captured(status,copy);
            },main);
        }catch(RuntimeException failure){
            if(bitmap!=null)bitmap.recycle();
            result.captured(-5,null);
        }
    }

    private static SurfaceView findSurface(View node){
        if(node instanceof SurfaceView)return (SurfaceView)node;
        if(node instanceof ViewGroup){
            ViewGroup parent=(ViewGroup)node;
            for(int i=0;i<parent.getChildCount();i++){
                SurfaceView found=findSurface(parent.getChildAt(i));
                if(found!=null)return found;
            }
        }
        return null;
    }

    void stop(){
        generation++;
        if(delayedPlay!=null){main.removeCallbacks(delayedPlay);delayedPlay=null;}
        MediaPlayer old=player;player=null;
        if(old!=null){
            try{old.setEventListener(null);}catch(RuntimeException ignored){}
            try{old.stop();}catch(RuntimeException ignored){}
            try{old.detachViews();}catch(RuntimeException ignored){}
            try{old.release();}catch(RuntimeException ignored){}
        }
        Media owned=media;media=null;
        if(owned!=null)try{owned.release();}catch(RuntimeException ignored){}
        LibVLC lib=vlc;vlc=null;
        if(lib!=null)try{lib.release();}catch(RuntimeException ignored){}
        if(changedBinding&&cm!=null&&cameraNetwork!=null){
            try{
                if(cameraNetwork.equals(cm.getBoundNetworkForProcess()))
                    cm.bindProcessToNetwork(previousNetwork);
            }catch(RuntimeException ignored){}
        }
        changedBinding=false;cm=null;previousNetwork=null;cameraNetwork=null;
        hasVout=false;
    }
}
