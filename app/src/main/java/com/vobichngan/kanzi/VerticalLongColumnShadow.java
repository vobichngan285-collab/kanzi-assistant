package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Diagnostics-only reading order hypothesis for OCR that already recognized
 * an entire traditional Chinese vertical column in ONE tall quadrilateral.
 *
 * This does NOT infer the semantics of the text, rotate pixels, run REC, change
 * live OCR, or send text to TTS. A sideways horizontal book can also produce
 * tall OCR quadrilaterals: this proposal is not proof of true vertical text.
 * The separate live gate must verify orientation cues before adopting it.
 */
public final class VerticalLongColumnShadow {
    private VerticalLongColumnShadow() {}

    private static final class Entry {
        final int id;
        final ReadingOrder.Box box;
        final float cx, top, bottom, width, height;
        Entry(int id, ReadingOrder.Box b) {
            this.id = id; this.box = b;
            cx = b.centerX(); top = b.top(); bottom = b.bottom();
            width = b.right() - b.left(); height = bottom - top;
        }
    }

    public static final class Result {
        public final boolean proposed;
        public final String reason;
        // Original zero-based OCR indices; no synthesized or substituted items.
        public final List<Integer> columnIds;
        public final List<Integer> remainingIds;
        public final List<Integer> allIds;
        public final boolean exactPermutation;

        private Result(boolean proposed, String reason, List<Integer> columns,
                       List<Integer> remaining, List<Integer> all, int originalCount) {
            this.proposed = proposed;
            this.reason = reason;
            this.columnIds = Collections.unmodifiableList(new ArrayList<>(columns));
            this.remainingIds = Collections.unmodifiableList(new ArrayList<>(remaining));
            this.allIds = Collections.unmodifiableList(new ArrayList<>(all));
            Set<Integer> seen = new HashSet<>(all);
            boolean inRange = true;
            for (int id : all) if (id < 0 || id >= originalCount) inRange = false;
            this.exactPermutation = all.size() == originalCount &&
                seen.size() == originalCount && inRange;
        }

        /** Never use this output as evidence that the content is true vertical. */
        public String describe(List<ReadingOrder.Box> original) {
            StringBuilder out = new StringBuilder();
            out.append(proposed ? "有长竖列几何候选：" : "未触发：");
            out.append(reason).append('\n');
            if (!proposed) {
                out.append("保留现有诊断和正式朗读顺序；不补字、不旋转、不增加推理。");
                return out.toString();
            }
            out.append("仅供人工核对：竖排文字应从右至左逐列读；横排书页整体旋转也可能产生相似几何。\n");
            out.append("候选长列原始OCR框：");
            for (int i = 0; i < columnIds.size(); i++) {
                if (i > 0) out.append(" → ");
                out.append('#').append(columnIds.get(i) + 1);
            }
            out.append("\n候选正文（原始OCR字符，按列重排）：\n");
            for (int i = 0; i < columnIds.size(); i++) {
                if (i > 0) out.append("\n\n");
                String item = original.get(columnIds.get(i)).text;
                if (item != null) out.append(item);
            }
            out.append("\n\n其他原始OCR框：").append(remainingIds.size())
                .append("个（保持原框顺序，不混入竖排正文；不代表它们是噪声）");
            for (int id : remainingIds) {
                out.append("\n#").append(id + 1).append(" ");
                String item = original.get(id).text;
                if (item != null) out.append(item);
            }
            out.append("\nOCR框编号完整性校验=")
                .append(exactPermutation ? "通过" : "失败，候选无效");
            return out.toString();
        }
    }

