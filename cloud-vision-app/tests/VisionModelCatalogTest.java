package com.vobich.bluestar.cloudprobe;

/** JVM-only regression: no old model, no silent plus fallback for valid selections. */
public final class VisionModelCatalogTest {
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(VisionModelCatalog.IDS.length==4,"four authorized choices");
        check(VisionModelCatalog.LABELS.length==4,"four UI labels");
        check(VisionModelCatalog.PLUS.equals(VisionModelCatalog.IDS[0]),"keep confirmed Plus default");
        for (int i=0;i<VisionModelCatalog.IDS.length;i++){
            String id=VisionModelCatalog.IDS[i];
            check(VisionModelCatalog.indexOf(id)==i,"correct index "+id);
            check(VisionModelCatalog.normalize(id).equals(id),"never change selected usable model "+id);
            check(!id.equals("qwen3-vl-flash"),"remove rejected old Flash");
        }
        check(VisionModelCatalog.normalize("qwen3-vl-flash").equals(VisionModelCatalog.PLUS),
            "legacy broken Flash migrates to known-working Plus");
        check(VisionModelCatalog.normalize(null).equals(VisionModelCatalog.PLUS),
            "null preference defaults to Plus");
        check(VisionModelCatalog.normalize("not-a-model").equals(VisionModelCatalog.PLUS),
            "unapproved value never reaches API");
        for (int i=1;i<VisionModelCatalog.IDS.length;i++)
            check(VisionModelCatalog.isBudgetFlash(VisionModelCatalog.IDS[i]),"Flash label");
        check(!VisionModelCatalog.isBudgetFlash(VisionModelCatalog.PLUS),"Plus label");
        System.out.println("VISION_MODEL_CATALOG_TESTS=PASS");
    }
}
