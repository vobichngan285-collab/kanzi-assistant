package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ApRequestDiagnosticTest {
    @Test public void explicitPermissionStateIsVisible() {
        String text = ApRequestDiagnostic.permissionSummary(true,true,true,true);
        assertTrue(text.contains("CHANGE_NETWORK_STATE=已授权"));
        assertTrue(text.contains("ACCESS_NETWORK_STATE=已授权"));
        assertTrue(text.contains("NEARBY_WIFI_DEVICES=已授权"));
        assertTrue(text.contains("ACCESS_FINE_LOCATION=已授权"));
        assertTrue(ApRequestDiagnostic.permissionSummary(false,true,true,true)
                .contains("CHANGE_NETWORK_STATE=未授权"));
    }

    @Test public void allThreeStagesAreDistinguishable() {
        assertTrue(ApRequestDiagnostic.safeFailure(
                ApRequestDiagnostic.Stage.PREPARE_SPECIFIER,
                new SecurityException("secret")).contains("WifiNetworkSpecifier"));
        assertTrue(ApRequestDiagnostic.safeFailure(
                ApRequestDiagnostic.Stage.BUILD_NETWORK_REQUEST,
                new IllegalArgumentException("secret")).contains("NetworkRequest"));
        assertTrue(ApRequestDiagnostic.safeFailure(
                ApRequestDiagnostic.Stage.REQUEST_NETWORK,
                new SecurityException("secret")).contains("提交 requestNetwork"));
    }

    @Test public void neverLeakExceptionMessageOrWifiSecrets() {
        String secret = "PRIVATE-SSID-123 / AP PASSWORD=12345678 / device MAC=00:11:22";
        for (ApRequestDiagnostic.Stage stage : ApRequestDiagnostic.Stage.values()) {
            String diagnostic = ApRequestDiagnostic.safeFailure(
                    stage, new SecurityException(secret));
            assertTrue(diagnostic.contains("SecurityException"));
            assertFalse(diagnostic.contains("PRIVATE-SSID-123"));
            assertFalse(diagnostic.contains("12345678"));
            assertFalse(diagnostic.contains("00:11:22"));
        }
    }
}
