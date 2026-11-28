package com.vobich.bluestar.openocrlab
import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class Reader(context:Context, asset:String,threads:Int=4):AutoCloseable {
 private val env=OrtEnvironment.getEnvironment()
 private val chars=listOf("")+context.assets.open("chars.txt").bufferedReader(Charsets.UTF_8).use{it.readLines()}+listOf(" ")
 private val session:OrtSession
 init { val opt=OrtSession.SessionOptions();try{opt.setIntraOpNumThreads(threads);session=env.createSession(context.assets.open(asset).use{it.readBytes()},opt)}finally{opt.close()} }
 data class Answer(val text:String,val confidence:Float,val width:Int,val minConfidence:Float=0f)
 private fun prepareFocus(source:Bitmap,box:TextLocator.Box,forceHorizontal:Boolean):Pair<FloatBuffer,Int> {
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
  return buf to width
 }
 fun read(source:Bitmap,box:TextLocator.Box,forceHorizontal:Boolean=false):Answer {
  val (buf,width)=prepareFocus(source,box,forceHorizontal)
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
 fun readOfficial(source:Mat,boxes:List<TextLocator.Box>):List<Answer>{
  val result=MutableList(boxes.size){Answer("",0f,0,0f)}
  val prepared=boxes.mapIndexedNotNull{i,b->prepareOfficial(source,b)?.let{i to it}}
  for(chunk in prepared.chunked(officialBatchSize)){
   val width=chunk.maxOf{it.second.width}
   val n=chunk.size
   val buf=ByteBuffer.allocateDirect(n*3*48*width*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
   for((_,p) in chunk)for(c in 0..2)for(y in 0 until 48){
    buf.put(p.pixels,(c*48+y)*p.width,p.width)
    repeat(width-p.width){buf.put(0f)}
   }
   buf.rewind()
   OnnxTensor.createTensor(env,buf,longArrayOf(n.toLong(),3,48,width.toLong())).use{input->
    session.run(mapOf(session.inputNames.single() to input)).use{res->
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
    }
   }
  }
  return result
 }

 // Immutable ownership: each lane takes one batch, prepares under the single
 // crop lock, and executes independently. No duplicated inference or reordering.
 private data class Job(val ordinal:Int,val ids:List<Int>,val input:FloatBuffer,val shape:LongArray,val prepMs:Double)
 private fun ms(ns:Long)=ns/1e6
 private fun decodeCompact(values:FloatArray,shape:IntArray,width:Int):List<Answer> =
  CompactCtc.decode(values,shape,chars).map{Answer(it.text,it.confidence,width,it.minConfidence)}
 private fun runCpu(j:Job):List<Answer>{
  val buf=j.input.duplicate().apply{rewind()}
  OnnxTensor.createTensor(env,buf,j.shape).use{input->
   session.run(mapOf(session.inputNames.single() to input)).use{result->
    val t=result[0] as OnnxTensor;val shape=t.info.shape
    require(shape.size==3&&shape[0]==j.shape[0]&&shape[2]==chars.size.toLong())
    val f=t.floatBuffer.apply{rewind()}
    return List(shape[0].toInt()){
     var last=-1;var sum=0f;var count=0;var low=1f;val text=StringBuilder()
     repeat(shape[1].toInt()){
      var best=0;var score=-Float.MAX_VALUE
      repeat(chars.size){id->val v=f.get();if(v>score){best=id;score=v}}
      if(best!=0&&best!=last){text.append(chars[best]);sum+=score;low=min(low,score);count++};last=best
     }
     Answer(text.toString(),if(count>0)sum/count else 0f,j.shape[3].toInt(),if(count>0)low else 0f)
    }
   }
  }
 }
 internal fun cooperate(source:Bitmap,bgr:Mat,boxes:List<TextLocator.Box>,official:Boolean,gpu:GpuClient,mixed:Boolean,note:(String)->Unit):List<Answer>{
  if(boxes.isEmpty())return emptyList()
  val began=System.nanoTime()
  val lock=Any();val results=arrayOfNulls<Answer>(boxes.size)
  val groups=boxes.indices.toList().chunked(if(official)officialBatchSize else 1)
  // Compute one GPU width per page without changing the original crop or CPU batch.
  val widths=boxes.mapNotNull{box->
   val p=box.points
   if(official){
    val cw=OfficialGeometry.cropWidth(p);val ch=OfficialGeometry.cropHeight(p)
    if(cw<1||ch<1)null else if(ch.toDouble()/cw>=1.5)OfficialGeometry.recWidth(ch,cw)else OfficialGeometry.recWidth(cw,ch)
   }else{
    val start=if(p[2]>=p[0])0 else 2
    val q=FloatArray(8){i->p[((start+i/2)%4)*2+i%2]}
    val w=(hypot(q[2]-q[0],q[3]-q[1])+hypot(q[4]-q[6],q[5]-q[7]))/2
    val h=(hypot(q[6]-q[0],q[7]-q[1])+hypot(q[4]-q[2],q[5]-q[3]))/2
    require(w>=1&&h>=1)
    max(320,ceil(48*w/h).toInt().coerceIn(1,960))
   }
  }
  val gpuWidth=FixedGpuInput.widthFor(widths.maxOrNull()?:320)
  val gpuBatch=if(official)officialBatchSize else 1
  note("GPU_PAGE_PLAN real_max_width="+(widths.maxOrNull()?:0)+" fixed_shape="+longArrayOf(gpuBatch.toLong(),3,48,gpuWidth.toLong()).contentToString()+" width_rounding=64 tail_duplicate=1 smart_batch=1 padding_changes_model_input=1")
  val useCpu=mixed&&groups.size>1
  val dispatch=BatchDispatch(groups.size,if(useCpu)2 else 1)
  val aborted=AtomicBoolean(false)
  val finished=mutableListOf<String>()
  val starts=mutableListOf<Triple<String,Long,Long>>()
  // Reserve the first batch for GPU and the second for CPU when both exist.
  // This tests both lanes even when one starts much faster than the other.
  fun take(reserved:Int?=null):Job?=synchronized(lock){
   if(aborted.get())return@synchronized null
   val id=if(reserved!=null)dispatch.first(reserved) else dispatch.next()?:return@synchronized null
   val at=System.nanoTime()
   val group=groups[id]
   if(official){
    val p=group.mapNotNull{index->prepareOfficial(bgr,boxes[index])?.let{index to it}?:run{results[index]=Answer("",0f,0,0f);null}}
    if(p.isEmpty())return@synchronized Job(id,emptyList(),ByteBuffer.allocateDirect(0).asFloatBuffer(),longArrayOf(0,3,48,0),ms(System.nanoTime()-at))
    val width=p.maxOf{it.second.width};val n=p.size
    val buf=ByteBuffer.allocateDirect(Math.multiplyExact(n*3*48*4,width)).order(ByteOrder.nativeOrder()).asFloatBuffer()
    for((_,v) in p)for(c in 0..2)for(y in 0 until 48){buf.put(v.pixels,(c*48+y)*v.width,v.width);repeat(width-v.width){buf.put(0f)}}
    buf.rewind()
    Job(id,p.map{it.first},buf,longArrayOf(n.toLong(),3,48,width.toLong()),ms(System.nanoTime()-at))
   }else{
    val (buf,w)=prepareFocus(source,boxes[group.single()],true)
    Job(id,group,buf,longArrayOf(1,3,48,w.toLong()),ms(System.nanoTime()-at))
   }
  }
  val pool=Executors.newFixedThreadPool(if(mixed)2 else 1)
  fun lane(name:String,reserved:Int){
   try{
    var job=take(reserved)
    while(job!=null){
     val j=job
     if(aborted.get())break
     if(j.ids.isNotEmpty()){
      val at=System.nanoTime()
      var details=""
      val answers=if(name=="GPU"){
       val gpuShape=longArrayOf(gpuBatch.toLong(),3,48,gpuWidth.toLong())
       val padAt=System.nanoTime()
       val padded=FixedGpuInput.pad(j.input,j.ids.size,j.shape[3].toInt(),gpuBatch,gpuWidth)
       val padMs=ms(System.nanoTime()-padAt)
       val o=gpu.infer(padded,gpuShape)
       details=o.detail+" compact_bytes="+o.values.size*4+" gpu_shape="+gpuShape.contentToString()+" gpu_pad_ms="+padMs+" real_rows="+j.ids.size+" dummy_rows="+(gpuBatch-j.ids.size)
       try{
        require(o.shape[0]==gpuBatch)
        val realShape=o.shape.copyOf().apply{this[0]=j.ids.size}
        decodeCompact(FixedGpuInput.realPrefix(o.values,o.shape,j.ids.size),realShape,j.shape[3].toInt())
       }catch(t:Throwable){
        synchronized(lock){finished.add("GPU_BATCH_FAIL ordinal="+j.ordinal+" ids="+j.ids+" original_shape="+j.shape.contentToString()+" output_shape="+o.shape.contentToString()+" "+details+" error="+t.message)}
        throw t
       }
      }else runCpu(j)
      val end=System.nanoTime()
      check(answers.size==j.ids.size)
      synchronized(lock){
       for(i in j.ids.indices){check(results[j.ids[i]]==null);results[j.ids[i]]=answers[i]}
       starts.add(Triple(name,at,end))
       finished.add("BATCH ordinal="+j.ordinal+" lane="+name+" ids="+j.ids+" shape="+j.shape.contentToString()+" prep_ms="+j.prepMs+" start_ms="+ms(at-began)+" finish_ms="+ms(end-began)+" host_ms="+ms(end-at)+" "+details)
      }
     }
     job=take()
    }
   }catch(t:Throwable){aborted.set(true);throw t}
  }
  val futures=ArrayList<java.util.concurrent.Future<Unit>>()
  try{
   futures.add(pool.submit(Callable<Unit>{lane("GPU",0)}))
   if(useCpu)futures.add(pool.submit(Callable<Unit>{lane("CPU",1)}))
   // Wait for both workers, including after error, before releasing bitmap/model.
   var failure:Throwable?=null
   for(f in futures)try{f.get()}catch(t:Throwable){aborted.set(true);if(failure==null)failure=t.cause?:t}
   for(line in finished)note(line)
   val cpu=starts.filter{it.first=="CPU"};val gpuRuns=starts.filter{it.first=="GPU"}
   var overlap=0L
   for(c in cpu)for(g in gpuRuns)overlap+=max(0L,min(c.third,g.third)-max(c.second,g.second))
   note("COOP_SUMMARY requested="+(if(mixed)"CPU_GPU" else "GPU")+" cpu_batches="+cpu.size+" gpu_batches="+gpuRuns.size+" host_overlap_ms="+ms(overlap)+" elapsed_ms="+ms(System.nanoTime()-began)+" groups="+groups.size+" (host overlap, not measured GPU kernel overlap)")
   if(failure!=null)throw failure
   check(results.all{it!=null}){"missing batch result"}
   return results.map{it!!}
  }finally{pool.shutdown()}
 }

 override fun close(){session.close()}
}
