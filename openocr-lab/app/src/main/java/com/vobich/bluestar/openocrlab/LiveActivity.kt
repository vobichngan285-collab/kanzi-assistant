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
 private val history=ArrayDeque<String>()
 private var official=true
 private var threads=4
 private var longSide=1600
 private fun now()=SystemClock.elapsedRealtime()
 private fun log(s:String){if(history.size>=80)history.removeFirst();history.addLast(s)}
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
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
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Live OCR",history.joinToString("\n")))
   Toast.makeText(this@LiveActivity,"已复制",Toast.LENGTH_SHORT).show()
  }},LinearLayout.LayoutParams(0,-2,1f))
  status=TextView(this).apply{textSize=16f;text="等待摄像头与TTS";root.addView(this)}
  surface=SurfaceView(this).apply{root.addView(this,LinearLayout.LayoutParams(-1,0,1f))}
  val scroll=ScrollView(this);root.addView(scroll,LinearLayout.LayoutParams(-1,0,.35f))
  result=TextView(this).apply{textSize=18f;setTextIsSelectable(true);scroll.addView(this)}
  surface.holder.addCallback(object:SurfaceHolder.Callback{
   override fun surfaceCreated(h:SurfaceHolder){maybeOpen()}
   override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,hg:Int){maybeOpen()}
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
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),7)else maybeOpen()
 }
 override fun onRequestPermissionsResult(req:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(req,p,g);if(req==7){if(g.firstOrNull()==PackageManager.PERMISSION_GRANTED)maybeOpen()else status.text="未授权摄像头"}}
 override fun onPause(){resumed=false;closeCamera();super.onPause()}
 private fun closeCamera(){
  epoch++;opening=false
  capture?.close();capture=null;camera?.close();camera=null
  images?.close();images=null
  tts?.stop();speechIds.clear();previous=emptyList();nextAt=0L
 }
 override fun onDestroy(){closeCamera();tts?.shutdown();tts=null
  worker.execute{runCatching{db?.close()};runCatching{reader?.close()}};worker.shutdown();super.onDestroy()
 }
 @SuppressLint("MissingPermission")
 private fun maybeOpen(){
  if(!resumed||opening||camera!=null||!surface.holder.surface.isValid||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return
  val token=++epoch;opening=true
  try{
   val manager=getSystemService(CAMERA_SERVICE) as CameraManager
   val id=manager.cameraIdList.first{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK}
   val info=manager.getCameraCharacteristics(id)
   val map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
   val common=map.getOutputSizes(ImageFormat.YUV_420_888).filter{it in map.getOutputSizes(SurfaceHolder::class.java)&&it.width%2==0&&it.height%2==0}
   val selected=common.firstOrNull{it.width==1920&&it.height==1080}
    ?:common.filter{it.width.toLong()*it.height<=2_100_000}.maxByOrNull{it.width.toLong()*it.height}
    ?:error("没有合适的预览/YUV共同尺寸")
   val displayDegrees=when(windowManager.defaultDisplay.rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
   rotation=((info.get(CameraCharacteristics.SENSOR_ORIENTATION)?:90)-displayDegrees+360)%360
   surface.holder.setFixedSize(selected.width,selected.height)
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
         s.setRepeatingRequest(req.build(),null,ui);status.text="实时识别 "+selected.width+"×"+selected.height
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
 private fun fail(token:Int,message:String){if(token==epoch){closeCamera();status.text=message;log(message)}}
 private fun onImage(src:ImageReader,token:Int){
  val image=try{src.acquireLatestImage()}catch(_:Exception){null}?:return
  if(token!=epoch||!resumed||busy||!ready||speechIds.isNotEmpty()||now()<nextAt){image.close();return}
  busy=true
  val no=++frameNo;val start=now();val rotate=rotation;val old=previous
  status.text="识别中 · 第"+no+"帧"
  // Copy while ImageReader owns a valid image; worker never touches a closed camera buffer.
  val w=image.width;val h=image.height
  val nv=try{pack(image)}catch(t:Throwable){busy=false;nextAt=now()+1000;log("frame_copy_error "+t);return}finally{image.close()}
  val copied=now()
  worker.execute{
   var bitmap:Bitmap?=null
   try{
    if(db==null)db=DbLocator(this,threads)
    val convertAt=now()
    val bm=decode(nv,w,h,rotate);bitmap=bm
    val converted=now()
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
          ui.post{if(token==epoch&&resumed)enqueue(line,no,start,at)}
         }
        }
       }
      }
     }finally{rgba.release();bgr.release()}
    }
    val done=now()
    val report="frame="+no+" input="+bm.width+"x"+bm.height+" copy_ms="+(copied-start)+" init_ms="+(convertAt-copied)+" convert_ms="+(converted-convertAt)+" db_ms="+(detected-converted)+" first_nonblank_ms="+first+" total_ms="+(done-start)+" boxes="+found.size+" rows="+out.size+" new_rows="+newRows
    ui.post{if(token==epoch&&resumed){previous=out.toList();result.text=out.joinToString("\n");status.text=if(out.isEmpty())"未检测到文字" else "识别完成 · "+(done-start)+" ms";log(report)}}
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
