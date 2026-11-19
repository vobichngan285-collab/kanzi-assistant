package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * C110+ photo-size/OCR/upload A/B experiment.
 *
 * The camera resolution is changed ONLY inside the OEM SJCAM Zone app.
 * This Activity sends no 1002 and uses exactly one proven physical 1001
 * shutter through C110WifiCamera per explicit tap, and one existing
 * CloudVisionClient recognition with the original SD JPEG.
 *
 * The reference text, returned text, and image live in RAM only. The
 * diagnostic export contains NUMBERS only, not image/text/API credentials.
 */
public final class C110ResolutionLabActivity extends Activity {
    private static final String[] LABELS={
        "20M · 5888×3312",
        "10M · 4320×2432",
        "8M · 3840×2160（4K）",
        "3M · 2560×1440（2K）"
    };
    private static final int[] WIDTHS={5888,4320,3840,2560};
    private static final int[] HEIGHTS={3312,2432,2160,1440};
    private static final int MAX_TRIALS=32;
    private final C110WifiCamera camera=new C110WifiCamera();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Trial> trials=new ArrayList<>();
    private Spinner labelChoice;
    private EditText knownReference;
    private TextView state,logView,lastTextView;
    private Button shootButton;
    private Future<?> running;
    private boolean busy,closed;
    private int generation;
    private String lastOcr="";
    private byte[] lastUploadedJpeg;
    private String latestError="";

    private static final class Trial {
        int slot,width,height,jpegBytes,promptTokens,completionTokens;
        int referenceChars,recognizedChars,editDistance;
        long modeMs,shutterMs,discoverMs,downloadMs,photoMs;
        long uploadMs,cloudMs,totalMs,firstTextMs;
        double accuracy;
        boolean correctDimensions,byteIdentical;
    }

    @Override public void onCreate(Bundle stateBundle){
        super.onCreate(stateBundle);
        ScrollView scrolling=new ScrollView(this);
        scrolling.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(13),dp(14),dp(19));
        root.setBackgroundColor(0xFF0B1D32);
        scrolling.addView(root);
        TextView heading=text("C110+ 照片 / OCR 综合实验",23,Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);
        root.addView(text(
            "先在原厂SJCAM Zone里手动选照片尺寸，返回后在下方标记档位。"
            +"本实验绝不更改相机分辨率；只真实拍一张、上传一次、对比一次。",
            15,0xFFD2E4F8));

