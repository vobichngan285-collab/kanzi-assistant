package com.vobich.bluestar.cloudprobe;

/**
 * Exactly three user-visible visual modes. OCR and ASR have their own fixed
 * engines and must never appear as interchangeable visual providers.
 */
final class VisionModelCatalog {
    static final String FLASH_38 = "qwen3.8-flash";
    static final String PLUS = "qwen3-vl-plus";
    static final String MAX_38 = "qwen3.8-max";
    static final String DEFAULT = FLASH_38;

    static final String[] IDS = {FLASH_38, PLUS, MAX_38};
    static final String[] LABELS = {
        "日常 · Qwen3.8-Flash",
        "稳定备用 · Qwen3-VL-Plus",
        "高精度应急 · Qwen3.8-Max"
    };

    private VisionModelCatalog() {}

    static int indexOf(String value) {
        for (int i=0; i<IDS.length; i++) if (IDS[i].equals(value)) return i;
        return -1;
    }

    /**
     * Never retain removed models: qwen3-vl-flash, qwen3.5-flash,
     * qwen3.7-flash. Migrating a stored choice does NOT issue an API request.
     */
    static String normalize(String value) {
        return indexOf(value)<0 ? DEFAULT : value;
    }

    static boolean isBudgetFlash(String value) {
        return FLASH_38.equals(value);
    }

    static boolean isEmergencyMax(String value) {
        return MAX_38.equals(value);
    }
}
