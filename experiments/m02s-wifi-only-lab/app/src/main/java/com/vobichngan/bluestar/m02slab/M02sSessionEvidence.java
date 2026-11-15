package com.vobichngan.bluestar.m02slab;

/**
 * READ-ONLY diagnostics of the difference between a phone network release
 * and firmware recovery. Never generates, authorizes or transmits a BLE frame.
 *
 * Grounded in the user's original com.starglasses.app APK:
 * Ldp.onGetDataSuccess assigns .l = list.size(), and full-original
 * download completion increments .o after local file handling. It is unsafe
 * to equate .l to the number of JPEG-only filtered candidates in our app.
 */
public final class M02sSessionEvidence {
    public enum OemFinishBranch {
        UNKNOWN_OR_INVALID, ZERO_COMPLETED, PART_COMPLETED, ALL_COMPLETED
    }

    /** For analysis only: NEVER map this result directly to BLE writes. */
    public static OemFinishBranch theoreticalOemBranch(int rawListEntries,
                                                       int originalsCompleted) {
        if (rawListEntries <= 0 || originalsCompleted < 0
                || originalsCompleted > rawListEntries) {
            return OemFinishBranch.UNKNOWN_OR_INVALID;
        }
        if (originalsCompleted == 0) return OemFinishBranch.ZERO_COMPLETED;
        return originalsCompleted == rawListEntries
                ? OemFinishBranch.ALL_COMPLETED : OemFinishBranch.PART_COMPLETED;
    }

    private int lastImportFlag = -1;
    private int lastRawListEntries = -1;
    private int lastEligibleImages = -1;
    private int releasesAfterVerifiedPhoto;
    private boolean phoneRequestReleased;
    private boolean importInactiveObservedAfterRelease;
    private boolean freshShutterObservedAfterRelease;

    public void onImportFlag(int flag) {
        if (flag != 0 && flag != 1) return;
        lastImportFlag = flag;
        if (phoneRequestReleased && flag == 0)
            importInactiveObservedAfterRelease = true;
    }

    public void onRawOemList(int rawEntries, int eligibleImages) {
        lastRawListEntries = rawEntries;
        lastEligibleImages = eligibleImages;
    }

    public void onPhoneNetworkReleased(boolean jpegWasVerified) {
        if (!jpegWasVerified) return;
        releasesAfterVerifiedPhoto++;
        phoneRequestReleased = true;
        importInactiveObservedAfterRelease = false;
        freshShutterObservedAfterRelease = false;
        // BLE may still report isImport=1. Neither releasing the NetworkCallback
        // nor seeing isImport=0 subsequently proves a second AP association.
    }

    public void onPhysicalShutterEnded() {
        if (phoneRequestReleased) freshShutterObservedAfterRelease = true;
    }

    public void onNextManualImportStarted() {
        // Keep previous release evidence until another successful JPEG.
        // Starting a request is NOT proof of AP association.
    }

    public int getLastImportFlag() { return lastImportFlag; }
    public int getLastRawListEntries() { return lastRawListEntries; }
    public int getLastEligibleImages() { return lastEligibleImages; }
    public int getVerifiedReleasedRounds() { return releasesAfterVerifiedPhoto; }
    public boolean isPhoneRequestReleased() { return phoneRequestReleased; }
    public boolean sawImportInactiveAfterRelease() {
        return importInactiveObservedAfterRelease;
    }
    public boolean sawPhysicalShutterAfterRelease() {
        return freshShutterObservedAfterRelease;
    }

    /** A second successful *different* original photo is the sole repeatable
     * import evidence here; it still does not prove future rounds will work.
     */
    public boolean hasObservedSecondSuccessfulRound() {
        return releasesAfterVerifiedPhoto >= 2;
    }

    public String statusText() {
        if (!phoneRequestReleased) return "本会话尚无下载完成后的手机网络释放记录";
        String flag = lastImportFlag < 0 ? "未获得" : String.valueOf(lastImportFlag);
        return "手机已释放NetworkCallback；镜端退出未确认；"
                + "最近BLE isImport=" + flag
                + "；释放后观察到isImport=0=" + importInactiveObservedAfterRelease
                + "；释放后观察到新快门结束=" + freshShutterObservedAfterRelease
                + "；不同高清原图成功轮次=" + releasesAfterVerifiedPhoto;
    }
}
