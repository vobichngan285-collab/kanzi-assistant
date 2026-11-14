package com.vobichngan.bluestar.m02slab;

/**
 * Human readable, conservative GATT status labels.
 * Codes are platform/hardware dependent; never treat code 1 as proof of
 * authentication failure, firmware denial, or a specific Bluetooth problem.
 */
public final class GattDiagnosticPolicy {
    private GattDiagnosticPolicy() {}

    public static String statusDescription(int code) {
        switch (code) {
            case 0: return "成功";
            case 1: return "非成功代码1；具体原因未确定";
            case 8: return "连接超时或监督超时（常见HCI码，需日志核实）";
            case 19: return "对端结束连接（常见HCI码，需日志核实）";
            case 22: return "本机结束连接（常见HCI码，需日志核实）";
            case 133: return "常见Android GATT通用错误133";
            case 257: return "Android GATT_FAILURE（通用错误）";
            default: return "未映射状态，请结合设备日志判断";
        }
    }

    public static boolean shouldDiscover(int code, int newState) {
        // BluetoothProfile.STATE_CONNECTED = 2. No Android dependency in tests.
        return code == 0 && newState == 2;
    }

    public static boolean shouldStopOnFailure(int code) {
        return code != 0;
    }
}
