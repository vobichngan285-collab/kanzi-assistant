package com.vobich.bluestar.openocrlab

// Different kernel tuning policies must never share the same MNN disk cache.
// FAST retains the v0.1.5 name/semantics; NONE is an independent experiment.
internal enum class GpuTuning(val wire:Int,val cacheName:String) {
 FAST(0,"rep-576805263c70-mnn350-opencl-high-fast-v012.cache"),
 NONE(1,"rep-576805263c70-mnn350-opencl-high-none-v016.cache"),
 NONE_NORMAL(2,"rep-576805263c70-mnn350-opencl-normal-none-v018.cache"),
 NONE_LOW(3,"rep-576805263c70-mnn350-opencl-low-none-v018.cache");
 companion object {
  fun fromWire(wire:Int):GpuTuning=values().single{it.wire==wire}
 }
}
