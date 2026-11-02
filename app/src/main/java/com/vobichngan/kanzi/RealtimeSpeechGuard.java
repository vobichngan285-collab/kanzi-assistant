package com.vobichngan.kanzi;

import java.util.HashMap;
import java.util.Map;

/**
 * Deduplicate LIVE OCR by recognized TEXT, never by luma frame generation.
 *
 * Hand motion, autoexposure and autofocus can all advance scene generations
 * several times during one full OCR pass. They do not prove the reader moved
 * to a different page. The active speech flow must remain intact until a
 * sufficiently different OCR target is actually recognized.
 *
 * Pure-Java; no model, bitmap, OCR pass or asynchronous timer.
 * Call on the main/UI thread from the TTS scheduler.
 */
public final class RealtimeSpeechGuard {
    private String activeText = "";

    public void reset() {
        activeText = "";
    }

    public boolean accept(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) return false;
        if (!activeText.isEmpty() && sameTarget(activeText, normalizedText))
            return false;
        activeText = normalizedText;
        return true;
    }

    /**
     * Repeated OCR often drops a few words, changes one glyph, or returns
     * only the cover heading. Compare 3-character shingles instead of a
     * full-string edit distance; this is O(text length), not O(n^2).
     * Comparisons are relative to the shorter reading because a partial
     * repeat must not restart the full page before its remaining lines play.
     */
    static boolean sameTarget(String previous, String next) {
        if (previous.equals(next)) return true;
        final int shorter = Math.min(previous.length(), next.length());
        if (shorter == 0) return false;
        if (shorter < 5) {
            // Two-character words like "简爱" can occur on another object;
            // exact matches are already handled above.
            return shorter >= 3 &&
                (previous.contains(next) || next.contains(previous));
        }
        if (previous.contains(next) || next.contains(previous)) return true;

        Map<String, Integer> previousShingles = new HashMap<>();
        for (int i = 0; i <= previous.length() - 3; i++) {
            String shingle = previous.substring(i, i + 3);
            Integer old = previousShingles.get(shingle);
            previousShingles.put(shingle, old == null ? 1 : old + 1);
        }
        int shared = 0;
        for (int i = 0; i <= next.length() - 3; i++) {
            String shingle = next.substring(i, i + 3);
            Integer available = previousShingles.get(shingle);
            if (available != null && available > 0) {
                shared++;
                previousShingles.put(shingle, available - 1);
            }
        }
        int shorterShingles = shorter - 2;
        // At least 60% of the shorter reading's character triples must be
        // shared. A distinct page with only the same publisher/author
        // heading is allowed to interrupt the previous one.
        return shared >= 3 && shared >= Math.ceil(shorterShingles * 0.60);
    }
}
