package com.vobichngan.bluestar.photowatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fast *arrival* detector for the experiment, NOT a file-completeness check.
 *
 * On the first scan it records pre-existing photos without announcing them.
 * On subsequent scans it announces a newly-visible image on its FIRST
 * appearance with nonzero size, exactly once.
 *
 * For a future OCR upload, a separate stage MUST verify file completeness
 * before opening/uploading image bytes. This detector reads metadata only.
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

    private boolean baselined = false;
    private final Set<String> known = new HashSet<>();

    public synchronized boolean hasBaseline() { return baselined; }
    public synchronized int baselineCount() { return known.size(); }

    public synchronized List<Photo> accept(List<Photo> listing) {
        if (listing == null) throw new IllegalArgumentException("listing cannot be null");
        List<Photo> newlyVisible = new ArrayList<>();
        if (!baselined) {
            for (Photo photo : listing) {
                if (photo.id != null && !photo.id.isEmpty()) known.add(photo.id);
            }
            baselined = true;
            return newlyVisible;
        }
        for (Photo photo : listing) {
            if (photo.id == null || photo.id.isEmpty()) continue;
            if (known.contains(photo.id)) continue;
            // Some providers expose a placeholder of 0 bytes during import.
            // Wait for nonzero size, but do not wait for a second poll.
            if (photo.size <= 0) continue;
            known.add(photo.id);
            newlyVisible.add(photo);
        }
        return newlyVisible;
    }
}
