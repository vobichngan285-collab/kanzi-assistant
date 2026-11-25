package com.vobich.bluestar.npulab
object Metrics {
    fun cer(reference: String, answer: String): Double? {
        val a=reference.filterNot{it.isWhitespace()}
        val b=answer.filterNot{it.isWhitespace()}
        if(a.isEmpty() || a.length>4096 || b.length>8192) return null
        var prev=IntArray(b.length+1){it}
        for(i in a.indices){
            val next=IntArray(b.length+1); next[0]=i+1
            for(j in b.indices) next[j+1]=minOf(next[j]+1,prev[j+1]+1,prev[j]+if(a[i]==b[j])0 else 1)
            prev=next
        }
        return prev.last().toDouble()/a.length
    }
    fun visible(raw:String):String {
        val s=raw.trimStart();val open="<think>";val close="</think>"
        if(s.isEmpty() || (s.length<open.length && open.startsWith(s))) return ""
        if(s.startsWith(open)){val end=s.indexOf(close,open.length);return if(end<0) "" else s.substring(end+close.length).trimStart()}
        return s
    }
}
