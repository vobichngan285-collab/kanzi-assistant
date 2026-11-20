package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.res.Configuration;
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
import android.widget.SeekBar;

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
    private CaptureDingFeedback captureDing;
    private TextView taskChip;
    private final C110WifiCamera photoCamera=new C110WifiCamera();
    private Button photoButton;
    private Future<?> photoJob;
    private boolean photoBusy,pageVisible=true;
    private int photoGeneration;
    private C110WifiCamera.DeletionCandidate pendingDeletion;
    private C110WifiBootstrap isolatedWifiBootstrap;
    private Runnable cameraConnectPoll;
    private long cameraConnectStartedMs;
    private boolean waitingForCameraWifi;
    // UI-only zoom and fullscreen never alter the verified RTSP/VLC/C110 Wi-Fi commands.
    private FrameLayout cameraRoot;
    private LinearLayout topControls,bottomControls,zoomControls;
    private Button fullscreenButton,fullscreenPhoto;
    private TextView zoomValue;
    private SeekBar zoomSeek;
    private float zoom=1f;
    private boolean fullscreen;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        deviceControls=new C110DeviceControls(this,this::say);
        frameReader=new C110FrameReader(this,this::say);
        captureDing=new CaptureDingFeedback(this);
        FrameLayout root=new FrameLayout(this);
        cameraRoot=root;
        root.setClipChildren(true);
        root.setClipToPadding(true);
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
        video.addOnLayoutChangeListener((view,left,top,right,bottom,oldL,oldT,oldR,oldB)->{
            if(fullscreen)applyZoomPreview();
        });
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));

        // Match the normal Blue Star screen: one tap anywhere in the live view
        // copies ONE fresh VLC Surface frame; no native shutter or page jump.
        View tapSurface=new View(this);
        tapSurface.setFocusable(true);
        tapSurface.setContentDescription("C110实时预览画面，点击立即截取当前帧并识别朗读");
        tapSurface.setOnClickListener(v->frameReader.tap(video,zoom,fullscreen,
                                                         c110ViewportAspect()));
        root.addView(tapSurface,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout titleRow=new LinearLayout(this);
        topControls=titleRow;
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(12),dp(5),dp(12),dp(5));
        titleRow.setBackgroundColor(0xE5091D36);
        Button back=new Button(this);
        back.setText("‹");back.setTextSize(26);back.setAllCaps(false);
        back.setContentDescription("返回蓝星向导主页");
        back.setOnClickListener(v->finish());
        titleRow.addView(back,new LinearLayout.LayoutParams(dp(43),dp(43)));
        TextView title=new TextView(this);
        title.setText("C110+ 预览");title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(48),1f));
        beginButton=new Button(this);
        beginButton.setText("↻");beginButton.setTextSize(22);
        beginButton.setContentDescription("重新连接C110实时预览");
        beginButton.setOnClickListener(v->{
            if(photoBusy){say("高清拍照中，请勿切换视频模式");return;}
            stop(true);cancelCameraWait();requestC110Network(true);
            connectAndBeginWhenReady();
        });
        titleRow.addView(beginButton,new LinearLayout.LayoutParams(dp(43),dp(43)));
        fullscreenButton=new Button(this);
        fullscreenButton.setText("⛶");fullscreenButton.setTextSize(20);
        fullscreenButton.setContentDescription("切换竖屏铺满取景");
        fullscreenButton.setOnClickListener(v->setFullscreen(!fullscreen));
        titleRow.addView(fullscreenButton,new LinearLayout.LayoutParams(dp(43),dp(43)));
        Button cameraSettings=new Button(this);
        cameraSettings.setText("⚙");cameraSettings.setTextSize(21);
        cameraSettings.setContentDescription("C110机身声音与灯光设置");
        cameraSettings.setOnClickListener(v->showC110Settings());
        titleRow.addView(cameraSettings,new LinearLayout.LayoutParams(dp(45),dp(43)));
        // User-approved unobstructed viewfinder: keep these existing controls
        // initialized for OEM connection/photo state, but never draw a top bar.
        // Android system Back remains available; camera settings are on Home.
        root.addView(titleRow,new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));
        titleRow.setVisibility(View.GONE);

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
        taskChip.setVisibility(View.GONE);

        output=new TextView(this);
        output.setGravity(Gravity.CENTER);
        output.setTextColor(Color.WHITE);
        output.setTextSize(13);
        output.setMaxLines(2);
        output.setPadding(dp(10),dp(6),dp(10),dp(6));
        output.setBackground(roundRect(0xDB163758,13));
        output.setText("正在连接C110…");
        output.setContentDescription("C110预览状态；长按复制完整诊断");
        output.setOnLongClickListener(v->{copyDiagnostic();return true;});
        FrameLayout.LayoutParams status=new FrameLayout.LayoutParams(-1,-2,
            Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        status.leftMargin=dp(12);status.rightMargin=dp(12);status.bottomMargin=dp(99);
        root.addView(output,status);
        output.setVisibility(View.GONE);

        LinearLayout footer=new LinearLayout(this);
        bottomControls=footer;
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(7),dp(5),dp(7),dp(7));
        // No opaque bottom plate. Preserve the actual OEM 1001 shutter button.
        footer.setBackgroundColor(Color.TRANSPARENT);
        photoButton=footerButton("◎  高清拍照",this::captureOriginalPhoto);
        photoButton.setContentDescription("单次C110高清拍照，云端只处理当前变焦区域");
        footer.addView(photoButton,new LinearLayout.LayoutParams(dp(200),dp(63)));
        root.addView(footer,new FrameLayout.LayoutParams(-1,dp(77),Gravity.BOTTOM));

        // Compact manual slider. At 1x, all cloud paths remain unchanged.
        zoomControls=new LinearLayout(this);
        zoomControls.setGravity(Gravity.CENTER_VERTICAL);
        zoomControls.setPadding(dp(5),dp(2),dp(5),dp(2));
        // Keep the thumb, track and 1.0x label directly on the camera preview.
        zoomControls.setBackgroundColor(Color.TRANSPARENT);
        zoomValue=new TextView(this);
        zoomValue.setGravity(Gravity.CENTER);
        zoomValue.setTextColor(Color.WHITE);
        zoomValue.setTextSize(15);
        zoomValue.setText("1.0×");
        zoomValue.setContentDescription("当前OCR取景放大倍率1倍");
        zoomSeek=new SeekBar(this);
        zoomSeek.setMax(30);
        zoomSeek.setProgress(0);
        zoomSeek.setContentDescription("手动放大，1到4倍；放大后的区域才发送给OCR");
        zoomSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
                if(user)setZoom(1f+progress/10f);
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        zoomControls.addView(zoomSeek,new LinearLayout.LayoutParams(0,dp(48),1f));
        zoomControls.addView(zoomValue,new LinearLayout.LayoutParams(dp(56),dp(47)));
        FrameLayout.LayoutParams zoomParams=new FrameLayout.LayoutParams(-1,dp(53),Gravity.BOTTOM);
        zoomParams.leftMargin=dp(8);zoomParams.rightMargin=dp(8);
        zoomParams.bottomMargin=dp(78);
        root.addView(zoomControls,zoomParams);

        fullscreenPhoto=zoomSmallButton("高清拍照",this::captureOriginalPhoto);
        fullscreenPhoto.setTextSize(13);
        fullscreenPhoto.setContentDescription("全屏时按一次C110真实快门，按当前缩放区域识别OCR");
        FrameLayout.LayoutParams fullPhotoParams=new FrameLayout.LayoutParams(dp(120),dp(48),
            Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        fullPhotoParams.bottomMargin=dp(8);
        root.addView(fullscreenPhoto,fullPhotoParams);
        fullscreenPhoto.setVisibility(View.GONE);
        setContentView(root);
        // Reflow the SAME VLC view on portrait fullscreen; never rebuild RTSP/VLC.
        root.addOnLayoutChangeListener((v,left,top,right,bottom,oldL,oldT,oldR,oldB)->{
            if(fullscreen)applyVideoViewport();
        });
        say("C110正在初始化预览；滑动放大后OCR只读对应区域；点击画面取帧，长按状态复制诊断");
        // Proven OEM start sequence remains unchanged below; the request waits for Wi-Fi first.
        main.post(this::connectAndBeginWhenReady);
    }


    private int dp(int d){return Math.round(d*getResources().getDisplayMetrics().density);}

    private Button zoomSmallButton(String label,Runnable click){
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(21);
        button.setTextColor(Color.WHITE);
        button.setPadding(0,0,0,0);
        button.setBackground(roundRect(0xFF235477,12));
        button.setOnClickListener(v->click.run());
        return button;
    }

    private void setZoom(float wanted){
        if(photoBusy||frameReader.isBusy())return;
        float next=Math.round(C110ZoomGeometry.clamp(wanted)*10f)/10f;
        if(Math.abs(zoom-next)<.045f)return;
        zoom=next;
        zoomSeek.setProgress(Math.round((zoom-1f)*10f));
        zoomValue.setText(String.format(Locale.CHINA,"%.1f×",zoom));
        zoomValue.setContentDescription(String.format(Locale.CHINA,
            "当前放大%.1f倍，云端仅识别当前对应区域",zoom));
        applyZoomPreview();
    }

    private void applyZoomPreview(){
        if(video==null)return;
        // Android scales the existing VLCVideoLayout, NOT the decoder/RTSP stream.
        // PixelCopy reads the underlying unscaled Surface, so C110FrameReader
        // applies the EXACT same center fraction before cloud upload.
        FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)video.getLayoutParams();
        float coverScale=1f;
        if(fullscreen&&cameraRoot!=null&&layout.width>1&&layout.height>1){
            int width=cameraRoot.getWidth()-cameraRoot.getPaddingLeft()-
                      cameraRoot.getPaddingRight();
            int height=cameraRoot.getHeight()-cameraRoot.getPaddingTop()-
                       cameraRoot.getPaddingBottom();
            if(width>1&&height>1)
                coverScale=Math.max(width/(float)layout.width,height/(float)layout.height);
        }
        video.setPivotX(video.getWidth()/2f);
        video.setPivotY(video.getHeight()/2f);
        video.setScaleX(zoom*coverScale);
        video.setScaleY(zoom*coverScale);
    }

    /** Width/height of the visible portrait viewport, excluding system insets. */
    private float c110ViewportAspect(){
        if(cameraRoot==null)return 9f/16f;
        int width=cameraRoot.getWidth()-cameraRoot.getPaddingLeft()-
                  cameraRoot.getPaddingRight();
        int height=cameraRoot.getHeight()-cameraRoot.getPaddingTop()-
                   cameraRoot.getPaddingBottom();
        return width>1&&height>1?width/(float)height:9f/16f;
    }

    /**
     * Native 16:9 VLCVideoLayout is enlarged behind the clipped portrait root.
     * The decoder aspect ratio stays unchanged; the viewport crops left/right.
     * This changes layout only, never the existing MediaPlayer or stream.
     */
    private void applyVideoViewport(){
        if(cameraRoot==null||video==null)return;
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)video.getLayoutParams();
        int width=cameraRoot.getWidth()-cameraRoot.getPaddingLeft()-
                  cameraRoot.getPaddingRight();
        int height=cameraRoot.getHeight()-cameraRoot.getPaddingTop()-
                   cameraRoot.getPaddingBottom();
        if(width<2||height<2)return;
        // Cap the underlying Surface around the proven 2K RTSP size. Only
        // Android's display transform grows it to fill the portrait viewport.
        // Avoid a 4K+ Surface allocation/PixelCopy on tall high-DPI phones.
        int neededW=Math.max(width,Math.round(height*(16f/9f)));
        int targetW=fullscreen?Math.min(1920,neededW):-1;
        int targetH=fullscreen?Math.max(2,Math.round(targetW*9f/16f)):-1;
        if(lp.width!=targetW||lp.height!=targetH||lp.gravity!=Gravity.CENTER){
            lp.width=targetW;
            lp.height=targetH;
            lp.gravity=Gravity.CENTER;
            video.setLayoutParams(lp);
        }
        video.post(this::applyZoomPreview);
    }

    private void applyFullscreenFlags(){
        if(cameraRoot!=null)
            cameraRoot.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void setFullscreen(boolean requested){
        if(fullscreen==requested)return;
        // Portrait screen only. SJCAM-style cover, no rotation or VLC restart.
        fullscreen=requested;
        topControls.setVisibility(View.GONE);
        bottomControls.setVisibility(View.VISIBLE);
        taskChip.setVisibility(View.GONE);
        output.setVisibility(View.GONE);
        fullscreenPhoto.setVisibility(View.GONE);
        fullscreenButton.setText(fullscreen?"◫":"⛶");
        fullscreenButton.setContentDescription(fullscreen?
            "退出竖屏铺满取景，恢复完整画面":"竖屏铺满取景，等比例裁切左右");
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)zoomControls.getLayoutParams();
        lp.bottomMargin=dp(78);
        zoomControls.setLayoutParams(lp);
        applyFullscreenFlags();
        applyVideoViewport();
    }

    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        // No forced landscape, no Wi-Fi/decoder restart, maintain the same surface.
        if(video!=null)video.post(this::applyVideoViewport);
        applyFullscreenFlags();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus&&fullscreen)applyFullscreenFlags();
    }

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
                @Override public void edit(TaskCatalog.Task task){returnToMain("tasks");}
                @Override public void openReading(){returnToMain("reading");}
                @Override public void removed(TaskCatalog.Task task){
                    say("任务列表已更新："+task.name);
                }
            });
    }

    private void showC110Settings(){
        // No A/B experiments, deletion prompts or nested menus.
        // Preserve the existing proven C110 hardware controls.
        deviceControls.showSettings();
    }

    private void confirmSingleFileDelete(){
        if(pendingDeletion==null){
            say("无可删除对象：先在本页完成一次高清拍照及完整JPEG下载");
            return;
        }
        if(photoBusy||frameReader.isBusy()||!started){
            say("请等识别完成且实时预览恢复后，再执行单张删除实验");
            return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("确认删除本次拍摄的单张原图？")
            .setMessage("该操作不可撤销。只允许删除刚才由本软件拍摄、已完整下载并验证的那一张JPEG；先核对目录和相机4003能力，再发送一次原厂4003，不会批量删除、格式化或自动重试。\n\n建议先用不重要的测试照片验证。")
            .setPositiveButton("确认，仅删除这一张",(dialog,which)->deleteOneConfirmedPhoto())
            .setNegativeButton("取消",null).show();
    }

    private void deleteOneConfirmedPhoto(){
        if(pendingDeletion==null||photoBusy||frameReader.isBusy()||!started)return;
        C110WifiCamera.DeletionCandidate candidate=pendingDeletion;
        pendingDeletion=null; // consume the one-use authorization even on uncertain response
        photoBusy=true;
        final int ticket=++photoGeneration;
        stop(false); // prevent RTSP/SD file-system activity during the one-time test
        photoButton.setEnabled(false);
        beginButton.setEnabled(false);
        say("单张删除实验：检查原图、型号和固件能力（仅一次4003）…");
        photoJob=io.submit(()->{
            try{
                C110WifiCamera.DeleteResult result=
                    photoCamera.deleteDownloadedOriginal(this,candidate);
                main.post(()->{
                    if(ticket!=photoGeneration||!pageVisible||isFinishing())return;
                    say("4003单张删除实验：HTTP="+result.httpCode+
                        " Status="+result.ackStatus+
                        " 文件消失="+(result.fileGone?"是":"尚未确认")+
                        " 耗时="+result.elapsedMs+"ms（不含OCR）");
                    finishDeletionTest(ticket);
                });
            }catch(Exception error){
                String type=error.getClass().getSimpleName();
                String message=error.getMessage()==null?"未给出原因":error.getMessage();
                main.post(()->{
                    if(ticket!=photoGeneration||!pageVisible||isFinishing())return;
                    say("删除实验已停止："+type+" "+message+
                        "；不会自动重复发送删除指令");
                    finishDeletionTest(ticket);
                });
            }
        });
    }

    private void finishDeletionTest(int ticket){
        if(ticket!=photoGeneration||!pageVisible)return;
        photoJob=null;
        photoBusy=false;
        photoButton.setEnabled(true);
        beginButton.setEnabled(true);
        connectAndBeginWhenReady(); // restored OEM RTSP pipeline, no extra shot
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
        pendingDeletion=null; // the previous photo can no longer be deleted by mistake
        final int ticket=++photoGeneration;
        final float zoomAtShutter=zoom;
        final boolean portraitCoverAtShutter=fullscreen;
        final float viewportAspectAtShutter=c110ViewportAspect();
        final long at=SystemClock.elapsedRealtime();
        // Stop and release the SAME proven video player before 3001/par=0.
        // No Activity finish, no MainActivity re-entry, no delayed callback race.
        stop(false);
        beginButton.setEnabled(false);
        photoButton.setEnabled(false);
        fullscreenPhoto.setEnabled(false);
        say("C110高清拍照：切换照片模式，执行一次真实快门…");
        photoJob=io.submit(()->{
            try{
                C110WifiCamera.Photo photo=photoCamera.shootAfterLivePreview(this);
                main.post(()->{
                    if(ticket!=photoGeneration||!pageVisible||isFinishing())return;
                    say("高清原图 "+photo.width+"×"+photo.height+
                        " 已下载，拍摄取图 "+photo.totalMs+"ms，快门命令 "+
                        photo.shutterMs+"ms");
                    finishPhotoFlow(ticket,photo,zoomAtShutter,
                                    portraitCoverAtShutter,viewportAspectAtShutter);
                });
            }catch(Exception ex){
                final String detail=ex.getMessage()==null?
                    ex.getClass().getSimpleName():ex.getMessage();
                photoCamera.resetPhotoMode();
                main.post(()->{
                    if(ticket!=photoGeneration||!pageVisible||isFinishing())return;
                    say("高清拍照未完成："+detail+"。不会自动重拍");
                    finishPhotoFlow(ticket,null,zoomAtShutter,
                                    portraitCoverAtShutter,viewportAspectAtShutter);
                });
            }
        });
    }

    private void finishPhotoFlow(int ticket,C110WifiCamera.Photo photo,float zoomAtShutter,
                                 boolean portraitCoverAtShutter,float viewportAspectAtShutter){
        if(ticket!=photoGeneration||!pageVisible)return;
        photoBusy=false;
        photoJob=null;
        photoButton.setEnabled(true);
        fullscreenPhoto.setEnabled(true);
        beginButton.setEnabled(true);
        pendingDeletion=photo==null?null:C110WifiCamera.DeletionCandidate.fromPhoto(photo);
        // Real OEM 1001 JPEG completed: play one phone-side metal ding.
        // Independent of C110's hardware mute command 9011, and never on failure.
        if(photo!=null&&captureDing!=null)captureDing.photoSucceeded();
        // 1x: send original bytes; >1x: decode only matching normalized JPEG ROI.
        if(photo!=null&&!frameReader.recognizeOriginalJpeg(
            photo.jpeg,photo.width,photo.height,zoomAtShutter,
            portraitCoverAtShutter,viewportAspectAtShutter))
            say("已拍得高清照片，但识别流程未开始");
        // User remains on C110 camera page. Resume OEM playback once only.
        say("正在恢复C110实时预览…");
        connectAndBeginWhenReady();
    }


    /**
     * Starting from a home Wi-Fi must not immediately execute 3012 on a missing
     * C110 network. The request may create a secondary local-only Wi-Fi with
     * NO default gateway; C110WifiBootstrap verifies 3012 in that case.
     */
    private void connectAndBeginWhenReady(){
        if(!pageVisible||photoBusy||busy||started)return;
        if(C110WifiBootstrap.connected(this)!=null){
            cancelCameraWait();
            begin(); // unchanged OEM 3012 -> 3016 -> 3001/1 -> 2015/1
            return;
        }
        if(waitingForCameraWifi)return;
        waitingForCameraWifi=true;
        cameraConnectStartedMs=SystemClock.elapsedRealtime();
        beginButton.setEnabled(true);
        say("正在申请连接C110，请允许Android系统的相机Wi-Fi连接提示");
        requestC110Network(false);
        cameraConnectPoll=new Runnable(){
            @Override public void run(){
                if(!pageVisible||!waitingForCameraWifi||photoBusy)return;
                if(C110WifiBootstrap.connected(C110CameraActivity.this)!=null){
                    cancelCameraWait();
                    say("C110 Wi-Fi已验证，正在开始实时预览");
                    begin();
                    return;
                }
                if(SystemClock.elapsedRealtime()-cameraConnectStartedMs>=35000L){
                    cancelCameraWait();
                    say("C110连接超时；可以在本页重新请求系统连接");
                    beginButton.setEnabled(true);
                    new android.app.AlertDialog.Builder(C110CameraActivity.this)
                        .setTitle("C110 自动连接未成功")
                        .setMessage("若Android不支持同时连接家用Wi-Fi和相机热点，可先点“重新请求”；仍失败时可直接打开系统Wi-Fi快捷面板选择C110，不必翻找系统设置。")
                        .setPositiveButton("重新请求",(dialog,which)->{
                            requestC110Network(true);
                            connectAndBeginWhenReady();
                        })
                        .setNeutralButton("Wi-Fi快捷面板",(dialog,which)->{
                            try{
                                startActivity(new Intent(android.provider.Settings.Panel.ACTION_WIFI));
                            }catch(RuntimeException error){
                                say("Wi-Fi快捷面板无法打开："+error.getClass().getSimpleName());
                            }
                        })
                        .setNegativeButton("关闭",null).show();
                    return;
                }
                main.postDelayed(this,550L);
            }
        };
        main.postDelayed(cameraConnectPoll,550L);
    }

    private void requestC110Network(boolean explicitRetry){
        // Keep the MainActivity WifiNetworkSpecifier lease alive while this
        // independent VLC Activity is on top. Never create competing requests.
        if(C110WifiBootstrap.requestFromCameraPage(this,explicitRetry))return;
        if(isolatedWifiBootstrap==null)
            isolatedWifiBootstrap=new C110WifiBootstrap(this,this::say);
        // Direct launch from Home has no MainActivity lease. Ask for the
        // exact OEM C110 hotspot immediately even on a fresh installation,
        // rather than waiting 35 seconds without ever opening the picker.
        isolatedWifiBootstrap.requestForCameraMode();
    }

    private void cancelCameraWait(){
        waitingForCameraWifi=false;
        cameraConnectStartedMs=0;
        if(cameraConnectPoll!=null){
            main.removeCallbacks(cameraConnectPoll);
            cameraConnectPoll=null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,
        String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        boolean granted=grantResults.length>0&&
            grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
        if(requestCode==C110WifiBootstrap.SCAN_PERMISSION_REQUEST){
            if(isolatedWifiBootstrap!=null)
                isolatedWifiBootstrap.onScanPermissionResult(this,granted);
            else C110WifiBootstrap.onScanPermissionFromCameraPage(this,granted);
        }else if(requestCode==C110WifiBootstrap.PERMISSION_REQUEST){
            if(isolatedWifiBootstrap!=null)
                isolatedWifiBootstrap.onPermissionResult(granted);
            else C110WifiBootstrap.onWifiPermissionFromCameraPage(this,granted);
        }
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
        Network trusted=C110WifiBootstrap.connected(this);
        if(trusted!=null)return trusted;
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
            options.add("--network-caching=150");
            options.add("--live-caching=150");
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
            media.addOption(":network-caching=150");
            media.addOption(":live-caching=150");
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
    private final Runnable hideStatus=()->{if(output!=null)output.setVisibility(View.GONE);};
    private void say(String text){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            main.post(()->say(text));return;
        }
        log.append(text).append('\n');
        if(log.length()>4500)log.delete(0,log.length()-3000);
        if(output!=null){
            output.setText(text);
            output.setVisibility(View.VISIBLE);
            main.removeCallbacks(hideStatus);
            main.postDelayed(hideStatus,2300L);
        }
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
    @Override protected void onResume(){
        super.onResume();
        // Same preference synchronization as Home quick shot; no player restart.
        if(frameReader!=null)frameReader.syncSpeechSettings();
        pageVisible=true;
        if(fullscreen)applyFullscreenFlags();
        if(!photoBusy&&!started&&!busy)main.post(this::connectAndBeginWhenReady);
    }
    @Override protected void onPause(){
        pageVisible=false;
        cancelCameraWait();
        photoGeneration++;
        photoBusy=false;
        if(photoJob!=null){photoJob.cancel(true);photoJob=null;}
        stop(false);
        super.onPause();
    }
    @Override protected void onDestroy(){
        cancelCameraWait();
        stop(false);
        if(isolatedWifiBootstrap!=null){
            isolatedWifiBootstrap.shutdown();isolatedWifiBootstrap=null;
        }
        main.removeCallbacks(hideStatus);
        if(captureDing!=null){captureDing.shutdown();captureDing=null;}
        frameReader.shutdown();deviceControls.shutdown();io.shutdownNow();
        super.onDestroy();
    }
}
