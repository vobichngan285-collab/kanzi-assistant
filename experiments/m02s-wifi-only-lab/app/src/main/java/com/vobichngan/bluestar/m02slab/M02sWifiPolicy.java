package com.vobichngan.bluestar.m02slab;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Wi-Fi target policy: decode the ORIGINAL OEM 0x25 SSID payload (UTF-8) in
 * memory only. Never print the network identifier, password or raw bytes.
 * Official P2P matcher uses WifiP2pDevice.deviceName.contains(ssid).
 */
public final class M02sWifiPolicy {
    private M02sWifiPolicy() { }

    public static String decodeSsid(byte[] raw) {
        if (raw == null || raw.length < 1 || raw.length > 32) return null;
        final String ssid;
        try {
            ssid = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException ex) {
            return null;
        }
        if (ssid.isEmpty()) return null;
        for (int i = 0; i < ssid.length(); i++) {
            if (Character.isISOControl(ssid.charAt(i))) return null;
        }
        return ssid;
    }

    /** Exact OEM contains() rule with minimum specificity for safety. */
    public static boolean matchesPeerName(String deviceName, String trustedSsid) {
        return deviceName != null && trustedSsid != null
                && trustedSsid.length() >= 8 && deviceName.contains(trustedSsid);
    }

    public static boolean isConnectableSsid(String ssid) {
        if (ssid == null || ssid.length() < 8) return false;
        byte[] bytes = ssid.getBytes(StandardCharsets.UTF_8);
        return bytes.length <= 32 && ssid.equals(decodeSsid(bytes));
    }

    public static String safeSsidReport(String ssid) {
        if (ssid == null) return "SSID 未识别";
        return "SSID 已从眼镜蓝牙响应解析，UTF-8 字节数="
                + ssid.getBytes(StandardCharsets.UTF_8).length
                + "（名称和凭证已隐藏）";
    }
}
