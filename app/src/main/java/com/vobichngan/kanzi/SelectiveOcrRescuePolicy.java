package com.vobichngan.kanzi;

import java.util.List;

/**
 * Cheap decision-only gate for experimental OCR rescue. No Bitmap allocation, ONNX call,
 * frame queue or camera access is allowed here. The caller must opt in explicitly.
 *
 * IMPORTANT: This is a diagnostic policy, NOT a real-time extra inference executor.
 * A native ONNX call cannot be stopped by checking the clock AFTER it returns.
 * Do not wire additional recognition into the USB frame thread until bounded
 * off-thread execution and Android P50/P95 measurements have been validated.
 */
public final class SelectiveOcrRescuePolicy {
    public enum Action { NONE, REVIEW_SEGMENTATION, CANDIDATE_STRETCH }
    public enum Reason {
        DISABLED, FAST_SCAN, UNSTABLE_FRAME, NO_TIME_BUDGET, COOLDOWN,
        NO_VALID_CANDIDATE, POSSIBLE_MERGED_LINES, NARROW_LOW_CONFIDENCE
    }

    /** All tunable values are experimental defaults, NOT proven accuracy thresholds. */
    public static final class Config {
        public final boolean enabled;
        public final long minRemainingFrameMs;
        public final long cooldownMs;
        public final long overrunCooldownMs;
        public final int maxBoxesToInspect;
        public final int maxCandidateChars;
        public final float maxConfidence;
        public final float maxEstimatedCharacterAspect;
        public final float maxTextSkewDegrees;
        public final float maxPerspectiveDistortion;
        public final float likelyMergedHeightMultiplier;

        public Config(boolean enabled) {
            this(enabled, 35, 1800, 8000, 48, 8, 0.78f, 0.67f,
                    8f, 0.16f, 1.8f);
        }

        public Config(boolean enabled, long minRemainingFrameMs,
                      long cooldownMs, long overrunCooldownMs,
                      int maxBoxesToInspect, int maxCandidateChars,
                      float maxConfidence, float maxEstimatedCharacterAspect,
                      float maxTextSkewDegrees, float maxPerspectiveDistortion,
                      float likelyMergedHeightMultiplier) {
            this.enabled = enabled;
            this.minRemainingFrameMs = minRemainingFrameMs;
            this.cooldownMs = cooldownMs;
            this.overrunCooldownMs = overrunCooldownMs;
            this.maxBoxesToInspect = maxBoxesToInspect;
            this.maxCandidateChars = maxCandidateChars;
            this.maxConfidence = maxConfidence;
            this.maxEstimatedCharacterAspect = maxEstimatedCharacterAspect;
            this.maxTextSkewDegrees = maxTextSkewDegrees;
            this.maxPerspectiveDistortion = maxPerspectiveDistortion;
            this.likelyMergedHeightMultiplier = likelyMergedHeightMultiplier;
        }
    }

    public static final class Line {
        public final String text;
        public final float confidence;
        // PaddleOCR polygon order: top-left, top-right, bottom-right, bottom-left.
        public final float[] xy; // [x0,y0,x1,y1,x2,y2,x3,y3]
        public Line(String text, float confidence, float[] xy) {
            this.text = text == null ? "" : text;
            this.confidence = confidence;
            this.xy = xy == null ? null : xy.clone();
        }
    }

    public static final class Decision {
        public final Action action;
        public final Reason reason;
        public final int lineIndex;
        public final float estimatedCharacterAspect;
        private Decision(Action action, Reason reason, int lineIndex, float aspect) {
            this.action = action;
            this.reason = reason;
            this.lineIndex = lineIndex;
            this.estimatedCharacterAspect = aspect;
        }
        static Decision skip(Reason reason) {
            return new Decision(Action.NONE, reason, -1, Float.NaN);
        }
        static Decision choose(Action action, Reason reason, int index, float aspect) {
            return new Decision(action, reason, index, aspect);
        }
    }

    private final Config config;
    private long lastAttemptMs = Long.MIN_VALUE;
    private long blockedUntilMs = Long.MIN_VALUE;

    public SelectiveOcrRescuePolicy() {
        this(new Config(false)); // Keep all extra work OFF unless explicitly opted in.
    }
    public SelectiveOcrRescuePolicy(Config config) {
        this.config = config;
    }

