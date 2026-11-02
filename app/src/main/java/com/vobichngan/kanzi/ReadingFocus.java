package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Decide WHICH visible book region should be spoken, independently of OCR.
 *
 * All detected OCR boxes remain available for diagnostics and preview overlays.
 * A partial neighboring page and narrow marginal notes must not precede the
 * complete body page just because their X coordinate is smaller.
 *
 * No additional image/recognition pass: O(n^2) book-line geometry only.
 */
public final class ReadingFocus {
    private ReadingFocus() {}

    public enum Mode { AUTO, ALL, LEFT, RIGHT }

    public static final class Selection {
        public final String text;
        public final String description;
        public final int selectedBoxes;
        public final int totalBoxes;
        public final float centerFraction;
        public final boolean isolated;
        Selection(String text, String description, int selectedBoxes, int totalBoxes,
                  float centerFraction, boolean isolated) {
            this.text = text;
            this.description = description;
            this.selectedBoxes = selectedBoxes;
            this.totalBoxes = totalBoxes;
            this.centerFraction = centerFraction;
            this.isolated = isolated;
        }
    }

    public static final class Tracker {
        private float previousCenter = Float.NaN;
        private int previousWidth;
        private int previousHeight;
        private Mode previousMode = Mode.AUTO;

        public synchronized void reset() {
            previousCenter = Float.NaN;
            previousWidth = 0;
            previousHeight = 0;
        }

        public synchronized Selection choose(List<ReadingOrder.Box> boxes, int width,
                                              int height, Mode mode) {
            return choose(boxes,width,height,mode,java.util.Collections.emptyList());
        }

        public synchronized Selection choose(List<ReadingOrder.Box> boxes, int width,
                                              int height, Mode mode,
                                              List<LayoutRegion> regions) {
            if (boxes == null) boxes = Collections.emptyList();
            if (mode == null) mode = Mode.AUTO;
            if (mode != previousMode || width != previousWidth || height != previousHeight) {
                reset();
            }
            previousMode = mode;
            previousWidth = width;
            previousHeight = height;
            Selection chosen = ReadingFocus.choose(boxes, width, height, mode, previousCenter, regions);
            if (chosen.isolated && mode == Mode.AUTO) previousCenter = chosen.centerFraction;
            return chosen;
        }
    }

    public static Selection choose(List<ReadingOrder.Box> boxes, int width,
                                   int height, Mode mode, float previousCenter) {
        return choose(boxes,width,height,mode,previousCenter,
            java.util.Collections.emptyList());
    }

