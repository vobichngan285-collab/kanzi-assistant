package com.vobich.bluestar.openocrlab

import android.content.Context
import ai.onnxruntime.*
import org.json.JSONArray
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class V2Reader(context:Context,threads:Int):AutoCloseable{
 private val env=OrtEnvironment.getEnvironment()
 private val chars=context.assets.open("v2-characters.json").bufferedReader(Charsets.UTF_8).use{
  val a=JSONArray(it.readText());List(a.length()){i->a.getString(i)}
 }.also{require(it.size==19685&&it[0]=="blank")}
 private val session:OrtSession
 init{
  val opt=OrtSession.SessionOptions()
  try{opt.setIntraOpNumThreads(threads);session=env.createSession(context.assets.open("openocrv2_ctc.onnx").use{it.readBytes()},opt)}finally{opt.close()}
 }
 val inputShape:String get()=(session.inputInfo.values.first().info as TensorInfo).shape.contentToString()
 var lastShapes:List<String> = emptyList();private set
 private data class Prepared(val index:Int,val shape:V2Preprocess.Shape,val pixels:FloatArray)
 private fun prepare(source:Mat,box:TextLocator.Box,index:Int):Prepared?{
  val p=box.points;val cw=OfficialGeometry.cropWidth(p);val ch=OfficialGeometry.cropHeight(p)
  if(cw<1||ch<1)return null
  val src=MatOfPoint2f(*Array(4){i->Point(p[2*i].toDouble(),p[2*i+1].toDouble())})
  val dst=MatOfPoint2f(Point(0.0,0.0),Point(cw.toDouble(),0.0),Point(cw.toDouble(),ch.toDouble()),Point(0.0,ch.toDouble()))
  val crop=Mat();val rotated=Mat();val transform=Imgproc.getPerspectiveTransform(src,dst)
  try{
   Imgproc.warpPerspective(source,crop,transform,Size(cw.toDouble(),ch.toDouble()),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE)
   val view=if(ch.toDouble()/cw>=1.5){Core.rotate(crop,rotated,Core.ROTATE_90_COUNTERCLOCKWISE);rotated}else crop
   val w=view.cols();val h=view.rows();val data=ByteArray(w*h*3);check(view.get(0,0,data)==data.size)
   val shape=V2Preprocess.shape(w,h)
   return Prepared(index,shape,V2Preprocess.tensor(data,w,h,shape))
  }finally{src.release();dst.release();transform.release();crop.release();rotated.release()}
 }
 fun read(source:Mat,boxes:List<TextLocator.Box>,timing:Reader.Timing,onBatch:()->Unit,capture:((java.nio.FloatBuffer,LongArray,List<Int>)->Unit)?=null):List<Reader.Answer>{
  val start=System.nanoTime()
  val result=MutableList(boxes.size){Reader.Answer("",0f,0,0f)}
  val prepared=boxes.mapIndexedNotNull{i,b->prepare(source,b,i)}
  val shapes=MutableList(boxes.size){"invalid"}
  for(p in prepared)shapes[p.index]="${p.index+1}:${p.shape.w}x${p.shape.h}"
  lastShapes=shapes
  timing.prepNs+=System.nanoTime()-start
  // Shape buckets match upstream; use the same maximum batch of six as RepSVTR.
  for((shape,group) in prepared.groupBy{it.shape}){
   for(chunk in group.chunked(6)){
    val tensorStart=System.nanoTime()
    val n=chunk.size;val h=shape.h;val w=shape.w
    val buf=ByteBuffer.allocateDirect(n*3*h*w*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    for(p in chunk)buf.put(p.pixels)
    buf.rewind()
    if(capture!=null){capture(buf,longArrayOf(n.toLong(),3,h.toLong(),w.toLong()),chunk.map{it.index});continue}
    OnnxTensor.createTensor(env,buf,longArrayOf(n.toLong(),3,h.toLong(),w.toLong())).use{input->
     timing.tensorNs+=System.nanoTime()-tensorStart
     val inferStart=System.nanoTime()
     session.run(mapOf(session.inputNames.single() to input)).use{res->
      timing.inferNs+=System.nanoTime()-inferStart
      val decodeStart=System.nanoTime()
      val t=res[0] as OnnxTensor;val s=t.info.shape
      require(s.size==3&&s[0]==n.toLong()&&s[2]==chars.size.toLong()){"Unexpected OpenOCRv2 CTC shape "+s.contentToString()}
      val f=t.floatBuffer;f.rewind()
      for(p in chunk){
       var last=-1;val text=StringBuilder();var conf=0f;var count=0;var low=1f
       repeat(s[1].toInt()){
        var best=0;var v=-Float.MAX_VALUE
        repeat(chars.size){i->val a=f.get();if(a>v){best=i;v=a}}
        if(best!=0&&best!=last){text.append(chars[best]);conf+=v;low=min(low,v);count++}
        last=best
       }
       result[p.index]=Reader.Answer(text.toString(),if(count>0)conf/count else 0f,w,if(count>0)low else 0f)
      }
      timing.decodeNs+=System.nanoTime()-decodeStart
     }
    }
    onBatch()
   }
  }
  return result
 }
 override fun close(){session.close()}
}
