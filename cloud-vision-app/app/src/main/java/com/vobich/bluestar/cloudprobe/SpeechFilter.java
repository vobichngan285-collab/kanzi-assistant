package com.vobich.bluestar.cloudprobe;

/**
 * TTS-only cleanup. Never mutates OCR results, stored source text, or AI prompts.
 * Keep sentence boundaries so the reader has natural pauses.
 */
final class SpeechFilter {
    private SpeechFilter() {}

    static String apply(String source, boolean skipLatin, boolean skipDigits, boolean skipSymbols) {
        if (source == null || source.isEmpty()) return "";
        if (!skipLatin && !skipDigits && !skipSymbols) return source;
        StringBuilder out = new StringBuilder(source.length());
        boolean lastSpace = false;
        for (int i = 0; i < source.length();) {
            int cp = source.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            boolean remove = (skipLatin && script == Character.UnicodeScript.LATIN
                && Character.isLetter(cp)) || (skipDigits && Character.isDigit(cp));
            int type = Character.getType(cp);
            boolean isSymbol = type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION || type == Character.OTHER_PUNCTUATION;
            boolean pause = cp == '。' || cp == '，' || cp == '、' || cp == '？'
                || cp == '！' || cp == '.' || cp == ',' || cp == '?' || cp == '!';
            if (skipSymbols && isSymbol && !pause) remove = true;
            if (remove) {
                if (!lastSpace && out.length() > 0) { out.append(' ');lastSpace = true; }
                continue;
            }
            if (skipSymbols && pause) {
                // Preserve gentle pacing even when all visible symbols are filtered.
                if (out.length() > 0 && !lastSpace) {out.append('，');lastSpace = false;}
                continue;
            }
            if (Character.isWhitespace(cp)) {
                if (!lastSpace && out.length() > 0) {out.append(' ');lastSpace = true;}
            } else {
                out.appendCodePoint(cp);lastSpace=false;
            }
        }
        return out.toString().trim().replaceAll(" +([，。！？、])", "$1")
            .replaceAll("([，。！？、]){2,}", "$1");
    }
}
