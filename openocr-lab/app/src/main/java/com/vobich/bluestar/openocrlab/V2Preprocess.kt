package com.vobich.bluestar.openocrlab

import kotlin.math.*

/** Separable bicubic (a=-0.5), antialias on downsampling; integer rounding per pass
 * mirrors Pillow RGB resize. Input bytes are interleaved BGR; output NCHW RGB.
 * CI checks against pinned upstream recognition_batches with synthetic inputs. */
object V2Preprocess {
 data class Shape(val w:Int,val h:Int)
 fun shape(w:Int,h:Int):Shape {
  require(w>0&&h>0)
  val ratio=round(w.toDouble()/h).toInt().coerceIn(1,20)
  return when(ratio){1->Shape(64,64);2->Shape(96,48);3->Shape(112,40);4->Shape(128,32);else->Shape(32*ratio,32)}
 }
 private data class Coeff(val start:Int,val weight:IntArray)
 private const val BITS=22
 private const val SCALE=1 shl BITS
 private fun cubic(x0:Double):Double {
  val x=abs(x0)
  return when {x<1.0->((1.5*x-2.5)*x)*x+1.0;x<2.0->((-0.5*x+2.5)*x-4.0)*x+2.0;else->0.0}
 }
 private fun coefficients(input:Int,output:Int):List<Coeff>{
  val scale=input.toDouble()/output
  val filter=max(1.0,scale);val support=2.0*filter
  return List(output){i->
   val center=(i+0.5)*scale
   val lo=max(0,(center-support+0.5).toInt())
   val hi=min(input,(center+support+0.5).toInt())
   val raw=DoubleArray(hi-lo){j->cubic((lo+j-center+0.5)/filter)}
   val sum=raw.sum()
   Coeff(lo,IntArray(raw.size){j->val v=raw[j]/sum*SCALE;if(v<0)(v-0.5).toInt() else (v+0.5).toInt()})
  }
 }
 private fun clipped(v:Long)=((v shr BITS).toInt().coerceIn(0,255)).toByte()
 fun resizeBgr(data:ByteArray,w:Int,h:Int,shape:Shape):ByteArray{
  require(data.size==w*h*3)
  val tw=shape.w;val th=shape.h
  val horiz=if(w==tw)data else {
   val coeff=coefficients(w,tw);val out=ByteArray(tw*h*3)
   for(y in 0 until h)for(x in 0 until tw)for(c in 0..2){
    val a=coeff[x];var v=(SCALE/2).toLong()
    for(k in a.weight.indices)v+=(data[(y*w+a.start+k)*3+c].toInt() and 255).toLong()*a.weight[k]
    out[(y*tw+x)*3+c]=clipped(v)
   };out
  }
  if(h==th)return horiz
  val coeff=coefficients(h,th);val out=ByteArray(tw*th*3)
  for(y in 0 until th)for(x in 0 until tw)for(c in 0..2){
   val a=coeff[y];var v=(SCALE/2).toLong()
   for(k in a.weight.indices)v+=(horiz[((a.start+k)*tw+x)*3+c].toInt() and 255).toLong()*a.weight[k]
   out[(y*tw+x)*3+c]=clipped(v)
  }
  return out
 }
 fun tensor(data:ByteArray,w:Int,h:Int,shape:Shape):FloatArray{
  val resized=resizeBgr(data,w,h,shape);val plane=shape.w*shape.h
  return FloatArray(3*plane){i->((resized[(i%plane)*3+2-i/plane].toInt() and 255)/255f-0.5f)/0.5f}
 }
}
