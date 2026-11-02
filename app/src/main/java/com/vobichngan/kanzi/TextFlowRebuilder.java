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
        float top = Float.MAX_VALUE;
        float maxHeight = 0;
        void add(ReadingOrder.Box b) {
            boxes.add(b);
            minLeft = Math.min(minLeft, b.left());
            maxRight = Math.max(maxRight, b.right());
            top = Math.min(top, b.top());
            maxHeight = Math.max(maxHeight, b.actualHeight());
        }
        float centerX() { return (minLeft + maxRight) / 2f; }
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

        Map<Integer,Flow> flows = new HashMap<>();
        for (int i=0;i<n;i++)
            flows.computeIfAbsent(root(flowParent,i), k->new Flow(k)).add(boxes.get(i));
        List<Flow> ordered = new ArrayList<>(flows.values());
        // Separate text regions (including small annotations) can all be
        // spoken, but never mixed INTO a different region's sentence.
        ordered.sort(Comparator.comparingDouble(Flow::centerX)
            .thenComparingDouble(f -> f.top));
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
