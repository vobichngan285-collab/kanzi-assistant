package com.vobichngan.kanzi;

import java.util.Locale;

/**
 * Experimental, allocation-light decision gate for short-text camera scanning.
 *
 * Consumes OCR observations that were ALREADY produced. It never starts OCR,
 * touches the camera, plays audio, or queues frames. Disabled by default.
 * A consumer may read the suggested text only after SPEAK is returned.
 *
 * NOTE: Existing FrameChangeGate may skip identical images for ~2800ms;
 * two-frame confirmation therefore has user-visible latency until an
 * independently benchmarked verification-frame strategy exists.
 * Do not wire this gate into MainActivity without that work.
 */
public final class StableShortTextGate {
    public enum Action { SKIP, WAIT_FOR_CONFIRMATION, SPEAK, DUPLICATE }
    public enum Reason {
        DISABLED, LONG_READING, SPEECH_BUSY, FRAME_UNSTABLE, INVALID_OBSERVATION,
        FIRST_OBSERVATION, NEED_MORE_OBSERVATIONS, NOT_STABLE_LONG_ENOUGH,
        STABLE, RECENT_DUPLICATE
    }

    public static final class Config {
        public final boolean enabled;
        public final int confirmations;
        public final long minimumStableMs;
        public final long maximumGapMs;
        public final int maximumCodepoints;
        public final float minimumConfidence;
        public final float maximumCenterDrift;

        public Config(boolean enabled) {
            this(enabled, 2, 180L, 3500L, 48, 0.72f, 0.09f);
        }

        public Config(boolean enabled, int confirmations, long minimumStableMs,
                      long maximumGapMs, int maximumCodepoints,
                      float minimumConfidence, float maximumCenterDrift) {
            if (confirmations < 2 || confirmations > 4 ||
                minimumStableMs < 0 || maximumGapMs < minimumStableMs ||
                maximumGapMs > 10000 || maximumCodepoints < 1 ||
                maximumCodepoints > 256 ||
                !Float.isFinite(minimumConfidence) ||
                minimumConfidence < 0 || minimumConfidence > 1 ||
                !Float.isFinite(maximumCenterDrift) ||
                maximumCenterDrift <= 0 || maximumCenterDrift > 1) {
                throw new IllegalArgumentException("Invalid short-text gate configuration");
            }
            this.enabled = enabled;
            this.confirmations = confirmations;
            this.minimumStableMs = minimumStableMs;
            this.maximumGapMs = maximumGapMs;
            this.maximumCodepoints = maximumCodepoints;
            this.minimumConfidence = minimumConfidence;
            this.maximumCenterDrift = maximumCenterDrift;
        }
    }

    public static final class Observation {
        public final String text;
        public final float confidence;
        // Normalized center of detected text area, each coordinate in [0,1].
        public final float centerX;
        public final float centerY;
        public final long frameSequence;

        public Observation(String text, float confidence, float centerX,
                           float centerY, long frameSequence) {
            this.text = text;
            this.confidence = confidence;
            this.centerX = centerX;
            this.centerY = centerY;
            this.frameSequence = frameSequence;
        }
    }

    public static final class Decision {
        public final Action action;
        public final Reason reason;
        /** Original OCR text. No dictionary substitutions or guesses are made. */
        public final String text;
        public final int confirmations;

        private Decision(Action action, Reason reason, String text, int confirmations) {
            this.action = action;
            this.reason = reason;
            this.text = text;
            this.confirmations = confirmations;
        }
        private static Decision skip(Reason reason) {
            return new Decision(Action.SKIP, reason, "", 0);
        }
        private static Decision waiting(Reason reason, int count) {
            return new Decision(Action.WAIT_FOR_CONFIRMATION, reason, "", count);
        }
    }

    private final Config config;
    private String pendingNormalized = "";
    private float pendingX;
    private float pendingY;
    private long pendingFrame = -1;
    private long firstSeenMs = -1;
    private long lastSeenMs = -1;
    private int consecutive = 0;
    private String lastSpokenNormalized = "";

    public StableShortTextGate() { this(new Config(false)); }
    public StableShortTextGate(Config config) {
        if (config == null) throw new IllegalArgumentException("config");
        this.config = config;
    }

    /** Stops or restarts scanning: re-reading previous text is allowed again. */
    public synchronized void reset() {
        clearPending();
        lastSpokenNormalized = "";
    }

