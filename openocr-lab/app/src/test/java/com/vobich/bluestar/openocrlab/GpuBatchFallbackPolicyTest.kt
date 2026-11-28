package com.vobich.bluestar.openocrlab

import org.junit.Assert.*
import org.junit.Test

class GpuBatchFallbackPolicyTest {
 @Test fun onlyLowPrecisionMixedExactRecovers(){
  for(mode in CoopMode.values())for(tuning in GpuTuning.values()){
   val expected=mode==CoopMode.MIXED_EXACT&&(tuning==GpuTuning.NONE_NORMAL||tuning==GpuTuning.NONE_LOW)
   assertEquals(expected,GpuBatchFallbackPolicy.enabled(mode,tuning))
   assertEquals(expected,GpuBatchFallbackPolicy.allow(mode,tuning,IllegalStateException("invalid int32 character index")))
  }
  assertFalse(GpuBatchFallbackPolicy.enabled(CoopMode.MIXED_EXACT,null))
 }
 @Test fun observedNumericErrorsQualify(){
  assertTrue(GpuBatchFallbackPolicy.allow(CoopMode.MIXED_EXACT,GpuTuning.NONE_LOW,IllegalArgumentException("score=-Infinity")))
  assertTrue(GpuBatchFallbackPolicy.allow(CoopMode.MIXED_EXACT,GpuTuning.NONE_NORMAL,IllegalStateException("invalid int32 character index")))
 }
 @Test fun fatalErrorsAndCancellationPropagate(){
  for(error in listOf(OutOfMemoryError(),AssertionError(),InterruptedException(),java.util.concurrent.CancellationException())){
   assertFalse(GpuBatchFallbackPolicy.allow(CoopMode.MIXED_EXACT,GpuTuning.NONE_LOW,error))
  }
 }
}
