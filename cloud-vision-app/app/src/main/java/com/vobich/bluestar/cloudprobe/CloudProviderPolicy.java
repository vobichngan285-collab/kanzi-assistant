package com.vobich.bluestar.cloudprobe;

import java.net.URI;
import java.util.Locale;

/** Pure Java allow-list for user-configured OpenAI-compatible HTTP endpoints. */
final class CloudProviderPolicy {
    static final String OCR="ocr", VISION="vision", ASR="asr";
    static final String CHAT="chat", LAYOUT="layout_parsing", ASR_QWEN="asr_qwen";
    static final String ASR_MULTIPART="asr_multipart", ASR_CHAT_AUDIO="asr_chat_audio";
    private CloudProviderPolicy(){}

    static boolean validRole(String role){
        return OCR.equals(role)||VISION.equals(role)||ASR.equals(role);
    }
    static boolean accepts(String role,String protocol){
        if(!validRole(role))return false;
        if(ASR.equals(role))
            return ASR_QWEN.equals(protocol)||ASR_MULTIPART.equals(protocol)||
                ASR_CHAT_AUDIO.equals(protocol);
        return CHAT.equals(protocol)||(OCR.equals(role)&&LAYOUT.equals(protocol));
    }
    /** The original experimental build persisted stream=false on every new
     * custom Chat profile, including models already known to support SSE.
     * Migrate ONLY these verified families once, and preserve later opt-outs. */
    static boolean recommendedFastStream(String model,String protocol){
        if(!CHAT.equals(protocol)||model==null)return false;
        String name=model.trim().toLowerCase(java.util.Locale.ROOT);
        return name.equals("glm-4.6v-flash")||
               name.equals("qwen3.7-flash")||
               name.equals("qwen3.8-flash")||
               name.equals("qwen3.8-max");
    }
    /** The two tested vendor Chat models use the SAME photo/prompt/SSE/OCR/TTS
     * path as Qwen. Only their native thinking flag differs on Zhipu's endpoint.
     * Never attach provider-specific parameters to Bailian or other services. */
    static boolean zhipuFastVisualOcr(String url,String model){
        if(url==null||model==null)return false;
        String name=model.trim().toLowerCase(Locale.ROOT);
        if(!("glm-4.6v".equals(name)||"glm-4.6v-flashx".equals(name)||
                "glm-4.6v-flash".equals(name)))return false;
        try{
            URI endpoint=new URI(url.trim());
            String host=endpoint.getHost();
            return "https".equalsIgnoreCase(endpoint.getScheme())&&
                host!=null&&("open.bigmodel.cn".equalsIgnoreCase(host)||
                    "api.z.ai".equalsIgnoreCase(host))&&
                endpoint.getPath()!=null&&
                endpoint.getPath().endsWith("/chat/completions");
        }catch(Exception ignored){return false;}
    }
    /** Experimental concise OCR instruction, ONLY for the stock photo OCR task
     * on the recognized official Zhipu Chat endpoint. Never overwrite a
     * user-edited instruction or alter Qwen / GLM-OCR Layout requests. */
    static String fastZhipuOcrInstruction(String original,
                                          boolean officialZhipuChat,
                                          boolean untouchedStockOcr){
        if(!officialZhipuChat||!untouchedStockOcr)return original;
        return "逐行原样识别照片中清晰可见的全部文字，每行换行。"
            +"仅输出原文，不加前言、说明、总结、重复内容或猜测。";
    }
    /** A real OCR probe must return the intended glyph, not just HTTP 200. */
    static boolean probeRecognizedA(String reply){
        if(reply==null)return false;
        String text=reply.trim().replace("Ａ","A").replace("ａ","a")
            .replaceAll("[\\s\\p{Punct}。！：，、]+","");
        return "A".equalsIgnoreCase(text)||"字母A".equalsIgnoreCase(text);
    }
    static String validatedUrl(String raw){
        if(raw==null||raw.trim().isEmpty())throw new IllegalArgumentException("请填写完整 HTTPS API 地址");
        String value=raw.trim();
        if(value.length()>1024)throw new IllegalArgumentException("接口地址过长");
        try{
            URI uri=new URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme())||
                uri.getHost()==null||uri.getHost().isEmpty()||
                uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||
                uri.getRawPath()==null||uri.getRawPath().length()<2)
                throw new IllegalArgumentException("仅支持完整 HTTPS 接口地址，不能包含账号密码或锚点");
            return uri.toString();
        }catch(java.net.URISyntaxException error){
            throw new IllegalArgumentException("接口地址格式错误");
        }
    }
    static String cleanModel(String model){
        String value=model==null?"":model.trim();
        if(value.length()<1||value.length()>180||
            value.indexOf('\n')>=0||value.indexOf('\r')>=0)
            throw new IllegalArgumentException("请填写厂商提供的准确模型 ID");
        return value;
    }
    static String cleanName(String name){
        String value=name==null?"":name.trim();
        if(value.isEmpty()||value.length()>40)
            throw new IllegalArgumentException("配置名称需要 1 到 40 字");
        return value;
    }
}
