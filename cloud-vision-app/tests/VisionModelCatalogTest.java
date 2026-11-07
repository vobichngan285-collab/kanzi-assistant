package com.vobich.bluestar.cloudprobe;

/** RC12 JVM regressions for three choices and migrated retired Plus. */
public final class VisionModelCatalogTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        check(VisionModelCatalog.IDS.length==3,"exactly three selectable models");
        check(VisionModelCatalog.LABELS.length==3,"three model labels");
        check(VisionModelCatalog.FLASH_38.equals(VisionModelCatalog.DEFAULT),"daily default");
        check(VisionModelCatalog.IDS[0].equals(VisionModelCatalog.FLASH_37),"budget first");
        check(VisionModelCatalog.IDS[1].equals(VisionModelCatalog.FLASH_38),"daily second");
        check(VisionModelCatalog.IDS[2].equals(VisionModelCatalog.MAX_38),"fast Max last");
        check(VisionModelCatalog.LABELS[2].contains("高精度快速"),"fast label");
        for(int i=0;i<VisionModelCatalog.IDS.length;i++){
            String id=VisionModelCatalog.IDS[i];
            check(VisionModelCatalog.indexOf(id)==i,"index "+id);
            check(VisionModelCatalog.normalize(id).equals(id),"retain valid "+id);
            for(int j=i+1;j<VisionModelCatalog.IDS.length;j++)
                check(!id.equals(VisionModelCatalog.IDS[j]),"distinct model IDs");
        }
        for(String retired:new String[]{"qwen3-vl-plus","qwen3-vl-flash",
                 "qwen3.5-flash","invalid",null,""}){
            check(VisionModelCatalog.indexOf(retired)==-1,"retired "+retired);
            check(VisionModelCatalog.DEFAULT.equals(VisionModelCatalog.normalize(retired)),
                "locally migrate retired model "+retired);
        }
        check(VisionModelCatalog.isBudgetFlash(VisionModelCatalog.FLASH_37),"Flash37");
        check(VisionModelCatalog.isBudgetFlash(VisionModelCatalog.FLASH_38),"Flash38");
        check(!VisionModelCatalog.isBudgetFlash(VisionModelCatalog.MAX_38),"Max not Flash");
        check(VisionModelCatalog.isEmergencyMax(VisionModelCatalog.MAX_38),"Max detection");
        System.out.println("RC12_MODEL_CATALOG_MIGRATION=PASS");
    }
}
