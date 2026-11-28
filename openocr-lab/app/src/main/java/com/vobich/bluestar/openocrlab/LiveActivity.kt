package com.vobich.bluestar.openocrlab

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.*
import ai.onnxruntime.OrtSession
import com.jiangdg.usb.USBMonitor
import com.jiangdg.uvc.IFrameCallback
import com.jiangdg.uvc.UVCCamera
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.*

private fun clock()=SystemClock.elapsedRealtime()
private fun isC110(d:UsbDevice)=d.vendorId==0x0603&&d.productId==0x8612

// Only a coarse luminance comparison gates OCR. Original 2560x1440 YUYV
// is copied and converted for OCR; the signature is never used as OCR input.
internal object SceneGate {
 const val W=2560; const val H=1440
 fun signature(b:ByteBuffer):IntArray {
  val origin=b.position()
  return IntArray(64*36){i->
   val x=(i%64)*40+20;val y=(i/64)*40+20
   val p=origin+2*(y*W+x)
   ((b.get(p-2).toInt() and 255)+(b.get(p).toInt() and 255)+(b.get(p+2).toInt() and 255))/3
  }
 }
 fun changed(a:IntArray?,b:IntArray):Boolean {
  if(a==null)return true
  var sum=0;var large=0
  for(i in b.indices){val d=abs(a[i]-b[i]);sum+=d;if(d>=24)large++}
  return sum>=8*b.size||large*100>=12*b.size
 }
}
internal object LiveLog {
 @Volatile var running=false
 @Volatile var status="未启动"
 @Volatile var raw=""
 private val lock=Any()
 fun append(c:Context,s:String)=synchronized(lock){
  val f=File(c.filesDir,"live-ocr.log")
  try{
   if(f.length()>260000){val tail=f.readText().takeLast(120000);f.writeText(tail.substringAfter('\n',""))}
   f.appendText(System.currentTimeMillis().toString()+" "+s+"\n")
  }catch(_:Exception){}
 }
 fun read(c:Context)=synchronized(lock){runCatching{File(c.filesDir,"live-ocr.log").readText()}.getOrDefault("")}
 fun clear(c:Context)=synchronized(lock){if(!running)File(c.filesDir,"live-ocr.log").delete()}
}
class LiveActivity:Activity(){
 private val main=Handler(Looper.getMainLooper())
 private lateinit var state:TextView
 private lateinit var text:TextView
 private lateinit var mode:Spinner
 private lateinit var start:Button
 private var receiverRegistered=false
 private var asking=false
 private val permissionAction get()=packageName+".USB_PERMISSION_LIVE"
 private val permissionReceiver=object:BroadcastReceiver(){
  override fun onReceive(c:Context,i:Intent){
   if(i.action!=permissionAction)return
   asking=false
   val devices=(getSystemService(USB_SERVICE) as UsbManager).deviceList.values.filter(::isC110)
   if(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)&&devices.size==1&&
     (getSystemService(USB_SERVICE) as UsbManager).hasPermission(devices.single()))launchService()
   else LiveLog.status="USB授权未通过"
  }
 }
 private val refresh=object:Runnable{
  override fun run(){state.text=LiveLog.status;text.text=LiveLog.raw;start.isEnabled=!LiveLog.running&&!asking;mode.isEnabled=!LiveLog.running;main.postDelayed(this,500)}
 }
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,32,24,24)}
  val scroll=ScrollView(this);scroll.addView(root);setContentView(scroll)
  fun label(s:String)=TextView(this).apply{text=s;textSize=18f;root.addView(this)}
  label("蓝星 C110 实时离线OCR v0.1.0\n原始1440P · 无预览 · 锁屏专项")
  mode=Spinner(this).apply{adapter=ArrayAdapter(this@LiveActivity,android.R.layout.simple_spinner_dropdown_item,listOf("官方原框（全画面）","智能主阅读区（冻结原版）"));root.addView(this)}
  start=Button(this).apply{text="启动实时识别";root.addView(this);setOnClickListener{begin()}}
  Button(this).apply{text="重新识别当前画面";root.addView(this);setOnClickListener{if(LiveLog.running)startService(Intent(this@LiveActivity,LiveService::class.java).setAction("rescan"))}}
  Button(this).apply{text="停止识别与朗读";root.addView(this);setOnClickListener{if(LiveLog.running)startService(Intent(this@LiveActivity,LiveService::class.java).setAction("stop"))}}
  Button(this).apply{text="复制诊断日志";root.addView(this);setOnClickListener{copy(LiveLog.read(this@LiveActivity))}}
  Button(this).apply{text="复制当前原文";root.addView(this);setOnClickListener{copy(LiveLog.raw)}}
  Button(this).apply{text="清空诊断（停止后）";root.addView(this);setOnClickListener{LiveLog.clear(this@LiveActivity)}}
  state=label(LiveLog.status)
  text=label(LiveLog.raw);text.setTextIsSelectable(true)
  if(Build.VERSION.SDK_INT>=33)registerReceiver(permissionReceiver,IntentFilter(permissionAction),Context.RECEIVER_NOT_EXPORTED)
  else registerReceiver(permissionReceiver,IntentFilter(permissionAction))
  receiverRegistered=true
  LiveLog.append(this,"APP v0.1.0 model="+Build.MODEL+" sdk="+Build.VERSION.SDK_INT)
 }
 private fun copy(s:String){(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("蓝星",s));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show()}
 override fun onResume(){super.onResume();main.removeCallbacks(refresh);main.post(refresh)}
 override fun onPause(){main.removeCallbacks(refresh);super.onPause()}
 override fun onDestroy(){main.removeCallbacks(refresh);if(receiverRegistered)unregisterReceiver(permissionReceiver);super.onDestroy()}
 private fun begin(){
  if(LiveLog.running||asking)return
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.CAMERA),10);return}
  val usb=getSystemService(USB_SERVICE) as UsbManager
  val ds=usb.deviceList.values.filter(::isC110)
  if(ds.size!=1){LiveLog.status="请只连接一台C110（0603:8612）";return}
  if(usb.hasPermission(ds.single())){launchService();return}
  asking=true
  val pi=PendingIntent.getBroadcast(this,91,Intent(permissionAction).setPackage(packageName),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
  try{usb.requestPermission(ds.single(),pi)}catch(e:Exception){asking=false;LiveLog.status=e.toString()}
 }
 private fun launchService(){
  val i=Intent(this,LiveService::class.java).setAction("start").putExtra("official",mode.selectedItemPosition==0)
  try{startForegroundService(i)}catch(e:Exception){LiveLog.status=e.toString();LiveLog.append(this,"START_ERROR "+e)}
 }
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)begin()}
}

