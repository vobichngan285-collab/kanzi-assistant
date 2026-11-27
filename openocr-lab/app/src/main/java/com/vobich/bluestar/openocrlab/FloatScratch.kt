package com.vobich.bluestar.openocrlab

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Worker-confined bounded scratch space. Never reuse until the input tensor is closed.
 * Oversize requests are deliberately not retained. Each lease has exactly the requested limit. */
internal class FloatScratch(private val maxRetainedBytes: Int) {
 private var storage: FloatBuffer? = null
 var allocations=0L
  private set
 var reuses=0L
  private set
 val retainedBytes:Int get()=(storage?.capacity()?:0)*4
 fun acquire(elements:Int):FloatBuffer {
  require(elements>0 && elements<=Int.MAX_VALUE/4)
  val bytes=elements*4
  val old=storage
  val backing=if(bytes<=maxRetainedBytes && old!=null && old.capacity()>=elements){
   reuses++;old
  }else{
   allocations++
   ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer().also{
    if(bytes<=maxRetainedBytes)storage=it
   }
  }
  val lease=backing.duplicate()
  lease.clear()
  lease.limit(elements)
  return lease.slice()
 }
 fun clear(){storage=null}
}
