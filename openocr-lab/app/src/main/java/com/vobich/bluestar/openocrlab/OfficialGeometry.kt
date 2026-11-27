package com.vobich.bluestar.openocrlab

import de.lighti.clipper.Clipper
import de.lighti.clipper.ClipperOffset
import de.lighti.clipper.Path
import de.lighti.clipper.Paths
import de.lighti.clipper.Point.LongPoint
import kotlin.math.*

/** Android port of OpenOCR quad path, upstream 1ccfc6ee6161f7133b192e16af3a9265273997ba.
 * Java Clipper is a port, not a byte-identical Python/C++ implementation. */
object OfficialGeometry {
 fun dimension(source:Int,ratio:Double):Int=max(32,(round((source*ratio).toInt()/32.0)*32).toInt())
 fun map(v:Double,src:Int,map:Int):Float=round(v*src/map).coerceIn(0.0,src.toDouble()).toFloat()
 fun order(boxes:List<TextLocator.Box>):List<TextLocator.Box>{
  val a=boxes.sortedWith(compareBy<TextLocator.Box>{it.points[1]}.thenBy{it.points[0]}).toMutableList()
  for(i in 0 until a.size-1)for(j in i downTo 0){
   if(abs(a[j+1].points[1]-a[j].points[1])<10&&a[j+1].points[0]<a[j].points[0]){
    val t=a[j];a[j]=a[j+1];a[j+1]=t
   }else break
  }
  return a
 }
 fun unclip(points:DoubleArray):List<DoubleArray>{
  require(points.size==8)
  var area=0.0;var perimeter=0.0
  val path=Path(4)
  for(i in 0..3){val j=(i+1)%4
   val x=points[2*i];val y=points[2*i+1]
   area+=x*points[2*j+1]-y*points[2*j]
   perimeter+=hypot(x-points[2*j],y-points[2*j+1])
   path.add(LongPoint(x.toLong(),y.toLong()))
  }
  if(perimeter<=0||abs(area)<=0)return emptyList()
  val offset=ClipperOffset(2.0,.25);offset.addPath(path,Clipper.JoinType.ROUND,Clipper.EndType.CLOSED_POLYGON)
  val result=Paths();offset.execute(result,abs(area)*.5*1.5/perimeter)
  return result.map{poly->DoubleArray(poly.size*2){i->if(i%2==0)poly[i/2].x.toDouble()else poly[i/2].y.toDouble()}}
 }
 fun recWidth(w:Int,h:Int):Int=max(320,(48.0*max(w.toDouble()/h,320.0/48)).toInt())
 fun cropWidth(p:FloatArray):Int=max(hypot(p[2]-p[0],p[3]-p[1]),hypot(p[4]-p[6],p[5]-p[7])).toInt()
 fun cropHeight(p:FloatArray):Int=max(hypot(p[6]-p[0],p[7]-p[1]),hypot(p[4]-p[2],p[5]-p[3])).toInt()
}
