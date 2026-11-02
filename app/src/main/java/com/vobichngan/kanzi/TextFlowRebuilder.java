package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstruct coherent OCR text flows from perspective-skewed quadrilaterals.
 *
 * A stream is a connected set of text boxes, not a horizontal X "column".
 * Two independent streams must not be spliced just because their Y positions
 * happen to match. All valid OCR boxes are retained exactly once.
 *
 * Build geometry-only connectivity (no LLM guesses or fabricated characters).
 */
public final class TextFlowRebuilder {
    private TextFlowRebuilder() {}

    private static final class Row {
        final List<ReadingOrder.Box> boxes = new ArrayList<>();
        float xMin = Float.MAX_VALUE, xMax = -Float.MAX_VALUE;
        float weightedY, totalWeight, projectedY;
        void add(ReadingOrder.Box b) {
            boxes.add(b);
            xMin = Math.min(xMin, b.left());
            xMax = Math.max(xMax, b.right());
        }
        void complete(float referenceX) {
            for (ReadingOrder.Box b : boxes) {
                float weight = Math.max(10f, b.right() - b.left());
                projectedY += b.alignedY(referenceX) * weight;
                totalWeight += weight;
            }
            projectedY /= Math.max(1f, totalWeight);
            boxes.sort(Comparator.comparingDouble(ReadingOrder.Box::left));
        }
        String text() {
            StringBuilder s = new StringBuilder();
            ReadingOrder.Box prev = null;
            for (ReadingOrder.Box b : boxes) {
                if (prev != null && ascii(prev.text.charAt(prev.text.length() - 1)) &&
                    ascii(b.text.charAt(0)) && b.left() - prev.right() > 1f) s.append(' ');
                s.append(b.text);
                prev = b;
            }
            return s.toString();
        }
    }

    private static final class Flow {
        final List<ReadingOrder.Box> boxes = new ArrayList<>();
        final int id;
        Flow(int id) { this.id = id; }
        float minLeft = Float.MAX_VALUE;
        float maxRight = -Float.MAX_VALUE;
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        float maxHeight = 0;
        float totalHeight = 0;
        void add(ReadingOrder.Box b) {
            boxes.add(b);
            minLeft = Math.min(minLeft, b.left());
            maxRight = Math.max(maxRight, b.right());
            top = Math.min(top, b.top());
            bottom = Math.max(bottom, b.bottom());
            maxHeight = Math.max(maxHeight, b.actualHeight());
            totalHeight += b.actualHeight();
        }
        float centerX() { return (minLeft + maxRight) / 2f; }
        float typicalHeight() { return totalHeight / Math.max(1, boxes.size()); }
    }

