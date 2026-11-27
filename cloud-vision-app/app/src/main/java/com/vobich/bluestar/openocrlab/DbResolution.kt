package com.vobich.bluestar.openocrlab

/** Single source of truth for the experimental detector long-side choices. */
object DbResolution {
 val sizes=listOf(960,1280,1600,1920,2048,2560,3200)
 const val defaultIndex=2
 fun label(size:Int)=when(size){
  960->"DB 长边 960（官方基线）"
  3200->"DB 长边 3200（高内存实验）"
  else->"DB 长边 $size"+if(size>1600)"（实验）"else ""
 }
 fun validate(size:Int){require(size in sizes){"不支持的检测长边：$size；允许：$sizes"}}
}
