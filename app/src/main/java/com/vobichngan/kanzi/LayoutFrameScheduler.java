package com.vobichngan.kanzi;

/**
 * Pure decision policy for reusing an existing on-device layout result.
 * Caller supplies monotonic milliseconds and measured frame displacement.
 * No camera hooks, network access, or model changes.
 */
public final class LayoutFrameScheduler {
    public enum Action { ANALYZE, REUSE, GEOMETRY_FALLBACK }
    private final long minIntervalMs;
    private final long maxAgeMs;
    private final float maxTranslationFraction;
    private long lastAnalysisMs = -1;
    private boolean hasCache;
    private boolean inFlight;

    public LayoutFrameScheduler(long minIntervalMs, long maxAgeMs, float maxTranslationFraction) {
        if (minIntervalMs < 0 || maxAgeMs <= minIntervalMs ||
            !(maxTranslationFraction > 0f && maxTranslationFraction < 1f))
            throw new IllegalArgumentException("Invalid scheduler thresholds");
        this.minIntervalMs=minIntervalMs;
        this.maxAgeMs=maxAgeMs;
        this.maxTranslationFraction=maxTranslationFraction;
    }

    /**
     * translationFraction: measured frame movement normalized to frame diagonal.
     * unknown movement (NaN) is never considered safe for cache reuse.
     * A cache is valid only if the caller keeps region coordinates aligned.
     */
    public synchronized Action decide(long nowMs, float translationFraction, boolean sceneChanged) {
        if (nowMs < 0) throw new IllegalArgumentException("negative monotonic timestamp");
        if (lastAnalysisMs >= 0 && nowMs < lastAnalysisMs) {
            invalidate();
        }
        boolean moved = !Float.isFinite(translationFraction) ||
            translationFraction > maxTranslationFraction;
        boolean stale = !hasCache || nowMs-lastAnalysisMs >= maxAgeMs;
        if (sceneChanged || moved) hasCache=false;
        if (!hasCache || stale) {
            if (inFlight) return Action.GEOMETRY_FALLBACK;
            // Rate limit re-analysis only if the prior result exists and remains usable.
            if (lastAnalysisMs >= 0 && nowMs-lastAnalysisMs < minIntervalMs)
                return Action.GEOMETRY_FALLBACK;
            inFlight=true;
            return Action.ANALYZE;
        }
        return Action.REUSE;
    }

    public synchronized void completed(long nowMs, boolean success) {
        if (!inFlight) throw new IllegalStateException("No analysis in flight");
        if (nowMs < 0) throw new IllegalArgumentException("negative timestamp");
        inFlight=false;
        lastAnalysisMs=nowMs;
        hasCache=success;
    }

    public synchronized void invalidate() {
        hasCache=false;
        inFlight=false;
        lastAnalysisMs=-1;
    }
}
