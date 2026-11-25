package com.vobich.bluestar.npulab

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.*
import android.os.Process
import android.os.Message
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

class LabService:Service(){
    private val worker=Executors.newSingleThreadExecutor()
    private var started=false
    private var residentEngine:Engine?=null
    private var residentMode:String?=null
    private val locator by lazy { TextRegionLocator(this) }
    private val main=Handler(Looper.getMainLooper())
    private val incoming=Messenger(Handler(Looper.getMainLooper()){msg->
        when(msg.what){
            LabProtocol.START->{
                if(!started){
                    started=true
                    val reply=msg.replyTo
                    val input=Bundle(msg.data)
                    send(reply,LabProtocol.HELLO,Bundle().apply{putInt("pid",Process.myPid())})
                    worker.execute{
                        try{runLab(input,reply);main.post{started=false;send(reply,LabProtocol.DONE)}}
                        catch(t:Throwable){main.post{started=false;send(reply,LabProtocol.FAILED,Bundle().apply{putString("line","error="+t.javaClass.simpleName)})}}
                    }
                }
                true
            }
            LabProtocol.STOP->{Process.killProcess(Process.myPid());true}
            else->false
        }
    })
    override fun onBind(intent:Intent):IBinder=incoming.binder
    private fun send(reply:Messenger,what:Int,data:Bundle=Bundle()){
        try{reply.send(Message.obtain(null,what).apply{this.data=data})}catch(_:RemoteException){Process.killProcess(Process.myPid())}
    }
    private fun row(reply:Messenger,s:String)=send(reply,LabProtocol.ROW,Bundle().apply{putString("line",s)})
    private fun stage(reply:Messenger,name:String,run:Int=0)=send(reply,LabProtocol.STAGE,Bundle().apply{putString("stage",name);putInt("run",run)})
    private fun now()=SystemClock.elapsedRealtime()
    private fun sha(f:File):String{
        val d=MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)}}
        return d.digest().joinToString(""){"%02x".format(it.toInt() and 255)}.take(12)
    }
    @OptIn(ExperimentalApi::class)
    private fun runLab(input:Bundle,reply:Messenger){
        val mode=requireNotNull(input.getString("mode"));require(mode in LabProtocol.modes)
        val model=File(requireNotNull(input.getString("model")));require(model.isFile && model.length()==1698202736L)
        val image=File(requireNotNull(input.getString("image")));require(image.isFile)
        val ref=input.getString("ref","")
        val hash=sha(image)
        val dir=applicationInfo.nativeLibraryDir
        val dispatch=File(dir,"libLiteRtDispatch_MediaTek.so").isFile
        val compiler=File(dir,"libLiteRtCompilerPlugin_MediaTek.so").isFile
        val soc=if(Build.VERSION.SDK_INT>=31)Build.SOC_MODEL else "unknown"
        val batch=input.getLong("batch");val block=input.getInt("block")
        val prefix="v="+MainActivity.V+" requested="+mode+" batch="+batch+" block="+block
        val textBackend=Backend.CPU(threadCount=6)
        val visionBackend=if(mode=="gpu_vision")Backend.GPU() else Backend.CPU(threadCount=6)
        row(reply,prefix+" phase=begin text="+textBackend.name+" vision="+visionBackend.name+" soc="+soc.replace(" ","_")+" image_sha="+hash+" dispatch_present="+dispatch+" compiler_present="+compiler+" hardware_execution=unverified")
        stage(reply,"load")
        ExperimentalFlags.enableBenchmark=true
        ExperimentalFlags.enableSpeculativeDecoding=false
        val reused=residentEngine!=null
        require(!reused||residentMode==mode)
        val load=now()
        val e=residentEngine?:Engine(EngineConfig(modelPath=model.absolutePath,backend=textBackend,visionBackend=visionBackend,maxNumImages=1,cacheDir=File(cacheDir,"backend-"+mode).apply{mkdirs()}.absolutePath)).also{
            it.initialize();residentEngine=it;residentMode=mode
        }
        row(reply,prefix+" phase=loaded load_ms="+(now()-load)+" resident_reused="+reused+" hardware_execution=unverified")
        val strategy=input.getString("strategy","compare")
        require(strategy in LabProtocol.strategies)
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(image.absolutePath,bounds)
        require(bounds.outWidth>0&&bounds.outHeight>0)
        val plan=LabProtocol.plan(strategy)
        val partDir=File(cacheDir,"ocr-prepared").apply{mkdirs()}
        partDir.listFiles()?.forEach{it.delete()}
        val prepared=mutableMapOf<String,List<ImageParts.Part>>()
        try{
            for((idx,kind) in plan.withIndex()){
                val run=idx+1
                stage(reply,"prepare",run)
                val prep=now()
                val cached=prepared.containsKey(kind)
                val parts=prepared.getOrPut(kind){
                    if(kind=="roi")locator.prepare(image,partDir){ detail->row(reply,prefix+" run="+run+" "+detail)}
                    else ImageParts.prepare(image,kind,partDir,bounds.outWidth,bounds.outHeight)
                }
                val prepMs=now()-prep
                row(reply,prefix+" run="+run+" phase=prepared strategy="+kind+" source_width="+bounds.outWidth+" source_height="+bounds.outHeight+" parts="+parts.size+" prepare_ms="+prepMs+" prepare_reused="+cached)
                val jobStart=prep
                val cpuStart=Process.getElapsedCpuTime()
                val tempBefore=batteryTemp();val thermalBefore=thermal()
                val answers=mutableListOf<String>()
                var first=-1L;var tokens=0;var tokenKnown=true;var capHits=0;var thought=0;var sessions=0L
                for((partIdx,part) in parts.withIndex()){
                    val n=partIdx+1
                    stage(reply,"conversation",run)
                    val sessionStart=now()
                    val c=e.createConversation(ConversationConfig(samplerConfig=SamplerConfig(topK=1,topP=1.0,temperature=0.0,seed=42),maxOutputToken=512,thinkingConfig=ThinkingConfig(enableThinking=false,thinkingTokenBudget=0),extraContext=mapOf("enable_thinking" to false),automaticToolCalling=false))
                    val session=now()-sessionStart;sessions+=session
                    try{
                        val raw=StringBuilder();var partFirst=-1L;var chunks=0;var emitted=0
                        stage(reply,"send",run)
                        val sendStart=now()
                        runBlocking{
                            c.sendMessageAsync(Contents.of(Content.ImageFile(part.file.absolutePath),Content.Text(MainActivity.PROMPT)),extraContext=mapOf("enable_thinking" to false)).collect{msg->
                                val content=msg.contents.contents.filterIsInstance<Content.Text>().joinToString(""){it.text}
                                if(msg.channels.isNotEmpty())thought++
                                if(content.isNotEmpty()){raw.append(content);chunks++}
                                val visible=Metrics.visible(raw.toString())
                                if(partFirst<0&&visible.any{!it.isWhitespace()}){
                                    partFirst=now()-sendStart
                                    if(first<0)first=now()-jobStart
                                    stage(reply,"first_text",run)
                                }
                                if((strategy=="original"||strategy=="roi") && visible.length>emitted){
                                    val delta=visible.substring(emitted);emitted=visible.length
                                    send(reply,LabProtocol.STREAM,Bundle().apply{putString("text",delta);putLong("start",jobStart);putBoolean("final",false)})
                                }
                            }
                        }
                        val total=now()-sendStart
                        val answer=Metrics.visible(raw.toString()).trim()
                        if((strategy=="original"||strategy=="roi"))send(reply,LabProtocol.STREAM,Bundle().apply{putLong("start",jobStart);putBoolean("final",true)})
                        answers.add(answer)
                        val b=runCatching{c.getBenchmarkInfo()}.getOrNull()
                        val decode=(b?.lastDecodeTokenCount?:-1).toInt()
                        if(decode<0)tokenKnown=false else tokens+=decode
                        if(decode>=512)capHits++
                        row(reply,prefix+" run="+run+" phase=part_complete strategy="+kind+" part="+n+"/"+parts.size+" rect="+part.rect+" session_ms="+session+" first_nonblank_ms="+partFirst+" total_ms="+total+" chars="+answer.length+" chunks="+chunks+" decode_tokens="+decode+" native_ttft_s="+(b?.timeToFirstTokenInSecond?:-1.0)+" native_decode_tps="+(b?.lastDecodeTokensPerSecond?:-1.0))
                    }finally{stage(reply,"close_conversation",run);c.close()}
                }
                val answer=answers.filter{it.isNotBlank()}.joinToString("\n")
                val total=now()-jobStart
                val cpu=Process.getElapsedCpuTime()-cpuStart
                val cer=Metrics.cer(ref,answer)?.let{String.format(Locale.US,"%.4f",it)}?:"NA"
                val normalized=answer.filterNot{it.isWhitespace()}
                val digest=MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}.take(12)
                val phase=if(answer.isBlank())"empty" else "complete"
                row(reply,prefix+" run="+run+" phase="+phase+" strategy="+kind+" image_sha="+hash+" prepare_ms="+prepMs+" parts="+parts.size+" empty_parts="+answers.count{it.isBlank()}+" session_ms="+sessions+" first_nonblank_ms="+first+" total_ms="+total+" chars="+answer.length+" nonspace_chars="+normalized.length+" answer_sha="+digest+" repeated_lines="+ImageParts.repeatedLines(answer)+" token_cap_parts="+capHits+" decode_tokens="+(if(tokenKnown)tokens else -1)+" thought_chunks="+thought+" cer="+cer+" cpu_process_ms="+cpu+" pss_kib="+Debug.getPss()+" battery_temp_before_decic="+tempBefore+" battery_temp_after_decic="+batteryTemp()+" thermal_before="+thermalBefore+" thermal_after="+thermal()+" resident_reused="+reused+" hardware_execution=unverified")
                send(reply,LabProtocol.RESULT,Bundle().apply{putString("text","第"+run+"次 "+kind+"（"+parts.size+"区，CER="+cer+"）\n"+answer.take(16384).ifBlank{"（空答案）"})})
            }
        }finally{partDir.listFiles()?.forEach{it.delete()}}
        row(reply,prefix+" phase=engine_retained pss_kib="+Debug.getPss())
    }
    private fun batteryTemp():Int=runCatching{registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE,-1)?:-1}.getOrDefault(-1)
    private fun thermal():Int=if(Build.VERSION.SDK_INT>=29)runCatching{(getSystemService(POWER_SERVICE) as PowerManager).currentThermalStatus}.getOrDefault(-1)else -1
}
