package com.vobich.kanzi.craftprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate package. Single system-camera photo or gallery Bitmap is frozen,
 * then passed without any content modifications to both DET models. This is
 * repeated paired *still frame* testing, NOT live 30FPS camera benchmarking.
 * Nothing is uploaded; export is initiated only by the user.
 */
public final class DetCompareActivity extends Activity {
    private static final int PICK=501,SHOOT=502,SAVE=503;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private DetCompareEngine engine;
    private Bitmap photo;
    private Uri cameraUri;
    private TextView status;
    private ImageView smallImage,cnImage;
    private String photoLabel="未选择真实图片",lastJson="{}";
    private final List<JSONObject> records=new ArrayList<>();

    private void status(String value) {main.post(()->{if(status!=null)status.setText(value+"\n\n"+status.getText());});}
    private void button(LinearLayout root,String name,Runnable call) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(name);b.setTextSize(17);
        b.setOnClickListener(v->call.run());root.addView(b);
    }
    private void execute(String name,Task action) {
        if(!busy.compareAndSet(false,true)) {Toast.makeText(this,"正在处理上一操作",Toast.LENGTH_SHORT).show();return;}
        status(name+"：运行中…");
        jobs.execute(()->{try{action.run();}catch(Throwable t){status(name+"失败："+t.getClass().getSimpleName()+": "+t.getMessage());}
            finally{busy.set(false);}});
    }
    interface Task {void run()throws Exception;}
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroller=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(14*getResources().getDisplayMetrics().density);
        root.setPadding(pad,pad,pad,pad);scroller.addView(root);
        TextView heading=new TextView(this);
        heading.setText("看字助手 · 同一帧双检测器 A/B");
        heading.setTextSize(24);heading.setTextColor(Color.BLACK);root.addView(heading);
        TextView info=new TextView(this);info.setTextSize(15);
        info.setText("独立包，不覆盖看字助手。\nSmall DET vs ChineseOCR-Lite DET。"
            +"同一张已冻结的拍照/相册图片分别找文字并绘制原始区域；没有识字、联网、TTS。"
            +"\n测预处理、ONNX推理、后处理、P50/P95、温度、PSS。不同模型沿用各自原始输入分辨率。"
            +"\n注意：不是实时视频流；框后处理的旋转框膨胀为几何近似，不能冒充与Linux源码逐像素一致。");
        root.addView(info);
        button(root,"① 离线加载并SHA校验两套DET模型",()->execute("加载",()->{
            if(!OpenCVLoader.initLocal())throw new IllegalStateException("OpenCV原生库加载失败");
            if(engine==null)engine=new DetCompareEngine(this);
            engine.load();status("模型就绪：Small "+engine.small.loadMs+"ms；CN-Lite "+engine.chinese.loadMs+
               "ms；两套SHA均通过。模型只在本机运行。");
        }));
        button(root,"② 系统相机拍一张真实图片",this::takePicture);
        button(root,"③ 从相册选择同一张图片",this::choose);
        button(root,"④ 同帧单次对照并画框",()->execute("单次双模型划框",()->bench(1)));
        button(root,"⑤ 同帧12对交替运行（含预热）",()->execute("12对测温",()->bench(12)));
        button(root,"⑥ 导出匿名逐框坐标与耗时 JSON",this::exportJson);
        TextView a=new TextView(this);a.setText("Small 检测框（红色）");a.setTextSize(19);root.addView(a);
        smallImage=new ImageView(this);smallImage.setAdjustViewBounds(true);root.addView(smallImage);
        TextView c=new TextView(this);c.setText("ChineseOCR-Lite 检测框（绿色）");c.setTextSize(19);root.addView(c);
        cnImage=new ImageView(this);cnImage.setAdjustViewBounds(true);root.addView(cnImage);
        status=new TextView(this);status.setTextSize(16);status.setTextColor(Color.BLACK);
        status.setText("请先点①校验模型，拍摄同一张真实照片，点④/⑤。"
            +"\n推荐分别拍：中文门牌/菜单含相邻价格/超市价签/商品包装。"
            +"\n导出JSON只含坐标、时间、设备和热状态，不含照片、识别文字或路径。");
        status.setTextIsSelectable(true);root.addView(status);
        setContentView(scroller);
    }
    private void takePicture() {
        if(busy.get()){status("先完成当前推理");return;}
        try {
            File folder=new File(getCacheDir(),"det-camera");
            if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("无法建立相机缓存");
            File file=new File(folder,"same-frame-"+System.currentTimeMillis()+".jpg");
            cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
            i.setClipData(ClipData.newRawUri("same-frame DET",cameraUri));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if(i.resolveActivity(getPackageManager())==null)throw new IllegalStateException("无系统相机应用");
            startActivityForResult(i,SHOOT);
        } catch(Exception x){status("打开相机失败："+x.getMessage());}
    }
    private void choose() {
        if(busy.get()){status("先完成当前推理");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");startActivityForResult(i,PICK);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK)return;
        if(request==SAVE) {
            if(data==null||data.getData()==null)return;
            Uri selected=data.getData();
            execute("导出测量JSON",()->{
                try(OutputStream out=getContentResolver().openOutputStream(selected)){
                    if(out==null)throw new IllegalStateException("无法写入文件");
                    out.write(lastJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                status("已按用户操作导出本地匿名JSON；没有自动上传。");
            });return;
        }
        if(request==PICK||request==SHOOT) {
            Uri uri=request==SHOOT?cameraUri:(data==null?null:data.getData());
            if(uri==null){status("没有选中照片");return;}
            execute("解码同一原始帧",()->{
                Bitmap next=readImage(uri);
                Bitmap old=photo;photo=next;
                photoLabel=request==SHOOT?"系统相机照片":"相册真实图片";
                if(old!=null&&old!=next)old.recycle();
                main.post(()->{smallImage.setImageBitmap(null);cnImage.setImageBitmap(null);});
                status("同帧已冻结：已解码 "+next.getWidth()+"x"+next.getHeight()
                    +"；之后两套模型共享这份Bitmap，不分别拍照。");
            });
        }
    }
    private Bitmap readImage(Uri uri)throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException("无法读取原图尺寸");
        int sample=1;
        while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>2600)sample*=2;
        BitmapFactory.Options settings=new BitmapFactory.Options();settings.inSampleSize=sample;
        Bitmap decoded;
        try(InputStream in=getContentResolver().openInputStream(uri)){decoded=BitmapFactory.decodeStream(in,null,settings);}
        if(decoded==null)throw new IllegalArgumentException("无法读取像素");
        int rotation=0;
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            ExifInterface exif=new ExifInterface(in);
            int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            if(orientation==ExifInterface.ORIENTATION_ROTATE_90)rotation=90;
            else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)rotation=180;
            else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)rotation=270;
        }catch(Exception ignored){}
        if(rotation!=0) {
            Matrix m=new Matrix();m.postRotate(rotation);
            Bitmap rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),m,true);
            if(rotated!=decoded)decoded.recycle();
            decoded=rotated;
        }
        return decoded;
    }
    private int batteryC10() {
        Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return i==null?-1:i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1);
    }
    private int thermal() {
        if(Build.VERSION.SDK_INT<29)return -1;
        PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
        return p==null?-1:p.getCurrentThermalStatus();
    }
    private static double pct(List<Long> list,double q) {
        if(list.isEmpty())return -1;
        ArrayList<Long> ordered=new ArrayList<>(list);
        Collections.sort(ordered);
        return ordered.get((int)Math.round((ordered.size()-1)*q));
    }
    private JSONObject sampleJson(DetCompareEngine.Result r)throws Exception {
        JSONObject o=new JSONObject();
        o.put("model",r.model);o.put("inputW",r.modelWidth);o.put("inputH",r.modelHeight);
        o.put("probW",r.mapWidth);o.put("probH",r.mapHeight);
        o.put("preprocessMs",r.preprocessMs);o.put("forwardMs",r.forwardMs);
        o.put("postprocessMs",r.postprocessMs);o.put("totalMs",r.totalMs);
        o.put("pssKb",r.pssKb);o.put("boxesCount",r.boxes.size());
        JSONArray boxes=new JSONArray();
        for(DetCompareEngine.Detection b:r.boxes) {
            JSONObject one=new JSONObject();JSONArray q=new JSONArray();
            for(float v:b.quad)q.put(Math.round(v*10.0)/10.0);
            one.put("quad",q);one.put("score",Math.round(b.score*10000.0)/10000.0);
            boxes.put(one);
        }
        o.put("boxes",boxes);
        return o;
    }
    private void bench(int pairs)throws Exception {
        if(engine==null||engine.small.session==null||engine.chinese.session==null)
            throw new IllegalStateException("先点①加载并校验两个模型");
        Bitmap src=photo;
        if(src==null||src.isRecycled())throw new IllegalStateException("请先拍照或选取真实图");
        int beforeTemp=batteryC10(),beforeThermal=thermal();
        // 2 warmups: same frozen image, both models, never count as performance.
        if(pairs>1) {engine.run(src,engine.small);engine.run(src,engine.chinese);}
        ArrayList<DetCompareEngine.Result> smallResults=new ArrayList<>(),cnResults=new ArrayList<>();
        for(int i=0;i<pairs;i++) {
            // Alternate to reduce fixed-order temperature/cache bias.
            DetCompareEngine.Result s,c;
            if((i&1)==0) {s=engine.run(src,engine.small);c=engine.run(src,engine.chinese);}
            else {c=engine.run(src,engine.chinese);s=engine.run(src,engine.small);}
            smallResults.add(s);cnResults.add(c);
            if(pairs>1&&(i+1)%3==0)status("同帧对照："+(i+1)+"/"+pairs+" 对");
        }
        int afterTemp=batteryC10(),afterThermal=thermal();
        DetCompareEngine.Result s=smallResults.get(smallResults.size()-1);
        DetCompareEngine.Result c=cnResults.get(cnResults.size()-1);
        JSONObject entry=new JSONObject();
        entry.put("kind","same_frame_paired_det");
        entry.put("timestampMs",System.currentTimeMillis());
        entry.put("origin",photoLabel);
        entry.put("frameW",src.getWidth());entry.put("frameH",src.getHeight());
        entry.put("pairs",pairs);entry.put("warmupPerModel",pairs>1?1:0);
        entry.put("sameDecodedBitmapForBoth",true);entry.put("alternatedModelOrder",true);
        entry.put("isLiveCameraStream",false);entry.put("recognitionOrTtsIncluded",false);
        entry.put("batteryC10Before",beforeTemp);entry.put("batteryC10After",afterTemp);
        entry.put("thermalBefore",beforeThermal);entry.put("thermalAfter",afterThermal);
        entry.put("pssKbAfter",Debug.getPss());
        entry.put("Small",aggregate(smallResults));
        entry.put("ChineseOCRLite",aggregate(cnResults));
        records.add(entry);
        JSONObject root=new JSONObject();
        root.put("title","Kanzi standalone same-frozen-frame DET A/B");
        root.put("device",Build.MANUFACTURER+" "+Build.MODEL);
        root.put("androidApi",Build.VERSION.SDK_INT);
        root.put("offline",true);
        root.put("imageNotExported",true);
        root.put("smallSha256",DetCompareEngine.SMALL_SHA);
        root.put("chineseLiteSha256",DetCompareEngine.CNLITE_SHA);
        root.put("limitations","Still photo; not camera stream; native resolutions differ; rectangle unclip approximates PyClipper; not validated against full line GT on phone.");
        root.put("experiments",new JSONArray(records));
        lastJson=root.toString(2);
        Bitmap smallOverlay=overlay(src,s,Color.RED);
        Bitmap cnOverlay=overlay(src,c,Color.rgb(0,180,45));
        main.post(()->{
            smallImage.setImageBitmap(smallOverlay);
            cnImage.setImageBitmap(cnOverlay);
        });
        status(pairs+" 对固定同帧检测完成：\nSmall "+s.boxes.size()+"框；"
           +entry.getJSONObject("Small").getDouble("totalP50Ms")+"ms P50\n"
           +"CN-Lite "+c.boxes.size()+"框；"+entry.getJSONObject("ChineseOCRLite").getDouble("totalP50Ms")+"ms P50\n"
           +"电池温度(0.1℃) "+beforeTemp+"→"+afterTemp
           +"；热状态 "+beforeThermal+"→"+afterThermal
           +"；PSS "+Debug.getPss()+" KB。"
           +"\n两幅框是原模型DET后处理结果，不是Small识字。可点⑥导出逐框JSON。");
    }
    private JSONObject aggregate(List<DetCompareEngine.Result> vals)throws Exception {
        List<Long> pre=new ArrayList<>(),forward=new ArrayList<>(),post=new ArrayList<>(),all=new ArrayList<>();
        JSONArray raw=new JSONArray();
        for(DetCompareEngine.Result r:vals) {
            pre.add(r.preprocessMs);forward.add(r.forwardMs);
            post.add(r.postprocessMs);all.add(r.totalMs);
            raw.put(sampleJson(r));
        }
        JSONObject j=new JSONObject();
        j.put("preprocessP50Ms",pct(pre,.5));j.put("preprocessP95Ms",pct(pre,.95));
        j.put("forwardP50Ms",pct(forward,.5));j.put("forwardP95Ms",pct(forward,.95));
        j.put("postP50Ms",pct(post,.5));j.put("postP95Ms",pct(post,.95));
        j.put("totalP50Ms",pct(all,.5));j.put("totalP95Ms",pct(all,.95));
        j.put("rawSamples",raw);return j;
    }
    private static Bitmap overlay(Bitmap src,DetCompareEngine.Result r,int color) {
        int width=src.getWidth(),height=src.getHeight();
        double scale=Math.min(1.0,1000.0/Math.max(width,height));
        Bitmap thumb=Bitmap.createScaledBitmap(src,Math.max(1,(int)(width*scale)),
            Math.max(1,(int)(height*scale)),true).copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas=new Canvas(thumb);
        Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);pen.setColor(color);
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(2.6f);
        for(DetCompareEngine.Detection d:r.boxes) {
            android.graphics.Path p=new android.graphics.Path();
            p.moveTo((float)(d.quad[0]*scale),(float)(d.quad[1]*scale));
            for(int k=1;k<4;k++)p.lineTo((float)(d.quad[2*k]*scale),(float)(d.quad[2*k+1]*scale));
            p.close();canvas.drawPath(p,pen);
        }
        return thumb;
    }
    private void exportJson() {
        if(records.isEmpty()){status("先点④或⑤生成检测结果");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"Kanzi_SameFrame_DET_AB_"+System.currentTimeMillis()+".json");
        startActivityForResult(i,SAVE);
    }
    @Override protected void onDestroy() {
        jobs.shutdown();
        super.onDestroy();
        // No automatic job or external camera process after Activity closes.
    }
}
