package com.vobich.bluestar.cloudprobe;

import android.util.Base64;
import android.net.Network;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * One user-triggered photo -> one conservative storefront check.
 * NO frame streaming, no camera changes, no always-on recording.
 * A model's unsupported guesses must never turn into navigation instructions.
 */
final class TargetVisionClient {
    static final class Result {
        final String decision, message, evidence, serverModel;
        final int promptTokens, completionTokens;
        Result(String d,String m,String e,String model,int prompt,int completion){
            decision=d;message=m;evidence=e;serverModel=model;
            promptTokens=prompt;completionTokens=completion;
        }
        boolean confirmed(){return "found".equals(decision);}
    }
    private TargetVisionClient(){}
    static Result inspect(byte[] jpeg,String target,String key,String visualModel) throws Exception {
        return inspect(jpeg,target,key,visualModel,null);
    }

    static Result inspect(byte[] jpeg,String target,String key,String visualModel,
                          Network cloudNetwork) throws Exception {
        return inspectInternal(jpeg,target,key,visualModel,cloudNetwork,null,null);
    }
    static Result inspect(byte[] jpeg,String target,CloudProviderStore.Config chosen,
                          Network cloudNetwork)throws Exception{
        return inspect(jpeg,target,chosen,cloudNetwork,null);
    }
    static Result inspect(byte[] jpeg,String target,CloudProviderStore.Config chosen,
                          Network cloudNetwork,CloudVisionClient.Cancellation cancel)throws Exception{
        return inspectInternal(jpeg,target,chosen.key,chosen.model,cloudNetwork,chosen,cancel);
    }
    private static Result inspectInternal(byte[] jpeg,String target,String key,String visualModel,
                          Network cloudNetwork,CloudProviderStore.Config config,
                          CloudVisionClient.Cancellation cancel)throws Exception{
        if(cancel!=null)cancel.check();
        if(jpeg==null||jpeg.length<200)throw new Exception("没有有效的照片");
        if(target==null||target.trim().isEmpty())throw new Exception("没有寻找目标");
        if(key==null||key.trim().isEmpty())throw new Exception("请先设置百炼 API Key");
        boolean custom=config!=null&&config.custom;
        if(!custom&&VisionModelCatalog.indexOf(visualModel)<0)
            throw new IllegalArgumentException("视觉模型未获支持，无法寻找目标");
        String model=visualModel;
        // Use natural image-relative words only, never distances, street directions or GPS.
        String instruction="你是盲人辅助寻找物品的视觉审核员。要寻找的目标是："+target+
            "。仅根据本张真实照片判断目标是否作为实际店铺、场所、清晰真实门头招牌或物体出现。"+
            "实际商铺或建筑门口的清晰招牌可以是证据，但广告图片、地图标记、"+
            "不属于目标场所的孤立指示牌、模糊难辨文字不能算已找到真实目标。"+
            "明确看见符合目标的实体和证据才给found，疑似给possible，否则not_found。"+
            "请严格输出一行JSON对象，字段decision只能为found/possible/not_found，"+
            "字段position只能为左侧/中间/右侧/画面中/未知，"+
            "字段evidence为十个中文字以内的照片可见依据，禁止推测道路距离和行走安全。";
        JSONObject image=new JSONObject().put("type","image_url").put("image_url",
            new JSONObject().put("url","data:image/jpeg;base64,"+
                Base64.encodeToString(jpeg,Base64.NO_WRAP)));
        JSONObject body=new JSONObject().put("model",model).put("stream",false)
            .put("max_tokens",260)
            .put("messages",new JSONArray().put(new JSONObject().put("role","user")
                .put("content",new JSONArray()
                    .put(new JSONObject().put("type","text").put("text",instruction))
                    .put(image))));
        // Other vendors may reject forced JSON format. The prompt and
        // conservative result parsing still guard against unsupported guesses.
        if(!custom)body.put("response_format",
            new JSONObject().put("type","json_object"));
        // This path requests JSON Object output. Bailian rejects thinking=true
        // together with JSON mode, so even manual Max uses direct JSON here.
        if(!custom&&(VisionModelCatalog.isBudgetFlash(model) ||
           VisionModelCatalog.isEmergencyMax(model)))
            body.put("enable_thinking",false);
        URL endpoint=new URL(custom?CloudProviderPolicy.validatedUrl(config.url):
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
        HttpURLConnection c=(HttpURLConnection)(cloudNetwork==null?
            endpoint.openConnection():cloudNetwork.openConnection(endpoint));
        c.setRequestMethod("POST");
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(12000);c.setReadTimeout(45000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization","Bearer "+key.trim());
        c.setRequestProperty("Content-Type","application/json");
        if(cancel!=null)cancel.attach(c);
        try{
            try(OutputStream out=c.getOutputStream()){
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            if(cancel!=null)cancel.check();
            int status=c.getResponseCode();
            if(status<200||status>=300)
                throw new Exception("目标寻找云端请求失败 HTTP "+status+
                    "，请确认模型权限及网络");
            JSONObject response=new JSONObject(readSmall(c.getInputStream()));
            if(cancel!=null)cancel.check();
            JSONArray choices=response.optJSONArray("choices");
            if(choices==null||choices.length()==0)throw new Exception("云端没有返回视觉结果");
            String raw=choices.getJSONObject(0).getJSONObject("message")
                .optString("content","").trim();
            String serverModel=response.optString("model","");
            JSONObject usage=response.optJSONObject("usage");
            int promptTokens=usage==null?-1:usage.optInt("prompt_tokens",-1);
            int completionTokens=usage==null?-1:usage.optInt("completion_tokens",-1);
            // If the model violates JSON format, treat as uncertainty, NEVER a match.
            JSONObject value;
            try{value=new JSONObject(raw);}
            catch(Exception ignored){
                return new Result("possible","本张照片无法确认是否有"+safeTarget(target),"格式异常",
                    serverModel,promptTokens,completionTokens);
            }
            String decision=value.optString("decision","not_found");
            String position=value.optString("position","未知");
            String evidence=value.optString("evidence","");
            if(!"found".equals(decision)&&!"possible".equals(decision))
                decision="not_found";
            if(!"左侧".equals(position)&&!"中间".equals(position)&&
                !"右侧".equals(position)&&!"画面中".equals(position))
                position="未知";
            String t=safeTarget(target);
            if("found".equals(decision)){
                // A naked 'found' without any visible evidence is not enough
                // to give a high-confidence spoken notification.
                if(evidence.trim().isEmpty())
                    return new Result("possible","可能看到"+t+"，但还无法确认。","",
                        serverModel,promptTokens,completionTokens);
                String where=("未知".equals(position)||"画面中".equals(position))?
                    "画面中":("画面"+position);
                return new Result(decision,"发现"+t+"，就在"+where+"。",evidence,
                    serverModel,promptTokens,completionTokens);
            }
            if("possible".equals(decision))
                return new Result(decision,"可能看到"+t+"，但这张照片还不能确认。",evidence,
                    serverModel,promptTokens,completionTokens);
            return new Result(decision,"这张照片暂时没看到"+t+"。",evidence,
                serverModel,promptTokens,completionTokens);
        }finally{
            if(cancel!=null)cancel.detach(c);
            c.disconnect();
        }
    }
    private static String safeTarget(String target){
        // Prevent a 30-character target becoming an unwieldy spoken sentence.
        String clean=target.replaceAll("[\\r\\n]","").trim();
        return clean.length()>16?clean.substring(0,16):clean;
    }
    private static String readSmall(InputStream stream)throws Exception{
        if(stream==null)return "";
        StringBuilder out=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            String line;
            while((line=r.readLine())!=null){
                if(out.length()+line.length()>300000)throw new Exception("响应过大");
                out.append(line);
            }
        }
        return out.toString();
    }
}
