package com.vobich.bluestar.npulab

import android.app.Service
import android.content.Intent
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
    private val incoming=Messenger(Handler(Looper.getMainLooper()){msg->
        when(msg.what){
            LabProtocol.START->{
                if(!started){
                    started=true
                    val reply=msg.replyTo
                    val input=Bundle(msg.data)
                    send(reply,LabProtocol.HELLO,Bundle().apply{putInt("pid",Process.myPid())})
                    worker.execute{
                        try{runLab(input,reply);send(reply,LabProtocol.DONE)}
                        catch(t:Throwable){send(reply,LabProtocol.FAILED,Bundle().apply{putString("line","error="+t.javaClass.simpleName)})}
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
        val prefix="v="+MainActivity.V+" requested="+mode
        val textBackend=if(mode=="gpu_all")Backend.GPU() else Backend.CPU(threadCount=6)
        val visionBackend=when(mode){"gpu_vision","gpu_all"->Backend.GPU();"npu_vision"->Backend.NPU(nativeLibraryDir=dir);else->Backend.CPU(threadCount=6)}
        row(reply,prefix+" phase=begin text="+textBackend.name+" vision="+visionBackend.name+" soc="+soc.replace(" ","_")+" image_sha="+hash+" dispatch_present="+dispatch+" compiler_present="+compiler+" hardware_execution=unverified")
        if(mode=="npu_vision")require(dispatch&&compiler)
        stage(reply,"load")
        ExperimentalFlags.enableBenchmark=true
        ExperimentalFlags.enableSpeculativeDecoding=false
        val e=Engine(EngineConfig(modelPath=model.absolutePath,backend=textBackend,visionBackend=visionBackend,maxNumImages=1,cacheDir=File(cacheDir,"backend-"+mode).apply{mkdirs()}.absolutePath))
        var initialized=false
        val load=now()
        try{
            e.initialize();initialized=true
            row(reply,prefix+" phase=loaded load_ms="+(now()-load)+" hardware_execution=unverified")
            for(run in 1..3){
                stage(reply,"conversation",run)
                val start=now()
                val c=e.createConversation(ConversationConfig(samplerConfig=SamplerConfig(topK=1,topP=1.0,temperature=0.0,seed=42),maxOutputToken=512,thinkingConfig=ThinkingConfig(enableThinking=false,thinkingTokenBudget=0),extraContext=mapOf("enable_thinking" to false),automaticToolCalling=false))
                val session=now()-start
                try{
                    row(reply,prefix+" run="+run+" phase=conversation_ready session_ms="+session)
                    val raw=StringBuilder()
                    var first=-1L;var chunks=0;var thought=0
                    val cpuStart=Process.getElapsedCpuTime()
                    stage(reply,"send",run)
                    val sendStart=now()
                    runBlocking{
                        c.sendMessageAsync(Contents.of(Content.ImageFile(image.absolutePath),Content.Text(MainActivity.PROMPT)),extraContext=mapOf("enable_thinking" to false)).collect{msg->
                            val content=msg.contents.contents.filterIsInstance<Content.Text>().joinToString(""){it.text}
                            if(msg.channels.isNotEmpty())thought++
                            if(content.isNotEmpty()){raw.append(content);chunks++}
                            if(first<0&&Metrics.visible(raw.toString()).any{!it.isWhitespace()}){
                                first=now()-sendStart
                                stage(reply,"first_text",run)
                                row(reply,prefix+" run="+run+" phase=first_text first_nonblank_ms="+first)
                            }
                        }
                    }
                    val total=now()-sendStart
                    val cpu=Process.getElapsedCpuTime()-cpuStart
                    val answer=Metrics.visible(raw.toString()).trim()
                    val b=runCatching{c.getBenchmarkInfo()}.getOrNull()
                    val cer=Metrics.cer(ref,answer)?.let{String.format(Locale.US,"%.4f",it)}?:"NA"
                    val phase=if(answer.isBlank())"empty" else "complete"
                    row(reply,prefix+" run="+run+" phase="+phase+" image_sha="+hash+" session_ms="+session+" first_nonblank_ms="+first+" total_ms="+total+" chars="+answer.length+" chunks="+chunks+" thought_chunks="+thought+" cer="+cer+" native_ttft_s="+(b?.timeToFirstTokenInSecond?:-1.0)+" prefill_tokens="+(b?.lastPrefillTokenCount?:-1)+" decode_tokens="+(b?.lastDecodeTokenCount?:-1)+" native_decode_tps="+(b?.lastDecodeTokensPerSecond?:-1.0)+" cpu_process_ms="+cpu+" pss_kib="+Debug.getPss()+" hardware_execution=unverified")
                    send(reply,LabProtocol.RESULT,Bundle().apply{putString("text",mode+" 第"+run+"轮\n"+answer.take(8192).ifBlank{"（空答案）"})})
                }finally{stage(reply,"close_conversation",run);c.close()}
            }
        }finally{if(initialized){stage(reply,"close_engine");e.close()}}
    }
}
