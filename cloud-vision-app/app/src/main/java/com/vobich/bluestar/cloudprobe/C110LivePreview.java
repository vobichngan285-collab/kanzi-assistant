package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.net.Network;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

/** Direct C110 original RTSP video, NOT phone screen capture or the OEM player. */
final class C110LivePreview {
    // Exact URL string recovered from the LY camera branch of SJCAM Zone DEX.
    static final String RTSP_URL="rtsp://192.168.1.254:554/xxx.mp4";
    interface Listener {
        void firstFrame(long elapsedMs);
        void dimensions(int width,int height);
        void error(int code,int reason);
        void state(int state);
    }
    private final PlayerView view;
    private ExoPlayer player;
    C110LivePreview(Context context){
        view=new PlayerView(context);
        view.setUseController(false);
        view.setKeepScreenOn(true);
    }
    PlayerView view(){return view;}
    void start(Context context,Network cameraNetwork,Listener listener){
        stop();
        long began=android.os.SystemClock.elapsedRealtime();
        RtspMediaSource source=new RtspMediaSource.Factory()
            .setSocketFactory(cameraNetwork.getSocketFactory())
            .setForceUseRtpTcp(true)
            .setTimeoutMs(8000)
            .createMediaSource(MediaItem.fromUri(Uri.parse(RTSP_URL)));
        ExoPlayer p=new ExoPlayer.Builder(context).build();
        player=p;view.setPlayer(p);
        p.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){
                listener.firstFrame(android.os.SystemClock.elapsedRealtime()-began);
            }
            @Override public void onVideoSizeChanged(VideoSize size){
                listener.dimensions(size.width,size.height);
            }
            @Override public void onPlayerError(PlaybackException exception){
                listener.error(exception.errorCode,C110PreviewProbe.playbackKind(exception));
            }
            @Override public void onPlaybackStateChanged(int state){listener.state(state);}
        });
        p.setVolume(0f);
        p.setMediaSource(source);
        p.prepare();
        p.play();
    }
    void stop(){
        ExoPlayer old=player;player=null;
        view.setPlayer(null);
        if(old!=null)old.release();
    }
}