class LiveService:Service(){
 private val main=Handler(Looper.getMainLooper())
 private val gate=java.lang.Object()
 private val cameraWorker=Executors.newSingleThreadExecutor()
 private val ocrWorker=Executors.newSingleThreadExecutor()
 @Volatile private var active=false
 private var closing=false
 private var official=true
 private var monitor:USBMonitor?=null
 private var selected:UsbDevice?=null
 private var camera:UVCCamera?=null
 private var openRequested=false
 private var wake:PowerManager.WakeLock?=null
 private var tts:TextToSpeech?=null
 @Volatile private var voiceReady=false
 private var signature:IntArray?=null
 private var force=false
 private var generation=0L
 private var pending:Frame?=null
 private var runningOptions:OrtSession.RunOptions?=null
 private var processing=0L
 private var frameCount=0L
 private var badFrames=0L
 private var accepted=0L
 private var cancelled=0L
 private var replaced=0L
 private var completed=0L
 private var lastFrame=0L
 private var lastStats=0L
 private var launchedAt=0L
 private var maxGap=0L
 private var previousInteractive=true
 private data class Frame(val id:Long,val at:Long,val bytes:ByteArray)
 override fun onBind(i:Intent?)=null
 private fun log(s:String)=LiveLog.append(this,s)
 private fun status(s:String){LiveLog.status=s}
 private fun notification():Notification{
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("live_ocr","蓝星实时离线识别",NotificationManager.IMPORTANCE_LOW))
  val stop=PendingIntent.getService(this,3,Intent(this,LiveService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val open=PendingIntent.getActivity(this,4,Intent(this,LiveActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  return Notification.Builder(this,"live_ocr").setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("蓝星实时离线OCR").setContentText("C110识别运行中 · 点击返回").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"停止",stop).build()).build()
 }
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
  when(i?.action){
   "stop"->{halt("USER_STOP");return START_NOT_STICKY}
   "rescan"->{synchronized(gate){force=true};return START_NOT_STICKY}
   "start"->{if(active||closing)return START_NOT_STICKY}
   else->{stopSelf();return START_NOT_STICKY}
  }
  val usb=getSystemService(USB_SERVICE) as UsbManager
  val ds=usb.deviceList.values.filter(::isC110)
  if(ds.size!=1||!usb.hasPermission(ds.single())){status("USB未授权");stopSelf();return START_NOT_STICKY}
  try{
   startForeground(41,notification())
   selected=ds.single();official=i.getBooleanExtra("official",true)
   active=true;LiveLog.running=true;LiveLog.raw="";launchedAt=clock()
   val pm=getSystemService(PowerManager::class.java)
   wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,packageName+":live").apply{setReferenceCounted(false);acquire()}
   previousInteractive=pm.isInteractive
   log("START mode="+(if(official)"official" else "focus")+" db=1600 cpu=4 source=2560x1440 MJPEG no_surface=1 no_network=1")
   status("初始化离线OCR与TTS")
   initTts()
   ocrWorker.execute{ocrLoop()}
   connect()
   main.postDelayed(tick,1000)
  }catch(e:Throwable){log("START_ERROR "+e);halt("START_ERROR")}
  return START_NOT_STICKY
 }
 private fun initTts(){
  tts=TextToSpeech(this){r->main.post{
   if(!active)return@post
   val e=tts?:return@post
   if(r!=TextToSpeech.SUCCESS){log("TTS_INIT_ERROR "+r);return@post}
   val local=e.voices.orEmpty().filter{!it.isNetworkConnectionRequired&&it.locale.language=="zh"&&!("notInstalled" in it.features.orEmpty())}
   val v=local.firstOrNull{it.locale.country=="CN"}?:local.firstOrNull()
   if(v==null||e.setVoice(v)!=TextToSpeech.SUCCESS){log("TTS_NO_LOCAL_ZH_VOICE");return@post}
   e.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
    override fun onStart(id:String){val p=id.split(':');if(p.size==4&&p[3]=="0")log("TTS_FIRST id="+p[0]+" frame_to_callback_ms="+(clock()-(p[1].toLongOrNull()?:clock()))+" enqueue_to_callback_ms="+(clock()-(p[2].toLongOrNull()?:clock()))+" callback_only=1")}
    override fun onDone(id:String){}
    override fun onError(id:String){log("TTS_ERROR id="+id)}
   })
   voiceReady=true;log("TTS_READY local_voice="+v.name)
  }}
 }
 private fun connect(){
  val m=USBMonitor.getInstance(applicationContext);monitor=m
  m.setOnDeviceConnectListener(object:USBMonitor.OnDeviceConnectListener{
   override fun onAttach(d:UsbDevice){}
   override fun onDetach(d:UsbDevice){if(d==selected)main.post{halt("USB_DETACH")}}
   override fun onDisconnect(d:UsbDevice,b:USBMonitor.UsbControlBlock){if(d==selected)main.post{if(active)halt("USB_DISCONNECT")}}
   override fun onCancel(d:UsbDevice){if(d==selected)main.post{halt("USB_PERMISSION_DENIED")}}
   override fun onConnect(d:UsbDevice,b:USBMonitor.UsbControlBlock,fresh:Boolean){
    main.post{
     if(!active||d!=selected||openRequested)return@post
     openRequested=true
     cameraWorker.execute{
      if(!active)return@execute
      try{
       val c=UVCCamera();camera=c;c.open(b)
       c.setPreviewSize(SceneGate.W,SceneGate.H,30,31,UVCCamera.FRAME_FORMAT_MJPEG,1.0f)
       c.setFrameCallback(object:IFrameCallback{
        override fun onFrame(f:ByteBuffer){}
        override fun onFrame(f:ByteBuffer,w:Int,h:Int){receive(f,w,h)}
       },UVCCamera.PIXEL_FORMAT_YUV)
       c.startPreview();log("STREAM_START no_window=1")
      }catch(e:Throwable){log("STREAM_ERROR "+e);main.post{halt("STREAM_ERROR")}}
     }
    }
   }
  })
  m.register();m.requestPermission(selected!!)
 }
 private fun cancelLocked(){
  val o=runningOptions
  if(o!=null){try{o.setTerminate(true)}catch(e:Exception){log("TERMINATE_ERROR "+e)}}
 }
 private fun receive(f:ByteBuffer,w:Int,h:Int){
  if(!active)return
  val now=clock()
  synchronized(gate){
   if(!active)return
   if(w!=SceneGate.W||h!=SceneGate.H||f.remaining()!=w*h*2){badFrames++;return}
   frameCount++;if(lastFrame>0)maxGap=max(maxGap,now-lastFrame);lastFrame=now
   val sig=SceneGate.signature(f)
   if(!force&&!SceneGate.changed(signature,sig))return
   force=false;signature=sig
   val id=++generation;accepted++
   if(processing!=0L){cancelled++;cancelLocked()}
   if(pending!=null)replaced++
   tts?.stop();LiveLog.raw=""
   val bytes=ByteArray(f.remaining());f.duplicate().get(bytes)
   pending=Frame(id,now,bytes)
   log("NEW id="+id+" replaced="+replaced+" cancel_requested="+(if(processing!=0L)1 else 0))
   gate.notifyAll()
  }
 }
 private fun alive(id:Long):Boolean=synchronized(gate){active&&generation==id}
 private fun ensure(id:Long){if(!alive(id))throw InterruptedException("stale frame")}
 private fun ocrLoop(){
  var detector:DbLocator?=null;var reader:Reader?=null
  try{
   val init=clock()
   val d=DbLocator(this,4);detector=d
   val r=Reader(this,"rep.onnx",4);reader=r
   log("MODELS_READY init_ms="+(clock()-init)+" batch="+r.officialBatchSize)
   while(active){
    val work=synchronized(gate){
     while(active&&pending==null)gate.wait()
     if(!active)null else {
      val f=pending!!;pending=null
      val o=OrtSession.RunOptions();runningOptions=o;processing=f.id
      f to o
     }
    }?:break
    val (frame,options)=work
    val begin=clock()
    try{recognize(d,r,frame,options)}catch(e:Throwable){
     if(!alive(frame.id))log("ABORT id="+frame.id+" work_ms="+(clock()-begin)+" frame_age_ms="+(clock()-frame.at))
     else {log("OCR_ERROR id="+frame.id+" "+e);status("识别失败，见日志")}
    }finally{
     synchronized(gate){runningOptions=null;processing=0;options.close()}
    }
   }
  }catch(e:Throwable){log("MODEL_ERROR "+e);main.post{halt("MODEL_ERROR")}}
  finally{runCatching{reader?.close()};runCatching{detector?.close()};log("OCR_RELEASED")}
 }
 private fun recognize(d:DbLocator,r:Reader,f:Frame,o:OrtSession.RunOptions){
  val start=clock();ensure(f.id)
  val rgba=Mat();val yuyv=Mat(SceneGate.H,SceneGate.W,CvType.CV_8UC2)
  var bm:Bitmap?=null
  val bgr=Mat()
  try{
   yuyv.put(0,0,f.bytes);Imgproc.cvtColor(yuyv,rgba,Imgproc.COLOR_YUV2RGBA_YUY2)
   val image=Bitmap.createBitmap(SceneGate.W,SceneGate.H,Bitmap.Config.ARGB_8888);bm=image
   Utils.matToBitmap(rgba,image);yuyv.release();rgba.release()
   ensure(f.id);val decoded=clock()
   d.clearProbability()
   val found=d.locate(image,1600,official,o);val detected=clock();ensure(f.id)
   val plan=if(official)null else FocusPlanner.plan(found.map{q->q.rect.let{FocusPlanner.R(it.left,it.top,it.right,it.bottom,q.score)}},image.width,image.height)
   val roi=plan?.roi
   val boxes=found.filter{b->roi==null||(b.rect.centerX()>=roi.l&&b.rect.centerX()<roi.r&&b.rect.centerY()>=roi.t&&b.rect.centerY()<roi.b)}
   val rows=if(official)OfficialGeometry.order(boxes).map{listOf(it)} else RowPlanner.plan(boxes,image.width,image.height,1.6f,true).rows
   if(official){Utils.bitmapToMat(image,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);rgba.release()}
   val lines=mutableListOf<String>();var dropped=0;var count=0;var part=0
   val batch=if(official)r.officialBatchSize else 1
   for(index in rows.indices step batch){
    ensure(f.id)
    val chunk=rows.subList(index,min(rows.size,index+batch))
    val answers=if(official)r.readOfficial(bgr,chunk.flatten(),o) else emptyList()
    var ai=0
    for(row in chunk){
     val raw=mutableListOf<String>()
     for(b in row){
      ensure(f.id)
      val a=if(official)answers[ai++] else r.read(image,b,true,o)
      count++;val keep=!official||a.confidence>=.5f
      if(!keep)dropped++
      if(keep&&a.text.isNotBlank())raw.add(a.text)
     }
     if(raw.isNotEmpty())lines.add(raw.joinToString(" "))
    }
    val newLines=lines.drop(part)
    if(newLines.isNotEmpty()){
     synchronized(gate){
      if(!active||generation!=f.id)throw InterruptedException("stale speech")
      val speech=newLines.joinToString("\n")
      LiveLog.raw=lines.joinToString("\n")
      if(part==0)log("FIRST_TEXT id="+f.id+" frame_ms="+(clock()-f.at)+" chars="+speech.length)
      if(voiceReady){
       val queued=clock()
       val chunks=SpeechChunks.split(speech,TextToSpeech.getMaxSpeechInputLength())
       for((idx,s) in chunks.withIndex()){
        val rc=tts?.speak(s,if(part==0&&idx==0)TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,f.id.toString()+":"+f.at+":"+queued+":"+(part+idx))
        if(rc!=TextToSpeech.SUCCESS)log("TTS_ENQUEUE_ERROR id="+f.id+" rc="+rc)
       }
       if(part==0)log("TTS_ENQUEUE id="+f.id+" frame_ms="+(queued-f.at))
      }
      part=lines.size
     }
    }
   }
   synchronized(gate){
    if(!active||generation!=f.id)throw InterruptedException("stale completion")
    completed++;LiveLog.raw=lines.joinToString("\n")
    status("运行中 · 最近识别 "+(clock()-f.at)+" ms · "+lines.size+" 行")
    log("OCR_DONE id="+f.id+" source=2560x1440 wait_ms="+(start-f.at)+" convert_ms="+(decoded-start)+" detect_ms="+(detected-decoded)+" total_ms="+(clock()-start)+" frame_ms="+(clock()-f.at)+" boxes="+found.size+" crops="+count+" dropped="+dropped+" lines="+lines.size+" pss_kb="+Debug.getPss())
    log(d.lastDiagnostic)
   }
  }finally{bm?.recycle();yuyv.release();rgba.release();bgr.release();d.clearProbability()}
 }
 private val tick=object:Runnable{
  override fun run(){
   if(!active)return
   val now=clock();val interactive=getSystemService(PowerManager::class.java).isInteractive
   synchronized(gate){
    if(interactive!=previousInteractive){previousInteractive=interactive;log("SCREEN interactive="+interactive+" frames="+frameCount)}
    if(now-lastStats>=5000){lastStats=now;log("HEARTBEAT interactive="+interactive+" frames="+frameCount+" mismatch="+badFrames+" accepted="+accepted+" cancel_requested="+cancelled+" pending_replaced="+replaced+" completed="+completed+" last_frame_age_ms="+(if(lastFrame==0L)-1 else now-lastFrame)+" max_gap_ms="+maxGap)}
    if(now-launchedAt>15000&&(lastFrame==0L||now-lastFrame>10000)){status("超过10秒无视频帧，见日志");log("FRAME_STALL")}
   }
   main.postDelayed(this,1000)
  }
 }
 private fun halt(reason:String){
  if(closing)return
  closing=true;active=false
  synchronized(gate){generation++;pending=null;cancelLocked();tts?.stop();gate.notifyAll()}
  main.removeCallbacks(tick)
  status("已停止："+reason);log("STOP "+reason)
  cameraWorker.execute{
   runCatching{camera?.destroy()};camera=null
   runCatching{monitor?.destroy()};monitor=null
   main.post{
    voiceReady=false;runCatching{tts?.shutdown()};tts=null
    wake?.let{if(it.isHeld)it.release()};wake=null
    LiveLog.running=false
    stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
   }
  }
  cameraWorker.shutdown();ocrWorker.shutdown()
 }
 override fun onDestroy(){if(!closing)halt("SERVICE_DESTROY");super.onDestroy()}
}
