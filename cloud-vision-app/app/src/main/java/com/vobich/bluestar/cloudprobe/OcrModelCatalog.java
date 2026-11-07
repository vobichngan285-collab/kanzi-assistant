package com.vobich.bluestar.cloudprobe;

/** Independent text-reading model choice. No cloud calls or auto fallback. */
final class OcrModelCatalog {
    static final String DEDICATED="qwen-vl-ocr";
    static final String DEFAULT=DEDICATED;
    // One user-approved instruction for every OCR choice. The editable
    // "朗读文字" preset defaults to this sentence and is sent automatically.
    static final String PROMPT="识别图片中清晰可见的文字，按正确阅读顺序原样输出，不解释、不补充、不猜测。";
    static final String[] IDS={
        DEDICATED,VisionModelCatalog.FLASH_37,
        VisionModelCatalog.FLASH_38,VisionModelCatalog.MAX_38
    };
    static final String[] LABELS={
        "专用 OCR（默认）",
        "Qwen3.7-Flash",
        "Qwen3.8-Flash",
        "Qwen3.8-Max"
    };
    private OcrModelCatalog(){}
    static int indexOf(String id){
        for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;
        return -1;
    }
    static String normalize(String id){return indexOf(id)<0?DEFAULT:id;}
    static String label(String id){
        int i=indexOf(id);return LABELS[i<0?0:i];
    }
    static String forTask(boolean textRecognition,String visual,String ocr){
        return textRecognition?normalize(ocr):VisionModelCatalog.normalize(visual);
    }
}
