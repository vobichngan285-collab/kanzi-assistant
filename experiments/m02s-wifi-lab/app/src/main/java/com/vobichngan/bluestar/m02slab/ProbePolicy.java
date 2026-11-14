package com.vobichngan.bluestar.m02slab;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Pure, testable policies for safe local-only HTTP probing. */
public final class ProbePolicy {
    private ProbePolicy() {}

    public static String checkedLocalHost(String raw) {
        if (raw == null) throw new IllegalArgumentException("请填写本地眼镜 IP");
        String s = raw.trim();
        String[] pieces = s.split("\\.", -1);
        if (pieces.length != 4) throw new IllegalArgumentException("仅允许 192.168.x.x 本地 IPv4");
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (pieces[i].isEmpty() || pieces[i].length() > 3 ||
                    !pieces[i].matches("[0-9]+")) {
                throw new IllegalArgumentException("本地 IP 格式错误");
            }
            octets[i] = Integer.parseInt(pieces[i]);
            if (octets[i] > 255) throw new IllegalArgumentException("IP 地址范围错误");
        }
        if (octets[0] != 192 || octets[1] != 168 || octets[2] == 0 ||
                octets[3] == 0 || octets[3] == 255) {
            throw new IllegalArgumentException("仅允许私有网段 192.168.x.x 的眼镜地址");
        }
        return String.format(Locale.ROOT, "%d.%d.%d.%d",
                octets[0], octets[1], octets[2], octets[3]);
    }

    public static String fingerprint(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String stateLabel(boolean networkAvailable, int code, int durationMs) {
        if (!networkAvailable) return "未连接/没有有效网络";
        if (code == 200) return "HTTP 200 · " + durationMs + " ms";
        if (code > 0) return "HTTP " + code + " · " + durationMs + " ms";
        return "网络有响应，但文件列表接口未成功";
    }
}
