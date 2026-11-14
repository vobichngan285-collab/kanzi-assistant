package com.vobichngan.bluestar.m02slab;

/**
 * Experimental, bounded Android AP recovery; not a vendor firmware command.
 * The original exact-SSID request must have failed once, and a second,
 * strictly newer unique beacon must permit a different (BSSID-pinned)
 * Wi-Fi request. No retries after the second onUnavailable.
 */
public final class M02sApRecoveryPolicy {
    private M02sApRecoveryPolicy() { }

    public static boolean allowOnePinnedRetry(int requestCount, boolean priorPinned,
            M02sApReadinessPolicy.Kind freshKind, String verifiedBssid) {
        return requestCount == 1
                && !priorPinned
                && freshKind == M02sApReadinessPolicy.Kind.FRESH_UNIQUE
                && M02sApScanPolicy.validBssid(verifiedBssid);
    }
}
