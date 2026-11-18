package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;

/**
 * C110 isolated OEM LibVLC comparison.
 *
 * SJCAM Zone VlcLyCameraPlayer$1.run() uses MediaPlayer.attachViews with
 * VLCVideoLayout and a 300ms cache plus H.264 decoder options. We follow
 * that initialization and the original known RTSP URL without copying OEM
 * libraries or trying guessed camera endpoints.
 *
 * This diagnostic never saves video, credentials, SDP, or raw native logs.
 * LibVLC native sockets temporarily use the selected camera Wi-Fi routing;
 * the previous process network is restored on stop, pause, or photo capture.
 */
final class C110LivePreview {
    static final String RTSP_URL="rtsp://192.168.1.254:554/xxx.mp4";
    interface Listener {
        void videoOutput(int count,long elapsedMs);
        void dimensions(int width,int height);
        void error(int code,int reason,int stage);
        void state(int state);
        void routing(int status);
        void milestone(int phase);
        void snapshot(int phase,int eventCount,int lastEvent,int routeBound);
        /** getPlayerState() and isPlaying() sampled from native LibVLC. */
        void nativeStatus(int when,int state,int playing);
    }
    private final VLCVideoLayout view;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LibVLC vlc;
    private MediaPlayer player;
    private Media retainedMedia; // OEM holds Media until next playback/release
    private ConnectivityManager connectivity;
    private Network priorBinding,cameraBinding;
    private boolean changedBinding,hasVout;
    private int generation,phase,nativeEvents,lastNativeEvent,lastReportedState;
    private Runnable watchdog,delayedPlay,stateProbe;

    C110LivePreview(Context context){
        view=new VLCVideoLayout(context);
        view.setKeepScreenOn(true);
    }
    VLCVideoLayout view(){return view;}

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
            progress(listener,1);

