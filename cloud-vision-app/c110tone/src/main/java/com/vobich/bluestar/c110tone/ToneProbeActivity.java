package com.vobich.bluestar.c110tone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Isolated standard C110 manual setting experiment: validated 9011, OEM 9038/9036, proven 1001 shutter. */
public final class ToneProbeActivity extends Activity {
    private static final String IP="192.168.1.254";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final C110WifiCamera camera=new C110WifiCamera();
    private final StringBuilder events=new StringBuilder();
    private TextView status,log;
    private Button detect,mute,photo,restore,ledOff,ledOn,strobeOff,strobeOn;
    private boolean busy,known;
    private volatile int generation;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF112238);
        panel.setPadding(dp(16),dp(14),dp(16),dp(20));
        scroll.addView(panel);
        TextView title=text("C110 声音与灯光实验",23,Color.WHITE);
        panel.addView(title);
        TextView instruction=text(
            "已复制蓝星向导真机成功的 C110 高清拍照流程。\n"+
            "9011 静音已通过真机验证；9038 指示灯及 9036 爆闪灯只来自标准 C110 原厂设置配置，效果未验证。\n"+
            "每个按钮单独执行一次；关闭后可按对应恢复按钮。\n"+
            "先连接 C110 Wi-Fi，测试拍照还需相机插入可用存储卡。\n"+
            "不会自动更改设置、拍照、上传云端、删除文件或改动播放器。",15,0xFFCBDDE9);
        instruction.setPadding(0,dp(12),0,dp(14));
        panel.addView(instruction);
        status=text("检测中…",17,0xFFBFE9F5);
        panel.addView(status);
        detect=button("重新检测 C110",0xFF2C537A);
        mute=button("① 关闭机身提示音（9011 / 0）",0xFF176A7A);
        photo=button("② 拍一张高清照片，测试声音",0xFF19678C);
        restore=button("③ 恢复机身提示音（9011 / 1）",0xFF575A7C);
        ledOff=button("④ 关闭状态指示灯（9038 / 0）",0xFF176A7A);
        ledOn=button("⑤ 恢复状态指示灯（9038 / 1）",0xFF575A7C);
        strobeOff=button("⑥ 关闭爆闪灯（9036 / 0）",0xFF176A7A);
        strobeOn=button("⑦ 恢复爆闪灯（9036 / 1）",0xFF575A7C);
        for(Button b:new Button[]{detect,mute,photo,restore,ledOff,ledOn,strobeOff,strobeOn})panel.addView(b,buttonParams());
        detect.setOnClickListener(v->probe());
        mute.setOnClickListener(v->askToggle(0));
        photo.setOnClickListener(v->askPhoto());
        restore.setOnClickListener(v->askToggle(1));
        ledOff.setOnClickListener(v->askLight(9038,0));
        ledOn.setOnClickListener(v->askLight(9038,1));
        strobeOff.setOnClickListener(v->askLight(9036,0));
        strobeOn.setOnClickListener(v->askLight(9036,1));
        Button copy=button("复制诊断",0xFF344B63);
        panel.addView(copy,buttonParams());
        copy.setOnClickListener(v->{
            ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clip!=null)clip.setPrimaryClip(
                ClipData.newPlainText("C110 Tone 9011 Experiment",events.toString()));
            addLog("诊断已复制");
        });
        Button exit=button("退出实验工具",0xFF344B63);
        panel.addView(exit,buttonParams());
        exit.setOnClickListener(v->finish());
        log=text("",14,0xFFE9E9EE);
        log.setPadding(0,dp(12),0,0);
        panel.addView(log);
        setContentView(scroll);
        updateButtons();
        probe(); // Startup only reads 3012. No automatic photo or 9011 write.
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int sp,int color){
        TextView v=new TextView(this);
        v.setText(value);v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        return v;
    }
    private Button button(String value,int color){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,16);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        return b;
    }
    private LinearLayout.LayoutParams buttonParams(){
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(62));
        params.topMargin=dp(10);
        return params;
    }
    private void updateButtons(){
        detect.setEnabled(!busy);
        mute.setEnabled(known&&!busy);
        photo.setEnabled(known&&!busy);
        restore.setEnabled(known&&!busy);
        ledOff.setEnabled(known&&!busy);
        ledOn.setEnabled(known&&!busy);
        strobeOff.setEnabled(known&&!busy);
        strobeOn.setEnabled(known&&!busy);
    }
    private void addLog(String event){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            ui.post(()->addLog(event));return;
        }
        events.append(event).append('\n');
        if(events.length()>3800)events.delete(0,events.length()-3000);
        if(log!=null)log.setText(events.toString());
    }
    private Network checkC110()throws Exception{
        Network wifi=C110WifiCamera.previewNetwork(this);
        String response=call(wifi,3012,-1);
        if(!Integer.valueOf(0).equals(C110PhotoPolicy.tagInt(response,"Status")))
            throw new IllegalStateException("相机3012身份状态错误");
        Matcher m=Pattern.compile("<String>([^<]{0,128})</String>",Pattern.CASE_INSENSITIVE)
            .matcher(response);
        boolean standard=false;
        while(m.find()){
            String v=m.group(1).toUpperCase(Locale.ROOT);
            if(v.contains("C110_PRO")||v.contains("C110+")||v.contains("C110 PLUS"))
                throw new IllegalStateException("不对 C110 Pro/Plus 运行此实验");
            if(v.contains("C110"))standard=true;
        }
        if(!standard)throw new IllegalStateException("3012未确认标准C110，不发送任何设置");
        return wifi;
    }
    private void probe(){
        if(busy)return;
        final int ticket=++generation;
        busy=true;known=false;updateButtons();
        status.setText("检查相机 Wi-Fi 与型号…");
        executor.execute(()->{
            try{
                checkC110();
                ui.post(()->{
                    if(ticket!=generation||isFinishing())return;
                    busy=false;known=true;updateButtons();
                    status.setText("标准 C110 已连接，可以实验");
                    addLog("3012：真实标准C110身份及状态已验证");
                    addLog("注意：9011回执成功也不等于拍照快门已静音");
                });
            }catch(Exception e){error(ticket,"连接检查失败",e);}
        });
    }
    private void askToggle(int setting){
        if(!known||busy)return;
        String choice=setting==0?"关闭":"开启";
        new AlertDialog.Builder(this).setTitle("确认"+choice+"机身按键声音？")
            .setMessage("只向C110发送一次 9011/par="+setting+"。\n"+
                "该指令来自原厂部分C110设置配置，可能被本机固件忽略。"+
                "设置可能保存在相机里，可用另一按钮恢复。\n"+
                "不会拍照、关机、删除或升级相机。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认发送",(dialog,which)->toggle(setting)).show();
    }
    private void toggle(int setting){
        if(!known||busy)return;
        final int ticket=++generation;
        busy=true;updateButtons();
        status.setText("发送机身音量设置…");
        executor.execute(()->{
            try{
                Network wifi=checkC110(); // Recheck before any write.
                String response=call(wifi,9011,setting);
                final Integer code=C110PhotoPolicy.tagInt(response,"Status");
                ui.post(()->{
                    if(ticket!=generation||isFinishing())return;
                    busy=false;known=true;updateButtons();
                    status.setText("9011/par="+setting+" 回复 "+(code==null?"无Status":code));
                    addLog("按键声命令9011/par="+setting+" 状态="+(code==null?"未返回":code)+
                        "；是否真正静音请按拍照按钮检验");
                });
            }catch(Exception e){error(ticket,"按键声音设置失败",e);}
        });
    }
    /** Manual OEM configuration test only. C110 675-C110$1.0.1/.4/.7 lists 9038 indicator lights and 9036 strobe. */
    private void askLight(int cmd,int setting){
        if(!known||busy)return;
        if(cmd!=9038&&cmd!=9036)return;
        if(setting!=0&&setting!=1)return;
        String target=cmd==9038?"状态指示灯":"爆闪灯";
        String choice=setting==0?"关闭":"开启";
        new AlertDialog.Builder(this).setTitle("确认"+choice+target+"？")
            .setMessage("仅向已确认的标准 C110 发送一次 "+cmd+"/par="+setting+"。\n"+
                "9038 和 9036 均来自原厂 C110 设置文件，但本机实际效果尚未验证。\n"+
                "两个灯光设置互不联动，不一定覆盖红色充电灯或所有指示状态。\n"+
                "操作后请观察灯光，用对应恢复按钮复原。不会改变 Wi-Fi 开关、拍照模式或播放器。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认发送",(dialog,which)->toggleLight(cmd,setting)).show();
    }
    private void toggleLight(int cmd,int setting){
        if(!known||busy||(cmd!=9038&&cmd!=9036)||(setting!=0&&setting!=1))return;
        final int ticket=++generation;
        busy=true;updateButtons();
        status.setText("发送灯光设置，等待相机回复…");
        executor.execute(()->{
            try{
                Network wifi=checkC110(); // Revalidate standard model before each change.
                String response=call(wifi,cmd,setting);
                final Integer code=C110PhotoPolicy.tagInt(response,"Status");
                ui.post(()->{
                    if(ticket!=generation||isFinishing())return;
                    busy=false;known=true;updateButtons();
                    String target=cmd==9038?"指示灯":"爆闪灯";
                    status.setText(target+" "+cmd+"/par="+setting+" 状态="+(code==null?"无Status":code));
                    addLog(target+"命令 "+cmd+"/par="+setting+" HTTP200 状态="+
                        (code==null?"未返回":code)+"；仅代表指令回复，真实灯光须肉眼观察");
                });
            }catch(Exception e){error(ticket,"灯光设置失败",e);}
        });
    }

    private void askPhoto(){
        if(!known||busy)return;
        new AlertDialog.Builder(this).setTitle("确认执行一次真实高清拍照？")
            .setMessage("使用蓝星向导成功的C110相机流程：3012/3002检查 → "+
                "3001/par=0照片模式 → 1001快门一次 → 从相机读取新JPEG。\n"+
                "不会反复发送快门。照片保存在C110的SD卡；下载的数据只在内存中。")
            .setNegativeButton("取消",null)
            .setPositiveButton("拍照",(dialog,which)->takeOnePhoto()).show();
    }
    private void takeOnePhoto(){
        if(!known||busy)return;
        final int ticket=++generation;
        busy=true;updateButtons();
        status.setText("正在执行C110真实快门拍照…");
        addLog("开始一次高清拍照：真实1001快门，禁止自动重复");
        executor.execute(()->{
            try{
                checkC110();
                C110WifiCamera.Photo image=camera.shoot(this);
                final int w=image.width,h=image.height,size=image.jpeg.length;
                final long elapsed=image.totalMs;
                ui.post(()->{
                    if(ticket!=generation||isFinishing())return;
                    busy=false;known=true;updateButtons();
                    status.setText("C110高清拍照成功："+w+"×"+h);
                    addLog("真实新JPEG="+w+"×"+h+" 字节="+size+
                        " 总耗时="+elapsed+"ms；不上传云端/不保存手机");
                });
            }catch(Exception e){error(ticket,"高清拍照失败",e);}
        });
    }
    private void error(int ticket,String what,Exception e){
        ui.post(()->{
            if(ticket!=generation||isFinishing())return;
            busy=false;known=false;updateButtons();
            status.setText(what+"，请重新检测");
            addLog(what+"："+e.getClass().getSimpleName()+" "+
                (e.getMessage()==null?"":e.getMessage()));
        });
    }
    private static String call(Network network,int cmd,int par)throws Exception{
        if(!((cmd==3012&&par==-1)||((cmd==9011||cmd==9038||cmd==9036)&&(par==0||par==1))))
            throw new IllegalArgumentException("仅允许只读身份、已验静音和原厂C110灯光开关");
        String path="/?custom=1&cmd="+cmd+(par<0?"":"&par="+par);
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)network.openConnection(new URL("http",IP,80,path));
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            if(conn.getResponseCode()!=200)
                throw new IllegalStateException("相机HTTP状态错误");
            try(InputStream in=conn.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[2048];int len;
                while((len=in.read(buf))!=-1){
                    if(out.size()+len>32768)throw new IllegalStateException("相机回复过大");
                    out.write(buf,0,len);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }finally{if(conn!=null)conn.disconnect();}
    }
    @Override protected void onDestroy(){
        generation++;
        executor.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
