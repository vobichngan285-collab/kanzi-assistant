package com.vobichngan.bluestar.m02slab;

import org.junit.Test;
import static org.junit.Assert.*;

public final class M02sBleWireTest {
    @Test public void photoAndWifiPacketsMatchOfficialDexBytecode() {
        assertArrayEquals(new byte[]{(byte)0xab, 0x55, 0, 3, 0x22, 0x30, 0x52},
                M02sBleWire.photoThumbnail());
        assertArrayEquals(new byte[]{(byte)0xab, 0x55, 0, 3, 0x39, 0x31, 0x6a},
                M02sBleWire.wifiP2pInfo());
        assertArrayEquals(new byte[]{(byte)0xab, 0x55, 0, 3, 0x39, 0x30, 0x69},
                M02sBleWire.wifiApInfo());
    }

    @Test public void allowOnlyThreeKnownPackets() {
        assertTrue(M02sBleWire.isExactAuthorizedCommand(M02sBleWire.photoThumbnail()));
        assertTrue(M02sBleWire.isExactAuthorizedCommand(M02sBleWire.wifiP2pInfo()));
        assertTrue(M02sBleWire.isExactAuthorizedCommand(M02sBleWire.wifiApInfo()));
        assertFalse(M02sBleWire.isExactAuthorizedCommand(
                new byte[]{(byte)0xab, 0x55, 0, 3, 0x22, 0x31, 0x53}));
        assertFalse(M02sBleWire.isExactAuthorizedCommand(new byte[]{0x22, 0x30}));
    }

    @Test public void parseActualSingleCaptureNotificationSequence() {
        byte[] cameraAck={(byte)0xac,0x55,0,3,0x22,1,0x23};
        byte[] progressStart={(byte)0xac,0x55,0,12,0x45,1,0,0,0,0,0,0,0,0,0,0x46};
        byte[] photoCount={(byte)0xac,0x55,0,4,0x42,0,4,0x46};
        byte[] progressEnd={(byte)0xac,0x55,0,12,0x45,0,0,0,0,0,0,0,0,0,0,0x45};
        for(byte[] data:new byte[][]{cameraAck,progressStart,photoCount,progressEnd}) {
            assertTrue(M02sBleWire.parseIncoming(data).checksumOk);
        }
        assertEquals(0x22,M02sBleWire.parseIncoming(cameraAck).command);
        assertEquals(0x42,M02sBleWire.parseIncoming(photoCount).command);
        assertTrue(M02sBleWire.parseIncoming(progressStart).safeSummary().contains("状态候选"));
    }

    @Test public void wifiResponsePayloadIsAlwaysRedacted() {
        byte[] msg={(byte)0xac,0x55,0,5,0x39, 'S', 'S', 'I', (byte)(0x39+'S'+'S'+'I')};
        M02sBleWire.ParsedFrame frame=M02sBleWire.parseIncoming(msg);
        assertNotNull(frame);
        String result=frame.safeSummary();
        assertTrue(result.contains("已隐藏"));
        assertFalse(result.contains("53")); // raw SSID byte must never be shown
        assertFalse(result.contains("安全预览"));
    }

    @Test public void malformedFramesAreNotTrustworthy() {
        assertNull(M02sBleWire.parseIncoming(new byte[]{(byte)0xac,0x55,0,3,0x22,1}));
        assertNull(M02sBleWire.parseIncoming(new byte[]{(byte)0xab,0x55,0,3,0x22,1,0x23}));
        assertNull(M02sBleWire.parseIncoming(null));
        byte[] wrong={(byte)0xac,0x55,0,3,0x22,1,0x00};
        assertFalse(M02sBleWire.parseIncoming(wrong).checksumOk);
    }
}
