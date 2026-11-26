package com.paddle.ocr.demo

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.os.Build
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.*
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.*

class MainActivity: Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private var engine:PaddleOCR?=null
 private var engineCap=0
 private var bytes:ByteArray?=null
 private lateinit var status:TextView
 private lateinit var output:TextView
 private lateinit var diagnostics:TextView
 private lateinit var pick:Button
 private lateinit var run:Button
 private lateinit var modes:Spinner
 private lateinit var reference:EditText
 private val logs=StringBuilder()
 private fun now()=SystemClock.elapsedRealtime()
 private fun note(s:String){logs.append(s).append('\n');diagnostics.text=logs.toString()}
 private fun copy(s:String){(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OCR",s));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show()}
 private fun lock(b:Boolean){pick.isEnabled=!b;run.isEnabled=!b;modes.isEnabled=!b}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,32)}
  setContentView(ScrollView(this).apply{addView(root)})
  fun label(s:String,size:Float=18f)=TextView(this).apply{text=s;textSize=size;setTextIsSelectable(true);root.addView(this)}
  fun button(s:String,action:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{action()};root.addView(this)}
  label("蓝星 · 飞桨离线OCR实验 0.1.0",24f)
  label("官方 PP-OCRv5 Mobile · ONNX CPU4 · 不使用大模型")
  status=label("请选择相册原图")
  pick=button("选择相册原图"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},11)}
  label("检测图像长边上限（原图字节保留）")
  modes=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("1280（速度优先）","2560（细节优先）","4000（高清对照）"));setSelection(1);root.addView(this)}
  run=button("开始识别 / 重复同图测速"){recognize()}
  output=label("")
  button("复制原始OCR文字"){copy(output.text.toString())}
  label("可选：粘贴人工核对的原文，计算字符错误率")
  reference=EditText(this).apply{hint="无需填写即可识别";minLines=2;root.addView(this)}
  button("计算字符错误率"){
   val a=reference.text.toString().filter{!it.isWhitespace()}.toCharArray()
   val b=output.text.toString().filter{!it.isWhitespace()}.toCharArray()
   if(a.isEmpty()||a.size>20000||b.size>20000){status.text="请输入1–20000字符的参考原文";return@button}
   var prev=IntArray(b.size+1){it}
   for(i in a.indices){val cur=IntArray(b.size+1);cur[0]=i+1;for(j in b.indices)cur[j+1]=minOf(cur[j]+1,prev[j+1]+1,prev[j]+if(a[i]==b[j])0 else 1);prev=cur}
   val cer=100.0*prev.last()/a.size
   note("CER="+String.format(java.util.Locale.US,"%.2f%%",cer)+" edit="+prev.last()+" ref_chars="+a.size+" hyp_chars="+b.size)
  }
  button("复制完整诊断日志"){copy(logs.toString())}
  diagnostics=label("",14f)
  note("v=0.1.0 soc="+Build.SOC_MODEL+" sdk="+Build.VERSION.SDK_INT+" model=PP-OCRv5_mobile_det+rec threads=4")
  note(assets.open("model-sha256.txt").bufferedReader().use{it.readText()})
 }
 @Deprecated("Activity callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=11||resultCode!=RESULT_OK)return
  val uri=data?.data?:return
  lock(true)
  scope.launch{
   try{
    val b=withContext(Dispatchers.IO){contentResolver.openInputStream(uri)!!.use{f->
     val out=java.io.ByteArrayOutputStream();val buf=ByteArray(128*1024);while(true){val n=f.read(buf);if(n<0)break;require(out.size()+n<=50*1024*1024){"图片超过50MB"};out.write(buf,0,n)};out.toByteArray()
    }}
    val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(b,0,b.size,o)
    require(o.outWidth>0&&o.outHeight>0&&o.outWidth.toLong()*o.outHeight<=30_000_000L){"无效图片或超过3000万像素"}
    bytes=b;output.text="";status.text="已选图 "+o.outWidth+"×"+o.outHeight
    note("image="+o.outWidth+"x"+o.outHeight+" bytes="+b.size+" sha256="+java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it.toInt() and 255)})
   }catch(t:Throwable){bytes=null;status.text="读取失败："+t.message;note(status.text.toString())}finally{lock(false)}
  }
 }
 private fun recognize(){
  val image=bytes?:run{status.text="请先选图";return}
  val cap=listOf(1280,2560,4000)[modes.selectedItemPosition]
  lock(true);output.text="";status.text="识别中..."
  scope.launch{
   try{
    val start=now();val reused=engine!=null&&engineCap==cap
    var load=0L
    if(!reused){
     engine?.release();engine=null
     val t=now();check(withContext(Dispatchers.IO){OpenCVUtils.init(this@MainActivity)})
     engine=PaddleOCR.create(this@MainActivity,PaddleOCRConfig(detLimitSideLen=cap,detLimitType="max",detMaxSideLimit=cap,recScoreThresh=0f,recBatchSize=1),EngineConfig(numThreads=4))
     engineCap=cap;load=now()-t
    }
    val result=engine!!.recognize(image);val elapsed=now()-start
    output.text=result.results.joinToString("\n"){it.text}
    note("cap="+cap+" reused="+reused+" load_ms="+load+" detection_ms="+result.detectionTimeMs+" recognition_ms="+result.recognitionTimeMs+" sdk_total_ms="+result.totalTimeMs+" click_to_result_ms="+elapsed+" lines="+result.lineCount)
    result.results.forEachIndexed{i,v->note("line="+i+" confidence="+v.confidence+" box="+v.box.points+" text="+v.text)}
    status.text="完成 · "+elapsed+"ms · "+result.lineCount+"行"
   }catch(t:Throwable){status.text="识别失败："+t.message;note(status.text.toString())}finally{lock(false)}
  }
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
