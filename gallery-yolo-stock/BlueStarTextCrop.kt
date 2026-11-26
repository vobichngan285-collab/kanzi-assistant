package com.google.ai.edge.gallery.ui.common.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Explicit opt-in picker. No cloud requests, no OCR, no change to Gallery engine. */
internal object BlueStarTextCrop {
    private var locator: TextLocator? = null
    @Synchronized
    fun crop(context: Context, uri: Uri): Bitmap? {
        val start=SystemClock.elapsedRealtime()
        var full:Bitmap?=null
        return try {
            val image=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)){ decoder, info, _ ->
                require(info.size.width.toLong()*info.size.height<=16_000_000L){"Image exceeds 16 MP"}
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            }
            full=image
            val decoded=SystemClock.elapsedRealtime()
            if(locator==null)locator=TextLocator(context.applicationContext)
            val init=SystemClock.elapsedRealtime()
            val found=locator!!.locate(image)
            val detect=SystemClock.elapsedRealtime()
            val roi=found.roi ?: run {
                notify(context,"YOLO未定位可靠主区域，使用原厂图片流程")
                return null
            }
            val result=Bitmap.createBitmap(image,roi.left,roi.top,roi.width(),roi.height())
            if(result===image)full=null
            val elapsed=SystemClock.elapsedRealtime()-start
            val summary="YOLO ${found.boxes.size}框 · ${result.width}×${result.height} · ${elapsed}ms"
            Log.i("BlueStarYolo","$summary decode_ms=${decoded-start} init_ms=${init-decoded} detect_ms=${detect-init} roi=$roi")
            notify(context,summary)
            result
        } catch(e:Exception) {
            Log.e("BlueStarYolo","Crop failed; using original picker",e)
            notify(context,"YOLO裁剪失败，使用原厂图片流程")
            null
        } catch(e:OutOfMemoryError) {
            Log.e("BlueStarYolo","Crop OOM; using original picker",e)
            notify(context,"YOLO内存不足，使用原厂图片流程")
            null
        } finally {
            full?.recycle()
        }
    }
    private fun notify(context:Context,text:String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext,text,Toast.LENGTH_LONG).show()
        }
    }
}
