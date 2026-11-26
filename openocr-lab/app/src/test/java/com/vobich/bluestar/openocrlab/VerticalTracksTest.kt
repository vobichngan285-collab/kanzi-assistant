package com.vobich.bluestar.openocrlab
import org.junit.Test
import org.junit.Assert.*
class VerticalTracksTest{
 fun f(id:Int,x:Float,y:Float,h:Float=120f,w:Float=30f)=VerticalTracks.Fragment(id,x-.05f*y,y,w,h,-.05f,.8f)
 @Test fun joinsTiltedFragmentsAndSquareGlyphs(){
  val v=listOf(f(0,200f,100f),f(1,200f,270f),f(2,200f,185f,30f),f(3,110f,100f),f(4,110f,270f),f(5,300f,180f,30f,150f))
  val t=VerticalTracks.build(v);assertEquals(2,t.size);assertEquals(setOf(0,1,2),t[0].ids.toSet());assertEquals(setOf(3,4),t[1].ids.toSet());assertTrue(t[0].top<=40f&&t[0].bottom>=330f)
 }
 @Test fun separatesDistantVerticalRegions(){assertEquals(2,VerticalTracks.build(listOf(f(0,200f,100f),f(1,200f,700f))).size)}
 @Test fun blankAndShortProjectionRetainsBaseline(){val r=listOf(4..20,30..48);assertEquals(r,VerticalTracks.gridCuts(IntArray(64),30,r))}
}
