package com.vobichngan.bluestar.m02slab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A newly observed file, not the pre-existing newest file, is required.
 * No list-order, timestamp-alone, or BLE-ACK-only assumptions.
 */
public final class M02sFreshPhotoPolicy {
    private M02sFreshPhotoPolicy() { }

    public enum Status { NO_NEW_FILE, NEW_FILE_INCOMPLETE, AMBIGUOUS, READY }

    public static final class Selection {
        public final Status status;
        public final int freshCount;
        public final M02sPhotoPolicy.Candidate photo;
        /** Private session token: never write into diagnostics. */
        public final String stabilityKey;

        private Selection(Status status, int count,
                          M02sPhotoPolicy.Candidate item, String key) {
            this.status = status;
            this.freshCount = count;
            this.photo = item;
            this.stabilityKey = key;
        }
    }

    public static Selection select(List<M02sPhotoPolicy.Candidate> before,
                                   List<M02sPhotoPolicy.Candidate> after) {
        if (before == null || after == null) throw new IllegalArgumentException("null snapshots");
        Set<String> oldNames = new HashSet<>();
        for (M02sPhotoPolicy.Candidate item : before) oldNames.add(item.remoteName);
        List<M02sPhotoPolicy.Candidate> fresh = new ArrayList<>();
        for (M02sPhotoPolicy.Candidate item : after) {
            if (!oldNames.contains(item.remoteName)
                    && M02sPhotoPolicy.isImageName(item.remoteName)) fresh.add(item);
        }
        if (fresh.isEmpty()) return new Selection(Status.NO_NEW_FILE, 0, null, null);

        M02sPhotoPolicy.Candidate chosen = M02sPhotoPolicy.uniqueLatestOrNull(fresh);
        if (chosen == null) {
            return new Selection(Status.AMBIGUOUS, fresh.size(), null, null);
        }
        if (chosen.declaredBytes < 32 ||
                chosen.declaredBytes > M02sPhotoPolicy.MAX_IMAGE_BYTES) {
            return new Selection(Status.NEW_FILE_INCOMPLETE, fresh.size(), null, null);
        }
        String key = chosen.remoteName + "\u0000" + chosen.declaredBytes + "\u0000"
                + chosen.createdAt;
        return new Selection(Status.READY, fresh.size(), chosen, key);
    }

    /**
     * AP was intentionally not connected before BLE shutter, so the first shot
     * has no pre-shot file-list baseline. A unique latest file may be PREVIEWED,
     * but is never claimed to have been created by this shutter event.
     */
    public static Selection selectLatestUnverified(List<M02sPhotoPolicy.Candidate> after) {
        if (after == null) throw new IllegalArgumentException("null snapshot");
        if (after.isEmpty()) return new Selection(Status.NO_NEW_FILE, 0, null, null);
        M02sPhotoPolicy.Candidate chosen = M02sPhotoPolicy.uniqueLatestOrNull(after);
        if (chosen == null) return new Selection(Status.AMBIGUOUS, after.size(), null, null);
        if (chosen.declaredBytes < 32 || chosen.declaredBytes > M02sPhotoPolicy.MAX_IMAGE_BYTES) {
            return new Selection(Status.NEW_FILE_INCOMPLETE, after.size(), null, null);
        }
        String key = chosen.remoteName + "\u0000" + chosen.declaredBytes + "\u0000" + chosen.createdAt;
        return new Selection(Status.READY, after.size(), chosen, key);
    }

    /** Two back-to-back identical list observations required before download. */
    public static boolean stablePair(Selection previous, Selection current) {
        return previous != null && current != null
                && previous.status == Status.READY
                && current.status == Status.READY
                && previous.stabilityKey.equals(current.stabilityKey);
    }
}
