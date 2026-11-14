package com.vobichngan.bluestar.m02slab;

import java.util.List;

/**
 * ScanResult.timestamp is elapsed-time microseconds, not wall-clock time.
 * A cached result (even one only a few seconds old) must not be described as
 * evidence that the eyewear AP appeared AFTER the BLE AP information request.
 *
 * A fresh beacon still does not prove the AP will accept authentication.
 */
public final class M02sApReadinessPolicy {
    private M02sApReadinessPolicy() { }

    public enum Kind { NO_TARGET, CACHE_ONLY, FRESH_UNIQUE, FRESH_AMBIGUOUS }

    public static final class Observation {
        public final Kind kind;
        public final int cachedCount;
        public final int freshCount;
        public final String uniqueBssid;
        public final long newestFreshTimestampUs;

        Observation(Kind k, int cached, int fresh, String bssid, long latest) {
            kind = k;
            cachedCount = cached;
            freshCount = fresh;
            uniqueBssid = bssid;
            newestFreshTimestampUs = latest;
        }
    }

    public static Observation inspect(String trustedSsid,
            List<M02sApScanPolicy.Beacon> beacons, long requestedAtUs, long nowUs) {
        if (trustedSsid == null || trustedSsid.isEmpty() || beacons == null
                || requestedAtUs <= 0 || nowUs <= 0 || nowUs < requestedAtUs) {
            return new Observation(Kind.NO_TARGET, 0, 0, null, 0);
        }
        int old = 0;
        int fresh = 0;
        String firstRadio = null;
        boolean multiple = false;
        long latest = 0;
        for (M02sApScanPolicy.Beacon b : beacons) {
            if (b == null || !trustedSsid.equals(b.ssid) ||
                    !M02sApScanPolicy.validBssid(b.bssid) || b.scannedAtUs <= 0
                    || b.scannedAtUs > nowUs) continue;
            // Treat every matching pre-request beacon as cached even if <30s old.
            // Post-request observations must also be recent (<10s).
            if (b.scannedAtUs <= requestedAtUs || nowUs - b.scannedAtUs > 10_000_000L) {
                old++;
                continue;
            }
            fresh++;
            latest = Math.max(latest, b.scannedAtUs);
            if (firstRadio == null) firstRadio = b.bssid;
            else if (!firstRadio.equalsIgnoreCase(b.bssid)) multiple = true;
        }
        if (fresh == 0) return new Observation(
                old > 0 ? Kind.CACHE_ONLY : Kind.NO_TARGET, old, 0, null, 0);
        if (multiple) return new Observation(Kind.FRESH_AMBIGUOUS,
                old, fresh, null, latest);
        return new Observation(Kind.FRESH_UNIQUE,
                old, fresh, firstRadio, latest);
    }

    /** Settling is a delay, not proof of multiple independent scans. */
    public static boolean canConnect(Observation observation,
            String firstBssid, long firstObservedAtMs, long nowMs) {
        return observation != null
                && observation.kind == Kind.FRESH_UNIQUE
                && firstBssid != null
                && firstBssid.equalsIgnoreCase(observation.uniqueBssid)
                && firstObservedAtMs > 0
                && nowMs >= firstObservedAtMs
                && nowMs - firstObservedAtMs >= 1800L;
    }
}
