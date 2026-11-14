package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;

public final class M02sApWireTest {
    private static byte[] response(int command, byte[] payload) {
        byte[] frame = new byte[payload.length + 6];
        frame[0] = (byte)0xAC; frame[1] = 0x55;
        frame[2] = (byte)((payload.length + 2) >>> 8);
        frame[3] = (byte)(payload.length + 2);
        frame[4] = (byte)command;
        System.arraycopy(payload, 0, frame, 5, payload.length);
        int checksum = command;
        for (byte b : payload) checksum += b & 255;
        frame[frame.length - 1] = (byte)checksum;
        return frame;
    }
    @Test public void onlyExactKnownWifiApCommandAllowed() {
        byte[] command = M02sApWire.apRequest();
        assertArrayEquals(new byte[]{(byte)0xAB,0x55,0,3,0x39,0x30,0x69},command);
        assertTrue(M02sApWire.authorized(command));
        command[4] = 0x22;  // remote shutter is blocked
        assertFalse(M02sApWire.authorized(command));
        command = M02sApWire.apRequest();
        command[5] = 0x31;  // no P2P on this experiment
        assertFalse(M02sApWire.authorized(command));
        assertFalse(M02sApWire.authorized(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,0,0x74}));
    }
    @Test public void parsesOnlyVerifiedOemSsidFrame() {
        byte[] frame = response(0x25,"M02S_TEST_WIFI".getBytes(StandardCharsets.UTF_8));
        assertEquals("M02S_TEST_WIFI",M02sApWire.decodeWifiSsid(frame));
        frame[frame.length - 1] ^= 0x01;
        assertNull(M02sApWire.decodeWifiSsid(frame));
        assertNull(M02sApWire.decodeWifiSsid(response(0x22,new byte[]{1})));
    }
    @Test public void rejectsMalformedAndShortSsid() {
        byte[] frame = response(0x25,"x".getBytes(StandardCharsets.UTF_8));
        assertNull(M02sApWire.decodeWifiSsid(frame));
        assertNull(M02sApWire.decodeWifiSsid(new byte[]{(byte)0xAC,0x55,0,3,0x25,0x01}));
        assertNull(M02sApWire.decodeWifiSsid(null));
        frame = response(0x25,"M02S_TEST_WIFI".getBytes(StandardCharsets.UTF_8));
        frame[3] = 0x00;
        assertNull(M02sApWire.decodeWifiSsid(frame));
    }
    @Test public void e09ActionSyncPhotoFlagChecksChecksumAndState() {
        byte[] payload = new byte[9];
        payload[0] = 1;
        byte[] event = response(0x45, payload);
        assertEquals(1, M02sApWire.actionSyncPhotoFlag(event));
        payload[0] = 0;
        assertEquals(0, M02sApWire.actionSyncPhotoFlag(response(0x45, payload)));
        event[5] = 9;
        assertEquals(-1, M02sApWire.actionSyncPhotoFlag(event));
        event = response(0x45, new byte[9]);
        event[event.length-1] ^= 1;
        assertEquals(-1, M02sApWire.actionSyncPhotoFlag(event));
        assertEquals(-1, M02sApWire.actionSyncPhotoFlag(response(0x25,payload)));
        assertEquals(-1, M02sApWire.actionSyncPhotoFlag(response(0x45,new byte[]{1})));
        assertEquals(-1, M02sApWire.actionSyncPhotoFlag(null));
    }

    @Test public void nonClearFinishIsExplicitlyExactAndDeleteNeverAllowed() {
        byte[] allowed=M02sApWire.nonClearFinishRequest();
        assertArrayEquals(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,0x01,0x75},allowed);
        assertTrue(M02sApWire.authorizedNonClearFinish(allowed));
        // Original AP command validator remains strictly AP-only.
        assertFalse(M02sApWire.authorized(allowed));
        assertFalse(M02sApWire.authorizedNonClearFinish(M02sApWire.apRequest()));
        // This is the dangerous clear/delete variant, unconditionally refused.
        byte[] delete=(byte[])allowed.clone();
        delete[6]=0x00;
        delete[7]=0x74;
        assertFalse(M02sApWire.authorizedNonClearFinish(delete));
        byte[] partial=(byte[])allowed.clone();
        partial[5]=0x31; partial[7]=0x76;
        assertFalse(M02sApWire.authorizedNonClearFinish(partial));
        allowed[5]=(byte)0x99;
        assertFalse(M02sApWire.authorizedNonClearFinish(allowed));
        assertArrayEquals(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,0x01,0x75},
                M02sApWire.nonClearFinishRequest());
        assertFalse(M02sApWire.authorizedNonClearFinish(null));
    }
}
