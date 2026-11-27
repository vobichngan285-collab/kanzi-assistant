package com.vobich.bluestar.openocrlab
import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class Reader(context:Context, asset:String,threads:Int=4):AutoCloseable {
 private val env=OrtEnvironment.getEnvironment()
 private val chars=listOf("")+context.assets.open("chars.txt").bufferedReader(Charsets.UTF_8).use{it.readLines()}+listOf(" ")
 private val session:OrtSession
 init { val opt=OrtSession.SessionOptions();try{opt.setIntraOpNumThreads(threads);session=env.createSession(context.assets.open(asset).use{it.readBytes()},opt)}finally{opt.close()} }
 data class Answer(val text:String,val confidence:Float,val width:Int,val minConfidence:Float=0f)
 fun read(source:Bitmap,box:TextLocator.Box,forceHorizontal:Boolean=false):Answer {
  val p=box.points.copyOf()
  fun len(a:Int,b:Int)=hypot(p[a*2]-p[b*2],p[a*2+1]-p[b*2+1])
  // Rotate the longer edge to the horizontal axis, choosing reading direction left to right.
  val long01=forceHorizontal||len(0,1)>=len(1,2)
  val start=if(long01){if(p[2]>=p[0])0 else 2}else{if(p[4]>=p[2])1 else 3}
  val q=FloatArray(8){i->p[((start+i/2)%4)*2+i%2]}
  val w=(hypot(q[2]-q[0],q[3]-q[1])+hypot(q[4]-q[6],q[5]-q[7]))/2
  val h=(hypot(q[6]-q[0],q[7]-q[1])+hypot(q[4]-q[2],q[5]-q[3]))/2
  require(w>=1&&h>=1)
  val actual=ceil(48*w/h).toInt().coerceIn(1,960)
  val width=max(320,actual)
  val bm=Bitmap.createBitmap(actual,48,Bitmap.Config.ARGB_8888)
  val pixels=IntArray(actual*48)
  try {
   val m=Matrix()
   check(m.setPolyToPoly(q,0,floatArrayOf(0f,0f,actual.toFloat(),0f,actual.toFloat(),48f,0f,48f),0,4))
   Canvas(bm).apply{drawColor(Color.WHITE);drawBitmap(source,m,Paint(Paint.FILTER_BITMAP_FLAG))}
   bm.getPixels(pixels,0,actual,0,0,actual,48)
  } finally {bm.recycle()}
  val buf=ByteBuffer.allocateDirect(3*48*width*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  // Official BGR input, normalized to [-1,1], zero right padding.
  for(shift in intArrayOf(0,8,16))for(y in 0 until 48)for(x in 0 until width)buf.put(if(x<actual)(((pixels[y*actual+x] shr shift) and 255)/127.5f-1f)else 0f)
  buf.rewind()
  OnnxTensor.createTensor(env,buf,longArrayOf(1,3,48,width.toLong())).use{input->
   session.run(mapOf(session.inputNames.single() to input)).use{result->
    val t=result[0] as OnnxTensor;val s=t.info.shape
    require(s.size==3&&s[0]==1L&&s[2]==chars.size.toLong()){"Unexpected CTC shape "+s.contentToString()}
    val f=t.floatBuffer;f.rewind();var last=-1;val out=StringBuilder();var conf=0f;var n=0;var minConf=1f
    repeat(s[1].toInt()){
     var best=0;var v=-Float.MAX_VALUE
     repeat(chars.size){i->val a=f.get();if(a>v){best=i;v=a}}
     if(best!=0&&best!=last){out.append(chars[best]);conf+=v;minConf=min(minConf,v);n++}
     last=best
    }
    return Answer(out.toString(),if(n>0)conf/n else 0f,width,if(n>0)minConf else 0f)
   }
  }
 }
 /** Official E2E crop: original-size cubic perspective transform, replicate border,
  * tall rotation >=1.5, then official dynamic BGR normalization and right padding. */
 private data class Prepared(val width:Int,val pixels:FloatArray)
 private fun prepareOfficial(source:Mat,box:TextLocator.Box):Prepared?{
  val p=box.points;val cw=OfficialGeometry.cropWidth(p);val ch=OfficialGeometry.cropHeight(p)
  if(cw<1||ch<1)return null
  val src=MatOfPoint2f(*Array(4){i->Point(p[2*i].toDouble(),p[2*i+1].toDouble())})
  val dst=MatOfPoint2f(Point(0.0,0.0),Point(cw.toDouble(),0.0),Point(cw.toDouble(),ch.toDouble()),Point(0.0,ch.toDouble()))
  val crop=Mat();val rotated=Mat();val resized=Mat()
  val transform=Imgproc.getPerspectiveTransform(src,dst)
  try{
   Imgproc.warpPerspective(source,crop,transform,Size(cw.toDouble(),ch.toDouble()),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE)
   val view=if(ch.toDouble()/cw>=1.5){Core.rotate(crop,rotated,Core.ROTATE_90_COUNTERCLOCKWISE);rotated}else crop
   val width=OfficialGeometry.recWidth(view.cols(),view.rows())
   val actual=min(width,ceil(48.0*view.cols()/view.rows()).toInt()).coerceAtLeast(1)
   Imgproc.resize(view,resized,Size(actual.toDouble(),48.0),0.0,0.0,Imgproc.INTER_LINEAR)
   val bytes=ByteArray(actual*48*3);resized.get(0,0,bytes)
   val data=FloatArray(3*48*width)
   for(c in 0..2)for(y in 0 until 48)for(x in 0 until actual)data[(c*48+y)*width+x]=((bytes[(y*actual+x)*3+c].toInt() and 255)/255f-.5f)/.5f
   return Prepared(width,data)
  }finally{src.release();dst.release();transform.release();crop.release();rotated.release();resized.release()}
 }
 val officialBatchSize:Int get(){val s=(session.inputInfo.values.first().info as TensorInfo).shape;return if(s[0]<=0)6 else 1}
 val inputShape:String get()=(session.inputInfo.values.first().info as TensorInfo).shape.contentToString()
 data class Timing(var prepNs:Long=0,var tensorNs:Long=0,var inferNs:Long=0,var decodeNs:Long=0)
 fun readOfficial(source:Mat,boxes:List<TextLocator.Box>,timing:Timing?=null):List<Answer>{
  val prepStart=System.nanoTime()
  val result=MutableList(boxes.size){Answer("",0f,0,0f)}
  val prepared=boxes.mapIndexedNotNull{i,b->prepareOfficial(source,b)?.let{i to it}}
  timing?.let{it.prepNs+=System.nanoTime()-prepStart}
  for(chunk in prepared.chunked(officialBatchSize)){
   val tensorStart=System.nanoTime()
   val width=chunk.maxOf{it.second.width}
   val n=chunk.size
   val buf=ByteBuffer.allocateDirect(n*3*48*width*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
   for((_,p) in chunk)for(c in 0..2)for(y in 0 until 48){
    buf.put(p.pixels,(c*48+y)*p.width,p.width)
    repeat(width-p.width){buf.put(0f)}
   }
   buf.rewind()
   OnnxTensor.createTensor(env,buf,longArrayOf(n.toLong(),3,48,width.toLong())).use{input->
    timing?.let{it.tensorNs+=System.nanoTime()-tensorStart}
    val inferStart=System.nanoTime()
    session.run(mapOf(session.inputNames.single() to input)).use{res->
     timing?.let{it.inferNs+=System.nanoTime()-inferStart}
     val decodeStart=System.nanoTime()
     val t=res[0] as OnnxTensor;val s=t.info.shape
     require(s.size==3&&s[0]==n.toLong()&&s[2]==chars.size.toLong()){"Unexpected official CTC shape "+s.contentToString()}
     val f=t.floatBuffer;f.rewind()
     for((index,_) in chunk){
      var last=-1;val out=StringBuilder();var conf=0f;var count=0;var low=1f
      repeat(s[1].toInt()){
       var best=0;var v=-Float.MAX_VALUE
       repeat(chars.size){i->val a=f.get();if(a>v){best=i;v=a}}
       if(best!=0&&best!=last){out.append(chars[best]);conf+=v;low=min(low,v);count++};last=best
      }
      result[index]=Answer(out.toString(),if(count>0)conf/count else 0f,width,if(count>0)low else 0f)
     }
     timing?.let{it.decodeNs+=System.nanoTime()-decodeStart}
    }
   }
  }
  return result
 }
 override fun close(){session.close()}
}
