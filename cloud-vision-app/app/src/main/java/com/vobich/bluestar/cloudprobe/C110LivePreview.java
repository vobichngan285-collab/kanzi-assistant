package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
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
        /** 1=SurfaceCreated, 2=SurfaceChanged, 3=Destroyed, 4=already valid. */
        void surface(int event,int width,int height,int valid);
        /** 0=waiting for real Surface; 1=single play() issued after ready. */
        void playGate(int gate);
        void surfaceSnapshot(int valid,int playIssued);
    }
    private final VLCVideoLayout view;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LibVLC vlc;
    private MediaPlayer player;
    private Media retainedMedia; // OEM holds Media until next playback/release
    private SurfaceView observedSurface;
    private SurfaceHolder.Callback observedCallback;
    private boolean surfaceReady,delayExpired,playIssued,readySizeReported,waitingLogged;
    private int surfaceWidth,surfaceHeight;
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
            // VideoHelper inflates a single video SurfaceView when subtitles=false.
            // Observe that real inflated view, not VLCVideoLayout itself.
            observedSurface=findVideoSurface(view);
            if(observedSurface==null)
                throw new IllegalStateException("VLC video SurfaceView was not inflated");
            final SurfaceView video=observedSurface;
            observedCallback=new SurfaceHolder.Callback(){
                @Override public void surfaceCreated(SurfaceHolder holder){
                    onSurface(id,current,listener,1,video.getWidth(),video.getHeight());
                }
                @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){
                    onSurface(id,current,listener,2,w,h);
                }
                @Override public void surfaceDestroyed(SurfaceHolder holder){
                    onSurface(id,current,listener,3,0,0);
                }
            };
            video.getHolder().addCallback(observedCallback);
            // A surface may predate the callback registration.
            if(validSurface(video))
                onSurface(id,current,listener,4,video.getWidth(),video.getHeight());
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
                        listener.surfaceSnapshot(isSurfaceUsable()?1:0,playIssued?1:0);
                        listener.snapshot(phase,nativeEvents,lastNativeEvent,boundToCamera()?1:0);
                        listener.error(2301,1,3);break;
                    case MediaPlayer.Event.EndReached:
                        listener.surfaceSnapshot(isSurfaceUsable()?1:0,playIssued?1:0);
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
            // OEM delays setup by 300ms, but a delay alone does not prove
            // the Android Surface is created. The only play() call is gated.
            delayedPlay=()->{
                if(id!=generation||player!=current)return;
                delayExpired=true;
                startOnlyWhenSurfaceReady(id,current,listener);
            };
            main.postDelayed(delayedPlay,300L);
            // A local timer is not an RTSP/server error.
            watchdog=()->{
                if(id==generation&&player==current&&!hasVout){
                    reportNativeStatus(current,listener,2);
                    listener.surfaceSnapshot(isSurfaceUsable()?1:0,playIssued?1:0);
                    listener.snapshot(phase,nativeEvents,lastNativeEvent,boundToCamera()?1:0);
                    listener.error(2302,2,5);
                }
            };
            main.postDelayed(watchdog,20000L);
        }catch(RuntimeException|LinkageError failure){
            int stoppedAt=phase;
            listener.surfaceSnapshot(isSurfaceUsable()?1:0,playIssued?1:0);
            listener.snapshot(stoppedAt,nativeEvents,lastNativeEvent,boundToCamera()?1:0);
            stop();
            listener.error(2304,stoppedAt,6);
        }
    }

    private static SurfaceView findVideoSurface(View node){
        if(node instanceof SurfaceView)return (SurfaceView)node;
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){
                SurfaceView found=findVideoSurface(group.getChildAt(i));
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static boolean validSurface(SurfaceView target){
        if(target==null)return false;
        SurfaceHolder holder=target.getHolder();
        Surface surface=holder==null?null:holder.getSurface();
        return target.isAttachedToWindow() && surface!=null && surface.isValid();
    }
    private boolean isSurfaceUsable(){
        return surfaceReady && validSurface(observedSurface) &&
            surfaceWidth>0 && surfaceHeight>0;
    }

    private void onSurface(int id,MediaPlayer current,Listener listener,
                           int event,int width,int height){
        if(id!=generation||player!=current)return;
        if(event==3){
            surfaceReady=false;
            surfaceWidth=0;surfaceHeight=0;
            listener.surface(event,0,0,0);
            return;
        }
        if(width>0&&height>0){surfaceWidth=width;surfaceHeight=height;}
        surfaceReady=validSurface(observedSurface);
        // Record the first Created/Changed/previously-valid fact and the first
        // actual positive size. Avoid filling the 24-event privacy ledger.
        if(event==1 || (event==4 && surfaceReady))
            listener.surface(event,surfaceWidth,surfaceHeight,surfaceReady?1:0);
        if(surfaceReady&&surfaceWidth>0&&surfaceHeight>0&&!readySizeReported){
            readySizeReported=true;
            listener.surface(2,surfaceWidth,surfaceHeight,1);
        }
        startOnlyWhenSurfaceReady(id,current,listener);
    }

    private void startOnlyWhenSurfaceReady(int id,MediaPlayer current,Listener listener){
        if(id!=generation||player!=current||playIssued||!delayExpired)return;
        if(!isSurfaceUsable()){
            if(!waitingLogged){
                waitingLogged=true;
                listener.playGate(0);
            }
            return;
        }
        // One user gesture = one native play() call; SurfaceChanged callbacks
        // cannot repeatedly restart the player or send a second RTSP request.
        playIssued=true;
        listener.playGate(1);
        try{
            current.play();
            progress(listener,5);
            stateProbe=()->{
                if(id==generation&&player==current)
                    reportNativeStatus(current,listener,1);
            };
            main.postDelayed(stateProbe,1200L);
        }catch(RuntimeException|LinkageError startFailure){
            listener.surfaceSnapshot(isSurfaceUsable()?1:0,1);
            listener.snapshot(phase,nativeEvents,lastNativeEvent,boundToCamera()?1:0);
            listener.error(2304,phase,6);
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
        if(observedSurface!=null&&observedCallback!=null){
            try{observedSurface.getHolder().removeCallback(observedCallback);}
            catch(RuntimeException ignored){}
        }
        observedSurface=null;observedCallback=null;
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
        surfaceReady=false;delayExpired=false;playIssued=false;
        readySizeReported=false;waitingLogged=false;surfaceWidth=0;surfaceHeight=0;
    }
}
