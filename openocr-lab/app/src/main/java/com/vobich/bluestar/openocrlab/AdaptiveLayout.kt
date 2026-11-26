package com.vobich.bluestar.openocrlab
import kotlin.math.*

/** Geometry-only crop selection. Keep the detector's original quadrilateral for
 * independent words/lines, but keep the row planner's union for spaced glyphs. */
object CropPolicy {
 data class R(val l:Float,val t:Float,val r:Float,val b:Float){
  val w get()=r-l;val h get()=b-t
  val cx get()=(l+r)/2;val cy get()=(t+b)/2
 }
 fun members(target:R,raw:List<R>):List<Int> = raw.indices.filter{i->
  val q=raw[i]
  val area=max(0f,min(q.r,target.r)-max(q.l,target.l))*max(0f,min(q.b,target.b)-max(q.t,target.t))
  q.w>0&&q.h>0&&q.cx in target.l..target.r&&q.cy in target.t..target.b&&area/(q.w*q.h)>=.8f
 }
 fun independentLongLines(a:List<R>):Boolean{
  if(a.size<2||a.any{it.w<it.h*2.5f})return false
  return a.sortedBy{it.l}.zipWithNext().all{(l,r)->r.l-l.r>=min(l.h,r.h)*.8f}
 }
 fun isSignedNumber(s:String):Boolean{
  val a=s.trimStart()
  return a.firstOrNull() in listOf('+','-','−','–','—')&&a.dropWhile{it in listOf('+','-','−','–','—',' ')}.firstOrNull()?.isDigit()==true
 }
}
object AdaptiveLayout {
 data class Plan(val blocks:List<MixedLayout.Block>,val raw:Set<TextLocator.Box>,val alternates:Map<TextLocator.Box,TextLocator.Box>)
 private fun rect(b:TextLocator.Box)=b.rect.let{CropPolicy.R(it.left,it.top,it.right,it.bottom)}
 fun plan(boxes:List<TextLocator.Box>,w:Int,h:Int):Plan{
  val vertical=boxes.filter{val r=it.rect;r.height()>r.width()*2.2f}
  val horizontal=boxes.filter{it !in vertical}
  val rects=horizontal.map{rect(it)}
  val raw=mutableSetOf<TextLocator.Box>()
  val alternatives=mutableMapOf<TextLocator.Box,TextLocator.Box>()
  val used=mutableSetOf<Int>()
  val hr=RowPlanner.plan(horizontal,w,h,1.6f,true).rows.map{row->
   val chosen=row.flatMap{q->
    val ids=CropPolicy.members(rect(q),rects).filter{it !in used}
    if(ids.size==1){
     val original=horizontal[ids.single()]
     used.addAll(ids);raw.add(original);alternatives[original]=q;listOf(original)
    }else if(CropPolicy.independentLongLines(ids.map{rects[it]})){
     used.addAll(ids);ids.map{horizontal[it]}.sortedBy{it.rect.left}.also{raw.addAll(it)}
    }else{used.addAll(ids);listOf(q)}
   }
   MixedLayout.Block(chosen,false)
  }
  val vr=vertical.sortedByDescending{it.rect.centerX()}.map{MixedLayout.Block(listOf(it),true)}
  if(vr.isEmpty())return Plan(hr,raw,alternatives)
  val top=vertical.minOf{it.rect.top}
  val before=hr.filter{it.boxes.minOf{q->q.rect.centerY()}<top}
  return Plan(before+vr+hr.filter{it !in before},raw,alternatives)
 }
}
object GlyphProjection {
 data class Result(val background:Int,val profile:IntArray)
 fun analyze(gray:IntArray,w:Int,h:Int):Result{
  require(w>=4&&h>=1&&gray.size==w*h)
  val edge=max(1,w/10)
  val edges=IntArray(h*edge*2)
  var k=0
  for(y in 0 until h)for(x in 0 until edge){
   edges[k++]=gray[y*w+x];edges[k++]=gray[y*w+w-1-x]
  }
  edges.sort()
  val bg=edges[edges.size/2]
  val noise=edges.map{abs(it-bg)}.sorted().let{it[it.size/2]}
  val threshold=(noise*3+15).coerceIn(22,55)
  return Result(bg,IntArray(h){y->(edge until w-edge).count{x->abs(gray[y*w+x]-bg)>=threshold}})
 }
}
