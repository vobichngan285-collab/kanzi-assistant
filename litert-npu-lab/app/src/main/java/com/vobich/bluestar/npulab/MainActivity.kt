package com.vobich.bluestar.npulab
import android.app.Activity
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.*
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import java.io.*
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity: Activity() {
    companion object {
        const val V="0.1.5"
        const val SHA="20c73b5a1e48a315974cff1853535ff5d017100b6420d5ef221b9dd1b955dd7c"
        const val URL="https://huggingface.co/litert-community/MiniCPM-V-4.7-1B/resolve/main/MiniCPM-V-4.7-1B-VL_int8vis.litertlm"
        const val PROMPT="请逐字抄写图片中所有可见文字和数字。不要解释。"
        const val SAMPLE="蓝星向导\n药品 5931"
    }
    private val worker=Executors.newSingleThreadExecutor()
    private val model by lazy{File(filesDir,"minicpm-v47-int8vis.litertlm")}
    private val prefs by lazy{getSharedPreferences("lab",MODE_PRIVATE)}
    private val sample by lazy{File(cacheDir,"sample.png")}
    private var selected:File?=null
    private lateinit var status:TextView
    private lateinit var output:TextView
    private lateinit var logs:TextView
    private lateinit var reference:EditText
    private val buttons=mutableListOf<Button>()
    private var busy=false
    private lateinit var runner: LabController

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL; val p=(16*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)}
        fun label(s:String,size:Float=17f):TextView=TextView(this).apply{text=s;textSize=size;root.addView(this)}
        fun button(s:String,action:()->Unit){root.addView(Button(this).apply{text=s;isAllCaps=false;setOnClickListener{if(!busy)action()};buttons.add(this)})}
        label("蓝星 GPU / NPU 实验室 v"+V,23f).setTextColor(Color.rgb(22,74,160))
        status=label(if(ready())"模型已校验" else "首次使用：下载约1.70 GB LiteRT模型后导入，旧GGUF不可复用")
        label("本地离线；GPU/NPU为请求；加速器超时后隔离进程会停止。")
        button("下载指定模型（浏览器）"){runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(URL)))}.onFailure{status.text="请从发布说明打开模型链接"}}
        button("导入 LiteRT 模型"){choose("*/*",1)}
        button("内置测试图"){selected=sample;reference.setText(SAMPLE);status.text="已选择内置图"}
        button("选择图片"){choose("image/*",2)}
        reference=EditText(this).apply{hint="参考文字（可选，用于CER）";setText(SAMPLE);root.addView(this)}
        button("CPU 6线程（3轮）"){test(listOf("cpu"))}
        button("GPU视觉 + CPU 6线程（3轮）"){test(listOf("gpu_vision"))}
        button("GPU全面加速（3轮）"){test(listOf("gpu_all"))}
        button("NPU视觉 + CPU 6线程（3轮）"){test(listOf("npu_vision"))}
        button("自动对比 CPU / GPU（各3轮）"){test(listOf("cpu","gpu_vision","gpu_all"))}
        button("自动对比全部模式（各3轮）"){test(listOf("cpu","gpu_vision","gpu_all","npu_vision"))}
        root.addView(Button(this).apply{text="停止当前实验";setOnClickListener{if(::runner.isInitialized)runner.stop()}})
        output=label("识别结果")
        root.addView(Button(this).apply{text="复制数值日志";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("蓝星NPU日志",prefs.getString("rows","")?:""));Toast.makeText(this@MainActivity,"已复制",Toast.LENGTH_SHORT).show()}})
        button("清空日志"){prefs.edit().remove("rows").commit();logs.text=""}
        logs=label(prefs.getString("rows","")?:"",13f)
        val scroll=ScrollView(this).apply{isFillViewport=true;addView(root)}
        if(android.os.Build.VERSION.SDK_INT>=35)scroll.setOnApplyWindowInsetsListener{v,i->val b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);i}
        setContentView(scroll)
        runner=LabController(this,{line->append(line)},{s->status.text=s},{s->output.text=s},{busy=false;buttons.forEach{it.isEnabled=true};mark("complete")})
        if(!sample.exists())makeSample()
        selected=sample
        val stage=prefs.getString("stage","idle")
        if(stage!="idle" && stage!="complete") append("v=$V previous_incomplete_stage=$stage")
    }
    private fun ready()=model.isFile && prefs.getString("model_sha","")==SHA && prefs.getLong("model_bytes",-1)==model.length()
    private fun choose(type:String,code:Int){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);this.type=type},code)}
    @Deprecated("Activity result bridge")
    override fun onActivityResult(code:Int,result:Int,data:Intent?){
        super.onActivityResult(code,result,data)
        val uri=data?.data?:return
        if(result!=RESULT_OK||busy)return
        if(code==1)launch("导入并校验模型"){
            val part=File(filesDir,"model-import.part")
            try{
                val md=MessageDigest.getInstance("SHA-256");var bytes=0L
                contentResolver.openInputStream(uri)!!.use{input->FileOutputStream(part).use{out->
                    val buf=ByteArray(1024*1024)
                    while(true){val n=input.read(buf);if(n<0)break;bytes+=n;require(bytes<=2_000_000_000L){"模型过大"};md.update(buf,0,n);out.write(buf,0,n)}
                    out.fd.sync()
                }}
                require(hex(md.digest())==SHA){"模型SHA256不匹配"}
                require(part.renameTo(model)){"无法保存模型"}
                prefs.edit().putString("model_sha",SHA).putLong("model_bytes",bytes).commit()
                append("v=$V phase=model_verified bytes=$bytes")
                setStatus("模型导入成功")
            }finally{part.delete()}
        }else if(code==2)launch("读取图片"){
            val dest=File(cacheDir,"selected.img");val part=File(cacheDir,"selected.part")
            try{
                contentResolver.openInputStream(uri)!!.use{input->FileOutputStream(part).use{out->
                    val b=ByteArray(65536);var total=0L
                    while(true){val n=input.read(b);if(n<0)break;total+=n;require(total<=32L*1024*1024){"图片超过32MB"};out.write(b,0,n)}
                }}
                val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(part.absolutePath,o)
                require(o.outWidth>0 && o.outHeight>0 && o.outWidth.toLong()*o.outHeight<=48_000_000L){"图片无法解码或过大"}
                require(part.renameTo(dest));selected=dest
                runOnUiThread{reference.setText("")}
                setStatus("已选择原图 "+o.outWidth+"×"+o.outHeight)
            }finally{part.delete()}
        }
    }
    private fun makeSample(){
        val b=Bitmap.createBitmap(448,448,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(Color.WHITE)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=52f;textAlign=Paint.Align.CENTER}
        c.drawText("蓝星向导",224f,160f,p);c.drawText("药品 5931",224f,250f,p)
        FileOutputStream(sample).use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    private fun test(modes:List<String>){
        if(!ready()){status.text="请先导入指定模型";return}
        if(busy)return
        val image=selected?:sample;val ref=reference.text.toString()
        busy=true;buttons.forEach{it.isEnabled=false};mark("experiment")
        runner.start(modes,model.absolutePath,image.absolutePath,ref)
    }
    override fun onDestroy(){
        if(::runner.isInitialized)runner.dispose()
        worker.shutdown()
        super.onDestroy()
    }
    private fun now()=SystemClock.elapsedRealtime()
    private fun hex(b:ByteArray)=b.joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun sha256(f:File):String{val md=MessageDigest.getInstance("SHA-256");FileInputStream(f).use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}};return hex(md.digest())}
    private fun mark(s:String){prefs.edit().putString("stage",s).commit()}
    private fun append(s:String){val rows=((prefs.getString("rows","")?:"")+"\n"+s).trim().lines().takeLast(120).joinToString("\n");prefs.edit().putString("rows",rows).commit();runOnUiThread{logs.text=rows}}
    private fun setStatus(s:String){runOnUiThread{status.text=s}}
    private fun launch(s:String,block:()->Unit){
        if(busy)return;busy=true;buttons.forEach{it.isEnabled=false};setStatus(s)
        worker.execute{try{mark("working");block();mark("complete")}catch(t:Throwable){append("v=$V phase=failed error="+t.javaClass.simpleName);setStatus("操作失败："+(t.message?.takeIf{it.length<100&&!it.contains('/')}?:t.javaClass.simpleName));mark("complete")}finally{runOnUiThread{busy=false;buttons.forEach{it.isEnabled=true}}}}
    }
}
