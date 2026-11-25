package com.vobich.bluestar.npulab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.SystemClock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/** Local bundled OCR used only for geometry; MiniCPM generates the spoken answer. */
class TextRegionLocator(context:Context){
    private val detector by lazy {
        MlKit.initialize(context.applicationContext)
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private fun now()=SystemClock.elapsedRealtime()
    fun prepare(image:File,dir:File,kind:String,log:(String)->Unit):List<ImageParts.Part>{
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(image.absolutePath,bounds)
        fun fallback(reason:String):List<ImageParts.Part>{
            log("phase=roi_fallback reason="+reason)
            return listOf(ImageParts.Part(image,"0,0,"+bounds.outWidth+","+bounds.outHeight))
        }
        try{
            val start=now()
            // Decode the high resolution oriented source once; limit memory on very large photos.
            val full=ImageDecoder.decodeBitmap(ImageDecoder.createSource(image)){d,info,_->
                d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                val size=info.size
                val scale=min(1.0,min(4096.0/max(size.width,size.height),sqrt(16000000.0/(size.width.toDouble()*size.height))))
                if(scale<1)d.setTargetSize(max(1,(size.width*scale).roundToInt()),max(1,(size.height*scale).roundToInt()))
            }
            try{
                val scale=min(1.0,2000.0/max(full.width,full.height))
                val small=if(scale<1)Bitmap.createScaledBitmap(full,max(1,(full.width*scale).roundToInt()),max(1,(full.height*scale).roundToInt()),true) else full
                val dw=small.width;val dh=small.height
                val decoded=now()
                val lines=try{
                    val result=Tasks.await(detector.process(InputImage.fromBitmap(small,0)))
                    result.textBlocks.flatMap{block->block.lines.mapNotNull{line->line.boundingBox?.let{TextBox(it.left,it.top,it.right,it.bottom)}}}
                }finally{if(small!==full)small.recycle()}
                val detected=now()
                val choice=RegionGeometry.choose(lines,dw,dh,if(kind=="roi")0.11 else 0.018)
                log("phase=roi_detect kind="+kind+" source="+full.width+"x"+full.height+" detector="+dw+"x"+dh+" decode_ms="+(decoded-start)+" detect_ms="+(detected-decoded)+" lines="+choice.total+" groups="+choice.groups+" included="+choice.included+" decision="+choice.reason)
                val box=choice.box?:return fallback(choice.reason)
                val regions=if(kind=="bands")RegionGeometry.bands(choice,dw,dh) else listOf(box)
                val mapped=regions.map{RegionGeometry.scale(it,dw,dh,full.width,full.height)}
                if(mapped.isEmpty()||mapped.any{it.w<16||it.h<16})return fallback("small_crop")
                val parts=mapped.mapIndexed{i,b->
                    val crop=Bitmap.createBitmap(full,b.l,b.t,b.w,b.h)
                    try{
                        val out=File(dir,kind+"-"+i+".png")
                        val started=now()
                        FileOutputStream(out).use{require(crop.compress(Bitmap.CompressFormat.PNG,100,it))}
                        log("phase=roi_crop kind="+kind+" part="+(i+1)+"/"+mapped.size+" rect="+b.l+","+b.t+","+b.r+","+b.b+"@"+full.width+"x"+full.height+" crop_width="+b.w+" crop_height="+b.h+" area_permille="+(b.area*1000/(full.width.toLong()*full.height))+" encode_ms="+(now()-started)+" excluded_lines="+(choice.total-choice.included))
                        ImageParts.Part(out,""+b.l+","+b.t+","+b.r+","+b.b+"@"+full.width+"x"+full.height)
                    }finally{if(crop!==full)crop.recycle()}
                }
                log("phase=roi_prepared kind="+kind+" parts="+parts.size+" crop_ms="+(now()-detected))
                return parts
            }finally{full.recycle()}
        }catch(e:Exception){return fallback(e.javaClass.simpleName)}
    }
}
