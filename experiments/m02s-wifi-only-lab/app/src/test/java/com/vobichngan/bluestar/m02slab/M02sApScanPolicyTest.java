package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class M02sApScanPolicyTest {
    private static final long NOW = 400_000_000L;
    private static M02sApScanPolicy.Beacon b(String ssid,String mac,long ageSeconds) {
        return new M02sApScanPolicy.Beacon(ssid,mac,NOW-ageSeconds*1_000_000L);
    }

    @Test public void exactSsidOnlyNotSameLengthNeighbor() {
        M02sApScanPolicy.Choice c=M02sApScanPolicy.choose("M02S-Real-AP",
                Arrays.asList(b("Other-13Chars","12:34:56:78:90:11",1),
                        b("M02S-Real-AP","12:34:56:78:90:12",2)),NOW);
        assertEquals(M02sApScanPolicy.Kind.UNIQUE,c.kind);
        assertEquals("12:34:56:78:90:12",c.uniqueBssid);
    }

    @Test public void staleAndInvalidResultsNeverCount() {
        assertEquals(M02sApScanPolicy.Kind.NOT_VISIBLE,
                M02sApScanPolicy.choose("Glass",
                    Arrays.asList(b("Glass","12:34:56:78:90:11",31),
                        b("Glass","bad_mac",1)),NOW).kind);
    }

    @Test public void sameSsidDifferentRadiosCannotPinAnArbitraryBssid() {
        M02sApScanPolicy.Choice c=M02sApScanPolicy.choose("Glass",
                Arrays.asList(b("Glass","12:34:56:78:90:11",1),
                        b("Glass","12:34:56:78:90:12",2)),NOW);
        assertEquals(M02sApScanPolicy.Kind.MULTIPLE,c.kind);
        assertNull(c.uniqueBssid);
    }

    @Test public void sameRadioDuplicateEntryCountsOnlyOnce() {
        M02sApScanPolicy.Choice c=M02sApScanPolicy.choose("Glass",
                Arrays.asList(b("Glass","12:34:56:78:90:11",1),
                        b("Glass","12:34:56:78:90:11",2)),NOW);
        assertEquals(M02sApScanPolicy.Kind.UNIQUE,c.kind);
    }

    @Test public void retryRequiresFreshTargetAndIsLimitedToOne() {
        M02sApScanPolicy.Choice good=M02sApScanPolicy.choose("Glass",
                Collections.singletonList(b("Glass","12:34:56:78:90:11",1)),NOW);
        M02sApScanPolicy.Choice none=M02sApScanPolicy.choose("Glass",
                Collections.emptyList(),NOW);
        assertTrue(M02sApScanPolicy.allowChangedStrategyRetry(1,false,good));
        assertTrue(M02sApScanPolicy.allowChangedStrategyRetry(1,true,good));
        assertFalse(M02sApScanPolicy.allowChangedStrategyRetry(2,false,good));
        assertFalse(M02sApScanPolicy.allowChangedStrategyRetry(1,false,none));
    }

    @Test public void wifiInfoSsidQuotationNormalization() {
        assertEquals("Glass",M02sApScanPolicy.unquoteWifiSsid("\"Glass\""));
        assertEquals("Glass",M02sApScanPolicy.unquoteWifiSsid("Glass"));
        assertEquals("",M02sApScanPolicy.unquoteWifiSsid(null));
    }
}
