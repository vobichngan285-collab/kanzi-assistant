package com.vobich.bluestar.openocrlab

import kotlin.math.min

/** Reserved first batches prove both engines take real work, when available.
 * Every remaining ordinal can be claimed by exactly one lane.
 */
internal class BatchDispatch(private val total:Int,private val reserved:Int){
 init{require(total>=1&&reserved in 1..total)}
 private val claimed=BooleanArray(total)
 private var cursor=reserved
 @Synchronized fun first(index:Int):Int{
  require(index in 0 until reserved&&!claimed[index]);claimed[index]=true;return index
 }
 @Synchronized fun next():Int?{
  if(cursor>=total)return null
  val i=cursor++;check(!claimed[i]);claimed[i]=true;return i
 }
}
internal object CompactCtc{
 data class Decoded(val text:String,val confidence:Float,val minConfidence:Float)
 fun decode(values:FloatArray,shape:IntArray,chars:List<String>):List<Decoded>{
  require(shape.size==3&&shape[0]>0&&shape[1]>0&&shape[2]==2&&shape.fold(1L){a,b->a*b}==values.size.toLong())
  return List(shape[0]){row->
   var last=-1;var sum=0f;var n=0;var low=1f;val text=StringBuilder()
   repeat(shape[1]){step->
    val at=(row*shape[1]+step)*2;val raw=values[at];val score=values[at+1];val id=raw.toInt()
    require(raw.isFinite()&&raw==id.toFloat()&&id in chars.indices&&score.isFinite()){"invalid compact output"}
    if(id!=0&&id!=last){text.append(chars[id]);sum+=score;low=min(low,score);n++};last=id
   }
   Decoded(text.toString(),if(n>0)sum/n else 0f,if(n>0)low else 0f)
  }
 }
}
