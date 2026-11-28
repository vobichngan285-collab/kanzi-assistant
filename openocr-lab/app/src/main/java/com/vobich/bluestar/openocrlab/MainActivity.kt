package com.vobich.bluestar.openocrlab
import android.app.Activity
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.content.*
import android.graphics.*
import android.os.*
import android.widget.*
import android.view.View
import android.view.WindowInsets
import java.util.UUID
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.*
import java.util.concurrent.Executors

class MainActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private lateinit var status:TextView;private lateinit var output:TextView;private lateinit var preview:ImageView
 private lateinit var choose:Button;private lateinit var run:Button;private lateinit var mode:Spinner
 private var source:Bitmap?=null;private var reader:Reader?=null;private var readerThreads=0
 private var db:DbLocator?=null;private var dbThreads=0
 private lateinit var threads:Spinner;private lateinit var dbSize:Spinner
 private lateinit var backend:Spinner
 private lateinit var history:ExperimentHistory
 private var sampleId=""
 private var currentRun=""
 private var speechRun=""
 private var gpu:GpuClient?=null
 private var gpuThreads=0
 private var busyNow=false
 private data class Reference(val run:String,val geometry:List<String>,val values:List<Reader.Answer>,val keep:List<Boolean>,val complete:Long)
 private val references=object:LinkedHashMap<String,Reference>(){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,Reference>?):Boolean=size>8}
 private lateinit var raw:TextView;private lateinit var voiceStatus:TextView
 private lateinit var replay:Button;private lateinit var stopVoice:Button
 private var tts:TextToSpeech?=null;private var ttsReady=false;private var ttsFailed=false
 private var serial=0;private var lastIndex=0;private var speechOrigin=0L;private var queuedAt=0L
 private var lastText="";private var pending:Pair<String,Long>?=null;private var testStart=0L
 private fun now()=SystemClock.elapsedRealtime()
 private fun voiceNote(s:String){voiceStatus.text=s;output.append("\nTTS "+s+"\n");if(speechRun.isNotEmpty())runCatching{history.append("TTS run="+speechRun+" "+s+" callback_only=1")}.onFailure{status.text="诊断写入失败："+it.message}}
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
 override fun onDestroy(){serial++;pending=null;tts?.stop();tts?.shutdown();tts=null;worker.execute{runCatching{db?.close()};runCatching{reader?.close()};runCatching{gpu?.close()}};worker.shutdown();super.onDestroy()}
 override fun onCreate(state:Bundle?){super.onCreate(state)
  val scroll=ScrollView(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,48,24,32)}
  scroll.addView(box);setContentView(scroll)
  history=ExperimentHistory(this)
  if(Build.VERSION.SDK_INT>=30)box.setOnApplyWindowInsetsListener{v,i->val bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(24+bars.left,16+bars.top,24+bars.right,16+bars.bottom);i}
  fun label(t:String)=TextView(this).apply{text=t;textSize=18f;box.addView(this)}
  label("蓝星 OpenOCR CPU＋GPU v0.1.3\n静态照片协同识别实验 · 基于v0.4.8")
  choose=Button(this).apply{text="选择相册原图";box.addView(this);setOnClickListener{stopSpeech();startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)}}
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;box.addView(this)}
  dbSize=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,DbResolution.sizes.map{DbResolution.label(it)});setSelection(DbResolution.defaultIndex);box.addView(this)}
  mode=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("官方原框（还原实验）","智能主阅读区（保留原版）"));box.addView(this)}
  threads=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("CPU 4线程（默认）","CPU 2线程","CPU 6线程"));box.addView(this)}
  backend=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("原版 ORT CPU（原宽度对照）","CPU＋GPU 协同（固定宽度）","GPU单独识别（固定宽度）","ORT CPU（固定宽度精度对照）","GPU原始形状（精度诊断）","GPU原宽度（仅尾批补齐）"));box.addView(this)}
  run=Button(this).apply{text="开始识别 / 同图复测";box.addView(this);setOnClickListener{test()}}
  status=label("请选择图片")
  voiceStatus=label("系统 TTS 初始化中")
  replay=Button(this).apply{text="重新朗读原文";isEnabled=false;box.addView(this);setOnClickListener{speechRun=currentRun+"/replay";speak(lastText,now())}}
  stopVoice=Button(this).apply{text="停止朗读";box.addView(this);setOnClickListener{stopSpeech()}}
  Button(this).apply{text="复制全部照片实验日志";box.addView(this);setOnClickListener{
   try{val all=history.allForClipboard()
    if(all==null)Toast.makeText(this@MainActivity,"日志超过安全剪贴板容量，请使用导出完整日志",Toast.LENGTH_LONG).show()
    else copy(all)
   }catch(t:Throwable){status.text="复制失败："+t.message}
  }}
  Button(this).apply{text="复制当前结果及诊断";box.addView(this);setOnClickListener{copy(output.text.toString()+"\n原文：\n"+raw.text)}}
  Button(this).apply{text="导出完整累计日志（TXT）";box.addView(this);setOnClickListener{
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="text/plain";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"OpenOCR_CPU_GPU_history.txt")},11)
  }}
  Button(this).apply{text="清空累计日志";box.addView(this);setOnClickListener{
   if(!busyNow)android.app.AlertDialog.Builder(this@MainActivity).setMessage("清空全部历史实验记录？").setNegativeButton("取消",null).setPositiveButton("清空"){_,_->if(!busyNow){stopSpeech();speechRun="";runCatching{history.clear()}.onSuccess{status.text="历史日志已清空"}.onFailure{status.text=it.toString()}}}.show()
   else Toast.makeText(this@MainActivity,"请在识别完成后清空",Toast.LENGTH_SHORT).show()
  }}
  raw=label("");raw.setTextIsSelectable(true)
  output=TextView(this)
  initSpeech()
 }
 private fun copy(s:String){
  if(s.toByteArray(Charsets.UTF_8).size>300000){Toast.makeText(this,"内容过长，请导出完整日志",Toast.LENGTH_LONG).show();return}
  runCatching{(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OpenOCR",s))}.onSuccess{Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show()}.onFailure{status.text="复制失败："+it.message}
 }
 override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req==11&&res==RESULT_OK){val uri=data?.data?:return;worker.execute{try{val out=contentResolver.openOutputStream(uri)?:error("无法打开导出文件");out.use{history.copyTo(it)};runOnUiThread{status.text="已导出完整历史日志"}}catch(t:Throwable){runOnUiThread{status.text="导出失败："+t.message}}};return}
  if(req!=9||res!=RESULT_OK)return;val uri=data?.data?:return
  busy(true);worker.execute{
   try{val hash=contentResolver.openInputStream(uri)?.use{ExperimentHistory.sampleHash(it)}?:error("无法读取原图");val bm=ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,uri)){d,i,_->
    require(i.size.width.toLong()*i.size.height<=16_000_000L){"图片超过1600万像素"};d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE}
    db?.clearProbability()
    runOnUiThread{if(isDestroyed){bm.recycle();return@runOnUiThread};preview.setImageBitmap(bm);source?.recycle();source=bm;sampleId=hash;lastText="";raw.text="";output.text="";replay.isEnabled=false;status.text="原图 "+bm.width+"×"+bm.height+" · "+hash.take(12)+" · 累计日志 "+history.size()+" bytes";busy(false)}
   }catch(t:Throwable){runOnUiThread{status.text=t.toString();busy(false)}}
  }
 }
 private fun busy(v:Boolean){busyNow=v;choose.isEnabled=!v;run.isEnabled=!v;mode.isEnabled=!v;threads.isEnabled=!v;dbSize.isEnabled=!v;backend.isEnabled=!v}
 private fun test(){
  if(busyNow)return
  val clicked=now();val bm=source?:return
  val official=mode.selectedItemPosition==0
  val name=if(official)"官方原框" else "智能主阅读区（原版）"
  val nt=listOf(4,2,6)[threads.selectedItemPosition]
  val longSide=DbResolution.sizes[dbSize.selectedItemPosition]
  val selected=backend.selectedItemPosition
  val backendName=listOf("ORT_CPU","CPU_GPU","GPU_ONLY","ORT_CPU_FIXED","GPU_EXACT","GPU_TAIL_FIXED")[selected]
  val coopMode=when(selected){1->CoopMode.MIXED_FIXED;2->CoopMode.GPU_FIXED;3->CoopMode.CPU_FIXED;4->CoopMode.GPU_EXACT;5->CoopMode.GPU_TAIL_FIXED;else->null}
  val needsGpu=coopMode!=null&&!coopMode.cpuOnly
  val runId=UUID.randomUUID().toString();currentRun=runId;speechRun=""
  val sample=sampleId
  val header="BEGIN run=$runId sample_sha256=$sample backend=$backendName mode=$name source=${bm.width}x${bm.height} db=$longSide cpu_threads=$nt device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} time_ms=${System.currentTimeMillis()} db_cache=forced_miss thermal_start="+(if(Build.VERSION.SDK_INT>=29)(getSystemService(POWER_SERVICE) as PowerManager).currentThermalStatus else -1)
  try{history.append(header.replaceFirst("BEGIN","START"))}catch(t:Throwable){status.text="无法保存诊断："+t.message;return}
  stopSpeech();lastText="";raw.text="";replay.isEnabled=false;testStart=clicked
  busy(true);output.text="";status.text="OpenOCR RepViT-DB 定位中"
  worker.execute{
   val details=StringBuilder(header).append("\n")
   fun note(s:String){details.append(s).append("\n")}
   try{
    val start=now()
    if(db==null||dbThreads!=nt){db?.close();db=null;db=DbLocator(this,nt);dbThreads=nt}
    db!!.clearProbability()
    val init=now();val found=db!!.locate(bm,longSide,official);val detected=now()
    val diagnostic=db!!.lastDiagnostic
    note(diagnostic+"\n"+found.mapIndexed{i,q->"raw=$i det=${q.score} quad=${q.points.contentToString()}"}.joinToString("\n"))
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
    note("Field CPU_GPU v0.1.3 source=${bm.width}x${bm.height} detector_threads=$nt rec_threads=$nt detector_init_ms=${init-start} detect_ms=${detected-init} recognizer_load_ms=$loadMs raw_boxes=${found.size} focus_plan_ms=$focusMs focus_reason=${plan?.reason?:"official_bypass"} rec_shape=${engine.inputShape} rec_batch=${if(official)engine.officialBatchSize else 1}\nmode=$name roi=$roi selected=${boxes.size} outside=${outside.size} rows=${rows.size} crops=${rows.sumOf{it.size}} duplicates=${columns?.removed?:0} plan_ms=$pMs\n"+(if(outside.isNotEmpty())"提示：区域外${outside.size}个检测框未识别，可切换官方原框。\n" else "")+outside.joinToString("\n"){q->"outside det=${q.score} quad=${q.points.contentToString()}"}+"\n")
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
    runOnUiThread{if(!isDestroyed)preview.setImageBitmap(shown)else shown.recycle()}
    if(needsGpu){
     val t=now();val existing=gpu!=null&&gpuThreads==nt
     if(!existing){gpu?.close();gpu=null;gpu=GpuClient(applicationContext,nt);gpuThreads=nt}
     note("GPU_INIT reused=$existing prepare_ms=${now()-t} "+(if(existing)"resident_session=1 initial_load_detail="+gpu!!.loadDetail else gpu!!.loadDetail))
     note("HOST_PROJECTION "+assets.open("compact-report.json").bufferedReader().use{it.readText()}.replace("\n"," "))
    }else note("GPU_RESIDENT="+(gpu!=null))
    val begin=now();var count=0;var uncertain=0;var blank=0;var dropped=0;var first=-1L
    val lines=mutableListOf<String>();val rawLines=mutableListOf<String>();val notes=mutableListOf<String>()
    val compared=mutableListOf<Reader.Answer>();val keepFlags=mutableListOf<Boolean>()
    val rgba=Mat();val bgr=Mat()
    try{
     if(official){Utils.bitmapToMat(bm,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);rgba.release()}
     // Bounded batches preserve official ordering and avoid holding all crops in memory.
     val batch=if(official)engine.officialBatchSize else 1
     val parallel=coopMode?.let{engine.cooperate(bm,bgr,rows.flatten(),official,gpu,it,::note)}
     var cooperativeIndex=0
     for(index in rows.indices step batch){
      val batchAt=now()
      val chunk=rows.subList(index,min(rows.size,index+batch))
      val answers=if(official&&parallel==null)engine.readOfficial(bgr,chunk.flatten())else emptyList()
      var ai=0
      for((ri,row) in chunk.withIndex()){
       val parts=mutableListOf<String>();val rawParts=mutableListOf<String>()
       for((ci,b) in row.withIndex()){
        val ans=if(parallel!=null)parallel[cooperativeIndex++]else if(official)answers[ai++]else engine.read(bm,b,true)
        val suspect=if(official)ans.confidence<.5f else ans.text.isBlank()||ans.confidence<.75f||ans.minConfidence<.65f||(ans.text.count{!it.isWhitespace()}<=2&&b.score<.55f&&ans.confidence<.8f)
        val keep=!official||ans.confidence>=.5f
        compared.add(ans);keepFlags.add(keep)
        if(!keep)dropped++
        if(ans.text.isNotBlank()&&keep)rawParts.add(ans.text)
        count++;if(suspect)uncertain++;if(ans.text.isBlank())blank++
        parts.add(if(suspect)"[待核:${ans.text}]"else ans.text.ifBlank{"[未识别]"})
        notes.add("cell row=${index+ri+1} col=${ci+1} quad=${b.points.contentToString()} det=${b.score} rec=${ans.confidence} min=${ans.minConfidence} width=${ans.width} suspect=$suspect keep=$keep text=${ans.text}")
       }
       if(rawParts.isNotEmpty())rawLines.add(rawParts.joinToString(" "))
       if(parts.isNotEmpty())lines.add(parts.joinToString(" | "))
       if(first<0&&parallel==null)first=now()-begin
      }
      if(parallel==null)note("CPU_BASE_BATCH row_start="+(index+1)+" rows="+chunk.size+" crops="+chunk.sumOf{it.size}+" prep_infer_decode_wall_ms="+(now()-batchAt))
     }
     if(parallel!=null)check(cooperativeIndex==parallel.size)
    }finally{rgba.release();bgr.release()}
    val rec=now()-begin;val total=detected-init+pMs+rec+focusMs
    val original=rawLines.joinToString("\n");val done=now()
    val key="$sample/$name/$longSide/$nt"
    val geometry=rows.flatten().map{it.points.contentToString()}
    fun compare(ref:Reference?,label:String){
     if(ref==null)note("$label unavailable=run_matching_CPU_reference_first")
     else if(ref.geometry!=geometry||ref.values.size!=compared.size)note("$label reference="+ref.run+" geometry_equal=false not_comparable=1")
     else{
      val ids=compared.indices;val textDiff=ids.count{compared[it].text!=ref.values[it].text};val keepDiff=ids.count{keepFlags[it]!=ref.keep[it]};val widthDiff=ids.count{compared[it].width!=ref.values[it].width};val maxConf=ids.maxOfOrNull{abs(compared[it].confidence-ref.values[it].confidence)}?:0f;val maxMin=ids.maxOfOrNull{abs(compared[it].minConfidence-ref.values[it].minConfidence)}?:0f
      note("$label reference="+ref.run+" geometry_equal=true cells="+compared.size+" text_diff="+textDiff+" keep_diff="+keepDiff+" width_diff="+widthDiff+" max_mean_conf_diff="+maxConf+" max_min_conf_diff="+maxMin+" reference_complete_ms="+ref.complete+" candidate_complete_ms="+(done-clicked)+" (CPU agreement is not human truth)")
      for(i in ids)if(compared[i].text!=ref.values[i].text||keepFlags[i]!=ref.keep[i]){
       val a=ref.values[i];val b=compared[i]
       note("$label DIFF index=$i reference_text="+org.json.JSONObject.quote(a.text)+" candidate_text="+org.json.JSONObject.quote(b.text)+" reference_mean="+a.confidence+" candidate_mean="+b.confidence+" reference_keep="+ref.keep[i]+" candidate_keep="+keepFlags[i])
      }
     }
    }
    if(selected==0)references[key]=Reference(runId,geometry,compared.toList(),keepFlags.toList(),done-clicked)
    else{
     compare(references[key],"REFERENCE_COMPARE")
     if(selected==3)references["fixed/$key"]=Reference(runId,geometry,compared.toList(),keepFlags.toList(),done-clicked)
     if(selected==2)compare(references["fixed/$key"],"MATCHED_FIXED_COMPARE")
    }
    note("\n$name detect_ms=${detected-init} first_row_ms=$first rec_ms=$rec total_ms=$total crops=$count suspect=$uncertain blank=$blank official_dropped=$dropped pss_kb=${Debug.getPss()}\n"+lines.joinToString("\n")+"\n"+notes.joinToString("\n")+"\n OCR 点击至原文就绪：${done-clicked} ms\n")
    note("SUMMARY run=$runId sample_sha256=$sample backend=$backendName complete_ms=${done-clicked} rec_ms=$rec detect_ms=${detected-init} crops=$count first_row_scope="+(if(selected==0)"baseline" else "not_measured_see_batch_finish")+" thermal_status="+(if(Build.VERSION.SDK_INT>=29)(getSystemService(POWER_SERVICE) as PowerManager).currentThermalStatus else -1))
    note("RAW_TEXT_BEGIN\n"+original+"\nRAW_TEXT_END\nEND run=$runId success=1")
    history.append(details.toString())
    runOnUiThread{if(!isDestroyed){
     output.text=details.toString();raw.text=original;lastText=original;replay.isEnabled=original.isNotBlank();speechRun=runId;speak(original,clicked);status.text="识别完成 · 历史日志 "+history.size()+" bytes";busy(false)
    }}
   }catch(t:Throwable){
    if(needsGpu){note("GPU_SESSION_CLOSE reason=failed_run");runCatching{gpu?.close()};gpu=null}
    note("FAIL run=$runId backend=$backendName error="+t.stackTraceToString()+"\nEND run=$runId success=0")
    val saved=runCatching{history.append(details.toString())}
    runOnUiThread{if(!isDestroyed){output.text=details.toString();status.text="失败："+t.message+(if(saved.isFailure)"；历史日志写入失败" else "；已保留日志");busy(false)}}
   }
  }
 }
}
