package com.vobich.bluestar.openocrlab
import android.graphics.RectF
/** Shared immutable-by-convention quad data; YOLO engine is archived in v0.4.5. */
object TextLocator{
 data class Box(val points:FloatArray,val score:Float){
  val rect get()=RectF(points.filterIndexed{i,_->i%2==0}.min(),points.filterIndexed{i,_->i%2==1}.min(),points.filterIndexed{i,_->i%2==0}.max(),points.filterIndexed{i,_->i%2==1}.max())
 }
}
