package com.vobichngan.bluestar.m02slab;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public final class M02sE09OemMatchPolicyTest {
    private static final String SSID = "M02S-TEST-3584";
    private static final String BSSID = "02:11:22:33:44:55";
    private static final long REQUEST_US = 2_000_000L;
    private static final long NOW_US = 4_000_000L;

    private M02sApReadinessPolicy.Observation fresh() {
        return M02sApReadinessPolicy.inspect(SSID, Arrays.asList(
                new M02sApScanPolicy.Beacon(SSID, BSSID, 3_200_000L)),
                REQUEST_US, NOW_US);
    }

    @Test public void vivoLiveUsesLiteralPatternAndUniquePostBleBssid() {
        assertEquals(M02sE09OemMatchPolicy.Choice.OEM_LITERAL_PATTERN_PINNED,
                M02sE09OemMatchPolicy.choose(true, "vivo", fresh(),
                        BSSID, 5000L, 6900L));
        assertEquals(M02sE09OemMatchPolicy.Choice.OEM_LITERAL_PATTERN_PINNED,
                M02sE09OemMatchPolicy.choose(true, "VIVO", fresh(),
                        BSSID, 5000L, 7000L));
    }

    @Test public void ordinaryPhotoApAndNonVivoAreUnaffected() {
        assertEquals(M02sE09OemMatchPolicy.Choice.UNCHANGED_SSID_ONLY,
                M02sE09OemMatchPolicy.choose(false, "vivo", fresh(),
                        BSSID, 5000L, 6900L));
        assertEquals(M02sE09OemMatchPolicy.Choice.UNCHANGED_SSID_ONLY,
                M02sE09OemMatchPolicy.choose(true, "oppo", fresh(),
                        BSSID, 5000L, 6900L));
    }

    @Test public void noStaleCachedAmbiguousOrMismatchedTargetCanBePinned() {
        M02sApReadinessPolicy.Observation cached =
                M02sApReadinessPolicy.inspect(SSID, Arrays.asList(
                        new M02sApScanPolicy.Beacon(SSID, BSSID, REQUEST_US)),
                        REQUEST_US, NOW_US);
        M02sApReadinessPolicy.Observation ambiguous =
                M02sApReadinessPolicy.inspect(SSID, Arrays.asList(
                        new M02sApScanPolicy.Beacon(SSID, BSSID, 3_200_000L),
                        new M02sApScanPolicy.Beacon(SSID, "02:11:22:33:44:66", 3_200_000L)),
                        REQUEST_US, NOW_US);
        for (M02sApReadinessPolicy.Observation o :
                Arrays.asList(cached, ambiguous)) {
            assertEquals(M02sE09OemMatchPolicy.Choice.TARGET_NOT_READY,
                    M02sE09OemMatchPolicy.choose(true, "vivo", o,
                            BSSID, 5000L, 6900L));
        }
        assertEquals(M02sE09OemMatchPolicy.Choice.TARGET_NOT_READY,
                M02sE09OemMatchPolicy.choose(true, "vivo", fresh(),
                        "02:11:22:33:44:66", 5000L, 6900L));
        assertEquals(M02sE09OemMatchPolicy.Choice.TARGET_NOT_READY,
                M02sE09OemMatchPolicy.choose(true, "vivo", fresh(),
                        BSSID, 6000L, 6500L));
        assertEquals(M02sE09OemMatchPolicy.Choice.TARGET_NOT_READY,
                M02sE09OemMatchPolicy.choose(true, "vivo", fresh(),
                        "00:00:00:00:00:00", 5000L, 6900L));
    }

    @Test public void missingApBeaconIsNotEquivalentToRtspUnsupported() {
        M02sApReadinessPolicy.Observation none =
                M02sApReadinessPolicy.inspect(SSID,
                        Collections.emptyList(), REQUEST_US, NOW_US);
        assertEquals(M02sE09OemMatchPolicy.Choice.TARGET_NOT_READY,
                M02sE09OemMatchPolicy.choose(true, "vivo", none,
                        BSSID, 5000L, 6900L));
    }
}
