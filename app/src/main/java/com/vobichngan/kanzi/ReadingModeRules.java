package com.vobichngan.kanzi;

/** Explicit user-facing behavior contract for the two reading modes. */
public final class ReadingModeRules {
    private ReadingModeRules() {}

    /**
     * Fast scan never waits for the previous utterance before submitting a
     * NEW camera scene for OCR. Long reading holds the existing page until TTS ends.
     */
    public static boolean shouldDeferOcrForSpeech(boolean longReading, boolean speaking) {
        return longReading && speaking;
    }

    /** Only new text interrupts fast scan; manual capture overrides either mode. */
    public static boolean shouldInterruptSpeech(boolean longReading, boolean manualCapture) {
        return manualCapture || !longReading;
    }

    /**
     * Exact normalized content is a duplicate in fast scan. Never apply broad
     * 88% similarity to a different page or room number, e.g. 房号201/房号202.
     */
    public static boolean isFastDuplicate(String incoming, String lastSpoken,
                                          String currentlySpeaking) {
        if (incoming == null || incoming.isEmpty()) return true;
        return incoming.equals(lastSpoken) || incoming.equals(currentlySpeaking);
    }
}
