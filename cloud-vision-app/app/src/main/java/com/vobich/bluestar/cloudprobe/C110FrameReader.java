package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One tap = one fresh PixelCopy frame from the proven C110 VLC Surface.
 * It does not call the shutter, restart RTSP, cache stale frames or write JPEG files.
 * Reuses CloudVisionClient/OCR model/task/filter, speaking via the local TTS engine.
 */
final class C110FrameReader {
    private static final String PREFS="kanzi_settings";
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Consumer<String> updates;
    private final AtomicInteger request=new AtomicInteger();
    private TextToSpeech tts;
    private boolean ttsReady,closed,busy;
    private long lastTapMs;
    private Future<?> taskFuture;
    private String lastText="";
    private boolean outputReady;

    C110FrameReader(Activity activity,Consumer<String> updates){
        this.activity=activity;
        this.updates=updates;
        SharedPreferences p=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String engine=p.getString("app_tts_engine_package","");
        TextToSpeech.OnInitListener ready=status->main.post(()->{
            if(closed)return;
            ttsReady=status==TextToSpeech.SUCCESS;
            if(ttsReady&&tts!=null){
                tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                tts.setSpeechRate(p.getBoolean("local_tts_follow_default",true)?1f:
                    Math.max(.5f,Math.min(3f,p.getFloat("local_tts_rate",1f))));
            }
        });
        tts=engine==null||engine.isEmpty()?
            new TextToSpeech(activity,ready):new TextToSpeech(activity,ready,engine);
    }

    void setVideoOutputReady(boolean ready){outputReady=ready;}

    boolean isBusy(){return busy;}

