package com.vobichngan.bluestar.m02slab;

import org.junit.Test;
import static org.junit.Assert.*;

public class GattDiagnosticPolicyTest {
    @Test public void statusOneIsNotMisreportedAsAuthentication() {
        String msg = GattDiagnosticPolicy.statusDescription(1);
        assertTrue(msg.contains("未确定"));
        assertFalse(msg.contains("身份"));
        assertFalse(msg.contains("拒绝配对"));
    }
    @Test public void onlySuccessfulConnectedTransitionCanDiscoverServices() {
        assertTrue(GattDiagnosticPolicy.shouldDiscover(0, 2));
        assertFalse(GattDiagnosticPolicy.shouldDiscover(1, 2));
        assertFalse(GattDiagnosticPolicy.shouldDiscover(0, 0));
        assertFalse(GattDiagnosticPolicy.shouldDiscover(133, 0));
        assertTrue(GattDiagnosticPolicy.shouldStopOnFailure(1));
        assertFalse(GattDiagnosticPolicy.shouldStopOnFailure(0));
    }
    @Test public void unexpectedCodesStayDiagnosticNotSpeculative() {
        assertTrue(GattDiagnosticPolicy.statusDescription(139).contains("未映射"));
        assertTrue(GattDiagnosticPolicy.statusDescription(133).contains("133"));
    }
}
