package com.vobich.bluestar.cloudprobe;

import android.os.SystemClock;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Beijing DashScope-compatible single-image OCR / streaming vision (no key/image logging). */
final class CloudVisionClient {
    private static final String ENDPOINT="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    interface PartListener { void onSpeakablePart(String part); }
    static final class Result {
        final String text, model;
        final long networkMs, firstTextMs, firstSpeakableMs;
        final int promptTokens, completionTokens;
        Result(String text,String model,long networkMs,long firstTextMs,
               long firstSpeakableMs,int promptTokens,int completionTokens){
            this.text=text;this.model=model;this.networkMs=networkMs;
            this.firstTextMs=firstTextMs;this.firstSpeakableMs=firstSpeakableMs;
            this.promptTokens=promptTokens;this.completionTokens=completionTokens;
        }
    }

    static Result recognize(byte[] jpeg,String key,TaskCatalog.Task task,
                            String visionModel,PartListener listener) throws Exception {
        if(key==null||key.trim().isEmpty())
            throw new IllegalArgumentException("请先在设置中配置API Key");
        boolean isOcr=TaskCatalog.OCR.equals(task.engine);
        boolean streaming=!isOcr;
        String model=isOcr?"qwen-vl-ocr":
            ("qwen3-vl-flash".equals(visionModel)?"qwen3-vl-flash":"qwen3-vl-plus");
        JSONObject image=new JSONObject().put("type","image_url").put("image_url",
            new JSONObject().put("url","data:image/jpeg;base64,"+
                Base64.encodeToString(jpeg,Base64.NO_WRAP)));
        String instruction;
        if(isOcr){
            instruction="只依据照片中真实可见的信息完成任务；不猜测模糊文字。任务："+task.instruction;
        }else if("menu".equals(task.id)){
            instruction="只根据照片可见信息完成任务："+task.instruction+
                "。请直接列出清晰的菜名和价格；有明确标注的推荐才称推荐。禁止长篇说明和Markdown格式。";
        }else if(task.custom){
            instruction="只根据照片可见信息完成任务："+task.instruction+
                "。尽可能简洁直接，不猜测。不要Markdown格式。";
        }else{
            instruction="只根据照片中真实可见的信息完成任务："+task.instruction+
                "。用中文直接说最重要的信息，尽量控制在80字以内，最多3句。"+
                "不做冗长解释，不重复题目，不使用Markdown符号；文字和细节看不清就说明看不清。";
        }
        JSONArray content=new JSONArray()
            .put(new JSONObject().put("type","text").put("text",instruction))
            .put(image);
        int maxOutput=isOcr?1200:("menu".equals(task.id)?550:(task.custom?650:250));
        JSONObject body=new JSONObject().put("model",model).put("stream",streaming)
            .put("max_tokens",maxOutput).put("messages",new JSONArray().put(
                new JSONObject().put("role","user").put("content",content)));
        if(streaming)body.put("stream_options",new JSONObject().put("include_usage",true));

        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(45000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization","Bearer "+key.trim());
        connection.setRequestProperty("Content-Type","application/json");
        long started=SystemClock.elapsedRealtime();
        try{
            byte[] request=body.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=connection.getOutputStream()){out.write(request);}
            int status=connection.getResponseCode();
            long headersMs=SystemClock.elapsedRealtime()-started;
            if(status<200||status>=300){
                String error=readLimited(connection.getErrorStream());
                String reason="请求失败";
                try{
                    JSONObject detail=new JSONObject(error);
                    reason=detail.optString("code",detail.optString("message","云端请求失败"));
                    if(detail.has("error")){
                        JSONObject obj=detail.optJSONObject("error");
                        if(obj!=null)reason=obj.optString("code",obj.optString("message",reason));
                    }
                }catch(Exception ignored){}
                throw new Exception("云端HTTP "+status+"："+reason);
            }
            if(!streaming){
                JSONObject response=new JSONObject(readLimited(connection.getInputStream()));
                JSONArray choices=response.optJSONArray("choices");
                if(choices==null||choices.length()==0)throw new Exception("云端没有返回识别内容");
                String answer=choices.getJSONObject(0).getJSONObject("message").optString("content","");
                if(answer.trim().isEmpty())throw new Exception("没有识别到清晰内容");
                JSONObject usage=response.optJSONObject("usage");
                return new Result(answer.trim(),model,SystemClock.elapsedRealtime()-started,
                    headersMs,-1,usage==null?-1:usage.optInt("prompt_tokens",-1),
                    usage==null?-1:usage.optInt("completion_tokens",-1));
            }

            StringBuilder all=new StringBuilder();
            SpeechChunker speechChunks=new SpeechChunker();
            long firstText=-1;
            final long[] firstSpeakable={-1};
            java.util.function.Consumer<String> speakPart=part->{
                if(firstSpeakable[0]<0)
                    firstSpeakable[0]=SystemClock.elapsedRealtime()-started;
                if(listener!=null)listener.onSpeakablePart(part);
            };
            int promptTokens=-1,completionTokens=-1;
            try(BufferedReader reader=new BufferedReader(
                    new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))){
                String line;
                int charBudget=0;
                while((line=reader.readLine())!=null){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                    if(!line.startsWith("data:"))continue;
                    String payload=line.substring(5).trim();
                    if("[DONE]".equals(payload))break;
                    if(payload.isEmpty())continue;
                    JSONObject chunk=new JSONObject(payload);
                    JSONObject usage=chunk.optJSONObject("usage");
                    if(usage!=null){
                        promptTokens=usage.optInt("prompt_tokens",promptTokens);
                        completionTokens=usage.optInt("completion_tokens",completionTokens);
                    }
                    JSONArray choices=chunk.optJSONArray("choices");
                    if(choices==null||choices.length()==0)continue;
                    JSONObject delta=choices.getJSONObject(0).optJSONObject("delta");
                    String part=delta==null?"":delta.optString("content","");
                    if(part.isEmpty())continue;
                    if(firstText<0)firstText=SystemClock.elapsedRealtime()-started;
                    charBudget+=part.length();
                    if(charBudget>6000)throw new Exception("云端响应超过安全长度");
                    all.append(part);
                    speechChunks.append(part,speakPart);
                }
            }
            speechChunks.finish(speakPart);
            String answer=all.toString().trim();
            if(answer.isEmpty())throw new Exception("云端没有返回有效内容");
            return new Result(answer,model,SystemClock.elapsedRealtime()-started,
                firstText,firstSpeakable[0],promptTokens,completionTokens);
        }finally{connection.disconnect();}
    }

    private static String readLimited(InputStream stream) throws Exception {
        if(stream==null)return "";
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=stream){
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){
                if(bytes.size()+n>1024*1024)throw new Exception("云端响应过大");
                bytes.write(buf,0,n);
            }
        }
        return bytes.toString("UTF-8");
    }
}
