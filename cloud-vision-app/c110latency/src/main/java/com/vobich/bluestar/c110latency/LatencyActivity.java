package com.vobich.bluestar.c110latency;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.text.InputType;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone, user-controlled C110 RTSP caching experiment.
 *
 * The ALREADY VERIFIED OEM command sequence, always-visible VLCVideoLayout,
 * LibVLC version, decoder flags, stream URL and one-shot delayed play stay
 * intact. ONLY the network/live cache values are changed between runs.
 *
 * Observables are timing of player events, optional LibVLC frame statistics,
 * buffering callbacks, UI-observed stalls and CPU time. NONE equals physical
 * camera-to-display glass-to-glass latency; user labels that separately.
 *
 * No shutter, no cloud, no OCR, no TTS, no BLE, no image uploads or saves.
 */
public final class LatencyActivity extends Activity {
    private static final String IP="192.168.1.254";
    private static final String VIDEO_URL="rtsp://192.168.1.254:554/xxx.mp4";
    private static final int DEFAULT_CACHE_MS=300;
    private static final int MAX_LOG=28000;

    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final StringBuilder log=new StringBuilder();
    private final SimpleDateFormat clock=new SimpleDateFormat("HH:mm:ss.SSS",Locale.CHINA);
    private ConnectivityManager cm;
    private VLCVideoLayout video;
    private TextView status,summary;
    private Button playButton;
    private LibVLC vlc;
    private MediaPlayer player;
    private Media media;
    private Network originalNetwork,cameraNetwork;
    private boolean changedBinding,busy,started,visible,initialRun=true;
    private volatile int generation;
    private Runnable delayedPlay,monitor;
    private int cacheMs=DEFAULT_CACHE_MS;
    private long sessionStarted,playCalled,playingAt,voutAt,lastMonitorAt,lastCpuMs;
    private long lastFrames=-1,lastLost=-1,totalLost,totalFrames;
    private int sampleCount,bufferEvents,stallSamples,voutCount,playerErrors,firstFrameCount;
    private float lastMeasuredFps=-1f,lastCpuPercent=-1f;
    private boolean statsUnsupported;

    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video=new VLCVideoLayout(this);
        // Critical success invariant: LibVLC's own VideoHelper owns a visible
        // VLCVideoLayout from Activity creation. Never add a Surface gate.
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(8),dp(5),dp(8),dp(7));
        header.setBackgroundColor(0xE50B1C31);
        TextView title=new TextView(this);
        title.setText("C110 预览延迟实验 · 仅调整缓存");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        header.addView(title,new LinearLayout.LayoutParams(-1,dp(36)));
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout choices=new LinearLayout(this);
        for(int value:new int[]{300,150,100,50,500}){
            Button preset=button(value+"ms");
            preset.setOnClickListener(v->switchTo(value));
            choices.addView(preset,new LinearLayout.LayoutParams(dp(77),dp(50)));
        }
        Button custom=button("自定义");
        custom.setOnClickListener(v->customCache());
        choices.addView(custom,new LinearLayout.LayoutParams(dp(95),dp(50)));
        scroll.addView(choices);
        header.addView(scroll);
        root.addView(header,new FrameLayout.LayoutParams(-1,dp(101),Gravity.TOP));

        LinearLayout footer=new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(8),dp(5),dp(8),dp(8));
        footer.setBackgroundColor(0xE50B1C31);
        summary=new TextView(this);
        summary.setTextColor(Color.WHITE);
        summary.setTextSize(14);
        summary.setMaxLines(3);
        summary.setText("实验还未启动。请先手动连接C110 Wi-Fi。");
        footer.addView(summary,new LinearLayout.LayoutParams(-1,dp(72)));

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        playButton=button("重新播放");
        playButton.setOnClickListener(v->switchTo(cacheMs));
        Button mark=button("标记体感延迟");
        mark.setOnClickListener(v->markDelay());
        Button copy=button("复制诊断");
        copy.setOnClickListener(v->copyLog());
        for(Button b:new Button[]{playButton,mark,copy}){
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,dp(60),1);
            layout.setMargins(dp(2),0,dp(2),0);
            actions.addView(b,layout);
        }
        footer.addView(actions);
        status=new TextView(this);
        status.setTextColor(0xFFE3EFFA);
        status.setTextSize(12);
        status.setMaxLines(2);
        status.setText("将收集播放器事件及帧统计，不收集相机画面。");
        footer.addView(status,new LinearLayout.LayoutParams(-1,dp(50)));
        root.addView(footer,new FrameLayout.LayoutParams(-1,dp(197),Gravity.BOTTOM));
        setContentView(root);
        event("实验启动：内存日志；原厂3012→3016→3001/1→2015/1与LibVLC 3.6.5");
        event("仅缓存随档位改变；codec、file-cache=300、sout-mux-cache=300、play延时300保持不变");
        event("注意：Vout是视频输出就绪事件，不是独立帧，日志无法直接测量物理端到端延迟");
    }

    private int dp(int units){return Math.round(units*getResources().getDisplayMetrics().density);}
    private Button button(String label){
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);
        b.setTextSize(label.length()>5?12:14);
        b.setContentDescription(label);
        return b;
    }

    private void customCache(){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("50～1500毫秒");
        input.setText(String.valueOf(cacheMs));
        new AlertDialog.Builder(this).setTitle("输入实验缓存（毫秒）")
            .setView(input)
            .setPositiveButton("切换测试",(dialog,which)->{
                try{
                    int value=Integer.parseInt(input.getText().toString().trim());
                    if(value<50||value>1500)throw new NumberFormatException();
                    switchTo(value);
                }catch(NumberFormatException bad){
                    event("拒绝自定义缓存：请输入50～1500之间的整数");
                }
            }).setNegativeButton("取消",null).show();
    }

    private void switchTo(int requested){
        if(requested<50||requested>1500)return;
        stopPlayer(false);
        cacheMs=requested;
        if(!visible)return;
        final int id=++generation;
        busy=true;
        sessionStarted=SystemClock.elapsedRealtime();
        playingAt=0;voutAt=0;playCalled=0;
        lastFrames=-1;lastLost=-1;totalFrames=0;totalLost=0;
        sampleCount=0;bufferEvents=0;stallSamples=0;voutCount=0;
        playerErrors=0;firstFrameCount=0;statsUnsupported=false;
        lastMeasuredFps=-1;lastCpuPercent=-1;
        lastMonitorAt=0;lastCpuMs=0;
        playButton.setEnabled(false);
        summary.setText("档位 "+cacheMs+"ms：获取C110网络与视频流…");
        event("=== SESSION cache_ms="+cacheMs+" at="+stamp()+" ===");
        io.execute(()->{
            try{
                Network wifi=findCameraWifi();
                if(id!=generation)return;
                String identity=get(wifi,3012,-1);
                if(tagInt(identity,"Status")!=0)
                    throw new IllegalStateException("3012拒绝设备身份");
                String model=family(identity);
                if("C110_PRO".equals(model))
                    throw new IllegalStateException("本实验不适用于C110 Pro");
                runCurrent(id,()->event("3012 status=0 model="+model));
                String mode=get(wifi,3016,-1);
                runCurrent(id,()->event("3016 mode="+tagInt(mode,"Value")));
                if(id!=generation)return;
                if(tagInt(get(wifi,3001,1),"Status")!=0)
                    throw new IllegalStateException("3001/par1视频模式失败");
                if(id!=generation)return;
                if(tagInt(get(wifi,2015,1),"Status")!=0)
                    throw new IllegalStateException("2015/par1启流失败");
                runCurrent(id,()->openPlayer(id,wifi));
            }catch(Exception e){
                runCurrent(id,()->{
                    busy=false;
                    playButton.setEnabled(true);
                    event("SESSION failed="+e.getClass().getSimpleName()+" "+e.getMessage());
                    summary.setText("连接失败：请先在系统Wi-Fi连接C110，然后点「重新播放」。");
                });
            }
        });
    }

    private Network findCameraWifi(){
        if(cm==null)throw new IllegalStateException("ConnectivityManager不可用");
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            LinkProperties props=cm.getLinkProperties(n);
            if(caps==null||props==null||
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
            for(RouteInfo route:props.getRoutes())
                if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address&&
                    IP.equals(route.getGateway().getHostAddress()))return n;
        }
        throw new IllegalStateException("未找到192.168.1.254网关；该独立实验包需要先手动连接C110热点");
    }

    // Exact already-verified standard C110 whitelist and parameter format.
    private String get(Network wifi,int cmd,int param)throws Exception{
        if(cmd!=3012&&cmd!=3016&&cmd!=3001&&cmd!=2015)
            throw new IllegalArgumentException("禁止的相机命令");
        if((cmd==3001||cmd==2015)&&param!=1)
            throw new IllegalArgumentException("禁止的模式参数");
        String path="/?custom=1&cmd="+cmd+(param>=0?"&par=1":"");
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)wifi.openConnection(new URL("http",IP,path));
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(2800);
            connection.setReadTimeout(2800);
            if(connection.getResponseCode()!=200)
                throw new IllegalStateException("C110 HTTP非200");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream result=new ByteArrayOutputStream()){
                byte[] buffer=new byte[2048];int len;
                while((len=in.read(buffer))!=-1){
                    if(result.size()+len>32768)
                        throw new IllegalStateException("C110回执过长");
                    result.write(buffer,0,len);
                }
                return result.toString(StandardCharsets.UTF_8.name());
            }
        }finally{if(connection!=null)connection.disconnect();}
    }
    private static int tagInt(String xml,String field){
        Matcher match=Pattern.compile("<"+field+">(-?\\d+)</"+field+">",
            Pattern.CASE_INSENSITIVE).matcher(xml);
        if(!match.find())return -4096;
        try{return Integer.parseInt(match.group(1));}
        catch(NumberFormatException ignored){return -4096;}
    }
    private static String family(String xml){
        Matcher m=Pattern.compile("<String>([^<]{0,128})</String>",
            Pattern.CASE_INSENSITIVE).matcher(xml);
        boolean standard=false;
        while(m.find()){
            String name=m.group(1).toUpperCase(Locale.ROOT);
            if(name.contains("C110_PRO")||name.contains("C110+"))return "C110_PRO";
            if(name.contains("C110"))standard=true;
        }
        return standard?"C110":"未分类";
    }

    private void openPlayer(int id,Network wifi){
        if(id!=generation||!visible)return;
        try{
            originalNetwork=cm.getBoundNetworkForProcess();
            cameraNetwork=wifi;
            if(!wifi.equals(originalNetwork)){
                if(!cm.bindProcessToNetwork(wifi))
                    throw new IllegalStateException("LibVLC绑定C110 Network失败");
                changedBinding=true;
            }
            ArrayList<String> options=new ArrayList<>();
            options.add("--network-caching="+cacheMs);
            options.add("--live-caching="+cacheMs);
            options.add("--no-audio");
            vlc=new LibVLC(getApplicationContext(),options);
            player=new MediaPlayer(vlc);
            player.attachViews(video,null,false,false);
            MediaPlayer active=player;
            active.setEventListener(event->runOnUiThread(()->{
                if(id!=generation||player!=active)return;
                switch(event.type){
                    case MediaPlayer.Event.Playing:
                        if(playingAt==0){
                            playingAt=SystemClock.elapsedRealtime();
                            event("Playing at_ms="+(playingAt-sessionStarted)+
                                " from_play_ms="+(playCalled==0?-1:playingAt-playCalled));
                        }
                        break;
                    case MediaPlayer.Event.Vout:
                        voutCount++;
                        if(event.getVoutCount()>0&&voutAt==0){
                            voutAt=SystemClock.elapsedRealtime();
                            event("Vout first_event_ms="+(voutAt-sessionStarted)+
                                " from_play_ms="+(playCalled==0?-1:voutAt-playCalled)+
                                " (Vout is not a visible-pixel timestamp)");
                        }
                        break;
                    case MediaPlayer.Event.Buffering:bufferEvents++;break;
                    case MediaPlayer.Event.EncounteredError:
                        playerErrors++;
                        event("ERROR: VLC EncounteredError");
                        break;
                    case MediaPlayer.Event.EndReached:event("VLC EndReached");break;
                    default:break;
                }
            }));
            media=new Media(vlc,Uri.parse(VIDEO_URL));
            media.addOption(":no-audio");
            media.addOption(":network-caching="+cacheMs);
            media.addOption(":live-caching="+cacheMs);
            // Intentionally fixed to preserve the working OEM decoder pipeline.
            media.addOption(":file-caching=300");
            media.addOption(":sout-mux-caching=300");
            media.addOption(":codec=mediacodec,iomx,all");
            active.setMedia(media);
            active.setVolume(0);
            event("LibVLC attached codec=mediacodec,iomx,all cache_network_live="+cacheMs);
            delayedPlay=()->{
                if(id!=generation||player!=active)return;
                playCalled=SystemClock.elapsedRealtime();
                active.play(); // Exactly one call, OEM 300ms delayed-play stays fixed.
                event("play_call_ms="+(playCalled-sessionStarted));
                startMonitoring(id);
            };
            main.postDelayed(delayedPlay,300);
            busy=false;started=true;playButton.setEnabled(true);
        }catch(RuntimeException|LinkageError error){
            event("VLC startup failed="+error.getClass().getSimpleName());
            stopPlayer(false);
        }
    }

    private void startMonitoring(int id){
        monitor=new Runnable(){
            @Override public void run(){
                if(id!=generation||!started||player==null)return;
                sampleStats();
                main.postDelayed(this,2000);
            }
        };
        main.postDelayed(monitor,2000);
    }

    private static long readCounter(Object stats,String name) throws Exception{
        Field f=stats.getClass().getField(name);
        Object v=f.get(stats);
        return v instanceof Number?((Number)v).longValue():-1L;
    }

    private void sampleStats(){
        long now=SystemClock.elapsedRealtime();
        long cpu=android.os.Process.getElapsedCpuTime();
        long elapsed=lastMonitorAt>0?now-lastMonitorAt:0;
        if(elapsed>0)lastCpuPercent=100f*(cpu-lastCpuMs)/elapsed;
        lastMonitorAt=now;lastCpuMs=cpu;
        float fps=-1f;
        long dropDelta=-1;
        if(media!=null){
            try{
                // LibVLC 3.6.5 exposes stats via getStats on many builds.
                // Reflection gracefully degrades if this native binding is absent.
                Method accessor=media.getClass().getMethod("getStats");
                Object stats=accessor.invoke(media);
                if(stats!=null){
                    long displayed=readCounter(stats,"displayedPictures");
                    long lost=readCounter(stats,"lostPictures");
                    if(lastFrames>=0&&displayed>=lastFrames&&elapsed>0){
                        long frameDelta=displayed-lastFrames;
                        totalFrames+=frameDelta;
                        fps=1000f*frameDelta/elapsed;
                        lastMeasuredFps=fps;
                    }
                    if(lastLost>=0&&lost>=lastLost){
                        dropDelta=lost-lastLost;
                        totalLost+=dropDelta;
                    }
                    lastFrames=displayed;lastLost=lost;
                }
            }catch(Exception|LinkageError error){
                if(!statsUnsupported){
                    statsUnsupported=true;
                    event("帧统计API不可用："+error.getClass().getSimpleName()+
                        "，不会把Vout事件误当帧率");
                }
            }
        }
        boolean playing=player!=null&&player.isPlaying();
        if(playingAt>0&&!playing)stallSamples++;
        sampleCount++;
        String fpsText=fps<0?"未知":String.format(Locale.US,"%.1f",fps);
        String cpuText=lastCpuPercent<0?"未知":
            String.format(Locale.US,"%.0f%%",lastCpuPercent);
        summary.setText("缓存 "+cacheMs+"ms  |  实际显示帧率 "+fpsText+"FPS\n"+
            "播放中 "+(playing?"是":"否")+"  |  缓冲回调 "+bufferEvents+
            "  |  非播放采样 "+stallSamples+"\n"+
            "最近2秒丢帧 "+(dropDelta<0?"未知":String.valueOf(dropDelta))+
            "  |  进程CPU约 "+cpuText+"（单核口径）");
        event("SAMPLE cache_ms="+cacheMs+" sec="+(now-sessionStarted)/1000+
            " playing="+(playing?1:0)+" fps="+fpsText+
            " lost_delta="+dropDelta+" buffer_events="+bufferEvents+
            " nonplaying_samples="+stallSamples+" cpu_one_core="+cpuText);
    }

    private void stopPlayer(boolean fromLifecycle){
        boolean hadSession=started||busy;
        if(hadSession){
            long duration=sessionStarted>0?SystemClock.elapsedRealtime()-sessionStarted:0;
            event("SUMMARY cache_ms="+cacheMs+" elapsed_ms="+duration+
                " playing_event_ms="+(playingAt>0?playingAt-sessionStarted:-1)+
                " vout_event_ms="+(voutAt>0?voutAt-sessionStarted:-1)+
                " samples="+sampleCount+" displayed_delta="+totalFrames+
                " lost_delta="+totalLost+" buffering_callbacks="+bufferEvents+
                " nonplaying_samples="+stallSamples+" vlc_errors="+playerErrors);
        }
        generation++;
        busy=false;started=false;
        if(monitor!=null){main.removeCallbacks(monitor);monitor=null;}
        if(delayedPlay!=null){main.removeCallbacks(delayedPlay);delayedPlay=null;}
        MediaPlayer old=player;player=null;
        if(old!=null){
            try{old.setEventListener(null);old.stop();old.detachViews();old.release();}
            catch(RuntimeException ignored){}
        }
        Media current=media;media=null;
        if(current!=null)try{current.release();}catch(RuntimeException ignored){}
        LibVLC lib=vlc;vlc=null;
        if(lib!=null)try{lib.release();}catch(RuntimeException ignored){}
        if(changedBinding&&cm!=null&&cameraNetwork!=null){
            try{
                if(cameraNetwork.equals(cm.getBoundNetworkForProcess()))
                    cm.bindProcessToNetwork(originalNetwork);
            }catch(RuntimeException ignored){}
        }
        changedBinding=false;originalNetwork=null;cameraNetwork=null;
        if(playButton!=null)playButton.setEnabled(true);
        if(fromLifecycle&&hadSession)event("会话结束：离开前台已停止播放与采样");
    }

    private void markDelay(){
        String[] labels={"几乎同步（看不出明显滞后）",
            "轻微滞后（主观约0.1～0.3秒）",
            "明显慢半拍（主观约0.3～0.7秒）",
            "严重滞后（主观超过0.7秒）",
            "画面不流畅或卡住"};
        new AlertDialog.Builder(this).setTitle("标记当前档位的主观观察")
            .setItems(labels,(dialog,which)->{
                event("USER_OBSERVATION cache_ms="+cacheMs+
                    " choice="+which+" label="+labels[which]);
                status.setText("已标记："+labels[which]);
            }).setNegativeButton("取消",null).show();
    }

    private void copyLog(){
        event("EXPORT log_events_only no_frame no_password no_audio");
        ClipboardManager manager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(manager!=null)
            manager.setPrimaryClip(ClipData.newPlainText("C110 latency lab",log.toString()));
        status.setText("诊断已复制；可粘贴到聊天中。未包含相机画面、Wi-Fi密码或云端数据。");
    }

    private void runCurrent(int id,Runnable action){
        main.post(()->{if(id==generation&&visible)action.run();});
    }
    private String stamp(){return clock.format(new Date());}
    private void event(String message){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            main.post(()->event(message));return;
        }
        log.append(stamp()).append(" ").append(message).append('\n');
        if(log.length()>MAX_LOG)log.delete(0,log.length()-20000);
        if(status!=null)status.setText(message);
    }

    @Override protected void onResume(){
        super.onResume();visible=true;
        if(initialRun){
            initialRun=false;
            main.post(()->switchTo(DEFAULT_CACHE_MS));
        }
    }
    @Override protected void onPause(){
        visible=false;
        stopPlayer(true);
        super.onPause();
    }
    @Override protected void onDestroy(){
        stopPlayer(false);
        io.shutdownNow();
        super.onDestroy();
    }
}
