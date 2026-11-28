package com.vobich.bluestar.openocrlab

// One attempt is enforced structurally by MainActivity's single catch.
// Only an identified transport loss before the first GPU send of the page
// qualifies; never retry an engine/decoder failure or uncertain in-flight call.
internal object GpuRecoveryPolicy {
 fun allow(mode:CoopMode,beforeSend:Boolean,before:Long,after:Long):Boolean =
  mode==CoopMode.MIXED_EXACT && beforeSend && before==after
}
