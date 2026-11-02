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

    private static boolean isSingleHan(Box b) {
        return b.text.codePointCount(0, b.text.length()) == 1 &&
            Character.UnicodeScript.of(b.text.codePointAt(0)) == Character.UnicodeScript.HAN &&
            (b.right() - b.left()) >= b.actualHeight() * .45f &&
            (b.right() - b.left()) <= b.actualHeight() * 1.9f;
    }

    private static final class Vertical {
        final List<Box> items = new ArrayList<>();
        float x;
        float top;
        float bottom;
        Vertical(Box b) { items.add(b); x = b.centerX(); top = b.top(); bottom = b.bottom(); }
        void add(Box b) {
            x = (x * items.size() + b.centerX()) / (items.size() + 1);
            top = Math.min(top, b.top());
            bottom = Math.max(bottom, b.bottom());
            items.add(b);
        }
        String text() {
            items.sort(Comparator.comparingDouble(Box::centerY));
            StringBuilder out = new StringBuilder();
            for (Box b : items) out.append(b.text);
            return out.toString();
        }
        Box asBox() {
            float left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
            for (Box b : items) { left = Math.min(left, b.left()); right = Math.max(right, b.right()); }
            return new Box(text(), 1f,
                new float[]{left, right, right, left},
                new float[]{top, top, bottom, bottom});
        }
    }

    /** Group already-recognized upright Han glyphs by column, without rotating source pixels. */
    private static List<Vertical> verticalColumns(List<Box> all) {
        List<Box> candidates = new ArrayList<>();
        for (Box b : all) {
            if (!isSingleHan(b)) continue;
            boolean belongsToHorizontalLine = false;
            for (Box other : all) {
                if (other == b) continue;
                float dy = Math.abs(other.centerY() - b.centerY());
                float dx = Math.abs(other.centerX() - b.centerX());
                if (dy < b.actualHeight() * .35f &&
                        dx > b.actualHeight() * .6f && dx < b.actualHeight() * 1.5f &&
                        other.text.codePointCount(0, other.text.length()) > 1) {
                    belongsToHorizontalLine = true; break;
                }
            }
            if (!belongsToHorizontalLine) candidates.add(b);
        }
        candidates.sort(Comparator.comparingDouble(Box::centerX).reversed()
            .thenComparingDouble(Box::top));
        List<Vertical> columns = new ArrayList<>();
        for (Box b : candidates) {
            Vertical closest = null;
            float best = Float.MAX_VALUE;
            for (Vertical column : columns) {
                float delta = Math.abs(b.centerX() - column.x);
                if (delta <= b.actualHeight() * .60f && delta < best) { best = delta; closest = column; }
            }
            if (closest == null) columns.add(new Vertical(b)); else closest.add(b);
        }
        List<Vertical> real = new ArrayList<>();
        for (Vertical col : columns) {
            if (col.items.size() < 3) continue;
            col.items.sort(Comparator.comparingDouble(Box::centerY));
            float typical = col.items.get(col.items.size() / 2).actualHeight();
            int nearPairs = 0;
            for (int i = 1; i < col.items.size(); i++) {
                float distance = col.items.get(i).centerY() - col.items.get(i - 1).centerY();
                if (distance >= typical * .65f && distance <= typical * 2.6f) nearPairs++;
            }
            if (nearPairs >= col.items.size() - 2 && col.bottom - col.top >= typical * 2.7f)
                real.add(col);
        }
        real.sort(Comparator.comparingDouble((Vertical c) -> c.x).reversed());
        return real;
    }

    /** No confidence filtering here: losing a valid line is worse than exposing uncertain OCR text. */
    public static String arrange(List<Box> incoming) {
        return arrange(incoming, java.util.Collections.emptyList());
    }

    public static String arrange(List<Box> incoming, List<LayoutRegion> regions) {
        List<Box> boxes = new ArrayList<>();
        for (Box box : incoming) {
            if (box == null || box.x.length != 4 || box.y.length != 4 || box.text == null) continue;
            String clean = box.text.replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
            if (clean.isEmpty() || Float.isNaN(box.centerX()) || Float.isNaN(box.centerY())) continue;
            boxes.add(new Box(clean, box.confidence, box.x, box.y));
        }
        if (boxes.isEmpty()) return "";

        List<Vertical> vertical = verticalColumns(boxes);
        if (!vertical.isEmpty()) {
            // Traditional books: reading order is right-to-left by column and top-to-bottom.
            // Only activate page-wide vertical order when most detected glyphs belong
            // to reliable vertical columns, avoiding accidental changes to normal books.
            int verticalGlyphs = 0;
            for (Vertical col : vertical) verticalGlyphs += col.items.size();
            if (vertical.size() >= 2 && verticalGlyphs >= boxes.size() * .65f) {
                StringBuilder text = new StringBuilder();
                List<Box> unassigned = new ArrayList<>(boxes);
                for (Vertical col : vertical) {
                    if (text.length() > 0) text.append("\n\n");
                    text.append(col.text());
                    unassigned.removeAll(col.items);
                }
                // A small horizontal caption/number can coexist with vertical
                // paragraphs. Previous code silently lost every such box.
                // Append as a distinct flow rather than interrupting columns.
                if (!unassigned.isEmpty()) {
                    String additional = arrange(unassigned);
                    if (!additional.isEmpty()) text.append("\n\n").append(additional);
                }
                return text.toString();
            }
            // Packaging / signs: combine top-to-bottom characters into a complete word.
            List<Box> merged = new ArrayList<>(boxes);
            for (Vertical col : vertical) {
                merged.removeAll(col.items);
                merged.add(col.asBox());
            }
            boxes = merged;
        }

        // Baseline-aware line construction and text-stream reconstruction
        // belong in one geometric graph. A simple shared-X column heuristic
        // causes unrelated footnotes to be inserted into running prose.
        return TextFlowRebuilder.arrange(boxes, regions);

    }

    private static boolean asciiWord(char c) { return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'; }

    /** Return raw horizontal text regions for page-focus selection. */
    public static List<List<Box>> horizontalColumns(List<Box> boxes) {
        return splitColumns(boxes);
    }

    /**
     * Recover parallel reading streams even when a camera perspective leaves
     * only a VERY SMALL gutter (or lightly skewed quadrilaterals touch it).
     *
     * The old global-edge-gutter test could not split columns when a single
     * corner crossed the seam. Instead, group repeated horizontal OCR lines
     * by significant X-INTERVAL OVERLAP. A long line can join another line
     * in the same column, but a 2-5 px accidental edge intersection cannot
     * bridge two entire book pages.
     *
     * Only accept multiple columns if each one persists vertically and two
     * groups share a substantial Y range. An isolated title or footer is
     * assigned to the nearest group, rather than creating a false column.
     * Costs O(n^2) over boxes; no additional OCR invocation or image copy.
     */
    private static List<List<Box>> splitColumns(List<Box> boxes) {
        if (boxes.size() < 4) return Arrays.asList(boxes);
        List<Float> heights = new ArrayList<>();
        for (Box b : boxes) heights.add(b.actualHeight());
        heights.sort(Float::compare);
        float typicalHeight = heights.get(heights.size() / 2);
        float minAnchorWidth = Math.max(24f, typicalHeight * 2.5f);
        List<Float> widths = new ArrayList<>();
        for (Box b : boxes) {
            float width = b.right() - b.left();
            if (width >= minAnchorWidth) widths.add(width);
        }
        widths.sort(Float::compare);
        // A single page-spanning title must not bridge two otherwise separate
        // columns in the overlap graph. Keep that title in the output later.
        float ordinaryWide = widths.isEmpty() ? Float.MAX_VALUE :
            widths.get((int)Math.floor((widths.size() - 1) * .80f));
        float maxAnchorWidth = Math.max(ordinaryWide * 1.50f, typicalHeight * 9f);
        List<Integer> anchorIds = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            float width = b.right() - b.left();
            // Even on sparse two-page scenes, a full-width heading must not
            // connect left and right page columns into one stream.
            if (width >= minAnchorWidth && width <= maxAnchorWidth)
                anchorIds.add(i);
        }
        if (anchorIds.size() < 4) return Arrays.asList(boxes);
        int n = anchorIds.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            Box a = boxes.get(anchorIds.get(i));
            for (int j = i + 1; j < n; j++) {
                Box b = boxes.get(anchorIds.get(j));
                float overlap = Math.min(a.right(), b.right()) -
                    Math.max(a.left(), b.left());
                float narrow = Math.min(a.right() - a.left(), b.right() - b.left());
                // Do not connect two page columns through a 5px curved edge.
                if (overlap >= Math.max(typicalHeight * 0.35f, narrow * 0.37f)) {
                    union(parent, i, j);
                }
            }
        }

        List<Column> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int root = root(parent, i);
            Column group = null;
            for (Column c : candidates) if (c.root == root) { group = c; break; }
            if (group == null) {
                group = new Column(root);
                candidates.add(group);
            }
            group.add(boxes.get(anchorIds.get(i)));
        }
        List<Column> persistent = new ArrayList<>();
        for (Column c : candidates) {
            if (c.anchors.size() >= 2 &&
                c.maxBottom - c.minTop >= typicalHeight * 2f) {
                persistent.add(c);
            }
        }
        if (persistent.size() < 2) return Arrays.asList(boxes);

        // An actual multi-page image has concurrent text on left and right.
        // Isolated headings at a different Y level must not trigger splitting.
        List<Column> streams = new ArrayList<>();
        for (Column c : persistent) {
            for (Column other : persistent) {
                if (c == other) continue;
                float sharedHeight = Math.min(c.maxBottom, other.maxBottom) -
                                     Math.max(c.minTop, other.minTop);
                if (sharedHeight >= typicalHeight * 1.5f &&
                    Math.abs(c.centerX() - other.centerX()) >= typicalHeight * 1.8f) {
                    streams.add(c);
                    break;
                }
            }
        }
        if (streams.size() < 2) return Arrays.asList(boxes);
        streams.sort(Comparator.comparingDouble(Column::centerX));

        // Assign EVERY original box exactly once. Short isolated words and
        // punctuation were excluded only from the clustering *anchors*.
        // Page-wide titles ABOVE all streams are read first, not attached
        // to the middle of one column.
        List<Box> first = new ArrayList<>();
        List<Box> last = new ArrayList<>();
        float firstStreamTop = Float.MAX_VALUE;
        float lastStreamBottom = -Float.MAX_VALUE;
        for (Column c : streams) {
            firstStreamTop = Math.min(firstStreamTop, c.minTop);
            lastStreamBottom = Math.max(lastStreamBottom, c.maxBottom);
        }
        List<List<Box>> result = new ArrayList<>();
        for (int i = 0; i < streams.size(); i++) result.add(new ArrayList<>());
        for (Box box : boxes) {
            if (box.right() - box.left() > maxAnchorWidth) {
                if (box.bottom() < firstStreamTop + typicalHeight) {
                    first.add(box);
                    continue;
                }
                if (box.top() > lastStreamBottom - typicalHeight) {
                    last.add(box);
                    continue;
                }
            }
            int preferred = 0;
            float best = -Float.MAX_VALUE;
            for (int i = 0; i < streams.size(); i++) {
                Column c = streams.get(i);
                float overlap = Math.max(0f, Math.min(box.right(), c.maxRight) -
                    Math.max(box.left(), c.minLeft));
                float width = Math.max(1f, box.right() - box.left());
                // Prefer a stream with substantial shared X coverage.
                // Penalize a distant stream; a word touching a gutter must
                // not be swallowed by the next page merely due to Y alignment.
                float distance = Math.abs(box.centerX() - c.centerX());
                float score = overlap / width * 4f -
                    distance / Math.max(typicalHeight, width) * .35f;
                if (score > best) { best = score; preferred = i; }
            }
            result.get(preferred).add(box);
        }
        result.removeIf(List::isEmpty);
        if (result.size() < 2) return Arrays.asList(boxes);
        if (!first.isEmpty()) {
            first.sort(Comparator.comparingDouble(Box::top));
            result.add(0, first);
        }
        if (!last.isEmpty()) {
            last.sort(Comparator.comparingDouble(Box::top));
            result.add(last);
        }
        return result;
    }

    private static final class Column {
        final int root;
        final List<Box> anchors = new ArrayList<>();
        float minLeft = Float.MAX_VALUE, maxRight = -Float.MAX_VALUE;
        float minTop = Float.MAX_VALUE, maxBottom = -Float.MAX_VALUE;
        float weightedCenters;
        Column(int root) { this.root = root; }
        void add(Box b) {
            anchors.add(b);
            weightedCenters += b.centerX();
            minLeft = Math.min(minLeft, b.left());
            maxRight = Math.max(maxRight, b.right());
            minTop = Math.min(minTop, b.top());
            maxBottom = Math.max(maxBottom, b.bottom());
        }
        float centerX() { return weightedCenters / anchors.size(); }
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        a = root(parent, a);
        b = root(parent, b);
        if (a != b) parent[b] = a;
    }
}
