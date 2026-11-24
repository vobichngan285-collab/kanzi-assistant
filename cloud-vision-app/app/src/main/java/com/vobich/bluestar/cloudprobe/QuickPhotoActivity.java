package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Two explicit real-still buttons and the UNMODIFIED four-column task desktop.
 * No camera, preview or cloud process runs when simply opening this page. */
public final class QuickPhotoActivity extends Activity {
    private static final String PREFS="kanzi_settings";
    private static final int BG=0xFF091B32,FG=0xFFF3F8FF,MUTED=0xFFB9DCF8;
    private static final int REQUEST_PHONE=4401;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService imageWorker=Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private TextureView phoneSurface;
    private InternalCameraController phone;
    private C110FrameReader phoneReader;
    private C110QuickShotController c110;
    private TextView taskTitle,status;
    private boolean active,phoneTaking,closed;
    private int phoneGeneration,phoneRotation;
    private long phoneStartedMs;

    @Override protected void attachBaseContext(Context base){
        super.attachBaseContext(BlueStarFontScale.wrap(base));
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){
        TextView view=new TextView(this);
        view.setText(text);view.setTextSize(size);view.setTextColor(color);
        return view;
    }
    private GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        phoneReader=new C110FrameReader(this,this::showStatus);
        c110=new C110QuickShotController(this,this::showStatus);
        root=new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(0,insets.getSystemWindowInsetTop(),0,
                insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        // The hidden preview SurfaceTexture is ATTACHED only. Camera2 is not
        // started before the user explicitly taps the phone button.
        phoneSurface=new TextureView(this);
        phoneSurface.setAlpha(0.01f);
        FrameLayout.LayoutParams surfaceParams=new FrameLayout.LayoutParams(dp(4),dp(4),
            Gravity.BOTTOM|Gravity.LEFT);
        root.addView(phoneSurface,surfaceParams);
        phone=new InternalCameraController(this,phoneSurface,new InternalCameraController.Listener(){
            @Override public boolean shouldReceiveFrame(){return false;}
            @Override public void onFrame(byte[] nv21,int w,int h){}
            @Override public void onOpened(int rawW,int rawH,int sensor,int rotation){
                phoneRotation=rotation;
            }
            @Override public void onPreviewStarted(){
                main.post(()->{
                    if(closed||!active||!phoneTaking)return;
                    final int token=phoneGeneration;
                    phone.requestStillPhoto(new InternalCameraController.StillListener(){
                        @Override public void onJpeg(byte[] jpeg,int w,int h,long at){
                            main.post(()->onPhoneJpeg(token,jpeg,w,h));
                        }
                        @Override public void onFailure(String reason){
                            main.post(()->failPhone(token,reason));
                        }
                    });
                });
            }
            @Override public void onError(String reason){
                main.post(()->failPhone(phoneGeneration,reason));
            }
        });
        draw();
    }
    private void draw(){
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(12),dp(16),dp(20));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(72)));
        TextView back=label("‹",36,FG);
        back.setGravity(Gravity.CENTER);
        header.addView(back,new LinearLayout.LayoutParams(dp(55),-1));
        back.setOnClickListener(v->finish());
        back.setContentDescription("返回首页");
        TextView title=label("快速拍照",27,FG);
        title.setGravity(Gravity.CENTER);title.setTypeface(null,1);
        header.addView(title,new LinearLayout.LayoutParams(0,-1,1f));
        header.addView(new View(this),new LinearLayout.LayoutParams(dp(55),1));

        LinearLayout taskRow=new LinearLayout(this);
        taskRow.setGravity(Gravity.CENTER_VERTICAL);
        taskRow.setPadding(dp(14),dp(12),dp(10),dp(12));
        taskRow.setBackground(shape(0xFF162F62,20));
        LinearLayout taskTexts=new LinearLayout(this);
        taskTexts.setOrientation(LinearLayout.VERTICAL);
        taskRow.addView(taskTexts,new LinearLayout.LayoutParams(0,-2,1f));
        TextView small=label("当前任务",16,MUTED);
        taskTexts.addView(small);
        taskTitle=label("",23,FG);
        taskTitle.setTypeface(null,1);
        taskTexts.addView(taskTitle);
        TextView select=label("选择任务 ›",19,FG);
        select.setGravity(Gravity.CENTER);
        select.setPadding(dp(12),dp(12),dp(12),dp(12));
        select.setBackground(shape(0xFF126CEE,15));
        taskRow.addView(select,new LinearLayout.LayoutParams(-2,dp(58)));
        select.setOnClickListener(v->{
            if(phoneTaking){showStatus("正在获取高清照片");return;}
            releaseSpeech();
            startActivity(new Intent(this,TaskDesktopActivity.class));
        });
        LinearLayout.LayoutParams taskLp=new LinearLayout.LayoutParams(-1,dp(104));
        taskLp.topMargin=dp(16);content.addView(taskRow,taskLp);

        LinearLayout row=new LinearLayout(this);
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,dp(212));
        rowLp.topMargin=dp(22);
        content.addView(row,rowLp);
        TextView phoneButton=label("▯\n\n手机高清拍照",22,Color.WHITE);
        phoneButton.setGravity(Gravity.CENTER);phoneButton.setTypeface(null,1);
        phoneButton.setBackground(shape(0xFF1269ED,22));
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1f);
        left.rightMargin=dp(8);
        row.addView(phoneButton,left);
        phoneButton.setOnClickListener(v->takePhone());
        TextView c110Button=label("▣\n\n相机高清拍照",22,Color.WHITE);
        c110Button.setGravity(Gravity.CENTER);c110Button.setTypeface(null,1);
        c110Button.setBackground(shape(0xFF079B82,22));
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1f);
        right.leftMargin=dp(8);
        row.addView(c110Button,right);
        c110Button.setOnClickListener(v->takeC110());
        status=label("等待拍照",17,FG);
        status.setPadding(dp(16),dp(18),dp(16),dp(18));
        status.setBackground(shape(0xFF152E5A,18));
        LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(-1,-2);
        statusLp.topMargin=dp(20);
        content.addView(status,statusLp);
        refreshTask();
    }
    private void refreshTask(){
        String id=TaskCatalog.selectedId(this);
        String name="朗读文字";
        for(TaskCatalog.Task task:TaskCatalog.all(this)){
            if(task.id.equals(id)){name=task.name;break;}
        }
        if(taskTitle!=null)taskTitle.setText(name);
    }
    private void showStatus(String value){
        main.post(()->{if(!closed&&status!=null)status.setText(value);});
    }
    private void releaseSpeech(){
        if(phoneReader!=null)phoneReader.releaseSpeechForOtherScreen();
        if(c110!=null)c110.beforeOpenAnotherScreen();
    }
    private void takeC110(){
        if(!active||phoneTaking){showStatus("手机正在拍照");return;}
        if(phoneReader!=null)phoneReader.cancelRecognition();
        if(c110!=null)c110.trigger();
    }
    private void takePhone(){
        if(!active||closed||phoneTaking)return;
        if(!phone.hasPermission()){
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_PHONE);
            return;
        }
        // New source replaces the previous cloud task only after shutter tap.
        if(c110!=null)c110.beforeOpenAnotherScreen();
        if(phoneReader!=null)phoneReader.cancelRecognition();
        phoneTaking=true;phoneStartedMs=android.os.SystemClock.elapsedRealtime();
        final int token=++phoneGeneration;
        showStatus("正在拍摄手机高清照片…");
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        boolean front=p.getBoolean("phone_camera_front",false);
        phone.start(front,true); // Always REAL JPEG, not current preview-mode.
        main.postDelayed(()->{
            if(token!=phoneGeneration||!phoneTaking||closed)return;
            failPhone(token,"手机高清拍照启动超时");
        },8000L);
    }
    private void onPhoneJpeg(int token,byte[] jpeg,int w,int h){
        if(token!=phoneGeneration||closed||!phoneTaking)return;
        phoneTaking=false;
        phone.stop(); // Camera2 shuts down immediately after 1 exposure.
        showStatus("已拍照 · 正在准备识别…");
        final int rotation=phoneRotation;
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        final int savedRotation=p.getInt("preview_rotation",0);
        final boolean mirror=p.getBoolean("preview_mirror",false);
        final boolean flip=p.getBoolean("preview_flip",false);
        final int quality=p.getInt("phone_hd_upload_mode_trial_v1",
            PhoneStillUploadPolicy.P1440);
        final int processedRotation=(rotation+savedRotation+360)%360;
        final int request=token;
        imageWorker.execute(()->{
            try{
                PhoneStillJpeg.Result result=PhoneStillJpeg.prepare(
                    jpeg,processedRotation,mirror,flip,
                    PhoneStillUploadPolicy.normalize(quality));
                main.post(()->{
                    if(closed||!active||request!=phoneGeneration)return;
                    if(!phoneReader.recognizePreparedPhoneJpeg(
                        result.jpeg,result.width,result.height))
                        showStatus("识别任务仍在处理，请再试一次");
                });
            }catch(Exception error){
                main.post(()->failPhone(request,
                    "手机高清照片处理失败："+error.getClass().getSimpleName()));
            }
        });
    }
    private void failPhone(int token,String reason){
        if(token!=phoneGeneration||closed)return;
        phoneTaking=false;
        if(phone!=null)phone.stop();
        showStatus(reason);
    }
    @Override protected void onResume(){
        super.onResume();
        active=true;
        if(c110!=null)c110.onResume();
        refreshTask();
    }
    @Override protected void onPause(){
        active=false;
        if(c110!=null)c110.onPause();
        if(phoneTaking){++phoneGeneration;phoneTaking=false;phone.stop();}
        releaseSpeech();
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        boolean granted=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;
        if(code==REQUEST_PHONE){
            if(granted)takePhone();else showStatus("没有手机摄像头权限");
        }else if(code==C110WifiBootstrap.SCAN_PERMISSION_REQUEST){
            if(!C110WifiBootstrap.onScanPermissionFromCameraPage(this,granted))
                showStatus("请返回相机设置连接Wi-Fi");
        }else if(code==C110WifiBootstrap.PERMISSION_REQUEST){
            if(!C110WifiBootstrap.onWifiPermissionFromCameraPage(this,granted))
                showStatus("请返回相机设置连接Wi-Fi");
        }
    }
    @Override protected void onDestroy(){
        closed=true;++phoneGeneration;phoneTaking=false;
        if(phone!=null)phone.stop();
        if(phoneReader!=null)phoneReader.shutdown();
        if(c110!=null)c110.shutdown();
        imageWorker.shutdownNow();
        super.onDestroy();
    }
}
