package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Geometry-only, non-mutating SHADOW risk audit for cross-row vertical OCR quads.
 * No OCR model, reading-order change, text rewrite, image processing or TTS.
 * A returned "suspect" is NOT proof of a wrong line and must never auto-delete
 * or reinterpret a recognized string. True vertical columns can look similar.
 */
public final class CrossRowOrientationAudit {
    private CrossRowOrientationAudit() {}
    private static final int MAX_BOXES = 512;

    public static final class Box {
        public final int sourceId;
        public final float[] quad;
        public final float confidence;
        public Box(int sourceId, float[] xy8, float confidence) {
            this.sourceId = sourceId;
            this.quad = xy8 == null ? null : xy8.clone();
            this.confidence = confidence;
        }
    }

    public static final class Alert {
        public final int sourceId;
        public final int[] companionIds;
        public final int[] anchorIds;
        public final float heightToWidth;
        private Alert(int id, List<Integer> companions, List<Integer> anchors, float ratio) {
            sourceId = id;
            companionIds = toArray(companions);
            anchorIds = toArray(anchors);
            heightToWidth = ratio;
        }
        private static int[] toArray(List<Integer> in) {
            int[] out = new int[in.size()];
            for (int i = 0; i < out.length; i++) out[i] = in.get(i);
            return out;
        }
    }

    public static final class Audit {
        public final List<Alert> suspects;
        public final int validBoxes;
        public final int horizontalAnchors;
        public final float typicalHeight;
        public final String status;
        private Audit(List<Alert> alerts, int valid, int anchors, float h, String status) {
            suspects = Collections.unmodifiableList(new ArrayList<>(alerts));
            validBoxes = valid;
            horizontalAnchors = anchors;
            typicalHeight = h;
            this.status = status;
        }
    }

    private static final class Rect {
        final Box original;
        final float x0, x1, y0, y1, width, height, cx, cy;
        Rect(Box b, float minx, float maxx, float miny, float maxy) {
            original = b;
            x0 = minx; x1 = maxx; y0 = miny; y1 = maxy;
            width = maxx - minx; height = maxy - miny;
            cx = (minx + maxx) * .5f; cy = (miny + maxy) * .5f;
        }
    }

