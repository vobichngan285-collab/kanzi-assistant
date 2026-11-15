package com.vobichngan.bluestar.m02slab;
import static org.junit.Assert.*;
import org.junit.Test;

public final class M02sWifiRecoveryPolicyTest {
    @Test public void onceOnlyAfterFreshBssidPinnedRequestFails() {
        assertTrue(M02sWifiRecoveryPolicy.canTrySsidOnly(true,false,true,true,true,false));
        assertFalse(M02sWifiRecoveryPolicy.canTrySsidOnly(true,true,true,true,true,false));
        assertFalse(M02sWifiRecoveryPolicy.canTrySsidOnly(false,false,true,true,true,false));
        assertFalse(M02sWifiRecoveryPolicy.canTrySsidOnly(true,false,false,true,true,false));
        assertFalse(M02sWifiRecoveryPolicy.canTrySsidOnly(true,false,true,false,true,false));
        assertFalse(M02sWifiRecoveryPolicy.canTrySsidOnly(true,false,true,true,false,false));
        assertFalse(M02sWifiRecoveryPolicy.canTrySsidOnly(true,false,true,true,true,true));
        assertTrue(M02sWifiRecoveryPolicy.hasAttemptRemaining(false));
        assertFalse(M02sWifiRecoveryPolicy.hasAttemptRemaining(true));
    }
}
