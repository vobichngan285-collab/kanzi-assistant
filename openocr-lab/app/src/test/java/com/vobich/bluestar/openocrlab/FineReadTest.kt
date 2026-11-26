package com.vobich.bluestar.openocrlab
import org.junit.Test
import org.junit.Assert.*
class FineReadTest{
 fun a(t:String,c:Float=.95f,m:Float=.85f)=Reader.Answer(t,c,320,m)
 @Test fun addsOnlyVisualSupportedCharacter(){assertTrue(FineRead.accept(a("滑 走罐",.9f),a("滑罐走罐")));assertFalse(FineRead.accept(a("滑走罐"),a("滑罐走罐",.6f)))}
 @Test fun digitsNeverSilentlyChange(){assertFalse(FineRead.accept(a("90元"),a("900元")));assertFalse(FineRead.accept(a("90元"),a("80元")))}
 @Test fun lowConfidenceIsFlaggedNotErased(){assertTrue(FineRead.suspicious(a("子",.5f),.3f));assertFalse(FineRead.suspicious(a("子",.95f),.3f))}
 @Test fun internalSpaceCanTriggerRetry(){assertTrue(FineRead.retryNeeded(a("滑 走罐")))}
}
