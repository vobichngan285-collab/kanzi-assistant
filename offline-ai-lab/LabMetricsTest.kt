package com.example.minicpm_v_demo
import org.junit.Test
import org.junit.Assert.*
class LabMetricsTest{
    @Test fun accuracy(){
        assertEquals(0.0,LabMetrics.cer("你好😀","你好😀"),0.0)
        assertEquals(0.5,LabMetrics.cer("你好","你"),0.0)
        assertEquals(1.5,LabMetrics.cer("你好","你好世界啊"),0.0)
        assertEquals("10.00",LabMetrics.rate(20,2000))
        assertEquals("NA",LabMetrics.rate(20,0))
    }
}
