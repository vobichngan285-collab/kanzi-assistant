package com.vobich.bluestar.openocrlab

/** Experimental low-precision mixed-exact pages only. Strict diagnostics
 * remain strict. VM errors, interruption and cancellation are not suppressed. */
internal object GpuBatchFallbackPolicy {
 fun enabled(mode:CoopMode,tuning:GpuTuning?):Boolean =
  mode==CoopMode.MIXED_EXACT && (tuning==GpuTuning.NONE_NORMAL||tuning==GpuTuning.NONE_LOW)
 fun allow(mode:CoopMode,tuning:GpuTuning?,error:Throwable):Boolean =
  enabled(mode,tuning) && error is Exception &&
   error !is InterruptedException && error !is java.util.concurrent.CancellationException
}
