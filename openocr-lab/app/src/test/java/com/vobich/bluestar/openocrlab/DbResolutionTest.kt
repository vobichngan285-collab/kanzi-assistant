package com.vobich.bluestar.openocrlab
import org.junit.Assert.*
import org.junit.Test
class DbResolutionTest {
 @Test fun allUiChoicesAcceptedByDetectorGate(){
  assertEquals(listOf(960,1280,1600,1920,2048,2560,3200),DbResolution.sizes)
  assertEquals(1600,DbResolution.sizes[DbResolution.defaultIndex])
  DbResolution.sizes.forEach{DbResolution.validate(it);assertTrue(DbResolution.label(it).contains(it.toString()))}
 }
 @Test(expected=IllegalArgumentException::class) fun unsupportedSizeRejected(){DbResolution.validate(999)}
}
