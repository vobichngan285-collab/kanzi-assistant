package com.vobichngan.bluestar.m02slab;

import java.util.Arrays;

/**
 * Minimal, exact OEM Wi-Fi AP command and AA14 SSID response, from the prior
 * M02S real-world successful experiment and E09 open-source protocol.
 *
 * NO TAKE_PHOTO method; no file delete, clear, or OTA commands.
 * APP -> glasses: AB 55 00 03 39 30 69
 * glasses -> APP: AC 55 ... 25 [SSID UTF-8] checksum
 */
public final class M02sApWire {
    private M02sApWire() {}

    private static final byte[] AP_REQUEST = {
            (byte) 0xAB, 0x55, 0x00, 0x03, 0x39, 0x30, 0x69
    };
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
}
