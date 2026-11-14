package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class M02sPhotoPolicyTest {
    @Test public void safeRelativePathCannotEscapeTheVerifiedHost() {
        assertEquals("DCIM/photo%202026.jpg",
                M02sPhotoPolicy.safeRelativePath("DCIM/photo 2026.jpg"));
        assertEquals("http://192.168.169.1/DCIM/photo%202026.jpg",
                M02sPhotoPolicy.safePhotoUrl("192.168.169.1","DCIM/photo 2026.jpg"));
        for (String name : new String[]{"", "../x.jpg", "/DCIM/x.jpg",
                "DCIM/../x.jpg", "DCIM//x.jpg", "http://evil.com/x.jpg",
                "DCIM/x.jpg?token=z", "DCIM/%2e%2e/x.jpg",
                "DCIM\\x.jpg", "DCIM/x.mp4"}) {
            assertNull("Unsafe filename passed: " + name,
                    M02sPhotoPolicy.safePhotoUrl("192.168.169.1", name));
        }
        try {
            M02sPhotoPolicy.safePhotoUrl("8.8.8.8","photo.jpg");
            fail("External host accepted");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void latestRequiresUnambiguousTimestamps() {
        M02sPhotoPolicy.Candidate early =
                new M02sPhotoPolicy.Candidate("DCIM/old.jpg", 200, "2026-11-14 12:00:00","0");
        M02sPhotoPolicy.Candidate late =
                new M02sPhotoPolicy.Candidate("DCIM/new.jpg", 200, "2026-11-14 14:00:00","0");
        M02sPhotoPolicy.Candidate unknown =
                new M02sPhotoPolicy.Candidate("DCIM/no-date.jpg", 200, "","0");
        M02sPhotoPolicy.Candidate tie =
                new M02sPhotoPolicy.Candidate("DCIM/tie.jpg", 200, "2026-11-14 14:00:00","0");
        assertSame(late, M02sPhotoPolicy.uniqueLatestOrNull(Arrays.asList(early,late)));
        assertNull(M02sPhotoPolicy.uniqueLatestOrNull(Arrays.asList(unknown,late)));
        assertNull(M02sPhotoPolicy.uniqueLatestOrNull(Arrays.asList(late,tie)));
        assertSame(unknown, M02sPhotoPolicy.uniqueLatestOrNull(Collections.singletonList(unknown)));
        assertNull(M02sPhotoPolicy.uniqueLatestOrNull(Collections.emptyList()));
    }

    @Test public void filenameTimestampMayBeUsedButListOrderMustNotBeAssumed() {
        M02sPhotoPolicy.Candidate a = new M02sPhotoPolicy.Candidate(
                "IMG_20261114_113000.jpg", -1,"","");
        M02sPhotoPolicy.Candidate b = new M02sPhotoPolicy.Candidate(
                "IMG_20261114_133000.jpg", -1,"","");
        assertSame(b,M02sPhotoPolicy.uniqueLatestOrNull(Arrays.asList(a,b)));
    }

    @Test public void rejectNonImageOrMislabelledDownload() {
        byte[] jpeg = new byte[32];
        jpeg[0] = (byte) 0xff; jpeg[1] = (byte) 0xd8; jpeg[2] = (byte) 0xff;
        assertEquals(M02sPhotoPolicy.ImageKind.JPEG, M02sPhotoPolicy.imageKind(jpeg));
        assertTrue(M02sPhotoPolicy.extensionMatches("DCIM/P.JPG",
                M02sPhotoPolicy.imageKind(jpeg)));
        assertFalse(M02sPhotoPolicy.extensionMatches("DCIM/P.PNG",
                M02sPhotoPolicy.imageKind(jpeg)));
        assertFalse(M02sPhotoPolicy.isImageName("video.mp4"));
        assertEquals(M02sPhotoPolicy.ImageKind.UNKNOWN,
                M02sPhotoPolicy.imageKind("this is not a photograph".getBytes()));
        assertEquals(32 * 1024 * 1024,M02sPhotoPolicy.MAX_IMAGE_BYTES);
    }
}
