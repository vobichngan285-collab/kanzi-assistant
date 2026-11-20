package com.vobich.bluestar.cloudprobe;

import android.net.Network;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Independent ASR endpoint. Existing Qwen3 WAV Chat Completions stays default;
 * user-configured ASR can use Qwen-compatible JSON, Chat audio, or the standard
 * OpenAI-compatible /audio/transcriptions multipart file+model payload.
 * No WAV, secret, transcript or endpoint is logged.
 */
final class CloudAsrClient {
    static final String ENDPOINT="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private CloudAsrClient(){}

    static String recognize(byte[] wav,String key)throws Exception{
        return recognize(wav,key,null);
    }
    static String recognize(byte[] wav,String key,Network cloudNetwork)throws Exception{
        return recognizeInternal(wav,key,ENDPOINT,"qwen3-asr-flash",
            CloudProviderPolicy.ASR_QWEN,cloudNetwork);
    }
    static String recognize(byte[] wav,CloudProviderStore.Config chosen,
                            Network cloudNetwork)throws Exception{
        if(chosen==null)throw new IllegalArgumentException("未选择语音识别服务商");
        return recognizeInternal(wav,chosen.key,
            chosen.custom?CloudProviderPolicy.validatedUrl(chosen.url):ENDPOINT,
            chosen.model,chosen.protocol,cloudNetwork);
    }
    private static String recognizeInternal(byte[] wav,String key,String endpoint,
                           String model,String protocol,Network cloudNetwork)throws Exception{
        if(wav==null||wav.length<44)throw new Exception("录音为空");
        if(wav.length>1024*1024)throw new Exception("录音过长");
        if(key==null||key.trim().isEmpty())throw new Exception("请在云端AI设置中配置语音识别 API Key");
        if(!CloudProviderPolicy.accepts(CloudProviderPolicy.ASR,protocol))
            throw new IllegalArgumentException("语音识别协议不支持");
        HttpURLConnection c=(HttpURLConnection)(cloudNetwork==null?
            new URL(endpoint).openConnection():cloudNetwork.openConnection(new URL(endpoint)));
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(10000);
        c.setReadTimeout(28000);
        c.setRequestMethod("POST");
        c.setRequestProperty("Authorization","Bearer "+key.trim());
        c.setDoOutput(true);
        try{
            if(CloudProviderPolicy.ASR_MULTIPART.equals(protocol))
                sendMultipart(c,wav,model);
            else sendChatAudio(c,wav,model,protocol);
            int status=c.getResponseCode();
            if(status<200||status>=300){
                String response=readSmall(c.getErrorStream());
                String code="";
                try{
                    JSONObject json=new JSONObject(response);
                    JSONObject error=json.optJSONObject("error");
                    code=error==null?json.optString("code"):error.optString("code");
                }catch(Exception ignored){}
                throw new Exception("云端语音转文字失败 HTTP "+status+" "+code);
            }
            JSONObject data=new JSONObject(readSmall(c.getInputStream()));
            String text;
            if(CloudProviderPolicy.ASR_MULTIPART.equals(protocol)){
                text=data.optString("text","").trim();
            }else{
                JSONArray choices=data.optJSONArray("choices");
                if(choices==null||choices.length()==0)throw new Exception("云端未返回语音内容");
                text=choices.getJSONObject(0).getJSONObject("message")
                    .optString("content","").trim();
            }
            if(text.isEmpty())throw new Exception("没有识别到清晰语音");
            return text;
        }finally{c.disconnect();}
    }
    private static void sendChatAudio(HttpURLConnection c,byte[] wav,
                          String model,String protocol)throws Exception{
        JSONObject options=new JSONObject();
        if(CloudProviderPolicy.ASR_QWEN.equals(protocol)){
            options.put("data","data:audio/wav;base64,"+
                Base64.encodeToString(wav,Base64.NO_WRAP));
        }else if(CloudProviderPolicy.ASR_CHAT_AUDIO.equals(protocol)){
            // OpenAI-compatible Chat audio content: raw base64 + explicit WAV.
            options.put("data",Base64.encodeToString(wav,Base64.NO_WRAP));
            options.put("format","wav");
        }else{
            throw new IllegalArgumentException("不支持的语音输入格式");
        }
        JSONObject input=new JSONObject().put("type","input_audio").put("input_audio",options);
        JSONObject message=new JSONObject().put("role","user")
            .put("content",new JSONArray().put(input));
        JSONObject body=new JSONObject().put("model",model)
            .put("messages",new JSONArray().put(message)).put("stream",false);
        if(CloudProviderPolicy.ASR_QWEN.equals(protocol))
            body.put("asr_options",new JSONObject().put("enable_itn",true));
        c.setRequestProperty("Content-Type","application/json");
        try(OutputStream out=c.getOutputStream()){
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private static void sendMultipart(HttpURLConnection c,byte[] wav,String model)
            throws Exception{
        // RFC 7578, no extra local WAV file: bound to the same user recording.
        String boundary="BlueStar"+UUID.randomUUID().toString().replace("-","");
        c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        try(OutputStream out=c.getOutputStream()){
            String fields="--"+boundary+"\r\n"+
                "Content-Disposition: form-data; name=\"model\"\r\n\r\n"+
                CloudProviderPolicy.cleanModel(model)+"\r\n"+
                "--"+boundary+"\r\n"+
                "Content-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\n"+
                "Content-Type: audio/wav\r\n\r\n";
            out.write(fields.getBytes(StandardCharsets.UTF_8));
            out.write(wav);
            out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        }
    }
    private static String readSmall(InputStream stream)throws Exception{
        if(stream==null)return "";
        StringBuilder s=new StringBuilder();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            String line;
            while((line=in.readLine())!=null){
                if(s.length()+line.length()>900000)throw new Exception("ASR响应太大");
                s.append(line).append('\n');
            }
        }
        return s.toString();
    }
}
