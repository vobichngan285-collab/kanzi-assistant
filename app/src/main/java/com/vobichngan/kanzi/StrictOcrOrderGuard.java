package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed bridge between OCR and an optional on-device layout/order model.
 *
 * The model may return ONLY OCR box IDs. Spoken text is always taken verbatim
 * from the OCR engine, never from model-generated strings.
 * This class is Android-independent for JVM regression tests.
 */
public final class StrictOcrOrderGuard {
    private StrictOcrOrderGuard() {}

    public static final class Item {
        public final int id;
        public final String rawText;
        public Item(int id, String rawText) {
            this.id = id;
            this.rawText = rawText;
        }
    }

    public static final class Result {
        public final boolean accepted;
        public final String reason;
        public final List<Item> ordered;
        private Result(boolean accepted, String reason, List<Item> ordered) {
            this.accepted = accepted;
            this.reason = reason;
            this.ordered = Collections.unmodifiableList(ordered);
        }
        /** Exact OCR text, without normalization, paraphrase or hallucinated characters. */
        public String exactText() {
            StringBuilder out = new StringBuilder();
            for (Item item : ordered) {
                if (out.length() > 0) out.append('\n');
                out.append(item.rawText);
            }
            return out.toString();
        }
    }

    /**
     * Accept a complete permutation of source OCR IDs or fall back to original order.
     * Blank OCR entries are retained: a caller may choose not to speak them separately.
     */
    public static Result apply(List<Item> original, List<Integer> proposedIds) {
        if (original == null) throw new IllegalArgumentException("original is null");
        List<Item> fallback = new ArrayList<>(original);
        Map<Integer, Item> byId = new HashMap<>();
        for (Item item : original) {
            if (item == null || item.rawText == null || byId.put(item.id, item) != null)
                throw new IllegalArgumentException("Invalid or duplicate source OCR box");
        }
        if (proposedIds == null || proposedIds.size() != original.size())
            return new Result(false, "missing or extra box IDs", fallback);
        Set<Integer> seen = new HashSet<>();
        List<Item> sorted = new ArrayList<>();
        for (Integer id : proposedIds) {
            if (id == null || !seen.add(id) || !byId.containsKey(id))
                return new Result(false, "unknown or duplicate box ID", fallback);
            sorted.add(byId.get(id));
        }
        return new Result(true, "verified permutation of original OCR boxes", sorted);
    }
}
