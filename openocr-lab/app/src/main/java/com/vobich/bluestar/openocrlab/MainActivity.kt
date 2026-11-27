package com.vobich.bluestar.openocrlab
import android.app.Activity
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.content.*
import android.graphics.*
import android.os.*
import android.widget.*
import android.view.View
import android.view.WindowManager
import android.provider.Settings
import java.nio.ByteBuffer
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.*
import java.util.concurrent.Executors

class MainActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private lateinit var status:TextView;private lateinit var output:TextView;private lateinit var preview:ImageView
 private lateinit var choose:Button;private lateinit var run:Button;private lateinit var mode:Spinner
 private lateinit var cameraStatus:TextView
 private lateinit var watcher:C110PhysicalWatcher
 private var photoEpoch=0;private var sourceMeta="";private var sourceKind="album"
 private var source:Bitmap?=null;private var reader:Reader?=null;private var readerThreads=0
 private var db:DbLocator?=null;private var dbThreads=0
 private lateinit var threads:Spinner;private lateinit var dbSize:Spinner
 private lateinit var raw:TextView;private lateinit var voiceStatus:TextView
 private lateinit var replay:Button;private lateinit var stopVoice:Button
 private var tts:TextToSpeech?=null;private var ttsReady=false;private var ttsFailed=false
 private var serial=0;private var lastIndex=0;private var speechOrigin=0L;private var queuedAt=0L
 private var lastText="";private var pending:Pair<String,Long>?=null;private var testStart=0L
 private fun now()=SystemClock.elapsedRealtime()
 private fun voiceNote(s:String){voiceStatus.text=s;output.append("\nTTS "+s+"\n")}
 private fun stopSpeech(){serial++;pending=null;tts?.stop();voiceStatus.text="朗读已停止"}
 private fun initSpeech(){
  tts=TextToSpeech(this){result->runOnUiThread{
   if(isDestroyed)return@runOnUiThread
   ttsReady=result==TextToSpeech.SUCCESS;ttsFailed=!ttsReady
   if(ttsReady){
    tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
     override fun onStart(id:String){val at=now();runOnUiThread{
      if(!isDestroyed&&id==serial.toString()+":0")voiceNote("首段开始回调："+(at-speechOrigin)+" ms；入队至回调："+(at-queuedAt)+" ms")
     }}
     override fun onDone(id:String){val at=now();runOnUiThread{
      if(!isDestroyed&&id==serial.toString()+":"+lastIndex)voiceNote("朗读完成回调："+(at-speechOrigin)+" ms")
     }}
     override fun onError(id:String){onError(id,TextToSpeech.ERROR)}
     override fun onError(id:String,errorCode:Int){runOnUiThread{
      if(!isDestroyed&&id.startsWith(serial.toString()+":")){stopSpeech();voiceNote("系统 TTS 错误："+errorCode)}
     }}
    })
    voiceStatus.text="系统 TTS 已就绪"
    val p=pending;pending=null;if(p!=null)speak(p.first,p.second)
   }else{pending=null;voiceNote("系统 TTS 初始化失败，请检查手机文字转语音设置")}
  }}
 }
 private fun speak(text:String,origin:Long){
  if(text.isBlank())return
  stopSpeech()
  if(ttsFailed){voiceNote("系统 TTS 不可用");return}
  if(!ttsReady){pending=Pair(text,origin);voiceStatus.text="等待系统 TTS 初始化";return}
  val engine=tts?:return
  val chunks=SpeechChunks.split(text,TextToSpeech.getMaxSpeechInputLength())
  if(chunks.isEmpty())return
  speechOrigin=origin;queuedAt=now();lastIndex=chunks.lastIndex
  voiceNote("开始入队；点击至入队："+(queuedAt-origin)+" ms；段数："+chunks.size)
  for((i,chunk) in chunks.withIndex()){
   val code=engine.speak(chunk,if(i==0)TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,serial.toString()+":"+i)
   if(code!=TextToSpeech.SUCCESS){stopSpeech();voiceNote("系统 TTS 入队失败："+code);break}
  }
 }
 override fun onResume(){super.onResume();if(::watcher.isInitialized&&cameraPrefs().getBoolean(C110PhysicalPolicy.ENABLE,false)){window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);watcher.start()}}
 override fun onPause(){if(::watcher.isInitialized)watcher.stop();window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onPause()}
 private fun cameraPrefs()=getSharedPreferences("kanzi_settings",MODE_PRIVATE)
 override fun onDestroy(){if(::watcher.isInitialized)watcher.shutdown();serial++;pending=null;tts?.stop();tts?.shutdown();tts=null;worker.execute{runCatching{db?.close()};runCatching{reader?.close()}};worker.shutdown();super.onDestroy()}
 override fun onCreate(state:Bundle?){super.onCreate(state)
  val scroll=ScrollView(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,48,24,32)}
  scroll.addView(box);setContentView(scroll)
  fun label(t:String)=TextView(this).apply{text=t;textSize=18f;box.addView(this)}
  label("蓝星 OpenOCR 原图与C110实验 v0.1.2 · 基于 v0.4.8")
  Button(this).apply{text="手机实时识别（原1080p路线）";box.addView(this);setOnClickListener{if(choose.isEnabled){stopSpeech();startActivity(Intent(this@MainActivity,LiveActivity::class.java).putExtra("official",mode.selectedItemPosition==0).putExtra("threads",listOf(4,2,6)[threads.selectedItemPosition]).putExtra("longSide",DbResolution.sizes[dbSize.selectedItemPosition]))}}}
  choose=Button(this).apply{text="选择相册原图";box.addView(this);setOnClickListener{stopSpeech();startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)}}
  label("相册 / C110：原图分辨率识别，不缩小、不放大")
  Button(this).apply{text="连接C110相机 Wi-Fi";box.addView(this);setOnClickListener{startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))}}
  Button(this).apply{text="开启C110实体快门自动接收";box.addView(this);setOnClickListener{
   cameraPrefs().edit().putBoolean(C110PhysicalPolicy.ENABLE,true).apply()
   window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);watcher.start()
  }}
  Button(this).apply{text="停止C110接收";box.addView(this);setOnClickListener{
   cameraPrefs().edit().putBoolean(C110PhysicalPolicy.ENABLE,false).apply();watcher.stop()
   window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);cameraStatus.text="C110接收已停止"
  }}
  cameraStatus=label("C110：先连接相机Wi-Fi，再开启接收；保持本页前台")
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;box.addView(this)}
  dbSize=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,DbResolution.sizes.map{DbResolution.label(it)});setSelection(DbResolution.defaultIndex);box.addView(this)}
  mode=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("官方原框（还原实验）","智能主阅读区（保留原版）"));box.addView(this)}
  threads=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("CPU 4线程（默认）","CPU 2线程","CPU 6线程"));box.addView(this)}
  run=Button(this).apply{text="开始识别 / 同图复测";box.addView(this);setOnClickListener{test()}}
  status=label("请选择图片")
  voiceStatus=label("系统 TTS 初始化中")
  replay=Button(this).apply{text="重新朗读原文";isEnabled=false;box.addView(this);setOnClickListener{speak(lastText,now())}}
  stopVoice=Button(this).apply{text="停止朗读";box.addView(this);setOnClickListener{stopSpeech()}}
  Button(this).apply{text="复制结果及诊断";box.addView(this);setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OpenOCR",output.text));Toast.makeText(this@MainActivity,"已复制",Toast.LENGTH_SHORT).show()}}
  raw=label("");raw.setTextIsSelectable(true)
  output=TextView(this)
  initSpeech()
  watcher=C110PhysicalWatcher(this,object:C110PhysicalWatcher.PhotoSink{
   override fun cancelPhysicalRecognition(){photoEpoch++;stopSpeech()}
   override fun acceptPhysicalPhoto(photo:C110WifiCamera.Photo,queryMs:Long,downloadMs:Long,eventGap:Int,voiceWaitMs:Long,onSuccess:Runnable?):Boolean{
    if(isDestroyed||!choose.isEnabled)return false
    val epoch=photoEpoch
    val meta="jpeg_bytes=${photo.jpeg.size} camera_jpeg=${photo.width}x${photo.height} event_gap_ms=$eventGap query_ms=$queryMs download_ms=$downloadMs event_finish_to_download_ms=${photo.totalMs} defer_ms=$voiceWaitMs"
    cameraStatus.text="收到C110原图 ${photo.width}×${photo.height}，自动识别"
    loadSource("c110",meta,epoch){ImageDecoder.createSource(ByteBuffer.wrap(photo.jpeg))}
    return true
   }
  },{false},{!choose.isEnabled},{stopSpeech()},{message->cameraStatus.text=message})
 }
 override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req!=9||res!=RESULT_OK)return;val uri=data?.data?:return
  loadSource("album","",null){ImageDecoder.createSource(contentResolver,uri)}
 }
 private fun loadSource(kind:String,meta:String,autoEpoch:Int?,input:()->ImageDecoder.Source){
  busy(true);val received=now()
  worker.execute{
   var decoded:Bitmap?=null
   try{
    val bm=ImageDecoder.decodeBitmap(input()){d,_,_->d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE}
    decoded=bm
    val decodeMs=now()-received
    db?.clearProbability()
    runOnUiThread{
     if(isDestroyed){bm.recycle();return@runOnUiThread}
     preview.setImageBitmap(bm);source?.recycle();source=bm;sourceKind=kind;sourceMeta="$meta decode_ms=$decodeMs"
     lastText="";raw.text="";output.text="";replay.isEnabled=false
     status.text="原图 "+bm.width+"×"+bm.height;busy(false)
     if(autoEpoch!=null&&autoEpoch==photoEpoch)test(autoEpoch,received)
    }
   }catch(t:Throwable){decoded?.recycle();runOnUiThread{if(!isDestroyed){status.text=if(t is OutOfMemoryError)"原图超出当前设备可用内存，无法解码" else t.toString();busy(false)}}}
  }
 }
 private fun busy(v:Boolean){choose.isEnabled=!v;run.isEnabled=!v;mode.isEnabled=!v;threads.isEnabled=!v;dbSize.isEnabled=!v}
 private fun test(autoEpoch:Int?=null,received:Long?=null){
  val bm=source?:return
  val inputMeta=sourceMeta;val inputKind=sourceKind
  val official=mode.selectedItemPosition==0
  val name=if(official)"官方原框" else "智能主阅读区（原版）"
  val nt=listOf(4,2,6)[threads.selectedItemPosition]
  val longSide=DbResolution.sizes[dbSize.selectedItemPosition]
  stopSpeech();lastText="";raw.text="";replay.isEnabled=false;testStart=now();val clicked=testStart
  busy(true);output.text="";status.text="OpenOCR RepViT-DB 定位中"
  worker.execute{
   try{
    runOnUiThread{output.append("PIXEL source_type=$inputKind original=${bm.width}x${bm.height} requested_cap=0 input=${bm.width}x${bm.height} input_pixels=${bm.width.toLong()*bm.height} resize_ms=0 $inputMeta\n")}
    val start=now()
    if(db==null||dbThreads!=nt){db?.close();db=null;db=DbLocator(this,nt);dbThreads=nt}
    db!!.clearProbability();val init=now();val found=db!!.locate(bm,longSide,official);val detected=now()
    val diagnostic=db!!.lastDiagnostic
    runOnUiThread{output.append(diagnostic+"\n"+found.mapIndexed{i,q->"raw=$i det=${q.score} quad=${q.points.contentToString()}"}.joinToString("\n")+"\n")}
    require(found.isNotEmpty()){"未检测到文字"}
    val focusStart=now()
    val plan=if(official)null else FocusPlanner.plan(found.map{q->q.rect.let{FocusPlanner.R(it.left,it.top,it.right,it.bottom,q.score)}},bm.width,bm.height)
    val focusMs=now()-focusStart
    val roi=plan?.roi?.let{Rect(floor(it.l).toInt(),floor(it.t).toInt(),ceil(it.r).toInt(),ceil(it.b).toInt())}
    val boxes=found.filter{b->roi==null||roi.contains(b.rect.centerX().toInt(),b.rect.centerY().toInt())}
    val outside=found.filter{it !in boxes}
    val pStart=now()
    val columns=if(official)null else RowPlanner.plan(boxes,bm.width,bm.height,1.6f,true)
    val rows=columns?.rows?:OfficialGeometry.order(boxes).map{listOf(it)}
    val pMs=now()-pStart
    val load=now();if(reader==null||readerThreads!=nt){reader?.close();reader=null;reader=Reader(this,"rep.onnx",nt);readerThreads=nt}
    val engine=reader!!;val loadMs=now()-load
    runOnUiThread{output.append("Field TTS v0.4.8 source=${bm.width}x${bm.height} detector_threads=$nt rec_threads=$nt detector_init_ms=${init-start} detect_ms=${detected-init} recognizer_load_ms=$loadMs raw_boxes=${found.size} focus_plan_ms=$focusMs focus_reason=${plan?.reason?:"official_bypass"} rec_shape=${engine.inputShape} rec_batch=${if(official)engine.officialBatchSize else 1}\nmode=$name roi=$roi selected=${boxes.size} outside=${outside.size} rows=${rows.size} crops=${rows.sumOf{it.size}} duplicates=${columns?.removed?:0} plan_ms=$pMs\n"+(if(outside.isNotEmpty())"提示：区域外${outside.size}个检测框未识别，可切换官方原框。\n" else "")+outside.joinToString("\n"){q->"outside det=${q.score} quad=${q.points.contentToString()}"}+"\n")}
    val scale=min(1f,1200f/max(bm.width,bm.height))
    val scaled=Bitmap.createScaledBitmap(bm,(bm.width*scale).toInt(),(bm.height*scale).toInt(),true)
    val shown=scaled.copy(Bitmap.Config.ARGB_8888,true)
    if(scaled!==bm)scaled.recycle()
    Canvas(shown).apply{
     scale(scale,scale);val paint=Paint().apply{style=Paint.Style.STROKE;strokeWidth=3/scale}
     paint.color=Color.GRAY;outside.forEach{drawRect(it.rect,paint)}
     paint.color=Color.YELLOW;boxes.forEach{drawRect(it.rect,paint)}
     if(roi!=null){paint.color=Color.GREEN;drawRect(roi,paint)}
     paint.color=Color.CYAN;rows.flatten().forEach{b->val path=Path();path.moveTo(b.points[0],b.points[1]);for(i in 1..3)path.lineTo(b.points[2*i],b.points[2*i+1]);path.close();drawPath(path,paint)}
    }
    runOnUiThread{preview.setImageBitmap(shown)}
    val begin=now();var count=0;var uncertain=0;var blank=0;var dropped=0;var first=-1L
    val lines=mutableListOf<String>();val rawLines=mutableListOf<String>();val notes=mutableListOf<String>()
    val rgba=Mat();val bgr=Mat()
    try{
     if(official){Utils.bitmapToMat(bm,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);rgba.release()}
     // Bounded batches preserve official ordering and avoid holding all crops in memory.
     val batch=if(official)engine.officialBatchSize else 1
     for(index in rows.indices step batch){
      val chunk=rows.subList(index,min(rows.size,index+batch))
      val answers=if(official)engine.readOfficial(bgr,chunk.flatten())else emptyList()
      var ai=0
      for((ri,row) in chunk.withIndex()){
       val parts=mutableListOf<String>();val rawParts=mutableListOf<String>()
       for((ci,b) in row.withIndex()){
        val ans=if(official)answers[ai++]else engine.read(bm,b,true)
        val suspect=if(official)ans.confidence<.5f else ans.text.isBlank()||ans.confidence<.75f||ans.minConfidence<.65f||(ans.text.count{!it.isWhitespace()}<=2&&b.score<.55f&&ans.confidence<.8f)
        val keep=!official||ans.confidence>=.5f
        if(!keep)dropped++
        if(ans.text.isNotBlank()&&keep)rawParts.add(ans.text)
        count++;if(suspect)uncertain++;if(ans.text.isBlank())blank++
        parts.add(if(suspect)"[待核:${ans.text}]"else ans.text.ifBlank{"[未识别]"})
        notes.add("cell row=${index+ri+1} col=${ci+1} quad=${b.points.contentToString()} det=${b.score} rec=${ans.confidence} min=${ans.minConfidence} width=${ans.width} suspect=$suspect keep=$keep text=${ans.text}")
       }
       if(rawParts.isNotEmpty())rawLines.add(rawParts.joinToString(" "))
       if(parts.isNotEmpty())lines.add(parts.joinToString(" | "))
       if(first<0)first=now()-begin
      }
     }
    }finally{rgba.release();bgr.release()}
    val rec=now()-begin;val total=detected-init+pMs+rec+focusMs
    val original=rawLines.joinToString("\n");val done=now()
    runOnUiThread{if(!isDestroyed){
     output.append("\n$name detect_ms=${detected-init} first_row_ms=$first rec_ms=$rec total_ms=$total crops=$count suspect=$uncertain blank=$blank official_dropped=$dropped pss_kb=${Debug.getPss()}\n"+lines.joinToString("\n")+"\n"+notes.joinToString("\n")+"\nOCR 点击至原文就绪：${done-clicked} ms\n")
     raw.text=original;lastText=original;replay.isEnabled=original.isNotBlank()
     if(received!=null)output.append("C110 receive_to_ocr_ready_ms=${done-received}\n")
     if(autoEpoch==null||autoEpoch==photoEpoch)speak(original,received?:clicked)
     else output.append("C110 stale speech suppressed\n")
     status.text="识别完成";busy(false)
    }}
   }catch(t:Throwable){runOnUiThread{if(!isDestroyed){output.append("\n失败："+t.toString());status.text=if(t is OutOfMemoryError)"原图超出当前设备可用内存，无法识别" else "失败";busy(false)}}}
  }
 }
}
