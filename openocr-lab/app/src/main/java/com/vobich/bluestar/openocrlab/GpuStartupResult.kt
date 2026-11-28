package com.vobich.bluestar.openocrlab

// Successful warmup makes the worker eligible for real tasks. It can finish
// after OCR has completed; no Bitmap or Mat is retained.
internal data class GpuStartupResult(val client:GpuClient?,val log:String)