    private static Result reject(String reason, int n) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(i);
        return new Result(false, reason, Collections.emptyList(), ids, ids, n);
    }

    /**
     * Conservative long-column geometry candidate, NOT automatic adoption.
     * In particular, this cannot prove that the source was typeset vertically.
     */
    public static Result inspect(List<ReadingOrder.Box> original) {
        if (original == null || original.isEmpty()) return reject("无文字框", 0);
        int n = original.size();
        if (n < 4) return reject("检测框不足4个", n);
        List<Entry> longColumns = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ReadingOrder.Box b = original.get(i);
            if (b == null || b.text == null || b.x.length != 4 || b.y.length != 4) {
                return reject("存在无法定位的原始OCR框", n);
            }
            for (float v : b.x) if (!Float.isFinite(v))
                return reject("存在非有限的文字框坐标", n);
            for (float v : b.y) if (!Float.isFinite(v))
                return reject("存在非有限的文字框坐标", n);
            Entry e = new Entry(i, b);
            if (e.width <= 0 || e.height <= 0) continue;
            int codepoints = b.text.codePointCount(0, b.text.length());
            // A separately typeset vertical English title can resemble a body
            // column geometrically, but must not restrict the shared overlap
            // of Chinese traditional columns. No language completion: only
            // Unicode script of the already recognized OCR characters.
            int han = 0;
            for (int pos=0;pos<b.text.length();) {
                int c=b.text.codePointAt(pos);
                if (Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN) han++;
                pos+=Character.charCount(c);
            }
            if (codepoints >= 9 && han>=6 && han*10>=codepoints*7 &&
                e.height >= 160f && e.width >= 8f &&
                e.height >= e.width * 4.0f && b.confidence >= 0.45f) {
                longColumns.add(e);
            }
        }
        if (longColumns.size() < 4) return reject(
            "长竖列不足4条，避免对普通横排或局部文字误排序", n);
        // An unusual number of long columns suggests either mixed pages or
        // many near-duplicate detections; do not present a certified page flow.
        if (longColumns.size() > 20) return reject(
            "长竖列过多，疑似混合区域，需单独分区", n);
        List<Float> widths = new ArrayList<>(), heights = new ArrayList<>();
        float commonTop = -Float.MAX_VALUE, commonBottom = Float.MAX_VALUE;
        for (Entry e : longColumns) {
            // Perspective tilt inflates the axis-aligned X bounding box:
            // a 35px wide column can cover 100px in X from top to bottom.
            // Measure actual *short edge thickness*, not its X bounding span.
            float first = (float)Math.hypot(
                e.box.x[1]-e.box.x[0], e.box.y[1]-e.box.y[0]);
            float second = (float)Math.hypot(
                e.box.x[2]-e.box.x[1], e.box.y[2]-e.box.y[1]);
            widths.add(Math.min(first,second));
            heights.add(e.height);
            commonTop = Math.max(commonTop, e.top);
            commonBottom = Math.min(commonBottom, e.bottom);
        }
        Collections.sort(widths);
        Collections.sort(heights);
        float medianWidth = widths.get(widths.size() / 2);
        float medianHeight = heights.get(heights.size() / 2);
        if (commonBottom - commonTop < medianHeight * 0.35f)
            return reject("长列纵向共同覆盖不足，可能属于不同阅读区域", n);
        longColumns.sort(Comparator.comparingDouble((Entry e) -> e.cx)
            .reversed().thenComparingInt(e -> e.id));
        for (int i = 1; i < longColumns.size(); i++) {
            float dx = longColumns.get(i - 1).cx - longColumns.get(i).cx;
            // Distinct columns, rather than overlapping duplicate detections.
            if (dx < Math.max(12f,medianWidth * .70f) ||
                dx > Math.max(120f,medianWidth * 5.0f))
                return reject("列间距不稳定或存在多个区域，不强行拼接", n);
        }
        List<Integer> columns = new ArrayList<>(), others = new ArrayList<>();
        Set<Integer> selected = new HashSet<>();
        for (Entry e : longColumns) {
            columns.add(e.id); selected.add(e.id);
        }
        for (int i = 0; i < n; i++) if (!selected.contains(i)) others.add(i);
        List<Integer> all = new ArrayList<>(columns);
        all.addAll(others);
        Result result = new Result(true, longColumns.size() +
            "列，按列中心X从右至左排列；不修改列内任何字", columns, others, all, n);
        return result.exactPermutation ? result : reject("原始OCR框未能完整保留", n);
    }
}
