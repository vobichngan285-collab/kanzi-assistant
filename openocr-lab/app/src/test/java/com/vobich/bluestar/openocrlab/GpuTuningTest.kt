package com.vobich.bluestar.openocrlab

import org.junit.Assert.*
import org.junit.Test

class GpuTuningTest {
 @Test fun wireValuesAndCacheNamesAreStableAndDistinct(){
  assertEquals(GpuTuning.FAST,GpuTuning.fromWire(0))
  assertEquals(GpuTuning.NONE,GpuTuning.fromWire(1))
  assertEquals("rep-576805263c70-mnn350-opencl-high-fast-v012.cache",GpuTuning.FAST.cacheName)
  assertNotEquals(GpuTuning.FAST.cacheName,GpuTuning.NONE.cacheName)
 }
 @Test(expected=NoSuchElementException::class) fun invalidWireRejected(){
  GpuTuning.fromWire(2)
 }
}
