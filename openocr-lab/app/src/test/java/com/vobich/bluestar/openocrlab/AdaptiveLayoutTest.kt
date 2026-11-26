package com.vobich.bluestar.openocrlab
import org.junit.Test
import org.junit.Assert.*
class AdaptiveLayoutTest{
 private fun r(l:Float,t:Float,w:Float,h:Float)=CropPolicy.R(l,t,l+w,t+h)
 @Test fun singletonKeepsOriginal(){assertEquals(listOf(0),CropPolicy.members(r(0f,0f,110f,40f),listOf(r(5f,5f,90f,25f),r(120f,0f,30f,30f))))}
 @Test fun separateLongLinesButJoinGlyphs(){
  assertTrue(CropPolicy.independentLongLines(listOf(r(0f,0f,100f,20f),r(130f,0f,100f,20f))))
  assertFalse(CropPolicy.independentLongLines(listOf(r(0f,0f,20f,20f),r(30f,0f,20f,20f))))
  assertFalse(CropPolicy.independentLongLines(listOf(r(0f,0f,100f,20f),r(105f,0f,100f,20f))))
 }
 @Test fun signsTriggerReviewWithoutChangingText(){
  assertTrue(CropPolicy.isSignedNumber("-42"));assertTrue(CropPolicy.isSignedNumber("--42"))
  assertTrue(CropPolicy.isSignedNumber("− 42"));assertFalse(CropPolicy.isSignedNumber("42"))
 }
 @Test fun projectionHandlesBothPolarities(){
  val w=64;val h=192
  for((bg,fg) in listOf(240 to 15,15 to 240)){
   val p=IntArray(w*h){i->val y=i/w;val x=i%w;if(x in 12..50&&(y in 10..48||y in 74..112||y in 138..176))fg else bg}
   val result=GlyphProjection.analyze(p,w,h)
   assertEquals(bg,result.background)
   assertEquals(listOf(10..48,74..112,138..176),MixedLayout.cuts(result.profile,w))
   assertTrue(GlyphProjection.analyze(IntArray(w*h){bg},w,h).profile.all{it==0})
  }
 }
}
