package com.vobich.bluestar.openocrlab

import android.content.Context
import android.os.Debug
import ai.onnxruntime.*
import org.json.JSONArray
import java.io.*
import java.nio.*
import kotlin.math.*

class NativeMnn(path:String,backend:Int,threads:Int):AutoCloseable{
 companion object { init{System.loadLibrary("ocr_mnn_jni")} }
 private external fun open(path:String,backend:Int,threads:Int):Long
 private external fun run(handle:Long,input:FloatBuffer,shape:LongArray):FloatArray
 private external fun info(handle:Long):String
 private external fun outputShape(handle:Long):IntArray
 private external fun close(handle:Long)
 private val handle=open(path,backend,threads).also{check(it!=0L)}
 fun infer(input:FloatBuffer,shape:LongArray):FloatArray=run(handle,input,shape)
 fun details()=info(handle)
 fun shape()=outputShape(handle)
 override fun close(){close(handle)}
}
class GpuReplay(private val context:Context,private val model:String,private val threads:Int,private val note:(String)->Unit):AutoCloseable{
 private data class Batch(val input:FloatBuffer,val shape:LongArray,val ids:List<Int>)
 private val batches=ArrayList<Batch>()
 private var bytes=0L
 private val temp=File.createTempFile("ocr-gpu-",".tmp",context.cacheDir).also{check(it.delete());check(it.mkdir())}
 private val chars=if(model=="rep")listOf("")+context.assets.open("chars.txt").bufferedReader(Charsets.UTF_8).use{it.readLines()}+listOf(" ")
 else context.assets.open("v2-characters.json").bufferedReader(Charsets.UTF_8).use{val a=JSONArray(it.readText());List(a.length()){i->a.getString(i)}}
 private val onnx=if(model=="rep")"rep.onnx" else "openocrv2_ctc.onnx"
 private val mnn=if(model=="rep")"rep.mnn" else "v2.mnn"
 private fun now()=System.nanoTime()
 private fun ms(n:Long)=n/1e6
 fun capture(input:FloatBuffer,shape:LongArray,ids:List<Int>){
  val buf=input.duplicate();buf.rewind()
  check(buf.remaining().toLong()==shape.fold(1L){a,b->a*b})
  bytes+=buf.remaining()*4L
  require(bytes<=128L*1024*1024){"Prepared tensor replay exceeds 128 MiB"}
  batches.add(Batch(buf,shape.copyOf(),ids.toList()))
 }
 fun checkCoverage(count:Int){
  check(batches.flatMap{it.ids}.sorted()==(0 until count).toList()){"Missing or repeated box IDs"}
  note("replay_batches=${batches.size} tensor_bytes=$bytes classes=${chars.size}")
 }
 private data class Decoded(val texts:List<String>,val argmax:IntArray,val confidence:List<Float>)
 private fun decode(a:FloatArray,shape:IntArray):Decoded{
  require(shape.size==3&&shape[2]==chars.size&&shape.fold(1L){x,y->x*y}==a.size.toLong())
  val n=shape[0];val t=shape[1];val c=shape[2];val indices=IntArray(n*t);val strings=ArrayList<String>();val conf=ArrayList<Float>()
  for(b in 0 until n){
   var last=-1;val s=StringBuilder();var sum=0f;var emitted=0
   for(k in 0 until t){
    val offset=(b*t+k)*c;var top=0;var v=-Float.MAX_VALUE
    for(j in 0 until c){val x=a[offset+j];require(x.isFinite()){"Nonfinite output"};if(x>v){v=x;top=j}}
    indices[b*t+k]=top
    if(top!=0&&top!=last){s.append(chars[top]);sum+=v;emitted++};last=top
   }
   strings.add(s.toString());conf.add(if(emitted>0)sum/emitted else 0f)
  }
  return Decoded(strings,indices,conf)
 }
 private fun ref(i:Int)=File(temp,"ref-$i.bin")
 private fun writeRef(i:Int,a:FloatArray){
  DataOutputStream(BufferedOutputStream(FileOutputStream(ref(i)),65536)).use{out->for(v in a)out.writeFloat(v)}
 }
 private data class Diff(val max:Float,val mean:Double,val outside:Long,val steps:Int,val text:Int)
 private fun compare(i:Int,a:FloatArray,shape:IntArray,d:Decoded,reference:Decoded):Diff{
  var worst=0f;var sum=0.0;var outside=0L
  DataInputStream(BufferedInputStream(FileInputStream(ref(i)),65536)).use{inp->
   for(v in a){val r=inp.readFloat();val e=abs(v-r);worst=max(worst,e);sum+=e.toDouble();if(e>5e-4f+3e-3f*abs(r))outside++}
   check(inp.read()==-1)
  }
  check(d.argmax.size==reference.argmax.size&&shape[0]==reference.texts.size)
  return Diff(worst,sum/a.size,outside,d.argmax.indices.count{d.argmax[it]!=reference.argmax[it]},d.texts.indices.count{d.texts[it]!=reference.texts[it]})
 }
 private data class Out(val values:FloatArray,val shape:IntArray,val detail:String="")
 private val refs=HashMap<Int,Decoded>()
 private val shapes=HashMap<Int,IntArray>()
 private fun passes(name:String,infer:(Batch)->Out){
  for(pass in 1..2){
   var runNs=0L;var decNs=0L;var valNs=0L;var differs=0;var stepDiff=0;var outside=0L;var total=0L;var worst=0f
   val wall=now()
   for((i,b) in batches.withIndex()){
    val start=now();val out=infer(b);val elapsed=now()-start;runNs+=elapsed
    val ds=now();val decoded=decode(out.values,out.shape);decNs+=now()-ds
    check(out.shape[0]==b.ids.size)
    val vs=now()
    if(name=="ORT_CPU"&&pass==2){writeRef(i,out.values);refs[i]=decoded;shapes[i]=out.shape.copyOf()}
    if(name!="ORT_CPU"){
     check(out.shape.contentEquals(shapes.getValue(i))){"Output shape mismatch"}
     val diff=compare(i,out.values,out.shape,decoded,refs.getValue(i))
     differs+=diff.text;stepDiff+=diff.steps;outside+=diff.outside;total+=out.values.size;worst=max(worst,diff.max)
     note("$name pass=$pass batch=$i diff_max=${diff.max} mean=${diff.mean} outside=${diff.outside}/${out.values.size} argmax_diff=${diff.steps} text_diff=${diff.text}")
    }
    valNs+=now()-vs
    note("$name pass=$pass batch=$i input=${b.shape.contentToString()} output=${out.shape.contentToString()} ids=${b.ids.map{it+1}} batch_host_ms=${ms(elapsed)} ${out.detail}")
    if(pass==2)note(b.ids.indices.joinToString("\n"){j->"$name id=${b.ids[j]+1} rec=${decoded.confidence[j]} text=${decoded.texts[j]}"})
   }
   note("$name pass=$pass replay_ms=${ms(runNs+decNs)} batch_host_ms=${ms(runNs)} decode_ms=${ms(decNs)} validation_ms=${ms(valNs)} wall_ms=${ms(now()-wall)} text_diff=$differs argmax_diff=$stepDiff outside=$outside/$total max_abs=$worst pss_kib=${Debug.getPss()} (PSS snapshot; replay excludes DB and capture)")
  }
 }
 fun run(target:Int){
  val env=OrtEnvironment.getEnvironment()
  val load=now()
  val opt=OrtSession.SessionOptions()
  val session=try{opt.setIntraOpNumThreads(threads);env.createSession(context.assets.open(onnx).use{it.readBytes()},opt)}finally{opt.close()}
  session.use{s->
   note("ORT_CPU load_ms=${ms(now()-load)}")
   passes("ORT_CPU"){b->
    val buf=b.input.duplicate();buf.rewind()
    OnnxTensor.createTensor(env,buf,b.shape).use{input->
     s.run(mapOf(s.inputNames.single() to input)).use{result->
      val t=result[0] as OnnxTensor;val f=t.floatBuffer;f.rewind();val a=FloatArray(f.remaining());f.get(a)
      Out(a,t.info.shape.map{it.toInt()}.toIntArray())
     }
    }
   }
  }
  val file=File(temp,mnn);val stage=now()
  context.assets.open(mnn).use{input->file.outputStream().use{input.copyTo(it)}}
  note("MNN asset_stage_ms=${ms(now()-stage)} (excluded from replay)")
  for(backend in (if(target==0)listOf(0) else listOf(0,target))){
   val name=when(backend){0->"MNN_CPU";3->"MNN_OPENCL";7->"MNN_VULKAN";else->error("backend")}
   try{
    val start=now()
    NativeMnn(file.absolutePath,backend,threads).use{engine->
     note("$name load_ms=${ms(now()-start)} ${engine.details()}")
     passes(name){b->val input=b.input.duplicate();input.rewind();val a=engine.infer(input,b.shape);Out(a,engine.shape(),engine.details())}
    }
   }catch(t:Throwable){note("$name FAILED ${t.stackTraceToString()}")}
   note("$name closed_pss_kib=${Debug.getPss()}")
  }
 }
 override fun close(){temp.deleteRecursively()}
}
