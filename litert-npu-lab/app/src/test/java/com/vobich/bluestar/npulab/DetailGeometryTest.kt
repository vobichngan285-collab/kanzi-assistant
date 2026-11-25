package com.vobich.bluestar.npulab
import org.junit.Test
import org.junit.Assert.*
class DetailGeometryTest{
 @Test fun bandsNeverCutDetectedRows(){
    val lines=(0..10).flatMap{i->listOf(TextBox(300,200+i*45,480,220+i*45),TextBox(490,200+i*45,530,220+i*45))}
    val loose=RegionGeometry.choose(lines,1000,1200)
    val tight=RegionGeometry.choose(lines,1000,1200,0.018)
    assertTrue(tight.box!!.area<loose.box!!.area)
    val bands=RegionGeometry.bands(tight,1000,1200)
    assertTrue(bands.size in 2..3)
    for(b in lines)assertEquals(1,bands.count{it.l<=b.l&&it.t<=b.t&&it.r>=b.r&&it.b>=b.b})
    for((a,b) in bands.zipWithNext())assertTrue(a.b<=b.t)
 }
 @Test fun comparisonPlans(){
    assertEquals(listOf("roi","tight","bands","tight","roi"),LabProtocol.plan("detail_compare"))
    assertEquals(List(5){"tight"},LabProtocol.plan("state_compare"))
 }
}
