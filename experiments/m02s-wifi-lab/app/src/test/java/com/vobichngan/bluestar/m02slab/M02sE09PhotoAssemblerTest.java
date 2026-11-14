package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Arrays;

public class M02sE09PhotoAssemblerTest {
    private static byte[] packet(int cmd, byte... payload) {
        byte[] a = new byte[8 + payload.length];
        a[0] = 0x52; a[1] = 0x58;
        a[2] = (byte)((payload.length + 2) >>> 8);
        a[3] = (byte)(payload.length + 2);
        a[4] = (byte)cmd;
        System.arraycopy(payload, 0, a, 5, payload.length);
        a[a.length - 3] = 0; // Source Swift parser skips checksum
        a[a.length - 2] = 0x58;
        a[a.length - 1] = 0x52;
        return a;
    }
    private static byte[] start(int declared) {
        return packet(0x97, (byte)0, (byte)0,
                (byte)((declared >> 8) & 0xff), (byte)(declared & 0xff));
    }
    private static byte[] chunk(int offset, byte... data) {
        byte[] p = new byte[data.length + 4];
        p[0] = (byte)(offset >>> 24);
        p[1] = (byte)(offset >>> 16);
        p[2] = (byte)(offset >>> 8);
        p[3] = (byte)offset;
        System.arraycopy(data, 0, p, 4, data.length);
        return packet(0x98, p);
    }
    private static final byte[] JPEG = new byte[] {
            (byte)0xff,(byte)0xd8,0,0x11,(byte)0xff,(byte)0xd9
    };

    @Test public void originalCompleteJpegIsAcceptedOutOfOrderAndDuplicateChunksAreIdempotent() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        assertEquals(M02sE09PhotoAssembler.Step.START, a.receive(start(6)));
        assertEquals(M02sE09PhotoAssembler.Step.CHUNK,
                a.receive(chunk(3, (byte)0x11, (byte)0xff, (byte)0xd9)));
        assertEquals(M02sE09PhotoAssembler.Step.CHUNK,
                a.receive(chunk(0, (byte)0xff, (byte)0xd8, (byte)0)));
        assertEquals(M02sE09PhotoAssembler.Step.CHUNK,
                a.receive(chunk(0, (byte)0xff, (byte)0xd8, (byte)0)));
        assertEquals(M02sE09PhotoAssembler.Step.COMPLETE,a.receive(packet(0x99)));
        assertTrue(a.hasJpegStart());
        assertTrue(a.hasJpegEnd());
        assertEquals(0,a.trailingBytes());
        assertEquals(6,a.declaredLength());
        assertEquals(6,a.receivedLength());
        assertArrayEquals(JPEG,a.result());
        assertArrayEquals(JPEG,a.rawResult());
    }

    @Test public void e09KnownFiveHundredByteTransportTailIsNotMistakenForJpegDamage() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        assertEquals(M02sE09PhotoAssembler.Step.START,a.receive(start(JPEG.length)));
        byte[] extended = Arrays.copyOf(JPEG, JPEG.length + 500);
        Arrays.fill(extended, JPEG.length, extended.length, (byte)0x71);
        for(int offset = 0; offset < extended.length; offset += 72) {
            int end = Math.min(offset+72,extended.length);
            assertEquals(M02sE09PhotoAssembler.Step.CHUNK,
                    a.receive(chunk(offset,Arrays.copyOfRange(extended,offset,end))));
        }
        assertEquals(M02sE09PhotoAssembler.Step.COMPLETE,a.receive(packet(0x99)));
        assertTrue(a.hasJpegEnd());
        assertEquals(500,a.trailingBytes());
        assertEquals(JPEG.length+500,a.receivedLength());
        assertEquals(JPEG.length,a.jpegLength());
        assertArrayEquals(JPEG,a.result());
        assertArrayEquals(extended,a.rawResult());
        assertTrue(a.safeMetrics().contains("尾随字节=500"));
    }

    @Test public void tailLengthIsMeasuredNotHardcodedToFiveHundred() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        a.receive(start(6));
        a.receive(chunk(0,(byte)0xff,(byte)0xd8,(byte)0,(byte)0x11,
                (byte)0xff,(byte)0xd9,(byte)0x12,(byte)0x13,(byte)0x14));
        assertEquals(M02sE09PhotoAssembler.Step.COMPLETE,a.receive(packet(0x99)));
        assertEquals(3,a.trailingBytes());
        assertArrayEquals(JPEG,a.result());
    }

    @Test public void jpegWithoutEoiReportsFlagAndLeavesCandidateForAndroidDecoder() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        a.receive(start(6));
        a.receive(chunk(0,(byte)0xff,(byte)0xd8,(byte)0,(byte)0x11,
                (byte)0x34,(byte)0x56));
        assertEquals(M02sE09PhotoAssembler.Step.COMPLETE,a.receive(packet(0x99)));
        assertFalse(a.hasJpegEnd());
        assertEquals(-1,a.jpegLength());
        assertEquals(-1,a.trailingBytes());
        assertEquals(6,a.result().length);
        assertFalse(a.safeMetrics().contains("EOI已找到=true"));
    }

    @Test public void rejectsMissingChunksRatherThanMakingUpImageBytes() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        assertEquals(M02sE09PhotoAssembler.Step.IGNORED,
                a.receive(chunk(0,(byte)3)));
        a.receive(start(6));
        a.receive(chunk(0,(byte)0xff,(byte)0xd8));
        a.receive(chunk(3,(byte)0x11,(byte)0xff,(byte)0xd9));
        assertEquals(M02sE09PhotoAssembler.Step.ERROR,a.receive(packet(0x99)));
        assertEquals(2,a.firstMissingOffset());
        assertTrue(a.lastError().contains("不连续"));
        assertNull(a.result());
    }

    @Test public void invalidJpegHeaderIsNotSilentlyRepaired() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        a.receive(start(6));
        a.receive(chunk(0,(byte)0x52,(byte)0x55,(byte)0,
                (byte)0x11,(byte)0xff,(byte)0xd9));
        assertEquals(M02sE09PhotoAssembler.Step.ERROR,a.receive(packet(0x99)));
        assertTrue(a.lastError().contains("SOI"));
        assertFalse(a.hasJpegStart());
        assertNull(a.result());
        assertEquals(6,a.receivedLength());
    }

    @Test public void rejectsInvalidAnnouncedSizeAndOutOfBoundsOffset() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        assertEquals(M02sE09PhotoAssembler.Step.ERROR,a.receive(start(0)));
        a.receive(start(6));
        assertEquals(M02sE09PhotoAssembler.Step.ERROR,
                a.receive(chunk(2 * 1024 * 1024 - 1,(byte)0,(byte)0)));
        assertNull(a.result());
    }

    @Test public void ignoresAA14FramesAndRejectsMalformedAA15Framing() {
        M02sE09PhotoAssembler a = new M02sE09PhotoAssembler();
        assertEquals(M02sE09PhotoAssembler.Step.IGNORED,
                a.receive(new byte[]{(byte)0xac,0x55,0,3,0x22,1,0x23}));
        byte[] bad = start(6);
        bad[bad.length-1]=0;
        assertEquals(M02sE09PhotoAssembler.Step.IGNORED,a.receive(bad));
        assertEquals(M02sE09PhotoAssembler.Step.COMPLETE,
                completeSimple(a));
    }
    private static M02sE09PhotoAssembler.Step completeSimple(M02sE09PhotoAssembler a) {
        a.receive(start(6));
        a.receive(chunk(0,JPEG));
        return a.receive(packet(0x99));
    }
}
