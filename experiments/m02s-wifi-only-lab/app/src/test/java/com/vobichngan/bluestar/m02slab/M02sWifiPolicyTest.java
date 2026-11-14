package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class M02sWifiPolicyTest {
    @Test public void decodeUtf8AndRetainInMemory() {
        String value = "DIRECT-ABC-M02S";
        assertEquals(value,M02sWifiPolicy.decodeSsid(value.getBytes(StandardCharsets.UTF_8)));
        assertEquals("摄影眼镜网络",M02sWifiPolicy.decodeSsid("摄影眼镜网络".getBytes(StandardCharsets.UTF_8)));
        assertNull(M02sWifiPolicy.decodeSsid(new byte[]{(byte) 0xff, (byte) 0xfe}));
        assertNull(M02sWifiPolicy.decodeSsid(new byte[]{'a',0,'b'}));
        assertNull(M02sWifiPolicy.decodeSsid(new byte[33]));
    }
    @Test public void matchOnlyRealTargetNotPrinter() {
        String ssid = "M02S-3584";
        assertTrue(M02sWifiPolicy.matchesPeerName("DIRECT-WQ-M02S-3584",ssid));
        assertTrue(M02sWifiPolicy.matchesPeerName("M02S-3584",ssid));
        assertFalse(M02sWifiPolicy.matchesPeerName("DIRECT-92-HP M132f LaserJet",ssid));
        assertFalse(M02sWifiPolicy.matchesPeerName("DIRECT-92-HP M132f LaserJet","DIRECT"));
        assertFalse(M02sWifiPolicy.matchesPeerName(null,ssid));
        assertTrue(M02sWifiPolicy.isConnectableSsid(ssid));
        assertFalse(M02sWifiPolicy.isConnectableSsid("DIRECT"));
    }
    @Test public void logsHideIdentifiers() {
        String ssid = "DIRECT-9A-PRIVATE";
        String summary = M02sWifiPolicy.safeSsidReport(ssid);
        assertTrue(summary.contains("字节数"));
        assertFalse(summary.contains(ssid));
        assertFalse(summary.contains("12345678"));
    }
}
