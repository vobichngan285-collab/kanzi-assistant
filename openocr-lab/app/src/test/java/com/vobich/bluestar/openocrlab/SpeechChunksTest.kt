package com.vobich.bluestar.openocrlab
import org.junit.Assert.*
import org.junit.Test
class SpeechChunksTest {
 @Test fun shortTextUnchanged(){val s="价格 404.1 元\n型号 C110";assertEquals(listOf(s),SpeechChunks.split(s,100))}
 @Test fun longTextLossless(){val s="第一句。第二句！价格 404.1 元，型号 C110。";val c=SpeechChunks.split(s,8);assertEquals(s,c.joinToString(""));assertTrue(c.all{it.length<=8})}
 @Test fun surrogatePreserved(){val s="甲😀乙😀丙";val c=SpeechChunks.split(s,2);assertEquals(s,c.joinToString(""));assertTrue(c.none{Character.isHighSurrogate(it.last())||Character.isLowSurrogate(it.first())})}
}
