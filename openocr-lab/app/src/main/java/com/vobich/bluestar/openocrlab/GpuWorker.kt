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
internal class NativeMnn(path:String,backend:Int,threads:Int,cachePath:String,tuning:GpuTuning):AutoCloseable{
 companion object { init{System.loadLibrary("ocr_mnn_jni")} }
 private external fun open(path:String,backend:Int,threads:Int,cachePath:String,tuning:Int):Long
 private external fun run(handle:Long,input:FloatBuffer,shape:LongArray):FloatArray
 private external fun info(handle:Long):String
 private external fun outputShape(handle:Long):IntArray
 private external fun close(handle:Long)
 private val handle=open(path,backend,threads,cachePath,tuning.wire).also{check(it!=0L)}
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
 private var tuning=GpuTuning.FAST
 override fun onCreate(){
  super.onCreate()
  thread=HandlerThread("OpenOCR-OpenCL").apply{start()}
  handler=Handler(thread.looper){m->
   val reply=Message.obtain(null,m.what)
   val b=Bundle()
   try{
    when(m.what){
     1->{
      // A replacement client may bind before Android destroys the prior service.
      // All native handles remain serialized on this HandlerThread.
      val old=engine;engine=null;old?.close();file?.delete();file=null
      tuning=GpuTuning.fromWire(m.arg2)
      val stage=SystemClock.elapsedRealtime()
      val f=File.createTempFile("rep-compact-", ".mnn",cacheDir);file=f
      assets.open("rep-compact.mnn").use{src->f.outputStream().use{src.copyTo(it)}}
      val loaded=SystemClock.elapsedRealtime()
      val cache=File(cacheDir,tuning.cacheName)
      val before=cache.length()
      engine=NativeMnn(f.absolutePath,3,m.arg1,cache.absolutePath,tuning)
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
        b.putFloatArray("values",out);b.putIntArray("dims",dims);b.putString("detail",details+" cache_bytes="+File(cacheDir,tuning.cacheName).length())
        b.putLong("pss",Debug.getPss())
       }finally{SharedMemory.unmap(bytes)}
      }finally{mem.close()}
     }
     3->{
      // Fresh interpreter/session, same process and persistent cache.
      val f=file?:error("model not staged")
      val started=SystemClock.elapsedRealtime()
      val old=engine;engine=null;old?.close()
      val cache=File(cacheDir,tuning.cacheName)
      val before=cache.length()
      engine=NativeMnn(f.absolutePath,3,m.arg1,cache.absolutePath,tuning)
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

// Transport loss is separate from engine errors and timeouts. Only a known
// pre-dispatch loss may be recovered before the first GPU inference of a page.
internal class GpuTransportLost(message:String,val beforeSend:Boolean):IllegalStateException(message)
internal class GpuClient(private val context:Context,threads:Int,val tuning:GpuTuning):AutoCloseable{
 data class Output(val values:FloatArray,val shape:IntArray,val detail:String)
 private val connected=CountDownLatch(1)
 private val responses=ArrayBlockingQueue<Bundle>(1)
 private val replies=HandlerThread("OpenOCR-GPU-reply").apply{start()}
 private val reply=Messenger(Handler(replies.looper){m->responses.offer(m.data);true})
 private val linkLock=Any()
 @Volatile private var remote:Messenger?=null
 @Volatile private var failureReason:String?=null
 @Volatile private var transportLost=false
 @Volatile private var closed=false
 @Volatile var completedInferences=0L
  private set
 private var bound=false
 private var linkedBinder:IBinder?=null
 private val death=IBinder.DeathRecipient{disconnected("binder_death")}
 private fun disconnected(reason:String){
  synchronized(linkLock){
   if(closed||failureReason!=null)return
   transportLost=true
   failureReason=reason
   remote=null
   connected.countDown()
   responses.offer(Bundle().apply{putString("error","GPU transport lost: "+reason);putBoolean("local_transport_lost",true)})
  }
 }
 private fun engineFailed(reason:String){
  synchronized(linkLock){if(failureReason==null)failureReason=reason}
 }
 private val conn=object:ServiceConnection{
  override fun onServiceConnected(n:ComponentName,b:IBinder){
   synchronized(linkLock){
    // A client generation is permanently invalid after death. Never adopt
    // Android's automatic reconnect into an already initialized client.
    if(closed||failureReason!=null)return
    if(linkedBinder!=null&&linkedBinder!==b){
     disconnected("unexpected_reconnect");return
    }
    try{
     if(linkedBinder==null){b.linkToDeath(death,0);linkedBinder=b}
     if(!b.isBinderAlive){disconnected("binder_not_alive");return}
     remote=Messenger(b);connected.countDown()
    }catch(t:RemoteException){disconnected("link_to_death_failed")}
   }
  }
  override fun onServiceDisconnected(n:ComponentName){disconnected("service_disconnected")}
  override fun onBindingDied(n:ComponentName){disconnected("binding_died")}
  override fun onNullBinding(n:ComponentName){disconnected("null_binding")}
 }
 val loadDetail:String
 init{
  try{
   // Keep the worker at the client's importance while bound; Android may
   // still reclaim it, especially when the photo picker backgrounds us.
   bound=context.bindService(Intent(context,GpuWorker::class.java),conn,Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
   check(bound&&connected.await(20,TimeUnit.SECONDS)&&remote!=null&&failureReason==null){"GPU service connection failed: "+failureReason}
   loadDetail=call(1,Bundle(),threads).getString("detail").orEmpty()
  }catch(t:Throwable){close();throw t}
 }
 // Called on the OCR executor, never on the UI thread. A live binder is not
 // proof of a healthy GPU engine; engine failures invalidate the client too.
 fun unavailableReason(probe:Boolean=false):String?{
  if(closed)return "closed"
  failureReason?.let{return it}
  val r=remote?:return "not_connected"
  if(!r.binder.isBinderAlive||(probe&&!r.binder.pingBinder())){
   disconnected("binder_not_alive")
   return failureReason?:"binder_not_alive"
  }
  return failureReason
 }
 private fun call(kind:Int,b:Bundle,threads:Int=0):Bundle{
  unavailableReason()?.let{
   if(transportLost)throw GpuTransportLost("GPU transport lost before send: "+it,true)
   error("GPU worker unavailable: "+it)
  }
  val r=remote?:throw GpuTransportLost("GPU connection lost before send",true)
  val m=Message.obtain(null,kind);m.arg1=threads;m.arg2=tuning.wire;m.data=b;m.replyTo=reply
  try{r.send(m)}catch(t:RemoteException){
   disconnected("send_remote_exception")
   throw GpuTransportLost("GPU transport lost during send: "+t,false)
  }
  val result=responses.poll(120,TimeUnit.SECONDS)?:run{engineFailed("response_timeout");error("GPU worker timed out after 120s")}
  if(result.getBoolean("local_transport_lost"))throw GpuTransportLost(result.getString("error").orEmpty(),false)
  result.getString("error")?.let{engineFailed("engine_error");error(it)}
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
   val out=Output(result.getFloatArray("values")?:error("missing compact values"),result.getIntArray("dims")?:error("missing compact shape"),
    "ipc_input_copy_ms="+(copied-start)+" ipc_roundtrip_ms="+(SystemClock.elapsedRealtime()-copied)+" gpu_process_pss_kb="+result.getLong("pss")+" "+result.getString("detail"))
   completedInferences++
   return out
  }finally{mem.close()}
 }
 override fun close(){
  synchronized(linkLock){
   if(closed)return
   closed=true;remote=null;connected.countDown()
   linkedBinder?.let{runCatching{it.unlinkToDeath(death,0)}};linkedBinder=null
   responses.offer(Bundle().apply{putString("error","GPU client closed");putBoolean("local_transport_lost",true)})
  }
  if(bound)runCatching{context.unbindService(conn)}
  bound=false;replies.quitSafely()
 }
}
