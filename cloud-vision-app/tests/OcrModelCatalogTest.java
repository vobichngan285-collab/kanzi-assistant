package com.vobich.bluestar.cloudprobe;

/** RC17 JVM tests: text model is independent from image analysis model. */
public final class OcrModelCatalogTest {
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args){
        check(OcrModelCatalog.IDS.length==4,"four independent OCR choices");
        check(OcrModelCatalog.LABELS.length==4,"four labels");
        check(OcrModelCatalog.DEFAULT.equals("qwen-vl-ocr"),"dedicated default");
        check(OcrModelCatalog.PROMPT.equals(
            "识别图片中清晰可见的文字，按正确阅读顺序原样输出，不解释、不补充、不猜测。"),
            "exact user-approved short prompt");
        for(int i=0;i<OcrModelCatalog.IDS.length;i++){
            String id=OcrModelCatalog.IDS[i];
            check(OcrModelCatalog.indexOf(id)==i,"exact index for "+id);
            check(OcrModelCatalog.normalize(id).equals(id),"keep selected "+id);
            check(!OcrModelCatalog.label(id).isEmpty(),"label for "+id);
            check(OcrModelCatalog.forTask(true,VisionModelCatalog.MAX_38,id).equals(id),
                "OCR uses selected text model, never visual model");
            check(OcrModelCatalog.forTask(false,VisionModelCatalog.FLASH_37,id)
                .equals(VisionModelCatalog.FLASH_37),
                "menu/shop/hospital stays visual model independent of OCR");
            for(int j=i+1;j<OcrModelCatalog.IDS.length;j++)
                check(!id.equals(OcrModelCatalog.IDS[j]),"no duplicate model IDs");
        }
        for(String bad:new String[]{"",null,"qwen3-vl-plus","unknown","qwen3.5-flash"}){
            check(OcrModelCatalog.indexOf(bad)==-1,"invalid OCR ID");
            check(OcrModelCatalog.normalize(bad).equals(OcrModelCatalog.DEFAULT),
                "invalid text model falls back locally to dedicated OCR");
        }
        check(VisionModelCatalog.IDS.length==3,
            "visual model selector remains exactly three choices");
        System.out.println("RC17_OCR_FOUR_CHOICES_INDEPENDENT_AND_PROMPT=PASS");
    }
}
