package com.vobich.bluestar.openocrlab
import org.junit.Assert.*
import org.junit.Test

class FloatScratchTest {
 @Test fun exactSliceAndReuse(){
  val s=FloatScratch(64)
  val a=s.acquire(16)
  assertTrue(a.isDirect);assertEquals(16,a.remaining())
  repeat(16){a.put(it.toFloat())}
  val b=s.acquire(4)
  assertEquals(4,b.capacity());assertEquals(4,b.remaining())
  repeat(4){b.put(-it.toFloat())}
  b.rewind();assertEquals(-3f,b.get(3),0f)
  assertEquals(1L,s.allocations);assertEquals(1L,s.reuses)
  assertEquals(64,s.retainedBytes)
 }
 @Test fun growAndDoNotRetainLarge(){
  val s=FloatScratch(64)
  s.acquire(4);s.acquire(12);s.acquire(32)
  assertEquals(48,s.retainedBytes);assertEquals(3L,s.allocations)
  assertEquals(8,s.acquire(8).remaining());assertEquals(1L,s.reuses)
  s.clear();assertEquals(0,s.retainedBytes)
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsInvalid(){FloatScratch(16).acquire(0)}
}
