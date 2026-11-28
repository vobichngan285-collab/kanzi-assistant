package com.vobich.bluestar.openocrlab

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class CoopCoreTest{
 @Test fun twoLanesClaimEveryBatchExactlyOnce(){
  val d=BatchDispatch(77,2);val pool=Executors.newFixedThreadPool(2)
  try{
   val workers=(0..1).map{i->pool.submit(java.util.concurrent.Callable<List<Int>>{
    val r=mutableListOf(d.first(i));while(true){r.add(d.next()?:break)};r
   })}
   val all=workers.flatMap{it.get()}
   assertEquals((0..76).toList(),all.sorted())
  }finally{pool.shutdownNow()}
 }
 @Test fun singleBatchHasNoFakeSecondLane(){
  val d=BatchDispatch(1,1);assertEquals(0,d.first(0));assertNull(d.next())
 }
 @Test fun duplicateReservedRejected(){
  val d=BatchDispatch(3,2);d.first(0)
  try{d.first(0);fail("duplicate claim accepted")}catch(_:IllegalArgumentException){}
 }
 @Test fun ctcPreservesBlankResetAndOriginalConfidence(){
  val x=floatArrayOf(1f,.9f,1f,.7f,0f,.8f,1f,.6f,2f,.5f,0f,.9f,2f,.4f,2f,.3f,0f,.9f,0f,.9f)
  val r=CompactCtc.decode(x,intArrayOf(2,5,2),listOf("","甲","乙"))
  assertEquals("甲甲乙",r[0].text);assertEquals((.9f+.6f+.5f)/3,r[0].confidence,1e-6f);assertEquals(.5f,r[0].minConfidence,1e-6f)
  assertEquals("乙",r[1].text);assertEquals(.4f,r[1].confidence,1e-6f)
 }
 @Test fun invalidIndexRejected(){
  try{CompactCtc.decode(floatArrayOf(1.5f,.9f),intArrayOf(1,1,2),listOf("","甲"));fail("fractional index accepted")}catch(_:IllegalArgumentException){}
 }
 @Test fun blankIsNotACharacter(){
  val r=CompactCtc.decode(floatArrayOf(0f,.9f,0f,.8f),intArrayOf(1,2,2),listOf("","甲")).single()
  assertEquals("",r.text);assertEquals(0f,r.confidence,0f);assertEquals(0f,r.minConfidence,0f)
 }

 @Test fun fixedWidthNeverShrinks(){
  assertEquals(320,FixedGpuInput.widthFor(1))
  assertEquals(704,FixedGpuInput.widthFor(672))
  assertEquals(4096,FixedGpuInput.widthFor(4096))
  try{FixedGpuInput.widthFor(4097);fail("oversize accepted")}catch(_:IllegalArgumentException){}
 }
 @Test fun fixedInputPreservesPixelsAndDuplicatesTail(){
  val width=3;val target=5;val real=4;val batch=6
  val src=FloatArray(real*3*48*width){(it+1).toFloat()}
  val buf=java.nio.FloatBuffer.wrap(src)
  val out=FixedGpuInput.pad(buf,real,width,batch,target)
  assertTrue(out.isDirect)
  assertEquals(batch*3*48*target,out.remaining())
  for(b in 0 until batch)for(y in 0 until 144)for(x in 0 until target){
   val expected=if(x<width)src[(minOf(b,real-1)*144+y)*width+x]else 0f
   assertEquals(expected,out.get(),0f)
  }
  assertEquals(0,buf.position())
 }
 @Test fun fixedPrefixDoesNotDecodeDummyRows(){
  val values=floatArrayOf(1f,.9f,0f,.8f,2f,.7f,0f,.8f,Float.NaN,.1f,Float.NaN,.2f)
  val prefix=FixedGpuInput.realPrefix(values,intArrayOf(3,2,2),2)
  val decoded=CompactCtc.decode(prefix,intArrayOf(2,2,2),listOf("","甲","乙"))
  assertEquals(listOf("甲","乙"),decoded.map{it.text})
 }
 @Test fun realInvalidValueStillRejected(){
  val values=floatArrayOf(Float.NaN,.9f,1f,.8f)
  try{CompactCtc.decode(FixedGpuInput.realPrefix(values,intArrayOf(2,1,2),1),intArrayOf(1,1,2),listOf("","甲"));fail("invalid real value accepted")}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("row=0 step=0"))}
 }

 @Test fun precisionModesIsolateWidthAndTail(){
  val original=longArrayOf(4,3,48,421)
  val fixed=longArrayOf(6,3,48,704)
  assertArrayEquals(fixed,CoopMode.CPU_FIXED.targetShape(original,704,6))
  assertArrayEquals(fixed,CoopMode.GPU_FIXED.targetShape(original,704,6))
  assertArrayEquals(original,CoopMode.GPU_EXACT.targetShape(original,0,6))
  assertArrayEquals(longArrayOf(6,3,48,421),CoopMode.GPU_TAIL_FIXED.targetShape(original,0,6))
  val full=longArrayOf(6,3,48,503)
  assertArrayEquals(full,CoopMode.GPU_TAIL_FIXED.targetShape(full,0,6))
 }
}
