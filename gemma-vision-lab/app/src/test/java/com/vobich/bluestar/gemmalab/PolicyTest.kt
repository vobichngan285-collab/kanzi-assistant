package com.vobich.bluestar.gemmalab
import org.junit.Assert.*
import org.junit.Test
class PolicyTest{
 @Test fun budgets(){assertEquals(listOf(70,140,280),Policy.budgets(intArrayOf(1120,70,280,140,280,0),280));assertTrue(Policy.budgets(null,280).isEmpty())}
 @Test fun size(){assertEquals(3072 to 4096,Policy.size(3072,4096,0));assertEquals(768 to 1024,Policy.size(3072,4096,1024));assertEquals(384 to 512,Policy.size(3072,4096,512));assertEquals(100 to 80,Policy.size(100,80,512))}
}