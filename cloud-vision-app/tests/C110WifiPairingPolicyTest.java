package com.vobich.bluestar.cloudprobe;
public final class C110WifiPairingPolicyTest {
    private static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        ok(C110WifiPairingPolicy.validTarget("C110_2e53","12345678"),"valid");
        ok(!C110WifiPairingPolicy.validTarget("","12345678"),"empty ssid");
        ok(!C110WifiPairingPolicy.validTarget("C110_2e53","short"),"short password");
        ok(!C110WifiPairingPolicy.validTarget("A".repeat(33),"12345678"),"ssid too long");
        ok(C110WifiPairingPolicy.shouldAutoConnect(true,false,false,"C110_2e53","12345678"),"auto once");
        ok(!C110WifiPairingPolicy.shouldAutoConnect(false,false,false,"C110_2e53","12345678"),"auto disabled");
        ok(!C110WifiPairingPolicy.shouldAutoConnect(true,true,false,"C110_2e53","12345678"),"no duplicate");
        ok(!C110WifiPairingPolicy.shouldAutoConnect(true,false,true,"C110_2e53","12345678"),"no reconnect if connected");
        ok(!C110WifiPairingPolicy.shouldAutoConnect(true,false,false,"","12345678"),"first time must pair");
        ok(C110WifiPairingPolicy.commitNewTarget(true,true),"verified pair");
        ok(!C110WifiPairingPolicy.commitNewTarget(false,true),"reject wrong wifi without overwriting");
        ok(!C110WifiPairingPolicy.commitNewTarget(true,false),"auto does not overwrite pairing");
        ok(C110WifiPairingPolicy.effectiveBssid("00:11:22:33:44:55",false).isEmpty(),"no stale bssid on auto");
        ok(C110WifiPairingPolicy.effectiveBssid("00:11:22:33:44:55",true)
            .equals("00:11:22:33:44:55"),"use fresh manually selected bssid");
        System.out.println("PASS: C110 first verified pairing, auto reconnect, no stale BSSID, replacement isolation");
    }
}
