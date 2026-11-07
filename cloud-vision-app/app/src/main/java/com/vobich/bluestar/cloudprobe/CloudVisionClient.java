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
        final String text, model, serverModel;
        final long networkMs, uploadMs, firstTextMs, firstSpeakableMs;
        final int promptTokens, completionTokens;
        Result(String text,String model,String serverModel,long networkMs,long uploadMs,
               long firstTextMs,long firstSpeakableMs,int promptTokens,int completionTokens){
            this.text=text;this.model=model;this.serverModel=serverModel;this.networkMs=networkMs;
            this.uploadMs=uploadMs;
            this.firstTextMs=firstTextMs;this.firstSpeakableMs=firstSpeakableMs;
            this.promptTokens=promptTokens;this.completionTokens=completionTokens;
        }
    }

    static Result recognize(byte[] jpeg,String key,TaskCatalog.Task task,
                            String visionModel,PartListener listener) throws Exception {
        return recognizeInternal(jpeg,key,task,visionModel,listener,false);
    }

    /** Same streaming/TTS and accounting path, but strictly NO JPEG/image_url. */
    static Result recognizeText(String textPrompt,String key,PartListener listener) throws Exception {
        if(textPrompt==null||textPrompt.trim().isEmpty())
            throw new IllegalArgumentException("文字智能分析缺少上下文");
        TaskCatalog.Task task=new TaskCatalog.Task(
            "text-followup","文字智能分析",textPrompt,TaskCatalog.VISION,true,"✦");
        // Use the user's confirmed inexpensive Flash, independently of visual
        // Max/Plus selection. Never auto-switch or retry a failed request.
        return recognizeInternal(null,key,task,VisionModelCatalog.FLASH_38,listener,true);
    }

    private static Result recognizeInternal(byte[] jpeg,String key,TaskCatalog.Task task,
                               String visionModel,PartListener listener,
                               boolean textOnly) throws Exception {
        if(key==null||key.trim().isEmpty())
            throw new IllegalArgumentException("请先在设置中配置API Key");
        boolean isOcr=TaskCatalog.OCR.equals(task.engine);
        boolean connectionTest="model-probe".equals(task.id);
        boolean streaming=!isOcr&&!connectionTest;
        // Respect the actual user-selected model: never silently spend Plus tokens.
        if(!isOcr&&VisionModelCatalog.indexOf(visionModel)<0)
            throw new IllegalArgumentException("所选视觉模型不支持，请重新选择");
        String model=isOcr?"qwen-vl-ocr":visionModel;
        boolean customPreset=!task.custom&&!task.instruction.equals(
            TaskCatalog.defaultInstruction(task.id));
        String instruction;
        if(textOnly){
            instruction=task.instruction;
        }else if(isOcr){
            instruction="只依据照片中真实可见的信息完成任务；不猜测模糊文字。任务："+task.instruction;
        }else if("menu".equals(task.id)){
            instruction="只根据照片可见信息完成用户当前任务："+task.instruction+
                "。不猜测看不清的信息，不使用Markdown格式。";
        }else if(task.custom||customPreset){
            instruction="只根据照片可见信息完成用户指令："+task.instruction+
                "。优先执行用户指令，不猜测看不清的内容，不使用Markdown格式。";
        }else{
            instruction="只根据照片中真实可见的信息完成任务："+task.instruction+
                "。用中文直接说最重要的信息，尽量控制在80字以内，最多3句。"+
                "不做冗长解释，不重复题目，不使用Markdown符号；文字和细节看不清就说明看不清。";
        }
        JSONArray content=new JSONArray()
            .put(new JSONObject().put("type","text").put("text",instruction));
        if(!textOnly){
            if(jpeg==null||jpeg.length==0)throw new IllegalArgumentException("没有可用的照片");
            JSONObject image=new JSONObject().put("type","image_url").put("image_url",
                new JSONObject().put("url","data:image/jpeg;base64,"+
                    Base64.encodeToString(jpeg,Base64.NO_WRAP)));
            content.put(image);
        }
        // The emergency Max may think briefly before answering. Keep a hard
        // ceiling on reasoning + response tokens, rather than its unbounded default.
        boolean emergency=VisionModelCatalog.isEmergencyMax(model);
        int maxOutput=isOcr?1200:(connectionTest?64:(emergency?2048:
            (textOnly?550:("menu".equals(task.id)?550:((task.custom||customPreset)?650:350)))));
        JSONObject body=new JSONObject().put("model",model).put("stream",streaming)
            .put("max_tokens",maxOutput).put("messages",new JSONArray().put(
                new JSONObject().put("role","user").put("content",content)));
        // Flash: responsive daily mode. Max: bounded reasoning for manual
        // emergency use; short connection probes skip reasoning to save tokens.
        // Verified Plus and OCR keep their original request behavior.
        if(!isOcr&&VisionModelCatalog.isBudgetFlash(model))
            body.put("enable_thinking",false);
        if(!isOcr&&emergency){
            body.put("enable_thinking",!connectionTest);
            if(!connectionTest)body.put("thinking_budget",512);
        }
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
            // Includes connection establishment and request-body upload, not model execution.
            long uploadMs=SystemClock.elapsedRealtime()-started;
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
                // Empty success is not a server/transport failure: notify via voice, no modal.
                JSONObject usage=response.optJSONObject("usage");
                int prompt=usage==null?-1:usage.optInt("prompt_tokens",-1);
                int completion=usage==null?-1:usage.optInt("completion_tokens",-1);
                String readable;
                if(isOcr){
                    try{readable=OcrResponseGate.requireReadable(answer);}
                    catch(OcrResponseGate.NoReadableTextException empty){
                        throw new OcrResponseGate.NoReadableTextException(
                            response.optString("model",""),prompt,completion);
                    }
                }else{
                    readable=answer==null?"":answer.trim();
                    if(readable.isEmpty())throw new Exception("模型连接测试没有返回文字");
                }
                return new Result(readable,model,response.optString("model",""),
                    SystemClock.elapsedRealtime()-started,
                    uploadMs,headersMs,-1,usage==null?-1:usage.optInt("prompt_tokens",-1),
                    usage==null?-1:usage.optInt("completion_tokens",-1));
            }

            StringBuilder all=new StringBuilder();
            SpeechChunker speechChunks=new SpeechChunker();
            long firstText=-1;
            String serverModel="";
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
                    if(serverModel.isEmpty())
                        serverModel=chunk.optString("model","");
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
            return new Result(answer,model,serverModel,SystemClock.elapsedRealtime()-started,
                uploadMs,firstText,firstSpeakable[0],promptTokens,completionTokens);
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
