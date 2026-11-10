package com.vobichngan.bluestar.photowatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-session detector. First scan establishes a baseline and NEVER announces
 * old photos. A new image must keep the same nonzero size and modification time
 * on two consecutive scans before it can be announced.
 *
 * No image bytes are read. No network communication occurs.
 */
public final class PhotoSnapshotTracker {
    public static final class Photo {
        public final String id;
        public final String name;
        public final long size;
        public final long modified;
        public Photo(String id, String name, long size, long modified) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.modified = modified;
        }
    }

    private static final class Pending {
        final long size;
        final long modified;
        final int unchangedScans;
        Pending(long size, long modified, int unchangedScans) {
            this.size = size;
            this.modified = modified;
            this.unchangedScans = unchangedScans;
        }
    }

    private boolean baselined = false;
    private final Set<String> known = new HashSet<>();
    private final Map<String, Pending> pending = new HashMap<>();

    public synchronized boolean hasBaseline() { return baselined; }
    public synchronized int baselineCount() { return known.size(); }

    public synchronized List<Photo> accept(List<Photo> listing) {
        List<Photo> newlyStable = new ArrayList<>();
        if (listing == null) {
            throw new IllegalArgumentException("listing cannot be null");
        }
        if (!baselined) {
            for (Photo photo : listing) {
                if (photo.id != null) known.add(photo.id);
            }
            baselined = true;
            return newlyStable;
        }

        Set<String> present = new HashSet<>();
        for (Photo photo : listing) {
            if (photo.id == null || photo.id.isEmpty()) continue;
            present.add(photo.id);
            if (known.contains(photo.id)) continue;
            if (photo.size <= 0) {
                pending.remove(photo.id);
                continue;
            }
            Pending before = pending.get(photo.id);
            if (before != null && before.size == photo.size
                    && before.modified == photo.modified) {
                if (before.unchangedScans >= 1) {
                    known.add(photo.id);
                    pending.remove(photo.id);
                    newlyStable.add(photo);
                }
            } else {
                pending.put(photo.id, new Pending(photo.size, photo.modified, 1));
            }
        }
        // Do not trigger on a half-written or temporarily missing photo.
        pending.keySet().retainAll(present);
        return newlyStable;
    }
}
