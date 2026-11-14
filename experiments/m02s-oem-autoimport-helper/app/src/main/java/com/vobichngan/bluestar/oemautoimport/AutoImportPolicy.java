package com.vobichngan.bluestar.oemautoimport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal UI gate; no BLE, Wi-Fi, storage, or HTTP code.
 * This is a pilot, not a replacement or decompile/repackage of OEM code.
 */
public final class AutoImportPolicy {
    private static final Pattern PENDING = Pattern.compile("发现\\s*([0-9]+)\\s*个文件");
    public static final String OEM_PACKAGE = "com.starglasses.app";

    private AutoImportPolicy() { }

    public static boolean isOemPackage(CharSequence pkg) {
        return pkg != null && OEM_PACKAGE.contentEquals(pkg);
    }

    public static int pendingCount(CharSequence text) {
        if (text == null) return -1;
        Matcher m = PENDING.matcher(text);
        if (!m.find()) return -1;
        try {
            int n = Integer.parseInt(m.group(1));
            return n > 0 && n <= 10000 ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean isExactImportButton(CharSequence text) {
        return text != null && "导入".equals(text.toString().trim());
    }

    public static boolean allowAutoClick(boolean enabled, boolean oemForeground,
            boolean observedEmptyFirst, int pendingCount, boolean importVisible,
            boolean waitingForStableUi, boolean alreadyClickedThisCandidate) {
        return enabled && oemForeground && observedEmptyFirst
                && pendingCount == 1 && importVisible
                && !waitingForStableUi && !alreadyClickedThisCandidate;
    }
}
