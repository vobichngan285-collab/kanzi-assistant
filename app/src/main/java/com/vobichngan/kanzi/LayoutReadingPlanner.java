package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/**
 * Standalone, deterministic OCR box -> layout region -> reading-order planner.
 * It NEVER generates text; final output must pass StrictOcrOrderGuard.
 * This is a candidate planner, not yet connected to camera/TTS.
 */
public final class LayoutReadingPlanner {
    private LayoutReadingPlanner() {}

    public static final class Placement {
        public final int ocrId;
        public final int regionId; // -1 means geometric fallback
        public final int readingIndex; // 0-based
        public Placement(int ocrId, int regionId, int readingIndex) {
            this.ocrId = ocrId;
            this.regionId = regionId;
            this.readingIndex = readingIndex;
        }
    }

    public static final class Plan {
        public final List<Placement> placements;
        public final StrictOcrOrderGuard.Result verified;
        public final boolean usedLayout;
        private Plan(List<Placement> placements, StrictOcrOrderGuard.Result verified,
                     boolean usedLayout) {
            this.placements = Collections.unmodifiableList(new ArrayList<>(placements));
            this.verified = verified;
            this.usedLayout = usedLayout;
        }
    }

    private static final class VisualRow {
        final List<Integer> indices = new ArrayList<>();
        float projectedY;
        float averageHeight;
        VisualRow(int index, float y, float h) {
            indices.add(index);
            projectedY=y;
            averageHeight=h;
        }
        void add(int index, float y, float h) {
            int n=indices.size();
            projectedY=(projectedY*n+y)/(n+1);
            averageHeight=(averageHeight*n+h)/(n+1);
            indices.add(index);
        }
    }

    /**
     * Order by projected baselines rather than raw centerY. Perspective skew can
     * make the right half of line 1 lower than the left half of line 2.
     */
    private static void sortVisualRows(List<Integer> indices, List<ReadingOrder.Box> boxes) {
        if (indices.size() < 2) return;
        float referenceX=0f;
        for(int id:indices)referenceX+=boxes.get(id).centerX();
        referenceX/=indices.size();
        final float xRef=referenceX;
        List<Integer> byBaseline=new ArrayList<>(indices);
        byBaseline.sort((a,b)->{
            int y=Float.compare(boxes.get(a).alignedY(xRef),boxes.get(b).alignedY(xRef));
            return y!=0?y:Float.compare(boxes.get(a).left(),boxes.get(b).left());
        });
        List<VisualRow> rows=new ArrayList<>();
        for(int id:byBaseline) {
            ReadingOrder.Box box=boxes.get(id);
            float y=box.alignedY(xRef);
            VisualRow closest=null;
            float best=Float.MAX_VALUE;
            for(VisualRow row:rows) {
                float d=Math.abs(y-row.projectedY);
                float tolerance=Math.max(3f,Math.min(row.averageHeight,box.actualHeight())*.43f);
                if(d<=tolerance && d<best){best=d;closest=row;}
            }
            if(closest==null)rows.add(new VisualRow(id,y,box.actualHeight()));
            else closest.add(id,y,box.actualHeight());
        }
        rows.sort(Comparator.comparingDouble(r->r.projectedY));
        indices.clear();
        for(VisualRow row:rows) {
            row.indices.sort(Comparator.comparingDouble((Integer id)->boxes.get(id).left())
                .thenComparingInt(id->id));
            indices.addAll(row.indices);
        }
    }

    private static float area(float l, float t, float r, float b) {
        return Math.max(0, r-l)*Math.max(0,b-t);
    }

