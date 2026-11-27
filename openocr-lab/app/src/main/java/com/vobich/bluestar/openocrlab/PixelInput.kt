package com.vobich.bluestar.openocrlab
import kotlin.math.floor
import kotlin.math.sqrt
object PixelInput {
 val caps=listOf(2_000_000L,4_000_000L,8_000_000L,10_000_000L,12_000_000L,16_000_000L,20_000_000L,0L)
 fun size(w:Int,h:Int,cap:Long):Pair<Int,Int>{
  require(w>0&&h>0&&cap>=0)
  val area=w.toLong()*h
  if(cap==0L||area<=cap)return w to h
  val ratio=sqrt(cap.toDouble()/area)
  var x=floor(w*ratio).toInt().coerceAtLeast(1)
  var y=floor(h*ratio).toInt().coerceAtLeast(1)
  if(x.toLong()*y>cap){if(x>=y)x=(cap/y).toInt().coerceAtLeast(1)else y=(cap/x).toInt().coerceAtLeast(1)}
  return x to y
 }
}
