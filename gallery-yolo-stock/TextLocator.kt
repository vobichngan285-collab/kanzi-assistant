package com.google.ai.edge.gallery.ui.common.chat

import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** YOLO26-Text end-to-end OBB output, [cx,cy,w,h,score,class,angle_rad]. */
class TextLocator(context:Context):AutoCloseable {
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    init {
        val opts=OrtSession.SessionOptions()
        try {
            opts.setIntraOpNumThreads(4)
            session=env.createSession(context.assets.open("yolo26n-text.onnx").use{it.readBytes()},opts)
        } finally {opts.close()}
        require(session.inputNames.size==1)
        val shape=(session.inputInfo.values.first().info as TensorInfo).shape
        require(shape.contentEquals(longArrayOf(1,3,1280,1280))){"Unexpected detector input "+shape.contentToString()}
    }
    data class Box(val points:FloatArray,val score:Float){
        val rect get()=RectF(points.filterIndexed{i,_->i%2==0}.min(),points.filterIndexed{i,_->i%2==1}.min(),points.filterIndexed{i,_->i%2==0}.max(),points.filterIndexed{i,_->i%2==1}.max())
    }
    data class Found(val boxes:List<Box>,val roi:Rect?,val groups:Int,val selectedCount:Int)
    fun locate(bitmap:Bitmap):Found {
        val side=1280
        val ratio=min(side.toFloat()/bitmap.width,side.toFloat()/bitmap.height)
        val w=(bitmap.width*ratio).roundToInt().coerceAtLeast(1)
        val h=(bitmap.height*ratio).roundToInt().coerceAtLeast(1)
        val left=(side-w)/2;val top=(side-h)/2
        val resized=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(side*side)
        try {
            val canvas=Canvas(resized);canvas.drawColor(Color.rgb(114,114,114))
            canvas.drawBitmap(bitmap,null,Rect(left,top,left+w,top+h),Paint(Paint.FILTER_BITMAP_FLAG))
            resized.getPixels(pixels,0,side,0,0,side,side)
        } finally {resized.recycle()}
        val buf=ByteBuffer.allocateDirect(side*side*3*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for(shift in intArrayOf(16,8,0)) for(p in pixels) buf.put(((p shr shift) and 255)/255f)
        buf.rewind()
        val boxes=mutableListOf<Box>()
        OnnxTensor.createTensor(env,buf,longArrayOf(1,3,side.toLong(),side.toLong())).use{input->
            session.run(mapOf(session.inputNames.first() to input)).use{result->
                val output=result[0] as OnnxTensor
                val shape=output.info.shape
                require(shape.size==3&&shape[0]==1L&&shape[2]==7L&&shape[1] in 1L..3000L){"Unexpected OBB output "+shape.contentToString()}
                val arr=output.floatBuffer
                arr.rewind()
                repeat(shape[1].toInt()){
                    val cx=arr.get();val cy=arr.get();val bw=arr.get();val bh=arr.get();val conf=arr.get();val cls=arr.get();val angle=arr.get()
                    if(conf>=.25f&&conf<=1.001f&&abs(cls)<.01f&&bw>1f&&bh>1f&&listOf(cx,cy,bw,bh,angle).all{it.isFinite()}&&cx>=left&&cx<=left+w&&cy>=top&&cy<=top+h){
                        val cosA=cos(angle);val sinA=sin(angle)
                        val p=FloatArray(8)
                        val corners=listOf(-bw/2 to -bh/2,bw/2 to -bh/2,bw/2 to bh/2,-bw/2 to bh/2)
                        for((i,q) in corners.withIndex()){
                            p[i*2]=((cx+q.first*cosA-q.second*sinA-left)*bitmap.width/w).coerceIn(0f,bitmap.width.toFloat())
                            p[i*2+1]=((cy+q.first*sinA+q.second*cosA-top)*bitmap.height/h).coerceIn(0f,bitmap.height.toFloat())
                        }
                        boxes.add(Box(p,conf))
                    }
                }
            }
        }
        return group(boxes,bitmap.width,bitmap.height)
    }
    override fun close(){session.close()}
    companion object {
        fun group(boxes:List<Box>,w:Int,h:Int):Found{
            if(boxes.isEmpty())return Found(boxes,null,0,0)
            val rects=boxes.map{it.rect}
            val parent=IntArray(boxes.size){it}
            fun find(i:Int):Int{var x=i;while(parent[x]!=x){parent[x]=parent[parent[x]];x=parent[x]};return x}
            fun join(i:Int,j:Int){val a=find(i);val b=find(j);if(a!=b)parent[b]=a}
            for(i in rects.indices)for(j in 0 until i){
                val a=rects[i];val b=rects[j]
                val small=min(min(a.width(),a.height()),min(b.width(),b.height())).coerceAtLeast(1f)
                val large=max(min(a.width(),a.height()),min(b.width(),b.height())).coerceAtLeast(1f)
                if(large>small*3.5f)continue
                val gx=max(0f,max(a.left,b.left)-min(a.right,b.right))
                val gy=max(0f,max(a.top,b.top)-min(a.bottom,b.bottom))
                val row=abs(a.centerY()-b.centerY())<=large*.85f&&gx<=large*7f
                val col=gy<=large*2.1f&&gx<=large*2.2f
                if(row||col)join(i,j)
            }
            val groups=rects.indices.groupBy{find(it)}.values
            fun envelope(g:List<Int>):RectF=RectF(rects[g.first()]).also{r->g.drop(1).forEach{r.union(rects[it])}}
            val chosen=groups.maxByOrNull{g->
                val rect=envelope(g)
                val distance=hypot((rect.centerX()-w/2f)/w,(rect.centerY()-h/2f)/h)
                g.sumOf{boxes[it].score.toDouble()}/(1+distance*2)
            }!!
            val rect=envelope(chosen);val margin=max(8f,max(rect.width(),rect.height())*.035f)
            val roi=Rect(floor(rect.left-margin).toInt().coerceIn(0,w),floor(rect.top-margin).toInt().coerceIn(0,h),ceil(rect.right+margin).toInt().coerceIn(0,w),ceil(rect.bottom+margin).toInt().coerceIn(0,h))
            val good=chosen.size>=2&&roi.width()>=32&&roi.height()>=32&&roi.width().toLong()*roi.height()<w.toLong()*h*.85
            return Found(boxes,if(good)roi else null,groups.size,chosen.size)
        }
    }
}
