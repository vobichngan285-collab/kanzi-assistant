package com.vobichngan.bluestar.m02slab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Latency optimization ONLY. Make the latest original available to the UI
 * before downloading historical originals. If entire inventory is eligible,
 * still download every entry before the already proven OEM FULL 0x44 finish.
 *
 * Download sequence changed, but count and FULL exit are unchanged. Partial
 * failure can only use actual completed-count (never pretend FULL success).
 */
public final class M02sFastDownloadPlan {
    private M02sFastDownloadPlan() {}

    public static List<M02sPhotoPolicy.Candidate> plan(
            M02sPhotoListReader.Result list, M02sPhotoPolicy.Candidate latest,
            boolean fullBatch) {
        if (list==null || latest==null || list.entriesObserved<=0
                || !M02sPhotoPolicy.isImageName(latest.remoteName))
            throw new IllegalArgumentException("No verified latest image");
        boolean exists=false;
        for (M02sPhotoPolicy.Candidate item:list.candidates) {
            if (item.remoteName.equals(latest.remoteName)) { exists=true; break; }
        }
        if (!exists) throw new IllegalArgumentException("Latest absent from original list");
        if (!fullBatch) return Collections.singletonList(latest);
        if (!M02sOemImportPolicy.canCompleteWholeList(
                list.entriesObserved,list.eligibleImages))
            throw new IllegalArgumentException("Not eligible for whole OEM batch");
        List<M02sPhotoPolicy.Candidate> order = new ArrayList<>(list.candidates.size());
        order.add(latest);
        Set<String> used=new HashSet<>();
        used.add(latest.remoteName);
        for (M02sPhotoPolicy.Candidate item:list.candidates) {
            if (used.add(item.remoteName)) order.add(item);
        }
        if (order.size()!=list.entriesObserved)
            throw new IllegalArgumentException("List not unique; cannot declare FULL");
        return Collections.unmodifiableList(order);
    }
}
