package com.example.minicpm_v_demo

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.os.Process
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class LabActivity : AppCompatActivity() {
    private data class Weight(val name:String,val size:Long,val sha:String,val url:String)
    private data class Model(val id:String,val title:String,val weights:List<Weight>) {
        val size get()=weights.sumOf { it.size }
    }
    private data class Photo(val bytes:ByteArray,val width:Int,val height:Int,val sha:String)
    private lateinit var models:List<Model>
    private lateinit var engine:LlamaEngine
    private lateinit var picker:Spinner
    private lateinit var task:Spinner
    private lateinit var custom:EditText
    private lateinit var reference:EditText
    private lateinit var status:TextView
    private lateinit var output:TextView
    private lateinit var log:TextView
    private lateinit var imageLabel:TextView
    private lateinit var stop:Button
    private val controls=mutableListOf<View>()
    private var photo:Photo?=null
    private var busy=false
    @Volatile private var stopping=false
    private var shotFile:File?=null
    private var pendingImport:String?=null
    private val rows=ArrayList<String>()
    private val tasks=listOf(
        "朗读文字" to "识别图片中清晰可见的文字，按正确阅读顺序原样输出，不解释、不补充、不猜测。",
        "餐厅菜单" to "识别菜名、价格、菜品类别，找出明确标注的招牌菜和推荐菜",
        "医院科室" to "识别科室名称、楼层、诊室编号、方向指示",
        "店铺招牌" to "重点识别店名、经营项目、招牌文字和明确显示的特色",
        "商品信息" to "识别商品名称、品牌、规格、价格、生产日期等可见内容",
        "自定义任务" to ""
    )
    private val chooseImage=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && !busy) loadPhoto { contentResolver.openInputStream(uri) ?: error("无法读取图片") }
    }
    private val takePicture=registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f=shotFile;shotFile=null
        if(ok && f!=null && !busy) loadPhoto({f.delete()}) { f.inputStream() }
        else f?.delete()
    }
    private val chooseFolder=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id=pendingImport;pendingImport=null
        if(uri!=null && id!=null && !busy) {
            val m=models.first { it.id==id }
            operate("导入 "+m.title) { transfer(m,uri) }
        }
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        models=readManifest()
        engine=LlamaEngine.getInstance(applicationContext)
        val scroll=ScrollView(this)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24) }
        scroll.addView(root);setContentView(scroll)
        fun label(s:String,size:Float=17f)=TextView(this).apply { text=s;textSize=size;root.addView(this) }
        fun button(s:String,action:()->Unit)=Button(this).apply {
            text=s;setOnClickListener { action() };root.addView(this);controls.add(this)
        }
        fun spinner(items:List<String>)=Spinner(this).apply {
            adapter=ArrayAdapter(this@LabActivity,android.R.layout.simple_spinner_dropdown_item,items)
            root.addView(this);controls.add(this)
        }
        label("蓝星离线AI实验室",25f)
        label("4.7 安卓适配实验 · CPU · 不上传照片")
        picker=spinner(models.map { it.title+" · "+String.format(java.util.Locale.US,"%.2f GB",it.size/1e9) })
        status=label("请选择模型；下载或导入后可断网实验")
        button("下载所选模型（校验 SHA-256）") {
            val m=selected()
            AlertDialog.Builder(this).setMessage("需要约 "+m.size/1_000_000+" MB 存储。请使用可上网的 Wi-Fi，下载时保持本页。")
                .setPositiveButton("下载"){_,_->operate("下载 "+m.title){transfer(m,null)}}
                .setNegativeButton("取消",null).show()
        }
        button("导入所选模型文件夹") {
            pendingImport=selected().id;chooseFolder.launch(null)
        }
        button("删除所选模型") {
            val m=selected()
            AlertDialog.Builder(this).setMessage("仅删除实验工具中的 "+m.title+" 文件？")
                .setPositiveButton("删除"){_,_->operate("删除"){withContext(Dispatchers.IO){
                    check(directory(m).deleteRecursively()){"删除失败"}
                }}}
                .setNegativeButton("取消",null).show()
        }
        button("手机拍照（系统相机原图）") {
            try {
                val dir=File(cacheDir,"lab_photos");dir.mkdirs()
                val f=File.createTempFile("lab-",".jpg",dir);shotFile=f
                takePicture.launch(FileProvider.getUriForFile(this,packageName+".labfiles",f))
            } catch(e:Exception){shotFile?.delete();shotFile=null;status.text="拍照启动失败："+e.message}
        }
        button("导入同一张 JPEG / PNG"){chooseImage.launch(arrayOf("image/jpeg","image/png"))}
        imageLabel=label("尚未选择图片")
        task=spinner(tasks.map{it.first})
        custom=EditText(this).apply{hint="自定义任务提示词（选择自定义时使用）";maxLines=4;root.addView(this);controls.add(this)}
        reference=EditText(this).apply{hint="可选：正确文字，用于计算字符错误率";maxLines=5;root.addView(this);controls.add(this)}
        button("测试所选模型（1次冷加载 + 2次暖测）"){startTest(listOf(selected()))}
        button("两款模型顺序对比（同图同任务）"){startTest(models)}
        stop=button("停止（当前原生阶段完成后）"){stopping=true;status.text="已请求停止，等待当前阶段结束"}
        controls.remove(stop);stop.isEnabled=false
        button("复制数值日志（不含图片、提示词或识别文本）"){
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("BlueStar Lab",rows.joinToString("\n")))
        }
        output=label("识别结果")
        log=label("实验记录")
        shotFile=state?.getString("shot")?.let{File(it)}
        pendingImport=state?.getString("import")
        rows.addAll(getSharedPreferences("lab_log",MODE_PRIVATE).getString("rows","").orEmpty().lines().filter{it.isNotBlank()}.takeLast(150))
        log.text=rows.joinToString("\n")
    }
    override fun onSaveInstanceState(out:Bundle){
        out.putString("shot",shotFile?.absolutePath);out.putString("import",pendingImport)
        super.onSaveInstanceState(out)
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed(){if(busy)status.text="实验或下载正在进行，请先停止" else super.onBackPressed()}
    private fun selected()=models[picker.selectedItemPosition]
    private fun directory(m:Model)=File(LlamaEngine.modelDir(this),m.id)
    private fun now()=SystemClock.elapsedRealtime()
    private fun ByteArray.hex()=joinToString(""){ "%02x".format(it.toInt() and 255) }
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun checkStop(){if(stopping)throw CancellationException("用户停止")}
    private fun readManifest():List<Model>{
        val json=JSONObject(assets.open("lab_models.json").bufferedReader().use{it.readText()})
        val a=json.getJSONArray("models")
        return (0 until a.length()).map{i->
            val m=a.getJSONObject(i);val fs=m.getJSONArray("files")
            Model(m.getString("id"),m.getString("title"),(0 until fs.length()).map{j->
                val f=fs.getJSONObject(j)
                Weight(f.getString("name"),f.getLong("size"),f.getString("sha256"),f.getString("url"))
            })
        }
    }
    private fun operate(title:String,work:suspend ()->Unit){
        if(busy)return
        busy=true;stopping=false;controls.forEach{it.isEnabled=false};stop.isEnabled=true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status.text=title
        lifecycleScope.launch{
            try{work();status.text="完成："+title}
            catch(e:CancellationException){status.text="已停止或超时";if(!isActive)throw e}
            catch(e:Exception){status.text="失败："+(e.message?:e.javaClass.simpleName);append("operation_failed "+e.javaClass.simpleName)}
            finally{
                busy=false;stop.isEnabled=false;controls.forEach{it.isEnabled=true}
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    private fun loadPhoto(cleanup:()->Unit={},open:()->InputStream){
        operate("读取图片"){
            val p=withContext(Dispatchers.IO){
                try{
                    val bytes=open().use{input->
                        val out=ByteArrayOutputStream();val b=ByteArray(65536)
                        while(true){checkStop();val n=input.read(b);if(n<0)break
                            require(out.size().toLong()+n<=24L*1024*1024){"图片超过24 MiB"};out.write(b,0,n)}
                        out.toByteArray()
                    }
                    val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts)
                    require(opts.outMimeType in listOf("image/jpeg","image/png") &&
                        opts.outWidth>0 && opts.outHeight>0 && opts.outWidth.toLong()*opts.outHeight<=32_000_000){"仅支持不超过3200万像素的 JPEG/PNG"}
                    Photo(bytes,opts.outWidth,opts.outHeight,digest(bytes))
                }finally{cleanup()}
            }
            photo=p;imageLabel.text="图片 "+p.width+"×"+p.height+" · "+p.bytes.size+" B · "+p.sha.take(12)
        }
    }
    private fun verified(file:File,w:Weight):Boolean{
        if(!file.isFile || file.length()!=w.size)return false
        val md=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use{input->
            val b=ByteArray(1024*1024)
            while(true){checkStop();val n=input.read(b);if(n<0)break;md.update(b,0,n)}
        }
        return md.digest().hex()==w.sha
    }
    private suspend fun transfer(m:Model,folder:Uri?)=withContext(Dispatchers.IO){
        val dir=directory(m);check(dir.exists()||dir.mkdirs()){"无法创建模型目录"}
        val tree=folder?.let{DocumentFile.fromTreeUri(this@LabActivity,it)?:error("无效文件夹")}
        val needed=m.weights.sumOf{w->val f=File(dir,w.name);val tmp=File(f.path+".part")
            if(f.isFile && f.length()==w.size)0L else w.size-(if(folder==null)tmp.length().coerceIn(0,w.size) else 0L)}
        require(StatFs(dir.absolutePath).availableBytes>=needed+512L*1024*1024){"存储空间不足"}
        for((index,w) in m.weights.withIndex()){
            checkStop();val dst=File(dir,w.name);dst.parentFile?.mkdirs()
            withContext(Dispatchers.Main){status.text=m.title+" "+(index+1)+"/"+m.weights.size+" 校验 "+w.name}
            if(verified(dst,w))continue
            if(dst.exists())check(dst.delete()){"无法移除损坏文件"}
            val part=File(dst.path+".part")
            if(tree!=null){
                var doc:DocumentFile?=tree
                for(seg in w.name.split('/'))doc=doc?.findFile(seg)
                val src=doc?:error("缺少 "+w.name)
                check(src.isFile){"不是文件："+w.name}
                contentResolver.openInputStream(src.uri)!!.use{input->FileOutputStream(part,false).use{out->copyWeight(input,out,0,w,index,m)}}
            }else{
                if(part.length()>w.size)part.delete()
                if(part.length()!=w.size){
                    var offset=part.length()
                    val c=URL(w.url).openConnection() as HttpURLConnection
                    try{
                        c.connectTimeout=15000;c.readTimeout=20000;c.instanceFollowRedirects=true
                        c.setRequestProperty("Accept-Encoding","identity")
                        if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-")
                        val rc=c.responseCode
                        if(rc==200)offset=0
                        else require(rc==206 && c.getHeaderField("Content-Range")=="bytes "+offset+"-"+(w.size-1)+"/"+w.size){"HTTP "+rc+" 或断点不匹配"}
                        c.inputStream.use{input->FileOutputStream(part,offset>0).use{out->copyWeight(input,out,offset,w,index,m)}}
                    }finally{c.disconnect()}
                }
            }
            if(!verified(part,w)){part.delete();error("模型 SHA-256 校验失败："+w.name)}
            check(part.renameTo(dst)){"模型文件提交失败"}
        }
    }
    private suspend fun copyWeight(input:InputStream,out:OutputStream,start:Long,w:Weight,index:Int,m:Model){
        val b=ByteArray(256*1024);var count=start;var last=0L
        while(true){checkStop();val n=input.read(b);if(n<0)break
            require(count+n<=w.size){"模型大小超出预期"};out.write(b,0,n);count+=n
            if(now()-last>500){last=now();val pct=count*100/w.size
                withContext(Dispatchers.Main){status.text=m.title+" "+(index+1)+"/"+m.weights.size+" "+w.name+" "+pct+"%"}}
        }
        require(count==w.size){"文件未下载完整；下次可续传"}
    }
    private fun append(s:String){
        rows.add(s);if(rows.size>150)rows.removeAt(0)
        log.text=rows.joinToString("\n")
        getSharedPreferences("lab_log",MODE_PRIVATE).edit().putString("rows",rows.joinToString("\n")).apply()
    }
    private fun batteryTemp():Int? {
        val x=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return x?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)?.takeUnless{it==Int.MIN_VALUE}
    }
    private fun charge():Int?=(getSystemService(BATTERY_SERVICE) as BatteryManager)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeUnless{it==Int.MIN_VALUE || it<=0}
    private fun startTest(chosen:List<Model>){
        val p=photo?:run{status.text="请先拍照或导入图片";return}
        val prompt=if(task.selectedItemPosition==tasks.lastIndex)custom.text.toString().trim() else tasks[task.selectedItemPosition].second
        val ref=reference.text.toString()
        if(prompt.isBlank()||prompt.length>1500||ref.length>4096){status.text="提示词须1–1500字；参考文字最多4096字";return}
        val taskIndex=task.selectedItemPosition
        operate("离线实验"){
            withTimeout(30000){engine.state.first{it !is LlamaState.Uninitialized && it !is LlamaState.Initializing}}
            check(engine.state.value is LlamaState.Initialized){"推理引擎未就绪，请退出重开实验工具"}
            for(m in chosen){
                checkStop()
                status.text="校验 "+m.title+" 模型文件"
                withContext(Dispatchers.IO){for(w in m.weights)require(verified(File(directory(m),w.name),w)){"未安装或校验失败："+m.title+" / "+w.name}}
                val am=getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val mem=ActivityManager.MemoryInfo();am.getMemoryInfo(mem)
                if(m.id.endsWith("35b"))require(mem.totalMem>=10L*1024*1024*1024 && !mem.lowMemory){"高性能版需要约12GB物理内存；请释放内存"}
                LlamaEngine.setSelectedModel(applicationContext,m.id)
                LlamaEngine.setEnableThinkingPref(applicationContext,false)
                LlamaEngine.setImageMaxSliceNumsPref(applicationContext,9)
                val loadStart=now()
                try{
                    status.text="加载 "+m.title
                    engine.loadModel(LlamaEngine.modelPath(this),LlamaEngine.mmprojPath(this))
                    val loadMs=now()-loadStart
                    check(engine.isVisionSupported){"视觉投影器加载失败，禁止文本降级"}
                    for(run in 0..2){
                        checkStop()
                        val resetStart=now()
                        if(run>0)engine.clearContext()
                        val reset=if(run==0)0L else now()-resetStart
                        status.text=m.title+" 第"+(run+1)+"轮"
                        output.text=""
                        val started=now();val cpu=Process.getElapsedCpuTime();val temp=batteryTemp();val ch=charge()
                        var first=-1L;var speak=-1L;var maxPss=Debug.getPss()
                        val text=StringBuilder()
                        val sampler=lifecycleScope.launch{while(isActive){maxPss=maxOf(maxPss,Debug.getPss());delay(500)}}
                        try{
                            engine.prefillImage(p.bytes)
                            val accept=now()-started
                            engine.sendUserPrompt(prompt,512).collect{chunk->
                                checkStop();text.append(chunk)
                                if(first<0 && chunk.any{!it.isWhitespace()})first=now()-started
                                if(speak<0 && (text.length>=12 || text.any{it in "。！？；\n.!?"}))speak=now()-started
                                output.text=text.toString()
                            }
                            checkStop()
                            check(first>=0 && engine.state.value is LlamaState.ModelReady){"推理未产生有效输出"}
                            val elapsed=now()-started;val cpuMs=Process.getElapsedCpuTime()-cpu
                            val t2=batteryTemp();val c2=charge()
                            val cer=if(ref.isNotBlank()&&taskIndex==0)LabMetrics.cer(ref,text.toString()).toString() else "NA"
                            append(listOf("model="+m.id,"run="+(run+1),"phase="+if(run==0)"cold" else "warm",
                                "image="+p.width+"x"+p.height,"sha="+p.sha.take(12),"task="+taskIndex,"slices=9","max_tokens=512",
                                "load_ms="+if(run==0)loadMs else 0L,"reset_ms="+reset,"image_accept_ms="+accept,
                                "prompt_prefill_ms="+engine.labPromptMs,"first_text_ms="+first,
                                "first_speakable_est_ms="+if(speak<0)elapsed else speak,"total_ms="+elapsed,
                                "tokens="+engine.labGeneratedTokens,"generation_tok_s="+LabMetrics.rate(engine.labGeneratedTokens,elapsed-accept-engine.labPromptMs),
                                "cpu_process_ms="+cpuMs,"pss_sample_max_kib="+maxPss,"temp_start_deciC="+temp,"temp_end_deciC="+t2,
                                "charge_delta_uAh="+if(ch!=null&&c2!=null)ch-c2 else "NA","cer="+cer).joinToString(" "))
                        }finally{sampler.cancelAndJoin()}
                    }
                }finally{withContext(NonCancellable){engine.labRelease()}}
            }
        }
    }
}
