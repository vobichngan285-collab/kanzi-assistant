package com.vobich.bluestar.openocrlab
import kotlin.math.*

/** Geometry-only main reading region. No text, vocabulary, or image-specific coordinates. */
object FocusPlanner {
 data class R(val l:Float,val t:Float,val r:Float,val b:Float,val score:Float=0f){
  val w get()=r-l;val h get()=b-t;val cx get()=(l+r)/2;val cy get()=(t+b)/2
  val size get()=min(w,h)
  fun union(o:R)=R(min(l,o.l),min(t,o.t),max(r,o.r),max(b,o.b))
  fun contains(o:R)=o.cx in l..r&&o.cy in t..b
 }
 data class Plan(val roi:R?,val reason:String,val groups:Int,val best:Double,val rival:Double)
 private fun overlap(a:R,b:R)=max(0f,min(a.r,b.r)-max(a.l,b.l))*max(0f,min(a.b,b.b)-max(a.t,b.t))
 fun plan(input:List<R>,w:Int,h:Int):Plan{
  fun full(reason:String,groups:Int=0,best:Double=0.0,rival:Double=0.0)=Plan(null,reason,groups,best,rival)
  val valid=input.filter{it.w>1&&it.h>1&&it.score.isFinite()}
  if(valid.any{it.score>=.45f&&it.h>it.w*2.5f&&it.h>=min(w,h)*.025f})return full("possible_vertical")
  // Do not let duplicate/subword detections inflate cluster importance.
  val anchors=mutableListOf<R>()
  for(a in valid.filter{it.score>=.40f}.sortedByDescending{it.w*it.h}){
   if(anchors.none{b->overlap(a,b)/(a.w*a.h)>=.85f&&abs(a.cy-b.cy)<=max(a.h,b.h)*.4f})anchors.add(a)
  }
  if(anchors.size<4)return full("few_anchors")
  val parent=IntArray(anchors.size){it}
  fun root(i:Int):Int{var x=i;while(parent[x]!=x){parent[x]=parent[parent[x]];x=parent[x]};return x}
  fun join(i:Int,j:Int){val a=root(i);val b=root(j);if(a!=b)parent[b]=a}
  for(i in anchors.indices)for(j in 0 until i){
   val a=anchors[i];val b=anchors[j];val lo=min(a.size,b.size);val hi=max(a.size,b.size)
   if(hi>lo*2.8f)continue
   val gx=max(0f,max(a.l,b.l)-min(a.r,b.r));val gy=max(0f,max(a.t,b.t)-min(a.b,b.b))
   // Wide table gutters connect only approximately aligned text; nearby rows require x overlap/proximity.
   if((abs(a.cy-b.cy)<=hi*.75f&&gx<=hi*8f)||(gy<=hi*3f&&gx<=hi*1.6f))join(i,j)
  }
  data class Group(val boxes:List<R>,val area:R,val score:Double,val rows:Int,val pitch:Float)
  val groups=anchors.indices.groupBy{root(it)}.values.map{ids->
   val boxes=ids.map{anchors[it]};val pitch=boxes.map{it.size}.sorted().let{it[it.size/2]}
   val envelope=boxes.reduce{a,b->a.union(b)}
   val ys=mutableListOf<Float>()
   for(y in boxes.map{it.cy}.sorted())if(ys.isEmpty()||y-ys.last()>pitch*.70f)ys.add(y)
   // Weight by glyph scale, but cap individual contribution; no center-position assumption.
   val score=boxes.sumOf{it.score.toDouble()*sqrt(it.size.toDouble())}
   Group(boxes,envelope,score,ys.size,pitch)
  }.sortedByDescending{it.score}
  val best=groups.first();val rival=groups.drop(1).firstOrNull()
  if(best.boxes.size<4||best.rows<3)return full("weak_structure",groups.size,best.score,rival?.score?:0.0)
  if(rival!=null&&rival.score>=best.score*.60)return full("multiple_regions",groups.size,best.score,rival.score)
  val pad=max(8f,best.pitch*1.4f)
  val area=R((best.area.l-pad).coerceIn(0f,w.toFloat()),(best.area.t-pad).coerceIn(0f,h.toFloat()),(best.area.r+pad).coerceIn(0f,w.toFloat()),(best.area.b+pad).coerceIn(0f,h.toFloat()))
  if(area.w*area.h>=w.toDouble()*h*.85||valid.all{area.contains(it)})return full("whole_image",groups.size,best.score,rival?.score?:0.0)
  // Significant text outside the focus area must never be silently discarded. A second coherent
  // group of similar-sized glyphs, even if small, makes the decision ambiguous.
  val outside=groups.drop(1).filter{g->g.boxes.any{!area.contains(it)}}
  if(outside.any{it.boxes.size>=3&&it.rows>=2&&it.pitch>=best.pitch*.65f&&it.score>=best.score*.25})return full("important_outside",groups.size,best.score,rival?.score?:0.0)
  return Plan(area,"dominant_region",groups.size,best.score,rival?.score?:0.0)
 }
}
