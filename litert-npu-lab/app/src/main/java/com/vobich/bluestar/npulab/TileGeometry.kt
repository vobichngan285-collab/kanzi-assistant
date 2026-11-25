package com.vobich.bluestar.npulab
import kotlin.math.*
object TileGeometry{
    fun count(w:Int,h:Int):Int{
        require(w>0&&h>0)
        val ratio=max(w,h).toDouble()/min(w,h)
        return if(ratio<1.25){if(min(w,h)>=640)2 else 1}else ceil(ratio).toInt().coerceIn(2,4)
    }
    fun edges(length:Int,count:Int,energy:(Int)->Double):List<Int>{
        require(count in 1..4&&length>=count)
        val result=mutableListOf(0)
        for(i in 1 until count){
            val ideal=length*i/count
            val range=max(1,length/count/8)
            val lo=max(result.last()+1,ideal-range)
            val hi=min(length-(count-i),ideal+range)
            val best=(lo..hi).minByOrNull{energy(it)+0.35*abs(it-ideal)/range}?:ideal
            result.add(best)
        }
        result.add(length)
        return result
    }
    fun repeatedLines(answer:String):Int{
        val seen=mutableSetOf<String>()
        return answer.lines().map{it.filterNot{c->c.isWhitespace()}}.count{it.length>=4&&!seen.add(it)}
    }
}
