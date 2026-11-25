package com.vobich.bluestar.npulab
import kotlin.math.*
data class TextBox(val l:Int,val t:Int,val r:Int,val b:Int){
    val w:Int get()=r-l
    val h:Int get()=b-t
    val area:Long get()=w.toLong()*h
    fun union(o:TextBox)=TextBox(min(l,o.l),min(t,o.t),max(r,o.r),max(b,o.b))
    fun clamp(w:Int,h:Int)=TextBox(l.coerceIn(0,w),t.coerceIn(0,h),r.coerceIn(0,w),b.coerceIn(0,h))
}
object RegionGeometry {
    data class Choice(val box:TextBox?,val groups:Int,val included:Int,val total:Int,val reason:String)
    fun choose(boxes:List<TextBox>,width:Int,height:Int):Choice{
        val lines=boxes.map{it.clamp(width,height)}.filter{it.w>=6&&it.h>=5}
        if(lines.isEmpty())return Choice(null,0,0,0,"no_lines")
        if(lines.size>500)return Choice(null,0,0,lines.size,"too_many_lines")
        val parent=IntArray(lines.size){it}
        fun root(i:Int):Int {var k=i;while(parent[k]!=k){parent[k]=parent[parent[k]];k=parent[k]};return k}
        for(i in lines.indices)for(j in i+1 until lines.size){
            val a=lines[i];val b=lines[j]
            val h=max(a.h,b.h)
            val gapX=max(0,max(a.l,b.l)-min(a.r,b.r))
            val gapY=max(0,max(a.t,b.t)-min(a.b,b.b))
            val overlapX=min(a.r,b.r)-max(a.l,b.l)
            val overlapY=min(a.b,b.b)-max(a.t,b.t)
            val rows=overlapX>=min(a.w,b.w)*0.20 && gapY<=h*2.4
            val columns=overlapY>=min(a.h,b.h)*0.45 && gapX<=h*6.0
            if((rows||columns)&&max(a.h,b.h)<=min(a.h,b.h)*3.5)parent[root(j)]=root(i)
        }
        val groups=lines.indices.groupBy{root(it)}.values
        val best=groups.maxByOrNull{g->g.sumOf{lines[it].area.toDouble().let(::sqrt)}}!!
        val core=best.map{lines[it]}.reduce{a,b->a.union(b)}
        if(best.size<2 && core.area<width.toLong()*height*0.025)return Choice(null,groups.size,best.size,lines.size,"weak_region")
        val margin=max(8,(max(core.w,core.h)*0.11).roundToInt())
        val crop=TextBox(core.l-margin,core.t-margin,core.r+margin,core.b+margin).clamp(width,height)
        if(crop.area>=width.toLong()*height*0.80)return Choice(null,groups.size,best.size,lines.size,"already_full")
        return Choice(crop,groups.size,best.size,lines.size,"primary_region")
    }
    fun scale(box:TextBox,sw:Int,sh:Int,dw:Int,dh:Int)=TextBox(
        floor(box.l.toDouble()*dw/sw).toInt(),floor(box.t.toDouble()*dh/sh).toInt(),
        ceil(box.r.toDouble()*dw/sw).toInt(),ceil(box.b.toDouble()*dh/sh).toInt()).clamp(dw,dh)
}
