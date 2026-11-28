package com.vobich.bluestar.openocrlab

import java.nio.ByteBuffer
import kotlin.math.abs

/** Pure frame-state policy. A movement episode invalidates exactly once.
 * Adjacent quiet frames end the episode; no fixed OCR interval or frame queue.
 * Signatures never replace the full-resolution OCR input.
 */
internal class SceneGate {
 companion object {
  const val W=2560; const val H=1440
  const val COLS=64; const val ROWS=36
  const val SIZE=COLS*ROWS
  // Cross sample per tile, deliberately cheap; retains local structure.
  fun signature(b:ByteBuffer):IntArray {
   val origin=b.position()
   fun y(x:Int,y:Int)=b.get(origin+2*(y*W+x)).toInt() and 255
   return IntArray(SIZE){i->
    val x=(i%COLS)*40+20;val row=(i/COLS)*40+20
    (y(x,row)+y(x-7,row)+y(x+7,row)+y(x,row-7)+y(x,row+7))/5
   }
  }
 }
 enum class Action { HOLD, WAIT, INVALIDATE, START }
 data class Difference(val mean:Double,val large:Double,val local:Double,val exposure:Double){
  val changed:Boolean get()=mean>=8.0||large>=.12||(mean>=2.5&&large>=.045)||local>=24.0
  val quiet:Boolean get()=mean<=3.0&&large<=.025&&local<=12.0
 }
 data class Decision(val action:Action,val motion:Difference?,val anchor:Difference?,val quietCount:Int)
 private var anchor:IntArray?=null
 private var previous:IntArray?=null
 private var seeking=true
 private var quietCount=0
 private val tiles=DoubleArray(8*6)
 // Signed global luminance offset is removed before measuring structure.
 // Clipping, local shadows, blur and very fine text remain limitations.
 private fun compare(a:IntArray,b:IntArray):Difference {
  var signed=0
  for(i in b.indices)signed+=b[i]-a[i]
  val offset=signed.toDouble()/b.size
  var sum=0.0;var large=0
  tiles.fill(0.0)
  for(i in b.indices){
   val d=abs(b[i]-a[i]-offset);sum+=d;if(d>=20.0)large++
   val tile=(i/COLS/6)*8+(i%COLS/8)
   tiles[tile]+=d
  }
  return Difference(sum/b.size,large.toDouble()/b.size,(tiles.maxOrNull()?:0.0)/48.0,offset)
 }
 fun observe(s:IntArray,force:Boolean=false):Decision {
  require(s.size==SIZE)
  val step=previous?.let{compare(it,s)}
  val fromAnchor=anchor?.let{compare(it,s)}
  previous=s
  if(force){
   anchor=s;seeking=false;quietCount=0
   return Decision(Action.START,step,fromAnchor,0)
  }
  if(!seeking){
   if(fromAnchor?.changed!=true)return Decision(Action.HOLD,step,fromAnchor,0)
   seeking=true;quietCount=0
   return Decision(Action.INVALIDATE,step,fromAnchor,0)
  }
  // Two consecutive quiet comparisons at native 30fps (~67ms when still).
  // Movement resets the evidence, not the OCR worker.
  quietCount=if(step?.quiet==true)quietCount+1 else 0
  if(quietCount<2)return Decision(Action.WAIT,step,fromAnchor,quietCount)
  anchor=s;seeking=false
  return Decision(Action.START,step,fromAnchor,quietCount)
 }
}
