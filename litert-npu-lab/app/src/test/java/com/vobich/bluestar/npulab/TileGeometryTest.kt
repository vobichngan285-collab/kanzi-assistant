package com.vobich.bluestar.npulab
import org.junit.Test
import org.junit.Assert.*
class TileGeometryTest{
    @Test fun countAndCover(){
        assertEquals(3,TileGeometry.count(691,1536))
        assertEquals(4,TileGeometry.count(100,2000))
        assertEquals(1,TileGeometry.count(448,448))
        assertEquals(2,TileGeometry.count(1200,1200))
        val e=TileGeometry.edges(1536,3){if(it==500||it==1010)0.0 else 100.0}
        assertEquals(listOf(0,500,1010,1536),e)
        assertTrue(e.zipWithNext().all{it.first<it.second})
    }
    @Test fun repeatedLinesAreOnlyCounted(){
        assertEquals(1,TileGeometry.repeatedLines("1234\n1234\n12\n12"))
    }
}
