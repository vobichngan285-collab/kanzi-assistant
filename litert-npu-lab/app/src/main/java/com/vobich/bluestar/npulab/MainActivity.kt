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
        const val V="0.1.9"
        const val SHA="20c73b5a1e48a315974cff1853535ff5d017100b6420d5ef221b9dd1b955dd7c"
        const val URL="https://huggingface.co/litert-community/MiniCPM-V-4.7-1B/resolve/main/MiniCPM-V-4.7-1B-VL_int8vis.litertlm"
        const val PROMPT="请逐字抄写图片中所有可见文字和数字。不要解释。"
        const val SAMPLE="蓝星向导\n药品 5931"
        const val HOME_REF="C110运动相机\n监听\nWi-Fi未连接\n快速连接\n灯光\n快捷拍照\n实时预览\n朗读任务\n阅读模式\n设置\n主页\n预览\n按住AI"
        const val DENSE="营业时间\n09:30 - 18:00\n药品咨询服务\n请在窗口排队\n编号 4827\n价格 28.50 元"
    }
    private val worker=Executors.newSingleThreadExecutor()
    private val model by lazy{File(filesDir,"minicpm-v47-int8vis.litertlm")}
    private val prefs by lazy{getSharedPreferences("lab",MODE_PRIVATE)}
    private val sample by lazy{File(cacheDir,"sample.png")}
    private val dense by lazy{File(cacheDir,"dense.png")}
    private var selected:File?=null
    private lateinit var status:TextView
    private lateinit var output:TextView
    private lateinit var progress:TextView
    private lateinit var logs:TextView
    private lateinit var reference:EditText
    private val buttons=mutableListOf<Button>()
    private var busy=false
    private val answers=mutableListOf<String>()
    private lateinit var runner: LabController
    private lateinit var speech: StreamSpeech

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL; val p=(16*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)}
        fun label(s:String,size:Float=17f):TextView=TextView(this).apply{text=s;textSize=size;root.addView(this)}
        fun button(s:String,action:()->Unit){root.addView(Button(this).apply{text=s;isAllCaps=false;setOnClickListener{if(!busy)action()};buttons.add(this)})}
        label("蓝星 OCR 常驻朗读实验 v"+V,23f).setTextColor(Color.rgb(22,74,160))
        status=label(if(ready())"模型已校验" else "首次使用：下载约1.70 GB LiteRT模型后导入，旧GGUF不可复用")
        label("离线 CPU 原图识别。预热后保留模型；识别时逐段使用系统离线中文语音。")
        button("下载指定模型（浏览器）"){runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(URL)))}.onFailure{status.text="请从发布说明打开模型链接"}}
        button("导入 LiteRT 模型"){choose("*/*",1)}
        button("内置简单图"){selected=sample;reference.setText(SAMPLE);status.text="已选择简单图"}
        button("内置密集文字图"){selected=dense;reference.setText(DENSE);status.text="已选择密集文字图"}
        button("选择图片"){choose("image/*",2)}
        reference=EditText(this).apply{hint="参考文字（可选，用于CER）";setText(SAMPLE);root.addView(this)}
        button("填入蓝星首页正文参考（仅对应截图）"){reference.setText(HOME_REF)}
        button("预热模型（不朗读）"){warmup()}
        button("原图识别并朗读"){test(listOf("cpu"),"original")}
        root.addView(Button(this).apply{text="停止实验 / 释放模型";setOnClickListener{if(::speech.isInitialized)speech.cancel();if(::runner.isInitialized)runner.stop()}})
        progress=label("等待实验")
        output=label("识别结果")
        root.addView(Button(this).apply{text="复制本批识别原文（含各方案）";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("识别原文",answers.joinToString("\n\n")));Toast.makeText(this@MainActivity,"已复制",Toast.LENGTH_SHORT).show()}})
        root.addView(Button(this).apply{text="复制数值日志";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("蓝星NPU日志",prefs.getString("rows","")?:""));Toast.makeText(this@MainActivity,"已复制",Toast.LENGTH_SHORT).show()}})
        button("清空日志"){prefs.edit().remove("rows").commit();logs.text=""}
        logs=label((prefs.getString("rows","")?:"").lines().takeLast(24).joinToString("\n"),13f)
        val scroll=ScrollView(this).apply{isFillViewport=true;addView(root)}
        if(android.os.Build.VERSION.SDK_INT>=35)scroll.setOnApplyWindowInsetsListener{v,i->val b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);i}
        setContentView(scroll)
        speech=StreamSpeech(this){line->append("v=$V "+line)}
        runner=LabController(this,{line->append(line)},{s->status.text=s;progress.text=s},{s->answers.add(s);output.text=s},{b->speech.accept(b)},{speech.cancel()},{busy=false;buttons.forEach{it.isEnabled=true};mark("complete")})
        if(!sample.exists())makeSample()
        if(!dense.exists())makeDense()
        selected=sample
        val stage=prefs.getString("stage","idle")
        if(stage!="idle" && stage!="complete") append("v=$V previous_incomplete_stage=$stage")
        if(ready())warmup()
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
                setStatus("模型导入成功；请点击预热模型")
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
    private fun makeDense(){
        val b=Bitmap.createBitmap(448,448,Bitmap.Config.ARGB_8888)
        val c=Canvas(b);c.drawColor(Color.WHITE)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=31f;textAlign=Paint.Align.LEFT}
        DENSE.lines().forEachIndexed{i,line->c.drawText(line,24f,64f+i*62f,p)}
        FileOutputStream(dense).use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    private fun warmup(){
        if(!ready()||busy)return
        speech.cancel();busy=true;buttons.forEach{it.isEnabled=false};mark("experiment")
        answers.clear();output.text="正在预热（内置图片，不朗读）"
        runner.start(listOf("cpu"),model.absolutePath,sample.absolutePath,SAMPLE,"warmup")
    }
    private fun test(modes:List<String>,strategy:String){
        if(!ready()){status.text="请先导入指定模型";return}
        if(busy)return
        val image=selected?:sample;val ref=reference.text.toString()
        busy=true;buttons.forEach{it.isEnabled=false};mark("experiment")
        speech.begin();answers.clear();output.text="正在识别"
        runner.start(modes,model.absolutePath,image.absolutePath,ref,strategy)
    }
    override fun onDestroy(){
        if(::runner.isInitialized)runner.dispose()
        if(::speech.isInitialized)speech.close()
        worker.shutdown()
        super.onDestroy()
    }
    private fun now()=SystemClock.elapsedRealtime()
    private fun hex(b:ByteArray)=b.joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun sha256(f:File):String{val md=MessageDigest.getInstance("SHA-256");FileInputStream(f).use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}};return hex(md.digest())}
    private fun mark(s:String){prefs.edit().putString("stage",s).commit()}
    private fun append(s:String){val rows=((prefs.getString("rows","")?:"")+"\n"+s).trim().lines().takeLast(600).joinToString("\n");prefs.edit().putString("rows",rows).commit();runOnUiThread{logs.text=rows.lines().takeLast(24).joinToString("\n")}}
    private fun setStatus(s:String){runOnUiThread{status.text=s}}
    private fun launch(s:String,block:()->Unit){
        if(busy)return;busy=true;buttons.forEach{it.isEnabled=false};setStatus(s)
        worker.execute{try{mark("working");block();mark("complete")}catch(t:Throwable){append("v=$V phase=failed error="+t.javaClass.simpleName);setStatus("操作失败："+(t.message?.takeIf{it.length<100&&!it.contains('/')}?:t.javaClass.simpleName));mark("complete")}finally{runOnUiThread{busy=false;buttons.forEach{it.isEnabled=true}}}}
    }
}
