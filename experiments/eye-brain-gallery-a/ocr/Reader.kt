package com.vobich.bluestar.openocrlab
import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
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
 override fun close(){session.close()}
}
