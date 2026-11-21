package com.vobich.bluestar.cloudprobe;

import java.net.URI;
import java.util.Locale;

/** Explicit vendor allow-list. Unknown providers or OCR Layout never receive a
 * guessed thinking field; default to the already-tested non-thinking behavior. */
final class CloudThinkingPolicy {
    static final String NONE="", QWEN="qwen", ZHIPU="zhipu";
    private CloudThinkingPolicy(){}

    static String style(String url,String model,String protocol){
        if(!CloudProviderPolicy.CHAT.equals(protocol)||url==null||model==null)return NONE;
        if(CloudProviderPolicy.zhipuFastVisualOcr(url,model))return ZHIPU;
        try{
            URI u=new URI(url.trim());
            String host=u.getHost(),path=u.getPath();
            if(!"https".equalsIgnoreCase(u.getScheme())||
               host==null||path==null||
               !path.endsWith("/chat/completions")||
               !"dashscope.aliyuncs.com".equalsIgnoreCase(host))return NONE;
            String id=model.trim().toLowerCase(Locale.ROOT);
            if("qwen3.7-flash".equals(id)||"qwen3.8-flash".equals(id)||
               "qwen3.8-max".equals(id))return QWEN;
        }catch(Exception ignored){}
        return NONE;
    }
}
