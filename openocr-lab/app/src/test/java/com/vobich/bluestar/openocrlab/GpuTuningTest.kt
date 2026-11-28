package com.vobich.bluestar.openocrlab

import org.junit.Assert.*
import org.junit.Test

class GpuTuningTest {
 @Test fun wireValuesAndCacheNamesAreStableAndDistinct(){
  assertEquals(GpuTuning.FAST,GpuTuning.fromWire(0))
  assertEquals(GpuTuning.NONE,GpuTuning.fromWire(1))
  assertEquals("rep-576805263c70-mnn350-opencl-high-fast-meanfix-v0111-fast.cache",GpuTuning.FAST.cacheName)
  assertNotEquals(GpuTuning.FAST.cacheName,GpuTuning.NONE.cacheName)
  assertEquals(GpuTuning.NONE_NORMAL,GpuTuning.fromWire(2))
  assertEquals(GpuTuning.NONE_LOW,GpuTuning.fromWire(3))
  assertEquals(4,GpuTuning.values().map{it.cacheName}.toSet().size)
  assertTrue(GpuTuning.values().all{it.cacheName.contains("meanfix-v0111")})
 }
 @Test(expected=NoSuchElementException::class) fun invalidWireRejected(){
  GpuTuning.fromWire(4)
 }
}
