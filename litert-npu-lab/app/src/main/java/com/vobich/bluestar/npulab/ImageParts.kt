package com.vobich.bluestar.npulab

import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

object ImageParts {
    data class Part(val file:File,val rect:String)
    fun repeatedLines(s:String)=TileGeometry.repeatedLines(s)
    fun prepare(image:File,kind:String,dir:File,w:Int,h:Int):List<Part>{
        if(kind=="original")return listOf(Part(image,"0,0,"+w+","+h))
        val bmp=ImageDecoder.decodeBitmap(ImageDecoder.createSource(image)){decoder,info,_->
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            val size=info.size
            val scale=min(1.0,2400.0/max(size.width,size.height))
            if(scale<1.0)decoder.setTargetSize(max(1,(size.width*scale).roundToInt()),max(1,(size.height*scale).roundToInt()))
        }
        try{
            val width=bmp.width;val height=bmp.height
            val vertical=height>=width
            val count=if(kind=="pad")1 else TileGeometry.count(width,height)
            val length=if(vertical)height else width
            val cross=if(vertical)width else height
            fun gray(color:Int)=((Color.red(color)*77+Color.green(color)*150+Color.blue(color)*29)/256)
            fun energy(pos:Int):Double{
                var total=0.0;var samples=0
                // Average nearby longitudinal/adjacent contrast; favor quiet seams.
                for(delta in -2..2){
                    val p=(pos+delta).coerceIn(0,length-2)
                    val step=max(1,cross/80)
                    for(q in 0 until cross step step){
                        val a=if(vertical)bmp.getPixel(q,p)else bmp.getPixel(p,q)
                        val b=if(vertical)bmp.getPixel(q,p+1)else bmp.getPixel(p+1,q)
                        val q2=min(cross-1,q+step)
                        val c=if(vertical)bmp.getPixel(q2,p)else bmp.getPixel(p,q2)
                        total+=abs(gray(a)-gray(b))+0.5*abs(gray(a)-gray(c));samples++
                    }
                }
                return if(samples==0)0.0 else total/samples
            }
            val edges=TileGeometry.edges(length,count,::energy)
            return (0 until count).map{i->
                val rect=if(vertical)Rect(0,edges[i],width,edges[i+1])else Rect(edges[i],0,edges[i+1],height)
                val side=448
                val out=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888)
                try{
                    val canvas=Canvas(out)
                    val bg=bmp.getPixel(rect.left,rect.top)
                    canvas.drawColor(Color.rgb(Color.red(bg),Color.green(bg),Color.blue(bg)))
                    val scale=min(side.toFloat()/rect.width(),side.toFloat()/rect.height())
                    val dw=rect.width()*scale;val dh=rect.height()*scale
                    val dest=RectF((side-dw)/2,(side-dh)/2,(side+dw)/2,(side+dh)/2)
                    canvas.drawBitmap(bmp,rect,dest,Paint(Paint.FILTER_BITMAP_FLAG))
                    val file=File(dir,kind+"-"+i+".png")
                    FileOutputStream(file).use{require(out.compress(Bitmap.CompressFormat.PNG,100,it))}
                    Part(file,""+rect.left+","+rect.top+","+rect.right+","+rect.bottom+"@"+width+"x"+height)
                }finally{out.recycle()}
            }
        }finally{bmp.recycle()}
    }
}