            // Original Zone VlcLyCameraPlayer defaults to 300ms cache.
            // Keep transport negotiation inside VideoLAN; do not force RTP/TCP.
            ArrayList<String> args=new ArrayList<>();
            args.add("--network-caching=300");
            args.add("--live-caching=300");
            args.add("--no-audio");
            vlc=new LibVLC(context.getApplicationContext(),args);
            progress(listener,2);
            final MediaPlayer current=new MediaPlayer(vlc);
            player=current;
            // Crucial OEM difference from v0.1.8: the original uses VLCVideoLayout
            // and the MediaPlayer video helper, not a manually attached SurfaceView.
            current.attachViews(view,null,false,false);
            progress(listener,3);
            current.setEventListener(event->main.post(()->{
                if(id!=generation||player!=current)return;
                nativeEvents++;
                lastNativeEvent=event.type;
                switch(event.type){
                    case MediaPlayer.Event.Opening:
                        publishState(listener,1);break;
                    case MediaPlayer.Event.Buffering:
                        publishState(listener,2);break;
                    case MediaPlayer.Event.Playing:
                        publishState(listener,3);break;
                    case MediaPlayer.Event.Vout:
                        int count=event.getVoutCount();
                        if(count>0&&!hasVout){
                            hasVout=true;
                            listener.videoOutput(count,SystemClock.elapsedRealtime()-began);
                        }
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        listener.snapshot(phase,nativeEvents,lastNativeEvent,boundToCamera()?1:0);
                        listener.error(2301,1,3);break;
                    case MediaPlayer.Event.EndReached:
                        listener.snapshot(phase,nativeEvents,lastNativeEvent,boundToCamera()?1:0);
                        listener.error(2303,3,4);break;
                    default:break;
                }
            }));
            Media media=new Media(vlc,Uri.parse(RTSP_URL));
            try{
                // Options verified directly in Zone 6.7.3.16 classes5.dex.
                media.addOption(":no-audio");
                media.addOption(":avcodec-skiploopfilter=4");
                media.addOption(":avcodec-skip-frame=0");
                media.addOption(":avcodec-skip-idct=0");
                media.addOption(":no-stats");
                media.addOption(":sout-mux-caching=300");
                media.addOption(":live-caching=300");
                media.addOption(":file-caching=300");
                media.addOption(":network-caching=300");
                media.addOption(":codec=mediacodec,iomx,all");
                current.setMedia(media);
                // OEM VlcLyCameraPlayer stores Media in a field; do not
                // release the Java owner immediately after setMedia().
                retainedMedia=media;
            }catch(RuntimeException|LinkageError failure){
                media.release();
                throw failure;
            }
            progress(listener,4);
            current.setVolume(0);
            // OEM playByUrl() posts its player initialization runnable
            // after 300ms. Previous lab called play() immediately.
            delayedPlay=()->{
                if(id!=generation||player!=current)return;
                try{
                    current.play();
                    progress(listener,5);
                    stateProbe=()->{
                        if(id==generation&&player==current)
                            reportNativeStatus(current,listener,1);
                    };
                    main.postDelayed(stateProbe,1200L);
                }catch(RuntimeException|LinkageError startFailure){
                    listener.snapshot(phase,nativeEvents,lastNativeEvent,
                        boundToCamera()?1:0);
                    listener.error(2304,phase,6);
                }
            };
            main.postDelayed(delayedPlay,300L);
            // A local timer is not an RTSP/server error.
            watchdog=()->{
                if(id==generation&&player==current&&!hasVout){
                    reportNativeStatus(current,listener,2);
                    listener.snapshot(phase,nativeEvents,lastNativeEvent,boundToCamera()?1:0);
                    listener.error(2302,2,5);
                }
            };
            main.postDelayed(watchdog,20000L);
        }catch(RuntimeException|LinkageError failure){
            int stoppedAt=phase;
            listener.snapshot(stoppedAt,nativeEvents,lastNativeEvent,boundToCamera()?1:0);
            stop();
            listener.error(2304,stoppedAt,6);
        }
    }

    private void progress(Listener listener,int step){
        phase=step;
        listener.milestone(step);
    }
    private void publishState(Listener listener,int state){
        if(lastReportedState!=state){
            lastReportedState=state;
            listener.state(state);
        }
    }
    private void reportNativeStatus(MediaPlayer current,Listener listener,int sample){
        int state=-4096,playing=-4096;
        try{state=current.getPlayerState();}catch(RuntimeException|LinkageError unavailable){}
        try{playing=current.isPlaying()?1:0;}catch(RuntimeException|LinkageError unavailable){}
        listener.nativeStatus(sample,state,playing);
    }
    private boolean boundToCamera(){
        try{
            return connectivity!=null&&cameraBinding!=null&&
                cameraBinding.equals(connectivity.getBoundNetworkForProcess());
        }catch(Exception ignored){return false;}
    }
    void stop(){
        generation++;
        if(watchdog!=null){main.removeCallbacks(watchdog);watchdog=null;}
        if(delayedPlay!=null){main.removeCallbacks(delayedPlay);delayedPlay=null;}
        if(stateProbe!=null){main.removeCallbacks(stateProbe);stateProbe=null;}
        MediaPlayer old=player;player=null;
        if(old!=null){
            try{old.setEventListener(null);}catch(Exception ignored){}
            try{old.stop();}catch(Exception ignored){}
            try{old.detachViews();}catch(Exception ignored){}
            try{old.release();}catch(Exception ignored){}
        }
        Media oldMedia=retainedMedia;retainedMedia=null;
        if(oldMedia!=null)try{oldMedia.release();}catch(Exception ignored){}
        LibVLC oldVlc=vlc;vlc=null;
        if(oldVlc!=null)try{oldVlc.release();}catch(Exception ignored){}
        if(changedBinding&&connectivity!=null&&cameraBinding!=null){
            try{
                if(cameraBinding.equals(connectivity.getBoundNetworkForProcess()))
                    connectivity.bindProcessToNetwork(priorBinding);
            }catch(Exception ignored){}
        }
        changedBinding=false;priorBinding=null;cameraBinding=null;connectivity=null;
        hasVout=false;phase=0;nativeEvents=0;lastNativeEvent=0;lastReportedState=0;
    }
}
