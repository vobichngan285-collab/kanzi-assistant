package com.vobich.bluestar.openocrlab

import org.junit.Test
import org.junit.Assert.*
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToInt

class V2PreprocessTest{
 @Test fun officialFixtures(){
  val fixtures=javaClass.getResourceAsStream("/v2-preprocess.tsv")
  assertNotNull("Missing CI upstream fixtures",fixtures)
  fixtures!!.bufferedReader().useLines{lines->
   var count=0
   for(line in lines.filter{it.isNotBlank()}){
    val f=line.split('\t');val w=f[0].toInt();val h=f[1].toInt()
    val shape=V2Preprocess.shape(w,h)
    assertEquals("width for ${w}x$h",f[2].toInt(),shape.w)
    assertEquals("height for ${w}x$h",f[3].toInt(),shape.h)
    val raw=Base64.getDecoder().decode(f[4]);val expected=Base64.getDecoder().decode(f[5])
    val tensor=V2Preprocess.tensor(raw,w,h,shape)
    assertEquals(expected.size,tensor.size)
    var worst=0
    for(i in tensor.indices)worst=maxOf(worst,abs(((tensor[i]+1f)*127.5f).roundToInt()-(expected[i].toInt() and 255)))
    assertTrue("Pillow parity ${w}x$h maximum pixel error=$worst",worst<=1)
    count++
   }
   assertEquals(11,count)
  }
 }
}
