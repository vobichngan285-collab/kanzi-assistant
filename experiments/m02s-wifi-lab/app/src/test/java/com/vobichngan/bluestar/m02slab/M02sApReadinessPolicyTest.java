package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class M02sApReadinessPolicyTest {
    static final long REQ=100_000_000L;
    static final long NOW=103_000_000L;
    static M02sApScanPolicy.Beacon beacon(String ssid,String bssid,long ts) {
        return new M02sApScanPolicy.Beacon(ssid,bssid,ts);
    }

    @Test public void previousScanDoesNotMeanPostBleHotspotReady() {
        M02sApReadinessPolicy.Observation o = M02sApReadinessPolicy.inspect(
            "Target",Collections.singletonList(beacon("Target","ab:cd:ef:00:11:22",REQ-1)),
            REQ,NOW);
        assertEquals(M02sApReadinessPolicy.Kind.CACHE_ONLY,o.kind);
        assertEquals(1,o.cachedCount);
        assertFalse(M02sApReadinessPolicy.canConnect(o,"ab:cd:ef:00:11:22",101000,103000));
    }

    @Test public void newScanMatchingOnlyExactBleSsidAfterRequest() {
        M02sApReadinessPolicy.Observation o = M02sApReadinessPolicy.inspect(
            "Target",Arrays.asList(
                beacon("OtherA","ab:cd:ef:00:11:23",REQ+1000),
                beacon("Target","ab:cd:ef:00:11:22",REQ+500_000)),
            REQ,NOW);
        assertEquals(M02sApReadinessPolicy.Kind.FRESH_UNIQUE,o.kind);
        assertEquals("ab:cd:ef:00:11:22",o.uniqueBssid);
        assertFalse(M02sApReadinessPolicy.canConnect(o,o.uniqueBssid,102000,103000));
        assertTrue(M02sApReadinessPolicy.canConnect(o,o.uniqueBssid,101000,103000));
    }

    @Test public void differentRadiosDoNotCountAsStableUnique() {
        M02sApReadinessPolicy.Observation o = M02sApReadinessPolicy.inspect(
            "Target",Arrays.asList(
                beacon("Target","ab:cd:ef:00:11:22",REQ+500_000),
                beacon("Target","ab:cd:ef:00:11:24",REQ+800_000)),
            REQ,NOW);
        assertEquals(M02sApReadinessPolicy.Kind.FRESH_AMBIGUOUS,o.kind);
        assertNull(o.uniqueBssid);
    }

    @Test public void oldOrFutureTimestampCannotCountAsNewScan() {
        M02sApReadinessPolicy.Observation o = M02sApReadinessPolicy.inspect(
            "Target",Arrays.asList(
                beacon("Target","ab:cd:ef:00:11:22",REQ),
                beacon("Target","ab:cd:ef:00:11:22",NOW+1)),
            REQ,NOW);
        assertEquals(M02sApReadinessPolicy.Kind.CACHE_ONLY,o.kind);
    }

    @Test public void freshObservationOlderThanTenSecondsIsNotCurrent() {
        M02sApReadinessPolicy.Observation o = M02sApReadinessPolicy.inspect(
            "Target",Collections.singletonList(beacon(
                "Target","ab:cd:ef:00:11:22",100_000_001)),
            99_000_000,112_000_000);
        assertEquals(M02sApReadinessPolicy.Kind.CACHE_ONLY,o.kind);
    }

    @Test public void noBeaconsNeverTriggersConnection() {
        M02sApReadinessPolicy.Observation o=M02sApReadinessPolicy.inspect(
            "Target",Collections.emptyList(),REQ,NOW);
        assertEquals(M02sApReadinessPolicy.Kind.NO_TARGET,o.kind);
        assertFalse(M02sApReadinessPolicy.canConnect(o,null,1,2000));
    }
}
