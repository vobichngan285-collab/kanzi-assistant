package com.vobich.bluestar.cloudprobe;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Single-shot, Beijing-region DashScope compatible vision call; no logging of images or keys. */
final class CloudVisionClient {
    private static final String ENDPOINT="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    static String recognize(byte[] jpeg,String key,TaskCatalog.Task task) throws Exception {
        if(key==null||key.trim().isEmpty())throw new IllegalArgumentException("请先在设置中配置API Key");
        String model=TaskCatalog.OCR.equals(task.engine)?"qwen-vl-ocr":"qwen3-vl-plus";
        JSONObject image=new JSONObject().put("type","image_url").put("image_url",
            new JSONObject().put("url","data:image/jpeg;base64,"+
                Base64.encodeToString(jpeg,Base64.NO_WRAP)));
        JSONObject prompt=new JSONObject().put("type","text").put("text",
            "只依据照片中真实可见的信息完成任务；不猜测模糊文字。任务："+task.instruction);
        JSONArray content=new JSONArray().put(prompt).put(image);
        JSONObject body=new JSONObject().put("model",model).put("stream",false)
            .put("max_tokens",1200).put("messages",new JSONArray().put(
                new JSONObject().put("role","user").put("content",content)));
        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(45000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization","Bearer "+key.trim());
        connection.setRequestProperty("Content-Type","application/json");
        try {
            byte[] request=body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try(OutputStream out=connection.getOutputStream()){out.write(request);}
            int status=connection.getResponseCode();
            InputStream stream=status>=200&&status<300?
                connection.getInputStream():connection.getErrorStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(stream!=null)try(InputStream in=stream){
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1){
                    if(bytes.size()+n>1024*1024)throw new Exception("云端响应过大");
                    bytes.write(buf,0,n);
                }
            }
            JSONObject response=new JSONObject(bytes.toString("UTF-8"));
            if(status<200||status>=300){
                String error=response.optString("code",response.optString("error","请求失败"));
                throw new Exception("云端HTTP "+status+"："+error);
            }
            JSONArray choices=response.optJSONArray("choices");
            if(choices==null||choices.length()==0)throw new Exception("云端没有返回识别内容");
            Object result=choices.getJSONObject(0).getJSONObject("message").opt("content");
            String answer=result instanceof String?(String)result:String.valueOf(result);
            if(answer==null||answer.trim().isEmpty())throw new Exception("没有识别到清晰内容");
            return answer.trim();
        }finally{connection.disconnect();}
    }
}
