package com.vobichngan.bluestar.m02slab;

import java.util.Arrays;

/**
 * Minimal, exact OEM Wi-Fi AP command and AA14 SSID response, from the prior
 * M02S real-world successful experiment and E09 open-source protocol.
 *
 * NO TAKE_PHOTO method; no file delete, clear, or OTA commands.
 * Optional E09-derived 0x44/0x30/0x01 NON_CLEAR finish requires explicit
 * opt-in and a separate exact whitelist; never 0x44/0x30/0x00.
 * APP -> glasses: AB 55 00 03 39 30 69
 * glasses -> APP: AC 55 ... 25 [SSID UTF-8] checksum
 */
public final class M02sApWire {
    private M02sApWire() {}

    private static final byte[] AP_REQUEST = {
            (byte) 0xAB, 0x55, 0x00, 0x03, 0x39, 0x30, 0x69
    };
    /**
     * Research-only E09 OEM session-close signal, explicitly NOT CLEAR.
     * Based on iec.java stopP2pWifiConnect and E09 MIT Constants.swift.
     * Frame: AB 55 00 04 44 30 01 75 (checksum 44+30+01=75).
     *
     * This is NOT physically verified on M02S. Only emit on explicit
     * experimental opt-in / user tap. NEVER expose 44 30 00 (delete).
     */
    private static final byte[] FINISH_NOT_CLEAR = {
            (byte)0xAB, 0x55, 0x00, 0x04, 0x44, 0x30, 0x01, 0x75
    };
    public static byte[] nonClearFinishRequest() { return FINISH_NOT_CLEAR.clone(); }
    public static boolean authorizedNonClearFinish(byte[] frame) {
        return Arrays.equals(FINISH_NOT_CLEAR, frame);
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
