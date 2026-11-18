package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;
import java.util.ArrayList;

/**
 * C110 native LibVLC trial, following original SJCAM Zone's playback engine.
 * Never logs video/SDP/credentials, records media or submits camera commands.
 * Native VLC sockets use a scoped camera Wi-Fi process binding, restored at stop.
 */
final class C110LivePreview {
    static final String RTSP_URL="rtsp://192.168.1.254:554/xxx.mp4";
    interface Listener {
        void videoOutput(int count,long elapsedMs); // Vout is NOT a proven rendered frame
        void dimensions(int width,int height);
        void error(int code,int reason,int stage);
        void state(int state);
        void routing(int status);
    }
    private final FrameLayout view;
    private final SurfaceView surface;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LibVLC vlc;
    private MediaPlayer player;
    private ConnectivityManager connectivity;
    private Network priorBinding,cameraBinding;
    private boolean changedBinding,hasVout;
    private int generation;
    private Runnable watchdog;

    C110LivePreview(Context context){
        view=new FrameLayout(context);
        view.setKeepScreenOn(true);
        surface=new SurfaceView(context);
        surface.setKeepScreenOn(true);
        view.addView(surface,new FrameLayout.LayoutParams(-1,-1));
    }
    FrameLayout view(){return view;}

    void start(Context context,Network cameraNetwork,Listener listener){
        stop();
        if(cameraNetwork==null)throw new IllegalArgumentException("Missing C110 Wi-Fi Network");
        final int id=generation;
        final long began=SystemClock.elapsedRealtime();
        try{
            connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(connectivity==null)throw new IllegalStateException("No connectivity service");
            priorBinding=connectivity.getBoundNetworkForProcess();
            cameraBinding=cameraNetwork;
            if(!cameraNetwork.equals(priorBinding)){
                if(!connectivity.bindProcessToNetwork(cameraNetwork))
                    throw new IllegalStateException("Native VLC Wi-Fi bind failed");
                changedBinding=true;
            }
            listener.routing(1);
            // Original Zone uses LibVLC network/live caching. Do not force RTP/TCP.
            ArrayList<String> opts=new ArrayList<>();
            opts.add("--network-caching=400");
            opts.add("--live-caching=300");
            opts.add("--no-audio");
            vlc=new LibVLC(context.getApplicationContext(),opts);
            final MediaPlayer current=new MediaPlayer(vlc);
            player=current;
            final IVLCVout vout=current.getVLCVout();
            vout.setVideoView(surface);
            vout.attachViews((output,w,h,vw,vh,sarNum,sarDen)->main.post(()->{
                if(id==generation&&player==current&&w>0&&h>0)
                    listener.dimensions(w,h);
            }));
            current.setEventListener(event->main.post(()->{
                if(id!=generation||player!=current)return;
                switch(event.type){
                    case MediaPlayer.Event.Opening:
                        listener.state(1);break;
                    case MediaPlayer.Event.Buffering:
                        listener.state(2);break;
                    case MediaPlayer.Event.Playing:
                        listener.state(3);break;
                    case MediaPlayer.Event.Vout:
                        int count=event.getVoutCount();
                        if(count>0&&!hasVout){
                            hasVout=true;
                            listener.videoOutput(count,SystemClock.elapsedRealtime()-began);
                        }
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        listener.error(2301,1,3);break;
                    case MediaPlayer.Event.EndReached:
                        listener.error(2303,3,4);break;
                    default:break;
                }
            }));
            Media media=new Media(vlc,Uri.parse(RTSP_URL));
            try{
                media.addOption(":network-caching=400");
                media.addOption(":live-caching=300");
                media.addOption(":codec=mediacodec,iomx,all");
                media.addOption(":no-audio");
                current.setMedia(media);
            }finally{media.release();}
            current.setVolume(0);
            current.play();
            // A bounded trial: Playing/Vout do not independently prove real pixels.
            watchdog=()->{
                if(id==generation&&player==current&&!hasVout)
                    listener.error(2302,2,5);
            };
            main.postDelayed(watchdog,12000L);
        }catch(RuntimeException|LinkageError failure){
            stop();
            throw failure;
        }
    }

    void stop(){
        generation++;
        if(watchdog!=null){main.removeCallbacks(watchdog);watchdog=null;}
        MediaPlayer old=player;player=null;
        if(old!=null){
            try{old.setEventListener(null);}catch(Exception ignored){}
            try{old.stop();}catch(Exception ignored){}
            try{old.getVLCVout().detachViews();}catch(Exception ignored){}
            try{old.release();}catch(Exception ignored){}
        }
        LibVLC oldVlc=vlc;vlc=null;
        if(oldVlc!=null)try{oldVlc.release();}catch(Exception ignored){}
        if(changedBinding&&connectivity!=null&&cameraBinding!=null){
            try{
                if(cameraBinding.equals(connectivity.getBoundNetworkForProcess()))
                    connectivity.bindProcessToNetwork(priorBinding);
            }catch(Exception ignored){}
        }
        changedBinding=false;priorBinding=null;cameraBinding=null;connectivity=null;
        hasVout=false;
    }
}
