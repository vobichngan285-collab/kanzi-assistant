package com.vobichngan.bluestar.oemautoimport;

import static org.junit.Assert.*;
import org.junit.Test;

public final class AutoImportPolicyTest {
    @Test public void exactOemPackageOnly() {
        assertTrue(AutoImportPolicy.isOemPackage("com.starglasses.app"));
        assertFalse(AutoImportPolicy.isOemPackage("com.example.photo"));
        assertFalse(AutoImportPolicy.isOemPackage(null));
    }

    @Test public void parseOemNewFileBannerAndRejectOtherLabels() {
        assertEquals(1, AutoImportPolicy.pendingCount("发现1个文件"));
        assertEquals(2, AutoImportPolicy.pendingCount("发现2个文件"));
        assertEquals(-1,AutoImportPolicy.pendingCount("导入中... (1)"));
        assertEquals(-1,AutoImportPolicy.pendingCount("未发现设备"));
        assertEquals(-1,AutoImportPolicy.pendingCount("发现0个文件"));
    }

    @Test public void importButtonMustBeExact() {
        assertTrue(AutoImportPolicy.isExactImportButton("导入"));
        assertFalse(AutoImportPolicy.isExactImportButton("导入中... (1)"));
        assertFalse(AutoImportPolicy.isExactImportButton("删除"));
        assertFalse(AutoImportPolicy.isExactImportButton(null));
    }

    @Test public void neverClickWithoutNewSingleFileAndExplicitOptIn() {
        assertTrue(AutoImportPolicy.allowAutoClick(true,true,true,1,true,false,false));
        assertFalse(AutoImportPolicy.allowAutoClick(false,true,true,1,true,false,false));
        assertFalse(AutoImportPolicy.allowAutoClick(true,false,true,1,true,false,false));
        assertFalse(AutoImportPolicy.allowAutoClick(true,true,false,1,true,false,false));
        assertFalse(AutoImportPolicy.allowAutoClick(true,true,true,2,true,false,false));
        assertFalse(AutoImportPolicy.allowAutoClick(true,true,true,1,false,false,false));
        assertFalse(AutoImportPolicy.allowAutoClick(true,true,true,1,true,true,false));
        assertFalse(AutoImportPolicy.allowAutoClick(true,true,true,1,true,false,true));
    }
}
