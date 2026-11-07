package com.vobich.bluestar.cloudprobe;

/** One-shot web-search permission for explicit AI conversations only. */
final class SearchRoutePolicy {
    static final String PREF="cloud_smart_web_search_v1";
    static final boolean DEFAULT_ENABLED=true;

    private SearchRoutePolicy(){}

    static boolean allow(String taskId,String taskEngine,String model,boolean enabled){
        return enabled
            && !"ocr".equals(taskEngine)
            && VisionModelCatalog.indexOf(model)>=0
            && ("text-followup".equals(taskId)||"user-followup".equals(taskId));
    }
}