    void tap(View videoRoot){
        if(closed||busy)return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastTapMs<850)return;
        if(!outputReady){report("C110画面尚未显示，请确认视频预览");return;}
        SurfaceView video=findSurface(videoRoot);
        if(video==null||!video.isAttachedToWindow()||
            video.getWidth()<2||video.getHeight()<2){
            report("C110尚未准备好视频画面");return;
        }
        Surface surface=video.getHolder().getSurface();
        if(surface==null||!surface.isValid()){report("视频表面尚未就绪");return;}
        int width=video.getWidth(),height=video.getHeight();
        if((long)width*height>9_000_000L){report("视频画幅过大，已拒绝拷贝");return;}
        final int id=request.incrementAndGet();
        lastTapMs=now;
        busy=true;
        if(tts!=null)tts.stop();
        report("正在取C110当前画面…");
        try{
            Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            PixelCopy.request(video,image,status->{
                if(closed||id!=request.get()){
                    image.recycle();return;
                }
                if(status!=PixelCopy.SUCCESS){
                    image.recycle();busy=false;
                    report("实时取帧失败（PixelCopy="+status+"），没有使用旧照片");
                    return;
                }
                // PixelCopy returns a current frame; any further network wait is off UI.
                report("已获取实时画面，正在识别…");
                taskFuture=io.submit(()->analyze(id,image,now));
            },main);
        }catch(RuntimeException ex){
            busy=false;report("取帧失败："+ex.getClass().getSimpleName());
        }
    }

    private void analyze(int id,Bitmap fresh,long started){
        byte[] jpeg;
        try{
            try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                if(!fresh.compress(Bitmap.CompressFormat.JPEG,90,bytes))
                    throw new IllegalStateException("JPEG编码失败");
                jpeg=bytes.toByteArray();
            }
        }catch(Exception ex){
            if(id==request.get())main.post(()->{
                if(id!=request.get()||closed)return;
                busy=false;report("实时画面编码失败");
            });
            return;
        }finally{fresh.recycle();}
        if(jpeg.length<256){
            main.post(()->{if(id==request.get()&&!closed){
                busy=false;report("取得的当前帧无效");}});
            return;
        }
        try{
            // The camera AP is local-only; all cloud traffic must explicitly use cellular.
            Network cellular=C110WifiCamera.requireCellular(activity);
            String key=CloudKeyStore.load(activity);
            if(key.isEmpty())throw new IllegalStateException("请先在三横杠设置中填写百炼 API Key");
            TaskCatalog.Task selected=null;
            String idTask=TaskCatalog.selectedId(activity);
            for(TaskCatalog.Task task:TaskCatalog.all(activity))
                if(task.id.equals(idTask)){selected=task;break;}
            if(selected==null)selected=TaskCatalog.defaults()[0];
            final TaskCatalog.Task task=selected;
            SharedPreferences prefs=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            boolean ocr=TaskCatalog.OCR.equals(task.engine);
            String model=ocr?
                OcrModelCatalog.normalize(prefs.getString("cloud_ocr_model_v1",OcrModelCatalog.DEFAULT)):
                VisionModelCatalog.normalize(prefs.getString("cloud_vision_model",VisionModelCatalog.DEFAULT));
            String speed=ocr?OcrSpeedExperiment.normalize(
                prefs.getString(OcrSpeedExperiment.PREF,OcrSpeedExperiment.DEFAULT)):
                OcrSpeedExperiment.A;
            AtomicInteger spokenParts=new AtomicInteger();
            CloudVisionClient.Result result=CloudVisionClient.recognize(jpeg,key,task,model,
                part->main.post(()->{
                    if(closed||id!=request.get()||!ttsReady||tts==null)return;
                    String piece=filtered(part,task);
                    if(piece.isEmpty())return;
                    int count=spokenParts.getAndIncrement();
                    tts.speak(piece,count==0?TextToSpeech.QUEUE_FLUSH:
                        TextToSpeech.QUEUE_ADD,null,"c110-"+id+"-"+count);
                }),speed,cellular);
            main.post(()->{
                if(closed||id!=request.get())return;
                busy=false;
                lastText=result.text==null?"":result.text;
                report("已识别："+task.name+"，取帧到识别 "+(SystemClock.elapsedRealtime()-started)+"ms");
                if(spokenParts.get()==0||!OcrSpeedExperiment.earlySpeak(speed))
                    speak(filtered(lastText,task));
            });
        }catch(OcrResponseGate.NoReadableTextException noText){
            main.post(()->{
                if(closed||id!=request.get())return;
                busy=false;report("当前画面未识别到文字");
                speak("当前画面未识别到文字，请调整角度后再试。");
            });
        }catch(Exception ex){
            String message=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
            main.post(()->{
                if(closed||id!=request.get())return;
                busy=false;report("取帧已完成，但识别失败："+message);
            });
        }
    }

    private String filtered(String source,TaskCatalog.Task task){
        SharedPreferences p=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(source==null)return "";
        if(!p.getBoolean("speech_filter_all_tasks",false)&&
            !TaskCatalog.OCR.equals(task.engine))return source.trim();
        return SpeechFilter.apply(source,p.getBoolean("speech_skip_latin",false),
            p.getBoolean("speech_skip_digits",false),
            p.getBoolean("speech_skip_symbols",false)).trim();
    }

    private void speak(String text){
        if(ttsReady&&tts!=null&&text!=null&&!text.isEmpty())
            tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"c110-final-"+request.get());
    }

    void replay(){
        if(lastText.isEmpty()){report("还没有可重读的画面文字");return;}
        TaskCatalog.Task task=null;
        for(TaskCatalog.Task t:TaskCatalog.all(activity))
            if(t.id.equals(TaskCatalog.selectedId(activity))){task=t;break;}
        if(task==null)task=TaskCatalog.defaults()[0];
        speak(filtered(lastText,task));
    }

    private void report(String message){
        main.post(()->{if(!closed)updates.accept(message);});
    }

    private static SurfaceView findSurface(View node){
        if(node instanceof SurfaceView)return (SurfaceView)node;
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){
                SurfaceView found=findSurface(group.getChildAt(i));
                if(found!=null)return found;
            }
        }
        return null;
    }

    void stop(){
        request.incrementAndGet();
        outputReady=false;
        busy=false;
        if(taskFuture!=null){taskFuture.cancel(true);taskFuture=null;}
        if(tts!=null)tts.stop();
    }

    void shutdown(){
        closed=true;
        stop();
        io.shutdownNow();
        if(tts!=null){tts.shutdown();tts=null;}
    }
}
