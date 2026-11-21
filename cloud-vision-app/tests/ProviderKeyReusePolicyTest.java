package com.vobich.bluestar.cloudprobe;

/** Prevent credential reuse across similar-looking/different providers. */
public final class ProviderKeyReusePolicyTest {
    private static void check(boolean result,boolean expected,String label){
        if(result!=expected)throw new AssertionError(label+" result="+result);
    }
    public static void main(String[] args){
        String z="https://open.bigmodel.cn/api/paas/v4/chat/completions";
        String z2="https://open.bigmodel.cn/api/paas/v4/layout_parsing";
        String q="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        check(ProviderKeyReusePolicy.sameProvider(z,z2),true,"Zhipu Chat/Layout same origin");
        check(ProviderKeyReusePolicy.sameProvider(z,q),false,"Zhipu not Bailian");
        check(ProviderKeyReusePolicy.sameProvider(z,
              "https://open.bigmodel.cn.evil.org/api/paas/v4/chat/completions"),
              false,"Host suffix spoof");
        check(ProviderKeyReusePolicy.sameProvider(z,
              "http://open.bigmodel.cn/api/paas/v4/chat/completions"),
              false,"HTTPS only");
        check(ProviderKeyReusePolicy.sameProvider(z,
              "https://open.bigmodel.cn:443/api/paas/v4/chat/completions"),
              true,"Explicit default HTTPS port");
        check(ProviderKeyReusePolicy.sameProvider(z,
              "https://open.bigmodel.cn:8443/api/paas/v4/chat/completions"),
              false,"Port mismatch");
        check(ProviderKeyReusePolicy.sameProvider(z,
              "https://name:pass@open.bigmodel.cn/api/paas/v4/chat/completions"),
              false,"Credential-bearing URL");
        check(ProviderKeyReusePolicy.sameProvider(z,null),false,"Null URL");
        System.out.println("SAME_PROVIDER_KEY_REUSE_SECURITY=PASS");
    }
}
