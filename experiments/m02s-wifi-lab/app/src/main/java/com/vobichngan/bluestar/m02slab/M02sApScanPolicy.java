package com.vobichngan.bluestar.m02slab;

import java.util.List;
import java.util.Locale;

/**
 * Pure-Java, data-minimal subset of the OEM's target-AP scan selection:
 * accept only the exact BLE-confirmed SSID, only recently scanned beacons,
 * and pin BSSID only when one unambiguous target radio is visible.
 * Never log SSIDs/BSSIDs or guess devices from string lengths.
 */
public final class M02sApScanPolicy {
    private M02sApScanPolicy() { }

    static final long MAX_SCAN_AGE_US = 30_000_000L;

    public static final class Beacon {
        public final String ssid;
        public final String bssid;
        public final long scannedAtUs;

        public Beacon(String ssid, String bssid, long scannedAtUs) {
            this.ssid = ssid;
            this.bssid = bssid;
            this.scannedAtUs = scannedAtUs;
        }
    }

    public enum Kind { NOT_VISIBLE, UNIQUE, MULTIPLE }

    public static final class Choice {
        public final Kind kind;
        public final String uniqueBssid;
        public final int matchedCount;

        Choice(Kind kind, String bssid, int count) {
            this.kind = kind;
            this.uniqueBssid = bssid;
            this.matchedCount = count;
        }
    }

    public static Choice choose(String trustedSsid, List<Beacon> beacons, long nowUs) {
        if (trustedSsid == null || trustedSsid.isEmpty() || beacons == null) {
            return new Choice(Kind.NOT_VISIBLE, null, 0);
        }
        String only = null;
        int count = 0;
        for (Beacon b : beacons) {
            if (b == null || !trustedSsid.equals(b.ssid)
                    || !validBssid(b.bssid) || b.scannedAtUs <= 0
                    || nowUs < b.scannedAtUs
                    || nowUs - b.scannedAtUs > MAX_SCAN_AGE_US) continue;
            if (only == null) only = b.bssid;
            else if (only.equalsIgnoreCase(b.bssid)) continue;
            count++;
        }
        if (count == 0) return new Choice(Kind.NOT_VISIBLE, null, 0);
        if (count == 1) return new Choice(Kind.UNIQUE, only, 1);
        return new Choice(Kind.MULTIPLE, null, count);
    }

    public static boolean validBssid(String bssid) {
        return bssid != null && bssid.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")
                && !bssid.equalsIgnoreCase("ff:ff:ff:ff:ff:ff")
                && !bssid.equalsIgnoreCase("00:00:00:00:00:00");
    }

    /**
     * One bounded recovery is allowed only when the exact target AP is actually
     * visible and switching between BSSID-pin and SSID-only would change the
     * Android request. Never repeat a BLE shutter or vendor Wi-Fi command.
     */
    public static boolean allowChangedStrategyRetry(
            int attempts, boolean priorPinned, Choice refreshed) {
        if (attempts != 1 || refreshed == null) return false;
        return refreshed.kind == Kind.UNIQUE;
    }

    /** Remove Android's optional quotation marks from WifiInfo.getSSID(). */
    public static String unquoteWifiSsid(String ssid) {
        if (ssid == null) return "";
        return ssid.length() >= 2 && ssid.charAt(0) == '"'
                && ssid.charAt(ssid.length() - 1) == '"'
                ? ssid.substring(1, ssid.length() - 1) : ssid;
    }
}
