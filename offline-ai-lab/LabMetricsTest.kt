package com.example.minicpm_v_demo
import org.junit.Test
import org.junit.Assert.*
class LabMetricsTest{
    @Test fun dimensions(){
        assertEquals(3072 to 4096,LabMetrics.fit(3072,4096,0))
        assertEquals(768 to 1024,LabMetrics.fit(3072,4096,1024))
        assertEquals(2048 to 1536,LabMetrics.fit(4096,3072,2048))
        assertEquals(300 to 200,LabMetrics.fit(300,200,768))
        assertEquals(1 to 768,LabMetrics.fit(1,4096,768))
    }
    @Test fun accuracy(){
        assertEquals(0.0,LabMetrics.cer("你好😀","你好😀"),0.0)
        assertEquals(0.5,LabMetrics.cer("你好","你"),0.0)
        assertEquals(1.5,LabMetrics.cer("你好","你好世界啊"),0.0)
        assertEquals("10.00",LabMetrics.rate(20,2000))
        assertEquals("NA",LabMetrics.rate(20,0))
    }
}
