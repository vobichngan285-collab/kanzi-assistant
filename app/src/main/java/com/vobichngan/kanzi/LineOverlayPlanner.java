package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Isolated, geometry-only candidate for displaying ONE outline per OCR text row.
 *
 * No model inference, text recognition, camera work, TTS changes or UI mutation.
 * The real-time reading order is intentionally NOT changed by this experiment.
 *
 * A line retains every original OCR box index, original text and raw quadrilateral.
 * If an attachment is geometrically doubtful, the source boxes remain separate.
 */
public final class LineOverlayPlanner {
    private LineOverlayPlanner() {}

    public static final class Line {
        public final int[] sourceIds;
        public final String rawText;
        public final float[] outline;
        public final boolean merged;
        public final boolean drawable;

        private Line(int[] ids, String text, float[] quad, boolean merged, boolean drawable) {
            this.sourceIds = ids.clone();
            this.rawText = text;
            this.outline = quad.clone();
            this.merged = merged;
            this.drawable = drawable;
        }
    }

    public static final class Plan {
        public final List<Line> lines;
        public final int originalBoxCount;
        public final int mergedLineCount;
        private Plan(List<Line> rows, int boxCount) {
            lines = Collections.unmodifiableList(new ArrayList<>(rows));
            originalBoxCount = boxCount;
            int count = 0;
            for (Line row : lines) if (row.merged) count++;
            mergedLineCount = count;
        }
    }

    private static final class Group {
        final List<Integer> ids = new ArrayList<>();
        Group(int id) { ids.add(id); }
        int last() { return ids.get(ids.size() - 1); }
        int first() { return ids.get(0); }
    }

    /**
     * Shadow-only operation: no alterations to input boxes, SDK OCR text, or TTS.
     * Source IDs refer to the original OCR result positions (0-based).
     */
    public static Plan plan(List<ReadingOrder.Box> original) {
        if (original == null || original.isEmpty())
            return new Plan(Collections.emptyList(), 0);

        final int n = original.size();
        // Reuse the exact same baseline and crowded-strip predicates as the
        // existing TextFlowRebuilder; do not silently invent new row geometry.
        List<ReadingOrder.Box> geometry = new ArrayList<>(n);
        for (ReadingOrder.Box b : original) {
            // The production flow accepts non-null, valid ReadingOrder.Box.
            // Preserve even unpositionable entries as singleton, non-drawable rows.
            geometry.add(b == null ? invalidPlaceholder() : b);
        }
        boolean[] crowded = TextFlowRebuilder.denseTextStrips(geometry);
        List<Integer> ordered = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ordered.add(i);
        ordered.sort(Comparator.comparingDouble((Integer id) -> safeLeft(original.get(id)))
            .thenComparingDouble(id -> safeTop(original.get(id)))
            .thenComparingInt(id -> id));

        List<Group> groups = new ArrayList<>();
        for (int id : ordered) {
            ReadingOrder.Box box = original.get(id);
            if (!eligible(box)) {
                groups.add(new Group(id));
                continue;
            }
            Group best = null;
            float bestDistance = Float.MAX_VALUE;
            float secondDistance = Float.MAX_VALUE;
            for (Group group : groups) {
                ReadingOrder.Box previous = original.get(group.last());
                ReadingOrder.Box first = original.get(group.first());
                if (!eligible(previous) || !eligible(first)) continue;
                if (!TextFlowRebuilder.sameVisualRow(previous, box,
                        crowded[group.last()], crowded[id])) continue;
                float h = Math.min(first.actualHeight(), box.actualHeight());
                float gap = box.left() - previous.right();
                // Overlapping/duplicate OCR polygons must NOT be visually merged.
                if (gap < -Math.min(previous.right() - previous.left(),
                        box.right() - box.left()) * .12f) continue;
                if (Math.abs(first.slope() - box.slope()) > .08f) continue;
                float aligned = Math.abs(first.alignedY(first.centerX()) -
                        box.alignedY(first.centerX()));
                if (aligned > Math.max(2.5f, h * .30f)) continue;
                // A large difference in font height makes a row merger unsafe.
                if (Math.min(first.actualHeight(), box.actualHeight()) /
                        Math.max(first.actualHeight(), box.actualHeight()) < .74f) continue;
                float distance = aligned + Math.max(0, gap) * .02f;
                if (distance < bestDistance) {
                    secondDistance = bestDistance;
                    bestDistance = distance;
                    best = group;
                } else if (distance < secondDistance) {
                    secondDistance = distance;
                }
            }
            // Ambiguous multiple plausible rows: keep a separate visible box.
            if (best == null || secondDistance - bestDistance < 1.5f) {
                groups.add(new Group(id));
            } else {
                best.ids.add(id);
            }
        }

        List<Line> output = new ArrayList<>();
        for (Group group : groups) output.add(materialize(original, group.ids));
        output.sort(Comparator.comparingDouble((Line line) -> outlineTop(line.outline))
            .thenComparingDouble(line -> outlineLeft(line.outline))
            .thenComparingInt(line -> line.sourceIds[0]));
        return new Plan(output, n);
    }

