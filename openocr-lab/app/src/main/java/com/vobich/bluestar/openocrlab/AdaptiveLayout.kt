package com.vobich.bluestar.openocrlab

import kotlin.math.*

/** Conservative routing using only detected geometry. Never classifies semantic scene names. */
object AdaptiveLayout {
 data class R(val l:Float,val t:Float,val r:Float,val b:Float){
  val w get()=r-l;val h get()=b-t;val cx get()=(l+r)/2;val cy get()=(t+b)/2
 }
 data class Decision(val structured:Boolean,val reason:String,val support:List<Int> = emptyList())
 private fun median(v:List<Float>)=v.sorted()[v.size/2]
 fun decide(focusReason:String,rows:List<List<R>>,outside:List<R>):Decision {
  if(focusReason!="dominant_region")return Decision(false,"no_dominant_region")
  val cells=rows.flatten().filter{it.w>1&&it.h>1}
  if(cells.size<8)return Decision(false,"few_cells")
  val h=median(cells.map{min(it.w,it.h)})
  if(h<2)return Decision(false,"invalid_scale")
  // A substantial independent heading outside the focus could be a shop name.
  if(outside.any{it.h>h*2.5f&&it.w>it.h*1.8f})return Decision(false,"large_outside_heading")
  data class PairRow(val i:Int,val l:R,val r:R,val y:Float)
  val pairs=rows.mapIndexedNotNull{i,row->
   if(row.size!=2)return@mapIndexedNotNull null
   val a=row[0];val b=row[1]
   if(a.w<a.h*1.15f||b.w<b.h*1.1f||b.l-a.r<h*1.1f||
      a.h !in h*.55f..h*1.8f||b.h !in h*.5f..h*1.6f||
      abs(a.cy-b.cy)>h*.7f)return@mapIndexedNotNull null
   PairRow(i,a,b,(a.cy+b.cy)/2)
  }
  var best=emptyList<PairRow>()
  for(seed in pairs){
   val stable=pairs.filter{abs(it.r.cx-seed.r.cx)<=h*1.1f&&abs(it.l.l-seed.l.l)<=h*2.2f}
   var run=mutableListOf<PairRow>()
   for(p in stable){
    val prev=run.lastOrNull()
    if(prev!=null&&(p.i!=prev.i+1||p.y-prev.y !in h*.55f..h*2.1f)){
     if(run.size>best.size)best=run.toList()
     run=mutableListOf()
    }
    run.add(p)
   }
   if(run.size>best.size)best=run.toList()
  }
  if(best.size<4)return Decision(false,"weak_repeated_columns",best.map{it.i})
  val pitches=best.zipWithNext{a,b->b.y-a.y}
  val pitch=median(pitches)
  if(pitches.any{abs(it-pitch)>pitch*.40f})return Decision(false,"irregular_pitch",best.map{it.i})
  return Decision(true,"repeated_aligned_rows",best.map{it.i})
 }
 private fun overlap(a:R,b:R)=max(0f,min(a.r,b.r)-max(a.l,b.l))*max(0f,min(a.b,b.b)-max(a.t,b.t))
 /** Match a single complete original cell; never substitute a union of fragments. */
 fun originalCell(cell:R,raw:List<R>):Int?{
  val candidates=raw.indices.filter{i->val r=raw[i]
   r.w>1&&r.h>1&&r.cx in cell.l..cell.r&&r.cy in cell.t..cell.b&&
   overlap(cell,r)/(r.w*r.h)>=.80f
  }
  if(candidates.size!=1)return null
  val i=candidates.single();val r=raw[i]
  if(r.w<r.h*1.15f||r.w<cell.w*.65f||r.h<cell.h*.55f)return null
  return i
 }
}
