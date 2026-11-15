package com.vobichngan.bluestar.m02slab;

import java.util.Arrays;

/**
 * OEM com.starglasses.app Ldp.stopP2pWifiConnect() branch selection.
 * The user's eyewear media is disposable and they explicitly authorized
 * the OEM media clearing branch. It may erase photos on the eyewear.
 *
 * This class only builds/validates the exact OEM frames; GATT remains under
 * the active, one-shot manually started AP session.
 */
public final class M02sOemImportPolicy {
    private M02sOemImportPolicy() { }

    public static final int MAX_OEM_BATCH_IMAGES = 24;
    public enum Branch { ZERO, PARTIAL, FULL }

    public static Branch branch(int totalEntries, int completed) {
        if (totalEntries < 0 || completed < 0 || completed > totalEntries)
            throw new IllegalArgumentException("invalid OEM completion count");
        if (totalEntries == 0 || completed == 0) return Branch.ZERO;
        if (completed == totalEntries) return Branch.FULL;
        if (completed > 255) throw new IllegalArgumentException("partial count over BLE byte");
        return Branch.PARTIAL;
    }

    /** Download every original only for an all-image, reasonably bounded OEM list. */
    public static boolean canCompleteWholeList(int totalEntries, int imageCandidates) {
        return totalEntries > 0 && totalEntries <= MAX_OEM_BATCH_IMAGES
                && imageCandidates == totalEntries;
    }

    public static byte[] finishFrame(int totalEntries, int completed) {
        Branch b = branch(totalEntries, completed);
        int mode = b == Branch.PARTIAL ? 0x31 : 0x30;
        int value = b == Branch.ZERO ? 1 : b == Branch.FULL ? 0 : completed;
        return new byte[]{(byte)0xAB,0x55,0x00,0x04,0x44,(byte)mode,
                (byte)value,(byte)((0x44+mode+value)&255)};
    }

    public static boolean authorizedFinish(byte[] frame) {
        if (frame == null || frame.length != 8
                || (frame[0]&255)!=0xAB || (frame[1]&255)!=0x55
                || frame[2]!=0 || frame[3]!=4 || frame[4]!=0x44)
            return false;
        int mode=frame[5]&255, value=frame[6]&255;
        if (mode==0x30 && value!=0 && value!=1) return false;
        if (mode==0x31 && value<1) return false;
        if (mode!=0x30 && mode!=0x31) return false;
        return (frame[7]&255)==((0x44+mode+value)&255);
    }
}
