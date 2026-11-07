package com.vobich.bluestar.cloudprobe;

/** RC15 unplug/plug/manual override scenarios, without Android dependencies. */
public final class UsbAutoSwitchPolicyTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        check(UsbAutoSwitchPolicy.useUsbOnConnect(true,true,true,true,false),
            "plug UVC while on phone");
        // RC16 source reconciliation, not only a freshly delivered onConnect.
        check(UsbAutoSwitchPolicy.useUsbOnConnect(true,true,true,true,false),
            "cold USB-started Activity after onResume must switch");
        check(UsbAutoSwitchPolicy.useUsbOnConnect(true,true,true,true,false),
            "repeated Android onNewIntent must switch if still on phone");
        check(!UsbAutoSwitchPolicy.useUsbOnConnect(true,true,true,false,false),
            "already USB: avoid reopen");
        check(!UsbAutoSwitchPolicy.useUsbOnConnect(false,true,true,true,false),
            "auto switch disabled");
        check(!UsbAutoSwitchPolicy.useUsbOnConnect(true,false,true,true,false),
            "background attach must not switch foreground sources");
        check(!UsbAutoSwitchPolicy.useUsbOnConnect(true,true,false,true,false),
            "noncamera device ignored");
        check(!UsbAutoSwitchPolicy.useUsbOnConnect(true,true,true,true,true),
            "manual phone selection respected until next physical detach");
        check(!UsbAutoSwitchPolicy.useUsbOnConnect(true,true,false,true,false),
            "detached before queued probe runs must not switch");
        check(!UsbAutoSwitchPolicy.useUsbOnConnect(true,false,true,true,false),
            "onNewIntent while paused must wait for foreground resume");
        check(UsbAutoSwitchPolicy.keepManualOverrideForPhysicalDevice(true,3,
            new int[]{3,11}),"manual phone remains for same connected device");
        check(!UsbAutoSwitchPolicy.keepManualOverrideForPhysicalDevice(true,3,
            new int[]{4,11}),"new physical attach clears old manual override");
        check(!UsbAutoSwitchPolicy.keepManualOverrideForPhysicalDevice(true,3,
            new int[]{}),"device gone clears old manual override");
        check(UsbAutoSwitchPolicy.usePhoneOnDetach(true,true,true),
            "unplug active USB -> phone");
        check(!UsbAutoSwitchPolicy.usePhoneOnDetach(true,true,false),
            "unplug unrelated USB -> keep selected");
        check(!UsbAutoSwitchPolicy.usePhoneOnDetach(false,true,true),
            "user can disable auto fallback");
        check(!UsbAutoSwitchPolicy.usePhoneOnDetach(true,false,true),
            "already phone: no duplicate transition");
        check(UsbAutoSwitchPolicy.allowNativeUsbOpen(true,true),"selected USB can open");
        check(!UsbAutoSwitchPolicy.allowNativeUsbOpen(true,false),
            "native UVC must not run while user views phone camera");
        check(!UsbAutoSwitchPolicy.allowNativeUsbOpen(false,true),
            "inactive Activity must not reopen UVC");
        System.out.println("RC15_AUTO_USB_SWITCH_MANUAL_OVERRIDE_AND_ATTACH=PASS");
    }
}
