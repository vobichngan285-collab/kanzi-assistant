package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.List;

/**
 * Speak OCR as a continuous text stream.
 *
 * A single newline is a VISUAL line wrap, not a speech boundary. Two
 * newlines delimit distinct regions/paragraphs and must never be joined.
 * Every non-whitespace OCR character is retained in the same order.
 */
public final class SpeechText {
    private SpeechText() {}
    private static final int CHUNK_LIMIT = 180;
    private static final int EARLY_SENTENCE_BREAK = 65;

    public static List<String> chunks(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        String source = text.replace("\r\n", "\n").replace('\r', '\n')
            .replaceAll("[\\t ]+", " ");
        StringBuilder buffer = new StringBuilder();
        boolean wrapped = false;
        for (int i = 0; i < source.length();) {
            int cp = source.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n') {
                int breaks = 1;
                while (i < source.length() && source.charAt(i) == '\n') {
                    breaks++;
                    i++;
                }
                if (breaks >= 2) {
                    flush(buffer, result);
                    wrapped = false;
                } else {
                    wrapped = true;
                }
                continue;
            }
            // Only preserve whitespace between English words at a wrapped
            // line. Chinese lines continue without a synthetic character.
            if (wrapped && buffer.length() > 0 && asciiWord(buffer.charAt(buffer.length() - 1))
                && cp <= 127 && asciiWord((char) cp)) {
                if (buffer.length() >= CHUNK_LIMIT) flush(buffer, result);
                buffer.append(' ');
            }
            wrapped = false;
            int cpSize = Character.charCount(cp);
            if (buffer.length() + cpSize > CHUNK_LIMIT) flush(buffer, result);
            buffer.appendCodePoint(cp);
            if (buffer.length() >= EARLY_SENTENCE_BREAK && isEnding(cp))
                flush(buffer, result);
            else if (buffer.length() >= CHUNK_LIMIT)
                flush(buffer, result);
        }
        flush(buffer, result);
        return result;
    }

    /** Rapid scan reacts to the newest complete text flow, not the first N lines. */
    public static String scanExcerpt(String text) {
        return text == null ? "" : text.trim();
    }

    private static boolean asciiWord(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
            || (ch >= '0' && ch <= '9');
    }

    private static boolean isEnding(int ch) {
        return ch == '。' || ch == '！' || ch == '？' || ch == '；'
            || ch == '!' || ch == '?' || ch == ';';
    }

    private static void flush(StringBuilder buffer, List<String> result) {
        String chunk = buffer.toString().trim();
        if (!chunk.isEmpty()) result.add(chunk);
        buffer.setLength(0);
    }
}
