package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Geometry-based, column-aware reading order for Chinese OCR, independent of Android. */
public final class ReadingOrder {
    private ReadingOrder() {}

    public static final class Box {
        public final String text;
        public final float confidence;
        public final float[] x;
        public final float[] y;
        public Box(String text, float confidence, float[] x, float[] y) {
            this.text = text;
            this.confidence = confidence;
            this.x = x.clone();
            this.y = y.clone();
        }
        float left() { float v = Float.MAX_VALUE; for (float a : x) v = Math.min(v, a); return v; }
        float right() { float v = -Float.MAX_VALUE; for (float a : x) v = Math.max(v, a); return v; }
        float top() { float v = Float.MAX_VALUE; for (float a : y) v = Math.min(v, a); return v; }
        float bottom() { float v = -Float.MAX_VALUE; for (float a : y) v = Math.max(v, a); return v; }
        float centerX() { return (x[0] + x[1] + x[2] + x[3]) / 4f; }
        float centerY() { return (y[0] + y[1] + y[2] + y[3]) / 4f; }
        float actualHeight() {
            float leftEdge = (float) Math.hypot(x[3] - x[0], y[3] - y[0]);
            float rightEdge = (float) Math.hypot(x[2] - x[1], y[2] - y[1]);
            return Math.max(1f, (leftEdge + rightEdge) / 2f);
        }
        float slope() {
            float leftX = (x[0] + x[3]) / 2f;
            float rightX = (x[1] + x[2]) / 2f;
            float dx = rightX - leftX;
            if (Math.abs(dx) < 2f) return 0f;
            float leftY = (y[0] + y[3]) / 2f;
            float rightY = (y[1] + y[2]) / 2f;
            float slope = (rightY - leftY) / dx;
            return Math.abs(slope) > 0.35f ? 0f : slope;
        }
        float alignedY(float xReference) { return centerY() + slope() * (xReference - centerX()); }
    }

    private static final class Line {
        final List<Box> boxes = new ArrayList<>();
        float anchorX;
        float anchorY;
        float typicalHeight;
        Line(Box box) { boxes.add(box); anchorX = box.centerX(); anchorY = box.centerY(); typicalHeight = box.actualHeight(); }
        float expectedY(Box box) {
            return anchorY + box.slope() * (box.centerX() - anchorX);
        }
        boolean matches(Box box) {
            float distance = Math.abs(box.centerY() - expectedY(box));
            float allowance = Math.max(5f, Math.min(typicalHeight, box.actualHeight()) * 0.48f);
            return distance <= allowance;
        }
        void add(Box box) {
            // Align the incoming center to the existing line's reference x.
            float aligned = box.alignedY(anchorX);
            int count = boxes.size();
            anchorY = (anchorY * count + aligned) / (count + 1);
            typicalHeight = (typicalHeight * count + box.actualHeight()) / (count + 1);
            boxes.add(box);
        }
    }

    /** No confidence filtering here: losing a valid line is worse than exposing uncertain OCR text. */
    public static String arrange(List<Box> incoming) {
        List<Box> boxes = new ArrayList<>();
        for (Box box : incoming) {
            if (box == null || box.x.length != 4 || box.y.length != 4 || box.text == null) continue;
            String clean = box.text.replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
            if (clean.isEmpty() || Float.isNaN(box.centerX()) || Float.isNaN(box.centerY())) continue;
            boxes.add(new Box(clean, box.confidence, box.x, box.y));
        }
        if (boxes.isEmpty()) return "";

        List<List<Box>> columns = splitColumns(boxes);
        StringBuilder output = new StringBuilder();
        for (List<Box> column : columns) {
            column.sort(Comparator.comparingDouble(Box::top).thenComparingDouble(Box::left));
            List<Line> lines = new ArrayList<>();
            for (Box entry : column) {
                Line best = null;
                float distance = Float.POSITIVE_INFINITY;
                for (Line row : lines) {
                    float d = Math.abs(entry.centerY() - row.expectedY(entry));
                    if (row.matches(entry) && d < distance) { best = row; distance = d; }
                }
                if (best == null) lines.add(new Line(entry));
                else best.add(entry);
            }
            lines.sort(Comparator.comparingDouble(a -> a.anchorY));
            for (Line line : lines) {
                line.boxes.sort(Comparator.comparingDouble(Box::left));
                if (output.length() != 0) output.append('\n');
                Box previous = null;
                for (Box box : line.boxes) {
                    if (previous != null && !box.text.isEmpty()) {
                        char p = previous.text.charAt(previous.text.length() - 1);
                        char n = box.text.charAt(0);
                        if (asciiWord(p) && asciiWord(n) && box.left() - previous.right() > 1f) output.append(' ');
                    }
                    output.append(box.text);
                    previous = box;
                }
            }
        }
        return output.toString().trim();
    }

    private static boolean asciiWord(char c) { return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'; }

    /** Split only when a wide, persistent gutter separates two vertically overlapping columns. */
    private static List<List<Box>> splitColumns(List<Box> boxes) {
        if (boxes.size() < 4) return Arrays.asList(boxes);
        List<Box> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble(Box::left));
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        List<Float> heights = new ArrayList<>();
        for (Box box : boxes) {
            minX = Math.min(minX, box.left());
            maxX = Math.max(maxX, box.right());
            heights.add(box.actualHeight());
        }
        heights.sort(Float::compare);
        float typicalH = heights.get(heights.size() / 2);
        float minGutter = Math.max(typicalH * 2.5f, (maxX - minX) * 0.12f);
        float bestGap = minGutter;
        int bestIndex = -1;
        for (int split = 2; split <= sorted.size() - 2; split++) {
            float leftRight = -Float.MAX_VALUE, rightLeft = Float.MAX_VALUE;
            float leftTop = Float.MAX_VALUE, leftBottom = -Float.MAX_VALUE;
            float rightTop = Float.MAX_VALUE, rightBottom = -Float.MAX_VALUE;
            for (int i = 0; i < sorted.size(); i++) {
                Box b = sorted.get(i);
                if (i < split) {
                    leftRight = Math.max(leftRight, b.right());
                    leftTop = Math.min(leftTop, b.top());
                    leftBottom = Math.max(leftBottom, b.bottom());
                } else {
                    rightLeft = Math.min(rightLeft, b.left());
                    rightTop = Math.min(rightTop, b.top());
                    rightBottom = Math.max(rightBottom, b.bottom());
                }
            }
            float gap = rightLeft - leftRight;
            float overlap = Math.min(leftBottom, rightBottom) - Math.max(leftTop, rightTop);
            if (gap > bestGap && overlap > typicalH * 1.5f) { bestGap = gap; bestIndex = split; }
        }
        if (bestIndex < 0) return Arrays.asList(boxes);
        return Arrays.asList(new ArrayList<>(sorted.subList(0, bestIndex)),
                             new ArrayList<>(sorted.subList(bestIndex, sorted.size())));
    }
}
