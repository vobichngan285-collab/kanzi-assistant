package com.vobich.bluestar.openocrlab
import org.junit.Test
import org.junit.Assert.*
class FocusPlannerTest {
 private fun r(x:Float,y:Float,w:Float=120f,h:Float=24f,s:Float=.9f)=FocusPlanner.R(x,y,x+w,y+h,s)
 private fun block(x:Float,y:Float)= (0..5).flatMap{i->listOf(r(x,y+40*i),r(x+230,y+40*i,55f))}
 @Test fun dominantRegionKeepsWeakNearbyAndExcludesDistantTiny(){
  val main=block(400f,400f);val weak=r(410f,380f,50f,20f,.3f)
  val tiny=(0..8).map{i->r(40f,1100f+i*15,25f,8f,.5f)}
  val p=FocusPlanner.plan(main+weak+tiny,1600,1800)
  assertEquals("dominant_region",p.reason);val roi=p.roi!!
  assertTrue(main.all{roi.contains(it)});assertTrue(roi.contains(weak));assertTrue(tiny.none{roi.contains(it)})
 }
 @Test fun equalRegionsFallBack(){assertNull(FocusPlanner.plan(block(100f,200f)+block(1100f,1000f),1800,1800).roi)}
 @Test fun sparseOrVerticalFallsBack(){
  assertNull(FocusPlanner.plan(listOf(r(40f,40f)),1000,1000).roi)
  assertEquals("possible_vertical",FocusPlanner.plan(block(100f,100f)+r(800f,400f,25f,300f),1200,1200).reason)
 }
 @Test fun duplicateAnchorsDoNotCreateFalseDominance(){
  val a=block(100f,100f);val b=block(1000f,1000f)
  assertNull(FocusPlanner.plan(a+a+a+b,1800,1800).roi)
 }
 @Test fun offCenterRegionNotPenalized(){
  val main=block(50f,50f);val p=FocusPlanner.plan(main+listOf(r(1100f,1300f,25f,8f,.5f)),1600,1800)
  assertEquals("dominant_region",p.reason);assertTrue(main.all{p.roi!!.contains(it)})
 }
}
