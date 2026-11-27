package com.vobich.bluestar.openocrlab
import org.junit.Test
import org.junit.Assert.*
class PixelInputTest {
 @Test fun capsNeverUpscale(){
  for((w,h) in listOf(5184 to 3888,5008 to 2816,1920 to 1080,1000 to 2000,1 to 24000000)){
   for(cap in PixelInput.caps){
    val (x,y)=PixelInput.size(w,h,cap)
    assertTrue(x in 1..w && y in 1..h)
    if(cap==0L||w.toLong()*h<=cap)assertEquals(w to h,x to y)
    else assertTrue(x.toLong()*y<=cap)
   }
  }
 }
 @Test fun exactAndOriginal(){assertEquals(5008 to 2816,PixelInput.size(5008,2816,16_000_000));assertEquals(5184 to 3888,PixelInput.size(5184,3888,0))}
}
