package com.vobich.bluestar.cloudprobe;

/** JVM regression: low-cost visual mode and the three prior options coexist. */
public final class VisionModelCatalogTest {
    private static void check(boolean okay,String label){
        if(!okay)throw new AssertionError(label);
    }
    public static void main(String[] args){
        check(VisionModelCatalog.IDS.length==4,"exactly four selectable visual models");
        check(VisionModelCatalog.LABELS.length==4,"four short distinct UI labels");
        check(VisionModelCatalog.FLASH_38.equals(VisionModelCatalog.DEFAULT),
            "do not silently change the known-working daily default");
        check(VisionModelCatalog.IDS[0].equals(VisionModelCatalog.FLASH_37),
            "new low-cost 3.7 displayed first");
        check(VisionModelCatalog.IDS[1].equals(VisionModelCatalog.FLASH_38),
            "existing daily Flash retained");
        check(VisionModelCatalog.IDS[2].equals(VisionModelCatalog.PLUS),
            "tested stable Plus must NOT be removed in this version");
        check(VisionModelCatalog.IDS[3].equals(VisionModelCatalog.MAX_38),
            "manual expensive Max stays last");
        check(VisionModelCatalog.LABELS[0].contains("超省"),
            "low-cost model clearly labeled");
        for(int i=0;i<VisionModelCatalog.IDS.length;i++){
            String id=VisionModelCatalog.IDS[i];
            check(VisionModelCatalog.indexOf(id)==i,"catalog index of "+id);
            check(VisionModelCatalog.normalize(id).equals(id),
                "preserve explicit selected model without network call: "+id);
            for(int j=i+1;j<VisionModelCatalog.IDS.length;j++)
                check(!id.equals(VisionModelCatalog.IDS[j]),"duplicate visual model");
        }
        for(String removed:new String[]{"qwen3-vl-flash","qwen3.5-flash",
                                        "other-model",null,""}){
            check(VisionModelCatalog.indexOf(removed)==-1,"unsupported model disallowed");
            check(VisionModelCatalog.DEFAULT.equals(VisionModelCatalog.normalize(removed)),
                "migrate unsupported old choice locally to stable default");
        }
        check(VisionModelCatalog.isBudgetFlash(VisionModelCatalog.FLASH_37),
            "3.7-Flash disables optional thinking for cheaper image requests");
        check(VisionModelCatalog.isBudgetFlash(VisionModelCatalog.FLASH_38),
            "preserve 3.8-Flash direct request behavior");
        check(!VisionModelCatalog.isBudgetFlash(VisionModelCatalog.PLUS),
            "Plus keeps its existing request behavior");
        check(!VisionModelCatalog.isBudgetFlash(VisionModelCatalog.MAX_38),
            "expensive Max must never use Flash accounting/behavior");
        check(VisionModelCatalog.isEmergencyMax(VisionModelCatalog.MAX_38),
            "Max remains manual emergency mode");
        for(String value:new String[]{VisionModelCatalog.FLASH_37,
             VisionModelCatalog.FLASH_38,VisionModelCatalog.PLUS})
            check(!VisionModelCatalog.isEmergencyMax(value),
                "regular model must not trigger Max thinking budget: "+value);
        System.out.println("RC10_FOUR_VISUAL_CHOICES_AND_CHEAP_FLASH_TESTS=PASS");
    }
}
