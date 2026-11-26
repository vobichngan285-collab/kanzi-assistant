package com.vobich.bluestar.openocrlab
import org.junit.Test
import org.junit.Assert.*
class RowPlannerTest {
 @Test fun duplicateAndFragments(){
  fun r(l:Float,t:Float,w:Float,h:Float)=RowPlanner.R(l,t,l+w,t+h,.9f)
  val (rows,removed)=RowPlanner.geometry(listOf(r(0f,0f,100f,20f),r(10f,0f,30f,20f),r(220f,0f,35f,20f),r(0f,40f,15f,20f),r(28f,40f,15f,20f),r(56f,40f,15f,20f),r(84f,40f,15f,20f)))
  assertEquals(1,removed);assertEquals(2,rows.size);assertEquals(2,rows[0].size);assertEquals(1,rows[1].size)
  assertTrue(rows[1][0].l<=0f&&rows[1][0].r>=99f)
 }
 @Test fun nearbyRowsRemainSeparate(){
  val a=RowPlanner.R(0f,0f,40f,20f,.8f);val b=RowPlanner.R(0f,23f,40f,43f,.8f)
  assertEquals(2,RowPlanner.geometry(listOf(a,b)).first.size)
 }
}