        labelChoice=new Spinner(this);
        ArrayAdapter<String> choices=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,LABELS);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        labelChoice.setAdapter(choices);
        labelChoice.setSelection(0);
        root.addView(text("本次在原厂App选择的档位（仅用于标记）",16,Color.WHITE));
        root.addView(labelChoice,new LinearLayout.LayoutParams(-1,dp(55)));

        root.addView(text("正确原文（可粘贴；留空则只统计速度，不计算准确率）",16,Color.WHITE));
        knownReference=new EditText(this);
        knownReference.setTextColor(Color.WHITE);
        knownReference.setHintTextColor(0xFF8CA8C0);
        knownReference.setBackgroundColor(0xFF18344E);
        knownReference.setPadding(dp(12),dp(10),dp(12),dp(10));
        knownReference.setTextSize(16);
        knownReference.setHint("请粘贴测试纸上全部可见文字。每档拍同一张测试纸。");
        knownReference.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        knownReference.setMinLines(3);
        knownReference.setMaxLines(7);
        root.addView(knownReference,new LinearLayout.LayoutParams(-1,-2));

        shootButton=button("拍一张原图 → OCR → 自动统计");
        shootButton.setOnClickListener(v->shootOne());
        root.addView(shootButton);

        state=text("未开始；等待原厂App设定相机照片档位",16,0xFFFFE5A6);
        root.addView(state);
        root.addView(buttonWithAction("查看刚才实际送给AI的JPEG",this::previewUploadedJpeg));
        root.addView(buttonWithAction("查看最近OCR原文",this::showLastOcr));
        root.addView(buttonWithAction("复制数值诊断（供数据分析）",this::copyReport));

        logView=text("尚无实验记录。",13,0xFFD9E5F1);
        logView.setTextIsSelectable(true);
        root.addView(logView);
        lastTextView=text("",13,0xFFC2D9ED);
        root.addView(lastTextView);
        setContentView(scrolling);
        refresh();
    }

    private int dp(int n){
        return Math.round(n*getResources().getDisplayMetrics().density);
    }
    private TextView text(String value,int size,int color){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,dp(7),0,dp(7));
        return t;
    }
    private Button button(String name){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(name);b.setTextSize(16);
        b.setMinHeight(dp(51));
        return b;
    }
    private Button buttonWithAction(String name,Runnable action){
        Button b=button(name);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private void say(String message){
        state.setText(message);
        state.announceForAccessibility(message);
    }

    private void shootOne(){
        if(busy||closed)return;
        if(trials.size()>=MAX_TRIALS){
            say("为控制内存，最多32次实验；请先复制数值诊断");
            return;
        }
        final int slot=labelChoice.getSelectedItemPosition();
        if(slot<0||slot>=LABELS.length)return;
        final String known=knownReference.getText().toString();
        if(known.codePointCount(0,known.length())>2000){
            knownReference.setError("标准文字最多2000字符");
            return;
        }
        busy=true;shootButton.setEnabled(false);
        final int id=++generation;
        say("拍照中：一次真实1001快门，然后完整下载SD JPEG…");
        lastOcr="";lastUploadedJpeg=null;latestError="";
        final long started=SystemClock.elapsedRealtime();
        running=worker.submit(()->{
            try{
                if(Thread.currentThread().isInterrupted())return;
                // Existing 2026-11-18 field-verified mode0 -> ONE 1001 -> DCIM JPEG.
                C110WifiCamera.Photo photo=camera.shootAfterLivePreview(this);
                if(Thread.currentThread().isInterrupted())return;
                final long tookPhoto=SystemClock.elapsedRealtime()-started;
                if(photo.width<=0||photo.height<=0||photo.jpeg==null||photo.jpeg.length<256)
                    throw new IllegalStateException("原始JPEG不完整，不发云端");
                // DO NOT rescale, re-encode or copy. Exactly the same SD JPEG byte[].
                final byte[] submitted=photo.jpeg;
                final boolean identical=submitted==photo.jpeg;
                ui.post(()->{
                    if(closed||id!=generation)return;
                    say("相机原图 "+photo.width+"×"+photo.height+
                        " 已下载；正在使用手机蜂窝网络上传OCR…");
                });
                Network cellular=C110WifiCamera.requireCellular(this);
                String key=CloudKeyStore.load(this);
                if(key==null||key.trim().isEmpty())
                    throw new IllegalStateException("未设置千问百炼API Key；已拍照但未上传");
                // Fixed dedicated OCR model + identical prompt for every size.
                TaskCatalog.Task ocr=TaskCatalog.defaults()[0];
                CloudVisionClient.Result result=CloudVisionClient.recognize(
                    submitted,key,ocr,OcrModelCatalog.DEDICATED,null,
                    OcrSpeedExperiment.A,cellular);
                if(Thread.currentThread().isInterrupted())return;
                C110ResolutionScore.Result comparison=
                    C110ResolutionScore.compare(known,result.text);
                Trial trial=new Trial();
                trial.slot=slot;
                trial.width=photo.width;trial.height=photo.height;
                trial.jpegBytes=submitted.length;
                trial.correctDimensions=photo.width==WIDTHS[slot]&&
                    photo.height==HEIGHTS[slot];
                trial.byteIdentical=identical;
                trial.modeMs=photo.modeMs;
                trial.shutterMs=photo.shutterMs;
                trial.discoverMs=photo.discoverMs;
                trial.downloadMs=photo.downloadMs;
                trial.photoMs=photo.totalMs;
                trial.uploadMs=result.uploadMs; // connection + request write, not pure radio
                trial.cloudMs=result.networkMs;
                trial.firstTextMs=result.firstTextMs;
                trial.totalMs=SystemClock.elapsedRealtime()-started;
                trial.promptTokens=result.promptTokens;
                trial.completionTokens=result.completionTokens;
                trial.referenceChars=comparison.referenceChars;
                trial.recognizedChars=comparison.recognizedChars;
                trial.editDistance=comparison.edits;
                trial.accuracy=comparison.accuracyPercent;
                final String response=result.text;
                ui.post(()->{
                    if(closed||id!=generation)return;
                    if(trials.size()>=MAX_TRIALS)return;
                    trials.add(trial);
                    lastOcr=response==null?"":response;
                    lastUploadedJpeg=submitted;
                    say("第"+trials.size()+"张完成："+photo.width+"×"+photo.height+
                        "；总耗时"+trial.totalMs+"ms"+
                        (Double.isNaN(trial.accuracy)?"；未填写标准原文":
                            "；字符准确率"+percent(trial.accuracy)));
                    refresh();
                });
            }catch(Exception ex){
                String reason=ex.getMessage()==null?
                    ex.getClass().getSimpleName():ex.getMessage();
                ui.post(()->{
                    if(closed||id!=generation)return;
                    latestError=ex.getClass().getSimpleName();
                    say("本次未完成："+reason+
                        "（相机若已拍照不会自动重拍，云端不会自动重试）");
                    refresh();
                });
            }finally{
                ui.post(()->{
                    if(closed||id!=generation)return;
                    busy=false;running=null;shootButton.setEnabled(true);
                });
            }
        });
    }

    private static String percent(double p){
        return String.format(Locale.ROOT,"%.1f%%",p);
    }
    private String report(){
        StringBuilder b=new StringBuilder();
        b.append("Blue Star C110+ 分辨率/取图/云OCR A-B数值诊断\n")
            .append("模型=qwen-vl-ocr；每次真实1001快门+一份原始JPEG+一次云端请求；")
            .append("相机照片档位只在原厂App修改\n")
            .append("准确率=100×max(0,1-字符编辑距离/标准原文长度)；")
            .append("NFKC小写并忽略空白/标点；不代表语义评价\n")
            .append("upload_ms=建立连接+写入HTTP请求时间，不能单独视作无线传输时间；")
            .append("cloud_ms=整个云端请求耗时\n")
            .append("image_bytes=发送前相机原始JPEG长度；")
            .append("提交内容是未经重编码的同一byte[]；")
            .append("无法从客户端确认服务器内部缩放分辨率\n")
            .append("注意：标记档位不等于真实分辨率，以measured_px和matches_label为准\n")
            .append("试次=").append(trials.size()).append("\n\n");
        for(int i=0;i<trials.size();i++){
            Trial t=trials.get(i);
            b.append("#").append(i+1).append(" 选项=").append(LABELS[t.slot])
                .append(" 实测=").append(t.width).append("x").append(t.height)
                .append(" match=").append(t.correctDimensions?"yes":"NO")
                .append(" upload_equals_camera_original=").append(t.byteIdentical?"yes":"NO")
                .append("\nJPEG字节=").append(t.jpegBytes)
                .append(" 估计Base64字节=").append(((long)t.jpegBytes+2)/3*4)
                .append(" 视觉总像素=").append((long)t.width*t.height)
                .append("\nmode_ms=").append(t.modeMs)
                .append(" shutter_ms=").append(t.shutterMs)
                .append(" discover_ms=").append(t.discoverMs)
                .append(" jpeg_download_ms=").append(t.downloadMs)
                .append(" camera_photo_ms=").append(t.photoMs)
                .append("\nrequest_write_ms=").append(t.uploadMs)
                .append(" cloud_ms=").append(t.cloudMs)
                .append(" first_text_ms=").append(t.firstTextMs)
                .append(" end_to_end_ms=").append(t.totalMs)
                .append(" prompt_tokens=").append(t.promptTokens)
                .append(" completion_tokens=").append(t.completionTokens)
                .append("\nreference_chars=").append(t.referenceChars)
                .append(" recognized_chars=").append(t.recognizedChars)
                .append(" character_edits=").append(t.referenceChars==0?"unknown":
                    Integer.toString(t.editDistance))
                .append(" accuracy=").append(Double.isNaN(t.accuracy)?
                    "unknown":percent(t.accuracy)).append("\n\n");
        }
        if(!latestError.isEmpty())
            b.append("最新未完成阶段原因类型：").append(latestError).append("\n");
        return b.toString();
    }
    private void refresh(){
        if(logView!=null)logView.setText(report());
        if(lastTextView!=null)lastTextView.setText(trials.isEmpty()?
            "注意：一次只拍一张，原图保留在相机SD卡；本实验不清理。":
            "最近OCR结果长度="+(lastOcr==null?0:lastOcr.length())+
            "。点「查看最近OCR原文」查看内容；诊断不会包含识别文本。");
    }

    private void showLastOcr(){
        if(lastOcr==null||lastOcr.trim().isEmpty()){
            say("还没有OCR结果");return;
        }
        ScrollView sc=new ScrollView(this);
        TextView all=text(lastOcr,17,Color.BLACK);
        all.setPadding(dp(14),dp(14),dp(14),dp(14));
        all.setTextIsSelectable(true);
        sc.addView(all);
        new AlertDialog.Builder(this).setTitle("最近OCR原文（只存运行内存）")
            .setView(sc)
            .setPositiveButton("复制OCR文字",(d,w)->{
                ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText("OCR原文",lastOcr));
            })
            .setNegativeButton("关闭",null).show();
    }

    private void previewUploadedJpeg(){
        if(lastUploadedJpeg==null||lastUploadedJpeg.length==0){
            say("暂无已提交给AI的图像");return;
        }
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(lastUploadedJpeg,0,lastUploadedJpeg.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0){
            say("无法预览这张JPEG");return;
        }
        BitmapFactory.Options scaled=new BitmapFactory.Options();
        scaled.inSampleSize=Math.max(1,Math.max(
            (bounds.outWidth+1279)/1280,(bounds.outHeight+1279)/1280));
        final Bitmap bitmap=BitmapFactory.decodeByteArray(
            lastUploadedJpeg,0,lastUploadedJpeg.length,scaled);
        if(bitmap==null){say("无法预览这张JPEG");return;}
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.BLACK);
        image.setAdjustViewBounds(true);
        image.setImageBitmap(bitmap);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("本次实际上传JPEG："+bounds.outWidth+"×"+bounds.outHeight)
            .setMessage("屏幕预览为节省内存会缩小显示，不改变实际已上传的JPEG。")
            .setView(image)
            .setPositiveButton("关闭",null).create();
        dialog.setOnDismissListener(d->{
            image.setImageDrawable(null);bitmap.recycle();
        });
        dialog.show();
    }

    private void copyReport(){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard==null){say("剪贴板不可用");return;}
        clipboard.setPrimaryClip(ClipData.newPlainText("C110+ OCR实验数值诊断",report()));
        Toast.makeText(this,"数值诊断已复制，无照片与文字内容",Toast.LENGTH_LONG).show();
    }

    @Override protected void onPause(){
        generation++;
        if(running!=null)running.cancel(true);
        running=null;busy=false;
        if(shootButton!=null)shootButton.setEnabled(true);
        super.onPause();
    }
    @Override protected void onDestroy(){
        closed=true;generation++;
        if(running!=null)running.cancel(true);
        worker.shutdownNow();
        lastUploadedJpeg=null;
        lastOcr="";
        trials.clear();
        super.onDestroy();
    }
}
