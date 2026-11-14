package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class M02sFreshPhotoPolicyTest {
    private static M02sPhotoPolicy.Candidate c(String name, long bytes, String time) {
        return new M02sPhotoPolicy.Candidate(name, bytes, time, "0");
    }

    @Test public void oldNewestIsNeverTreatedAsNew() {
        List<M02sPhotoPolicy.Candidate> before=Arrays.asList(
                c("DCIM/A.jpg",400,"2026-11-14 17:01:00"),
                c("DCIM/B.jpg",500,"2026-11-14 17:02:00"));
        M02sFreshPhotoPolicy.Selection result=M02sFreshPhotoPolicy.select(before,before);
        assertEquals(M02sFreshPhotoPolicy.Status.NO_NEW_FILE,result.status);
        assertNull(result.photo);
    }

    @Test public void selectNewFilenameEvenIfItsTimestampIsOlder() {
        List<M02sPhotoPolicy.Candidate> old=Collections.singletonList(
                c("DCIM/old.jpg",4200,"2026-11-14 18:00:00"));
        List<M02sPhotoPolicy.Candidate> now=Arrays.asList(
                c("DCIM/old.jpg",4200,"2026-11-14 18:00:00"),
                c("DCIM/new.jpg",6000,"2026-11-14 17:59:58"));
        M02sFreshPhotoPolicy.Selection next=M02sFreshPhotoPolicy.select(old,now);
        assertEquals(M02sFreshPhotoPolicy.Status.READY,next.status);
        assertEquals("DCIM/new.jpg",next.photo.remoteName);
    }

    @Test public void requireStableSizeTwoTimesNotJustName() {
        List<M02sPhotoPolicy.Candidate> old=Collections.emptyList();
        M02sFreshPhotoPolicy.Selection p=M02sFreshPhotoPolicy.select(old,
                Collections.singletonList(c("IMG_20261114180000.jpg",100,"")));
        M02sFreshPhotoPolicy.Selection q=M02sFreshPhotoPolicy.select(old,
                Collections.singletonList(c("IMG_20261114180000.jpg",1800,"")));
        M02sFreshPhotoPolicy.Selection r=M02sFreshPhotoPolicy.select(old,
                Collections.singletonList(c("IMG_20261114180000.jpg",1800,"")));
        assertFalse(M02sFreshPhotoPolicy.stablePair(p,q));
        assertTrue(M02sFreshPhotoPolicy.stablePair(q,r));
    }

    @Test public void ambiguousMultipleNewFilesMustNeverAutoDownload() {
        M02sFreshPhotoPolicy.Selection selection = M02sFreshPhotoPolicy.select(
                Collections.emptyList(),
                Arrays.asList(c("A.jpg",300,""),c("B.jpg",500,"")));
        assertEquals(M02sFreshPhotoPolicy.Status.AMBIGUOUS,selection.status);
        assertNull(selection.photo);
    }

    @Test public void pendingIncompleteFileMustWait() {
        M02sFreshPhotoPolicy.Selection selection = M02sFreshPhotoPolicy.select(
                Collections.emptyList(),
                Collections.singletonList(c("new.jpg",0,"2026-11-14 18:00:00")));
        assertEquals(M02sFreshPhotoPolicy.Status.NEW_FILE_INCOMPLETE,selection.status);
        assertNull(selection.photo);
    }

    @Test public void selectsUniqueLatestAmongNewFilesOnly() {
        M02sFreshPhotoPolicy.Selection selection = M02sFreshPhotoPolicy.select(
                Collections.singletonList(c("OLD.jpg",400,"2026-11-14 19:00:00")),
                Arrays.asList(
                    c("OLD.jpg",400,"2026-11-14 19:00:00"),
                    c("NEW1.jpg",400,"2026-11-14 17:00:00"),
                    c("NEW2.jpg",500,"2026-11-14 17:02:00")));
        assertEquals(M02sFreshPhotoPolicy.Status.READY,selection.status);
        assertEquals("NEW2.jpg",selection.photo.remoteName);
        assertEquals(2,selection.freshCount);
    }
    @Test public void firstShotWithoutPreApBaselineIsOnlyUnverifiedLatest() {
        M02sFreshPhotoPolicy.Selection first =
                M02sFreshPhotoPolicy.selectLatestUnverified(Arrays.asList(
                    c("DCIM/old.jpg",450,"2026-11-14 16:00:00"),
                    c("DCIM/newest.jpg",850,"2026-11-14 18:00:00")));
        M02sFreshPhotoPolicy.Selection second =
                M02sFreshPhotoPolicy.selectLatestUnverified(Arrays.asList(
                    c("DCIM/old.jpg",450,"2026-11-14 16:00:00"),
                    c("DCIM/newest.jpg",850,"2026-11-14 18:00:00")));
        assertEquals(M02sFreshPhotoPolicy.Status.READY, first.status);
        assertEquals("DCIM/newest.jpg", first.photo.remoteName);
        assertTrue(M02sFreshPhotoPolicy.stablePair(first,second));
        // Caller must label it "unverified", never claim first-shot freshness.
    }

    @Test public void unverifiedLatestRequiresUniqueTimestamp() {
        M02sFreshPhotoPolicy.Selection result =
                M02sFreshPhotoPolicy.selectLatestUnverified(Arrays.asList(
                    c("A.jpg",300,""),c("B.jpg",500,"")));
        assertEquals(M02sFreshPhotoPolicy.Status.AMBIGUOUS,result.status);
        assertNull(result.photo);
    }

    @Test public void noInitialPhotosAndPartialPhotoAreNotDownloadable() {
        assertEquals(M02sFreshPhotoPolicy.Status.NO_NEW_FILE,
                M02sFreshPhotoPolicy.selectLatestUnverified(Collections.emptyList()).status);
        assertEquals(M02sFreshPhotoPolicy.Status.NEW_FILE_INCOMPLETE,
                M02sFreshPhotoPolicy.selectLatestUnverified(Collections.singletonList(
                    c("DCIM/new.jpg",0,"2026-11-14 18:00:00"))).status);
    }

}
