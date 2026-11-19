package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.util.TypedValue;
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
import java.util.concurrent.Future;
import android.os.SystemClock;
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
    private C110FrameReader frameReader;
    private TextView taskChip;
    private final C110WifiCamera photoCamera=new C110WifiCamera();
    private Button photoButton;
    private Future<?> photoJob;
    private boolean photoBusy,pageVisible=true;
    private int photoGeneration;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        deviceControls=new C110DeviceControls(this,this::say);
        frameReader=new C110FrameReader(this,this::say);
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

        // Proven OEM player: the exact visible VLCVideoLayout stays attached from creation.
        video=new VLCVideoLayout(this);
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));

        // Match the normal Blue Star screen: one tap anywhere in the live view
        // copies ONE fresh VLC Surface frame; no native shutter or page jump.
        View tapSurface=new View(this);
        tapSurface.setFocusable(true);
        tapSurface.setContentDescription("C110实时预览画面，点击立即截取当前帧并识别朗读");
        tapSurface.setOnClickListener(v->frameReader.tap(video));
        root.addView(tapSurface,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(12),dp(5),dp(12),dp(5));
        titleRow.setBackgroundColor(0xE5091D36);
        ImageView brand=new ImageView(this);
        brand.setImageResource(R.drawable.bluestar_globe);
        titleRow.addView(brand,new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView title=new TextView(this);
        title.setText("蓝星向导");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP,19);
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setPadding(dp(6),0,0,0);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(50),1f));
        beginButton=new Button(this);
        beginButton.setText("↻");
        beginButton.setTextSize(24);
        beginButton.setContentDescription("重新连接C110实时预览");
        beginButton.setOnClickListener(v->{
            if(photoBusy){say("高清拍照中，请勿切换视频模式");return;}
            stop(true);begin();
        });
        titleRow.addView(beginButton,new LinearLayout.LayoutParams(dp(45),dp(45)));
        Button cameraSettings=new Button(this);
        cameraSettings.setText("相机");
        cameraSettings.setAllCaps(false);
        cameraSettings.setContentDescription("C110机身设置：提示音和指示灯");
        cameraSettings.setOnClickListener(v->deviceControls.showSettings());
        titleRow.addView(cameraSettings,new LinearLayout.LayoutParams(dp(70),dp(45)));
        Button globalSettings=new Button(this);
        globalSettings.setText("☰");
        globalSettings.setTextSize(24);
        globalSettings.setContentDescription("打开三横杠全局设置");
        globalSettings.setOnClickListener(v->returnToMain("settings"));
        titleRow.addView(globalSettings,new LinearLayout.LayoutParams(dp(47),dp(45)));
        root.addView(titleRow,new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));

        taskChip=new TextView(this);
        taskChip.setTextColor(0xFFECF6FF);
        taskChip.setTextSize(15);
        taskChip.setPadding(dp(14),dp(7),dp(14),dp(7));
        taskChip.setBackground(roundRect(0xD9153359,14));
        taskChip.setText("任务："+selectedTaskName());
        FrameLayout.LayoutParams chip=new FrameLayout.LayoutParams(-2,dp(37),
            Gravity.TOP|Gravity.LEFT);
        chip.leftMargin=dp(12);chip.topMargin=dp(64);
        root.addView(taskChip,chip);

        output=new TextView(this);
        output.setGravity(Gravity.CENTER);
        output.setTextColor(Color.WHITE);
        output.setTextSize(13);
        output.setMaxLines(2);
        output.setPadding(dp(10),dp(6),dp(10),dp(6));
        output.setBackground(roundRect(0xDB163758,13));
        output.setText("连接C110后，点击画面快速取帧");
        output.setContentDescription("C110预览状态；长按复制完整诊断");
        output.setOnLongClickListener(v->{copyDiagnostic();return true;});
        FrameLayout.LayoutParams status=new FrameLayout.LayoutParams(-1,-2,
            Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        status.leftMargin=dp(12);status.rightMargin=dp(12);status.bottomMargin=dp(99);
        root.addView(output,status);

        LinearLayout footer=new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(7),dp(5),dp(7),dp(7));
        footer.setBackground(roundRect(0xED091D36,20));
        Button tasks=footerButton("▦\n任务",this::showCameraTasks);
        Button talk=footerButton("🎙\nAI对话",()->returnToMain("ai"));
        photoButton=footerButton("▤\n高清拍照",this::captureOriginalPhoto);
        photoButton.setContentDescription("C110真实高清快门，留在相机页面拍照并识别朗读");
        Button modes=footerButton("◉\n模式",this::showCameraModes);
        for(Button item:new Button[]{tasks,talk,photoButton,modes}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(75),1f);
            lp.setMargins(dp(3),0,dp(3),0);
            footer.addView(item,lp);
        }
        root.addView(footer,new FrameLayout.LayoutParams(-1,dp(89),Gravity.BOTTOM));
        setContentView(root);
        say("C110正在初始化预览；点击画面取帧朗读，长按状态复制诊断");
        // Proven OEM start sequence remains unchanged below.
        main.post(this::begin);
    }


    private int dp(int d){return Math.round(d*getResources().getDisplayMetrics().density);}

    private android.graphics.drawable.GradientDrawable roundRect(int color,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }

    private String selectedTaskName(){
        String id=TaskCatalog.selectedId(this);
        for(TaskCatalog.Task t:TaskCatalog.all(this))
            if(t.id.equals(id))return t.name;
        return "朗读文字";
    }

    private void copyDiagnostic(){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null)
            clipboard.setPrimaryClip(ClipData.newPlainText("C110 Preview",log.toString()));
        say("C110诊断已复制");
    }

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
                    if(taskChip!=null)taskChip.setText("任务："+task.name);
                    say("当前任务："+task.name);
                }
                @Override public void createTask(){returnToMain("tasks");}
                @Override public void openReading(){returnToMain("reading");}
                @Override public void removed(TaskCatalog.Task task){
                    say("任务列表已更新："+task.name);
                }
            });
    }

    /**
     * C110 high resolution capture stays in this page.
     * Close RTSP before entering photo mode; issue exactly one 1001 shutter;
     * only return to RTSP after a complete fresh JPEG or reported failure.
     */
    private void captureOriginalPhoto(){
        if(photoBusy){
            say("上一张高清照片仍在获取，请勿重复触发");return;
        }
        if(busy||!started){
            say("C110预览尚未连接，请确认画面后再拍高清照片");return;
        }
        if(frameReader.isBusy()){
            say("上一项AI识别尚未结束，请稍后拍照");return;
        }
        photoBusy=true;
        final int ticket=++photoGeneration;
        final long at=SystemClock.elapsedRealtime();
        // Stop and release the SAME proven video player before 3001/par=0.
        // No Activity finish, no MainActivity re-entry, no delayed callback race.
        stop(false);
        beginButton.setEnabled(false);
        photoButton.setEnabled(false);
        say("C110高清拍照：切换照片模式，执行一次真实快门…");
        photoJob=io.submit(()->{
            try{
                C110WifiCamera.Photo photo=photoCamera.shootAfterLivePreview(this);
                main.post(()->{
                    if(ticket!=photoGeneration||!pageVisible||isFinishing())return;
                    say("高清原图 "+photo.width+"×"+photo.height+
                        " 已下载，拍摄取图 "+photo.totalMs+"ms，快门命令 "+
                        photo.shutterMs+"ms");
                    finishPhotoFlow(ticket,photo);
                });
            }catch(Exception ex){
                final String detail=ex.getMessage()==null?
                    ex.getClass().getSimpleName():ex.getMessage();
                photoCamera.resetPhotoMode();
                main.post(()->{
                    if(ticket!=photoGeneration||!pageVisible||isFinishing())return;
                    say("高清拍照未完成："+detail+"。不会自动重拍");
                    finishPhotoFlow(ticket,null);
                });
            }
        });
    }

    private void finishPhotoFlow(int ticket,C110WifiCamera.Photo photo){
        if(ticket!=photoGeneration||!pageVisible)return;
        photoBusy=false;
        photoJob=null;
        photoButton.setEnabled(true);
        beginButton.setEnabled(true);
        // One original JPEG, one recognition; no PixelCopy or recompression.
        if(photo!=null&&!frameReader.recognizeOriginalJpeg(
            photo.jpeg,photo.width,photo.height))
            say("已拍得高清照片，但识别流程未开始");
        // User remains on C110 camera page. Resume OEM playback once only.
        say("正在恢复C110实时预览…");
        begin();
    }

    private void begin(){
        if(photoBusy||busy||started||!pageVisible)return;
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
                        if(event.getVoutCount()>0){
                            frameReader.setVideoOutputReady(true);
                            say("C110实时画面已就绪，点击画面取帧识别");
                        }
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
        if(output!=null)output.setText(text);
    }
    private void stop(boolean manual){
        generation++;
        busy=false;started=false;
        deviceControls.onDisconnected();
        if(frameReader!=null)frameReader.stop();
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
    @Override protected void onResume(){super.onResume();pageVisible=true;}
    @Override protected void onPause(){
        pageVisible=false;
        photoGeneration++;
        photoBusy=false;
        if(photoJob!=null){photoJob.cancel(true);photoJob=null;}
        stop(false);
        super.onPause();
    }
    @Override protected void onDestroy(){stop(false);frameReader.shutdown();deviceControls.shutdown();io.shutdownNow();super.onDestroy();}
}
