package com.vobich.bluestar.openocrlab
import org.junit.Assert.*
import org.junit.Test

class AdaptiveLayoutTest {
 private fun r(l:Float,t:Float,w:Float,h:Float)=AdaptiveLayout.R(l,t,l+w,t+h)
 private fun table()=(0..9).map{i->listOf(r(1164f,1528f+i*48,223f,48f),r(1530f,1521f+i*48,100f,43f))}
 @Test fun repeatedPriceRows(){val d=AdaptiveLayout.decide("dominant_region",table(),emptyList());assertTrue(d.structured);assertEquals(10,d.support.size)}
 @Test fun independentSign(){val rows=listOf(listOf(r(20f,20f,400f,90f)),listOf(r(50f,160f,80f,40f)),listOf(r(380f,230f,120f,20f)));assertFalse(AdaptiveLayout.decide("dominant_region",rows,emptyList()).structured)}
 @Test fun largeOutsideHeading(){assertFalse(AdaptiveLayout.decide("dominant_region",table(),listOf(r(100f,100f,800f,180f))).structured)}
 @Test fun mixedRegions(){assertFalse(AdaptiveLayout.decide("multiple_regions",table(),emptyList()).structured)}
 @Test fun noFocus(){assertFalse(AdaptiveLayout.decide("whole_image",table(),emptyList()).structured)}
 @Test fun originalPriceCell(){val c=r(1530f,1521f,100f,50f);assertEquals(0,AdaptiveLayout.originalCell(c,listOf(r(1540f,1529f,80f,36f))))}
 @Test fun noFragmentSubstitution(){val c=r(1530f,1521f,100f,50f);assertNull(AdaptiveLayout.originalCell(c,listOf(r(1540f,1529f,30f,36f),r(1580f,1529f,30f,36f))))}
}
