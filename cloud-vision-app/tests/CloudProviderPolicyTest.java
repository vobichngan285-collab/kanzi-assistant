package com.vobich.bluestar.cloudprobe;

/** Pure Java regression for independent provider/transport selections. */
public final class CloudProviderPolicyTest {
    private static void ok(boolean condition){if(!condition)throw new AssertionError();}
    private static void fails(Runnable job){
        boolean failed=false;
        try{job.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed);
    }
    public static void main(String[] args){
        ok(CloudProviderPolicy.accepts("ocr","chat"));
        ok(CloudProviderPolicy.accepts("ocr","layout_parsing"));
        ok(!CloudProviderPolicy.accepts("vision","layout_parsing"));
        ok(!CloudProviderPolicy.accepts("asr","layout_parsing"));
        ok(CloudProviderPolicy.recommendedFastStream("glm-4.6v-flash","chat"));
        ok(CloudProviderPolicy.recommendedFastStream("qwen3.7-flash","chat"));
        ok(!CloudProviderPolicy.recommendedFastStream("glm-ocr","layout_parsing"));
        ok(!CloudProviderPolicy.recommendedFastStream("unknown-other","chat"));
        String glmUrl="https://open.bigmodel.cn/api/paas/v4/chat/completions";
        ok(CloudProviderPolicy.zhipuFastVisualOcr(glmUrl,"glm-4.6v"));
        ok(CloudProviderPolicy.zhipuFastVisualOcr(glmUrl,"glm-4.6v-flashx"));
        ok(CloudProviderPolicy.zhipuFastVisualOcr(glmUrl,"glm-4.6v-flash"));
        ok(!CloudProviderPolicy.zhipuFastVisualOcr(glmUrl,"glm-ocr"));
        ok(!CloudProviderPolicy.zhipuFastVisualOcr(glmUrl,"glm-5.3-flash"));
        ok(!CloudProviderPolicy.zhipuFastVisualOcr(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions","glm-4.6v"));
        ok(!CloudProviderPolicy.zhipuFastVisualOcr(
            "https://open.bigmodel.cn/api/paas/v4/layout_parsing","glm-4.6v"));
        ok(!CloudProviderPolicy.zhipuFastVisualOcr(
            "https://open.bigmodel.cn.evil.example/chat/completions","glm-4.6v"));
        String oldPrompt="用户自定义逐字原样朗读";
        ok(oldPrompt.equals(CloudProviderPolicy.fastZhipuOcrInstruction(
            oldPrompt,false,true))); // Qwen untouched
        ok(oldPrompt.equals(CloudProviderPolicy.fastZhipuOcrInstruction(
            oldPrompt,true,false))); // User-edited GLM unchanged
        String fastPrompt=CloudProviderPolicy.fastZhipuOcrInstruction(
            oldPrompt,true,true);
        ok(fastPrompt.contains("逐行")&&fastPrompt.contains("原文"));
        ok(!fastPrompt.equals(oldPrompt));
        ok(CloudProviderPolicy.probeRecognizedA("A"));
        ok(CloudProviderPolicy.probeRecognizedA("字母 A"));
        ok(CloudProviderPolicy.probeRecognizedA("Ａ"));
        ok(!CloudProviderPolicy.probeRecognizedA("无法识别字母A"));
        ok(!CloudProviderPolicy.probeRecognizedA("不支持"));

        ok(CloudProviderPolicy.accepts("vision","chat"));
        ok(!CloudProviderPolicy.accepts("ocr","asr_multipart"));
        ok(!CloudProviderPolicy.accepts("vision","asr_qwen"));
        ok(CloudProviderPolicy.accepts("asr","asr_qwen"));
        ok(CloudProviderPolicy.accepts("asr","asr_multipart"));
        ok(CloudProviderPolicy.accepts("asr","asr_chat_audio"));
        ok(!CloudProviderPolicy.accepts("asr","chat"));
        ok(!CloudProviderPolicy.accepts("other","chat"));
        ok(CloudProviderPolicy.validatedUrl(
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions")
            .endsWith("/chat/completions"));
        ok(CloudProviderPolicy.validatedUrl(
            "https://api.example.test/v1/audio/transcriptions?api-version=2026-01").contains("?"));
        fails(()->CloudProviderPolicy.validatedUrl("http://api.example.test/v1/chat/completions"));
        fails(()->CloudProviderPolicy.validatedUrl("file:///tmp/bad"));
        fails(()->CloudProviderPolicy.validatedUrl("https://user:pass@api.example.test/api"));
        fails(()->CloudProviderPolicy.validatedUrl("https://api.example.test/"));
        fails(()->CloudProviderPolicy.cleanModel(" "));
        fails(()->CloudProviderPolicy.cleanModel("a\nb"));
        ok("doubao-seed-vision".equals(CloudProviderPolicy.cleanModel(" doubao-seed-vision ")));
        System.out.println("PASS CloudProviderPolicyTest: independent roles, protocol allowlist, HTTPS and model validation");
    }
}
