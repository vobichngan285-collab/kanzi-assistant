package com.vobich.bluestar.openocrlab
import kotlin.math.*
/** Pure geometry: use long-edge directions to deskew vertical fragments, cluster
 * by transverse position, and absorb single glyphs only inside supported tracks. */
object VerticalTracks {
 data class Fragment(val id:Int,val x:Float,val y:Float,val width:Float,val height:Float,val slope:Float,val score:Float)
 data class Track(val ids:List<Int>,val center:Float,val top:Float,val bottom:Float,val width:Float,val slope:Float)
 private fun med(a:List<Float>)=a.sorted()[a.size/2]
 fun build(f:List<Fragment>):List<Track>{
  val seeds=f.filter{it.height>=it.width*2.2f&&it.width>=3f&&abs(it.slope)<=.35f}
  if(seeds.isEmpty())return emptyList()
  val slope=med(seeds.map{it.slope})
  fun x(a:Fragment)=a.x-slope*a.y
  val groups=mutableListOf<MutableList<Fragment>>()
  for(a in seeds.sortedByDescending{it.height}){
   val g=groups.filter{g->
    val w=med(g.map{it.width});abs(x(a)-med(g.map{ x(it)}))<=min(w,a.width)*.68f&&
     max(w,a.width)<=min(w,a.width)*2f
   }.minByOrNull{abs(x(a)-med(it.map{q->x(q)}))}
   if(g==null)groups.add(mutableListOf(a))else g.add(a)
  }
  val tracks=mutableListOf<Track>()
  for(g in groups){
   val w=med(g.map{it.width});val cx=med(g.map{x(it)})
   val sorted=g.sortedBy{it.y-it.height/2}
   val chunks=mutableListOf<MutableList<Fragment>>()
   for(a in sorted){
    val last=chunks.lastOrNull()
    if(last!=null&&a.y-a.height/2-last.maxOf{it.y+it.height/2}<=w*4.0f)last.add(a)
    else chunks.add(mutableListOf(a))
   }
   for(chunk in chunks){
    val top=chunk.minOf{it.y-it.height/2};val bottom=chunk.maxOf{it.y+it.height/2}
    tracks.add(Track(chunk.map{it.id},cx,top,bottom,w,slope))
   }
  }
  // Attach square glyphs and short fragments, not full horizontal words.
  val used=tracks.flatMap{it.ids}.toMutableSet()
  for(a in f.filter{it.id !in used&&it.height>=it.width*.65f&&it.width<=it.height*1.5f}){
   val idx=tracks.indices.filter{i->
    val t=tracks[i];abs(x(a)-t.center)<=t.width*.65f&&a.width<=t.width*1.65f&&a.width>=t.width*.4f&&
    a.y>=t.top-t.width*1.5f&&a.y<=t.bottom+t.width*1.5f
   }.minByOrNull{abs(x(a)-tracks[it].center)}
   if(idx!=null){val t=tracks[idx];tracks[idx]=t.copy(ids=t.ids+a.id,top=min(t.top,a.y-a.height/2),bottom=max(t.bottom,a.y+a.height/2));used.add(a.id)}
  }
  return tracks.sortedWith(compareByDescending<Track>{it.center}.thenBy{it.top})
 }
 /** Refine pitch by looking for low-ink boundaries near expected cell edges.
  * Keep projection baseline for short or irregular strips. */
 fun gridCuts(profile:IntArray,width:Int,baseline:List<IntRange>):List<IntRange>{
  if(baseline.size<5)return baseline
  val centers=baseline.map{(it.first+it.last)/2f}
  val distances=centers.zipWithNext().map{(a,b)->b-a}.filter{it in width*.65f..width*1.8f}
  if(distances.size<4)return baseline
  val pitch=med(distances);if(pitch<1)return baseline
  val first=baseline.first().first;val last=baseline.last().last
  val n=((last-first+1)/pitch).roundToInt().coerceAtLeast(1)
  if(n<5||abs(n-baseline.size)>max(3,baseline.size/3))return baseline
  val bounds=mutableListOf(first)
  for(i in 1 until n){
   val target=first+(last-first+1)*i.toFloat()/n
   val lo=max(bounds.last()+1,(target-pitch*.22f).roundToInt())
   val hi=min(last,(target+pitch*.22f).roundToInt())
   if(lo>hi)return baseline
   bounds.add((lo..hi).minByOrNull{j->profile[j]+abs(j-target)*.2f}!!)
  }
  bounds.add(last+1)
  val cells=bounds.zipWithNext().map{(a,b)->a until b}
  return cells.filter{a->a.any{profile[it]>=max(1,width/18)}}
 }
}
