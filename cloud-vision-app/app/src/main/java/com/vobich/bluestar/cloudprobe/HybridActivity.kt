package com.vobich.bluestar.cloudprobe

import android.app.Activity
import android.content.*
import android.os.*
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.*
import com.vobich.bluestar.openocrlab.SpeechChunks
import java.util.concurrent.Executors

class HybridActivity:Activity(){
    private val worker=Executors.newSingleThreadExecutor()
    private var cancel:CloudVisionClient.Cancellation?=null
    private var tts:TextToSpeech?=null
    private var ready=false
    private var closed=false
    private var busy=false
    private var sequence=0
    private lateinit var report:TextView
    private lateinit var raw:TextView
    private lateinit var switch:Switch
    private lateinit var provider:Spinner
    private lateinit var model:Spinner
    private lateinit var instructionInput:EditText
    private lateinit var resetInstruction:Button
    private lateinit var hybrid:Button
    private lateinit var visual:Button
    private lateinit var offline:Button
    private lateinit var album:Button
    private lateinit var sourceLabel:TextView
    private var albumPhoto:ByteArray?=null
    private var albumId=""
    private var firstQueued=false
    private var partNo=0
    private var firstCallback=false
    private var firstAccepted=false
    private var profiles=emptyList<CloudProviderStore.Profile>()
    private val metrics=ArrayDeque<String>()
    private fun now()=SystemClock.elapsedRealtime()
    private fun log(s:String){if(metrics.size>=120)metrics.removeFirst();metrics.addLast(s);report.text=metrics.joinToString("\n\n")}
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val scroll=ScrollView(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,30,20,30)}
        scroll.addView(root);setContentView(scroll)
        fun label(s:String)=TextView(this).apply{text=s;textSize=18f;root.addView(this)}
        fun button(s:String,action:()->Unit)=Button(this).apply{text=s;root.addView(this);setOnClickListener{action()}}
        label("端云OCR纯文字整理实验 v0.1.3")
        label("端云仅发送识别原文，不发送照片或坐标；AI归类有效文字、过滤乱码，直接返回朗读内容。启用后快捷拍照的朗读文字任务走实验通道；寻找物品和其他任务不变。")
        switch=Switch(this).apply{text="启用端云OCR拍照通道";isChecked=HybridExperiment.enabled(this@HybridActivity);root.addView(this)}
        profiles=CloudProviderStore.list(this).filter{it.protocol==CloudProviderStore.CHAT}
        provider=Spinner(this).apply{
            adapter=ArrayAdapter(this@HybridActivity,android.R.layout.simple_spinner_dropdown_item,listOf("原有百炼API")+profiles.map{it.name+" · "+it.model})
            setSelection((profiles.indexOfFirst{it.id==HybridExperiment.profile(this@HybridActivity)}+1).coerceAtLeast(0));root.addView(this)
        }
        model=Spinner(this).apply{
            adapter=ArrayAdapter(this@HybridActivity,android.R.layout.simple_spinner_dropdown_item,VisionModelCatalog.IDS.toList())
            setSelection(VisionModelCatalog.indexOf(HybridExperiment.model(this@HybridActivity)).let{if(it<0)0 else it});root.addView(this)
        }
        label("百炼模型选择仅适用于原有百炼API；自定义服务使用自己的模型。API Key在原蓝星设置中配置。")
        label("云端AI任务（可编辑）")
        instructionInput=EditText(this).apply{
            hint="输入给云端AI的任务"
            contentDescription="云端AI任务，可编辑"
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity=android.view.Gravity.TOP or android.view.Gravity.START
            minLines=4;maxLines=10
            setText(HybridExperiment.instruction(this@HybridActivity))
            root.addView(this)
        }
        resetInstruction=button("恢复默认AI任务"){if(!busy){
            instructionInput.setText(HybridExperiment.DEFAULT_INSTRUCTION)
            HybridExperiment.saveInstruction(this,HybridExperiment.DEFAULT_INSTRUCTION)
        }}
        label("修改后直接点击同图端云朗读即可生效并保存，无需重新导入；OCR原文自动附加，纯离线和原云端图片对照不受影响。")
        button("保存实验设置"){if(!busy){save();Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show()}}
        label("相册选图后自动执行端云流式朗读；下方三种模式使用同一张相册原图，未导入时使用最近快捷拍照。纯云端对照会上传原JPEG，端云只上传识别文本。")
        sourceLabel=label("来源：最近快捷拍照")
        album=button("导入相册照片并自动测试"){
            if(!busy)startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"},41)
        }
        button("切回最近快捷拍照"){if(!busy){albumPhoto=null;albumId="";sourceLabel.text="来源：最近快捷拍照"}}
        hybrid=button("同图 · 端云流式朗读"){start("HYBRID")}
        offline=button("同图 · 纯离线OCR"){start("OFFLINE")}
        visual=button("同图 · 原云端图片识别"){start("VISUAL")}
        button("取消识别 / 停止朗读"){cancel?.cancel();tts?.stop();log("CANCEL run="+sequence)}
        button("复制数值诊断"){
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Hybrid OCR",metrics.joinToString("\n")))
        }
        button("返回"){finish()}
        report=label("暂无测试");report.setTextIsSelectable(true)
        raw=label("");raw.setTextIsSelectable(true)
        HybridExperiment.latestResult()?.let{log(it.details);raw.text="本地原文：\n"+it.raw+"\n\n整理结果：\n"+it.text}
        tts=TextToSpeech(this){code->runOnUiThread{if(!closed){ready=code==TextToSpeech.SUCCESS
            if(ready)tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                override fun onStart(id:String){val at=now();runOnUiThread{
                    val fields=id.split(":")
                    if(!closed&&fields.size==3&&fields[0]==sequence.toString()&&!firstCallback){firstCallback=true;log("TTS_FIRST run="+sequence+" tts_on_start_ms="+(at-fields[1].toLong())+" callback_only=1")}
                }}
                override fun onDone(id:String){}
                override fun onError(id:String){runOnUiThread{if(!closed)log("tts_error")}}
            })
        }}}
    }
    private fun save(){
        HybridExperiment.saveInstruction(this,instructionInput.text.toString())
        HybridExperiment.save(this,switch.isChecked,profiles.getOrNull(provider.selectedItemPosition-1)?.id?:"",VisionModelCatalog.IDS[model.selectedItemPosition.coerceAtLeast(0)])
    }
    private fun controls(active:Boolean){
        busy=!active;hybrid.isEnabled=active;offline.isEnabled=active;visual.isEnabled=active;album.isEnabled=active;instructionInput.isEnabled=active;resetInstruction.isEnabled=active;provider.isEnabled=active;model.isEnabled=active;switch.isEnabled=active
    }
    private fun beginRun(at:Long,mode:String):Pair<Int,CloudVisionClient.Cancellation>{
        save();controls(false);tts?.stop();val seq=++sequence
        firstQueued=false;firstCallback=false;firstAccepted=false;partNo=0
        val job=CloudVisionClient.Cancellation();cancel=job
        raw.text="测试中…";log("BEGIN run="+seq+" mode="+mode+" tts_ready="+ready)
        return Pair(seq,job)
    }
    override fun onActivityResult(req:Int,res:Int,data:Intent?){
        super.onActivityResult(req,res,data)
        if(req!=41||res!=RESULT_OK||busy)return
        val uri=data?.data?:return
        val at=now()
        val (seq,job)=beginRun(at,"HYBRID_ALBUM")
        val task=HybridExperiment.instruction(this)
        worker.execute{
            try{
                val bytes=contentResolver.openInputStream(uri)?.use{input->
                    val output=ByteArrayOutputStream()
                    val buf=ByteArray(65536)
                    while(true){
                        job.check();val n=input.read(buf);if(n<0)break
                        if(output.size().toLong()+n>64L*1024*1024)throw IllegalArgumentException("原图文件超过64MiB")
                        output.write(buf,0,n)
                    }
                    output.toByteArray()
                }?:error("无法读取照片")
                require(bytes.isNotEmpty())
                val id=MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString(""){"%02x".format(it.toInt() and 255)}
                val importMs=now()-at
                job.check()
                runOnUiThread{if(!closed&&seq==sequence&&!job.isCancelled()){
                    albumPhoto=bytes;albumId=id;sourceLabel.text="来源：相册原图 "+bytes.size+" bytes · "+id
                    log("IMPORT run="+seq+" sample="+id+" import_ms="+importMs+" bytes="+bytes.size)
                }}
                executeRun("HYBRID",bytes,null,"album",id,seq,at,importMs,job,task)
            }catch(t:Throwable){failed(t,seq,job)}
            finally{finishRun(seq)}
        }
    }
    private fun start(mode:String){
        if(busy)return
        val imported=albumPhoto
        val snap=if(imported==null)RecentPhotoSession.latest() else null
        val bytes=imported?:snap?.jpeg?:run{Toast.makeText(this,"请先导入相册或快捷拍照",Toast.LENGTH_LONG).show();return}
        val at=now()
        val (seq,job)=beginRun(at,mode)
        val task=HybridExperiment.instruction(this)
        val source=if(imported!=null)"album" else "recent"
        worker.execute{
            try{
                val id=if(imported!=null)albumId else MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString(""){"%02x".format(it.toInt() and 255)}
                executeRun(mode,bytes,snap?.cloudNetwork,source,id,seq,at,0,job,task)
            }catch(t:Throwable){failed(t,seq,job)}
            finally{finishRun(seq)}
        }
    }
    private fun executeRun(mode:String,bytes:ByteArray,network:android.net.Network?,source:String,id:String,seq:Int,at:Long,importMs:Long,job:CloudVisionClient.Cancellation,taskInstruction:String){
        val common=" run="+seq+" sample="+id+" source="+source+" import_ms="+importMs
        var delivered=0
        val listener=CloudVisionClient.PartListener{part->
            if(part.isNotBlank()&&!job.isCancelled()){
                delivered++
                val readyMs=now()-at
                runOnUiThread{if(!closed&&seq==sequence&&!job.isCancelled()){
                    if(!firstQueued)log("FIRST_SPEAKABLE"+common+" ready_ms="+readyMs)
                    queue(part,seq,at)
                }}
            }
        }
        if(mode!="VISUAL"){
            val r=HybridExperiment.runTest(this,bytes,network,job,if(mode=="HYBRID")listener else null,mode=="OFFLINE",taskInstruction)
            job.check();HybridExperiment.record(r)
            val done=now()-at;val streamed=delivered
            runOnUiThread{if(!closed&&seq==sequence&&!job.isCancelled()){
                log(mode+common+" "+r.details+" end_to_end_ms="+done+" streamed_parts="+streamed)
                raw.text=r.label+"\n本地原文：\n"+r.raw+"\n\n结果：\n"+r.text
                if(mode=="HYBRID"&&!r.cloudOk&&streamed>0){
                    tts?.stop();log("PARTIAL_CLOUD_FAILURE"+common+" invalid_for_comparison=1; 请用同图纯离线重测")
                }else if(streamed==0)queue(r.text,seq,at)
                if(r.text.isBlank())log("NO_TEXT"+common+" no_tts=1 cloud_ok="+(if(r.cloudOk)1 else 0))
            }}
        }else{
            require(bytes.size>=3&&(bytes[0].toInt() and 255)==255&&(bytes[1].toInt() and 255)==216&&(bytes[2].toInt() and 255)==255){"纯云端原图对照仅支持JPEG；端云及纯离线仍可识别其他系统支持图片"}
            val p=getSharedPreferences("kanzi_settings",MODE_PRIVATE)
            val task=TaskCatalog.defaults()[0]
            val service=CloudProviderStore.resolve(this,CloudProviderStore.OCR,OcrModelCatalog.normalize(p.getString("cloud_ocr_model_v1",OcrModelCatalog.DEFAULT)))
            val r=CloudVisionClient.recognize(bytes,service,task,listener,OcrSpeedExperiment.C,network,job)
            job.check();val done=now()-at;val streamed=delivered
            runOnUiThread{if(!closed&&seq==sequence&&!job.isCancelled()){
                log("VISUAL"+common+" model="+r.model+" jpeg_bytes="+bytes.size+" request_bytes="+r.requestBytes+" upload_ms="+r.uploadMs+" network_ms="+r.networkMs+" first_text_ms="+r.firstTextMs+" first_speakable_ms="+r.firstSpeakableMs+" prompt_tokens="+r.promptTokens+" completion_tokens="+r.completionTokens+" end_to_end_ms="+done+" streamed_parts="+streamed)
                raw.text="原云端识别结果：\n"+r.text
                if(streamed==0)queue(r.text,seq,at)
            }}
        }
    }
    private fun failed(t:Throwable,seq:Int,job:CloudVisionClient.Cancellation){
        runOnUiThread{if(!closed&&seq==sequence){
            tts?.stop()
            log("FAILED run="+seq+" reason="+t.javaClass.simpleName+" cancelled="+job.isCancelled())
            raw.text=if(job.isCancelled())"已取消" else if(t is IllegalArgumentException)t.message?:"图片不支持" else "测试未完成，请检查图片、API设置及网络。"
        }}
    }
    private fun finishRun(seq:Int){runOnUiThread{if(!closed&&seq==sequence){controls(true);cancel=null}}}
    private fun queue(text:String,seq:Int,at:Long){
        if(text.isBlank())return
        if(!ready){log("TTS_NOT_READY run="+seq+" no_tts=1");return}
        for(part in SpeechChunks.split(text,TextToSpeech.getMaxSpeechInputLength())){
            val code=tts?.speak(part,if(firstAccepted)TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,null,seq.toString()+":"+at+":"+(partNo++))
            if(code==TextToSpeech.SUCCESS&&!firstAccepted){
                firstAccepted=true;firstQueued=true;log("TTS_ENQUEUE run="+seq+" first_enqueue_ms="+(now()-at))
            }else if(code!=TextToSpeech.SUCCESS)log("TTS_ENQUEUE_FAILED run="+seq+" code="+code)
        }
    }
    override fun onDestroy(){closed=true;cancel?.cancel();tts?.stop();tts?.shutdown();worker.shutdownNow();super.onDestroy()}
}
