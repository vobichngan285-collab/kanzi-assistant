package com.vobichngan.bluestar.m02slab;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class M02sApPhotoProbePolicyTest {
    private M02sPhotoListReader.Result parse(String names) throws Exception {
        return M02sPhotoListReader.parse(("{\"info\":[{\"files\":[" + names + "]}]}")
                .getBytes(StandardCharsets.UTF_8));
    }
    private String entry(String name) {
        return "{\"name\":\""+name+"\",\"size\":100,\"createtimestr\":\"2026-11-15 17:00:00\"}";
    }
    @Test public void oneGenuinelyNewFileOnSameApCanBeDownloaded() throws Exception {
        M02sPhotoListReader.Result before=parse(entry("20261115160000.jpg"));
        M02sPhotoListReader.Result after=parse(entry("20261115160000.jpg")+","
                +entry("20261115170000.jpg"));
        M02sApPhotoProbePolicy.Delta d=M02sApPhotoProbePolicy.compare(before,after);
        assertEquals(M02sApPhotoProbePolicy.Status.NEW_UNIQUE_ORIGINAL,d.status);
        assertEquals("20261115170000.jpg",d.newOriginal.remoteName);
    }
    @Test public void unchangedFileIsNeverCalledNew() throws Exception {
        M02sPhotoListReader.Result before=parse(entry("A.jpg"));
        assertEquals(M02sApPhotoProbePolicy.Status.NO_CHANGE,
                M02sApPhotoProbePolicy.compare(before,before).status);
    }
    @Test public void replacedOrMultipleFilesAreNotAssumedSameShutter() throws Exception {
        M02sPhotoListReader.Result before=parse(entry("A.jpg"));
        assertEquals(M02sApPhotoProbePolicy.Status.NOT_VERIFIABLE,
                M02sApPhotoProbePolicy.compare(before,parse(entry("B.jpg"))).status);
        assertEquals(M02sApPhotoProbePolicy.Status.NOT_VERIFIABLE,
                M02sApPhotoProbePolicy.compare(before,parse(entry("A.jpg")+","
                        +entry("B.jpg")+","+entry("C.jpg"))).status);
    }
    @Test public void mixedFilesAreNotInventedAsNewImages() throws Exception {
        M02sPhotoListReader.Result before=parse(entry("A.jpg"));
        M02sPhotoListReader.Result after=parse(entry("A.jpg")+","
                +"{\"name\":\"Z.mp4\",\"size\":100}");
        assertEquals(M02sApPhotoProbePolicy.Status.NOT_VERIFIABLE,
                M02sApPhotoProbePolicy.compare(before,after).status);
    }
}
