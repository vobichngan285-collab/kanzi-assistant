package com.vobich.bluestar.npulab

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** UI process only. Never send recognized text to the diagnostic logger. */
class StreamSpeech(context:Context, private val log:(String)->Unit) {
    private val main=Handler(Looper.getMainLooper())
    private var tts:TextToSpeech?=null
    private var ready=false
    private var closed=false
    private var active=false
    private var generation=0L
    private var jobStart=0L
    private var tapStart=0L
    private var seq=0
    private var firstStarted=false
    private val pending=StringBuilder()
    init {
        tts=TextToSpeech(context.applicationContext){status->
            main.post{
                if(!closed){
                    val engine=tts
                    val voice=runCatching{engine?.voices?.filter{it.locale.language=="zh"&&!it.isNetworkConnectionRequired}?.sortedWith(compareByDescending<android.speech.tts.Voice>{it.locale.country=="CN"}.thenByDescending{it.quality})?.firstOrNull()}.getOrNull()
                    ready=status==TextToSpeech.SUCCESS && voice!=null && runCatching{engine?.setVoice(voice)==TextToSpeech.SUCCESS}.getOrDefault(false)
                    log("phase=tts_init offline_zh_ready="+ready)
                    if(ready&&active)drain(false)
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
            override fun onStart(id:String?){main.post{
                if(active&&!firstStarted&&id?.startsWith("ocr-"+generation+"-")==true){
                    firstStarted=true;val t=SystemClock.elapsedRealtime()
                    log("phase=tts_first_on_start job_ms="+(t-jobStart)+" tap_ms="+(t-tapStart)+" acoustic_unverified=true")
                }
            }}
            override fun onDone(id:String?){}
            @Deprecated("Legacy callback") override fun onError(id:String?){main.post{if(active&&id?.startsWith("ocr-"+generation+"-")==true)log("phase=tts_error")}}
        })
    }
    fun begin(){cancel();active=true;tapStart=SystemClock.elapsedRealtime()}
    fun accept(b:Bundle){
        if(!active)return
        if(jobStart==0L)jobStart=b.getLong("start")
        pending.append(b.getString("text",""))
        val final=b.getBoolean("final")
        drain(final)
        if(final&&!ready){pending.clear();log("phase=tts_unavailable offline_zh_ready=false")}
    }
    private fun drain(final:Boolean){
        if(!ready||!active||jobStart==0L)return
        while(pending.isNotEmpty()){
            val n=SpeechSegments.nextLength(pending,final)
            if(n==0)return
            val text=pending.substring(0,n).trim();pending.delete(0,n)
            if(text.isBlank())continue
            val id="ocr-"+generation+"-"+(++seq)
            val result=tts?.speak(text,TextToSpeech.QUEUE_ADD,null,id)
            if(seq==1)log("phase=tts_first_request job_ms="+(SystemClock.elapsedRealtime()-jobStart)+" tap_ms="+(SystemClock.elapsedRealtime()-tapStart)+" accepted="+(result==TextToSpeech.SUCCESS))
            if(result!=TextToSpeech.SUCCESS){log("phase=tts_enqueue_failed");cancel();return}
        }
    }
    fun cancel(){generation++;active=false;pending.clear();jobStart=0;seq=0;firstStarted=false;tts?.stop()}
    fun close(){cancel();closed=true;tts?.shutdown();tts=null}
}

object SpeechSegments {
    fun nextLength(s:CharSequence,final:Boolean):Int {
        val limit=minOf(s.length,24)
        for(i in 0 until limit)if(s[i] in "\n。！？；，、.!?;" && s.subSequence(0,i+1).any{!it.isWhitespace()})return i+1
        if(s.length>=16){
            val n=minOf(s.length,16)
            return if(n<s.length&&Character.isHighSurrogate(s[n-1])&&Character.isLowSurrogate(s[n]))n-1 else n
        }
        return if(final)s.length else 0
    }
}
