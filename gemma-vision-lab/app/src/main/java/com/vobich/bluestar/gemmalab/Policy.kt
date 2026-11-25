package com.vobich.bluestar.gemmalab
object Policy {
 const val MODEL_SIZE=2588147712L
 const val MODEL_SHA="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
 const val MODEL_URL="https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm"
 const val OCR="请准确识别图片中的所有文字、数字和价格，按照原文顺序输出，不要总结，不要猜测。"
 fun budgets(options:IntArray?, ceiling:Int)=(options?:intArrayOf()).filter{it>0&&(ceiling<=0||it<=ceiling)}.distinct().sorted()
 fun size(w:Int,h:Int,cap:Int):Pair<Int,Int>{
  require(w>0&&h>0&&cap>=0)
  val edge=maxOf(w,h)
  if(cap==0||edge<=cap)return w to h
  return maxOf(1,(w.toLong()*cap/edge).toInt()) to maxOf(1,(h.toLong()*cap/edge).toInt())
 }
}