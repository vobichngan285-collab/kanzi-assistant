package com.vobich.bluestar.gemmalab
import android.app.Activity
import android.os.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.ai.edge.litertlm.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalApi::class)
class MainActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var root:LinearLayout
 private lateinit var status:TextView
 private lateinit var info:TextView
 private lateinit var output:TextView
 private lateinit var logView:TextView
 private lateinit var prompt:EditText
 private lateinit var backend:Spinner
 private lateinit var mode:Spinner
 private lateinit var budget:Spinner
 private lateinit var preview:ImageView
 private var locator:TextLocator?=null
 private lateinit var speech:CheckBox
 private lateinit var follow:Button
 private val controls=mutableListOf<View>()
 private val logs=StringBuilder()
 private var options=emptyList<Int>()
 private var selected:File?=null
 private var source=""
 private var pending:File?=null
 private var busy=false
 private var closed=false
 private var hasChat=false
 private var tts:TextToSpeech?=null
 private var voiceReady=false
 private var utterance=0
 private var firstQueue=-1L
 private var started=0L
 private val speakBuffer=StringBuilder()
 private var engine:Engine?=null
 private var chat:Conversation?=null
 @Volatile private var engineKey=""
 private val model get()=File(filesDir,"gemma-4-E2B-it.litertlm")
 private val stamp get()=File(filesDir,"verified.txt")
 private fun now()=SystemClock.elapsedRealtime()
 private fun dp(x:Int)=(x*resources.displayMetrics.density+.5f).toInt()
 private fun ui(a:()->Unit){handler.post{if(!closed)a()}}
 private fun label(s:String,sz:Float=18f)=TextView(this).apply{text=s;textSize=sz;setPadding(dp(4),dp(6),dp(4),dp(6));setTextIsSelectable(true);root.addView(this)}
 private fun button(s:String,action:()->Unit,lock:Boolean=true)=Button(this).apply{text=s;textSize=17f;isAllCaps=false;setOnClickListener{action()};root.addView(this);if(lock)controls.add(this)}
 private fun spinner(items:List<String>)=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items);root.addView(this);controls.add(this)}
 private fun note(s:String){logs.append(s).append('\n');logView.text=logs.toString()}
 private fun lock(v:Boolean){busy=v;controls.forEach{it.isEnabled=!v};follow.isEnabled=!v&&hasChat;if(v)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
 private fun ready()=model.isFile&&model.length()==Policy.MODEL_SIZE&&stamp.isFile&&stamp.readText()==Policy.MODEL_SHA
 private fun release(){try{chat?.close()}finally{chat=null;try{engine?.close()}finally{engine=null;engineKey=""}}}
 private fun metadata(){
  val data=ModelInfo.from(model.absolutePath).use{m->
   require(m is ModelInfo.Llm){"模型非LLM"}
   val ceiling=m.maxVisionTokenBudget()
   val opts=Policy.budgets(m.visionSignatureSelection(),ceiling)
   require(ceiling==280&&opts.contains(280)){"不是原厂280视觉模型"}
   listOf(280) to ("max_visual="+ceiling+" options="+opts.joinToString(",")+" context="+m.maxContextTokens())
  }
  ui{options=data.first;budget.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,options.map{it.toString()});budget.setSelection(options.indexOf(280).takeIf{it>=0}?:options.lastIndex);note(data.second);status.text="模型已验证";lock(false)}
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(24))}
  setContentView(ScrollView(this).apply{addView(root)})
  label("蓝星 YOLO文字定位＋Gemma 280 v0.1.0",24f);label("Gemma 4 E2B · LiteRT-LM 0.18.1 · 离线")
  status=label("请导入官方模型")
  button("浏览器下载原厂模型（2.59GB）",{try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(Policy.MODEL_URL)))}catch(t:Throwable){status.text="无法打开浏览器"}})
  button("导入 .litertlm 模型",{pick(11,"*/*")})
  label("推理后端（视觉 / 语言）");backend=spinner(listOf("GPU / GPU（默认）","GPU / CPU","CPU / CPU"))
  label("视觉 Token（模型实际支持）");budget=spinner(listOf("导入后显示"))
  label("同图输入对照");mode=spinner(listOf("原图（基准）","长边1024 PNG","长边512 PNG","YOLO自动定位裁剪（默认）"));mode.setSelection(3)
  button("系统相机高清拍照",{takePhoto()})
  button("选择相册原图",{pick(12,"image/*")})
  info=label("尚未选图")
  preview=ImageView(this).apply{adjustViewBounds=true;contentDescription="文字定位框及选定裁剪区域预览";root.addView(this,LinearLayout.LayoutParams(-1,dp(260)))}
  prompt=EditText(this).apply{setText(Policy.OCR);textSize=18f;minLines=2;hint="识别或问答指令";root.addView(this);controls.add(this)}
  speech=CheckBox(this).apply{text="流式离线朗读";isChecked=true;textSize=18f;root.addView(this);controls.add(this)}
  button("开始识别 / 新图片问答",{runTurn(false)})
  follow=button("继续追问",{runTurn(true)});follow.isEnabled=false
  button("恢复 OCR 指令",{prompt.setText(Policy.OCR)})
  output=label("")
  button("复制识别结果",{copy(output.text.toString())},false)
  button("复制诊断日志",{copy(logs.toString())},false)
  logView=label("",14f)
  tts=TextToSpeech(this){code->ui{
   if(code==TextToSpeech.SUCCESS){val svc=tts;val voice=svc?.voices?.filter{!it.isNetworkConnectionRequired&&it.locale.language=="zh"}?.maxByOrNull{it.quality};if(voice!=null)voiceReady=svc?.setVoice(voice)==TextToSpeech.SUCCESS}
   note("offline_tts_ready="+voiceReady)
  }}
  note("v=0.1.0 soc="+Build.SOC_MODEL+" sdk="+Build.VERSION.SDK_INT)
  if(ready()){lock(true);worker.execute{try{metadata()}catch(t:Throwable){ui{status.text="模型元数据错误: "+t.message;lock(false)}}}}
 }
 private fun copy(s:String){(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("蓝星",s));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show()}
 private fun pick(code:Int,mime:String){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type=mime;addCategory(Intent.CATEGORY_OPENABLE)},code)}catch(t:Throwable){status.text="选择失败: "+t.message}}
 private fun takePhoto(){
  val f=File.createTempFile("photo-",".jpg",File(cacheDir,"captures").apply{mkdirs()});pending=f
  val uri=FileProvider.getUriForFile(this,packageName+".files",f)
  try{startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply{putExtra(MediaStore.EXTRA_OUTPUT,uri);clipData=ClipData.newRawUri("photo",uri);addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)},13)}
  catch(t:Throwable){pending=null;f.delete();status.text="相机失败: "+t.message}
 }
 @Deprecated("Activity callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==13){val f=pending;pending=null;if(resultCode==RESULT_OK&&f!=null&&f.length()>0)accept(f,"系统相机原图")else{f?.delete();status.text="未获得拍照文件"};return}
  if(resultCode!=RESULT_OK)return
  val uri=data?.data?:return
  if(requestCode==11){
   lock(true);hasChat=false;options=emptyList();status.text="复制并校验模型..."
   worker.execute{
    val temp=File(filesDir,"import.tmp")
    try{
     release();val digest=MessageDigest.getInstance("SHA-256");var total=0L;var last=0L
     contentResolver.openInputStream(uri)!!.use{input->temp.outputStream().buffered().use{out->
      val b=ByteArray(1024*1024)
      while(true){val n=input.read(b);if(n<0)break;total+=n;require(total<=Policy.MODEL_SIZE){"文件大小不匹配"};digest.update(b,0,n);out.write(b,0,n)
       if(total-last>=64L*1024*1024){last=total;val percent=total*100/Policy.MODEL_SIZE;ui{status.text="导入 "+percent+"%"}}
      }
     }}
     val sha=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
     require(total==Policy.MODEL_SIZE&&sha==Policy.MODEL_SHA){"非本实验锁定的官方版本：bytes="+total+" sha="+sha}
     stamp.delete();if(model.exists())check(model.delete());check(temp.renameTo(model));stamp.writeText(sha)
     ui{note("model_verified bytes="+total+" sha="+sha)};metadata()
    }catch(t:Throwable){ui{status.text="导入失败: "+t.message;note(status.text.toString());lock(false)}}finally{temp.delete()}
   }
  }else if(requestCode==12){
   lock(true);worker.execute{
    val f=File.createTempFile("album-",".img",cacheDir)
    try{
     var count=0L
     contentResolver.openInputStream(uri)!!.use{input->f.outputStream().buffered().use{out->val b=ByteArray(128*1024);while(true){val n=input.read(b);if(n<0)break;count+=n;require(count<=50L*1024*1024){"图片超过50MB"};out.write(b,0,n)}}}
     ui{accept(f,"相册原图");lock(false)}
    }catch(t:Throwable){f.delete();ui{status.text="读取失败: "+t.message;lock(false)}}
   }
  }
 }
 private fun bounds(f:File)=BitmapFactory.Options().apply{inJustDecodeBounds=true;BitmapFactory.decodeFile(f.absolutePath,this)}
 private fun accept(f:File,s:String){
  val b=bounds(f);if(b.outWidth<=0||b.outHeight<=0){f.delete();status.text="无效图片";return}
  selected?.delete();selected=f;source=s;hasChat=false;follow.isEnabled=false;preview.setImageDrawable(null)
  info.text=s+" "+b.outWidth+"×"+b.outHeight+" · "+f.length()+"字节";status.text="图片已选"
 }
 private data class Input(val bytes:ByteArray,val w:Int,val h:Int,val format:String)
 private fun prepare(f:File,kind:Int):Input{
  require(f.length() in 1L..(50L*1024*1024))
  val b=bounds(f);require(b.outWidth>0&&b.outHeight>0)
  require(b.outWidth.toLong()*b.outHeight<=30_000_000L){"图片超过3000万像素"}
  if(kind==3)return prepareYolo(f)
  val rotation=try{ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}catch(_:Exception){1}
  val cap=when(kind){1->1024;2->512;else->0}
  if(cap==0&&(rotation==0||rotation==1)&&(b.outMimeType=="image/jpeg"||b.outMimeType=="image/png"))return Input(f.readBytes(),b.outWidth,b.outHeight,"original_bytes")
  val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)){decoder,i,_->
   val p=Policy.size(i.size.width,i.size.height,cap);decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE;decoder.setTargetSize(p.first,p.second)
  }
  try{val out=ByteArrayOutputStream();check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));return Input(out.toByteArray(),bitmap.width,bitmap.height,if(cap==0)"orientation_or_format_png" else "edge_"+cap+"_png")}finally{bitmap.recycle()}
 }
 private fun prepareYolo(f:File):Input{
  val t=now()
  val b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)){decoder,i,_->
   require(i.size.width.toLong()*i.size.height<=16_000_000L){"自动裁剪模式原图超过1600万像素；请使用整图模式或较小照片"}
   decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
  }
  try{
   val decode=now()-t
   val d=now();if(locator==null)locator=TextLocator(this)
   val init=now()-d;val at=now()
   val found=locator!!.locate(b);val ms=now()-at
   val scale=minOf(1f,720f/maxOf(b.width,b.height))
   val overlay=Bitmap.createBitmap(maxOf(1,(b.width*scale).toInt()),maxOf(1,(b.height*scale).toInt()),Bitmap.Config.ARGB_8888)
   val canvas=Canvas(overlay);canvas.drawBitmap(b,null,Rect(0,0,overlay.width,overlay.height),Paint(Paint.FILTER_BITMAP_FLAG))
   canvas.scale(scale,scale)
   val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;color=Color.YELLOW;strokeWidth=2/scale}
   for(box in found.boxes){val p=Path();p.moveTo(box.points[0],box.points[1]);for(i in 1..3)p.lineTo(box.points[2*i],box.points[2*i+1]);p.close();canvas.drawPath(p,paint)}
   paint.color=Color.GREEN;paint.strokeWidth=4/scale
   val roi=found.roi
   if(roi!=null)canvas.drawRect(roi,paint)
   ui{preview.setImageBitmap(overlay);note("yolo boxes="+found.boxes.size+" groups="+found.groups+" chosen="+found.selectedCount+" decode_ms="+decode+" detector_init_ms="+init+" detect_ms="+ms+" source="+b.width+"x"+b.height+" roi="+(roi?.toShortString()?:"NONE original fallback"))}
   if(roi==null)return prepare(f,0)
   val crop=Bitmap.createBitmap(b,roi.left,roi.top,roi.width(),roi.height())
   try{
    val out=ByteArrayOutputStream();check(crop.compress(Bitmap.CompressFormat.PNG,100,out))
    return Input(out.toByteArray(),crop.width,crop.height,"yolo_original_roi_png")
   }finally{if(crop!==b)crop.recycle()}
  }finally{b.recycle()}
 }
 private fun read(chunk:String,finish:Boolean=false){
  if(!speech.isChecked||!voiceReady)return
  speakBuffer.append(chunk)
  while(speakBuffer.isNotEmpty()){
   var i=speakBuffer.indexOfFirst{it in "。！？；\n，"}
   if(i<0&&speakBuffer.length>=24)i=23
   if(i<0&&finish)i=speakBuffer.lastIndex
   if(i<0)break
   val text=speakBuffer.substring(0,i+1);speakBuffer.delete(0,i+1)
   if(text.isNotBlank()&&tts?.speak(text,TextToSpeech.QUEUE_ADD,null,"g"+(++utterance))==TextToSpeech.SUCCESS&&firstQueue<0)firstQueue=now()-started
  }
 }
 private fun runTurn(isFollow:Boolean){
  if(busy)return
  if(!ready()||options.isEmpty()){status.text="请先导入模型";return}
  val f=selected
  if(!isFollow&&f==null){status.text="请先选择图片";return}
  if(isFollow&&!hasChat)return
  val question=prompt.text.toString().trim();if(question.isEmpty())return
  val kind=backend.selectedItemPosition;val imageMode=mode.selectedItemPosition;val v=options[budget.selectedItemPosition];val key=kind.toString()+"/"+v
  if(isFollow&&engineKey!=key){status.text="配置已改，请重新识别";return}
  val src=source
  started=now();val start=started;firstQueue=-1;speakBuffer.clear();tts?.stop();output.text="";lock(true);status.text="准备中..."
  worker.execute{
   var prep=0L;var load=0L;var reset=0L;var first=-1L
   var shape="text_only";var bytes=0;var format="none"
   val reused=engine!=null&&engineKey==key
   try{
    val input=if(isFollow)null else{val t=now();prepare(f!!,imageMode).also{prep=now()-t;shape=it.w.toString()+"x"+it.h;bytes=it.bytes.size;format=it.format}}
    ExperimentalFlags.visualTokenBudget=v
    ExperimentalFlags.enableSpeculativeDecoding=false
    if(!reused){
     release();val t=now();ui{status.text="加载模型与GPU缓存..."}
     val e=Engine(EngineConfig(modelPath=model.absolutePath,backend=if(kind==0)Backend.GPU()else Backend.CPU(),visionBackend=if(kind==2)Backend.CPU()else Backend.GPU(),maxNumTokens=4000,cacheDir=File(cacheDir,"engine-cache").apply{mkdirs()}.absolutePath))
     try{e.initialize();engine=e;engineKey=key}catch(t:Throwable){try{e.close()}catch(_:Throwable){};throw t};load=now()-t
    }
    if(!isFollow){val t=now();chat?.close();chat=null;chat=engine!!.createConversation(ConversationConfig(samplerConfig=SamplerConfig(1,1.0,0.0),extraContext=mapOf("enable_thinking" to false),automaticToolCalling=false));reset=now()-t}
    val contents=mutableListOf<Content>();if(input!=null)contents.add(Content.ImageBytes(input.bytes));contents.add(Content.Text(question))
    val done=CountDownLatch(1);val error=AtomicReference<Throwable?>(null)
    val dispatch=now();ui{status.text="识别中...";note("dispatch source="+src+" input="+shape+" bytes="+bytes+" format="+format+" budget="+v)}
    chat!!.sendMessageAsync(Contents.of(contents),object:MessageCallback{
     override fun onMessage(message:com.google.ai.edge.litertlm.Message){val s=message.toString();if(s.isNotEmpty()){if(first<0&&s.isNotBlank())first=now()-start;val firstSeen=first;ui{output.append(s);read(s);status.text=if(firstSeen>=0)"首字 "+firstSeen+"ms · 生成中" else "生成中"}}}
     override fun onDone(){done.countDown()}
     override fun onError(throwable:Throwable){error.set(throwable);done.countDown()}
    },extraContext=mapOf("enable_thinking" to false))
    done.await();error.get()?.let{throw it}
    val elapsed=now()-start;val inf=if(first>=0)first-(dispatch-start)else -1L
    ui{read("",true);hasChat=true;note("turn follow="+isFollow+" backend_requested="+kind+" budget="+v+" reused="+reused+" prepare_ms="+prep+" load_ms="+load+" conversation_ms="+reset+" first_text_ms="+first+" inference_first_ms="+inf+" total_ms="+elapsed+" tts_first_queue_ms="+firstQueue);status.text="完成 · 首字 "+first+"ms · 全程 "+elapsed+"ms";lock(false)}
   }catch(t:Throwable){try{release()}catch(_:Throwable){};ui{hasChat=false;status.text="推理失败: "+t.message;note("error "+t.javaClass.simpleName+": "+t.message);lock(false)}}
   finally{ExperimentalFlags.visualTokenBudget=null;ExperimentalFlags.enableSpeculativeDecoding=false}
  }
 }
 override fun onDestroy(){closed=true;tts?.stop();tts?.shutdown();worker.execute{try{release()}catch(_:Throwable){};try{locator?.close()}catch(_:Throwable){};selected?.delete();pending?.delete()};worker.shutdown();super.onDestroy()}
}