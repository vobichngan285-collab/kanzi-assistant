package com.vobich.bluestar.cloudprobe;
/** Pure Java rules: remember only an identity-verified camera network. */
final class C110WifiPairingPolicy {
    private C110WifiPairingPolicy(){}
    static boolean validTarget(String ssid,String password){
        return ssid!=null&&!ssid.trim().isEmpty()&&ssid.length()<=32&&
            password!=null&&password.length()>=8&&password.length()<=63;
    }
    static boolean shouldAutoConnect(boolean enabled,boolean attempted,
                                     boolean connected,String ssid,String password){
        return enabled&&!attempted&&!connected&&validTarget(ssid,password);
    }
    static boolean commitNewTarget(boolean verified,boolean newSelection){
        return verified&&newSelection;
    }
    /** Saved SSID must not be pinned to an old camera BSSID. */
    static String effectiveBssid(String bssid,boolean newSelection){
        return newSelection&&bssid!=null?bssid:"";
    }
}
