package com.vobichngan.bluestar.photowatch;

import java.util.Locale;

/**
 * Test-only conservative markers and file-size stabilization. A parser
 * accepting a bitmap doesn't prove a concurrent writer has finished.
 * JPEG/PNG completion additionally requires their normal file terminators.
 * HEIC/WebP are heuristic (decode + unchanged size), never called guaranteed.
 */
public final class ImageCompletionPolicy {
    private ImageCompletionPolicy() { }

    public static boolean isSupported(String filename) {
        String name = normalize(filename);
        return isJpeg(name) || isPng(name) || name.endsWith(".heic")
                || name.endsWith(".heif") || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static boolean isJpeg(String s) {
        return s.endsWith(".jpg") || s.endsWith(".jpeg");
    }

    private static boolean isPng(String s) {
        return s.endsWith(".png");
    }

    public static boolean hasReliableEndMarker(String filename) {
        String name = normalize(filename);
        return isJpeg(name) || isPng(name);
    }

    /** Caller supplies up to the final 12 bytes of the actual file. */
    public static boolean hasValidEndMarker(String filename, byte[] tail) {
        if (tail == null) return false;
        String name = normalize(filename);
        if (isJpeg(name)) {
            return tail.length >= 2
                    && (tail[tail.length - 2] & 0xff) == 0xff
                    && (tail[tail.length - 1] & 0xff) == 0xd9;
        }
        if (isPng(name)) {
            final int[] expected = {
                    0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44,
                    0xae, 0x42, 0x60, 0x82
            };
            if (tail.length < 12) return false;
            for (int i = 0; i < 12; i++) {
                if ((tail[tail.length - 12 + i] & 0xff) != expected[i]) {
                    return false;
                }
            }
            return true;
        }
        // For non-JPEG/non-PNG, a successful sampled decode + stability is
        // an estimate, NOT a proof the producer has finished writing.
        return isSupported(filename);
    }

    public static boolean stable(long previousBytes, long currentBytes,
                                 long previousModified, long currentModified) {
        return previousBytes > 0 && previousBytes == currentBytes
                && previousModified == currentModified;
    }
}
