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
}
