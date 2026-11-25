package com.vobich.bluestar.npulab
import org.junit.Test
import org.junit.Assert.*
class LabProtocolTest {
    @Test fun modesAndWatchdog(){
        assertEquals(setOf("cpu","gpu_vision"),LabProtocol.modes)
        assertEquals(listOf("cpu","gpu_vision","gpu_vision","cpu"),LabProtocol.comparison)
        assertEquals(4,LabProtocol.rounds)
        assertEquals(20000L,LabProtocol.deadlineMs("connect"))
        assertEquals(120000L,LabProtocol.deadlineMs("conversation"))
        assertEquals(180000L,LabProtocol.deadlineMs("send"))
        assertEquals(30000L,LabProtocol.deadlineMs("close_engine"))
        assertTrue(LabProtocol.deadlineMs("first_text")>0)
    }
}
