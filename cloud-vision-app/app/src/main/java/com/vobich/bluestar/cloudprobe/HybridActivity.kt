package com.vobich.bluestar.cloudprobe

import android.app.Activity
import android.content.*
import android.os.*
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
    private lateinit var hybrid:Button
    private lateinit var visual:Button
    private var profiles=emptyList<CloudProviderStore.Profile>()
    private val metrics=ArrayDeque<String>()
    private fun now()=SystemClock.elapsedRealtime()
    private fun log(s:String){if(metrics.size>=30)metrics.removeFirst();metrics.addLast(s);report.text=metrics.joinToString("\n\n")}
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val scroll=ScrollView(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,30,20,30)}
        scroll.addView(root);setContentView(scroll)
        fun label(s:String)=TextView(this).apply{text=s;textSize=18f;root.addView(this)}
        fun button(s:String,action:()->Unit)=Button(this).apply{text=s;root.addView(this);setOnClickListener{action()}}
        label("端云OCR独立实验 v0.1.1")
        label("仅文字框坐标和识别原文发送云端，直接返回整理后的文字。启用后快捷拍照的朗读文字任务走实验通道；寻找物品和其他任务不变。")
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
        button("保存实验设置"){save();Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show()}
        label("先用快捷拍照获取一张照片，再返回本页。以下两个按钮使用内存中同一张照片。")
        hybrid=button("最近照片 · 本地OCR＋云端整理"){start(true)}
        visual=button("最近照片 · 原云端图片识别"){start(false)}
        button("取消识别 / 停止朗读"){cancel?.cancel();tts?.stop()}
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
                    if(!closed&&fields.size==3&&fields[0]==sequence.toString()&&fields[2]=="0")log("tts_on_start_ms="+(at-fields[1].toLong()))
                }}
                override fun onDone(id:String){}
                override fun onError(id:String){runOnUiThread{if(!closed)log("tts_error")}}
            })
        }}}
    }
    private fun save(){
        HybridExperiment.save(this,switch.isChecked,profiles.getOrNull(provider.selectedItemPosition-1)?.id?:"",VisionModelCatalog.IDS[model.selectedItemPosition.coerceAtLeast(0)])
    }
    private fun start(useHybrid:Boolean){
        if(busy)return
        val snap=RecentPhotoSession.latest()?:run{Toast.makeText(this,"还没有最近照片，请先快捷拍照",Toast.LENGTH_LONG).show();return}
        save()
        busy=true;hybrid.isEnabled=false;visual.isEnabled=false
        tts?.stop();val seq=++sequence;val at=now()
        val job=CloudVisionClient.Cancellation();cancel=job
        raw.text="测试中…"
        worker.execute{
            try{
                if(useHybrid){
                    val r=HybridExperiment.run(this,snap.jpeg,snap.cloudNetwork,job)
                    job.check();HybridExperiment.record(r)
                    runOnUiThread{if(!closed&&seq==sequence&&!job.isCancelled()){
                        log("HYBRID "+r.details+" end_to_end_ms="+(now()-at))
                        raw.text=r.label+"\n本地原文：\n"+r.raw+"\n\n整理结果：\n"+r.text
                        speak(r.text,seq,at)
                    }}
                }else{
                    val p=getSharedPreferences("kanzi_settings",MODE_PRIVATE)
                    val task=TaskCatalog.defaults()[0]
                    val service=CloudProviderStore.resolve(this,CloudProviderStore.OCR,OcrModelCatalog.normalize(p.getString("cloud_ocr_model_v1",OcrModelCatalog.DEFAULT)))
                    val r=CloudVisionClient.recognize(snap.jpeg,service,task,null,OcrSpeedExperiment.C,snap.cloudNetwork,job)
                    job.check()
                    runOnUiThread{if(!closed&&seq==sequence&&!job.isCancelled()){
                        log("VISUAL model="+r.model+" jpeg_bytes="+snap.jpeg.size+" request_bytes="+r.requestBytes+" upload_ms="+r.uploadMs+" network_ms="+r.networkMs+" first_text_ms="+r.firstTextMs+" prompt_tokens="+r.promptTokens+" completion_tokens="+r.completionTokens+" end_to_end_ms="+(now()-at))
                        raw.text="原云端识别结果：\n"+r.text;speak(r.text,seq,at)
                    }}
                }
            }catch(t:Throwable){runOnUiThread{if(!closed&&seq==sequence){log("test_failed "+t.javaClass.simpleName);raw.text="测试未完成，请检查API设置与网络。"}}}
            finally{runOnUiThread{if(!closed&&seq==sequence){busy=false;hybrid.isEnabled=true;visual.isEnabled=true;cancel=null}}}
        }
    }
    private fun speak(text:String,seq:Int,at:Long){
        if(!ready||text.isBlank())return
        for((i,part) in SpeechChunks.split(text,TextToSpeech.getMaxSpeechInputLength()).withIndex()){
            tts?.speak(part,if(i==0)TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,seq.toString()+":"+at+":"+i)
        }
    }
    override fun onDestroy(){closed=true;cancel?.cancel();tts?.stop();tts?.shutdown();worker.shutdownNow();super.onDestroy()}
}
