package com.vobich.bluestar.openocrlab
import kotlin.math.*

/** Conservative visual-only recovery. No dictionary or language-based correction. */
object FineRead {
 data class Result(val answer:Reader.Answer,val tried:Int,val accepted:Boolean,val suspicious:Boolean,val alternate:String)
 fun retryNeeded(a:Reader.Answer):Boolean = a.text.isBlank()||a.confidence<.88f||a.minConfidence<.65f||a.text.trim().contains(Regex("\\S\\s+\\S"))
 fun accept(old:Reader.Answer,new:Reader.Answer):Boolean {
  val a=old.text.filterNot{it.isWhitespace()};val b=new.text.filterNot{it.isWhitespace()}
  if(b.isEmpty())return false
  // Never silently change any digit from a nonempty baseline.
  if(a.any{it.isDigit()}&&a.filter{it.isDigit()}!=b.filter{it.isDigit()})return false
  if(a.isEmpty())return new.confidence>=.85f&&new.minConfidence>=.60f
  var k=0;for(ch in b)if(k<a.length&&ch==a[k])k++
  val adds=b.length in (a.length+1)..(a.length+2)&&k==a.length
  return if(adds)new.confidence>=.88f&&new.minConfidence>=.65f&&new.confidence>=old.confidence-.04f
  else b==a&&new.confidence>=old.confidence+.03f
 }
 fun expand(box:TextLocator.Box,fx:Float,fy:Float,w:Int,h:Int):TextLocator.Box {
  val p=box.points;val cx=(p[0]+p[2]+p[4]+p[6])/4;val cy=(p[1]+p[3]+p[5]+p[7])/4
  val ux=(p[2]-p[0]+p[4]-p[6])/2;val uy=(p[3]-p[1]+p[5]-p[7])/2
  val vx=(p[6]-p[0]+p[4]-p[2])/2;val vy=(p[7]-p[1]+p[5]-p[3])/2
  val q=FloatArray(8)
  for(i in 0..3){val x=if(i==0||i==3)-.5f else .5f;val y=if(i<2)-.5f else .5f
   q[i*2]=(cx+x*ux*fx+y*vx*fy).coerceIn(0f,w.toFloat());q[i*2+1]=(cy+x*uy*fx+y*vy*fy).coerceIn(0f,h.toFloat())}
  return TextLocator.Box(q,box.score)
 }
 fun suspicious(a:Reader.Answer,score:Float):Boolean{
  val n=a.text.count{!it.isWhitespace()}
  return n<=2&&score<.55f&&a.confidence<.80f
 }
}