    public synchronized Decision evaluate(
            List<Line> lines,
            long nowMs,
            long frameDeadlineMs,
            boolean fastScan,
            boolean cameraStable) {
        if (!config.enabled) return Decision.skip(Reason.DISABLED);
        if (fastScan) return Decision.skip(Reason.FAST_SCAN);
        if (!cameraStable) return Decision.skip(Reason.UNSTABLE_FRAME);
        if (frameDeadlineMs <= nowMs ||
                frameDeadlineMs - nowMs < config.minRemainingFrameMs)
            return Decision.skip(Reason.NO_TIME_BUDGET);
        if (nowMs < blockedUntilMs ||
                (lastAttemptMs != Long.MIN_VALUE &&
                        nowMs >= lastAttemptMs &&
                        nowMs - lastAttemptMs < config.cooldownMs))
            return Decision.skip(Reason.COOLDOWN);
        if (lines == null || lines.isEmpty()) return Decision.skip(Reason.NO_VALID_CANDIDATE);

        float typicalHeight = medianPlausibleHeight(lines);
        int bestStretchIndex = -1;
        float bestStretchAspect = Float.POSITIVE_INFINITY;
        int inspected = 0;
        for (int i = 0; i < lines.size() && inspected < config.maxBoxesToInspect; i++) {
            Line line = lines.get(i);
            inspected++;
            if (line == null || line.xy == null || line.xy.length != 8 ||
                    !Float.isFinite(line.confidence) || line.confidence < 0f ||
                    line.confidence > 1f) continue;

            float top = length(line.xy, 0, 2);
            float bottom = length(line.xy, 6, 4);
            float left = length(line.xy, 0, 6);
            float right = length(line.xy, 2, 4);
            float width = (top + bottom) / 2f;
            float height = (left + right) / 2f;
            if (!Float.isFinite(width) || !Float.isFinite(height) ||
                    width < 8f || height < 5f) continue;

            float skew = (float) Math.toDegrees(Math.atan2(
                    line.xy[3] - line.xy[1], line.xy[2] - line.xy[0]));
            float widthPerspective = Math.abs(top - bottom) / Math.max(top, bottom);
            float heightPerspective = Math.abs(left - right) / Math.max(left, right);
            // Never stretch skewed, rotated or heavily perspective-distorted lines.
            if (Math.abs(skew) > config.maxTextSkewDegrees ||
                    Math.max(widthPerspective, heightPerspective) >
                            config.maxPerspectiveDistortion) continue;

            int glyphCount = countNonWhitespaceCodePoints(line.text);
            if (typicalHeight > 0 && height > typicalHeight * config.likelyMergedHeightMultiplier
                    && glyphCount >= 3) {
                lastAttemptMs = nowMs;
                return Decision.choose(Action.REVIEW_SEGMENTATION,
                        Reason.POSSIBLE_MERGED_LINES, i, Float.NaN);
            }
            if (glyphCount < 2 || glyphCount > config.maxCandidateChars ||
                    line.confidence >= config.maxConfidence) continue;
            // Per-character width/height, NOT whole-line width/height:
            // a naturally long sentence should not trigger a stretch.
            float estimatedCharAspect = width / (glyphCount * height);
            if (estimatedCharAspect < config.maxEstimatedCharacterAspect &&
                    estimatedCharAspect < bestStretchAspect) {
                bestStretchIndex = i;
                bestStretchAspect = estimatedCharAspect;
            }
        }
        if (bestStretchIndex >= 0) {
            lastAttemptMs = nowMs;
            return Decision.choose(Action.CANDIDATE_STRETCH,
                    Reason.NARROW_LOW_CONFIDENCE,
                    bestStretchIndex, bestStretchAspect);
        }
        return Decision.skip(Reason.NO_VALID_CANDIDATE);
    }

    /**
     * Called after an optional *already bounded* experiment, not after a blocking
     * camera-thread inference. A slow candidate activates a cooldown.
     */
    public synchronized void recordExtraWork(long endMs, long extraDurationMs,
                                              long allowedExtraMs) {
        if (extraDurationMs < 0 || extraDurationMs > allowedExtraMs)
            blockedUntilMs = safeAdd(endMs, config.overrunCooldownMs);
    }

    private static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }
    private static float length(float[] xy, int i, int j) {
        return (float) Math.hypot(xy[i] - xy[j], xy[i + 1] - xy[j + 1]);
    }
    private static int countNonWhitespaceCodePoints(String text) {
        int count = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            if (!Character.isWhitespace(cp)) count++;
            i += Character.charCount(cp);
        }
        return count;
    }
    private static float medianPlausibleHeight(List<Line> lines) {
        // Bounded small allocation: only first 48 lines, regardless of large OCR pages.
        float[] heights = new float[Math.min(48, lines.size())];
        int n = 0;
        for (int i = 0; i < lines.size() && n < heights.length; i++) {
            Line l = lines.get(i);
            if (l == null || l.xy == null || l.xy.length != 8) continue;
            float h = (length(l.xy, 0, 6) + length(l.xy, 2, 4)) / 2f;
            if (Float.isFinite(h) && h >= 5f) heights[n++] = h;
        }
        if (n == 0) return -1f;
        java.util.Arrays.sort(heights, 0, n);
        return heights[n / 2];
    }
}
