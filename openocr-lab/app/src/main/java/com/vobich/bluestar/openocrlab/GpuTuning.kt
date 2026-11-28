package com.vobich.bluestar.openocrlab

// Different kernel tuning policies must never share the same MNN disk cache.
// New program digest and disjoint cache names isolate the changed mean kernel.
internal enum class GpuTuning(val wire:Int,val cacheName:String) {
 FAST(0,"rep-576805263c70-mnn350-opencl-high-fast-meanfix-v0111-fast.cache"),
 NONE(1,"rep-576805263c70-mnn350-opencl-high-none-meanfix-v0111-high.cache"),
 NONE_NORMAL(2,"rep-576805263c70-mnn350-opencl-normal-none-meanfix-v0111.cache"),
 NONE_LOW(3,"rep-576805263c70-mnn350-opencl-low-none-meanfix-v0111.cache");
 companion object {
  fun fromWire(wire:Int):GpuTuning=values().single{it.wire==wire}
 }
}
