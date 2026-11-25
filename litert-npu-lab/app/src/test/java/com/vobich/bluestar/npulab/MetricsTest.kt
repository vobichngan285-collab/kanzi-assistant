package com.vobich.bluestar.npulab
import org.junit.Test
import org.junit.Assert.*
class MetricsTest {
    @Test fun metrics(){
        assertEquals(0.0,Metrics.cer("蓝星向导\n药品 5931","蓝星向导 药品5931")!!,0.0)
        assertEquals(0.25,Metrics.cer("4827","4829")!!,0.0)
        assertNull(Metrics.cer(" ","x"))
        assertEquals(1.0,Metrics.cer("abc","")!!,0.0)
        assertEquals("",Metrics.visible("<thi"))
        assertEquals("",Metrics.visible("<think>文字"))
        assertEquals("药品5931",Metrics.visible("<think>分析</think>\n药品5931"))
        assertEquals("BLUE STAR",Metrics.visible("\nBLUE STAR"))
    }
}