    /** Default order is left-to-right by independent stream, then down. */
    public static String arrange(List<ReadingOrder.Box> input) {
        if (input == null || input.isEmpty()) return "";
        List<ReadingOrder.Box> boxes = new ArrayList<>(input);
        int n = boxes.size();
        int[] flowParent = new int[n];
        int[] rowParent = new int[n];
        for (int i = 0; i < n; i++) { flowParent[i] = i; rowParent[i] = i; }

        // Horizontal continuation: compare the two ENDPOINT baselines, not
        // box center Y. On a curved or oblique page a line can rise/fall 35px
        // across a single 180px word box.
        for (int i = 0; i < n; i++) {
            ReadingOrder.Box a = boxes.get(i);
            for (int j = i + 1; j < n; j++) {
                ReadingOrder.Box b = boxes.get(j);
                if (sameVisualRow(a,b)) {
                    unite(rowParent,i,j);
                    unite(flowParent,i,j);
                }
                // Vertical continuity: repeated lines share substantial
                // horizontal ink, similar font scale and nearby baselines.
                else if (adjacentVertical(a,b)) {
                    unite(flowParent,i,j);
                }
            }
        }

        // Adjacent paragraphs of the SAME region may have a large gap.
        // The first graph intentionally uses conservative local edges; merge
        // separate pieces only when their X corridors, font scale and Y
        // intervals agree. This fixes runs of prose that jumped backwards
        // after a blank line, without joining nearby marginal notes.
        Map<Integer,Flow> prelim = new HashMap<>();
        Map<Integer,Integer> firstIndex = new HashMap<>();
        for (int i=0;i<n;i++) {
            int id=root(flowParent,i);
            prelim.computeIfAbsent(id,k->new Flow(k)).add(boxes.get(i));
            firstIndex.putIfAbsent(id,i);
        }
        List<Flow> parts = new ArrayList<>(prelim.values());
        for(int i=0;i<parts.size();i++) for(int j=i+1;j<parts.size();j++) {
            Flow a=parts.get(i),b=parts.get(j);
            // Never use a singleton title, page number or isolated noisy
            // fragment as a bridge joining two unrelated columns.
            if (a.boxes.size()<2 || b.boxes.size()<2) continue;
            float widthA=a.maxRight-a.minLeft,widthB=b.maxRight-b.minLeft;
            float overlap=Math.min(a.maxRight,b.maxRight)-Math.max(a.minLeft,b.minLeft);
            if (overlap < Math.min(widthA,widthB)*.54f) continue;
            float hRatio=Math.min(a.typicalHeight(),b.typicalHeight())/
                Math.max(a.typicalHeight(),b.typicalHeight());
            if (hRatio<.73f) continue;
            float overlapY=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top);
            float spanA=a.bottom-a.top,spanB=b.bottom-b.top;
            if(overlapY>Math.min(spanA,spanB)*.46f) continue;
            float gap=Math.max(0f,Math.max(a.top-b.bottom,b.top-a.bottom));
            if(gap>Math.max(a.typicalHeight(),b.typicalHeight())*8f) continue;
            unite(flowParent,firstIndex.get(a.id),firstIndex.get(b.id));
        }
        Map<Integer,Flow> flows = new HashMap<>();
        for (int i=0;i<n;i++)
            flows.computeIfAbsent(root(flowParent,i), k->new Flow(k)).add(boxes.get(i));
        List<Flow> ordered = new ArrayList<>(flows.values());
        // A stand-alone wide heading ABOVE all repeated streams is read first,
        // not between the left and right columns based on its center X.
        float earliestRepeated = Float.MAX_VALUE, widestRepeated = 0f;
        for (Flow f : ordered) if (f.boxes.size() >= 2) {
            earliestRepeated = Math.min(earliestRepeated,f.top);
            widestRepeated = Math.max(widestRepeated,f.maxRight-f.minLeft);
        }
        final float firstRepeatedTop = earliestRepeated;
        final float repeatedMaxWidth = widestRepeated;
        // Separate text regions (including small annotations) can all be
        // spoken, but never mixed INTO a different region's sentence.
        ordered.sort((aFlow,bFlow) -> {
            boolean aTitle = aFlow.boxes.size()==1 &&
                aFlow.maxRight-aFlow.minLeft > repeatedMaxWidth*1.45f &&
                aFlow.top+aFlow.maxHeight*1.25f < firstRepeatedTop;
            boolean bTitle = bFlow.boxes.size()==1 &&
                bFlow.maxRight-bFlow.minLeft > repeatedMaxWidth*1.45f &&
                bFlow.top+bFlow.maxHeight*1.25f < firstRepeatedTop;
            if(aTitle != bTitle) return aTitle?-1:1;
            int cmp = Float.compare(aFlow.centerX(),bFlow.centerX());
            return cmp!=0?cmp:Float.compare(aFlow.top,bFlow.top);
        });
        StringBuilder output = new StringBuilder();
        for (Flow flow : ordered) {
            // Convert global connected row IDs into this stream's local rows.
            Map<Integer,Row> rows = new HashMap<>();
            for (int i=0;i<n;i++) {
                ReadingOrder.Box b = boxes.get(i);
                if (root(flowParent,i) != flow.id) continue;
                rows.computeIfAbsent(root(rowParent,i),k->new Row()).add(b);
            }
            float pivot = flow.centerX();
            List<Row> list = new ArrayList<>(rows.values());
            for (Row row : list) row.complete(pivot);
            list.sort(Comparator.comparingDouble((Row row)->row.projectedY)
                .thenComparingDouble(row->row.xMin));
            if (output.length() > 0) output.append("\n\n");
            for (int i=0;i<list.size();i++) {
                if (i>0) output.append("\n");
                output.append(list.get(i).text());
            }
        }
        return output.toString().trim();
    }

    private static boolean sameVisualRow(ReadingOrder.Box aa,ReadingOrder.Box bb) {
        ReadingOrder.Box a=aa.left()<=bb.left()?aa:bb;
        ReadingOrder.Box b=a==aa?bb:aa;
        float widthA=a.right()-a.left(), widthB=b.right()-b.left();
        if (widthA<=0 || widthB<=0) return false;
        float ha=a.actualHeight(),hb=b.actualHeight();
        float heightRatio=Math.min(ha,hb)/Math.max(ha,hb);
        if (heightRatio < .72f) return false;
        float dx=b.left()-a.right();
        // Do not bridge an entire other column or distant sign.
        if (dx > Math.max(7f,Math.min(ha,hb)*.60f) ||
            dx < -Math.min(widthA,widthB)*.32f) return false;
        float seamX=(a.right()+b.left())/2f;
        float dy=Math.abs(a.alignedY(seamX)-b.alignedY(seamX));
        return dy < Math.max(3.2f,Math.min(ha,hb)*.39f);
    }

    private static boolean adjacentVertical(ReadingOrder.Box a,ReadingOrder.Box b) {
        float wA=a.right()-a.left(),wB=b.right()-b.left();
        if (wA<=0 || wB<=0) return false;
        float ha=a.actualHeight(),hb=b.actualHeight();
        float ratio=Math.min(ha,hb)/Math.max(ha,hb);
        if (ratio<.70f) return false;
        float overlap=Math.min(a.right(),b.right())-Math.max(a.left(),b.left());
        if (overlap < Math.max(7f, Math.min(wA,wB)*.28f)) return false;
        float pivot=(Math.max(a.left(),b.left())+Math.min(a.right(),b.right()))/2f;
        float dy=Math.abs(a.alignedY(pivot)-b.alignedY(pivot));
        // Very wide section headers may span multiple independent columns.
        // Prevent them from acting as a bridge across both columns below.
        float widthRatio=Math.max(wA,wB)/Math.max(1f,Math.min(wA,wB));
        if (widthRatio>1.45f && dy>Math.max(ha,hb)*2.2f) return false;
        return dy>=Math.min(ha,hb)*.69f &&
            dy<=Math.max(ha,hb)*4.3f;
    }

    private static boolean ascii(char c) {
        return (c>='0'&&c<='9')||(c>='A'&&c<='Z')||(c>='a'&&c<='z');
    }

    private static int root(int[] p,int i) {
        while(p[i]!=i){p[i]=p[p[i]];i=p[i];}
        return i;
    }
    private static void unite(int[] p,int a,int b) {
        a=root(p,a);b=root(p,b);if(a!=b)p[b]=a;
    }
}
