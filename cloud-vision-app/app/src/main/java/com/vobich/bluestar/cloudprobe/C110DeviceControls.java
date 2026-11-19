package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Independent C110 settings controller. Never touches RTSP, VLC or the photo pipeline.
 * Standard C110 identity must be read and verified before each LED/sound write.
 */
final class C110DeviceControls {
    private static final String IP="192.168.1.254";
    private static final String PREFS="c110_camera_controls";
    private static final String LIGHT_SECONDS="light_delay_seconds";
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Consumer<String> reporter;
    private volatile int sequence;
    private Network cameraWifi;
    private Runnable pendingOff;
    private boolean closed;

    C110DeviceControls(Activity activity,Consumer<String> reporter){
        this.activity=activity;
        this.reporter=reporter;
    }

    private SharedPreferences preferences(){
        return activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    int lightDelaySeconds(){
        return Math.max(-1,Math.min(600,preferences().getInt(LIGHT_SECONDS,30)));
    }

    private String describe(){
        int seconds=lightDelaySeconds();
        return seconds<0?"始终显示":seconds==0?"连接后立即熄灭":
            "连接后 "+seconds+" 秒自动熄灭";
    }

    void showSettings(){
        if(closed)return;
        new AlertDialog.Builder(activity).setTitle("C110 相机设置")
            .setItems(new String[]{
                "机身提示音：关闭",
                "机身提示音：恢复",
                "指示灯："+describe()+"（修改）",
                "指示灯：立即关闭",
                "指示灯：恢复显示",
                "说明：连接后开始计时，断开则取消"
            },(dialog,i)->{
                switch(i){
                    case 0: manualCommand(9011,0);break;
                    case 1: manualCommand(9011,1);break;
                    case 2: chooseLightDelay();break;
                    case 3: saveLightDelay(0);break;
                    case 4: saveLightDelay(-1);break;
                    default:break;
                }
            }).setNegativeButton("关闭",null).show();
    }

    private void chooseLightDelay(){
        int s=lightDelaySeconds();
        int selected=s==15?0:s==30?1:s==60?2:s==0?5:s<0?4:3;
        new AlertDialog.Builder(activity).setTitle("灯光自动熄灭时间")
            .setSingleChoiceItems(new String[]{
                "15 秒","30 秒（默认）","60 秒","自定义秒数",
                "始终显示","连接后立即关闭"
            },selected,(dialog,i)->{
                dialog.dismiss();
                switch(i){
                    case 0:saveLightDelay(15);break;
                    case 1:saveLightDelay(30);break;
                    case 2:saveLightDelay(60);break;
                    case 3:customLightDelay();break;
                    case 4:saveLightDelay(-1);break;
                    case 5:saveLightDelay(0);break;
                    default:break;
                }
            }).setNegativeButton("取消",null).show();
    }

    private void customLightDelay(){
        EditText input=new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setHint("5–600 秒");
        input.setText(String.valueOf(Math.max(15,lightDelaySeconds())));
        new AlertDialog.Builder(activity).setTitle("自定义亮灯时间（秒）")
            .setView(input)
            .setPositiveButton("保存",(d,i)->{
                try{
                    int seconds=Integer.parseInt(input.getText().toString().trim());
                    if(seconds<5||seconds>600)throw new NumberFormatException();
                    saveLightDelay(seconds);
                }catch(NumberFormatException ex){
                    report("请输入5至600秒的整数，原设置不变");
                }
            }).setNegativeButton("取消",null).show();
    }

    private void saveLightDelay(int seconds){
        preferences().edit().putInt(LIGHT_SECONDS,seconds).apply();
        report("指示灯策略："+describe());
        if(cameraWifi!=null)onCameraConnected(cameraWifi);
    }

    void onCameraConnected(Network wifi){
        if(closed||wifi==null)return;
        clearTimer();
        cameraWifi=wifi;
        final int ticket=sequence;
        final int seconds=lightDelaySeconds();
        worker.execute(()->{
            try{
                if(ticket!=sequence)return;
                verifyStandardCamera(wifi);
                if(ticket!=sequence)return;
                int setting=seconds==0?0:1;
                int status=responseStatus(request(wifi,9038,setting));
                if(ticket!=sequence)return;
                if(status!=0){report("9038回执="+status+"；未启用倒计时");return;}
                if(seconds<=0){
                    report(seconds==0?"指示灯熄灭命令已发送":"指示灯保持正常显示");
                    return;
                }
                main.post(()->{
                    if(ticket!=sequence||closed)return;
                    report("C110已连接，"+seconds+"秒后自动关闭指示灯");
                    pendingOff=()->{
                        if(ticket!=sequence||closed)return;
                        worker.execute(()->{
                            try{
                                if(ticket!=sequence)return;
                                int off=responseStatus(request(wifi,9038,0));
                                if(ticket==sequence)report("自动熄灯9038/0回执="+off);
                            }catch(Exception ex){
                                if(ticket==sequence)report("自动熄灯失败："+ex.getClass().getSimpleName());
                            }
                        });
                    };
                    main.postDelayed(pendingOff,seconds*1000L);
                });
            }catch(Exception ex){
                if(ticket==sequence)report("灯光设置未执行："+ex.getMessage());
            }
        });
    }

    private void manualCommand(int cmd,int par){
        Network wifi=cameraWifi;
        if(closed||wifi==null){
            report("请先成功连接C110实时预览");return;
        }
        final int ticket=sequence;
        worker.execute(()->{
            try{
                if(ticket!=sequence)return;
                verifyStandardCamera(wifi);
                if(ticket!=sequence)return;
                int status=responseStatus(request(wifi,cmd,par));
                if(ticket==sequence)report(cmd+"/par="+par+" Status="+status);
            }catch(Exception ex){
                if(ticket==sequence)report("机身设置失败："+ex.getMessage());
            }
        });
    }

    void onDisconnected(){
        clearTimer();
        cameraWifi=null;
    }

    private void clearTimer(){
        sequence++;
        if(pendingOff!=null){
            main.removeCallbacks(pendingOff);
            pendingOff=null;
        }
    }

    void shutdown(){
        closed=true;
        onDisconnected();
        worker.shutdownNow();
    }

    private void report(String s){
        main.post(()->{if(!closed)reporter.accept(s);});
    }

    private void verifyStandardCamera(Network wifi)throws Exception{
        String info=request(wifi,3012,-1);
        if(responseStatus(info)!=0)
            throw new IllegalStateException("3012标准C110身份状态异常");
        Matcher names=Pattern.compile("<String>([^<]{1,128})</String>",
            Pattern.CASE_INSENSITIVE).matcher(info);
        boolean standard=false;
        while(names.find()){
            String text=names.group(1).toUpperCase(Locale.ROOT);
            if(text.contains("C110_PRO")||text.contains("C110+")
                ||text.contains("C110 PLUS"))
                throw new IllegalStateException("不是标准C110，禁止写设置");
            if(text.contains("C110"))standard=true;
        }
        if(!standard)throw new IllegalStateException("无法确认标准C110，禁止写设置");
    }

    private int responseStatus(String reply){
        Matcher m=Pattern.compile("<Status>(-?\\d+)</Status>",
            Pattern.CASE_INSENSITIVE).matcher(reply);
        if(!m.find())return -4096;
        try{return Integer.parseInt(m.group(1));}
        catch(NumberFormatException ex){return -4096;}
    }

    private String request(Network wifi,int cmd,int par)throws Exception{
        if(!((cmd==3012&&par==-1)||
            ((cmd==9011||cmd==9038)&&(par==0||par==1))))
            throw new IllegalArgumentException("不允许的机身指令");
        String path="/?custom=1&cmd="+cmd+(par<0?"":"&par="+par);
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)wifi.openConnection(new URL("http",IP,80,path));
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(2800);
            conn.setReadTimeout(2800);
            if(conn.getResponseCode()!=200)
                throw new IllegalStateException("机身HTTP状态非200");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[1024];int n;
                while((n=in.read(buffer))!=-1){
                    if(bytes.size()+n>32768)throw new IllegalStateException("相机回执超长");
                    bytes.write(buffer,0,n);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        }finally{
            if(conn!=null)conn.disconnect();
        }
    }
}