    private static Rect read(Box box, int imageWidth, int imageHeight) {
        if (box == null || box.quad == null || box.quad.length != 8 ||
            !Float.isFinite(box.confidence)) return null;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i += 2) {
            float x = box.quad[i], y = box.quad[i + 1];
            if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
            x0 = Math.min(x0, x); x1 = Math.max(x1, x);
            y0 = Math.min(y0, y); y1 = Math.max(y1, y);
        }
        if (x1 - x0 < 4 || y1 - y0 < 4 || x0 < -2 || y0 < -2 ||
            x1 > imageWidth + 2 || y1 > imageHeight + 2) return null;
        return new Rect(box, x0, x1, y0, y1);
    }

    private static float medianHeight(List<Rect> horizontal) {
        List<Float> heights = new ArrayList<>();
        for (Rect b : horizontal) heights.add(b.height);
        Collections.sort(heights);
        int n = heights.size();
        return n == 0 ? Float.NaN : (n % 2 == 0 ?
            (heights.get(n / 2 - 1) + heights.get(n / 2)) / 2f : heights.get(n / 2));
    }

    private static float overlapY(Rect a, Rect b) {
        return Math.max(0f, Math.min(a.y1, b.y1) - Math.max(a.y0, b.y0));
    }

    private static List<Rect> anchorColumn(Rect target, List<Rect> horizontal,
            float typicalH, int imageWidth) {
        float near = Math.max(typicalH * 7.5f, imageWidth * .35f);
        List<Rect> nearby = new ArrayList<>();
        for (Rect h : horizontal) {
            if (h.cy < target.y0 + typicalH * .25f ||
                h.cy > target.y1 - typicalH * .25f) continue;
            float separation = Math.min(Math.abs(h.x0 - target.x1),
                Math.abs(target.x0 - h.x1));
            boolean oppositeSide = h.x0 > target.x1 + typicalH * .5f ||
                h.x1 < target.x0 - typicalH * .5f;
            if (!oppositeSide || separation > near) continue;
            nearby.add(h);
        }
        if (nearby.size() < 3) return Collections.emptyList();
        // Pick a coherent right/left aligned horizontal price/text column.
        for (Rect seed : nearby) {
            List<Rect> column = new ArrayList<>();
            for (Rect other : nearby) {
                if (Math.abs(other.cx - seed.cx) <= 1.6f * typicalH &&
                    (other.x0 > target.x1) == (seed.x0 > target.x1) &&
                    other.height >= .55f * typicalH && other.height <= 1.55f * typicalH) {
                    column.add(other);
                }
            }
            column.sort(Comparator.comparingDouble(a -> a.cy));
            List<Rect> distinctRows = new ArrayList<>();
            for (Rect candidate : column) {
                if (distinctRows.isEmpty() ||
                    candidate.cy - distinctRows.get(distinctRows.size() - 1).cy >= .75f * typicalH) {
                    distinctRows.add(candidate);
                }
            }
            if (distinctRows.size() >= 3) return distinctRows;
        }
        return Collections.emptyList();
    }

    public static Audit inspect(List<Box> boxes, int imageWidth, int imageHeight) {
        if (boxes == null || boxes.isEmpty() || imageWidth < 20 || imageHeight < 20)
            return new Audit(Collections.emptyList(), 0, 0, Float.NaN, "INSUFFICIENT_INPUT");
        if (boxes.size() > MAX_BOXES)
            return new Audit(Collections.emptyList(), 0, 0, Float.NaN, "CAPACITY_ABSTAIN");
        List<Rect> valid = new ArrayList<>();
        List<Rect> horiz = new ArrayList<>();
        for (Box box : boxes) {
            Rect r = read(box, imageWidth, imageHeight);
            if (r == null) continue;
            valid.add(r);
            if (r.width / r.height >= 1.65f && box.confidence >= .65f)
                horiz.add(r);
        }
        if (horiz.size() < 3)
            return new Audit(Collections.emptyList(), valid.size(), horiz.size(), Float.NaN,
                "INSUFFICIENT_HORIZONTAL_EVIDENCE");
        float typical = medianHeight(horiz);
        if (!Float.isFinite(typical) || typical < 6)
            return new Audit(Collections.emptyList(), valid.size(), horiz.size(), typical, "INSUFFICIENT_SCALE");
        // Restrict horizontal anchors to consistent normal-size lines, excluding headers.
        List<Rect> normalH = new ArrayList<>();
        for (Rect r : horiz) if (r.height >= typical * .55f && r.height <= typical * 1.55f)
            normalH.add(r);
        List<Rect> tall = new ArrayList<>();
        for (Rect r : valid) {
            if (r.height / r.width >= 2.6f && r.height >= 2.6f * typical)
                tall.add(r);
        }
        if (tall.size() < 3)
            return new Audit(Collections.emptyList(), valid.size(), normalH.size(), typical,
                "INSUFFICIENT_VERTICAL_CLUSTER");
        List<Alert> alerts = new ArrayList<>();
        for (Rect target : tall) {
            List<Integer> neighbors = new ArrayList<>();
            for (Rect other : tall) {
                if (target == other) continue;
                if (Math.abs(target.cx - other.cx) <= 4.5f * typical &&
                    overlapY(target, other) >= .55f * Math.min(target.height, other.height))
                    neighbors.add(other.original.sourceId);
            }
            if (neighbors.size() < 2) continue;
            List<Rect> column = anchorColumn(target, normalH, typical, imageWidth);
            if (column.size() < 3) continue;
            List<Integer> anchors = new ArrayList<>();
            for (Rect anchor : column) anchors.add(anchor.original.sourceId);
            Collections.sort(neighbors); Collections.sort(anchors);
            alerts.add(new Alert(target.original.sourceId, neighbors, anchors,
                target.height / target.width));
        }
        alerts.sort(Comparator.comparingInt(a -> a.sourceId));
        return new Audit(alerts, valid.size(), normalH.size(), typical,
            alerts.isEmpty() ? "NO_CONTEXTUAL_RISK_DETECTED" : "SUSPECT_ONLY_NO_TEXT_MUTATION");
    }
}
