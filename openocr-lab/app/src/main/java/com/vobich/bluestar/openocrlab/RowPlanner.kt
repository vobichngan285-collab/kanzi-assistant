package com.vobich.bluestar.openocrlab
import kotlin.math.*

/** Geometry only: project boxes into a dominant horizontal coordinate system. */
object RowPlanner {
 data class R(val l:Float,val t:Float,val r:Float,val b:Float,val score:Float){
  val w get()=r-l;val h get()=b-t;val cy get()=(t+b)/2
  fun union(o:R)=R(min(l,o.l),min(t,o.t),max(r,o.r),max(b,o.b),max(score,o.score))
 }
 data class Plan(val rows:List<List<TextLocator.Box>>,val removed:Int)
 private fun median(a:List<Float>):Float{val s=a.sorted();return s[s.size/2]}
 fun plan(boxes:List<TextLocator.Box>,width:Int,height:Int,mergeGap:Float=2.2f):Plan{
  if(boxes.isEmpty())return Plan(emptyList(),0)
  val angles=boxes.mapNotNull{b->
   val p=b.points;val a=hypot(p[2]-p[0],p[3]-p[1]);val c=hypot(p[4]-p[2],p[5]-p[3])
   if(max(a,c)<min(a,c)*1.8f)null else {
    var x=if(a>=c)atan2(p[3]-p[1],p[2]-p[0]) else atan2(p[5]-p[3],p[4]-p[2])
    while(x>PI/2)x-=PI.toFloat();while(x< -PI/2)x+=PI.toFloat()
    x.takeIf{abs(it)<.35f}
   }
  }
  val angle=if(angles.isEmpty())0f else median(angles);val c=cos(angle);val s=sin(angle)
  val rects=boxes.map{b->val xs=(0..3).map{i->b.points[2*i]*c+b.points[2*i+1]*s};val ys=(0..3).map{i->-b.points[2*i]*s+b.points[2*i+1]*c};R(xs.min(),ys.min(),xs.max(),ys.max(),b.score)}.filter{it.w>1&&it.h>1}
  return geometry(rects,mergeGap).let{(rows,removed)->
   Plan(rows.map{row->row.map{r->
    val pts=floatArrayOf(r.l,r.t,r.r,r.t,r.r,r.b,r.l,r.b)
    val p=FloatArray(8);for(i in 0..3){val x=pts[i*2];val y=pts[i*2+1];p[i*2]=(x*c-y*s).coerceIn(0f,width.toFloat());p[i*2+1]=(x*s+y*c).coerceIn(0f,height.toFloat())}
    TextLocator.Box(p,r.score)
   }},removed)
  }
 }
 fun geometry(input:List<R>,mergeGap:Float=2.2f):Pair<List<List<R>>,Int>{
  if(input.isEmpty())return emptyList<List<R>>() to 0
  val unique=mutableListOf<R>()
  for(r in input.sortedByDescending{it.w*it.h}){
   if(unique.any{q->
    val inter=max(0f,min(q.r,r.r)-max(q.l,r.l))*max(0f,min(q.b,r.b)-max(q.t,r.t))
    inter/(r.w*r.h)>=.88f && abs(q.cy-r.cy)<=max(q.h,r.h)*.40f
   })continue
   unique.add(r)
  }
  val rows=mutableListOf<MutableList<R>>()
  for(r in unique.sortedBy{it.cy}){
   val row=rows.filter{rr->
    val h=median(rr.map{it.h});val y=median(rr.map{it.cy})
    abs(r.cy-y)<=max(3f,min(h,r.h)*.58f)
   }.minByOrNull{abs(r.cy-median(it.map{q->q.cy}))}
   if(row==null)rows.add(mutableListOf(r))else row.add(r)
  }
  val sorted=rows.sortedBy{median(it.map{r->r.cy})}
  val out=sorted.mapIndexed{i,rr->
   val h=median(rr.map{it.h});val groups=mutableListOf<R>()
   for(r in rr.sortedBy{it.l}){
    val prev=groups.lastOrNull()
    // Join spaced glyphs and touching word boxes, but keep large table column gaps.
    if(prev!=null&&r.l-prev.r<=h*mergeGap)groups[groups.lastIndex]=prev.union(r) else groups.add(r)
   }
   val topLimit=if(i>0)(median(sorted[i-1].map{it.cy})+median(rr.map{it.cy}))/2 else -Float.MAX_VALUE
   val bottomLimit=if(i+1<sorted.size)(median(sorted[i+1].map{it.cy})+median(rr.map{it.cy}))/2 else Float.MAX_VALUE
   groups.mapIndexed{j,r->
    val pad=max(2f,h*.20f)
    val left=if(j>0)max(r.l-pad,(groups[j-1].r+r.l)/2)else r.l-pad
    val right=if(j+1<groups.size)min(r.r+pad,(r.r+groups[j+1].l)/2)else r.r+pad
    // Padding may be clipped to midline but never removes existing detected pixels.
    R(left,min(r.t,max(r.t-pad,topLimit)),right,max(r.b,min(r.b+pad,bottomLimit)),r.score)
   }
  }
  return out to (input.size-unique.size)
 }
}
