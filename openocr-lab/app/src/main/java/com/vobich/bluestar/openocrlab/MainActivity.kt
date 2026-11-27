package com.vobich.bluestar.openocrlab
import android.app.Activity
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.content.*
import android.graphics.*
import android.os.*
import android.widget.*
import android.view.View
import kotlin.math.*
import java.util.concurrent.Executors

class MainActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private lateinit var status:TextView;private lateinit var output:TextView;private lateinit var preview:ImageView
 private lateinit var choose:Button;private lateinit var run:Button;private lateinit var mode:Spinner;private lateinit var detectorPicker:Spinner
 private var source:Bitmap?=null;private var locator:TextLocator?=null;private var reader:Reader?=null;private var readerThreads=0
 private var db:DbLocator?=null;private var dbThreads=0
 private lateinit var threads:Spinner;private lateinit var dbSize:Spinner;private lateinit var dbProfile:Spinner
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
 override fun onDestroy(){serial++;pending=null;tts?.stop();tts?.shutdown();tts=null;worker.execute{runCatching{locator?.close()};runCatching{db?.close()};runCatching{reader?.close()}};worker.shutdown();super.onDestroy()}
 override fun onCreate(state:Bundle?){super.onCreate(state)
  val scroll=ScrollView(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,48,24,32)}
  scroll.addView(box);setContentView(scroll)
  fun label(t:String)=TextView(this).apply{text=t;textSize=18f;box.addView(this)}
  label("蓝星 OpenOCR v0.4.5 · DB 画框性能对照")
  choose=Button(this).apply{text="选择相册原图";box.addView(this);setOnClickListener{stopSpeech();startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)}}
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;box.addView(this)}
  detectorPicker=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("OpenOCR RepViT-DB（默认）","YOLO26n-Text（原版）"));box.addView(this)}
  dbSize=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("DB 长边 960（基线）","DB 长边 1280","DB 长边 1600"));box.addView(this)}
  dbProfile=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("DB 原始参数（基线）","DB 小字短边 2 / 4","DB 紧框扩张 1.2","DB 概率阈值 0.25","DB 概率阈值 0.35"));box.addView(this)}
  mode=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("全图原框（默认）","智能主阅读区","原v0.1.3区域基线","全图完整列裁剪","同图区域对照（新/旧/全图）"));box.addView(this)}
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
 }
 override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req!=9||res!=RESULT_OK)return;val uri=data?.data?:return
  busy(true);worker.execute{
   try{val bm=ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,uri)){d,i,_->
    require(i.size.width.toLong()*i.size.height<=16_000_000L){"图片超过1600万像素"};d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE}
    db?.clearProbability()
    runOnUiThread{preview.setImageBitmap(bm);source?.recycle();source=bm;lastText="";raw.text="";output.text="";replay.isEnabled=false;status.text="原图 "+bm.width+"×"+bm.height;busy(false)}
   }catch(t:Throwable){runOnUiThread{status.text=t.toString();busy(false)}}
  }
 }
 private fun busy(v:Boolean){choose.isEnabled=!v;run.isEnabled=!v;mode.isEnabled=!v;threads.isEnabled=!v;detectorPicker.isEnabled=!v;dbSize.isEnabled=!v;dbProfile.isEnabled=!v}
 private fun test(){
  val bm=source?:return;val selected=if(mode.selectedItemPosition==0)4 else mode.selectedItemPosition-1;val useDb=detectorPicker.selectedItemPosition==0;val detectorName=if(useDb)"OpenOCR RepViT-DB" else "YOLO26n-Text";val nt=listOf(4,2,6)[threads.selectedItemPosition];val longSide=listOf(960,1280,1600)[dbSize.selectedItemPosition];val profile=DbProfile.profiles[dbProfile.selectedItemPosition]
  stopSpeech();lastText="";raw.text="";replay.isEnabled=false;testStart=now();val clicked=testStart
  busy(true);output.text="";status.text="$detectorName 定位中"
  worker.execute{
   try{
    val start=now()
    if(useDb){
     locator?.close();locator=null
     if(db==null||dbThreads!=nt){db?.close();db=null;db=DbLocator(this,nt);dbThreads=nt}
    }else{
     db?.close();db=null
     if(locator==null)locator=TextLocator(this)
    }
    val init=now()
    val found=if(useDb)TextLocator.group(db!!.locate(bm,longSide,profile),bm.width,bm.height)else locator!!.locate(bm)
    val detected=now()
    if(useDb){val diagnostic=db!!.lastDiagnostic;runOnUiThread{output.append(diagnostic+"\n")}}
    require(found.boxes.isNotEmpty()){"未检测到文字"}
    runOnUiThread{output.append(found.boxes.mapIndexed{i,q->"raw=$i det=${q.score} quad=${q.points.contentToString()}"}.joinToString("\n")+"\n")}
    val focusStart=now()
    val plan=FocusPlanner.plan(found.boxes.map{q->q.rect.let{FocusPlanner.R(it.left,it.top,it.right,it.bottom,q.score)}},bm.width,bm.height)
    val focusMs=now()-focusStart
    val focus=plan.roi?.let{Rect(floor(it.l).toInt(),floor(it.t).toInt(),ceil(it.r).toInt(),ceil(it.b).toInt())}
    val modes=if(selected==3)listOf(0,1,2)else listOf(selected)
    val load=now();if(reader==null||readerThreads!=nt){reader?.close();reader=null;reader=Reader(this,"rep.onnx",nt);readerThreads=nt}
    val engine=reader!!;val loadMs=now()-load
    runOnUiThread{output.append("Field TTS v0.4.5 detector=$detectorName source=${bm.width}x${bm.height} detector_threads=${if(useDb)nt else 4} rec_threads=$nt detector_init_ms=${init-start} detect_ms=${detected-init} recognizer_load_ms=$loadMs raw_boxes=${found.boxes.size} focus_plan_ms=$focusMs focus_reason=${plan.reason} groups=${plan.groups} best=${plan.best} rival=${plan.rival}\n")}
    for(m in modes){
     val name=when(m){4->"全图原框";else->listOf("智能主阅读区","原v0.1.3区域","全图")[m]}
     val roi=when(m){0->focus;1->found.roi;else->null}
     val boxes=found.boxes.filter{b->roi==null||roi.contains(b.rect.centerX().toInt(),b.rect.centerY().toInt())}
     val outside=found.boxes.filter{it !in boxes}
     val pStart=now()
     val columns=if(m==4)null else RowPlanner.plan(boxes,bm.width,bm.height,1.6f,true)
     val rows=columns?.rows?:MixedLayout.dedup(boxes).sortedWith(compareBy<TextLocator.Box>{it.rect.top}.thenBy{it.rect.left}).map{listOf(it)}
     val pMs=now()-pStart
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
     runOnUiThread{preview.setImageBitmap(shown);output.append("\nmode=$m $name roi=$roi selected=${boxes.size} outside=${outside.size} rows=${rows.size} crops=${rows.sumOf{it.size}} duplicates=${columns?.removed ?: (boxes.size-rows.sumOf{it.size})} plan_ms=$pMs\n"+(if(outside.isNotEmpty())"提示：区域外${outside.size}个检测框未识别，可切换全图模式；灰框为未读区域。\n" else "")+outside.joinToString("\n"){q->"outside det=${q.score} quad=${q.points.contentToString()}"}+"\n")}
     for(round in 1..(if(selected==3)2 else 1)){
      val begin=now();var first=-1L;var count=0;var uncertain=0;var blank=0
      val lines=mutableListOf<String>();val notes=mutableListOf<String>();val rawLines=mutableListOf<String>()
      for((ri,row) in rows.withIndex()){
       val parts=mutableListOf<String>();val rawParts=mutableListOf<String>()
       for((ci,b) in row.withIndex()){
        val ans=if(m==4&&b.rect.height()>b.rect.width()*2.2f)MixedLayout.verticalRead(bm,b,engine,false)else engine.read(bm,b,m!=4)
        if(ans.text.isNotBlank())rawParts.add(ans.text)
        val oldSuspect=FineRead.suspicious(ans,b.score)
        val suspect=if(m==4)oldSuspect||ans.text.isBlank()||ans.confidence<.4f else oldSuspect||(m!=1&&(ans.text.isBlank()||ans.confidence<.75f||ans.minConfidence<.65f))
        count++;if(suspect)uncertain++;if(ans.text.isBlank())blank++
        if(m!=1||!oldSuspect)parts.add(if(suspect)"[待核:${ans.text}]"else ans.text.ifBlank{"[未识别]"})
        notes.add("cell row=${ri+1} col=${ci+1} quad=${b.points.contentToString()} det=${b.score} rec=${ans.confidence} min=${ans.minConfidence} suspect=$suspect text=${ans.text}")
       }
       if(rawParts.isNotEmpty())rawLines.add(rawParts.joinToString(" "))
       if(parts.isNotEmpty())lines.add(parts.joinToString(" | "));if(first<0)first=now()-begin
      }
      val rec=now()-begin;val total=detected-init+pMs+rec+(if(m==0)focusMs else 0)
      runOnUiThread{output.append("\n$name round=$round detect_ms=${detected-init} first_row_ms=$first rec_ms=$rec total_ms=$total crops=$count suspect=$uncertain blank=$blank pss_kb=${Debug.getPss()}\n"+lines.joinToString("\n")+"\n"+notes.joinToString("\n")+"\n")}
      if(round==1){
       val original=rawLines.joinToString("\n");val ocrDone=now()
       runOnUiThread{
        if(!isDestroyed){
         output.append("\nOCR 点击至原文就绪："+(ocrDone-clicked)+" ms\n")
         if(selected==3){raw.append(name+"\n"+original+"\n\n")}
         else{raw.text=original;lastText=original;replay.isEnabled=original.isNotBlank();speak(original,clicked)}
        }
       }
      }
     }
    }
    runOnUiThread{status.text=if(selected==3)"对照完成（不自动朗读）" else "识别完成";busy(false)}
   }catch(t:Throwable){runOnUiThread{output.append("\n失败："+t.toString());status.text="失败";busy(false)}}
  }
 }
}
