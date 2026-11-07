package com.vobich.bluestar.cloudprobe;

/** RC18: no hidden search for OCR, photo tasks, native target checks or tests. */
public final class SearchRoutePolicyTest {
    private static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
    public static void main(String[] args){
        ok(SearchRoutePolicy.DEFAULT_ENABLED,"new RC18 web-search setting is enabled");
        for(String model:VisionModelCatalog.IDS){
            ok(SearchRoutePolicy.allow("text-followup","vision",model,true),
                "text-only conversation may search using "+model);
            ok(SearchRoutePolicy.allow("user-followup","vision",model,true),
                "recheck-image conversation may search using "+model);
            ok(!SearchRoutePolicy.allow("text-followup","vision",model,false),
                "setting OFF refuses search "+model);
            for(String other:new String[]{"ocr","menu","hospital","shop","product",
                    "voice-scene","voice-read","model-probe"}){
                ok(!SearchRoutePolicy.allow(other,"vision",model,true),
                    "one-off photo task must not search: "+other+" "+model);
            }
        }
        ok(!SearchRoutePolicy.allow("text-followup","ocr",VisionModelCatalog.FLASH_38,true),
            "OCR never web searches");
        ok(!SearchRoutePolicy.allow("user-followup","vision","qwen-vl-ocr",true),
            "dedicated OCR cannot search");
        ok(!SearchRoutePolicy.allow("user-followup","vision","qwen3-vl-plus",true),
            "retired visual model cannot search");
        System.out.println("RC18_SEARCH_ONLY_EXPLICIT_FOLLOWUP_THREE_MODELS=PASS");
    }
}
