package com.vobich.bluestar.openocrlab
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.util.Size
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.*
import android.widget.*
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors
import kotlin.math.*

class LiveActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private val ui=Handler(Looper.getMainLooper())
 private lateinit var surface:SurfaceView
 private lateinit var status:TextView
 private lateinit var result:TextView
 private var camera:CameraDevice?=null
 private var capture:CameraCaptureSession?=null
 private var images:ImageReader?=null
 private var resumed=false
 private var opening=false
 private var epoch=0
 private var busy=false
 private var nextAt=0L
 private var frameNo=0
 private var speechNo=0
 private var rotation=0
 private var db:DbLocator?=null
 private var reader:Reader?=null
 private var tts:TextToSpeech?=null
 private var ready=false
 private val speechIds=mutableMapOf<String,Long>()
 private var previous=emptyList<String>()
 private var external=false
 private var runOcr=true
 private lateinit var metrics:TextView
 private lateinit var toggle:Button
 private var candidateIndex=0
 private var candidateCount=0
 private var probing=true
 private var probeStart=0L
 private var windowAt=0L
 private var receivedFrames=0L
 private var windowFrames=0L
 private var lastSensor=0L
 private var windowSensor=0L
 private var captureFrames=0L
 private var windowCaptures=0L
 private var lastW=0
 private var lastH=0
 private var actualFps=0.0
 private var lastOcrAt=0L
 private var ocrCompleted=0L
 private val cameraIo=Executors.newSingleThreadExecutor()
 private var vlc:LibVLC?=null
 private var player:MediaPlayer?=null
 private var vlcMedia:Media?=null
 private var bound:Network?=null
 private var oldBinding:Network?=null
 private var changedBinding=false
 private var trackW=0
 private var trackH=0
 private var layoutW=0
 private var layoutH=0
 private var codedW=0
 private var codedH=0
 private var matchAt=0L
 private var videoReady=false
 private var prevDecoded=-1L
 private var prevDisplayed=-1L
 private var prevLost=-1L
 private var prevStatsAt=0L
 private val ticker=object:Runnable{override fun run(){
  if(!resumed)return
  if(external)pollExternal()
  ui.postDelayed(this,1000)
 }}
 private fun fmt(v:Double)=String.format(java.util.Locale.US,"%.2f",v)
 private fun sizeText(w:Int,h:Int)=if(w>0&&h>0)"${w}x${h} (${w.toLong()*h} px)" else "unknown"
 private fun resetMeasurements(){
  probeStart=now();windowAt=probeStart;receivedFrames=0;windowFrames=0;captureFrames=0;windowCaptures=0
  lastSensor=0;windowSensor=0;lastW=0;lastH=0;actualFps=0.0;probing=true
  prevDecoded=-1;prevDisplayed=-1;prevLost=-1;prevStatsAt=0;matchAt=0;videoReady=false
  trackW=0;trackH=0;layoutW=0;layoutH=0;codedW=0;codedH=0
 }
 private fun canSample()=resumed&&runOcr&&!probing&&!busy&&ready&&speechIds.isEmpty()&&now()>=nextAt

 private var official=true
 private var threads=4
 private var longSide=1600
 private fun now()=SystemClock.elapsedRealtime()
 private fun log(s:String){VideoExperimentLog.add(this,(if(external)"C110" else "PHONE")+" "+s)}
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  external=intent.getBooleanExtra("c110",false)
  log("SESSION start version=0.1.3 source="+(if(external)"rtsp" else "camera2"))
  official=intent.getBooleanExtra("official",true)
  threads=intent.getIntExtra("threads",4)
  longSide=intent.getIntExtra("longSide",1600)
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,48,12,12)}
  setContentView(root)
  val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  root.addView(controls)
  controls.addView(Button(this).apply{text="停止 / 返回";setOnClickListener{finish()}},LinearLayout.LayoutParams(0,-2,1f))
  controls.addView(Button(this).apply{text="复制诊断";setOnClickListener{
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Live OCR",VideoExperimentLog.read(this@LiveActivity)))
   Toast.makeText(this@LiveActivity,"已复制",Toast.LENGTH_SHORT).show()
  }},LinearLayout.LayoutParams(0,-2,1f))
  toggle=Button(this).apply{text="暂停OCR（仅预览）";root.addView(this);setOnClickListener{
   runOcr=!runOcr;text=if(runOcr)"暂停OCR（仅预览）" else "开启实时OCR"
   if(!runOcr){tts?.stop();speechIds.clear();previous=emptyList()}
   log("OCR enabled="+runOcr)
  }}
  metrics=TextView(this).apply{textSize=14f;text="等待实际帧；先测试预览";root.addView(this)}
  status=TextView(this).apply{textSize=16f;text="等待摄像头与TTS";root.addView(this)}
  surface=SurfaceView(this).apply{root.addView(this,LinearLayout.LayoutParams(-1,0,1f))}
  val scroll=ScrollView(this);root.addView(scroll,LinearLayout.LayoutParams(-1,0,.35f))
  result=TextView(this).apply{textSize=18f;setTextIsSelectable(true);scroll.addView(this)}
  surface.holder.addCallback(object:SurfaceHolder.Callback{
   override fun surfaceCreated(h:SurfaceHolder){maybeOpen()}
   override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,hg:Int){if(external){log("SURFACE callback="+w+"x"+hg)};maybeOpen()}
   override fun surfaceDestroyed(h:SurfaceHolder){closeCamera()}
  })
  tts=TextToSpeech(this){code->ui.post{
   if(isDestroyed)return@post
   ready=code==TextToSpeech.SUCCESS
   if(ready)tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
    override fun onStart(id:String){val at=now();ui.post{speechIds[id]?.let{log("tts_start id="+id+" frame_to_callback_ms="+(at-it))}}}
    override fun onDone(id:String){ui.post{speechIds.remove(id)}}
    override fun onError(id:String){ui.post{speechIds.remove(id);log("tts_error id="+id)}}
   }) else status.text="系统TTS不可用"
  }}
 }
 override fun onResume(){super.onResume();resumed=true
  candidateIndex=0;log("RESUME");ui.removeCallbacks(ticker);ui.post(ticker)
  if(external)maybeOpen() else if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),7)else maybeOpen()
 }
 override fun onRequestPermissionsResult(req:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(req,p,g);if(req==7){if(g.firstOrNull()==PackageManager.PERMISSION_GRANTED)maybeOpen()else status.text="未授权摄像头"}}
 override fun onPause(){resumed=false;ui.removeCallbacks(ticker);closeCamera();log("SESSION pause ocr_completed="+ocrCompleted);super.onPause()}
 private fun closeCamera(){
  epoch++;opening=false;closeExternal()
  capture?.close();capture=null;camera?.close();camera=null
  images?.close();images=null
  tts?.stop();speechIds.clear();previous=emptyList();nextAt=0L
 }
 override fun onDestroy(){closeCamera();tts?.shutdown();tts=null
  worker.execute{runCatching{db?.close()};runCatching{reader?.close()}};worker.shutdown();cameraIo.shutdownNow();super.onDestroy()
 }
 @SuppressLint("MissingPermission")
 private fun maybeOpen(){
  if(!resumed||opening||!surface.holder.surface.isValid)return
  if(external){if(player==null)openExternal();return}
  if(camera!=null||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return
  val token=++epoch;opening=true
  try{
   val manager=getSystemService(CAMERA_SERVICE) as CameraManager
   val id=manager.cameraIdList.first{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK}
   val info=manager.getCameraCharacteristics(id)
   val map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
   val sizes=map.getOutputSizes(ImageFormat.YUV_420_888).filter{it.width%2==0&&it.height%2==0}.sortedByDescending{it.width.toLong()*it.height}
   val displays=map.getOutputSizes(SurfaceHolder::class.java).toList()
   if(candidateIndex==0){
    log("CAPS camera_id="+id+" regular_yuv="+sizes.joinToString(","){v->"${v.width}x${v.height}:min_ns="+runCatching{map.getOutputMinFrameDuration(ImageFormat.YUV_420_888,v)}.getOrDefault(0)})
    log("CAPS high_resolution_still_yuv="+(map.getHighResolutionOutputSizes(ImageFormat.YUV_420_888)?.joinToString(",")?: "none")+" excluded_from_continuous_probe=true")
    log("CAPS surface_sizes="+displays.joinToString(","))
   }
   // Test regular YUV sizes, with a smaller preview surface when necessary; preview size never limits OCR.
   val candidates=sizes.flatMap{v->
    val small=displays.filter{it.width.toLong()*it.height<=2_100_000L}.minWithOrNull(compareBy<Size>{abs(it.width.toDouble()/it.height-v.width.toDouble()/v.height)}.thenByDescending{it.width.toLong()*it.height})
    listOfNotNull(if(v in displays)Pair(v,v) else null,small?.let{Pair(v,it)}).distinct()
   }
   candidateCount=candidates.size
   if(candidateIndex>=candidateCount){opening=false;status.text="没有通过实时帧测试的组合";log("PROBE exhausted");return}
   val (selected,displaySize)=candidates[candidateIndex]
   resetMeasurements()
   log("PROBE attempt="+(candidateIndex+1)+"/"+candidateCount+" yuv="+selected+" display="+displaySize+" min_ns="+runCatching{map.getOutputMinFrameDuration(ImageFormat.YUV_420_888,selected)}.getOrDefault(0))
   ui.postDelayed({if(token==epoch&&probing)retryPhone(token,"no_stable_frames_timeout")},10000)
   val displayDegrees=when(windowManager.defaultDisplay.rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
   rotation=((info.get(CameraCharacteristics.SENSOR_ORIENTATION)?:90)-displayDegrees+360)%360
   surface.holder.setFixedSize(displaySize.width,displaySize.height)
   val ir=ImageReader.newInstance(selected.width,selected.height,ImageFormat.YUV_420_888,3)
   images=ir
   ir.setOnImageAvailableListener({src->onImage(src,token)},ui)
   log("camera requested="+selected.width+"x"+selected.height+" rotation="+rotation+" mode="+(if(official)"official" else "smart")+" cpu="+threads+" db="+longSide)
   manager.openCamera(id,object:CameraDevice.StateCallback(){
    override fun onOpened(c:CameraDevice){
     if(token!=epoch||!resumed){c.close();return}
     camera=c
     try{
      val display=surface.holder.surface
      c.createCaptureSession(listOf(display,ir.surface),object:CameraCaptureSession.StateCallback(){
       override fun onConfigured(s:CameraCaptureSession){
        if(token!=epoch||!resumed){s.close();return}
        capture=s;opening=false
        try{
         val req=c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{
          addTarget(display);addTarget(ir.surface)
          if(info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)==true)set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
         }
         s.setRepeatingRequest(req.build(),object:CameraCaptureSession.CaptureCallback(){
          override fun onCaptureCompleted(session:CameraCaptureSession,request:CaptureRequest,result:TotalCaptureResult){if(token==epoch)captureFrames++}
         },ui);status.text="正在验证 "+selected.width+"×"+selected.height;probeStart=now();windowAt=probeStart
        }catch(t:Throwable){fail(token,t.toString())}
       }
       override fun onConfigureFailed(s:CameraCaptureSession){s.close();fail(token,"预览/YUV组合配置失败")}
      },ui)
     }catch(t:Throwable){fail(token,t.toString())}
    }
    override fun onDisconnected(c:CameraDevice){c.close();fail(token,"摄像头断开")}
    override fun onError(c:CameraDevice,e:Int){c.close();fail(token,"摄像头错误 "+e)}
   },ui)
  }catch(t:Throwable){fail(token,t.toString())}
 }
 private fun retryPhone(token:Int,message:String){
  if(token!=epoch||!resumed)return
  log("PROBE reject index="+candidateIndex+" reason="+message)
  closeCamera();candidateIndex++
  if(candidateIndex<candidateCount){opening=true;ui.postDelayed({opening=false;maybeOpen()},350)}
  else status.text="实时预览失败："+message
 }
 private fun fail(token:Int,message:String){if(token==epoch){
  if(!external&&probing&&candidateCount>0)retryPhone(token,message)
  else{closeCamera();status.text=message;log("ERROR "+message)}
 }}
 private fun measurePhone(image:Image,token:Int){
  val t=now();val stamp=image.timestamp
  if(receivedFrames==0L){lastW=image.width;lastH=image.height;log("FIRST actual_yuv="+sizeText(lastW,lastH)+" sensor_timestamp_ns="+stamp);windowAt=t;windowSensor=stamp;windowCaptures=captureFrames;probeStart=t}
  receivedFrames++;lastSensor=stamp
  val dt=t-windowAt
  if(dt>=1000){
   val frames=receivedFrames-windowFrames
   actualFps=frames*1000.0/dt
   val timestampFps=if(stamp>windowSensor&&frames>0)frames*1e9/(stamp-windowSensor) else -1.0
   val cameraFps=(captureFrames-windowCaptures)*1000.0/dt
   val sr=surface.holder.surfaceFrame
   val line="PREVIEW actual_yuv="+sizeText(lastW,lastH)+" surface="+sr.width()+"x"+sr.height()+" view="+surface.width+"x"+surface.height+" delivered_fps="+fmt(actualFps)+" sensor_sample_fps="+fmt(timestampFps)+" capture_callback_fps="+fmt(cameraFps)+" frames="+receivedFrames+" capture_callbacks="+captureFrames+" ocr_busy="+busy+" probe="+probing
   metrics.text=line;log(line)
   windowAt=t;windowFrames=receivedFrames;windowSensor=stamp;windowCaptures=captureFrames
   if(probing&&t-probeStart>=4000){
    val avg=(receivedFrames-1)*1000.0/max(1L,t-probeStart)
    if(avg>=5.0){probing=false;log("PROBE accepted actual="+sizeText(lastW,lastH)+" avg_delivered_fps="+fmt(avg)+" threshold_fps=5 duration_ms="+(t-probeStart));status.text="最高通过档位 "+lastW+"×"+lastH}
    else ui.post{retryPhone(token,"delivered_fps="+fmt(avg)+" below_5")}
   }
  }
 }
 private fun openExternal(){
  val token=++epoch;opening=true;resetMeasurements();status.text="连接C110视频流"
  cameraIo.execute{
   try{
    val wifi=C110WifiCamera.previewNetwork(this)
    val init=C110WifiCamera().startPreview(this,wifi)
    require(init.modeStatus==0&&init.startStatus==0){"C110启流失败 "+init.modeStatus+"/"+init.startStatus}
    ui.post{if(token==epoch&&resumed){
     log("RTSP start mode="+init.modeStatus+" stream="+init.startStatus+" mode_ms="+init.modeMs+" start_ms="+init.startMs)
     try{
      val cm=getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
      oldBinding=cm.boundNetworkForProcess;bound=wifi
      if(oldBinding!=wifi){check(cm.bindProcessToNetwork(wifi)){"C110 Wi-Fi绑定失败"};changedBinding=true}
      val lib=LibVLC(applicationContext,arrayListOf("--network-caching=150","--live-caching=150","--no-audio"));vlc=lib
      val mp=MediaPlayer(lib);player=mp
      val out=mp.vlcVout
      out.setVideoView(surface)
      out.attachViews { _,w,h,vw,vh,sn,sd ->
       if(token==epoch&&player===mp){
        codedW=w;codedH=h;layoutW=if(vw>0)vw else w;layoutH=if(vh>0)vh else h
        log("VLC_LAYOUT coded="+w+"x"+h+" visible="+layoutW+"x"+layoutH+" sar="+sn+":"+sd)
        updateNativeSurface()
       }
      }
      mp.setEventListener{ev->ui.post{if(token==epoch&&player===mp){
       when(ev.type){
        MediaPlayer.Event.Playing->log("VLC playing")
        MediaPlayer.Event.Vout->{videoReady=ev.getVoutCount()>0;log("VLC vout="+ev.getVoutCount())}
        MediaPlayer.Event.EncounteredError,MediaPlayer.Event.EndReached->fail(token,"VLC event="+ev.type)
       }
      }}}
      val m=Media(lib,Uri.parse("rtsp://192.168.1.254:554/xxx.mp4"));vlcMedia=m
      for(opt in listOf(":no-audio",":network-caching=150",":live-caching=150",":file-caching=300",":sout-mux-caching=300",":codec=mediacodec,iomx,all"))m.addOption(opt)
      mp.media=m;mp.volume=0
      ui.postDelayed({if(token==epoch&&player===mp){mp.play();opening=false}},300)
     }catch(t:Throwable){fail(token,"C110 VLC "+t)}
    }}
   }catch(t:Throwable){ui.post{fail(token,"C110连接 "+t)}}
  }
 }
 private fun closeExternal(){
  val mp=player;player=null
  if(mp!=null){runCatching{mp.setEventListener(null)};runCatching{mp.stop()};runCatching{mp.vlcVout.detachViews()};runCatching{mp.release()}}
  vlcMedia?.let{runCatching{it.release()}};vlcMedia=null
  vlc?.let{runCatching{it.release()}};vlc=null
  if(changedBinding)runCatching{val cm=getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager;if(cm.boundNetworkForProcess==bound)cm.bindProcessToNetwork(oldBinding)}
  changedBinding=false;bound=null;oldBinding=null
 }
 // Read public VLC fields/methods defensively; unknown values stay unknown, never substituted with Surface dimensions.
 private fun call(obj:Any?,name:String):Any?=runCatching{obj?.javaClass?.getMethod(name)?.invoke(obj)}.getOrNull()
 private fun number(obj:Any?,name:String):Number?{
  if(obj is Map<*,*>)return obj[name] as? Number
  return (runCatching{obj?.javaClass?.getField(name)?.get(obj)}.getOrNull()?:call(obj,"get"+name.replaceFirstChar{it.uppercase()})) as? Number
 }
 private fun updateNativeSurface(){
  val mp=player?:return
  val w=if(layoutW>0)layoutW else trackW;val h=if(layoutH>0)layoutH else trackH
  if(w<=0||h<=0)return
  val sr=surface.holder.surfaceFrame
  if(sr.width()!=w||sr.height()!=h){surface.holder.setFixedSize(w,h);matchAt=0}
  mp.vlcVout.setWindowSize(w,h)
 }
 private fun pollExternal(){
  val mp=player?:return
  try{
   val track=call(mp,"getCurrentVideoTrack")
   val w=number(track,"width")?.toInt()?:0;val h=number(track,"height")?.toInt()?:0
   if(w>0&&h>0&&(w!=trackW||h!=trackH)){trackW=w;trackH=h;matchAt=0;log("RTSP_TRACK size="+sizeText(w,h))}
   updateNativeSurface()
   val sr=surface.holder.surfaceFrame
   val nativeW=if(layoutW>0)layoutW else trackW;val nativeH=if(layoutH>0)layoutH else trackH
   val match=videoReady&&nativeW>0&&nativeH>0&&sr.width()==nativeW&&sr.height()==nativeH&&(trackW==0||trackW==nativeW)&&(trackH==0||trackH==nativeH)
   val stats=call(vlcMedia,"getStats")?:call(mp,"getStats")
   val dec=number(stats,"decodedVideo")?.toLong()?:-1
   val disp=number(stats,"displayedPictures")?.toLong()?:-1
   val lost=number(stats,"lostPictures")?.toLong()?:-1
   val t=now();val dt=t-prevStatsAt
   fun rate(v:Long,old:Long)=if(dt>0&&v>=0&&old>=0&&v>=old)fmt((v-old)*1000.0/dt) else "unknown"
   val df=rate(dec,prevDecoded);val pf=rate(disp,prevDisplayed);val lf=rate(lost,prevLost)
   val num=number(track,"frameRateNum")?.toDouble()?:0.0;val den=number(track,"frameRateDen")?.toDouble()?:0.0
   val nominal=if(den>0&&num>0)fmt(num/den) else "unknown"
   val line="PREVIEW track="+sizeText(trackW,trackH)+" coded="+sizeText(codedW,codedH)+" visible="+sizeText(layoutW,layoutH)+" surface="+sr.width()+"x"+sr.height()+" view="+surface.width+"x"+surface.height+" track_fps="+nominal+" decoded_fps="+df+" displayed_fps="+pf+" lost_fps="+lf+" decoded_total="+dec+" displayed_total="+disp+" native_surface_match="+match+" ocr_busy="+busy
   log(line);metrics.text=line
   prevStatsAt=t;prevDecoded=dec;prevDisplayed=disp;prevLost=lost
   if(!match){matchAt=0;probing=true;status.text="等待原生视频尺寸与Surface一致";return}
   if(matchAt==0L)matchAt=t
   if(t-matchAt>=1500){if(probing)log("PROBE accepted native_size_surface="+nativeW+"x"+nativeH+" source=PixelCopy_not_raw_decoder_buffer");probing=false}
   if(canSample())sampleExternal(nativeW,nativeH)
  }catch(t:Throwable){log("VLC metrics error "+t);status.text="视频诊断失败："+t}
 }
 private fun sampleExternal(w:Int,h:Int){
  val token=epoch;val start=now();busy=true;val no=++frameNo;val old=previous
  val bm=try{Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)}catch(t:Throwable){busy=false;nextAt=now()+1000;log("C110 frame allocation error "+t);return}
  try{PixelCopy.request(surface.holder.surface,bm,{code->
   val sr=surface.holder.surfaceFrame
   if(token!=epoch||!resumed||code!=PixelCopy.SUCCESS||sr.width()!=w||sr.height()!=h){
    bm.recycle();busy=false;nextAt=now()+1000;log("C110 PixelCopy rejected code="+code);return@request
   }
   val copied=now()
   processFrame(token,no,start,copied,old,w,h){bm}
  },ui)}catch(t:Throwable){bm.recycle();busy=false;nextAt=now()+1000;log("C110 PixelCopy error "+t)}
 }

 private fun onImage(src:ImageReader,token:Int){
  val image=try{src.acquireLatestImage()}catch(_:Exception){null}?:return
  if(token!=epoch||!resumed){image.close();return}
  measurePhone(image,token)
  if(!canSample()){image.close();return}
  busy=true
  val no=++frameNo;val start=now();val rotate=rotation;val old=previous
  status.text="识别中 · 第"+no+"帧"
  // Copy while ImageReader owns a valid image; worker never touches a closed camera buffer.
  val w=image.width;val h=image.height
  val nv=try{pack(image)}catch(t:Throwable){busy=false;nextAt=now()+1000;log("frame_copy_error "+t);return}finally{image.close()}
  val copied=now()
  processFrame(token,no,start,copied,old,w,h){decode(nv,w,h,rotate)}
 }
 private fun processFrame(token:Int,no:Int,start:Long,copied:Long,old:List<String>,sourceW:Int,sourceH:Int,makeBitmap:()->Bitmap){
  worker.execute{
   var bitmap:Bitmap?=null
   try{
    val convertAt=now()
    val bm=makeBitmap();bitmap=bm
    val converted=now()
    if(db==null)db=DbLocator(this,threads)
    val dbAt=now()
    val locator=db!!
    locator.clearProbability()
    val found=locator.locate(bm,longSide,official)
    val detected=now()
    val plan=if(official)null else FocusPlanner.plan(found.map{q->q.rect.let{FocusPlanner.R(it.left,it.top,it.right,it.bottom,q.score)}},bm.width,bm.height)
    val roi=plan?.roi?.let{Rect(floor(it.l).toInt(),floor(it.t).toInt(),ceil(it.r).toInt(),ceil(it.b).toInt())}
    val boxes=found.filter{b->roi==null||roi.contains(b.rect.centerX().toInt(),b.rect.centerY().toInt())}
    val rows=if(official)OfficialGeometry.order(boxes).map{listOf(it)}else RowPlanner.plan(boxes,bm.width,bm.height,1.6f,true).rows
    val out=mutableListOf<String>()
    val remaining=old.groupingBy{it}.eachCount().toMutableMap()
    var first=-1L;var newRows=0
    if(rows.isNotEmpty()){
     if(reader==null)reader=Reader(this,"rep.onnx",threads)
     val engine=reader!!
     val rgba=Mat();val bgr=Mat()
     try{
      if(official){Utils.bitmapToMat(bm,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);rgba.release()}
      val batch=if(official)engine.officialBatchSize else 1
      for(index in rows.indices step batch){
       val chunk=rows.subList(index,min(rows.size,index+batch))
       val answers=if(official)engine.readOfficial(bgr,chunk.flatten())else emptyList()
       var ai=0
       for(row in chunk){
        val rawParts=mutableListOf<String>()
        for(b in row){
         val ans=if(official)answers[ai++]else engine.read(bm,b,true)
         if(ans.text.isNotBlank()&&(!official||ans.confidence>=.5f))rawParts.add(ans.text)
        }
        if(rawParts.isNotEmpty()){
         val line=rawParts.joinToString(" ");out.add(line)
         if(first<0)first=now()-start
         val left=remaining[line]?:0
         if(left>0)remaining[line]=left-1 else{
          newRows++
          val at=now()-start
          ui.post{if(token==epoch&&resumed&&runOcr)enqueue(line,no,start,at)}
         }
        }
       }
      }
     }finally{rgba.release();bgr.release()}
    }
    val done=now()
    val report="OCR frame="+no+" source="+sourceW+"x"+sourceH+" input="+sizeText(bm.width,bm.height)+" copy_ms="+(copied-start)+" convert_ms="+(converted-convertAt)+" detector_init_ms="+(dbAt-converted)+" db_ms="+(detected-dbAt)+" after_db_ms="+(done-detected)+" first_nonblank_ms="+first+" total_ms="+(done-start)+" boxes="+found.size+" rows="+out.size+" new_rows="+newRows+" pss_kb="+Debug.getPss()
    ui.post{if(token==epoch&&resumed){previous=if(runOcr)out.toList() else emptyList();result.text=out.joinToString("\n");status.text=if(out.isEmpty())"未检测到文字" else "识别完成 · "+(done-start)+" ms";ocrCompleted++;val interval=if(lastOcrAt==0L)-1L else now()-lastOcrAt;lastOcrAt=now();log(report+" ocr_completed="+ocrCompleted+" completion_interval_ms="+interval)}}
   }catch(t:Throwable){ui.post{if(token==epoch){status.text="识别失败："+t;log("frame_error "+t)}}}
   finally{
    db?.clearProbability();bitmap?.recycle()
    ui.post{busy=false;nextAt=now()+1000L}
   }
  }
 }
 private fun enqueue(line:String,no:Int,start:Long,at:Long){
  val engine=tts?:return
  for(part in SpeechChunks.split(line,TextToSpeech.getMaxSpeechInputLength())){
   val id="live:"+no+":"+(++speechNo)
   speechIds[id]=start
   val code=engine.speak(part,TextToSpeech.QUEUE_ADD,null,id)
   if(code!=TextToSpeech.SUCCESS){speechIds.remove(id);log("tts_enqueue_failed "+code)}
   else log("tts_enqueue frame="+no+" row_ready_ms="+at+" queue_ms="+(now()-start))
  }
 }
 private fun pack(image:Image):ByteArray{
  val w=image.width;val h=image.height
  require(w%2==0&&h%2==0)
  val nv=ByteArray(w*h*3/2)
  val y=image.planes[0];val u=image.planes[1];val v=image.planes[2]
  val yb=y.buffer;val ub=u.buffer;val vb=v.buffer
  val yp=yb.position();val up=ub.position();val vp=vb.position()
  for(r in 0 until h){
   if(y.pixelStride==1){yb.position(yp+r*y.rowStride);yb.get(nv,r*w,w)}
   else for(c in 0 until w)nv[r*w+c]=yb.get(yp+r*y.rowStride+c*y.pixelStride)
  }
  for(r in 0 until h/2)for(c in 0 until w/2){
   val p=w*h+r*w+c*2
   nv[p]=vb.get(vp+r*v.rowStride+c*v.pixelStride)
   nv[p+1]=ub.get(up+r*u.rowStride+c*u.pixelStride)
  }
  return nv
 }
 private fun decode(nv:ByteArray,w:Int,h:Int,rotate:Int):Bitmap{
  val packed=Mat(h+h/2,w,CvType.CV_8UC1);val rgba=Mat()
  val bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
  try{
   packed.put(0,0,nv);Imgproc.cvtColor(packed,rgba,Imgproc.COLOR_YUV2RGBA_NV21);Utils.matToBitmap(rgba,bm)
   if(rotate==0)return bm
   val turned=Bitmap.createBitmap(bm,0,0,w,h,Matrix().apply{postRotate(rotate.toFloat())},true)
   if(turned!==bm)bm.recycle()
   return turned
  }catch(t:Throwable){bm.recycle();throw t}finally{packed.release();rgba.release()}
 }
}
