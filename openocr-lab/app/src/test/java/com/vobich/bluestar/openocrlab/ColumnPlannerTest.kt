package com.vobich.bluestar.openocrlab
import org.junit.Test
import org.junit.Assert.*
class ColumnPlannerTest {
 fun r(l:Float,t:Float,w:Float,h:Float=20f)=RowPlanner.R(l,t,l+w,t+h,.9f)
 @Test fun bridgesMissingGlyphWithoutJoiningPrice(){
  val rows=(0..6).map{i->val y=i*35f;if(i==3)listOf(r(0f,y,20f),r(80f,y,40f),r(240f,y,40f))else listOf(r(0f,y,120f),r(240f,y,40f))}
  val out=ColumnPlanner.complete(rows)
  assertEquals(2,out[3].size);assertTrue(out[3][0].r>=120f);assertTrue(out[3][0].l<=0f);assertEquals(240f,out[3][1].l,0f)
 }
 @Test fun noTableEvidencePreservesRows(){
  val rows=listOf(listOf(r(0f,0f,20f),r(80f,0f,20f)),listOf(r(10f,40f,90f)))
  assertEquals(rows,ColumnPlanner.complete(rows))
 }
 @Test fun headingAndSeparatedFooterPreserved(){
  val rows=listOf(listOf(r(20f,0f,280f)))+(1..5).map{i->listOf(r(0f,i*35f,120f),r(240f,i*35f,40f))}+listOf(listOf(r(0f,250f,50f),r(150f,250f,50f)))
  assertEquals(rows.first(),ColumnPlanner.complete(rows).first())
  assertEquals(rows.last(),ColumnPlanner.complete(rows).last())
 }
}
