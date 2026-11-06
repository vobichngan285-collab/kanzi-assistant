package com.vobich.bluestar.cloudprobe;

/** Whitelist of user-selectable image-question models (OCR stays qwen-vl-ocr). */
final class VisionModelCatalog {
    static final String PLUS = "qwen3-vl-plus";
    static final String FLASH_37 = "qwen3.7-flash";
    static final String FLASH_35 = "qwen3.5-flash";
    static final String FLASH_38 = "qwen3.8-flash";
    static final String[] IDS = {PLUS, FLASH_37, FLASH_35, FLASH_38};
    static final String[] LABELS = {
        "qwen3-vl-plus（已验证可用 · 稳定备用）",
        "qwen3.7-flash（低成本 · 待连接测试）",
        "qwen3.5-flash（低成本 · 待连接测试）",
        "qwen3.8-flash（较新的 Flash · 待连接测试）"
    };

    private VisionModelCatalog() {}

    static int indexOf(String value) {
        for (int i=0; i<IDS.length; i++) if (IDS[i].equals(value)) return i;
        return -1;
    }
    /** Broken historical qwen3-vl-flash preference is deliberately never retried. */
    static String normalize(String value) {
        return indexOf(value)<0 ? PLUS : value;
    }
    static boolean isBudgetFlash(String value) {
        return !PLUS.equals(normalize(value));
    }
}
