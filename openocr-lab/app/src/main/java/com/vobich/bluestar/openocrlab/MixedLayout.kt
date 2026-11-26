package com.vobich.bluestar.openocrlab
import android.graphics.*
import kotlin.math.*

object MixedLayout {
 data class Scan(val boxes:List<TextLocator.Box>,val passes:Int)
 data class Block(val boxes:List<TextLocator.Box>,val vertical:Boolean)
 fun overlap(a:RectF,b:RectF):Float{
  val n=max(0f,min(a.right,b.right)-max(a.left,b.left))*max(0f,min(a.bottom,b.bottom)-max(a.top,b.top))
  return n/min(a.width()*a.height(),b.width()*b.height()).coerceAtLeast(1f)
 }
 fun dedup(boxes:List<TextLocator.Box>):List<TextLocator.Box>{
  val out=mutableListOf<TextLocator.Box>()
  for(b in boxes.sortedByDescending{it.score}){
   val a=b.rect
   if(a.width()<2||a.height()<2)continue
   if(out.any{q->val c=q.rect;overlap(a,c)>.85f && min(a.width()*a.height(),c.width()*c.height())/max(a.width()*a.height(),c.width()*c.height())>.55f})continue
   out.add(b)
  }
  return out
 }
 fun scan(bm:Bitmap,locator:TextLocator,initial:List<TextLocator.Box>,tiles:Boolean):Scan{
  if(!tiles)return Scan(dedup(initial),0)
  val out=initial.toMutableList();var passes=0
  // Four overlapping tiles cover the full image, rather than trusting one ROI.
  for(y in 0..1)for(x in 0..1){
   val l=if(x==0)0 else (bm.width*.40f).toInt()
   val t=if(y==0)0 else (bm.height*.40f).toInt()
   val r=if(x==0)(bm.width*.60f).toInt() else bm.width
   val b=if(y==0)(bm.height*.60f).toInt() else bm.height
   val tile=Bitmap.createBitmap(bm,l,t,r-l,b-t)
   try{for(q in locator.locate(tile).boxes){
    val pts=q.points.mapIndexed{i,v->v+if(i%2==0)l else t}.toFloatArray()
    out.add(TextLocator.Box(pts,q.score))
   };passes++}finally{if(tile!==bm)tile.recycle()}
  }
  return Scan(dedup(out),passes)
 }
 fun plan(boxes:List<TextLocator.Box>,w:Int,h:Int):List<Block>{
  val tall=boxes.filter{it.rect.height()>it.rect.width()*2.2f}
  // A vertical column needs more than a narrow isolated mark: at least 3 character heights.
  val seeds=tall.filter{it.rect.height()>it.rect.width()*3f}
  val vertical=mutableListOf<TextLocator.Box>()
  for(q in seeds.sortedByDescending{it.rect.height()}){
   if(vertical.any{overlap(it.rect,q.rect)>.75f})continue
   vertical.add(q)
  }
  // Attach fragments aligned with a detected vertical column; do not absorb horizontal lines.
  for(q in tall){if(vertical.any{overlap(it.rect,q.rect)>.7f})continue
   val a=q.rect
   val idx=vertical.indexOfFirst{v->val c=v.rect;abs(a.centerX()-c.centerX())<max(a.width(),c.width())*.65f&&max(0f,max(a.top,c.top)-min(a.bottom,c.bottom))<max(a.width(),c.width())*1.5f}
   if(idx>=0){val old=vertical[idx];val u=RectF(old.rect);u.union(a);vertical[idx]=axis(u,max(q.score,old.score))}else vertical.add(q)
  }
  val horizontal=boxes.filter{q->vertical.none{v->overlap(v.rect,q.rect)>.8f&&q.rect.width()<v.rect.width()*1.6f}}
  val hr=RowPlanner.plan(horizontal,w,h,1.6f,true).rows.map{Block(it,false)}
  // Vertical columns are kept together, right-to-left. Mixed block order is heuristic.
  val vr=vertical.sortedByDescending{it.rect.centerX()}.map{Block(listOf(it),true)}
  if(vr.isEmpty())return hr
  val top=vertical.minOf{it.rect.top};val bottom=vertical.maxOf{it.rect.bottom}
  return hr.filter{it.boxes.minOf{q->q.rect.centerY()}<top}+vr+hr.filter{it.boxes.minOf{q->q.rect.centerY()}>=top&&it.boxes.minOf{q->q.rect.centerY()}<=bottom}+hr.filter{it.boxes.minOf{q->q.rect.centerY()}>bottom}
 }
 fun completePlan(boxes:List<TextLocator.Box>,w:Int,h:Int):List<Block>{
  val fragments=boxes.mapIndexed{i,b->
   val p=b.points;val a=hypot(p[2]-p[0],p[3]-p[1]);val c=hypot(p[4]-p[2],p[5]-p[3])
   val dx=if(a>=c)p[2]-p[0] else p[4]-p[2]
   val dy=if(a>=c)p[3]-p[1] else p[5]-p[3]
   val r=b.rect;val tall=abs(dy)>abs(dx)*1.7f
   val ww=if(tall)min(a,c)else r.width();val hh=if(tall)max(a,c)else r.height()
   VerticalTracks.Fragment(i,r.centerX(),r.centerY(),ww,hh,if(tall&&abs(dy)>1f)dx/dy else 0f,b.score)
  }
  val tracks=VerticalTracks.build(fragments)
  val consumed=tracks.flatMap{it.ids}.toSet()
  val vr=tracks.map{t->
   val pad=t.width*.10f;val top=t.top-pad;val bottom=t.bottom+pad
   val half=t.width*.65f
   val p=floatArrayOf(t.center+t.slope*top-half,top,t.center+t.slope*top+half,top,t.center+t.slope*bottom+half,bottom,t.center+t.slope*bottom-half,bottom)
   for(i in p.indices)p[i]=p[i].coerceIn(0f,if(i%2==0)w.toFloat()else h.toFloat())
   Block(listOf(TextLocator.Box(p,t.ids.maxOf{boxes[it].score})),true)
  }
  val hr=RowPlanner.plan(boxes.filterIndexed{i,_->i !in consumed},w,h,1.6f,true).rows.map{Block(it,false)}
  if(vr.isEmpty())return hr
  val top=vr.minOf{it.boxes.first().rect.top};val bottom=vr.maxOf{it.boxes.first().rect.bottom}
  return hr.filter{it.boxes.minOf{q->q.rect.centerY()}<top}+vr+hr.filter{it.boxes.minOf{q->q.rect.centerY()} in top..bottom}+hr.filter{it.boxes.minOf{q->q.rect.centerY()}>bottom}
 }
 fun axis(r:RectF,score:Float)=TextLocator.Box(floatArrayOf(r.left,r.top,r.right,r.top,r.right,r.bottom,r.left,r.bottom),score)
 // Find blank horizontal gaps in a rectified vertical strip. Fall back to estimated
 // character pitch only when ink runs are unusually tall; retain original pixels.
 fun cuts(profile:IntArray,width:Int):List<IntRange>{
  val runs=mutableListOf<IntRange>();var begin=-1
  for(i in 0..profile.size){
   val ink=i<profile.size&&profile[i]>=max(1,width/18)
   if(ink&&begin<0)begin=i
   if(!ink&&begin>=0){runs.add(begin until i);begin=-1}
  }
  val merged=mutableListOf<IntRange>()
  for(a in runs){val last=merged.lastOrNull()
   if(last!=null&&a.first-last.last<=max(2,width/7)&&a.last-last.first<=width*1.25f)merged[merged.lastIndex]=last.first..a.last
   else merged.add(a)
  }
  return merged.flatMap{a->
   val len=a.last-a.first+1
   val n=if(len>width*1.65f)(len.toFloat()/width).roundToInt().coerceAtLeast(1)else 1
   (0 until n).map{k->(a.first+k*len/n) until (a.first+(k+1)*len/n)}
  }.filter{it.last-it.first+1>=max(2,width/10)}
 }
 fun verticalRead(bm:Bitmap,box:TextLocator.Box,reader:Reader,refine:Boolean=false):Reader.Answer{
  val p=box.points
  // Choose the top-left corner and clockwise order so glyphs remain upright.
  val start=(0..3).minByOrNull{p[it*2]+p[it*2+1]}!!
  val q=FloatArray(8){i->p[((start+i/2)%4)*2+i%2]}
  val ww=(hypot(q[2]-q[0],q[3]-q[1])+hypot(q[4]-q[6],q[5]-q[7]))/2
  val hh=(hypot(q[6]-q[0],q[7]-q[1])+hypot(q[4]-q[2],q[5]-q[3]))/2
  if(ww<2||hh<2||hh<ww*1.5f)return reader.read(bm,box)
  val w=64;val h=(hh/ww*w).roundToInt().coerceIn(64,4096)
  val strip=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
  try{
   val m=Matrix();check(m.setPolyToPoly(q,0,floatArrayOf(0f,0f,w.toFloat(),0f,w.toFloat(),h.toFloat(),0f,h.toFloat()),0,4))
   Canvas(strip).apply{drawColor(Color.WHITE);drawBitmap(bm,m,Paint(Paint.FILTER_BITMAP_FLAG))}
   val pixels=IntArray(w*h);strip.getPixels(pixels,0,w,0,0,w,h)
   fun gray(c:Int)=(Color.red(c)*3+Color.green(c)*6+Color.blue(c))/10
   val bg=pixels.map{gray(it)}.sorted().let{it[it.size*3/4]}
   val profile=IntArray(h){y->(w/10 until w-w/10).count{x->gray(pixels[y*w+x])<bg-35}}
   val basic=cuts(profile,w)
   val parts=if(refine)VerticalTracks.gridCuts(profile,w,basic)else basic
   if(parts.isEmpty())return Reader.Answer("",0f,320,0f)
   val out=StringBuilder();var sum=0f;var minConf=1f;var count=0
   // Reassemble upright glyph crops into short horizontal strips (no 90-degree glyph rotation).
   for(chunk in parts.chunked(12)){
    val line=Bitmap.createBitmap(chunk.size*64,64,Bitmap.Config.ARGB_8888)
    try{
     val canvas=Canvas(line);canvas.drawColor(Color.rgb(bg,bg,bg))
     for((i,a) in chunk.withIndex()){
      val top=max(0,a.first-2);val bottom=min(h,a.last+3);val len=bottom-top
      val destH=min(60,len);val dy=(64-destH)/2
      canvas.drawBitmap(strip,Rect(0,top,w,bottom),Rect(i*64+2,dy,i*64+62,dy+destH),Paint(Paint.FILTER_BITMAP_FLAG))
     }
     val ans=reader.read(line,axis(RectF(0f,0f,line.width.toFloat(),64f),box.score),true)
     out.append(ans.text);sum+=ans.confidence;minConf=min(minConf,ans.minConfidence);count++
    }finally{line.recycle()}
   }
   return Reader.Answer(out.toString(),sum/count,320,minConf)
  }finally{strip.recycle()}
 }
}
