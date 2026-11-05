package com.vobichngan.kanzi;
public final class UsbTextFocusPolicySmokeTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        UsbTextFocusPolicy p = new UsbTextFocusPolicy();
        p.onOpened(1000);
        check(!p.canLock(3,0.95,120,20,4,4,1500),"settling delay");
        check(!p.canLock(0,0.95,120,20,4,4,1900),"requires real text boxes");
        check(!p.canLock(3,0.30,120,20,4,4,1900),"reject low OCR confidence");
        check(!p.canLock(3,0.95,12,20,4,4,1900),"reject too dark");
        check(!p.canLock(3,0.95,120,2,4,4,1900),"reject blurry/no edges");
        check(!p.canLock(3,0.95,120,20,4,5,1900),"reject stale scene");
        check(p.canLock(3,0.95,120,20,4,4,1900),"confident actual text");
        p.markLocked(4,1900);
        check(!p.canLock(3,0.95,120,20,4,4,3000),"only one lock per target");
        check(!p.shouldRelease(4,3100),"same page stable");
        check(!p.shouldRelease(5,3200),"changed page not instant");
        check(!p.shouldRelease(5,3500),"hysteresis");
        check(p.shouldRelease(5,3820),"persistent new page releases");
        p.markReleased(3830);
        check(!p.canLock(3,0.95,120,20,5,5,4000),"refocus settling");
        check(p.canLock(3,0.95,120,20,5,5,4600),"new page can lock again");
        System.out.println("USB_EXPERIMENT_AF_LOCK_POLICY=PASS");
    }
}
