package com.vobich.bluestar.npulab
import org.junit.Test
import org.junit.Assert.*
class RegionGeometryTest{
 @Test fun groupsPriceRowsAndExcludesDistantLabel(){
    val lines=(0..8).flatMap{i->listOf(TextBox(300,400+i*35,510,420+i*35),TextBox(550,400+i*35,610,420+i*35))}+TextBox(800,950,900,970)
    val c=RegionGeometry.choose(lines,1000,1200)
    assertEquals("primary_region",c.reason)
    assertEquals(18,c.included)
    assertEquals(19,c.total)
    assertTrue(c.box!!.l<300 && c.box.r>610 && c.box.b<950)
 }
 @Test fun noBoxesAndScale(){
    assertNull(RegionGeometry.choose(emptyList(),100,100).box)
    assertEquals(TextBox(20,30,60,90),RegionGeometry.scale(TextBox(10,10,30,30),100,100,200,300))
    assertEquals(listOf("original","roi","roi","original"),LabProtocol.plan("roi_compare"))
 }
}
