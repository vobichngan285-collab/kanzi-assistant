package com.vobichngan.bluestar.photowatch;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PhotoSnapshotTrackerTest {
    private static PhotoSnapshotTracker.Photo p(String id, long size, long mod) {
        return new PhotoSnapshotTracker.Photo(id, id+".jpg", size, mod);
    }

    @Test public void existingImagesNeverAnnounce() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        assertTrue(t.accept(Arrays.asList(p("old", 200, 1000))).isEmpty());
        assertTrue(t.hasBaseline());
        assertEquals(1, t.baselineCount());
        assertTrue(t.accept(Arrays.asList(p("old", 200, 1000))).isEmpty());
    }

    @Test public void newImageNeedsTwoUnchangedScansAndNeverRepeats() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        List<PhotoSnapshotTracker.Photo> files = Arrays.asList(p("new", 400, 1000));
        assertTrue(t.accept(files).isEmpty());
        assertEquals("new", t.accept(files).get(0).id);
        assertTrue(t.accept(files).isEmpty());
    }

    @Test public void incompleteAndGrowingImagesDoNotAnnounce() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        assertTrue(t.accept(Arrays.asList(p("x", 0, 100))).isEmpty());
        assertTrue(t.accept(Arrays.asList(p("x", 50, 101))).isEmpty());
        assertTrue(t.accept(Arrays.asList(p("x", 99, 102))).isEmpty());
        assertEquals(1, t.accept(Arrays.asList(p("x", 99, 102))).size());
    }

    @Test public void fileDisappearsBeforeStabilityNoEvent() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        assertTrue(t.accept(Arrays.asList(p("x", 9, 2))).isEmpty());
        assertTrue(t.accept(Collections.emptyList()).isEmpty());
        assertTrue(t.accept(Arrays.asList(p("x", 9, 2))).isEmpty());
        assertEquals(1, t.accept(Arrays.asList(p("x", 9, 2))).size());
    }

    @Test public void burstOfTwoFilesAnnouncedOnce() {
        PhotoSnapshotTracker t = new PhotoSnapshotTracker();
        t.accept(Collections.emptyList());
        List<PhotoSnapshotTracker.Photo> batch = Arrays.asList(p("a", 10, 1), p("b", 20, 2));
        assertTrue(t.accept(batch).isEmpty());
        assertEquals(2, t.accept(batch).size());
        assertEquals(0, t.accept(batch).size());
    }
}
