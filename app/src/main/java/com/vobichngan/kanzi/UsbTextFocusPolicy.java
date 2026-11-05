package com.vobichngan.kanzi;

/**
 * Passive, camera-independent policy for experimental USB AF lock.
 * Caller may issue hardware AF commands ONLY after checking UVC support.
 * No OCR text, camera pixels, or video buffers are modified.
 */
public final class UsbTextFocusPolicy {
    private boolean locked;
    private long lockedScene = -1;
    private long lockedAt;
    private long searchStartedAt;
    private long candidateScene = -1;
    private long candidateSince;

    public synchronized void onOpened(long nowMs) {
        locked = false;
        lockedScene = -1;
        lockedAt = 0;
        searchStartedAt = nowMs;
        candidateScene = -1;
        candidateSince = 0;
    }

    /** Only after actual OCR boxes, decent confidence and usable true frame edges. */
    public synchronized boolean canLock(int boxes, double confidence,
                                        int brightness, double edgeStrength,
                                        long frameScene, long currentScene, long nowMs) {
        return !locked && boxes >= 2 && confidence >= 0.58 &&
            brightness >= 45 && brightness <= 225 && edgeStrength >= 8.0 &&
            frameScene == currentScene && nowMs - searchStartedAt >= 650;
    }

    public synchronized void markLocked(long scene, long nowMs) {
        locked = true;
        lockedScene = scene;
        lockedAt = nowMs;
        candidateScene = -1;
    }

    /**
     * Large scene change must persist for 600ms, and a newly locked lens
     * must remain locked for 1200ms. Small shake never causes per-frame AF writes.
     */
    public synchronized boolean shouldRelease(long scene, long nowMs) {
        if (!locked) return false;
        if (scene == lockedScene) {
            candidateScene = -1;
            candidateSince = 0;
            return false;
        }
        if (candidateScene != scene) {
            candidateScene = scene;
            candidateSince = nowMs;
            return false;
        }
        return nowMs - lockedAt >= 1200 && nowMs - candidateSince >= 600;
    }

    public synchronized void markReleased(long nowMs) {
        locked = false;
        lockedScene = -1;
        searchStartedAt = nowMs;
        candidateScene = -1;
        candidateSince = 0;
    }

    public synchronized boolean isLocked() { return locked; }
}
