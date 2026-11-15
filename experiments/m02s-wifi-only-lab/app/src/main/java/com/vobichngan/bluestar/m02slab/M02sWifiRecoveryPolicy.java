package com.vobichngan.bluestar.m02slab;

/**
 * Exactly one SSID-only contingency for an OEM AP session initially pinned to
 * a fresh, unique BSSID. This is NOT a new BLE/AP command or a re-scan loop.
 * Caller must receive Android's onUnavailable (the failed request is released).
 */
public final class M02sWifiRecoveryPolicy {
    private M02sWifiRecoveryPolicy() { }

    public static boolean canTrySsidOnly(boolean initialRequestPinnedToBssid,
            boolean alreadyTried, boolean apWriteAcknowledged, boolean hasVerifiedSsid,
            boolean deviceBleReady, boolean currentNetworkReady) {
        return initialRequestPinnedToBssid && !alreadyTried
                && apWriteAcknowledged && hasVerifiedSsid && deviceBleReady
                && !currentNetworkReady;
    }

    /** Only one exact-SSID fallback is allowed for this manually started import. */
    public static boolean hasAttemptRemaining(boolean previouslyTried) {
        return !previouslyTried;
    }
}
