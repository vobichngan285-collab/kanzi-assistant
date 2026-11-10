package com.vobichngan.bluestar.photowatch;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PhotoSnapshotTrackerTest {
    private static PhotoSnapshotTracker.Photo p(String id, long size, long mod) {
        return new PhotoSnapshotTracker.Photo(id, id + ".jpg", size, mod);
    }

    @Test public void existingPhotosAreIgnoredForeverAfterBaseline() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        assertTrue(t.accept(Arrays.asList(p("old", 200, 1000))).isEmpty());
        assertTrue(t.hasBaseline());
        assertEquals(1, t.baselineCount());
        assertTrue(t.accept(Arrays.asList(p("old", 300, 1001))).isEmpty());
    }

    @Test public void newPhotoAnnouncesOnFirstNonzeroSizeScan() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        List<PhotoSnapshotTracker.Photo> files = Arrays.asList(p("new", 400, 1000));
        assertEquals("new", t.accept(files).get(0).id);
        assertTrue(t.accept(files).isEmpty());
    }

    @Test public void newZeroLengthPhotoWaitsOnlyUntilNonzero() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        assertTrue(t.accept(Arrays.asList(p("x", 0, 100))).isEmpty());
        assertEquals(1, t.accept(Arrays.asList(p("x", 50, 101))).size());
        assertTrue(t.accept(Arrays.asList(p("x", 99, 102))).isEmpty());
    }

    @Test public void anUnseenEmptyFileCanDisappearAndReturn() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        assertTrue(t.accept(Arrays.asList(p("x", 0, 2))).isEmpty());
        assertTrue(t.accept(Collections.emptyList()).isEmpty());
        assertEquals(1, t.accept(Arrays.asList(p("x", 9, 2))).size());
        assertTrue(t.accept(Collections.emptyList()).isEmpty());
    }

    @Test public void burstOfTwoNewFilesAnnouncesBothOnce() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        List<PhotoSnapshotTracker.Photo> batch = Arrays.asList(p("a", 10, 1), p("b", 20, 2));
        assertEquals(2, t.accept(batch).size());
        assertTrue(t.accept(batch).isEmpty());
    }

    @Test public void invalidIdsAreIgnoredAndCannotDuplicate() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        List<PhotoSnapshotTracker.Photo> bad = Arrays.asList(
                p("", 100, 1), new PhotoSnapshotTracker.Photo(null, "null", 200, 1));
        assertTrue(t.accept(bad).isEmpty());
        assertEquals(1, t.accept(Arrays.asList(p("valid", 1, 2))).size());
        assertTrue(t.accept(Arrays.asList(p("valid", 2, 3))).isEmpty());
    }

    @Test public void directFileEventIsImmediateAndSubsequentSafScanDoesNotRepeat() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Arrays.asList(p("old", 200, 1000)));
        assertTrue(t.acceptPhysicalEvent("IMG_20261110_194500.JPG"));
        assertFalse(t.acceptPhysicalEvent("img_20261110_194500.jpg"));
        assertTrue(t.accept(Arrays.asList(p("old", 200, 1000),
                new PhotoSnapshotTracker.Photo("provider:34", "img_20261110_194500.jpg", 2400, 2000)))
                .isEmpty());
        assertEquals(1, t.accept(Arrays.asList(p("next", 33, 3))).size());
    }

    @Test public void directEventIgnoresExistingAndTemporaryFiles() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        assertFalse(t.acceptPhysicalEvent("new.jpg")); // baseline mandatory
        t.accept(Arrays.asList(p("old", 1, 1)));
        assertFalse(t.acceptPhysicalEvent("old.jpg"));
        assertFalse(t.acceptPhysicalEvent("photo.tmp"));
        assertFalse(t.acceptPhysicalEvent("subfolder/photo.jpg"));
        assertFalse(t.acceptPhysicalEvent("subfolder\\\\photo.jpg"));
        assertFalse(t.acceptPhysicalEvent(""));
        assertTrue(t.acceptPhysicalEvent("new.heic"));
        assertFalse(t.acceptPhysicalEvent("new.heic"));
    }

    @Test public void safDiscoveredPhotosPreventLateDirectDuplicates() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        assertEquals(1, t.accept(Arrays.asList(p("new", 99, 11))).size());
        assertFalse(t.acceptPhysicalEvent("new.jpg"));
    }
}
