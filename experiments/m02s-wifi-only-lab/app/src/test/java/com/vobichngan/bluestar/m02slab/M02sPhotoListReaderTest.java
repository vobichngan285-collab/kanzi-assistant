package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.json.JSONException;
import org.junit.Test;
import java.nio.charset.StandardCharsets;

public final class M02sPhotoListReaderTest {
    @Test public void parseOfficialApInfoFilesJson() throws Exception {
        // Synthetic fixture based on OEM PhotoApBean field names; not user content.
        String json = "{\"info\":[{\"files\":["
                + "{\"name\":\"DCIM/IMG_20261114_122000.jpg\","
                + "\"size\":1024,\"createtimestr\":\"2026-11-14 12:20:00\",\"type\":0},"
                + "{\"name\":\"DCIM/IMG_20261114_123000.jpg\","
                + "\"size\":2048,\"createtimestr\":\"2026-11-14 12:30:00\",\"type\":0},"
                + "{\"name\":\"DCIM/VID.mp4\",\"size\":100},"
                + "{\"name\":\"../private.jpg\",\"size\":1}"
                + "]}]}";
        M02sPhotoListReader.Result result =
                M02sPhotoListReader.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(4,result.entriesObserved);
        assertEquals(2,result.eligibleImages);
        assertEquals("DCIM/IMG_20261114_123000.jpg",
                M02sPhotoPolicy.uniqueLatestOrNull(result.candidates).remoteName);
    }

    @Test public void infoObjectAndFileStringSizes() throws Exception {
        String json = "{\"info\":{\"files\":["
                + "{\"name\":\"P_20261114130000.jpg\","
                + "\"size\":\"3000\",\"createtimestr\":\"\",\"type\":\"photo\"}"
                + "]}}";
        M02sPhotoListReader.Result result =
                M02sPhotoListReader.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(1,result.eligibleImages);
        assertEquals(3000,result.candidates.get(0).declaredBytes);
        assertNotNull(result.candidates.get(0).sortableTime);
    }

    @Test public void rejectNonApXmlAndOversizedResponses() throws Exception {
        try {
            M02sPhotoListReader.parse("<FileList/>".getBytes(StandardCharsets.UTF_8));
            fail("XML should not parse as AP JSON");
        } catch (JSONException expected) { }
        try {
            M02sPhotoListReader.parse("{\"files\":[]}".getBytes(StandardCharsets.UTF_8));
            fail("Missing info must not be guessed");
        } catch (JSONException expected) { }
        try {
            M02sPhotoListReader.parse(new byte[M02sPhotoPolicy.MAX_LIST_BYTES + 1]);
            fail("Oversized JSON accepted");
        } catch (JSONException expected) { }
    }

    @Test public void disallowDuplicateAndOversizedPhoto() throws Exception {
        String json="{\"info\":[{\"files\":["
                + "{\"name\":\"A.jpg\",\"size\":5},"
                + "{\"name\":\"A.jpg\",\"size\":5},"
                + "{\"name\":\"big.jpg\",\"size\":999999999}"
                + "]}]}";
        M02sPhotoListReader.Result result =
                M02sPhotoListReader.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(3,result.entriesObserved);
        assertEquals(1,result.eligibleImages);
    }
}
