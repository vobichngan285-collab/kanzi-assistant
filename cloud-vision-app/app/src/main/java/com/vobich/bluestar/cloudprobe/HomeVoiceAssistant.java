package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Home press-and-hold AI: one existing recorder, shared cloud credentials/model,
 * and the latest actual submitted camera image, with no photo stored on disk. */
final class HomeVoiceAssistant {
    static final int MICROPHONE_PERMISSION=4603;
    private final Activity activity;
    private final Consumer<String> status;
    private final Runnable releasePhotoSpeech;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final VoiceRecorder recorder=new VoiceRecorder();
    private final VoicePressState gesture=new VoicePressState();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<String> turns=new ArrayList<>();
    private TextToSpeech tts;
    private boolean ttsReady,active,working;
    private String pendingSpeech="",ttsPackage="";
    private float ttsRate=1f;
    private int generation,ttsGeneration;
    private byte[] conversationImage;

    HomeVoiceAssistant(Activity a,Consumer<String> onStatus,Runnable beforeTalk){
        activity=a;status=onStatus;releasePhotoSpeech=beforeTalk;
    }
    void onResume(){active=true;}
    void onPause(){
        active=false;
        generation++;
        recorder.cancel();
        gesture.interrupt();
        working=false;
        releaseSpeech();
    }
    void shutdown(){
        onPause();
        worker.shutdownNow();
        turns.clear();
        conversationImage=null;
    }

