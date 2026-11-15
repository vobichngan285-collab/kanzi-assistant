package com.vobichngan.bluestar.m02slab;

import java.util.Arrays;

/**
 * Minimal, exact OEM Wi-Fi AP command and AA14 SSID response, from the prior
 * M02S real-world successful experiment and E09 open-source protocol.
 *
 * Default has no TAKE_PHOTO; separate explicit AP-online 0x22/0x31 experiment only.\n * No OTA method. OEM 0x44 finish commands (including media clear)
 * are in M02sOemImportPolicy and only issued after verified GET completion.
 * APP -> glasses: AB 55 00 03 39 30 69
 * glasses -> APP: AC 55 ... 25 [SSID UTF-8] checksum
 */
public final class M02sApWire {
    private M02sApWire() {}

    private static final byte[] AP_REQUEST = {
            (byte) 0xAB, 0x55, 0x00, 0x03, 0x39, 0x30, 0x69
    };
    /**
     * OEM com.starglasses.app SendCommandViaBle.takePhoto(HIGH_QUALITY).
     * Experimental ONCE per AP session ONLY after the user explicitly clicks
     * the AP-online diagnostic button. This MAY fail or disconnect Wi-Fi.
     * It is not the existing 0x22/0x30 photo-thumbnail command.
     */
    private static final byte[] AP_ONLINE_HIGH_QUALITY_SHOT = {
            (byte)0xAB, 0x55, 0x00, 0x03, 0x22, 0x31, 0x53
    };
    public static byte[] apOnlineHighQualityShot() {
        return AP_ONLINE_HIGH_QUALITY_SHOT.clone();
    }
    public static boolean authorizedApOnlineHighQualityShot(byte[] frame) {
        return Arrays.equals(AP_ONLINE_HIGH_QUALITY_SHOT,frame);
    }

    /** E09 GET_CUSTOMER is a read-only query (command 0x64, no payload). */
    private static final byte[] PROJECT_QUERY = {
            (byte)0xAB, 0x55, 0x00, 0x02, 0x64, 0x64
    };
    public static byte[] deviceProjectQuery() { return PROJECT_QUERY.clone(); }
    public static boolean authorizedReadOnlyProject(byte[] bytes) {
        return Arrays.equals(PROJECT_QUERY, bytes);
    }

    /** Passive E09 ACTION_SYNC flag 9 (optional tenth payload byte).
     * Returns -1 for absent/unverified; 0 for false; 1 for true.
     * Does not infer import mode when firmware sends only nine flags. */
    public static int actionSyncImportFlag(byte[] frame) {
        if (actionSyncPhotoFlag(frame) < 0 || frame.length < 16) return -1;
        int flag = frame[14] & 0xff;
        return flag <= 1 ? flag : -1;
    }

    /**
     * OEM 0x64 response: project code in bytes 0..3, customer in bytes 4..7.
     * Return ONLY the 4-character project code; no MAC / SSID / customer ID.
     * Profile mapping belongs to UI and is only a hint, not hardware proof.
     */
    public static String decodeReadOnlyProject(byte[] frame) {
        if (frame == null || frame.length < 14 || frame.length > 30
                || (frame[0] & 255) != 0xAC || (frame[1] & 255) != 0x55
                || (frame[4] & 255) != 0x64)
            return null;
        int len = ((frame[2] & 255) << 8) | (frame[3] & 255);
        if (len != frame.length - 4 || len < 10) return null;
        int sum = 0;
        for (int i = 4; i < frame.length - 1; i++)
            sum = (sum + (frame[i] & 0xff)) & 0xff;
        if (sum != (frame[frame.length - 1] & 0xff)) return null;
        StringBuilder project = new StringBuilder(4);
        for (int i = 5; i <= 8; i++) {
            int c = frame[i] & 0xff;
            if (c == 0) continue;
            if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')
                    && !(c >= '0' && c <= '9') && c != '-' && c != '_')
                return null;
            project.append((char)c);
        }
        return project.length() == 0 ? null : project.toString();
    }

    public static byte[] apRequest() { return AP_REQUEST.clone(); }
    public static boolean authorized(byte[] bytes) {
        return Arrays.equals(AP_REQUEST, bytes);
    }

    /** Returns only verified SSID from 0x25 response; never log the bytes. */
    public static String decodeWifiSsid(byte[] frame) {
        if (frame == null || frame.length < 7 || frame.length > 64
                || (frame[0] & 255) != 0xAC || (frame[1] & 255) != 0x55) return null;
        int payloadLen = ((frame[2] & 255) << 8) | (frame[3] & 255);
        if (payloadLen != frame.length - 4 || (frame[4] & 255) != 0x25)
            return null;
        int sum = 0;
        for (int i = 4; i < frame.length - 1; i++) sum = (sum + (frame[i] & 255)) & 255;
        if (sum != (frame[frame.length - 1] & 255)) return null;
        byte[] raw = Arrays.copyOfRange(frame, 5, frame.length - 1);
        String ssid = M02sWifiPolicy.decodeSsid(raw);
        return M02sWifiPolicy.isConnectableSsid(ssid) ? ssid : null;
    }
    /**
     * Upstream MIT E09 ACTION_SYNC(0x45) payload[0] is isTakePhoto boolean.
     * -1 = not a validated photo-state event; 0 = inactive; 1 = active.
     *
     * On M02S this is a *candidate* physical-shutter notification only:
     * it must be checked on the user's hardware, not assumed guaranteed.
     */
    public static int actionSyncPhotoFlag(byte[] frame) {
        if (frame == null || frame.length < 15 || frame.length > 64
                || (frame[0] & 255) != 0xAC || (frame[1] & 255) != 0x55)
            return -1;
        int len = ((frame[2] & 255) << 8) | (frame[3] & 255);
        if (len != frame.length - 4 || (frame[4] & 255) != 0x45
                || len < 11)
            return -1;
        int sum = 0;
        for (int i = 4; i < frame.length - 1; i++)
            sum = (sum + (frame[i] & 255)) & 255;
        if (sum != (frame[frame.length - 1] & 255)) return -1;
        int flag = frame[5] & 255;
        return flag <= 1 ? flag : -1;
    }

}