    /**
     * Inputs are in identical OCR detection order. Box IDs must be unique.
     * Layout coordinates must be expressed in the same frame as OCR coordinates.
     * An uncovered OCR box remains in the output.
     */
    public static Plan plan(List<StrictOcrOrderGuard.Item> source,
                            List<ReadingOrder.Box> boxes,
                            List<LayoutRegion> regions) {
        if (source == null || boxes == null || source.size() != boxes.size())
            throw new IllegalArgumentException("OCR IDs and boxes must align");
        // Validate IDs even if layout is empty.
        StrictOcrOrderGuard.apply(source, null);
        List<LayoutRegion> safeRegions = regions == null ?
            Collections.<LayoutRegion>emptyList() : regions;
        final int n = boxes.size();
        int[] membership = new int[n];
        Arrays.fill(membership,-1);
        for (int i=0;i<n;i++) {
            ReadingOrder.Box b = boxes.get(i);
            if (b == null) throw new IllegalArgumentException("null OCR box");
            float boxArea = area(b.left(),b.top(),b.right(),b.bottom());
            if (boxArea <= 0) continue;
            float best = -1;
            for (int j=0;j<safeRegions.size();j++) {
                LayoutRegion r = safeRegions.get(j);
                if (r == null || !Float.isFinite(r.confidence) || r.confidence < .53f) continue;
                float regionArea = area(r.left,r.top,r.right,r.bottom);
                if (regionArea <= 0) continue;
                float intersection = area(Math.max(b.left(),r.left),
                    Math.max(b.top(),r.top),Math.min(b.right(),r.right),
                    Math.min(b.bottom(),r.bottom));
                float coverage = intersection/boxArea;
                if (coverage < .60f) continue;
                // Prefer the tightest covering region, rather than a page-wide parent.
                float score = coverage + .15f*r.confidence +
                    .10f*(boxArea/Math.max(boxArea,regionArea));
                if (score > best) { best=score; membership[i]=j; }
            }
        }

        // Geometric fallback: detect persistent left/right page columns.
        // A single layout region must not suppress this split.
        List<List<ReadingOrder.Box>> columns = ReadingOrder.horizontalColumns(boxes);
        // A detector can yield multiple entries sharing the same Box object.
        // Keep a queue per object identity so every original OCR ID survives.
        Map<ReadingOrder.Box,ArrayDeque<Integer>> indexByBox = new IdentityHashMap<>();
        for (int i=0;i<n;i++) {
            indexByBox.computeIfAbsent(boxes.get(i),k->new ArrayDeque<Integer>()).add(i);
        }
        final List<List<Integer>> groups = new ArrayList<>();
        if (columns.size() > 1) {
            for (List<ReadingOrder.Box> column: columns) {
                List<Integer> group = new ArrayList<>();
                for (ReadingOrder.Box b: column) {
                    ArrayDeque<Integer> remaining=indexByBox.get(b);
                    Integer id=remaining == null ? null : remaining.pollFirst();
                    if (id != null) group.add(id);
                }
                if (!group.isEmpty()) groups.add(group);
            }
            // Reject incomplete/duplicated grouping: preserve every OCR box.
            boolean[] seen = new boolean[n];
            int count=0;
            boolean valid=true;
            for (List<Integer> group:groups) for(int i:group) {
                if(seen[i]) valid=false;
                else {seen[i]=true;count++;}
            }
            if(!valid || count!=n) groups.clear();
        }
        // ReadingOrder.horizontalColumns already places full-width headings
        // ahead of left/right page streams and trailing footers last. Preserve
        // that order instead of applying a non-transitive rectangle comparator.
        final boolean groupedByGeometry = !groups.isEmpty();
        if (groups.isEmpty()) {
            Map<Integer,List<Integer>> byRegion = new HashMap<>();
            for(int i=0;i<n;i++) {
                int key=membership[i];
                if (!byRegion.containsKey(key)) byRegion.put(key,new ArrayList<Integer>());
                byRegion.get(key).add(i);
            }
            groups.addAll(byRegion.values());
        }
        if (!groupedByGeometry) {
            // Region groups are not necessarily in any meaningful HashMap order.
            // Choose ONE global reading mode before sorting. Mixing pairwise
            // 'above' and 'left-of' precedence can create comparison cycles.
            final Map<List<Integer>,float[]> bounds = new IdentityHashMap<>();
            for(List<Integer> g:groups) {
                float left=Float.MAX_VALUE, right=-Float.MAX_VALUE;
                float top=Float.MAX_VALUE, bottom=-Float.MAX_VALUE;
                for(int i:g) {
                    ReadingOrder.Box box=boxes.get(i);
                    left=Math.min(left,box.left());
                    right=Math.max(right,box.right());
                    top=Math.min(top,box.top());
                    bottom=Math.max(bottom,box.bottom());
                }
                bounds.put(g,new float[]{left,top,right,bottom});
            }
            boolean hasParallelRegions=false;
            for(int a=0;a<groups.size();a++) for(int b=a+1;b<groups.size();b++) {
                float[] x=bounds.get(groups.get(a)), y=bounds.get(groups.get(b));
                float commonY=Math.min(x[3],y[3])-Math.max(x[1],y[1]);
                float smallerHeight=Math.max(1f,Math.min(x[3]-x[1],y[3]-y[1]));
                if(commonY >= .35f*smallerHeight &&
                    (x[2]<=y[0] || y[2]<=x[0])) hasParallelRegions=true;
            }
            final boolean parallel=hasParallelRegions;
            final Map<List<Integer>,Integer> priority=new IdentityHashMap<>();
            for(List<Integer> g:groups) {
                float[] b=bounds.get(g);
                int p=1;
                if(parallel && groups.size()>2) {
                    boolean entirelyAboveAll=true, entirelyBelowAll=true;
                    for(List<Integer> other:groups) {
                        if(g==other)continue;
                        float[] o=bounds.get(other);
                        if(b[3]>o[1])entirelyAboveAll=false;
                        if(b[1]<o[3])entirelyBelowAll=false;
                    }
                    // Only promote a page-spanning banner; narrow side notes
                    // should not leap ahead of an entire opposite page.
                    float minWidth=Float.MAX_VALUE;
                    for(List<Integer> other:groups) if(g!=other) {
                        float[] o=bounds.get(other);
                        minWidth=Math.min(minWidth,o[2]-o[0]);
                    }
                    boolean wide=b[2]-b[0]>=minWidth*1.35f;
                    if(wide && entirelyAboveAll)p=0;
                    else if(wide && entirelyBelowAll)p=2;
                }
                priority.put(g,p);
            }
            groups.sort((a,b)->{
                int p=Integer.compare(priority.get(a),priority.get(b));
                if(p!=0)return p;
                float[] x=bounds.get(a),y=bounds.get(b);
                int primary=parallel ? Float.compare(x[0],y[0])
                                     : Float.compare(x[1],y[1]);
                if(primary!=0)return primary;
                int secondary=parallel ? Float.compare(x[1],y[1])
                                       : Float.compare(x[0],y[0]);
                if(secondary!=0)return secondary;
                // Deterministic tie-break, independent of HashMap iteration.
                return Integer.compare(a.get(0),b.get(0));
            });
        }
        List<Integer> orderedIds = new ArrayList<>();
        List<Placement> placements = new ArrayList<>();
        for(List<Integer> group:groups) {
            sortVisualRows(group,boxes);
            for(int i:group) {
                int id=source.get(i).id;
                orderedIds.add(id);
                placements.add(new Placement(id,membership[i],placements.size()));
            }
        }
        StrictOcrOrderGuard.Result checked=StrictOcrOrderGuard.apply(source,orderedIds);
        if (!checked.accepted) {
            placements.clear();
            for(int i=0;i<n;i++) placements.add(new Placement(source.get(i).id,-1,i));
        }
        boolean used=false;
        for(int id:membership) if(id>=0) used=true;
        return new Plan(placements,checked,used);
    }
}
