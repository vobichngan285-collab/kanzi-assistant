package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Camera-only one-shot C110+ photo workflow. No phone/UVC/LibVLC initialized.
 * One tap or one volume gesture = exactly one OEM 1001 command; no auto retry.
 */
public final class C110QuickPhotoActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final C110WifiCamera camera=new C110WifiCamera();
    private C110WifiBootstrap wifi;
    private C110FrameReader reader;
    private C110VolumeShortcut shortcut;
    private TextView status;
    private Button photo;
    private boolean active,shooting;
    private int generation;
    private long wifiStartedAt;
    private Runnable waiting;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22),dp(36),dp(22),dp(20));
        layout.setBackgroundColor(0xFF0B1D32);
        TextView heading=new TextView(this);
        heading.setText("C110+ 快捷拍照");heading.setTextSize(26);
        heading.setTextColor(Color.WHITE);
        layout.addView(heading);
        status=new TextView(this);
        status.setText("正在准备连接相机…");
        status.setTextColor(0xFFF5D687);status.setTextSize(18);
        LinearLayout.LayoutParams message=new LinearLayout.LayoutParams(-1,0,1f);
        layout.addView(status,message);
        photo=new Button(this);
        photo.setAllCaps(false);photo.setText("再拍一张高清原图");
        photo.setOnClickListener(v->startPhoto());
        layout.addView(photo);
        Button back=new Button(this);back.setText("返回主页");
        back.setOnClickListener(v->finish());layout.addView(back);
        setContentView(layout);
        reader=new C110FrameReader(this,this::setStatus);
        wifi=new C110WifiBootstrap(this,this::setStatus);
        shortcut=new C110VolumeShortcut(this,this::startPhoto);
    }
    private int dp(int px){return Math.round(px*getResources().getDisplayMetrics().density);}
    private void setStatus(String value){
        main.post(()->{if(active&&status!=null)status.setText(value);});
    }
    @Override protected void onResume(){
        super.onResume();active=true;
        // The single initial request is only created on first launch.
        // Returning from the system Wi-Fi picker does not fire another shutter.
        if(generation==0)startPhoto();
        else if(shooting&&waiting!=null)main.postDelayed(waiting,120L);
    }
    private void startPhoto(){
        if(!active||shooting||reader==null||reader.isBusy())return;
        final int id=++generation;
        shooting=true;photo.setEnabled(false);
        setStatus("已触发一次快捷拍照，正在检查C110+ Wi-Fi…");
        if(C110WifiBootstrap.connected(this)!=null){capture(id);return;}
        wifi.requestForCameraMode(); // may show the exact OEM Wi-Fi picker
        wifiStartedAt=SystemClock.elapsedRealtime();
        waiting=new Runnable(){
            @Override public void run(){
                if(!active||id!=generation||!shooting)return;
                if(C110WifiBootstrap.connected(C110QuickPhotoActivity.this)!=null){
                    waiting=null;capture(id);return;
                }
                if(SystemClock.elapsedRealtime()-wifiStartedAt>=35000L){
                    waiting=null;shooting=false;photo.setEnabled(true);
                    setStatus("C110+ Wi-Fi 连接超时；没有执行快门。请检查连接后再点拍照");
                    return;
                }
                main.postDelayed(this,500L);
            }
        };
        main.postDelayed(waiting,500L);
    }
    private void capture(int id){
        if(!active||id!=generation||!shooting)return;
        setStatus("C110+ 已连接，正在执行一次 1001 高清快门…");
        worker.execute(()->{
            try{
                C110WifiCamera.Photo result=camera.shootAfterLivePreview(this);
                main.post(()->{
                    if(!active||id!=generation)return;
                    shooting=false;photo.setEnabled(true);
                    setStatus("已取回高清原图 "+result.width+"×"+result.height+
                        "（"+result.totalMs+"ms）。正在按主页模型朗读…");
                    if(!reader.recognizeOriginalJpeg(result.jpeg,result.width,result.height))
                        setStatus("原图已拍摄，前一次识别尚未结束");
                });
            }catch(Exception ex){
                String reason=ex.getMessage()==null?
                    ex.getClass().getSimpleName():ex.getMessage();
                main.post(()->{
                    if(!active||id!=generation)return;
                    shooting=false;photo.setEnabled(true);
                    setStatus("本次拍照失败："+reason+"；不会自动重拍");
                });
            }
        });
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,
                                                   int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        boolean granted=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;
        if(wifi!=null){
            if(requestCode==C110WifiBootstrap.SCAN_PERMISSION_REQUEST)
                wifi.onScanPermissionResult(this,granted);
            else if(requestCode==C110WifiBootstrap.PERMISSION_REQUEST)
                wifi.onPermissionResult(granted);
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(shortcut!=null&&shortcut.dispatch(event))return true;
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onPause(){
        active=false;
        // Android's Wi-Fi consent screen may pause the Activity. Keep the
        // one pending user shutter token; resume its connection poll later.
        if(waiting!=null)main.removeCallbacks(waiting);
        if(shortcut!=null)shortcut.cancel();
        // No second 1001 can start after activity pauses; in-flight HTTP may
        // finish without any callback UI or cloud request.
        super.onPause();
    }
    @Override protected void onDestroy(){
        ++generation;shooting=false;
        if(waiting!=null){main.removeCallbacks(waiting);waiting=null;}
        if(wifi!=null)wifi.shutdown();
        if(reader!=null)reader.shutdown();
        worker.shutdownNow();
        super.onDestroy();
    }
}
