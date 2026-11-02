package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
        Map<ReadingOrder.Box,Integer> indexByBox = new HashMap<>();
        for (int i=0;i<n;i++) indexByBox.put(boxes.get(i),i);
        final List<List<Integer>> groups = new ArrayList<>();
        if (columns.size() > 1) {
            for (List<ReadingOrder.Box> column: columns) {
                List<Integer> group = new ArrayList<>();
                for (ReadingOrder.Box b: column) {
                    Integer id=indexByBox.get(b);
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
        if (groups.isEmpty()) {
            Map<Integer,List<Integer>> byRegion = new HashMap<>();
            for(int i=0;i<n;i++) {
                int key=membership[i];
                if (!byRegion.containsKey(key)) byRegion.put(key,new ArrayList<Integer>());
                byRegion.get(key).add(i);
            }
            groups.addAll(byRegion.values());
        }
        // Sort groups spatially (top-to-bottom; distinct side-by-side columns left first).
        Collections.sort(groups, new Comparator<List<Integer>>() {
            public int compare(List<Integer> a,List<Integer> b) {
                float ax=0,ay=0,bx=0,by=0;
                for(int i:a){ax+=boxes.get(i).centerX();ay+=boxes.get(i).centerY();}
                for(int i:b){bx+=boxes.get(i).centerX();by+=boxes.get(i).centerY();}
                ax/=a.size();ay/=a.size();bx/=b.size();by/=b.size();
                // Two separated streams: read left page before right page.
                float aLeft=Float.MAX_VALUE,aRight=-Float.MAX_VALUE;
                float bLeft=Float.MAX_VALUE,bRight=-Float.MAX_VALUE;
                for(int i:a){aLeft=Math.min(aLeft,boxes.get(i).left());aRight=Math.max(aRight,boxes.get(i).right());}
                for(int i:b){bLeft=Math.min(bLeft,boxes.get(i).left());bRight=Math.max(bRight,boxes.get(i).right());}
                if(aRight<=bLeft) return -1;
                if(bRight<=aLeft) return 1;
                int y=Float.compare(ay,by);
                return y!=0?y:Float.compare(ax,bx);
            }
        });
        List<Integer> orderedIds = new ArrayList<>();
        List<Placement> placements = new ArrayList<>();
        for(List<Integer> group:groups) {
            Collections.sort(group,new Comparator<Integer>() {
                public int compare(Integer a,Integer b) {
                    int y=Float.compare(boxes.get(a).centerY(),boxes.get(b).centerY());
                    return y!=0?y:Float.compare(boxes.get(a).centerX(),boxes.get(b).centerX());
                }
            });
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
