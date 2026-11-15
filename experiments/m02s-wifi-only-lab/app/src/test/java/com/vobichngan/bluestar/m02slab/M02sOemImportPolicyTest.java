package com.vobichngan.bluestar.m02slab;

import static org.junit.Assert.*;
import org.junit.Test;

public final class M02sOemImportPolicyTest {
    @Test public void originalOemFinishBranchesAndFrames() {
        assertArrayEquals(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,0,0x74},
                M02sOemImportPolicy.finishFrame(5,5));
        assertArrayEquals(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x31,1,0x76},
                M02sOemImportPolicy.finishFrame(5,1));
        assertArrayEquals(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,1,0x75},
                M02sOemImportPolicy.finishFrame(5,0));
        assertEquals(M02sOemImportPolicy.Branch.FULL,M02sOemImportPolicy.branch(1,1));
        assertEquals(M02sOemImportPolicy.Branch.ZERO,M02sOemImportPolicy.branch(0,0));
        assertEquals(M02sOemImportPolicy.Branch.PARTIAL,M02sOemImportPolicy.branch(10,7));
    }
    @Test public void refuseMalformedAndOutOfScopeFinishFrames() {
        assertFalse(M02sOemImportPolicy.authorizedFinish(new byte[]{(byte)0xAB,0x55,0,4,0x22,0x30,0,0x52}));
        assertFalse(M02sOemImportPolicy.authorizedFinish(null));
        assertFalse(M02sOemImportPolicy.authorizedFinish(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,2,0x76}));
        assertFalse(M02sOemImportPolicy.authorizedFinish(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x31,0,0x75}));
        assertFalse(M02sOemImportPolicy.authorizedFinish(new byte[]{(byte)0xAB,0x55,0,4,0x44,0x30,0,0x75}));
        for (byte[] allowed : new byte[][]{M02sOemImportPolicy.finishFrame(1,1),
                    M02sOemImportPolicy.finishFrame(5,1),
                    M02sOemImportPolicy.finishFrame(5,0)}) {
            assertTrue(M02sOemImportPolicy.authorizedFinish(allowed));
            assertFalse(M02sApWire.authorized(allowed)); // AP whitelist remains AP only
        }
    }
    @Test public void batchOnlyWhenOriginalListAllDownloadableAndBounded() {
        assertTrue(M02sOemImportPolicy.canCompleteWholeList(5,5));
        assertTrue(M02sOemImportPolicy.canCompleteWholeList(1,1));
        assertFalse(M02sOemImportPolicy.canCompleteWholeList(5,4));
        assertFalse(M02sOemImportPolicy.canCompleteWholeList(25,25));
        assertFalse(M02sOemImportPolicy.canCompleteWholeList(0,0));
    }
    @Test public void refuseBadCountsInsteadOfTruncatingOrGuessing() {
        for (int[] v : new int[][]{{-1,0},{5,-1},{4,5},{500,300}}) {
            try {M02sOemImportPolicy.finishFrame(v[0],v[1]);fail("unsafe count");}
            catch (IllegalArgumentException expected) {}
        }
    }
}
