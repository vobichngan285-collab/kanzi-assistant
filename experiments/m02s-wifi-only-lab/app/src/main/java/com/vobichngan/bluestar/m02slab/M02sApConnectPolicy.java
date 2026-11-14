package com.vobichngan.bluestar.m02slab;

/**
 * One-shot AP connection decision policy.
 *
 * A BLE-confirmed SSID is only a target identifier, NOT a proof of Wi-Fi
 * availability. Android's requestNetwork and then the original OEM HTTP
 * endpoint must still prove the target. Never guess an SSID or use an old
 * scan result as evidence of a new beacon.
 */
public final class M02sApConnectPolicy {
    public static final long SCAN_WAIT_LIMIT_US = 12_000_000L;

    private M02sApConnectPolicy() { }

    public static boolean allowExactSsidFallback(
            boolean apWriteAcknowledged,
            String bleConfirmedSsid,
            boolean alreadyRequested,
            M02sApReadinessPolicy.Kind observation,
            long elapsedUs) {
        return apWriteAcknowledged
                && M02sWifiPolicy.isConnectableSsid(bleConfirmedSsid)
                && !alreadyRequested
                && observation != M02sApReadinessPolicy.Kind.FRESH_AMBIGUOUS
                && elapsedUs >= SCAN_WAIT_LIMIT_US;
    }
}
