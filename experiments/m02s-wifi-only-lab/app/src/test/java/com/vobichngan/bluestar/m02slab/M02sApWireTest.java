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
    @Test public void allOem44FinishCommandsAreRejectedByAuditProtocol() {
        byte[] partial = new byte[]{(byte)0xAB,0x55,0,4,0x44,0x31,1,0x76};
        byte[] fullClear = new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,0,0x74};
        byte[] zero = new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,1,0x75};
        for (byte[] frame : new byte[][]{partial, fullClear, zero}) {
            assertFalse(M02sApWire.authorized(frame));
            assertFalse(M02sApWire.authorizedReadOnlyProject(frame));
        }
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

    @Test public void optionalE09IsImportFlagIsStrictAndAbsentIsUnknown() {
        byte[] nine = new byte[9];
        nine[0] = 1;
        assertEquals(-1, M02sApWire.actionSyncImportFlag(response(0x45, nine)));
        byte[] ten = new byte[10];
        ten[0] = 1;
        ten[9] = 1;
        assertEquals(1, M02sApWire.actionSyncImportFlag(response(0x45, ten)));
        assertEquals(1, M02sApWire.actionSyncPhotoFlag(response(0x45, ten)));
        ten[9] = 0;
        assertEquals(0, M02sApWire.actionSyncImportFlag(response(0x45, ten)));
        ten[9] = 2;
        assertEquals(-1, M02sApWire.actionSyncImportFlag(response(0x45, ten)));
        ten[9] = 1;
        byte[] corrupt = response(0x45,ten);
        corrupt[corrupt.length - 1] ^= 1;
        assertEquals(-1, M02sApWire.actionSyncImportFlag(corrupt));
        assertEquals(-1, M02sApWire.actionSyncImportFlag(response(0x64,ten)));
        assertEquals(-1, M02sApWire.actionSyncImportFlag(null));
    }

    @Test public void onlyExactReadOnlyProjectQueryAllowedAndReplyIsValidated() {
        byte[] query = M02sApWire.deviceProjectQuery();
        assertArrayEquals(new byte[]{(byte)0xAB,0x55,0,2,0x64,0x64},query);
        assertTrue(M02sApWire.authorizedReadOnlyProject(query));
        assertFalse(M02sApWire.authorized(query)); // AP whitelist remains AP only
        assertFalse(M02sApWire.authorizedReadOnlyProject(M02sApWire.apRequest()));
        byte[] mutated = query.clone();
        mutated[4]=0x44;
        assertFalse(M02sApWire.authorizedReadOnlyProject(mutated));
        assertFalse(M02sApWire.authorizedReadOnlyProject(
                new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,0,0x74}));
        query[4]=0x39;
        assertArrayEquals(new byte[]{(byte)0xAB,0x55,0,2,0x64,0x64},
                M02sApWire.deviceProjectQuery());
        assertFalse(M02sApWire.authorizedReadOnlyProject(null));
        byte[] projectResponse=response(0x64,new byte[]{
                'T',0,0,0,'D','E','M','O'});
        assertEquals("T",M02sApWire.decodeReadOnlyProject(projectResponse));
        projectResponse[projectResponse.length-1] ^= 1;
        assertNull(M02sApWire.decodeReadOnlyProject(projectResponse));
        byte[] notProject=response(0x25,new byte[]{
                'T',0,0,0,'D','E','M','O'});
        assertNull(M02sApWire.decodeReadOnlyProject(notProject));
        byte[] invalidProject=response(0x64,new byte[]{
                1,0,0,0,'D','E','M','O'});
        assertNull(M02sApWire.decodeReadOnlyProject(invalidProject));
        assertNull(M02sApWire.decodeReadOnlyProject(response(0x64,new byte[]{'T'})));
    }
}
