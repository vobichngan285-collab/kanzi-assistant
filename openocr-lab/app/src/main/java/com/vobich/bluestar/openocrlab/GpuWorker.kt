package com.vobich.bluestar.openocrlab

import android.app.Service
import android.content.*
import android.os.*
import android.system.OsConstants
import java.io.File
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Native GPU lives in a separate process: previous RepSVTR OpenCL crashed.
// Every handle stays on the same service HandlerThread.
internal class NativeMnn(path:String,backend:Int,threads:Int,cachePath:String):AutoCloseable{
 companion object { init{System.loadLibrary("ocr_mnn_jni")} }
 private external fun open(path:String,backend:Int,threads:Int,cachePath:String):Long
 private external fun run(handle:Long,input:FloatBuffer,shape:LongArray):FloatArray
 private external fun info(handle:Long):String
 private external fun outputShape(handle:Long):IntArray
 private external fun close(handle:Long)
 private val handle=open(path,backend,threads,cachePath).also{check(it!=0L)}
 fun infer(input:FloatBuffer,shape:LongArray)=run(handle,input,shape)
 fun details()=info(handle)
 fun shape()=outputShape(handle)
 override fun close(){close(handle)}
}
class GpuWorker:Service(){
 private lateinit var thread:HandlerThread
 private lateinit var handler:Handler
 private var engine:NativeMnn?=null
 private var file:File?=null
 override fun onCreate(){
  super.onCreate()
  thread=HandlerThread("OpenOCR-OpenCL").apply{start()}
  handler=Handler(thread.looper){m->
   val reply=Message.obtain(null,m.what)
   val b=Bundle()
   try{
    when(m.what){
     1->{
      check(engine==null){"already initialized"}
      val stage=SystemClock.elapsedRealtime()
      val f=File.createTempFile("rep-compact-", ".mnn",cacheDir);file=f
      assets.open("rep-compact.mnn").use{src->f.outputStream().use{src.copyTo(it)}}
      val loaded=SystemClock.elapsedRealtime()
      val cache=File(cacheDir,"rep-576805263c70-mnn350-opencl-high-fast-v012.cache")
      val before=cache.length()
      engine=NativeMnn(f.absolutePath,3,m.arg1,cache.absolutePath)
      b.putString("detail","worker_pid="+android.os.Process.myPid()+" stage_ms="+(loaded-stage)+" load_ms="+(SystemClock.elapsedRealtime()-loaded)+" cache_before_bytes="+before+" "+engine!!.details())
     }
     2->{
      val e=engine?:error("GPU not initialized")
      @Suppress("DEPRECATION") val mem=m.data.getParcelable<SharedMemory>("tensor")?:error("missing shared tensor")
      try{
       val shape=m.data.getLongArray("shape")?:error("missing input shape")
       require(shape.size==4&&shape.all{it in 1..4096}&&shape[1]==3L&&shape[2]==48L)
       val count=shape.fold(1L){a,v->a*v}
       require(count*4==mem.size.toLong()&&count<=32L*1024*1024)
       val bytes=mem.mapReadOnly()
       try{
        val out=e.infer(bytes.order(ByteOrder.nativeOrder()).asFloatBuffer(),shape)
        val dims=e.shape()
        require(dims.size==3&&dims[0].toLong()==shape[0]&&dims[2]==2&&out.size<=65536&&dims.fold(1L){a,v->a*v}==out.size.toLong())
        val details=e.details()
        val commands=Regex("executed_commands_by_backend=([^ ]*)").find(details)?.groupValues?.get(1).orEmpty()
        check(Regex("(?:^|,)3:[1-9][0-9]*(?:,|$)").containsMatchIn(commands)){"OpenCL did not execute any traced command: "+details}
        b.putFloatArray("values",out);b.putIntArray("dims",dims);b.putString("detail",details+" cache_bytes="+File(cacheDir,"rep-576805263c70-mnn350-opencl-high-fast-v012.cache").length())
        b.putLong("pss",Debug.getPss())
       }finally{SharedMemory.unmap(bytes)}
      }finally{mem.close()}
     }
     3->{
      // Fresh interpreter/session, same process and persistent cache.
      val f=file?:error("model not staged")
      val started=SystemClock.elapsedRealtime()
      val old=engine;engine=null;old?.close()
      val cache=File(cacheDir,"rep-576805263c70-mnn350-opencl-high-fast-v012.cache")
      val before=cache.length()
      engine=NativeMnn(f.absolutePath,3,m.arg1,cache.absolutePath)
      b.putString("detail","worker_pid="+android.os.Process.myPid()+" session_reset_ms="+(SystemClock.elapsedRealtime()-started)+" cache_before_bytes="+before+" "+engine!!.details())
     }
     else->error("unknown GPU message")
    }
   }catch(t:Throwable){b.putString("error",t.toString())}
   reply.data=b
   runCatching{m.replyTo.send(reply)}
   true
  }
 }
 override fun onBind(i:Intent?):IBinder=Messenger(handler).binder
 override fun onDestroy(){
  handler.post{try{engine?.close()}finally{engine=null;file?.delete();thread.quitSafely()}}
  super.onDestroy()
 }
}

