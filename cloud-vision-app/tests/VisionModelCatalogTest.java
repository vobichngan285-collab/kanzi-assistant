package com.vobich.bluestar.cloudprobe;

/** Pure Java regression: three honest modes, migration, and safe model routing. */
public final class VisionModelCatalogTest {
    private static void check(boolean okay,String label){
        if(!okay)throw new AssertionError(label);
    }
    public static void main(String[] args){
        check(VisionModelCatalog.IDS.length==3,"exactly three selectable visual models");
        check(VisionModelCatalog.LABELS.length==3,"three unambiguous UI labels");
        check(VisionModelCatalog.FLASH_38.equals(VisionModelCatalog.DEFAULT),
            "default is user's connected Flash 3.8");
        check(VisionModelCatalog.IDS[0].equals(VisionModelCatalog.FLASH_38),
            "daily Flash first");
        check(VisionModelCatalog.IDS[1].equals(VisionModelCatalog.PLUS),
            "tested stable Plus second");
        check(VisionModelCatalog.IDS[2].equals(VisionModelCatalog.MAX_38),
            "emergency Max last");
        for(int i=0;i<VisionModelCatalog.IDS.length;i++){
            String id=VisionModelCatalog.IDS[i];
            check(VisionModelCatalog.indexOf(id)==i,"index of "+id);
            check(VisionModelCatalog.normalize(id).equals(id),
                "preserve explicit current choice "+id);
        }
        for(String removed:new String[]{"qwen3-vl-flash","qwen3.5-flash",
                                        "qwen3.7-flash","other-model",null,""}){
            check(VisionModelCatalog.indexOf(removed)==-1,"removed selection not allowed");
            check(VisionModelCatalog.DEFAULT.equals(VisionModelCatalog.normalize(removed)),
                "migrate old selection to known-working Flash");
        }
        check(VisionModelCatalog.isBudgetFlash(VisionModelCatalog.FLASH_38),
            "Flash is only daily cheap model");
        check(!VisionModelCatalog.isBudgetFlash(VisionModelCatalog.PLUS),
            "Plus is not treated as cheap Flash");
        check(!VisionModelCatalog.isBudgetFlash(VisionModelCatalog.MAX_38),
            "expensive Max must not be treated as Flash");
        check(VisionModelCatalog.isEmergencyMax(VisionModelCatalog.MAX_38),
            "Max requires explicit emergency mode");
        check(!VisionModelCatalog.isEmergencyMax(VisionModelCatalog.FLASH_38),
            "Flash is not Max");
        check(!VisionModelCatalog.isEmergencyMax(VisionModelCatalog.PLUS),
            "Plus is not Max");
        System.out.println("RC7_THREE_TIER_VISION_MODEL_TESTS=PASS");
    }
}
