package com.vobich.bluestar.cloudprobe;

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

/**
 * Qwen3 ASR: direct short WAV -> Chinese transcript, no installed Android speech service.
 * Explicitly user-initiated; no audio files, API keys or transcript are logged.
 */
final class CloudAsrClient {
    static final String ENDPOINT="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private CloudAsrClient(){}
    static String recognize(byte[] wav,String key) throws Exception{
        if(wav==null||wav.length<44)throw new Exception("录音为空");
        if(wav.length>1024*1024)throw new Exception("录音过长");
        if(key==null||key.trim().isEmpty())throw new Exception("请在云端AI设置中配置百炼API Key");
        JSONObject audio=new JSONObject().put("type","input_audio").put("input_audio",
            new JSONObject().put("data","data:audio/wav;base64,"+
                Base64.encodeToString(wav,Base64.NO_WRAP)));
        JSONArray messages=new JSONArray().put(new JSONObject()
            .put("role","user").put("content",new JSONArray().put(audio)));
        JSONObject body=new JSONObject()
            .put("model","qwen3-asr-flash")
            .put("messages",messages).put("stream",false)
            .put("asr_options",new JSONObject().put("enable_itn",true));
        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(28000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization","Bearer "+key.trim());
        connection.setRequestProperty("Content-Type","application/json");
        connection.setDoOutput(true);
        try{
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
            int status=connection.getResponseCode();
            if(status<200||status>=300){
                String response=readSmall(connection.getErrorStream());
                String code="";
                try{
                    JSONObject json=new JSONObject(response);
                    JSONObject error=json.optJSONObject("error");
                    code=error==null?json.optString("code"):error.optString("code");
                }catch(Exception ignored){}
                throw new Exception("云端语音转文字失败 HTTP "+status+" "+code);
            }
            JSONObject result=new JSONObject(readSmall(connection.getInputStream()));
            JSONArray choices=result.optJSONArray("choices");
            if(choices==null||choices.length()==0)throw new Exception("云端未返回语音内容");
            String text=choices.getJSONObject(0).getJSONObject("message").optString("content","").trim();
            if(text.isEmpty())throw new Exception("没有识别到清晰语音");
            return text;
        }finally{connection.disconnect();}
    }
    private static String readSmall(InputStream stream)throws Exception{
        if(stream==null)return "";
        StringBuilder s=new StringBuilder();
        try(BufferedReader in=new BufferedReader(
                new InputStreamReader(stream,StandardCharsets.UTF_8))){
            String line;
            while((line=in.readLine())!=null){
                if(s.length()+line.length()>900000)throw new Exception("ASR响应太大");
                s.append(line).append('\n');
            }
        }
        return s.toString();
    }
}
