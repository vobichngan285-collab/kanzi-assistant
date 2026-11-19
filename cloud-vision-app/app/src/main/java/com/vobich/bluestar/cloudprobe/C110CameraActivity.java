package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A separate minimal C110 video player. No photos, OCR, BLE, uploads or retries. */
public final class C110CameraActivity extends Activity {
    private static final String IP="192.168.1.254";
    private static final String VIDEO_URL="rtsp://192.168.1.254:554/xxx.mp4";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final StringBuilder log=new StringBuilder();
    private ConnectivityManager cm;
    private VLCVideoLayout video;
    private TextView output;
    private Button beginButton;
    private LibVLC vlc;
    private MediaPlayer player;
    private Media media;
    private Network originalNetwork,cameraNetwork;
    private boolean changedBinding,busy,started;
    private volatile int generation;
    private Runnable delayedPlay;
    private C110DeviceControls deviceControls;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        deviceControls=new C110DeviceControls(this,this::say);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top=insets.getSystemWindowInsetTop();
            int bottom=insets.getSystemWindowInsetBottom();
            if(view.getPaddingTop()!=top||view.getPaddingBottom()!=bottom)
                view.setPadding(0,top,0,bottom);
            return insets;
        });
        root.requestApplyInsets();
        video=new VLCVideoLayout(this);
        // Always visible from Activity creation: allow LibVLC VideoHelper to
        // manage its own Surface, exactly as the OEM player does.
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10),0,dp(10),0);
        header.setBackgroundColor(0xE6091D36);
        TextView title=new TextView(this);
        title.setText("蓝星向导 · C110");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(54),1f));
        Button cameraSettings=new Button(this);
        cameraSettings.setText("相机设置");
        cameraSettings.setAllCaps(false);
        cameraSettings.setContentDescription("C110机身设置：提示音和指示灯");
        cameraSettings.setOnClickListener(v->deviceControls.showSettings());
        header.addView(cameraSettings,new LinearLayout.LayoutParams(dp(108),dp(48)));
        Button menu=new Button(this);
        menu.setText("☰");
        menu.setTextSize(24);
        menu.setContentDescription("打开三横杠全局设置菜单");
        menu.setOnClickListener(v->returnToMain("settings"));
        header.addView(menu,new LinearLayout.LayoutParams(dp(50),dp(48)));
        root.addView(header,new FrameLayout.LayoutParams(-1,dp(56),Gravity.TOP));

        LinearLayout dock=new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(10,8,10,8);
        dock.setBackgroundColor(0xD511202B);
        LinearLayout buttons=new LinearLayout(this);
        beginButton=new Button(this);
        beginButton.setAllCaps(false);
        beginButton.setText("重新连接预览");
        beginButton.setOnClickListener(v->begin());
        buttons.addView(beginButton,new LinearLayout.LayoutParams(0,-2,2));
        Button stop=new Button(this);
        stop.setText("返回蓝星");
        stop.setContentDescription("返回蓝星向导主界面");
        stop.setOnClickListener(v->finish());
        buttons.addView(stop,new LinearLayout.LayoutParams(0,-2,1));
        Button copy=new Button(this);
        copy.setText("复制诊断");
        copy.setOnClickListener(v->{
            ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(c!=null)c.setPrimaryClip(ClipData.newPlainText("C110 OEM minimal",log.toString()));
            say("诊断已复制");
        });
        buttons.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        dock.addView(buttons);
        ScrollView scroll=new ScrollView(this);
        output=new TextView(this);
        output.setTextColor(Color.WHITE);
        output.setTextSize(13);
        output.setPadding(6,4,6,4);
        scroll.addView(output);
        dock.addView(scroll,new LinearLayout.LayoutParams(-1,96));
        FrameLayout.LayoutParams infoPosition=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        infoPosition.topMargin=dp(56);
        root.addView(dock,infoPosition);

        LinearLayout footer=new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(7),dp(5),dp(7),dp(7));
        footer.setBackgroundColor(0xED091D36);
        Button taskButton=footerButton("▦\n任务",()->showCameraTasks());
        Button aiButton=footerButton("🎙\nAI对话",()->returnToMain("ai"));
        Button photoButton=footerButton("▤\n高清拍照",()->returnToMain("photo"));
        Button modeButton=footerButton("◉\n模式",()->showCameraModes());
        for(Button footerItem:new Button[]{taskButton,aiButton,photoButton,modeButton}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(75),1f);
            lp.setMargins(dp(3),0,dp(3),0);
            footer.addView(footerItem,lp);
        }
        root.addView(footer,new FrameLayout.LayoutParams(-1,dp(89),Gravity.BOTTOM));
        setContentView(root);
        say("C110独立实时预览：不拍照、不上传、不修改成功播放流程");
        say("一次按钮：3012设备信息→3016模式查询→3001/1→2015/1→LibVLC");
        // Stage1: dedicated portrait playback only, preserving OEM startup exactly.
        // Auto-start from the proven dedicated video Activity.
        main.post(this::begin);
    }


    private int dp(int d){return Math.round(d*getResources().getDisplayMetrics().density);}

    private Button footerButton(String label,Runnable click){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setContentDescription(label.replace("\n","，"));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xFF2C547D);
        bg.setCornerRadius(dp(15));
        b.setBackground(bg);
        b.setOnClickListener(v->click.run());
        return b;
    }

    private void returnToMain(String action){
        Intent result=new Intent();
        result.putExtra("c110_action",action);
        setResult(RESULT_OK,result);
        finish();
    }

    private void showCameraModes(){
        new android.app.AlertDialog.Builder(this).setTitle("选择模式")
            .setItems(new String[]{"手机摄像头","C110 运动相机","USB 摄像头"},(d,i)->{
                if(i==0)returnToMain("mode_phone");
                else if(i==2)returnToMain("mode_usb");
            }).setNegativeButton("取消",null).show();
    }

    private void showCameraTasks(){
        TaskDesktopOverlay.show(this,TaskCatalog.all(this),TaskCatalog.selectedId(this),
            new TaskDesktopOverlay.Listener(){
                @Override public void selected(TaskCatalog.Task task){
                    TaskCatalog.select(C110CameraActivity.this,task.id);
                    say("当前任务："+task.name);
                }
                @Override public void createTask(){returnToMain("tasks");}
                @Override public void openReading(){returnToMain("reading");}
                @Override public void removed(TaskCatalog.Task task){
                    say("任务列表已更新："+task.name);
                }
            });
    }

    private void begin(){
        if(busy||started)return;
        final int id=++generation;
        busy=true;
        beginButton.setEnabled(false);
        say("开始获取C110 Wi-Fi");
        io.execute(()->{
            try{
                Network wifi=findCameraWifi();
                String info=get(wifi,3012,-1);
                if(tagInt(info,"Status")!=0)
                    throw new IllegalStateException("3012未确认相机身份");
                String family=family(info);
                runWhenCurrent(id,()->say("3012状态0；类别="+family));
                // 3016 is a read-only current-mode query in LYCamera.handlerConnection.
                String mode=get(wifi,3016,-1);
                int value=tagInt(mode,"Value");
                runWhenCurrent(id,()->say("3016模式状态="+value+"（仅数值）"));
                if(family.equals("C110_PRO"))
                    throw new IllegalStateException("C110_PRO有额外固件分支，停止而不猜测URL");
                if(id!=generation)return;
                if(tagInt(get(wifi,3001,1),"Status")!=0)
                    throw new IllegalStateException("3001/1视频模式设置失败");
                runWhenCurrent(id,()->say("3001/par1 成功；LY模式1指向RTSP候选"));
                if(id!=generation)return;
                if(tagInt(get(wifi,2015,1),"Status")!=0)
                    throw new IllegalStateException("2015/1启流失败");
                runWhenCurrent(id,()->{say("2015/par1成功");openPlayer(id,wifi);});
            }catch(Exception ex){
                runWhenCurrent(id,()->{
                    say("请求结束："+ex.getClass().getSimpleName());
                    busy=false;
                    beginButton.setEnabled(true);
                });
            }
        });
    }

    private Network findCameraWifi(){
        if(cm==null)throw new IllegalStateException("没有ConnectivityManager");
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            LinkProperties lp=cm.getLinkProperties(n);
            if(caps==null||lp==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
            for(RouteInfo route:lp.getRoutes()){
                if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address
                    &&IP.equals(route.getGateway().getHostAddress()))return n;
            }
        }
        throw new IllegalStateException("未检测到192.168.1.254网关的C110热点");
    }

    private String get(Network wifi,int cmd,int param)throws Exception{
        if(cmd!=3012&&cmd!=3016&&cmd!=3001&&cmd!=2015)
            throw new IllegalArgumentException("不允许的相机命令");
        if((cmd==3001||cmd==2015)&&param!=1)
            throw new IllegalArgumentException("不允许的相机模式参数");
        String path="/?custom=1&cmd="+cmd+(param>=0?"&par=1":"");
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)wifi.openConnection(new URL("http",IP,path));
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(2800);
            conn.setReadTimeout(2800);
            if(conn.getResponseCode()!=200)
                throw new IllegalStateException("相机HTTP请求失败");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buf=new byte[2048];int len;
                while((len=in.read(buf))!=-1){
                    if(bytes.size()+len>32768)throw new IllegalStateException("响应超长");
                    bytes.write(buf,0,len);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        }finally{if(conn!=null)conn.disconnect();}
    }

    private static int tagInt(String xml,String tag){
        Matcher m=Pattern.compile("<"+tag+">(-?\\d+)</"+tag+">",Pattern.CASE_INSENSITIVE).matcher(xml);
        if(!m.find())return -4096;
        try{return Integer.parseInt(m.group(1));}catch(NumberFormatException ex){return -4096;}
    }
    private static String family(String info){
        Matcher m=Pattern.compile("<String>([^<]{0,128})</String>",
            Pattern.CASE_INSENSITIVE).matcher(info);
        boolean standard=false;
        while(m.find()){
            String s=m.group(1).toUpperCase(Locale.ROOT);
            if(s.contains("C110_PRO"))return "C110_PRO";
            if(s.contains("C110"))standard=true;
        }
        return standard?"C110":"未知（仅按已连接的C110网关使用模式1）";
    }

    private void openPlayer(int id,Network wifi){
        if(id!=generation)return;
        try{
            originalNetwork=cm.getBoundNetworkForProcess();
            cameraNetwork=wifi;
            if(!wifi.equals(originalNetwork)){
                if(!cm.bindProcessToNetwork(wifi))throw new IllegalStateException("VLC绑定Wi-Fi失败");
                changedBinding=true;
            }
            say("原生VLC已绑定C110 Wi-Fi");
            ArrayList<String> options=new ArrayList<>();
            options.add("--network-caching=300");
            options.add("--live-caching=300");
            options.add("--no-audio");
            vlc=new LibVLC(getApplicationContext(),options);
            player=new MediaPlayer(vlc);
            // OEM uses LibVLC + VLCVideoLayout without our extra Surface gate.
            player.attachViews(video,null,false,false);
            final MediaPlayer active=player;
            active.setEventListener(event->runOnUiThread(()->{
                if(id!=generation||player!=active)return;
                switch(event.type){
                    case MediaPlayer.Event.MediaChanged:say("VLC 256 MediaChanged");break;
                    case MediaPlayer.Event.Opening:say("VLC 258 Opening");break;
                    case MediaPlayer.Event.Playing:say("VLC 260 Playing");break;
                    case MediaPlayer.Event.Vout:
                        if(event.getVoutCount()>0)say("VLC Vout="+event.getVoutCount()+"（请确认真实画面）");
                        break;
                    case MediaPlayer.Event.EncounteredError:say("VLC原生 EncounteredError");break;
                    case MediaPlayer.Event.EndReached:say("VLC EndReached");break;
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
            active.setMedia(media);
            active.setVolume(0);
            say("VLC布局已连接；交由其原生VideoHelper管理Surface");
            delayedPlay=()->{
                if(id!=generation||player!=active)return;
                active.play();
                say("play调用一次；等原生Opening");
                main.postDelayed(()->{
                    if(id!=generation||player!=active)return;
                    say("原生state="+active.getPlayerState()+" isPlaying="+(active.isPlaying()?1:0));
                },1200);
            };
            main.postDelayed(delayedPlay,300);
            busy=false;started=true;
            deviceControls.onCameraConnected(wifi);
        }catch(RuntimeException|LinkageError ex){
            say("VLC启动异常："+ex.getClass().getSimpleName());
            stop(false);
        }
    }
    private void runWhenCurrent(int id,Runnable action){
        main.post(()->{if(id==generation)action.run();});
    }
    private void say(String text){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            main.post(()->say(text));return;
        }
        log.append(text).append('\n');
        if(log.length()>4500)log.delete(0,log.length()-3000);
        if(output!=null)output.setText(log.toString());
    }
    private void stop(boolean manual){
        generation++;
        busy=false;started=false;
        deviceControls.onDisconnected();
        if(delayedPlay!=null){main.removeCallbacks(delayedPlay);delayedPlay=null;}
        MediaPlayer p=player;player=null;
        if(p!=null){
            try{p.setEventListener(null);p.stop();p.detachViews();p.release();}
            catch(RuntimeException ignored){}
        }
        Media m=media;media=null;
        if(m!=null)try{m.release();}catch(RuntimeException ignored){}
        LibVLC lib=vlc;vlc=null;
        if(lib!=null)try{lib.release();}catch(RuntimeException ignored){}
        if(changedBinding&&cm!=null&&cameraNetwork!=null){
            try{
                if(cameraNetwork.equals(cm.getBoundNetworkForProcess()))
                    cm.bindProcessToNetwork(originalNetwork);
            }catch(RuntimeException ignored){}
        }
        changedBinding=false;cameraNetwork=null;originalNetwork=null;
        if(beginButton!=null)beginButton.setEnabled(true);
        if(manual)say("已停止并恢复此前网络");
    }
    @Override protected void onPause(){stop(false);super.onPause();}
    @Override protected void onDestroy(){stop(false);deviceControls.shutdown();io.shutdownNow();super.onDestroy();}
}
