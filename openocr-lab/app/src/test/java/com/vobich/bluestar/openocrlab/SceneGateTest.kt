package com.vobich.bluestar.openocrlab

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneGateTest {
 private fun flat(v:Int=110)=IntArray(SceneGate.SIZE){v}
 private fun pattern(phase:Int)=IntArray(SceneGate.SIZE){i->
  if(((i%SceneGate.COLS+phase)/4)%2==0)50 else 170
 }
 private fun seed(g:SceneGate,s:IntArray){
  assertEquals(SceneGate.Action.WAIT,g.observe(s).action)
  assertEquals(SceneGate.Action.WAIT,g.observe(s.copyOf()).action)
  assertEquals(SceneGate.Action.START,g.observe(s.copyOf()).action)
 }
 @Test fun staticStartsOnce(){
  val g=SceneGate();val s=pattern(0);seed(g,s)
  repeat(300){assertEquals(SceneGate.Action.HOLD,g.observe(s.copyOf()).action)}
 }
 @Test fun continuousMotionCancelsOnceThenStartsLatest(){
  val g=SceneGate();val a=pattern(0);val b=pattern(4);seed(g,a)
  assertEquals(SceneGate.Action.INVALIDATE,g.observe(b).action)
  repeat(300){i->assertEquals(SceneGate.Action.WAIT,g.observe(if(i%2==0)a else b).action)}
  assertEquals(SceneGate.Action.WAIT,g.observe(a).action)
  assertEquals(SceneGate.Action.WAIT,g.observe(a.copyOf()).action)
  assertEquals(SceneGate.Action.START,g.observe(a.copyOf()).action)
  assertEquals(SceneGate.Action.HOLD,g.observe(a.copyOf()).action)
 }
 @Test fun globalExposureAndSmallJitterDoNotInvalidate(){
  val g=SceneGate();val s=pattern(0);seed(g,s)
  for(offset in listOf(24,-23,40,0)){
   val next=IntArray(s.size){s[it]+offset+(it%3)-1}
   assertEquals(SceneGate.Action.HOLD,g.observe(next).action)
  }
 }
 @Test fun localChangeCanTriggerDespiteSmallWholeFrameMean(){
  val g=SceneGate();val a=flat();seed(g,a)
  val b=a.copyOf()
  for(y in 0..5)for(x in 0..7)b[y*SceneGate.COLS+x]=25
  assertEquals(SceneGate.Action.INVALIDATE,g.observe(b).action)
  assertEquals(SceneGate.Action.WAIT,g.observe(b.copyOf()).action)
  assertEquals(SceneGate.Action.START,g.observe(b.copyOf()).action)
 }
 @Test fun suddenNewSceneUsesTwoQuietComparisons(){
  val g=SceneGate();seed(g,pattern(0))
  val b=pattern(4)
  assertEquals(SceneGate.Action.INVALIDATE,g.observe(b).action)
  assertEquals(SceneGate.Action.WAIT,g.observe(b.copyOf()).action)
  assertEquals(SceneGate.Action.START,g.observe(b.copyOf()).action)
 }
 @Test fun explicitRescanBypassesQuietGate(){
  val g=SceneGate()
  assertEquals(SceneGate.Action.START,g.observe(pattern(0),true).action)
  assertEquals(SceneGate.Action.HOLD,g.observe(pattern(0)).action)
  assertEquals(SceneGate.Action.START,g.observe(pattern(0),true).action)
 }
}
