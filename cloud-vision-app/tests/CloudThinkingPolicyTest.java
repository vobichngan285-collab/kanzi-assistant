package com.vobich.bluestar.cloudprobe;

public final class CloudThinkingPolicyTest {
    private static void same(String expected,String actual){
        if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args){
        String qwen="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        String zhipu="https://open.bigmodel.cn/api/paas/v4/chat/completions";
        same(CloudThinkingPolicy.QWEN,
            CloudThinkingPolicy.style(qwen,"qwen3.8-flash",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.QWEN,
            CloudThinkingPolicy.style(qwen,"qwen3.8-max",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.QWEN,
            CloudThinkingPolicy.style(qwen,"qwen3.7-flash",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.ZHIPU,
            CloudThinkingPolicy.style(zhipu,"glm-4.6v",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.ZHIPU,
            CloudThinkingPolicy.style(zhipu,"glm-4.6v-flashx",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.NONE,
            CloudThinkingPolicy.style(zhipu,"glm-ocr",CloudProviderPolicy.LAYOUT));
        same(CloudThinkingPolicy.NONE,
            CloudThinkingPolicy.style(qwen,"qwen-vl-ocr",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.NONE,
            CloudThinkingPolicy.style("https://dashscope.aliyuncs.com.evil.test/v1/chat/completions",
                "qwen3.8-flash",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.NONE,
            CloudThinkingPolicy.style("http://open.bigmodel.cn/api/paas/v4/chat/completions",
                "glm-4.6v",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.NONE,
            CloudThinkingPolicy.style("https://example.org/v1/chat/completions",
                "glm-4.6v",CloudProviderPolicy.CHAT));
        same(CloudThinkingPolicy.NONE,
            CloudThinkingPolicy.style(zhipu,"glm-4.6v",CloudProviderPolicy.ASR_QWEN));
        System.out.println("CLOUD_REASONING_ONLY_EXPLICIT_SUPPORTED_ENDPOINTS=PASS");
    }
}
