package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

public final class GattProtocolPolicyTest {
    @Test public void onlyObservedVendorServicesAreEligible() {
        assertTrue(GattProtocolPolicy.isVendorService("0000ae00-0000-1000-8000-00805f9b34fb"));
        assertTrue(GattProtocolPolicy.isVendorService("0000AA12-0000-1000-8000-00805F9B34FB"));
        assertFalse(GattProtocolPolicy.isVendorService("0000180f-0000-1000-8000-00805f9b34fb"));
        assertFalse(GattProtocolPolicy.isVendorService(null));
    }

    @Test public void descriptorAndFlagsAreBothRequired() {
        assertFalse(GattProtocolPolicy.canSubscribe(0x10, false));
        assertTrue(GattProtocolPolicy.canSubscribe(0x10, true));
        assertTrue(GattProtocolPolicy.canSubscribe(0x20, true));
        assertFalse(GattProtocolPolicy.canSubscribe(0x08, true));
        assertTrue(GattProtocolPolicy.properties(0x1a).contains("通知"));
        assertTrue(GattProtocolPolicy.properties(0x1a).contains("读"));
        assertTrue(GattProtocolPolicy.properties(0x1a).contains("有回执写"));
    }

    @Test public void hexIsLimitedAndUnsigned() {
        assertEquals("00 7F FF", GattProtocolPolicy.shortHex(new byte[]{0,127,(byte)255}));
        byte[] secret = new byte[150];
        secret[0] = (byte)0x9a;
        String output = GattProtocolPolicy.shortHex(secret);
        assertTrue(output.startsWith("9A"));
        assertTrue(output.contains("截断"));
        assertTrue(output.length() < 90);
        assertTrue(GattProtocolPolicy.shouldLogPayload(40));
        assertFalse(GattProtocolPolicy.shouldLogPayload(41));
    }
}