internal class GpuClient(private val context:Context,threads:Int):AutoCloseable{
 data class Output(val values:FloatArray,val shape:IntArray,val detail:String)
 private val connected=CountDownLatch(1)
 private val responses=ArrayBlockingQueue<Bundle>(1)
 private val replies=HandlerThread("OpenOCR-GPU-reply").apply{start()}
 private val reply=Messenger(Handler(replies.looper){m->responses.offer(m.data);true})
 @Volatile private var remote:Messenger?=null
 @Volatile private var failed=false
 private var bound=false
 private var closed=false
 private fun disconnected(){
  failed=true;remote=null;connected.countDown()
  responses.offer(Bundle().apply{putString("error","GPU worker process disconnected or crashed")})
 }
 private val conn=object:ServiceConnection{
  override fun onServiceConnected(n:ComponentName,b:IBinder){remote=Messenger(b);connected.countDown()}
  override fun onServiceDisconnected(n:ComponentName){disconnected()}
  override fun onBindingDied(n:ComponentName){disconnected()}
  override fun onNullBinding(n:ComponentName){disconnected()}
 }
 val loadDetail:String
 init{
  try{
   bound=context.bindService(Intent(context,GpuWorker::class.java),conn,Context.BIND_AUTO_CREATE)
   check(bound&&connected.await(20,TimeUnit.SECONDS)&&remote!=null&&!failed){"GPU service connection failed"}
   loadDetail=call(1,Bundle(),threads).getString("detail").orEmpty()
  }catch(t:Throwable){close();throw t}
 }
 private fun call(kind:Int,b:Bundle,threads:Int=0):Bundle{
  check(!failed&&!closed){"GPU worker unavailable"}
  val r=remote?:error("GPU worker unavailable")
  val m=Message.obtain(null,kind);m.arg1=threads;m.data=b;m.replyTo=reply
  r.send(m)
  val result=responses.poll(120,TimeUnit.SECONDS)?:run{failed=true;error("GPU worker timed out after 120s")}
  result.getString("error")?.let{failed=true;error(it)}
  return result
 }
 fun resetSession(threads:Int):String=call(3,Bundle(),threads).getString("detail").orEmpty()
 fun infer(input:FloatBuffer,shape:LongArray):Output{
  val start=SystemClock.elapsedRealtime()
  val data=input.duplicate().apply{rewind()}
  val bytes=Math.multiplyExact(data.remaining(),4)
  require(bytes.toLong()==shape.fold(4L){a,v->a*v})
  val mem=SharedMemory.create("ocr-tensor",bytes)
  try{
   val buffer=mem.mapReadWrite()
   try{buffer.order(ByteOrder.nativeOrder()).asFloatBuffer().put(data)}finally{SharedMemory.unmap(buffer)}
   check(mem.setProtect(OsConstants.PROT_READ))
   val copied=SystemClock.elapsedRealtime()
   val result=call(2,Bundle().apply{putParcelable("tensor",mem);putLongArray("shape",shape)})
   return Output(result.getFloatArray("values")?:error("missing compact values"),result.getIntArray("dims")?:error("missing compact shape"),
    "ipc_input_copy_ms="+(copied-start)+" ipc_roundtrip_ms="+(SystemClock.elapsedRealtime()-copied)+" gpu_process_pss_kb="+result.getLong("pss")+" "+result.getString("detail"))
  }finally{mem.close()}
 }
 override fun close(){
  if(closed)return
  closed=true
  if(bound)runCatching{context.unbindService(conn)}
  bound=false;replies.quitSafely()
 }
}
