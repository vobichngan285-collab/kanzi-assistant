package com.vobich.bluestar.cloudprobe;

/** Pure policy: device connect never overrides an intentional phone selection. */
final class UsbAutoSwitchPolicy {
    private UsbAutoSwitchPolicy(){}
    static boolean useUsbOnConnect(boolean autoEnabled,boolean foreground,
                                   boolean uvcDevice,boolean usingPhone,
                                   boolean manualPhoneOverride){
        return autoEnabled&&foreground&&uvcDevice&&usingPhone&&!manualPhoneOverride;
    }
    static boolean keepManualOverrideForPhysicalDevice(boolean manual,
                                                        int originalDeviceId,
                                                        int[] connectedDeviceIds){
        if(!manual||originalDeviceId<0||connectedDeviceIds==null)return false;
        for(int id:connectedDeviceIds)if(id==originalDeviceId)return true;
        return false;
    }
    static boolean usePhoneOnDetach(boolean autoEnabled,boolean usingUsb,
                                   boolean detachedActiveUsb){
        return autoEnabled&&usingUsb&&detachedActiveUsb;
    }
    static boolean allowNativeUsbOpen(boolean foreground,boolean usingUsb){
        return foreground&&usingUsb;
    }
}
