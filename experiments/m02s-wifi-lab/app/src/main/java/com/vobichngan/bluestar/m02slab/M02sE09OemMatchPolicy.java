package com.vobichngan.bluestar.m02slab;

/**
 * Single experimental difference against v0.1.12-livecheck:
 * the decompiled Eyevue OEM WifiApConnector for non-Harmony, non-ColorOS,
 * non-Xiaomi devices uses a literal PatternMatcher and the target BSSID
 * found by Wi-Fi scan. This lab enables that strategy ONLY for E09 APP_LIVE
 * on the user's Vivo phone; ordinary photo-AP behavior is untouched.
 *
 * No timed retries, new BLE commands, hidden fallback, or guessed BSSIDs.
 */
public final class M02sE09OemMatchPolicy {
    private M02sE09OemMatchPolicy() { }

    public enum Choice { UNCHANGED_SSID_ONLY, OEM_LITERAL_PATTERN_PINNED, TARGET_NOT_READY }

    public static Choice choose(
            boolean e09LiveRequested, String deviceBrand,
            M02sApReadinessPolicy.Observation currentObservation,
            String stableBssid, long stableSinceMs, long nowMs) {
        if (!e09LiveRequested || !"vivo".equalsIgnoreCase(deviceBrand)) {
            return Choice.UNCHANGED_SSID_ONLY;
        }
        if (stableBssid == null || !M02sApScanPolicy.validBssid(stableBssid)
                || !M02sApReadinessPolicy.canConnect(
                        currentObservation, stableBssid, stableSinceMs, nowMs)) {
            return Choice.TARGET_NOT_READY;
        }
        return Choice.OEM_LITERAL_PATTERN_PINNED;
    }
}
