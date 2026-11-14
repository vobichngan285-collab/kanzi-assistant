package com.vobichngan.bluestar.m02slab;

/**
 * AP request diagnostics without copying Android exception messages, SSID,
 * OEM pairing keys, peer addresses or arbitrary exception string content.
 */
public final class ApRequestDiagnostic {
    private ApRequestDiagnostic() { }

    public enum Stage {
        PREPARE_SPECIFIER,
        BUILD_NETWORK_REQUEST,
        REQUEST_NETWORK
    }

    public static String permissionSummary(boolean changeNetwork, boolean accessNetwork,
                                           boolean nearbyWifi, boolean fineLocation) {
        return "AP系统权限预检：" +
                "CHANGE_NETWORK_STATE=" + state(changeNetwork) + "；" +
                "ACCESS_NETWORK_STATE=" + state(accessNetwork) + "；" +
                "NEARBY_WIFI_DEVICES=" + state(nearbyWifi) + "；" +
                "ACCESS_FINE_LOCATION=" + state(fineLocation);
    }

    public static String safeFailure(Stage stage, RuntimeException exception) {
        String type = exception instanceof SecurityException ? "SecurityException"
                : exception instanceof IllegalArgumentException ? "IllegalArgumentException"
                : exception instanceof IllegalStateException ? "IllegalStateException"
                : "RuntimeException";
        String where = stage == Stage.REQUEST_NETWORK ? "提交 requestNetwork"
                : stage == Stage.BUILD_NETWORK_REQUEST ? "构造 NetworkRequest"
                : "构造 WifiNetworkSpecifier";
        return "AP系统调用失败：阶段=" + where + "；类型=" + type
                + "；不显示系统异常正文，以防泄露网络标识";
    }

    private static String state(boolean granted) {
        return granted ? "已授权" : "未授权";
    }
}
