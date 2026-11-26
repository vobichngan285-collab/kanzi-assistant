package com.paddle.ocr.demo

import com.paddle.ocr.model.OCRResult
import kotlin.math.abs

/** Geometry-based optional reading-order hypothesis; never modifies raw OCR. */
object RowCandidates {
    private data class B(val t:String,val c:Float,val l:Float,val r:Float,val top:Float,val bottom:Float) {
        val w get()=r-l
        val h get()=bottom-top
        val cy get()=(top+bottom)/2
    }
    fun suggest(results:List<OCRResult>):List<String> {
        val all=results.map { v-> B(v.text,v.confidence,v.box.points.minOf{it.x},v.box.points.maxOf{it.x},v.box.points.minOf{it.y},v.box.points.maxOf{it.y}) }
        val tall=all.filter{it.c>=.9f&&it.w>0&&it.h>=3*it.w&&it.t.length in 3..20&&it.t.all{c->c in '\u4e00'..'\u9fff'}}.sortedBy{it.l}
        val remaining=tall.toMutableList()
        val out=mutableListOf<String>()
        while(remaining.isNotEmpty()){
            val a=remaining.removeAt(0)
            val group=mutableListOf(a)
            for(b in remaining.toList()){
                val last=group.last()
                if(b.t.length==a.t.length&&abs(b.top-a.top)<=a.w*.65f&&abs(b.bottom-a.bottom)<=a.w*.65f&&b.w in a.w*.6f..a.w*1.6f&&b.l-last.l in a.w*.6f..a.w*2.2f){
                    group.add(b);remaining.remove(b)
                }
            }
            if(group.size<3)continue
            val n=a.t.length
            val rowH=group.map{it.h/n}.average()
            val y0=group.map{it.top.toDouble()}.average()
            val right=group.maxOf{it.r}
            val anchors=all.filter{it.c>=.9f&&it.h>0&&it.w>=1.4*it.h&&it.l>=right&&it.l-right<=a.w*12&&it.cy>=a.top-a.w&&it.cy<=a.bottom+a.w&&it.t.matches(Regex("[0-9]+(\\.[0-9]+)?元"))}
            val matches=(0 until n).map { i->
                val cy=y0+(i+.5)*rowH
                anchors.filter{abs(it.cy-cy)<=rowH*.48}.minByOrNull{abs(it.cy-cy)}
            }
            if(matches.any{it==null}||matches.distinct().size!=n)continue
            // Require consistent right-hand price column. Genuine vertical writing can still satisfy this:
            // output is a labelled candidate only.
            val prices=matches.filterNotNull()
            if(prices.maxOf{it.r}-prices.minOf{it.r}>a.w*1.5f)continue
            val lines=(0 until n).map{i->group.joinToString(""){it.t[i].toString()}+"  "+prices[i].t}
            out.add("坐标重组候选（非重新识别）：\n"+lines.joinToString("\n"))
        }
        return out
    }
}
