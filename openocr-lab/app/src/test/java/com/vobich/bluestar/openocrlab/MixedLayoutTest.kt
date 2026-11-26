package com.vobich.bluestar.openocrlab
import org.junit.Test
import org.junit.Assert.*
class MixedLayoutTest {
 @Test fun blankGapsSplitUprightGlyphs(){
  val profile=IntArray(180){if(it in 5..45||it in 65..105||it in 125..165)20 else 0}
  assertEquals(listOf(5..45,65..105,125..165),MixedLayout.cuts(profile,60))
 }
 @Test fun tallRunSplitsIntoEstimatedPitch(){
  val profile=IntArray(120){20}
  assertEquals(2,MixedLayout.cuts(profile,60).size)
 }
 @Test fun blankStripDoesNotInventGlyphs(){assertTrue(MixedLayout.cuts(IntArray(120),60).isEmpty())}
}
