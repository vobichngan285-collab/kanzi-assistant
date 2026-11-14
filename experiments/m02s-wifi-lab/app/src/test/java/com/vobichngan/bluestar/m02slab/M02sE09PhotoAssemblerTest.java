package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

public class M02sE09PhotoAssemblerTest {
    private static byte[] packet(int cmd, byte... payload) {
        byte[] a = new byte[8+payload.length];
        a[0]=0x52;a[1]=0x58;a[2]=0;a[3]=(byte)(payload.length+2);
        a[4]=(byte)cmd;
        System.arraycopy(payload,0,a,5,payload.length);
        a[a.length-3]=0;a[a.length-2]=0x58;a[a.length-1]=0x52;
        return a;
    }
    @Test public void acceptsOffsetChunksInReverseOrderAndDuplicates() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        assertEquals(M02sE09PhotoAssembler.Step.START,a.receive(packet(0x97,(byte)0,(byte)0,(byte)0,(byte)6)));
        assertEquals(M02sE09PhotoAssembler.Step.CHUNK,a.receive(packet(0x98,(byte)0,(byte)0,(byte)0,(byte)3,(byte)0x11,(byte)0xff,(byte)0xd9)));
        assertEquals(M02sE09PhotoAssembler.Step.CHUNK,a.receive(packet(0x98,(byte)0,(byte)0,(byte)0,(byte)0,(byte)0xff,(byte)0xd8,(byte)0x00)));
        assertEquals(M02sE09PhotoAssembler.Step.CHUNK,a.receive(packet(0x98,(byte)0,(byte)0,(byte)0,(byte)0,(byte)0xff,(byte)0xd8,(byte)0x00)));
        assertEquals(M02sE09PhotoAssembler.Step.COMPLETE,a.receive(packet(0x99)));
        assertArrayEquals(new byte[]{(byte)0xff,(byte)0xd8,0,0x11,(byte)0xff,(byte)0xd9},a.result());
    }
    @Test public void refusesIncompleteFrameAndNoImage() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        assertEquals(M02sE09PhotoAssembler.Step.IGNORED,a.receive(packet(0x98,(byte)0,(byte)0,(byte)0,(byte)0,(byte)3)));
        a.receive(packet(0x97,(byte)0,(byte)0,(byte)0,(byte)6));
        a.receive(packet(0x98,(byte)0,(byte)0,(byte)0,(byte)0,(byte)0xff,(byte)0xd8));
        assertEquals(M02sE09PhotoAssembler.Step.ERROR,a.receive(packet(0x99)));
        assertNull(a.result());
    }
    @Test public void rejectsOversizedDeclaredImage() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        // length field is 16-bit on the E09 wire protocol. Invalid zero is rejected.
        assertEquals(M02sE09PhotoAssembler.Step.ERROR,
                a.receive(packet(0x97,(byte)0,(byte)0,(byte)0,(byte)0)));
    }
    @Test public void ignoresNonAA15AndNeverConfusesAA14Notifications() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        assertEquals(M02sE09PhotoAssembler.Step.IGNORED,
                a.receive(new byte[]{(byte)0xac,0x55,0,3,0x22,1,0x23}));
    }
}
