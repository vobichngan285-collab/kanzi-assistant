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
    private long activeScene = Long.MIN_VALUE;
    private String pendingText = "";
    private int pendingCount = 0;

    /**
     * Stable LIVE scheduling, not a text recognition filter. Once a result
     * begins speaking, NOTHING from the camera can replace it. Afterward,
     * another substantially different target must be seen in a changed
     * camera scene in TWO coherent OCR results. Scene change alone, one
     * different OCR reading, and tiny OCR fluctuations never re-start TTS.
     * Manual reading bypasses this policy in MainActivity.
     */
    public boolean acceptStable(String text, long scene, boolean audioBusy) {
        if (text == null || text.isEmpty() || audioBusy) return false;
        if (activeText.isEmpty()) {
            activeText = text;
            activeScene = scene;
            clearPending();
            return true;
        }
        if (sameTarget(activeText, text)) {
            clearPending();
            return false;
        }
        // A static camera with noisy recognition must not speak again,
        // even if the wording changes significantly between OCR attempts.
        if (scene < 0 || scene == activeScene) {
            clearPending();
            return false;
        }
        if (pendingText.isEmpty() || !sameTarget(pendingText, text)) {
            pendingText = text;
            pendingCount = 1;
            return false;
        }
        if (++pendingCount < 2) return false;
        activeText = text;
        activeScene = scene;
        clearPending();
        return true;
    }

    private void clearPending() {
        pendingText = "";
        pendingCount = 0;
    }

    public void reset() {
        activeText = "";
        activeScene = Long.MIN_VALUE;
        clearPending();
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
        // Strong generic overlap covers ordinary near-identical OCR frames.
        if (shared >= 3 && shared >= Math.ceil(shorterShingles * 0.60))
            return true;

        // Real cover regression: the SAME Jane Eyre cover varied between
        // 19 and 22 OCR boxes. The damaged translator biography, a stray
        // (4iThy) box and changed cover glyphs lowered shingle overlap below
        // 60%, making live TTS restart after the top slogan.
        //
        // Do not solve that solely by lowering the global threshold: require
        // BOTH significant shared text AND a contiguous long anchor phrase.
        // This distinguishes the same cover with noisy small print from a
        // different page that happens to repeat its publisher or author.
        // Pure string work; no extra model or OCR inference.
        return shorter >= 24 &&
            shared >= 10 &&
            shared >= Math.ceil(shorterShingles * 0.43) &&
            sharesContiguousAnchor(previous, next, 8);
    }

    private static boolean sharesContiguousAnchor(String previous, String next,
                                                  int anchorChars) {
        if (previous.length() < anchorChars || next.length() < anchorChars)
            return false;
        java.util.HashSet<String> phrases = new java.util.HashSet<>();
        for (int i = 0; i <= previous.length() - anchorChars; i++)
            phrases.add(previous.substring(i, i + anchorChars));
        for (int i = 0; i <= next.length() - anchorChars; i++)
            if (phrases.contains(next.substring(i, i + anchorChars)))
                return true;
        return false;
}
}
