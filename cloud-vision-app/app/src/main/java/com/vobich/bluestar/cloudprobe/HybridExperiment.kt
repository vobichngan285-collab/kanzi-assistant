package com.vobich.bluestar.cloudprobe

import android.content.Context
import android.graphics.*
import android.net.Network
import android.os.Build
import android.os.SystemClock
import com.vobich.bluestar.openocrlab.*
import org.json.JSONArray
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** Experiment only. Original v0.4.8 geometry/model sources are byte-for-byte vendored. */
object HybridExperiment {
    private const val PREF="hybrid_experiment"
    private var db:DbLocator?=null
    private var reader:Reader?=null
    private val lock=Any()
    @Volatile private var latest:Result?=null
    data class Cell(val text:String,val rect:RectF,val confidence:Float)
    data class Scan(val cells:List<Cell>,val width:Int,val height:Int,val elapsed:Long,val found:Int,val dropped:Int,val blank:Int,val decodeMs:Long)
    data class Result(val text:String,val raw:String,val label:String,val cloudOk:Boolean,
        val ocrMs:Long,val boxes:Int,val requestBytes:Int,val networkMs:Long,val firstTextMs:Long,
        val promptTokens:Int,val completionTokens:Int,val jpegBytes:Int,val details:String)
    @JvmStatic fun enabled(c:Context)=c.getSharedPreferences(PREF,0).getBoolean("enabled",false)
    @JvmStatic fun record(r:Result){latest=r}
    fun latestResult()=latest
    private fun now()=SystemClock.elapsedRealtime()
    fun model(c:Context)=c.getSharedPreferences(PREF,0).getString("model",VisionModelCatalog.FLASH_37)?:VisionModelCatalog.FLASH_37
    fun profile(c:Context)=c.getSharedPreferences(PREF,0).getString("profile","")?:""
    fun save(c:Context,enabled:Boolean,profile:String,model:String){
        c.getSharedPreferences(PREF,0).edit().putBoolean("enabled",enabled).putString("profile",profile).putString("model",model).apply()
    }
    private fun scan(c:Context,jpeg:ByteArray,cancel:CloudVisionClient.Cancellation):Scan=synchronized(lock){
        cancel.check()
        val at=now()
        val app=c.applicationContext
        require(Build.VERSION.SDK_INT>=28){"实时实验要求 Android 9 或以上"}
        val bm=ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(jpeg))){d,i,_->
            d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val decoded=now()
        try{
            if(db==null)db=DbLocator(app,4)
            val locator=db!!
            locator.clearProbability()
            val found=locator.locate(bm,1600,true)
            cancel.check()
            val boxes=OfficialGeometry.order(found)
            val cells=ArrayList<Cell>()
            var dropped=0;var blank=0
            if(boxes.isNotEmpty()){
                if(reader==null)reader=Reader(app,"rep.onnx",4)
                val engine=reader!!
                val rgba=Mat();val bgr=Mat()
                try{
                    Utils.bitmapToMat(bm,rgba)
                    Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);rgba.release()
                    for(chunk in boxes.chunked(engine.officialBatchSize)){
                        cancel.check()
                        val answers=engine.readOfficial(bgr,chunk)
                        for((i,answer) in answers.withIndex()){
                            if(answer.text.isBlank())blank++
                            if(answer.confidence<.5f)dropped++
                            if(answer.text.isNotBlank()&&answer.confidence>=.5f)
                                cells.add(Cell(answer.text,chunk[i].rect,answer.confidence))
                        }
                    }
                }finally{rgba.release();bgr.release()}
            }
            Scan(cells,bm.width,bm.height,now()-at,found.size,dropped,blank,decoded-at)
        }finally{db?.clearProbability();bm.recycle()}
    }
    @JvmStatic fun run(c:Context,jpeg:ByteArray,network:Network?,cancel:CloudVisionClient.Cancellation):Result=runTest(c,jpeg,network,cancel,null,false)
    internal fun runTest(c:Context,jpeg:ByteArray,network:Network?,cancel:CloudVisionClient.Cancellation,listener:CloudVisionClient.PartListener?,offline:Boolean):Result{
        val s=scan(c,jpeg,cancel)
        val texts=s.cells.map{it.text}
        val raw=texts.joinToString("\n")
        val base="jpeg_bytes="+jpeg.size+" source="+s.width+"x"+s.height+" decode_ms="+s.decodeMs+" ocr_ms="+s.elapsed+" boxes="+s.found+" kept="+texts.size+" dropped="+s.dropped+" blank="+s.blank
        if(texts.isEmpty())return Result("","","未检测到文字",false,s.elapsed,s.found,0,0,-1,-1,-1,jpeg.size,base+" no_text")
        if(offline)return Result(raw,raw,"纯离线OCR",false,s.elapsed,s.found,0,0,-1,-1,-1,jpeg.size,base+" offline=1")
        val data=JSONArray()
        s.cells.forEach{v->
            val r=v.rect
            data.put(JSONArray().put(r.left.roundToInt()).put(r.top.roundToInt())
                .put(r.right.roundToInt()).put(r.bottom.roundToInt()).put(v.text))
        }
        val prompt="请根据坐标把下面的文字整理成通顺、适合朗读的内容。保留原文信息和数字，不确定的不要猜。直接输出整理后的文字。每行数据为[左,上,右,下,文字]。\n"+data.toString()
        var cloud:CloudVisionClient.Result?=null
        val cloudAt=now()
        var requestedModel=model(c)
        try{
            cancel.check()
            val cfg=CloudProviderStore.resolveFor(c,CloudProviderStore.VISION,profile(c),model(c))
            requestedModel=cfg.model
            cloud=CloudVisionClient.organizeOcr(prompt,cfg,network,cancel,listener)
            cancel.check()
            val text=cloud.text.trim()
            require(text.isNotEmpty()){"empty cloud text"}
            return Result(text,raw,"端云整理完成",true,s.elapsed,s.found,cloud.requestBytes,cloud.networkMs,cloud.firstTextMs,cloud.promptTokens,cloud.completionTokens,jpeg.size,
                base+" model="+cloud.model+" request_bytes="+cloud.requestBytes+" upload_ms="+cloud.uploadMs+" headers_ms="+cloud.responseHeadersMs+" network_ms="+cloud.networkMs+" first_text_ms="+cloud.firstTextMs+" first_speakable_ms="+cloud.firstSpeakableMs+" prompt_tokens="+cloud.promptTokens+" completion_tokens="+cloud.completionTokens+" cloud_ok=1 prompt_mode=plain")
        }catch(t:Exception){
            cancel.check()
            return Result(raw,raw,"云端未返回有效文字，已保留本地原文",false,s.elapsed,s.found,cloud?.requestBytes?:-1,cloud?.networkMs?:now()-cloudAt,cloud?.firstTextMs?:-1,cloud?.promptTokens?:-1,cloud?.completionTokens?:-1,jpeg.size,
                base+" model="+requestedModel+" request_bytes="+(cloud?.requestBytes?:-1)+" network_ms="+(cloud?.networkMs?:now()-cloudAt)+" first_text_ms="+(cloud?.firstTextMs?:-1)+" prompt_tokens="+(cloud?.promptTokens?:-1)+" completion_tokens="+(cloud?.completionTokens?:-1)+" cloud_ok=0 prompt_mode=plain reason="+t.javaClass.simpleName)
        }
    }
}
