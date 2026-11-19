package com.vobich.bluestar.c110photosize;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PhotoSizeActivity extends Activity {
    private final C110NativePhotoCamera camera=new C110NativePhotoCamera();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final SimpleDateFormat fmt=new SimpleDateFormat("HH:mm:ss.SSS",Locale.CHINA);
    private final StringBuilder diagnostics=new StringBuilder();
    private Button probeButton,referenceButton,set2kButton,verifyButton,copyButton;
    private TextView headline,details;
    private Future<?> runningJob;
    private volatile boolean visible,closed;
    private boolean busy,photoSupported,sizeSettingSupported,baseline20mConfirmed;
    private int firstWidth=-1,firstHeight=-1;
    private boolean lastSetAcknowledged;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFF0D1728);
        layout.setPadding(dp(12),dp(16),dp(12),dp(10));
        TextView title=new TextView(this);
        title.setText("C110+ 原生照片尺寸实验");
        title.setTextColor(Color.WHITE);title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView instructions=new TextView(this);
        instructions.setText(
            "独立实验：只验证内存卡新照片的真实像素，不开视频、不上传云端、不删除照片。\n"+
            "先在系统 Wi-Fi 连接 C110+；①检查→②拍出20M基线→③候选2K设置→④再拍照。");
        instructions.setTextColor(0xFFD1E2F3);instructions.setTextSize(15);
        instructions.setPadding(0,dp(10),0,dp(10));
        layout.addView(instructions);

        probeButton=button("① 只读检查 C110+ / 1002");
        probeButton.setOnClickListener(v->runProbe());
        layout.addView(probeButton,params());

        referenceButton=button("② 拍摄20M原图基线");
        referenceButton.setOnClickListener(v->runOnePhoto(true));
        layout.addView(referenceButton,params());

        set2kButton=button("③ 设置C110+ 2K · 2560×1440");
        set2kButton.setOnClickListener(v->confirmSizeSetting());
        layout.addView(set2kButton,params());

        verifyButton=button("④ 再拍一张，验证SD卡JPEG尺寸");
        verifyButton.setOnClickListener(v->runOnePhoto(false));
        layout.addView(verifyButton,params());

        copyButton=button("复制全部诊断");
        copyButton.setOnClickListener(v->copyDiagnostics());
        layout.addView(copyButton,params());

        headline=new TextView(this);
        headline.setTextColor(0xFFFFFFFF);headline.setTextSize(17);
        headline.setText("未连接。待只读检查。");
        headline.setPadding(0,dp(7),0,dp(7));
        layout.addView(headline);

        ScrollView scroll=new ScrollView(this);
        details=new TextView(this);
        details.setTextColor(0xFFC4D6E7);details.setTextSize(13);
        details.setTextIsSelectable(true);
        scroll.addView(details);
        layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(layout);
        update();
        record("实验启动：仅本地内存记录；不含照片/文件路径/密钥");
        record("C110+原厂配置580-C110+ 1.0.7/1.0.8/1.1.2：1002第8项(索引7)=3M 2560×1440");
        record("只有从本机SD卡读到5888×3312基线，才允许向C110+发单次2K候选设置");
        record("更改相机设置可能保持到下次开机；本实验不会猜测原档值或自动恢复，需用原厂App设置回去");
    }

    private int dp(int px){return Math.round(px*getResources().getDisplayMetrics().density);}
    private Button button(String label){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(label);b.setTextSize(16);
        b.setMinHeight(dp(53));b.setContentDescription(label);
        return b;
    }
    private LinearLayout.LayoutParams params(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
        p.bottomMargin=dp(6);return p;
    }
    private void update(){
        if(probeButton==null)return;
        probeButton.setEnabled(!busy);
        referenceButton.setEnabled(!busy&&photoSupported);
        set2kButton.setEnabled(!busy&&sizeSettingSupported&&baseline20mConfirmed);
        verifyButton.setEnabled(!busy&&photoSupported);
        copyButton.setEnabled(true);
    }
    private void runProbe(){
        run("只读检查",()->{
            C110NativePhotoCamera.ProbeResult result=camera.probe(this);
            main.post(()->{
                if(!visible||closed)return;
                photoSupported=result.c110&&result.cmd1001;
                sizeSettingSupported=photoSupported&&result.cmd1002;
                headline.setText(sizeSettingSupported?
                    "已识别C110系列相机且声明1002；先拍20M基线":
                    "未确认原厂照片尺寸1002，禁止设置");
                record("3012 C110系列="+result.c110+
                    "；3002 快门1001="+result.cmd1001+
                    "；拍照尺寸1002="+result.cmd1002);
                record("3014 只读结果：Status="+result.settingsStatus+
                    " 字节数="+result.settingsBytes+
                    " 包含1002标签="+result.settingsHint1002+
                    "（不推断当前实际拍照档位）");
                update();
            });
        });
    }
    private void confirmSizeSetting(){
        if(busy||!sizeSettingSupported||!baseline20mConfirmed)return;
        new AlertDialog.Builder(this)
            .setTitle("确认改变 C110+ 内存卡拍照尺寸？")
            .setMessage("这是原厂 C110+ 配置表中的 3M / 2560×1440 候选：1002/par=7。\n\n"+
                "本次已重新读到5888×3312原图基线，但仍未验证固件档位索引。此操作会真的改变相机设置，并可能在重启后继续生效。\n\n"+
                "本程序不自动恢复原档、不删原照片、不批量操作。测试后如果需要恢复以前的分辨率，请使用原厂 SJCAM Zone 重新设置。\n\n"+
                "确认只发送一次设置指令？")
            .setPositiveButton("确认：尝试2K设置",(dialog,which)->run2kSetting())
            .setNegativeButton("取消",null).show();
    }
    private void run2kSetting(){
        run("尝试原厂2K候选",()->{
            C110NativePhotoCamera.SetResult outcome=camera.select2kCandidate(this);
            main.post(()->{
                if(!visible||closed)return;
                lastSetAcknowledged=outcome.httpOk&&outcome.status==0;
                record("1002/par=7 单次回执 HTTP200="+outcome.httpOk+
                    " Status="+outcome.status+" command_ms="+outcome.elapsedMs);
                if(lastSetAcknowledged){
                    headline.setText("相机接受了设置指令；请按④拍照核实新JPEG尺寸");
                    record("警告：Status=0只代表指令被接受，不代表已写成2K照片");
                }else{
                    headline.setText("固件未确认接受设置；仍可拍照记录实际尺寸");
                }
            });
        });
    }
    private void runOnePhoto(boolean reference){
        if(!photoSupported)return;
        run(reference?"基线JPEG拍照":"设置后JPEG尺寸验证",()->{
            C110NativePhotoCamera.PhotoResult shot=reference?camera.takeBaselineOriginal(this):camera.takeOneOriginal(this);
            main.post(()->{
                if(!visible||closed)return;
                String shape=shot.width+"×"+shot.height;
                if(reference){
                    firstWidth=shot.width;firstHeight=shot.height;
                    baseline20mConfirmed=firstWidth==5888&&firstHeight==3312;
                    if(!baseline20mConfirmed)
                        record("拒绝设置：当前照片非20M 5888×3312，无法从原图核实C110+配置映射");
                    update();
                    headline.setText("原档SD原图："+shape);
                    record("BASELINE_SD_JPEG width="+shot.width+" height="+shot.height+
                        " bytes="+shot.byteCount+" before_files="+shot.baselineCount+
                        " shutter_ms="+shot.shutterMs+" discover_ms="+shot.discoverMs+
                        " download_ms="+shot.downloadMs+" total_ms="+shot.totalMs);
                }else{
                    String conclusion=shot.exactly2k()?"与原厂配置2K尺寸一致":
                        "与目标2560×1440不一致";
                    if(shot.exactly2k()&&firstWidth==shot.width&&firstHeight==shot.height)
                        conclusion="新JPEG为2560×1440，但基线已经是2K，无法证明本次指令使它改变";
                    headline.setText("新SD原图："+shape+"；"+conclusion);
                    record("AFTER_SD_JPEG width="+shot.width+" height="+shot.height+
                        " bytes="+shot.byteCount+" before_files="+shot.baselineCount+
                        " shutter_ms="+shot.shutterMs+" discover_ms="+shot.discoverMs+
                        " download_ms="+shot.downloadMs+" total_ms="+shot.totalMs+
                        " set_ack="+lastSetAcknowledged+" target_2k="+shot.exactly2k());
                    if(firstWidth>0)
                        record("COMPARE baseline="+firstWidth+"×"+firstHeight+
                            " after="+shape+" camera_jpeg_not_scaled=1");
                }
                record("JPEG从相机/ DCIM 新文件经HTTP完整下载并校验，只保留尺寸和字节数，不保存到手机");
            });
        });
    }
    private void run(String name,Job task){
        if(busy||!visible||closed)return;
        busy=true;
        headline.setText(name+"：正在执行…");
        record("BEGIN "+name);
        update();
        runningJob=worker.submit(()->{
            try{
                if(!visible||closed||Thread.currentThread().isInterrupted())return;
                task.perform();
            }catch(Exception ex){
                String reason=ex.getMessage()==null?
                    ex.getClass().getSimpleName():ex.getMessage();
                main.post(()->{
                    if(!visible||closed)return;
                    headline.setText(name+"失败："+reason);
                    record("ERROR "+name+" type="+ex.getClass().getSimpleName()+
                        " message="+reason);
                });
            }finally{
                main.post(()->{
                    if(closed)return;
                    busy=false;runningJob=null;update();
                });
            }
        });
    }
    private interface Job{void perform()throws Exception;}
    private void record(String line){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            main.post(()->record(line));return;
        }
        diagnostics.append(fmt.format(new Date())).append(" ").append(line).append('\n');
        if(diagnostics.length()>22000)diagnostics.delete(0,diagnostics.length()-18000);
        if(details!=null)details.setText(diagnostics.toString());
    }
    private void copyDiagnostics(){
        ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText(
            "C110 native JPEG size test",diagnostics.toString()));
        record("复制诊断完成：不含JPEG图像、文件名、Wi-Fi密码或API Key");
    }
    @Override protected void onResume(){super.onResume();visible=true;}
    @Override protected void onPause(){
        visible=false;
        if(runningJob!=null)runningJob.cancel(true);
        super.onPause();
    }
    @Override protected void onDestroy(){
        closed=true;
        if(runningJob!=null)runningJob.cancel(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
