package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

/** Only the uniquely BLE-verified SSID can be requested once, at most. */
public final class M02sApConnectPolicyTest {
    private static final String VERIFIED = "M02S_EXACT_AP";

    @Test public void scanThrottleDoesNotForceFailureAfterDeadline() {
        assertTrue(M02sApConnectPolicy.allowExactSsidFallback(true, VERIFIED, false,
                M02sApReadinessPolicy.Kind.NO_TARGET, 12_000_000L));
        assertTrue(M02sApConnectPolicy.allowExactSsidFallback(true, VERIFIED, false,
                M02sApReadinessPolicy.Kind.CACHE_ONLY, 12_200_000L));
        assertTrue(M02sApConnectPolicy.allowExactSsidFallback(true, VERIFIED, false,
                M02sApReadinessPolicy.Kind.FRESH_UNIQUE, 12_200_000L));
    }

    @Test public void tooEarlyOrAnotherRequestInFlightIsNeverAllowed() {
        assertFalse(M02sApConnectPolicy.allowExactSsidFallback(true, VERIFIED, false,
                M02sApReadinessPolicy.Kind.NO_TARGET, 11_999_999L));
        assertFalse(M02sApConnectPolicy.allowExactSsidFallback(true, VERIFIED, true,
                M02sApReadinessPolicy.Kind.NO_TARGET, 30_000_000L));
    }

    @Test public void noBleAckOrVerifiedSsidBlocksFallback() {
        assertFalse(M02sApConnectPolicy.allowExactSsidFallback(false, VERIFIED, false,
                M02sApReadinessPolicy.Kind.NO_TARGET, 13_000_000L));
        assertFalse(M02sApConnectPolicy.allowExactSsidFallback(true, null, false,
                M02sApReadinessPolicy.Kind.NO_TARGET, 13_000_000L));
        assertFalse(M02sApConnectPolicy.allowExactSsidFallback(true, "401", false,
                M02sApReadinessPolicy.Kind.NO_TARGET, 13_000_000L));
    }

    @Test public void multipleConflictingFreshRadiosBlockFallback() {
        assertFalse(M02sApConnectPolicy.allowExactSsidFallback(true, VERIFIED, false,
                M02sApReadinessPolicy.Kind.FRESH_AMBIGUOUS, 14_000_000L));
    }
}
