package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.List;

/** Segment complete OCR pages into safe, naturally paused Android TTS utterances. */
public final class SpeechText {
    private SpeechText() {}
    private static final int CHUNK_LIMIT = 180;

    public static List<String> chunks(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[\\t ]+", " ");
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\n') {
                flush(buffer, result);
                continue;
            }
            buffer.append(ch);
            if (buffer.length() >= CHUNK_LIMIT || (buffer.length() >= 70 && isEnding(ch))) {
                flush(buffer, result);
            }
        }
        flush(buffer, result);
        return result;
    }

    private static boolean isEnding(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；' || c == '!' || c == '?' || c == ';';
    }

    private static void flush(StringBuilder buffer, List<String> result) {
        String chunk = buffer.toString().trim();
        if (!chunk.isEmpty()) result.add(chunk);
        buffer.setLength(0);
    }
}