    private static ReadingOrder.Box invalidPlaceholder() {
        return new ReadingOrder.Box("", 0f, new float[]{0, 0, 0, 0},
                new float[]{0, 0, 0, 0});
    }

    private static float safeLeft(ReadingOrder.Box box) {
        return valid(box) ? box.left() : Float.POSITIVE_INFINITY;
    }
    private static float safeTop(ReadingOrder.Box box) {
        return valid(box) ? box.top() : Float.POSITIVE_INFINITY;
    }
    private static boolean valid(ReadingOrder.Box box) {
        if (box == null || box.x == null || box.y == null ||
            box.x.length != 4 || box.y.length != 4) return false;
        for (int i = 0; i < 4; i++) {
            if (!Float.isFinite(box.x[i]) || !Float.isFinite(box.y[i])) return false;
        }
        return true;
    }
    private static boolean eligible(ReadingOrder.Box box) {
        if (!valid(box) || box.text == null || box.text.trim().isEmpty()) return false;
        float w = box.right() - box.left(), h = box.actualHeight();
        return w > 2f && h > 1f && w >= h * .78f &&
            Math.abs(box.slope()) <= .27f;
    }

    private static Line materialize(List<ReadingOrder.Box> boxes, List<Integer> ids) {
        int[] originals = new int[ids.size()];
        StringBuilder txt = new StringBuilder();
        for (int j = 0; j < ids.size(); j++) {
            originals[j] = ids.get(j);
            String fragment = boxes.get(ids.get(j)) == null ? null : boxes.get(ids.get(j)).text;
            if (fragment != null) txt.append(fragment);
        }
        ReadingOrder.Box first = boxes.get(ids.get(0));
        if (ids.size() == 1 || !eligible(first)) {
            return new Line(originals, txt.toString(), exactQuad(first), false, valid(first));
        }

        // A projected parallelogram encloses every source corner, including
        // slight text-line tilt. Never crop recognition from this display quad.
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        for (int id : ids) {
            ReadingOrder.Box b = boxes.get(id);
            minX = Math.min(minX, b.left());
            maxX = Math.max(maxX, b.right());
        }
        float slope = first.slope();
        float upper = Float.POSITIVE_INFINITY, lower = Float.NEGATIVE_INFINITY;
        for (int id : ids) {
            ReadingOrder.Box b = boxes.get(id);
            for (int j = 0; j < 4; j++) {
                float projected = b.y[j] - slope * (b.x[j] - minX);
                upper = Math.min(upper, projected);
                lower = Math.max(lower, projected);
            }
        }
        if (!Float.isFinite(upper) || !Float.isFinite(lower) ||
            minX >= maxX || upper >= lower) {
            // Defensive fallback: do not fabricate an outline from broken data.
            return new Line(originals, txt.toString(), exactQuad(first), false, false);
        }
        float shift = slope * (maxX - minX);
        float[] merged = new float[]{
            minX, upper, maxX, upper + shift,
            maxX, lower + shift, minX, lower
        };
        return new Line(originals, txt.toString(), merged, true, true);
    }

    private static float[] exactQuad(ReadingOrder.Box box) {
        if (!valid(box)) return new float[8];
        float[] quad = new float[8];
        for (int i = 0; i < 4; i++) {
            quad[2 * i] = box.x[i];
            quad[2 * i + 1] = box.y[i];
        }
        return quad;
    }
    private static float outlineTop(float[] p) {
        return Math.min(Math.min(p[1], p[3]), Math.min(p[5], p[7]));
    }
    private static float outlineLeft(float[] p) {
        return Math.min(Math.min(p[0], p[2]), Math.min(p[4], p[6]));
    }
}
