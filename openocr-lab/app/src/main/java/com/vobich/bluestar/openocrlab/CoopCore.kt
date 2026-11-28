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
    require(raw.isFinite()&&raw==id.toFloat()&&id in chars.indices&&score.isFinite()){"invalid compact output row=$row step=$step raw_index=$raw score=$score shape="+shape.contentToString()}
    if(id!=0&&id!=last){text.append(chars[id]);sum+=score;low=min(low,score);n++};last=id
   }
   Decoded(text.toString(),if(n>0)sum/n else 0f,if(n>0)low else 0f)
  }
 }
}

/** Page-level fixed shape, no resizing or truncating existing crop pixels.
 * The duplicate tail rows are excluded from the decoded real prefix. */
internal object FixedGpuInput {
 fun widthFor(maxWidth:Int):Int {
  require(maxWidth in 1..4096){"GPU width outside supported bounds: $maxWidth"}
  return ((maxWidth.coerceAtLeast(320)+63)/64)*64
 }
 fun pad(input:java.nio.FloatBuffer, real:Int, width:Int, batch:Int, targetWidth:Int):java.nio.FloatBuffer {
  require(real in 1..batch && batch in 1..6 && width in 1..targetWidth && targetWidth<=4096)
  val src=input.duplicate().apply{rewind()}
  require(src.remaining()==real*3*48*width)
  if(real==batch&&width==targetWidth)return src
  val dst=java.nio.ByteBuffer.allocateDirect(batch*3*48*targetWidth*4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
  val row=FloatArray(width)
  for(b in 0 until batch)for(y in 0 until 3*48){
   src.position((min(b,real-1)*3*48+y)*width);src.get(row)
   dst.put(row);repeat(targetWidth-width){dst.put(0f)}
  }
  dst.rewind();return dst
 }
 fun realPrefix(values:FloatArray,shape:IntArray,real:Int):FloatArray {
  require(shape.size==3&&shape[0] in real..6&&shape[1]>0&&shape[2]==2&&shape.fold(1L){a,b->a*b}==values.size.toLong())
  return if(real==shape[0])values else values.copyOf(real*shape[1]*2)
 }
}

/** The first two modes preserve v0.1.2 behavior. Diagnostic modes do not
 * silently substitute another engine or change the original crop. */
internal enum class CoopMode(val fixedWidth:Boolean,val fixedBatch:Boolean,val mixed:Boolean,val cpuOnly:Boolean){
 MIXED_FIXED(true,true,true,false),
 GPU_FIXED(true,true,false,false),
 CPU_FIXED(true,true,false,true),
 GPU_EXACT(false,false,false,false),
 GPU_TAIL_FIXED(false,true,false,false),
 MIXED_EXACT(false,false,true,false),
 GPU_TAIL_FIRST(false,true,false,false);
 fun targetShape(original:LongArray,pageWidth:Int,batch:Int):LongArray{
  require(original.size==4&&original[0] in 1L..6L&&original[1]==3L&&original[2]==48L)
  val n=if(fixedBatch)batch.toLong()else original[0]
  val w=if(fixedWidth)pageWidth.toLong()else original[3]
  require(n in original[0]..6L&&w in original[3]..4096L)
  return longArrayOf(n,3,48,w)
 }
}

/** Keep original group membership/order within each batch. Reserve an incomplete
 * last batch for ORT in the cooperative candidate; do not pad it for GPU.
 * Tail-first is a strict diagnostic: send the formerly failing shape first. */
internal object ExactBatchOrder{
 fun order(mode:CoopMode,count:Int,partialTail:Boolean):List<Int>{
  require(count>0)
  val ids=(0 until count).toList()
  return when{
   mode==CoopMode.GPU_TAIL_FIRST->listOf(ids.last())+ids.dropLast(1)
   mode==CoopMode.MIXED_EXACT&&partialTail&&count>1->listOf(0,count-1)+(1 until count-1).toList()
   else->ids
  }
 }
}