    boolean handleTouch(View button,MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                gesture.down(begin(button));
                return true;
            case MotionEvent.ACTION_MOVE:
                if(gesture.move(e.getY(),-button.getHeight()))
                    announce("松开取消录音");
                return true;
            case MotionEvent.ACTION_UP:
                VoicePressState.Decision decision=gesture.up();
                if(decision==VoicePressState.Decision.CANCEL){
                    recorder.cancel();announce("已取消录音");
                }else if(decision==VoicePressState.Decision.SEND){
                    recorder.finish();announce("正在识别语音…");
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                if(gesture.interrupt()==VoicePressState.Decision.CANCEL){
                    recorder.cancel();announce("已取消录音");
                }
                return true;
            default:return true;
        }
    }
    private boolean begin(View button){
        if(!active||working||recorder.isBusy()){
            announce("AI正在处理或录音，请稍后");return false;
        }
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            !=PackageManager.PERMISSION_GRANTED){
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                MICROPHONE_PERMISSION);
            announce("请允许麦克风权限，然后重新按住AI");return false;
        }
        releasePhotoSpeech.run();
        if(tts!=null)try{tts.stop();}catch(RuntimeException ignored){}
        final int ticket=++generation;
        boolean ok=recorder.begin(activity,new VoiceRecorder.Listener(){
            @Override public void onReady(String source){
                main.post(()->{if(active&&ticket==generation)announce("正在录音，松开发送");});
            }
            @Override public void onFinished(byte[] wav,int duration,int peak,int rms,String source){
                main.post(()->{
                    if(!active||ticket!=generation)return;
                    working=true;
                    announce("语音已采集，正在提问…");
                    playSendSound();
                    worker.execute(()->requestAI(ticket,wav));
                });
            }
            @Override public void onFailure(String message){
                main.post(()->{if(active&&ticket==generation)announce("录音失败："+message);});
            }
        });
        if(ok){
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            announce("按住说话，松开发送");
        }
        return ok;
    }
    private void requestAI(int ticket,byte[] wav){
        try{
            String key=CloudKeyStore.load(activity);
            if(key.isEmpty())throw new Exception("请先在设置中填写百炼API Key");
            RecentPhotoSession.Snapshot shot=RecentPhotoSession.latest();
            Network network=shot==null?null:shot.cloudNetwork;
            // A C110 hotspot is local-only. Do not send ASR/vision via camera Wi-Fi.
            if(network==null&&C110WifiBootstrap.connected(activity)!=null)
                network=C110WifiCamera.requireCellular(activity);
            String question=CloudAsrClient.recognize(wav,key,network);
            if(question.isEmpty())throw new Exception("没有识别到问题");
            main.post(()->{if(active&&ticket==generation)announce("已听到问题，正在回答…");});
            if(shot!=null&&shot.jpeg!=conversationImage){
                turns.clear();
                conversationImage=shot.jpeg;
            }
            StringBuilder prompt=new StringBuilder();
            if(shot!=null){
                if(shot.observed!=null&&!shot.observed.trim().isEmpty()){
                    prompt.append(FollowupPrompt.buildForImage(
                        shot.task,shot.observed,new ArrayList<>(turns),question));
                }else{
                    prompt.append("你是帮助用户识别图片的中文语音助手。请只根据这张已拍照片回答问题；")
                        .append("看不清就说看不清，禁止捏造文字、价格和物品。简短口语，不用Markdown。")
                        .append("用户问题：").append(question);
                    for(String t:turns)prompt.append("\n此前问答：").append(t);
                }
            }else prompt.append(FollowupPrompt.buildStandalone(
                new ArrayList<>(turns),question,false));
            SharedPreferences prefs=activity.getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
            String model=VisionModelCatalog.normalize(prefs.getString(
                "cloud_vision_model",VisionModelCatalog.DEFAULT));
            CloudVisionClient.Result answer;
            if(shot!=null){
                TaskCatalog.Task followup=new TaskCatalog.Task("home-photo-followup",
                    "上一张照片",prompt.toString(),TaskCatalog.VISION,true,"✦");
                answer=CloudVisionClient.recognize(
                    shot.jpeg,key,followup,model,null,false,network);
            }else{
                answer=CloudVisionClient.recognizeText(prompt.toString(),key,model,
                    null,false,network);
            }
            final String response=answer.text==null?"":answer.text.trim();
            main.post(()->{
                if(!active||ticket!=generation)return;
                working=false;
                if(shot!=null){
                    RecentPhotoSession.Snapshot current=RecentPhotoSession.latest();
                    if(current==null||current.jpeg!=shot.jpeg){
                        announce("已拍摄新照片，本次旧照片回答已取消");
                        return;
                    }
                }
                if(response.isEmpty()){announce("AI没有返回有效回答");return;}
                turns.add("问："+question+"，答："+response);
                while(turns.size()>3)turns.remove(0);
                announce("AI回答："+response);
                speak(response);
            });
        }catch(Exception error){
            final String reason=error.getMessage()==null?
                error.getClass().getSimpleName():error.getMessage();
            main.post(()->{
                if(!active||ticket!=generation)return;
                working=false;
                announce("AI对话失败："+reason);
                speak("AI对话失败，请检查网络或云端设置。");
            });
        }
    }
    private void announce(String text){
        if(active)status.accept(text);
    }
    private void playSendSound(){
        SharedPreferences prefs=activity.getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
        if(!prefs.getBoolean("sound_enabled",true))return;
        try{
            MediaPlayer player=MediaPlayer.create(activity,R.raw.voice_send_whoosh);
            if(player!=null){
                player.setOnCompletionListener(MediaPlayer::release);
                player.start();
            }
        }catch(RuntimeException ignored){}
    }
    private void speak(String text){
        SharedPreferences prefs=activity.getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
        String engine=prefs.getString("app_tts_engine_package","");
        if(engine==null)engine="";
        float rate=prefs.getBoolean("local_tts_follow_default",true)?1f:
            Math.max(.5f,Math.min(3f,prefs.getFloat("local_tts_rate",1f)));
        if(tts!=null&&engine.equals(ttsPackage)){
            ttsRate=rate;
            pendingSpeech=text;
            flushSpeech();
            return;
        }
        releaseSpeech();
        ttsPackage=engine;ttsRate=rate;pendingSpeech=text;
        final int ticket=++ttsGeneration;
        try{
            TextToSpeech.OnInitListener onReady=state->main.post(()->{
                if(!active||ticket!=ttsGeneration)return;
                ttsReady=state==TextToSpeech.SUCCESS&&tts!=null;
                if(!ttsReady){announce("当前语音引擎初始化失败，请在设置中试听");return;}
                try{tts.setLanguage(Locale.SIMPLIFIED_CHINESE);}catch(RuntimeException ignored){}
                flushSpeech();
            });
            tts=engine.isEmpty()?new TextToSpeech(activity,onReady):
                new TextToSpeech(activity,onReady,engine);
        }catch(RuntimeException ex){announce("语音引擎启动失败");}
    }
    private void flushSpeech(){
        if(!ttsReady||tts==null||pendingSpeech.isEmpty())return;
        try{
            tts.setSpeechRate(ttsRate);
            String text=pendingSpeech;pendingSpeech="";
            if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"home-ai-"+generation)
                !=TextToSpeech.SUCCESS)announce("语音引擎未接受回答");
        }catch(RuntimeException e){announce("语音播放失败，请在设置中试听");}
    }
    private void releaseSpeech(){
        ++ttsGeneration;
        ttsReady=false;pendingSpeech="";ttsPackage="";
        if(tts!=null){
            try{tts.stop();}catch(RuntimeException ignored){}
            try{tts.shutdown();}catch(RuntimeException ignored){}
            tts=null;
        }
    }
}
