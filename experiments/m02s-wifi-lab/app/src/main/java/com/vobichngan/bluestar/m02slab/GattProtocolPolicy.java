package com.vobichngan.bluestar.m02slab;

import java.util.Locale;

/** Pure formatting and conservative safety policy for M02S GATT diagnostic. */
public final class GattProtocolPolicy {
    private GattProtocolPolicy() { }

    public static boolean isVendorService(String uuid) {
        if (uuid == null) return false;
        String s = uuid.toLowerCase(Locale.ROOT);
        return s.equals("0000ae00-0000-1000-8000-00805f9b34fb")
                || s.equals("0000aa12-0000-1000-8000-00805f9b34fb");
    }

    public static boolean canSubscribe(int properties, boolean hasCccd) {
        // BluetoothGattCharacteristic.PROPERTY_NOTIFY (0x10), INDICATE (0x20)
        return hasCccd && (properties & 0x30) != 0;
    }

    public static String properties(int p) {
        StringBuilder out = new StringBuilder();
        if ((p & 0x02) != 0) out.append("读 ");
        if ((p & 0x04) != 0) out.append("无回执写 ");
        if ((p & 0x08) != 0) out.append("有回执写 ");
        if ((p & 0x10) != 0) out.append("通知 ");
        if ((p & 0x20) != 0) out.append("指示 ");
        if (out.length() == 0) out.append("其他/无已知能力");
        return out.toString().trim();
    }

    /** Only short, capped frames can enter a local, copyable diagnostic log. */
    public static String shortHex(byte[] data) {
        if (data == null) return "null";
        int limit = Math.min(data.length, 20);
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) hex.append(' ');
            hex.append(String.format(Locale.ROOT, "%02X", data[i] & 0xff));
        }
        if (limit < data.length) hex.append(" ... (已截断)");
        return hex.toString();
    }

    public static boolean shouldLogPayload(int count) {
        return count > 0 && count <= 40;
    }
}
