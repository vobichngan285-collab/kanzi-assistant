package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

public final class M02sApOnlineShotWireTest {
    @Test public void onlyOem22HighQuality31IsWhitelisted() {
        byte[] exact=M02sApWire.apOnlineHighQualityShot();
        assertArrayEquals(new byte[] {(byte)0xAB,0x55,0x00,0x03,0x22,0x31,0x53},exact);
        assertTrue(M02sApWire.authorizedApOnlineHighQualityShot(exact));
        exact[5]=0x30; // Previous 0x22/0x30 AP-online test was already negative
        assertFalse(M02sApWire.authorizedApOnlineHighQualityShot(exact));
        exact[5]=0x31;
        exact[6]=0;
        assertFalse(M02sApWire.authorizedApOnlineHighQualityShot(exact));
        assertFalse(M02sApWire.authorizedApOnlineHighQualityShot(
                M02sApWire.apRequest()));
        assertFalse(M02sApWire.authorizedApOnlineHighQualityShot(null));
    }

    @Test public void photoResponsesRequireCompleteOemChecksum() {
        assertEquals(0x22,M02sApWire.validatedResponseCommand(
                new byte[]{(byte)0xAC,0x55,0x00,0x02,0x22,0x22}));
        assertEquals(0x42,M02sApWire.validatedResponseCommand(
                new byte[]{(byte)0xAC,0x55,0x00,0x02,0x42,0x42}));
        assertEquals(-1,M02sApWire.validatedResponseCommand(
                new byte[]{(byte)0xAC,0x55,0x00,0x02,0x22,0x23}));
        assertEquals(-1,M02sApWire.validatedResponseCommand(
                new byte[]{(byte)0xAB,0x55,0x00,0x02,0x22,0x22}));
    }
}