    /** Called when an independently determined *new scene* is entered. */
    public synchronized void noteSceneChanged() {
        reset();
    }

    /**
     * Consumes a single completed OCR observation; never waits for the next frame.
     * Identical frameSequence cannot satisfy multiple confirmations.
     */
    public synchronized Decision observe(Observation input, long nowMs,
                                         boolean stableFrame, boolean longReadingMode,
                                         boolean speechBusy) {
        if (!config.enabled) return Decision.skip(Reason.DISABLED);
        if (longReadingMode) {
            clearPending();
            return Decision.skip(Reason.LONG_READING);
        }
        if (speechBusy) {
            clearPending();
            return Decision.skip(Reason.SPEECH_BUSY);
        }
        if (!stableFrame) {
            clearPending();
            return Decision.skip(Reason.FRAME_UNSTABLE);
        }
        if (input == null || input.text == null ||
            !Float.isFinite(input.confidence) ||
            input.confidence < config.minimumConfidence ||
            input.confidence > 1f ||
            !Float.isFinite(input.centerX) || !Float.isFinite(input.centerY) ||
            input.centerX < 0f || input.centerX > 1f ||
            input.centerY < 0f || input.centerY > 1f ||
            input.frameSequence < 0L || nowMs < 0L) {
            clearPending();
            return Decision.skip(Reason.INVALID_OBSERVATION);
        }
        String normalized = normalizeText(input.text);
        if (normalized.isEmpty() ||
            normalized.codePointCount(0, normalized.length()) > config.maximumCodepoints) {
            clearPending();
            return Decision.skip(Reason.INVALID_OBSERVATION);
        }

        if (pendingFrame == input.frameSequence) {
            return Decision.waiting(Reason.NEED_MORE_OBSERVATIONS, consecutive);
        }
        boolean same = normalized.equals(pendingNormalized) &&
            pendingFrame >= 0L && input.frameSequence > pendingFrame &&
            lastSeenMs >= 0L && nowMs > lastSeenMs &&
            nowMs - lastSeenMs <= config.maximumGapMs &&
            Math.abs(input.centerX - pendingX) <= config.maximumCenterDrift &&
            Math.abs(input.centerY - pendingY) <= config.maximumCenterDrift;
        if (!same) {
            pendingNormalized = normalized;
            pendingX = input.centerX;
            pendingY = input.centerY;
            pendingFrame = input.frameSequence;
            firstSeenMs = nowMs;
            lastSeenMs = nowMs;
            consecutive = 1;
            return Decision.waiting(Reason.FIRST_OBSERVATION, consecutive);
        }

        pendingFrame = input.frameSequence;
        lastSeenMs = nowMs;
        consecutive = Math.min(config.confirmations, consecutive + 1);
        if (consecutive < config.confirmations) {
            return Decision.waiting(Reason.NEED_MORE_OBSERVATIONS, consecutive);
        }
        if (nowMs - firstSeenMs < config.minimumStableMs) {
            return Decision.waiting(Reason.NOT_STABLE_LONG_ENOUGH, consecutive);
        }
        if (normalized.equals(lastSpokenNormalized)) {
            return new Decision(Action.DUPLICATE, Reason.RECENT_DUPLICATE, "", consecutive);
        }
        lastSpokenNormalized = normalized;
        return new Decision(Action.SPEAK, Reason.STABLE, input.text.trim(), consecutive);
    }

    private void clearPending() {
        pendingNormalized = "";
        pendingFrame = -1L;
        firstSeenMs = -1L;
        lastSeenMs = -1L;
        consecutive = 0;
    }

    private static String normalizeText(String source) {
        StringBuilder result = new StringBuilder(Math.min(source.length(), 256));
        boolean previousSpace = false;
        for (int i = 0; i < source.length();) {
            int cp = source.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp)) continue;
            if (Character.isWhitespace(cp)) {
                if (result.length() != 0 && !previousSpace) result.append(' ');
                previousSpace = true;
            } else {
                result.appendCodePoint(cp);
                previousSpace = false;
            }
            // Never build an unbounded normalized string from a malformed OCR result.
            if (result.length() > 512) return "";
        }
        return result.toString().trim().toLowerCase(Locale.ROOT);
    }
}
