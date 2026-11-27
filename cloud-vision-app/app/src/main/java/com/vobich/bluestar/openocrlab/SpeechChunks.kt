package com.vobich.bluestar.openocrlab
internal object SpeechChunks {
 fun split(text:String,limit:Int):List<String>{
  require(limit>=2)
  if(text.isEmpty())return emptyList()
  val out=mutableListOf<String>();var start=0
  while(start<text.length){
   var end=minOf(start+limit,text.length)
   if(end<text.length){
    if(Character.isHighSurrogate(text[end-1])&&Character.isLowSurrogate(text[end]))end--
    val floor=start+(end-start)/2
    for(i in end-1 downTo floor){if(text[i] in "\n。！？；.!?;"){end=i+1;break}}
   }
   out.add(text.substring(start,end));start=end
  }
  return out
 }
}