    public static Selection choose(List<ReadingOrder.Box> boxes, int width,
                                   int height, Mode mode, float previousCenter,
                                   List<LayoutRegion> regions) {
        if (boxes == null || boxes.isEmpty()) return new Selection("", "未发现文字", 0, 0, .5f, false);
        String entire = mode == Mode.AUTO ?
            ReadingOrder.arrange(boxes, regions) : ReadingOrder.arrange(boxes);
        // AUTO is a continuity-first READING mode, not a guess about the
        // user's preferred page. Keep every recognized character available.
        // Background objects may appear in independent flows, but are never
        // silently discarded merely for being shorter, left/right or smaller.
        // LEFT/RIGHT remain explicit user-directed filters only.
        if (mode == Mode.AUTO || mode == Mode.ALL || width <= 0 || height <= 0)
            return new Selection(entire, (mode == Mode.AUTO && regions != null && !regions.isEmpty() ?
                    "AI版面辅助+文字流（不丢弃有效识别框）" :
                    "连续文字流（不丢弃有效识别框）"),
                boxes.size(), boxes.size(), .5f, false);

        // A horizontal book body has repeated LONG recognized lines. Exclude
        // isolated titles/short punctuation as grouping anchors, but preserve
        // every original box in the unfiltered full-reading fallback.
        List<List<ReadingOrder.Box>> groups = ReadingOrder.horizontalColumns(boxes);
        if (groups.size() < 2)
            return new Selection(entire, "完整单区（全部阅读）", boxes.size(), boxes.size(), .5f, false);

        List<Region> candidates = new ArrayList<>();
        for (List<ReadingOrder.Box> group : groups) {
            Region region = new Region(group, width, height);
            if (region.bodyRows >= 5 && region.bodyCharacters >= 38 &&
                region.bodyBottom - region.bodyTop >= region.medianHeight * 4f &&
                region.medianWidth >= region.medianHeight * 3f)
                candidates.add(region);
        }
        if (candidates.size() < 2)
            return new Selection(entire, "没有可靠的多页分区（全部阅读）",
                boxes.size(), boxes.size(), .5f, false);

        candidates.sort(Comparator.comparingDouble(r -> r.center));
        Region strongest = null;
        for (Region r : candidates)
            if (strongest == null || r.score > strongest.score) strongest = r;

        // Only enable book-page focus for sufficiently substantial continuous
        // text. Avoid turning a supermarket label/sign into a single column.
        if (strongest.bodyRows < 7 || strongest.bodyCharacters < 90)
            return new Selection(entire, "非连续书页（全部阅读）",
                boxes.size(), boxes.size(), .5f, false);

        // Manual left/right should choose a meaningful book page rather than
        // a narrow marginal annotation at the farthest screen edge.
        List<Region> pages = new ArrayList<>();
        for (Region r : candidates) {
            if (r.bodyCharacters >= 50 &&
                r.score >= strongest.score * .07f &&
                r.medianHeight >= strongest.medianHeight * .78f)
                pages.add(r);
        }
        if (pages.isEmpty()) pages.add(strongest);

        Region selected = strongest;
        if (mode == Mode.LEFT) selected = pages.get(0);
        else if (mode == Mode.RIGHT) selected = pages.get(pages.size() - 1);
        else if (!Float.isNaN(previousCenter)) {
            // Hysteresis: retain the previously spoken page through small
            // camera movements unless another page is substantially stronger.
            for (Region r : pages) {
                if (Math.abs(r.center / width - previousCenter) < .16f &&
                    r.score >= strongest.score * .78f) {
                    selected = r;
                    break;
                }
            }
        }

        // Suppress only tiny, low-confidence isolated fragments in an
        // otherwise well-established body region. Do not discard whole lines
        // merely for low confidence: that would silently truncate books.
        List<ReadingOrder.Box> speakingBoxes = new ArrayList<>();
        for (ReadingOrder.Box box : selected.boxes) {
            if (box.confidence < .40f && box.text.length() <= 6 &&
                box.right() - box.left() < selected.medianWidth * .52f)
                continue;
            speakingBoxes.add(box);
        }
        String focused = ReadingOrder.arrange(speakingBoxes);
        if (focused.isEmpty())
            return new Selection(entire, "正文候选为空（全部阅读）",
                boxes.size(), boxes.size(), .5f, false);
        String side = selected.center < width * .45f ? "偏左" :
                      selected.center > width * .55f ? "偏右" : "居中";
        String description = (mode == Mode.AUTO ? "智能正文" :
                mode == Mode.LEFT ? "指定左侧正文" : "指定右侧正文") +
            "，" + side + "，选取" + speakingBoxes.size() +
            "/" + boxes.size() + "个文字框";
        return new Selection(focused, description, speakingBoxes.size(),
            boxes.size(), selected.center / width, true);
    }

    private static final class Region {
        final List<ReadingOrder.Box> boxes;
        final float center;
        final float medianHeight;
        final float medianWidth;
        final float bodyTop;
        final float bodyBottom;
        final int bodyRows;
        final int bodyCharacters;
        final float score;

        Region(List<ReadingOrder.Box> group, int width, int height) {
            boxes = group;
            List<Float> heights = new ArrayList<>();
            for (ReadingOrder.Box box : group) heights.add(box.actualHeight());
            heights.sort(Float::compare);
            float typical = heights.get(heights.size() / 2);
            List<Float> bodyWidths = new ArrayList<>();
            int rows = 0, characters = 0;
            float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
            float weight = 0f, cx = 0f;
            for (ReadingOrder.Box box : group) {
                float w = box.right() - box.left();
                if (w < Math.max(25f, typical * 2.8f) || box.text.length() < 3)
                    continue;
                rows++;
                characters += box.text.codePointCount(0, box.text.length());
                bodyWidths.add(w);
                top = Math.min(top, box.top());
                bottom = Math.max(bottom, box.bottom());
                float ink = Math.min(w, width * .65f);
                weight += ink;
                cx += box.centerX() * ink;
            }
            bodyWidths.sort(Float::compare);
            bodyRows = rows;
            bodyCharacters = characters;
            medianHeight = typical;
            medianWidth = bodyWidths.isEmpty() ? 0f :
                bodyWidths.get(bodyWidths.size() / 2);
            bodyTop = top;
            bodyBottom = bottom;
            center = weight > 0 ? cx / weight : width / 2f;
            // An edge-cropped partial page tends to have shorter rows, fewer
            // characters and often touches the image boundary. A footnote
            // has much less main-text ink than the body.
            float edge = Math.min(center, width - center) / Math.max(1f, width);
            float edgeFactor = .80f + Math.min(.20f, edge * .65f);
            float span = (bottom > top) ? Math.min(1.1f, (bottom - top) /
                Math.max(1f, height * .48f)) : .1f;
            score = (characters + .035f * weight) * edgeFactor *
                (.75f + .25f * span);
        }
    }
}
