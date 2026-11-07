package com.vobich.bluestar.cloudprobe;

/**
 * Three visible models: two Flash and fast-response high-precision Max.
 * engines and must never appear as interchangeable visual providers.
 */
final class VisionModelCatalog {
    static final String FLASH_37 = "qwen3.7-flash";
    static final String FLASH_38 = "qwen3.8-flash";
    static final String MAX_38 = "qwen3.8-max";
    static final String DEFAULT = FLASH_38;

    static final String[] IDS = {FLASH_37, FLASH_38, MAX_38};
    static final String[] LABELS = {
        "超省 · Qwen3.7-Flash",
        "日常 · Qwen3.8-Flash",
        "高精度快速 · Qwen3.8-Max"
    };

    private VisionModelCatalog() {}

    static int indexOf(String value) {
        for (int i=0; i<IDS.length; i++) if (IDS[i].equals(value)) return i;
        return -1;
    }

    /**
     * Retain all four supported choices, including qwen3.7-flash.
     * Removed qwen3-vl-plus, qwen3-vl-flash and qwen3.5-flash migrate to the
     * proven 3.8 default without issuing any API request.
     */
    static String normalize(String value) {
        return indexOf(value)<0 ? DEFAULT : value;
    }

    static boolean isBudgetFlash(String value) {
        return FLASH_37.equals(value)||FLASH_38.equals(value);
    }

    static boolean isEmergencyMax(String value) {
        return MAX_38.equals(value);
    }
}
