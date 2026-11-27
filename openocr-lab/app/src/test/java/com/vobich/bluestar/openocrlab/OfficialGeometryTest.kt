package com.vobich.bluestar.openocrlab
import org.junit.Assert.*
import org.junit.Test
class OfficialGeometryTest {
 @Test fun resizeUsesPythonTiesEven(){assertEquals(1216,OfficialGeometry.dimension(3072,1600.0/4096));assertEquals(64,OfficialGeometry.dimension(80,1.0));assertEquals(32,OfficialGeometry.dimension(1,1.0))}
 @Test fun coordinateRoundAndClip(){assertEquals(2f,OfficialGeometry.map(2.5,10,10),0f);assertEquals(0f,OfficialGeometry.map(-1.0,10,10),0f);assertEquals(10f,OfficialGeometry.map(12.0,10,10),0f)}
 @Test fun officialWidthNoArbitrary960Cap(){assertEquals(320,OfficialGeometry.recWidth(100,48));assertEquals(1200,OfficialGeometry.recWidth(1200,48))}
 @Test fun rectangleRoundOffset(){val p=OfficialGeometry.unclip(doubleArrayOf(10.0,10.0,30.0,10.0,30.0,20.0,10.0,20.0)).single();val x=p.filterIndexed{i,_->i%2==0};val y=p.filterIndexed{i,_->i%2==1};assertEquals(5.0,x.min(),0.0);assertEquals(35.0,x.max(),0.0);assertEquals(5.0,y.min(),0.0);assertEquals(25.0,y.max(),0.0)}
 @Test fun officialSortLocalSwap(){fun b(x:Float,y:Float)=TextLocator.Box(floatArrayOf(x,y,x+5,y,x+5,y+5,x,y+5),.9f);val a=b(100f,10f);val c=b(10f,15f);val d=b(0f,30f);assertEquals(listOf(c,a,d),OfficialGeometry.order(listOf(d,a,c)))}
}
