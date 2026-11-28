package com.vobich.bluestar.openocrlab

import org.junit.Assert.*
import org.junit.Test

class GpuRecoveryPolicyTest {
 @Test fun deadBeforeFirstSendCanRecoverEvenWithEarlierPages() {
  assertTrue(GpuRecoveryPolicy.allow(CoopMode.MIXED_EXACT,true,0,0))
  assertTrue(GpuRecoveryPolicy.allow(CoopMode.MIXED_EXACT,true,42,42))
 }
 @Test fun inFlightOrAfterSuccessfulBatchDoesNotRetry() {
  assertFalse(GpuRecoveryPolicy.allow(CoopMode.MIXED_EXACT,false,0,0))
  assertFalse(GpuRecoveryPolicy.allow(CoopMode.MIXED_EXACT,true,3,4))
 }
 @Test fun diagnosticModesNeverRetry() {
  for(mode in CoopMode.values())if(mode!=CoopMode.MIXED_EXACT)
   assertFalse(GpuRecoveryPolicy.allow(mode,true,0,0))
 }
}
