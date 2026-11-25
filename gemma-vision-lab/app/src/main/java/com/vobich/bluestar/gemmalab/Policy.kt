package com.vobich.bluestar.gemmalab
object Policy {
 const val MODEL_SIZE=1708468392L
 const val MODEL_SHA="4f52793970e74cae45b971e447cd5834dac6b9a70b687a5fe0f968c6e1c26b9e"
 const val MODEL_URL="https://huggingface.co/PenraApp/penra-models/resolve/e7947bb3f9a2960713df011ebca32aedc0f271bb/penra-e2b-1120.litertlm"
 const val OCR="请准确识别图片中的所有文字、数字和价格，按照原文顺序输出，不要总结，不要猜测。"
 fun budgets(options:IntArray?, ceiling:Int)=(options?:intArrayOf()).filter{it>0&&(ceiling<=0||it<=ceiling)}.distinct().sorted()
 fun size(w:Int,h:Int,cap:Int):Pair<Int,Int>{
  require(w>0&&h>0&&cap>=0)
  val edge=maxOf(w,h)
  if(cap==0||edge<=cap)return w to h
  return maxOf(1,(w.toLong()*cap/edge).toInt()) to maxOf(1,(h.toLong()*cap/edge).toInt())
 }
}