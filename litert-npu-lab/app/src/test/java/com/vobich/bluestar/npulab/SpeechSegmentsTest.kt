package com.vobich.bluestar.npulab
import org.junit.Test
import org.junit.Assert.*
class SpeechSegmentsTest {
    @Test fun segmentation(){
        assertEquals(0,SpeechSegments.nextLength("蓝星",false))
        assertEquals(3,SpeechSegments.nextLength("蓝星\n向导",false))
        assertEquals(16,SpeechSegments.nextLength("一二三四五六七八九十一二三四五六七八",false))
        assertEquals(2,SpeechSegments.nextLength("蓝星",true))
        assertEquals(15,SpeechSegments.nextLength("123456789012345😀",false))
        assertEquals(listOf("original"),LabProtocol.plan("warmup"))
    }
}
