package com.vobichngan.bluestar.m02slab;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative AP photo selection and URL safety rules, independent of Android. */
public final class M02sPhotoPolicy {
    private M02sPhotoPolicy() {}

    public static final int MAX_LIST_BYTES = 256 * 1024;
    public static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;
    public static final int MAX_CANDIDATES = 2000;
    private static final Pattern FILENAME_TIME =
            Pattern.compile("(20\\d{2})(\\d{2})(\\d{2})[_-]?(\\d{2})(\\d{2})(\\d{2})");
    private static final String[] TIME_FORMATS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
            "yyyyMMddHHmmss", "yyyyMMdd_HHmmss", "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
    };

    public static final class Candidate {
        public final String remoteName;  // private, never log
        public final long declaredBytes;
        public final String createdAt;   // private, never log
        public final String type;        // informational, not authorization
        public final Long sortableTime;

        public Candidate(String name, long bytes, String when, String type) {
            this.remoteName = name;
            this.declaredBytes = bytes;
            this.createdAt = when;
            this.type = type;
            Long parsed = parseTime(when);
            this.sortableTime = parsed == null ? timeFromName(name) : parsed;
        }
    }

    public static boolean isImageName(String name) {
        if (safeRelativePath(name) == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    /** File name is used by OEM as BASE_URL + FileItem.name; no arbitrary URLs. */
    public static String safeRelativePath(String input) {
        if (input == null || input.isEmpty() || input.length() > 240
                || input.startsWith("/") || input.startsWith("\\")
                || input.contains("://") || input.indexOf('?') >= 0
                || input.indexOf('#') >= 0 || input.indexOf('%') >= 0
                || input.indexOf('\\') >= 0) return null;
        String[] parts = input.split("/", -1);
        if (parts.length > 8) return null;
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (Character.isISOControl(c) || c == ':' || c == '\u0000') return null;
            }
            if (result.length() > 0) result.append('/');
            result.append(URLEncoder.encode(part, StandardCharsets.UTF_8)
                    .replace("+", "%20"));
        }
        return result.toString();
    }

    public static String safePhotoUrl(String verifiedHost, String remoteName) {
        String host = ProbePolicy.checkedLocalHost(verifiedHost);
        if (!isImageName(remoteName)) return null;
        String path = safeRelativePath(remoteName);
        if (path == null) return null;
        return "http://" + host + "/" + path;
    }

    public static Long parseTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim();
        for (String format : TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value,
                        DateTimeFormatter.ofPattern(format, Locale.ROOT))
                        .toEpochSecond(ZoneOffset.UTC);
            } catch (RuntimeException ignored) { }
        }
        try {
            if (value.matches("\\d{10}")) return Long.parseLong(value);
            if (value.matches("\\d{13}")) return Long.parseLong(value) / 1000L;
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private static Long timeFromName(String name) {
        if (name == null) return null;
        Matcher match = FILENAME_TIME.matcher(name);
        if (!match.find()) return null;
        try {
            return LocalDateTime.of(Integer.parseInt(match.group(1)),
                    Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)),
                    Integer.parseInt(match.group(4)), Integer.parseInt(match.group(5)),
                    Integer.parseInt(match.group(6))).toEpochSecond(ZoneOffset.UTC);
        } catch (RuntimeException ignored) { return null; }
    }

    /** Refuse to pretend an arbitrary list order is newest. */
    public static Candidate uniqueLatestOrNull(List<Candidate> all) {
        if (all == null || all.isEmpty()) return null;
        if (all.size() == 1) return all.get(0);
        Candidate best = null;
        Long highest = null;
        boolean tie = false;
        for (Candidate item : all) {
            if (item.sortableTime == null) return null;
            if (highest == null || item.sortableTime > highest) {
                best = item;
                highest = item.sortableTime;
                tie = false;
            } else if (item.sortableTime.equals(highest)) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    public enum ImageKind { JPEG, PNG, WEBP, UNKNOWN }

    public static ImageKind imageKind(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return ImageKind.UNKNOWN;
        if ((bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8
                && (bytes[2] & 255) == 0xff) return ImageKind.JPEG;
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        boolean isPng = true;
        for (int i = 0; i < png.length; i++) {
            if (bytes[i] != png[i]) { isPng = false; break; }
        }
        if (isPng) return ImageKind.PNG;
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return ImageKind.WEBP;
        return ImageKind.UNKNOWN;
    }

    public static boolean extensionMatches(String name, ImageKind kind) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        switch (kind) {
            case JPEG: return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            case PNG: return lower.endsWith(".png");
            case WEBP: return lower.endsWith(".webp");
            default: return false;
        }
    }

    public static List<Candidate> immutableCopy(List<Candidate> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
