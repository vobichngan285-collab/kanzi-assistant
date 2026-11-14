package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

public final class M02sApRecoveryPolicyTest {
    private static final String RADIO = "12:34:56:78:9a:bc";

    @Test public void onlyAfterFirstFailedSsidRequestAndNewUniqueBeacon() {
        assertTrue(M02sApRecoveryPolicy.allowOnePinnedRetry(
                1, false, M02sApReadinessPolicy.Kind.FRESH_UNIQUE, RADIO));
        assertFalse(M02sApRecoveryPolicy.allowOnePinnedRetry(
                0, false, M02sApReadinessPolicy.Kind.FRESH_UNIQUE, RADIO));
        assertFalse(M02sApRecoveryPolicy.allowOnePinnedRetry(
                2, true, M02sApReadinessPolicy.Kind.FRESH_UNIQUE, RADIO));
        assertFalse(M02sApRecoveryPolicy.allowOnePinnedRetry(
                1, true, M02sApReadinessPolicy.Kind.FRESH_UNIQUE, RADIO));
    }

    @Test public void staleAmbiguousAndInvalidBeaconsNeverAuthorizeRetry() {
        assertFalse(M02sApRecoveryPolicy.allowOnePinnedRetry(
                1, false, M02sApReadinessPolicy.Kind.CACHE_ONLY, RADIO));
        assertFalse(M02sApRecoveryPolicy.allowOnePinnedRetry(
                1, false, M02sApReadinessPolicy.Kind.NO_TARGET, RADIO));
        assertFalse(M02sApRecoveryPolicy.allowOnePinnedRetry(
                1, false, M02sApReadinessPolicy.Kind.FRESH_AMBIGUOUS, RADIO));
        assertFalse(M02sApRecoveryPolicy.allowOnePinnedRetry(
                1, false, M02sApReadinessPolicy.Kind.FRESH_UNIQUE, null));
        assertFalse(M02sApRecoveryPolicy.allowOnePinnedRetry(
                1, false, M02sApReadinessPolicy.Kind.FRESH_UNIQUE,
                "00:00:00:00:00:00"));
    }
}
