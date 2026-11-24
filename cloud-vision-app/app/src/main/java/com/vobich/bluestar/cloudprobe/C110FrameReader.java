package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Network;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
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
    // Keep a stuck old HTTP response off the image-preparation queue.
    private final ExecutorService photoCloudIo=Executors.newFixedThreadPool(2);
    private final Object photoNetworkLock=new Object();
    private CloudVisionClient.Cancellation activePhotoCloud;
    private final Consumer<String> updates;
    private final AtomicInteger request=new AtomicInteger();
    private TextToSpeech tts;
    private boolean ttsReady,ttsInitFailed,closed,busy;
    private String activeTtsEngine;
    private float activeTtsRate=1f;
    private int ttsGeneration;
    // The cloud may return before a newly selected TTS engine has initialized.
    // Keep only the text of the current user-requested photo, not a cache.
    private final StringBuilder pendingSpeech=new StringBuilder();
    private int pendingSpeechRequest=-1;
    private long lastTapMs;
    private Future<?> taskFuture;
    private String lastText="";
    private boolean outputReady;
    interface PhotoMetricsListener {
        void onCaptured(int width,int height,long captureMs);
        void onUpload(int width,int height,int jpegBytes);
        void onRecognized(long networkMs);
    }
    private PhotoMetricsListener metricsListener;
    private Runnable frameCaptureFeedback;
    void setPhotoMetricsListener(PhotoMetricsListener listener){metricsListener=listener;}
    void setFrameCaptureFeedback(Runnable feedback){frameCaptureFeedback=feedback;}

    C110FrameReader(Activity activity,Consumer<String> updates){
        this.activity=activity;
        this.updates=updates;
        // The main Blue Star Home remains alive behind MainActivity Settings.
        // Do not start a SECOND TTS engine merely by opening Home: the
        // selected third-party engine (grtts) is initialized on an actual
        // user-requested OCR/video speech operation instead.
    }

    /** Release the Home speaker before navigating to a different Activity
     *  (Settings, phone/USB or C110 preview) that owns its own TTS instance.
     *  No camera/Wi-Fi/cloud state is changed by this method. */
    void releaseSpeechForOtherScreen(){
        if(closed)return;
        ++ttsGeneration; // ignore late OnInit from the former engine
        ttsReady=false;
        ttsInitFailed=false;
        activeTtsEngine=null;
        clearPendingSpeech();
        if(tts!=null){
            try{tts.stop();}catch(RuntimeException ignored){}
            try{tts.shutdown();}catch(RuntimeException ignored){}
            tts=null;
        }
    }

    /**
     * Home does not own MainActivity's TTS instance. Share its selected
     * settings instead: re-create this reader's TTS only when the actual
     * Android engine package changes; update the speech rate otherwise.
     * Called after returning from unified Settings and before each capture.
     */
    void syncSpeechSettings(){
        if(closed)return;
        SharedPreferences p=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String configured=p.getString("app_tts_engine_package","");
        final String engine=configured==null?"":configured;
        final float rate=p.getBoolean("local_tts_follow_default",true)?1f:
            Math.max(.5f,Math.min(3f,p.getFloat("local_tts_rate",1f)));
        if(activeTtsEngine!=null&&activeTtsEngine.equals(engine)&&tts!=null){
            if(Float.compare(activeTtsRate,rate)!=0){
                activeTtsRate=rate;
                if(ttsReady)try{tts.setSpeechRate(rate);}
                    catch(RuntimeException error){report("语速设置失败："+error.getClass().getSimpleName());}
            }
            return;
        }
        activeTtsEngine=engine;
        activeTtsRate=rate;
        final int generation=++ttsGeneration;
        ttsReady=false;ttsInitFailed=false;
        if(tts!=null){
            tts.stop();tts.shutdown();tts=null;
        }
        // Match MainActivity's working initialization: do NOT force
        // setLanguage(Locale.SIMPLIFIED_CHINESE) on custom engines such as grtts.
        TextToSpeech.OnInitListener ready=status->main.post(()->{
            if(closed||generation!=ttsGeneration)return;
            ttsReady=status==TextToSpeech.SUCCESS&&tts!=null;
            if(!ttsReady){
                ttsInitFailed=true;
                clearPendingSpeech();
                report("所选语音引擎初始化失败，请在语音设置里试听");
                if(tts!=null){tts.shutdown();tts=null;}
                return;
            }
            // Restore the v1.0.50–v1.0.57 proven C110 setup. v1.0.58
            // removed this Chinese-language setup without a device A/B test.
            // A custom engine package stays selected; only request zh-CN.
            try{
                int localeResult=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if(localeResult==TextToSpeech.LANG_NOT_SUPPORTED||
                   localeResult==TextToSpeech.LANG_MISSING_DATA)
                    report("当前语音引擎不支持简体中文，请在语音设置检查音色");
            }catch(RuntimeException error){
                report("语音语言设置失败："+error.getClass().getSimpleName());
            }
            try{tts.setSpeechRate(activeTtsRate);}
            catch(RuntimeException error){
                report("所选引擎无法设置语速："+error.getClass().getSimpleName());
            }
            // The third-party voice may not expose speed control.
            // Always flush this request's OCR speech when the engine is ready.
            flushPendingSpeech();
        });
        try{
            tts=engine.isEmpty()?new TextToSpeech(activity,ready):
                new TextToSpeech(activity,ready,engine);
        }catch(RuntimeException error){
            tts=null;ttsReady=false;ttsInitFailed=true;
            clearPendingSpeech();
            report("无法启动所选语音引擎："+error.getClass().getSimpleName());
        }
    }

    private void clearPendingSpeech(){
        pendingSpeech.setLength(0);
        pendingSpeechRequest=-1;
    }
    private void flushPendingSpeech(){
        if(!ttsReady||tts==null||pendingSpeech.length()==0)return;
        int id=pendingSpeechRequest;
        String content=pendingSpeech.toString();
        clearPendingSpeech();
        if(id==request.get())
            enqueueSpeech(content,TextToSpeech.QUEUE_FLUSH,"c110-delayed-"+id);
    }
    private void enqueueSpeech(String content,int queueMode,String utterance){
        if(content==null||content.isEmpty()||closed)return;
        if(ttsInitFailed){
            report("已识别文字，但所选语音引擎初始化失败，未能朗读");
            return;
        }
        if(tts==null||!ttsReady){
            int id=request.get();
            if(pendingSpeechRequest!=id){clearPendingSpeech();pendingSpeechRequest=id;}
            if(pendingSpeech.length()+content.length()<=100_000)
                pendingSpeech.append(content);
            else report("语音未就绪，待播报文字过长；没有继续缓存");
            return;
        }
        try{
            if(tts.speak(content,queueMode,null,utterance)!=TextToSpeech.SUCCESS)
                report("所选语音引擎未接受朗读，请在语音设置里试听");
        }catch(RuntimeException error){
            report("所选语音引擎朗读失败："+error.getClass().getSimpleName());
        }
    }

    void setVideoOutputReady(boolean ready){outputReady=ready;}

    boolean isBusy(){return busy;}

    void tap(View videoRoot){tap(videoRoot,1f);}

    void tap(View videoRoot,float selectedZoom){
        tap(videoRoot,selectedZoom,false,1f);
    }

    void tap(View videoRoot,float selectedZoom,boolean portraitCover,
             float viewportAspect){
        if(closed)return;
        final float zoomAtTap=C110ZoomGeometry.clamp(selectedZoom);
        long now=SystemClock.elapsedRealtime();
        if(now-lastTapMs<350)return;
        // PixelCopy/native capture must not reuse the previous OCR job.
        if(busy)cancelRecognition();
        syncSpeechSettings();
        if(!outputReady){report("C110画面尚未显示，请确认视频预览");return;}
        SurfaceView video=findSurface(videoRoot);
        if(video==null||!video.isAttachedToWindow()||
            video.getWidth()<2||video.getHeight()<2){
            report("C110尚未准备好视频画面");return;
        }
        Surface surface=video.getHolder().getSurface();
        if(surface==null||!surface.isValid()){report("视频表面尚未就绪");return;}
        final int surfaceW=video.getWidth(),surfaceH=video.getHeight();
        // Match the center-crop/zoom ALREADY visible on screen. Do not copy
        // a scaled full frame and crop it a second time.
        final C110ZoomGeometry.Region visible;
        try{
            visible=C110ZoomFrames.visiblePreviewRegion(surfaceW,surfaceH,
                zoomAtTap,portraitCover,viewportAspect);
        }catch(IllegalArgumentException invalid){
            report("C110预览裁剪参数无效，不发送错误区域");return;
        }
        // Keep the existing absolute memory safety cap, but apply it to
        // the FINAL visible ROI rather than an unneeded whole frame.
        if((long)visible.width*visible.height>9_000_000L){
            report("C110预览可见区域过大，已拒绝分配");return;
        }
        final Rect sourceRect=new Rect(visible.left,visible.top,
            visible.left+visible.width,visible.top+visible.height);
        if(sourceRect.isEmpty()||sourceRect.left<0||sourceRect.top<0||
            sourceRect.right>surfaceW||sourceRect.bottom>surfaceH){
            report("C110裁剪位置超出视频画面，已取消上传");return;
        }
        final int id=request.incrementAndGet();
        clearPendingSpeech();
        lastTapMs=now;
        busy=true;
        if(tts!=null)tts.stop();
        final RuntimeDiagnostics previewDiag=new RuntimeDiagnostics(activity);
        previewDiag.begin("c110_preview");
        previewDiag.event("frame","surface_width_px",surfaceW,"ok");
        previewDiag.event("frame","surface_height_px",surfaceH,"ok");
        previewDiag.event("frame","crop_left_px",sourceRect.left,"ok");
        previewDiag.event("frame","crop_top_px",sourceRect.top,"ok");
        previewDiag.event("frame","visible_width_px",visible.width,"ok");
        previewDiag.event("frame","visible_height_px",visible.height,"ok");
        report("正在截取C110当前可见画面…");
        try{
            // PixelCopy accepts a source rectangle in SurfaceView coordinates.
            // Destination matches it 1:1: NO 2MP downsample, rescale, or
            // Bitmap.createBitmap subcrop after the video frame is copied.
            Bitmap image=Bitmap.createBitmap(visible.width,visible.height,
                                             Bitmap.Config.ARGB_8888);
            PixelCopy.request(video,sourceRect,image,status->{
                if(closed||id!=request.get()){
                    image.recycle();return;
                }
                if(status!=PixelCopy.SUCCESS){
                    image.recycle();busy=false;
                    previewDiag.event("frame","pixel_copy_status",status,"failed");
                    report("实时取帧失败（PixelCopy="+status+"），没有使用旧照片");
                    return;
                }
                previewDiag.event("frame","pixel_copy_status",status,"ok");
                if(frameCaptureFeedback!=null)frameCaptureFeedback.run();
                report("已获取当前可见画面，正在识别…");
                taskFuture=io.submit(()->analyzeVisibleFrame(
                    id,image,now,previewDiag));
            },main);
        }catch(RuntimeException ex){
            busy=false;
            previewDiag.event("frame","pixel_copy_status",-1,"failed");
            report("取帧失败："+ex.getClass().getSimpleName());
        }
    }

    private void analyzeVisibleFrame(int id,Bitmap visible,long started,
                                     RuntimeDiagnostics previewDiag){
        byte[] jpeg;
        final long encodedAt=SystemClock.elapsedRealtime();
        final int jpegW=visible.getWidth(),jpegH=visible.getHeight();
        try{
            // Single high-quality JPEG encode from the EXACT captured ROI.
            // JPEG is intrinsically lossy; avoid any OTHER resizing or second
            // encoding before CloudVisionClient consumes these same bytes.
            try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                if(!visible.compress(Bitmap.CompressFormat.JPEG,100,bytes))
                    throw new IllegalStateException("JPEG编码失败");
                jpeg=bytes.toByteArray();
            }
            if(jpeg.length<256)throw new IllegalStateException("JPEG数据无效");
        }catch(Exception ex){
            if(id==request.get())main.post(()->{
                if(id!=request.get()||closed)return;
                busy=false;report("C110预览JPEG编码失败："+ex.getClass().getSimpleName());
            });
            return;
        }finally{
            visible.recycle();
        }
        if(closed||id!=request.get())return;
        previewDiag.event("jpeg","encode_ms",SystemClock.elapsedRealtime()-encodedAt,"ok");
        previewDiag.event("jpeg","width_px",jpegW,"ok");
        previewDiag.event("jpeg","height_px",jpegH,"ok");
        previewDiag.event("jpeg","bytes",jpeg.length,"ok");
        // Existing network/model/TTS path uses this exact JPEG byte[].
        recognizeJpeg(id,jpeg,started,null,previewDiag);
    }

    /**
     * Hand off the true C110 full-resolution JPEG without recompressing it or
     * leaving the camera screen. Only a user-tapped 1001 result reaches here.
     */
    boolean recognizeOriginalJpeg(byte[] jpeg,int width,int height){
        return recognizeOriginalJpeg(jpeg,width,height,1f);
    }

    boolean recognizeOriginalJpeg(byte[] jpeg,int width,int height,float selectedZoom){
        return recognizeOriginalJpeg(jpeg,width,height,selectedZoom,false,1f);
    }

    boolean recognizeOriginalJpeg(byte[] jpeg,int width,int height,float selectedZoom,
                                  boolean portraitCover,float viewportAspect){
        return recognizeOriginalJpeg(jpeg,width,height,selectedZoom,portraitCover,
                                    viewportAspect,null);
    }
    /** One explicit Home voice task overrides only this photo. */
    boolean recognizeOriginalJpeg(byte[] jpeg,int width,int height,
                                  TaskCatalog.Task oneShotTask){
        return recognizeOriginalJpeg(jpeg,width,height,1f,false,1f,oneShotTask);
    }
    boolean recognizeOriginalJpeg(byte[] jpeg,int width,int height,
                                  TaskCatalog.Task oneShotTask,long captureMs){
        return recognizeOriginalJpeg(jpeg,width,height,1f,false,1f,oneShotTask,captureMs);
    }
    private boolean recognizeOriginalJpeg(byte[] jpeg,int width,int height,float selectedZoom,
                                  boolean portraitCover,float viewportAspect,
                                  TaskCatalog.Task oneShotTask){
        return recognizeOriginalJpeg(jpeg,width,height,selectedZoom,portraitCover,
                                    viewportAspect,oneShotTask,-1L);
    }
    private boolean recognizeOriginalJpeg(byte[] jpeg,int width,int height,float selectedZoom,
                                  boolean portraitCover,float viewportAspect,
                                  TaskCatalog.Task oneShotTask,long captureMs){
        if(closed||busy)return false;
        syncSpeechSettings();
        if(jpeg==null||jpeg.length<256||width<2||height<2){
            report("C110没有返回有效高清原图，未提交云端");return false;
        }
        final int id=request.incrementAndGet();
        clearPendingSpeech();
        final long started=SystemClock.elapsedRealtime();
        busy=true;
        lastTapMs=started;
        if(tts!=null)tts.stop();
        report("已下载 C110 高清原图 "+width+"×"+height+"，正在识别…");
        final float zoomAtTap=C110ZoomGeometry.clamp(selectedZoom);
        final int uploadMode=C110PhotoUploadPolicy.normalize(
            activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getInt(C110PhotoUploadPolicy.PREF,C110PhotoUploadPolicy.ORIGINAL));
        final RuntimeDiagnostics photoDiag=new RuntimeDiagnostics(activity);
        photoDiag.begin("c110_photo");
        if(captureMs>=0)photoDiag.event("c110","photo_total_ms",captureMs,"ok");
        if(metricsListener!=null&&captureMs>=0)
            metricsListener.onCaptured(width,height,captureMs);
        photoDiag.event("c110","upload_mode",uploadMode,"ok");
        photoDiag.event("c110","native_width_px",width,"ok");
        photoDiag.event("c110","native_height_px",height,"ok");
        taskFuture=io.submit(()->{
            long prepareAt=SystemClock.elapsedRealtime();
            try{
                // 1x sends the exact original JPEG, with no extra image decode/encode.
                C110ZoomFrames.JpegResult region=
                    C110ZoomFrames.cropNativeJpeg(jpeg,width,height,zoomAtTap,
                                                  portraitCover,viewportAspect);
                if(id!=request.get()||closed)return;
                if(zoomAtTap>1.001f||portraitCover)
                    report("高清OCR实际裁剪："+width+"×"+height+" → "+
                        region.width+"×"+region.height+"，倍率"+
                        String.format(Locale.ROOT,"%.1f",zoomAtTap)+"倍");
                C110ZoomFrames.JpegResult upload=C110ZoomFrames.limitForCloud(
                    region.jpeg,region.width,region.height,uploadMode);
                if(id!=request.get()||closed)return;
                photoDiag.event("jpeg","encode_ms",
                    SystemClock.elapsedRealtime()-prepareAt,"ok");
                photoDiag.event("jpeg","width_px",upload.width,"ok");
                photoDiag.event("jpeg","height_px",upload.height,"ok");
                photoDiag.event("jpeg","bytes",upload.jpeg.length,"ok");
                if(metricsListener!=null){
                    final int uw=upload.width,uh=upload.height,ub=upload.jpeg.length;
                    main.post(()->{
                        if(!closed&&id==request.get()&&metricsListener!=null)
                            metricsListener.onUpload(uw,uh,ub);
                    });
                }
                recognizeJpeg(id,upload.jpeg,started,oneShotTask,photoDiag);
            }catch(Exception e){
                main.post(()->{
                    if(id!=request.get()||closed)return;
                    busy=false;
                    report("高清缩放区域失败，未上传未裁剪整图："+e.getClass().getSimpleName());
                });
            }
        });
        return true;
    }

    /** Photo-only path. Phone JPEG has already passed through the existing
     * PhoneStillJpeg.prepare rotation/mirror/upload-size policy. This uses the
     * same cloud OCR/vision model and TTS settings as the C110 quick shutter,
     * but the phone may send over validated home Wi-Fi OR cellular. */
    boolean recognizePreparedPhoneJpeg(byte[] jpeg,int width,int height){
        return recognizePreparedPhoneJpeg(jpeg,width,height,null,SystemClock.elapsedRealtime());
    }
    boolean recognizePreparedPhoneJpeg(byte[] jpeg,int width,int height,
                                      RuntimeDiagnostics diagnostics,long started){
        if(closed||busy)return false;
        if(jpeg==null||jpeg.length<256||width<2||height<2){
            report("手机高清照片无效");return false;
        }
        syncSpeechSettings();
        final int id=request.incrementAndGet();
        clearPendingSpeech();
        busy=true;
        if(tts!=null)tts.stop();
        report("已拍照 · 正在识别…");
        if(diagnostics!=null){
            diagnostics.event("jpeg","width_px",width,"ok");
            diagnostics.event("jpeg","height_px",height,"ok");
            diagnostics.event("jpeg","bytes",jpeg.length,"ok");
        }
        if(metricsListener!=null)metricsListener.onUpload(width,height,jpeg.length);
        recognizeJpeg(id,jpeg,started,null,diagnostics,true);
        return true;
    }

    /** Never route phone cloud data over the local-only camera hotspot. */
    private Network phoneInternetNetwork(){
        ConnectivityManager cm=(ConnectivityManager)
            activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)throw new IllegalStateException("网络服务未就绪");
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            if(caps==null||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))continue;
            if(C110WifiBootstrap.connected(activity)!=null&&
               n.equals(C110WifiBootstrap.connected(activity)))continue;
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return n;
        }
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                &&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                &&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))return n;
        }
        throw new IllegalStateException("未找到可上网的Wi-Fi或移动网络");
    }

    private void recognizeJpeg(int id,byte[] jpeg,long started,TaskCatalog.Task oneShotTask){
        recognizeJpeg(id,jpeg,started,oneShotTask,null,false);
    }
    private void recognizeJpeg(int id,byte[] jpeg,long started,TaskCatalog.Task oneShotTask,
                               RuntimeDiagnostics photoDiag){
        recognizeJpeg(id,jpeg,started,oneShotTask,photoDiag,false);
    }
    private void recognizeJpeg(int id,byte[] jpeg,long started,TaskCatalog.Task oneShotTask,
                               RuntimeDiagnostics photoDiag,boolean phoneSource){
        if(closed||id!=request.get())return;
        if(jpeg.length<256){
            main.post(()->{if(id==request.get()&&!closed){
                busy=false;report("取得的当前帧无效");}});
            return;
        }
        final CloudVisionClient.Cancellation cancel=new CloudVisionClient.Cancellation();
        synchronized(photoNetworkLock){
            if(closed||id!=request.get())return;
            activePhotoCloud=cancel;
            taskFuture=photoCloudIo.submit(()->recognizeJpegNetwork(
                id,jpeg,started,oneShotTask,cancel,photoDiag,phoneSource));
        }
    }
    private void recognizeJpegNetwork(int id,byte[] jpeg,long started,
                                     TaskCatalog.Task oneShotTask,
                                     CloudVisionClient.Cancellation cancel,
                                     RuntimeDiagnostics photoDiag,boolean phoneSource){
        try{
            cancel.check();
            // C110 stays explicitly cellular; phone can use validated Wi-Fi.
            Network cellular=phoneSource?phoneInternetNetwork():
                C110WifiCamera.requireCellular(activity);
            // Share one target between Home quickshot, C110 preview and
            // MainActivity. Explicit voice read/observe applies to one shot.
            final String target=oneShotTask==null?
                FindingTargetStore.current(activity):"";
            SharedPreferences prefs=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            if(!target.isEmpty()){
                String visual=VisionModelCatalog.normalize(prefs.getString(
                    "cloud_vision_model",VisionModelCatalog.DEFAULT));
                CloudProviderStore.Config service=CloudProviderStore.resolve(
                    activity,CloudProviderStore.VISION,visual);
                RecentPhotoSession.captured(jpeg,cellular,"寻找"+target);
                TargetVisionClient.Result check=TargetVisionClient.inspect(
                    jpeg,target,service,cellular,cancel);
                main.post(()->{
                    if(closed||id!=request.get())return;
                    busy=false;
                    if(!target.equals(FindingTargetStore.current(activity))){
                        report("寻找目标已更改，本次旧照片结果已取消");
                        return;
                    }
                    lastText=check.message;
                    RecentPhotoSession.analyzed(jpeg,check.message);
                    if(check.confirmed())
                        FindingTargetStore.clearIfCurrent(activity,target);
                    report(check.confirmed()?"已找到："+target:
                        "寻找："+target+" · "+check.decision);
                    speak(check.message);
                });
                return;
            }
            TaskCatalog.Task selected=oneShotTask;
            if(selected==null){
                String idTask=TaskCatalog.selectedId(activity);
                for(TaskCatalog.Task task:TaskCatalog.all(activity))
                    if(task.id.equals(idTask)){selected=task;break;}
            }
            if(selected==null)selected=TaskCatalog.defaults()[0];
            final TaskCatalog.Task task=selected;
            boolean ocr=TaskCatalog.OCR.equals(task.engine);
            String model=ocr?
                OcrModelCatalog.normalize(prefs.getString("cloud_ocr_model_v1",OcrModelCatalog.DEFAULT)):
                VisionModelCatalog.normalize(prefs.getString("cloud_vision_model",VisionModelCatalog.DEFAULT));
            String speed=ocr?OcrSpeedExperiment.normalize(
                prefs.getString(OcrSpeedExperiment.PREF,OcrSpeedExperiment.DEFAULT)):
                OcrSpeedExperiment.A;
            CloudProviderStore.Config service=CloudProviderStore.resolve(activity,
                ocr?CloudProviderStore.OCR:CloudProviderStore.VISION,model);
            RecentPhotoSession.captured(jpeg,cellular,task.name);
            AtomicInteger spokenParts=new AtomicInteger();
            CloudVisionClient.Result result=CloudVisionClient.recognize(jpeg,service,task,
                part->main.post(()->{
                    if(closed||id!=request.get())return;
                    String piece=filtered(part,task);
                    if(piece.isEmpty())return;
                    int count=spokenParts.getAndIncrement();
                    enqueueSpeech(piece,count==0?TextToSpeech.QUEUE_FLUSH:
                        TextToSpeech.QUEUE_ADD,"c110-"+id+"-"+count);
                }),speed,cellular,cancel);
            main.post(()->{
                if(closed||id!=request.get())return;
                if(metricsListener!=null)metricsListener.onRecognized(result.networkMs);
                if(photoDiag!=null){
                    photoDiag.event("cloud","upload_ms",result.uploadMs,"ok");
                    photoDiag.event("cloud","network_ms",result.networkMs,"ok");
                    photoDiag.event("cloud","first_text_ms",result.firstTextMs,"ok");
                    photoDiag.event("cloud","first_speakable_ms",result.firstSpeakableMs,"ok");
                    photoDiag.event("capture","total_ms",
                        SystemClock.elapsedRealtime()-started,"ok");
                }
                busy=false;
                lastText=result.text==null?"":result.text;
                RecentPhotoSession.analyzed(jpeg,lastText);
                report("已识别："+task.name+"，取帧到识别 "+(SystemClock.elapsedRealtime()-started)+"ms");
                // A/B have no early OCR speech; C streams lines. Visual tasks
                // already stream parts, so do not repeat the entire response.
                if(spokenParts.get()==0||(ocr&&!OcrSpeedExperiment.earlySpeak(speed)))
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
        enqueueSpeech(text,TextToSpeech.QUEUE_FLUSH,"c110-final-"+request.get());
    }

    void replay(){
        syncSpeechSettings();
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

    /** A new photo can replace the prior OCR without stopping VLC/preview. */
    void cancelRecognition(){
        request.incrementAndGet();
        clearPendingSpeech();
        synchronized(photoNetworkLock){
            if(activePhotoCloud!=null){
                activePhotoCloud.cancel();activePhotoCloud=null;
            }
            if(taskFuture!=null){taskFuture.cancel(true);taskFuture=null;}
        }
        busy=false;
        if(tts!=null)tts.stop();
    }

    void stop(){
        cancelRecognition();
        outputReady=false;
    }

    void shutdown(){
        closed=true;
        ++ttsGeneration;
        stop();
        io.shutdownNow();
        photoCloudIo.shutdownNow();
        if(tts!=null){tts.shutdown();tts=null;}
        ttsReady=false;
    }
}
