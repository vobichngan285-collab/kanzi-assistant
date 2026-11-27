package com.vobich.bluestar.openocrlab

/** Independent detector postprocessing controls; no scene-specific rules. */
data class DbProfile(val id:String,val mask:Float=.3f,val score:Float=.6f,val minShort:Int=3,val unclip:Double=1.5){
 val minExpanded get()=minShort+2
 companion object {
  val profiles=listOf(
   DbProfile("baseline"),
   DbProfile("small_text",minShort=2),
   DbProfile("tight_box",unclip=1.2),
   DbProfile("mask_low",mask=.25f),
   DbProfile("mask_high",mask=.35f)
  )
  fun shortBucket(v:Double):Int=when{v<1->0;v<2->1;v<3->2;v<4->3;v<5->4;else->5}
 }
}
