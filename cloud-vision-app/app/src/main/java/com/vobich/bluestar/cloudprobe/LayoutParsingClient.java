package com.vobich.bluestar.cloudprobe;

import android.net.Network;
import android.os.SystemClock;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Generic compatible Layout Parsing OCR transport, initially verified against
 * the GLM-OCR public spec. Unlike Chat Completions this endpoint expects
 * {"model":"glm-ocr","file":"data:image/jpeg;base64,..."} and replies with
 * {"md_results":"recognized text", "usage":{...}}.
 *
 * It is deliberately non-streaming: Layout Parsing returns a complete
 * document, and must not be wrapped in Chat messages or fake SSE.
 */
final class LayoutParsingClient {
    private LayoutParsingClient(){}

    static CloudVisionClient.Result recognize(byte[] jpeg,CloudProviderStore.Config choice,
                                              Network network)throws Exception{
        if(choice==null||!CloudProviderStore.LAYOUT.equals(choice.protocol))
            throw new IllegalArgumentException("未选择 Layout Parsing 接口");
        if(jpeg==null||jpeg.length<16)
            throw new IllegalArgumentException("缺少需要识别的 JPEG 图片");
        if(jpeg.length>10*1024*1024)
            throw new IllegalArgumentException("图片超过 GLM-OCR 单张10MB限制");
        URL endpoint=new URL(CloudProviderPolicy.validatedUrl(choice.url));
        HttpURLConnection c=(HttpURLConnection)(network==null?
            endpoint.openConnection():network.openConnection(endpoint));
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");
        c.setConnectTimeout(12000);
        c.setReadTimeout(65000);
        c.setRequestProperty("Authorization","Bearer "+choice.key);
        c.setRequestProperty("Content-Type","application/json");
        c.setDoOutput(true);
        long start=SystemClock.elapsedRealtime(),upload=-1,headers=-1;
        try{
            JSONObject payload=new JSONObject()
                .put("model",CloudProviderPolicy.cleanModel(choice.model))
                .put("file","data:image/jpeg;base64,"+
                    Base64.encodeToString(jpeg,Base64.NO_WRAP));
            try(OutputStream out=c.getOutputStream()){
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
            upload=SystemClock.elapsedRealtime()-start;
            int http=c.getResponseCode();
            headers=SystemClock.elapsedRealtime()-start;
            if(http<200||http>=300){
                String raw=readLimited(c.getErrorStream());
                String reason="请求参数或模型权限不符合该接口要求";
                try{
                    JSONObject error=new JSONObject(raw);
                    String code=error.optString("code","");
                    String message=error.optString("msg",error.optString("message",""));
                    JSONObject nested=error.optJSONObject("error");
                    if(nested!=null){
                        code=nested.optString("code",code);
                        message=nested.optString("message",message);
                    }
                    if(!code.isEmpty())reason=code;
                    else if(!message.isEmpty())reason=message.length()>100?
                        message.substring(0,100):message;
                }catch(Exception ignored){}
                throw new Exception("Layout Parsing HTTP "+http+"："+reason);
            }
            JSONObject raw=new JSONObject(readLimited(c.getInputStream()));
            JSONObject data=raw.optJSONObject("data");
            JSONObject result=data==null?raw:data;
            String markdown=result.optString("md_results","").trim();
            if(markdown.isEmpty())
                throw new Exception("Layout Parsing 未返回 md_results 文字，不能当作OCR成功");
            String readable=OcrResponseGate.requireReadable(markdown);
            JSONObject usage=result.optJSONObject("usage");
            if(usage==null)usage=raw.optJSONObject("usage");
            String serverModel=result.optString("model",raw.optString("model",""));
            return new CloudVisionClient.Result(readable,choice.model,serverModel,
                SystemClock.elapsedRealtime()-start,upload,-1,-1,-1,headers,
                usage==null?-1:usage.optInt("prompt_tokens",-1),
                usage==null?-1:usage.optInt("completion_tokens",-1));
        }finally{c.disconnect();}
    }

    private static String readLimited(InputStream stream)throws Exception{
        if(stream==null)return "";
        try(InputStream in=stream;ByteArrayOutputStream data=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){
                if(data.size()+n>2*1024*1024)
                    throw new Exception("版面解析响应超过2MB安全限制");
                data.write(buffer,0,n);
            }
            return data.toString("UTF-8");
        }
    }
}
