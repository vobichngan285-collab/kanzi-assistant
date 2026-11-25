package com.example.minicpm_v_demo
object LabMetrics {
    fun fit(width:Int,height:Int,edge:Int):Pair<Int,Int>{
        require(width>0 && height>0 && edge>=0)
        val longest=maxOf(width,height)
        if(edge==0 || longest<=edge)return width to height
        return maxOf(1,((width.toLong()*edge+longest/2)/longest).toInt()) to
               maxOf(1,((height.toLong()*edge+longest/2)/longest).toInt())
    }
    fun rate(tokens:Int,ms:Long):String =
        if(ms>0)String.format(java.util.Locale.US,"%.2f",tokens*1000.0/ms) else "NA"
    fun cer(reference:String,result:String):Double{
        val a=reference.codePoints().toArray();val b=result.codePoints().toArray()
        require(a.isNotEmpty() && a.size<=4096 && b.size<=32768)
        var prev=IntArray(b.size+1){it};var cur=IntArray(b.size+1)
        for(i in a.indices){
            cur[0]=i+1
            for(j in b.indices)cur[j+1]=minOf(cur[j]+1,prev[j+1]+1,prev[j]+if(a[i]==b[j])0 else 1)
            val t=prev;prev=cur;cur=t
        }
        return prev[b.size].toDouble()/a.size
    }
}
