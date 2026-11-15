package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

public final class M02sFastDownloadPlanTest {
    private M02sPhotoListReader.Result parse(String json) throws Exception {
        return M02sPhotoListReader.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void prioritizesLatestButRetainsEveryOriginalForFullExit() throws Exception {
        M02sPhotoListReader.Result result=parse("{\"info\":[{\"files\":["
                +"{\"name\":\"A_20261115090000.jpg\",\"size\":100},"
                +"{\"name\":\"B_20261115100000.jpg\",\"size\":100},"
                +"{\"name\":\"C_20261115120000.jpg\",\"size\":100}"
                +"]}]}");
        M02sPhotoPolicy.Candidate latest=M02sPhotoPolicy.uniqueLatestOrNull(result.candidates);
        List<M02sPhotoPolicy.Candidate> order=M02sFastDownloadPlan.plan(result,latest,true);
        assertEquals(3,order.size());
        assertEquals("C_20261115120000.jpg",order.get(0).remoteName);
        assertEquals("A_20261115090000.jpg",order.get(1).remoteName);
        assertEquals("B_20261115100000.jpg",order.get(2).remoteName);
        assertEquals(M02sOemImportPolicy.Branch.FULL,M02sOemImportPolicy.branch(3,order.size()));
        try {order.remove(0);fail("mutable plan");}
        catch(UnsupportedOperationException expected){}
    }

    @Test public void partModeOnlyLatestWhenMixedOrTooManyFiles() throws Exception {
        M02sPhotoListReader.Result mixed=parse("{\"info\":[{\"files\":["
                +"{\"name\":\"A_20261115110000.jpg\",\"size\":100},"
                +"{\"name\":\"VIDEO.mp4\",\"size\":100}"
                +"]}]}");
        assertFalse(M02sOemImportPolicy.canCompleteWholeList(mixed.entriesObserved,mixed.eligibleImages));
        List<M02sPhotoPolicy.Candidate> order=M02sFastDownloadPlan.plan(
                mixed,mixed.candidates.get(0),false);
        assertEquals(1,order.size());
        assertEquals(M02sOemImportPolicy.Branch.PARTIAL,
                M02sOemImportPolicy.branch(mixed.entriesObserved,order.size()));
        try {M02sFastDownloadPlan.plan(mixed,mixed.candidates.get(0),true);fail("unsafe FULL");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void neverInventLatestThatIsAbsent() throws Exception {
        M02sPhotoListReader.Result one=parse("{\"info\":[{\"files\":["
                +"{\"name\":\"A_20261115120000.jpg\",\"size\":100}]}]}");
        M02sPhotoPolicy.Candidate fake=new M02sPhotoPolicy.Candidate(
                "B_20261115130000.jpg",100,"","photo");
        try {M02sFastDownloadPlan.plan(one,fake,true);fail("fake latest accepted");}
        catch(IllegalArgumentException expected){}
    }
}
