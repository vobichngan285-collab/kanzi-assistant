package com.vobich.bluestar.c110eventprobe;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.graphics.Color;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public final class ProbeActivity extends Activity {
    private static final String HOST="192.168.1.254";
    private static final int PORT=3333, LIMIT_MS=120000, MAX_CHARS=131072;
    private volatile Socket socket;
    private volatile boolean stopped=true;
    private volatile int generation;
    private Thread worker;
    private Button start,stop,mark;
    private TextView log;
    private ScrollView scroll;
    private final StringBuilder records=new StringBuilder();
    private long began;
    private int marks;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(16),dp(16),dp(16));
        TextView title=new TextView(this);title.setText("C110 拍照通知实验 0.1.0");title.setTextSize(24);content.addView(title);
        TextView help=new TextView(this);help.setText("先关闭蓝星监听、自动删图及原厂 App，手动连接 C110 Wi-Fi。开始后先空等10秒，再按相机实体快门。每次实验最多2分钟，仅接收通知，不拍照、不读SD卡、不删图。相机异常立刻停止。");help.setTextSize(16);content.addView(help);
        start=button(content,"开始监听",this::begin);
        stop=button(content,"停止监听",()->stopSession("手动停止"));
        stop.setEnabled(false);
        mark=button(content,"标记一次实体拍照",()->append("人工标记 #"+(++marks)+"（不向相机发送命令）"));
        mark.setEnabled(false);
        button(content,"复制实验日志",()->{
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("C110 通知日志",records.toString()));
        });
        scroll=new ScrollView(this);log=new TextView(this);log.setTextSize(16);log.setTextIsSelectable(true);scroll.addView(log);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(content);
    }
    private Button button(LinearLayout p,String t,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(t);b.setTextSize(18);b.setOnClickListener(v->action.run());p.addView(b);return b;
    }
    private void append(String s){
        String line=(began==0?"":("+"+(SystemClock.elapsedRealtime()-began)+"ms ")) +s+"\n";
        if(records.length()+line.length()>32768)return;
        records.append(line);log.setText(records.toString());scroll.post(()->scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
    private Network cameraNetwork(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return null;
        Network match=null;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            LinkProperties lp=cm.getLinkProperties(n);
            if(caps==null||lp==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
            for(RouteInfo r:lp.getRoutes()){
                if(r.getGateway()!=null&&HOST.equals(r.getGateway().getHostAddress())){
                    if(match!=null&&!match.equals(n))return null;
                    match=n;
                }
            }
        }
        return match;
    }
    private void begin(){
        if(worker!=null)return;
        Network wifi=cameraNetwork();
        began=SystemClock.elapsedRealtime();marks=0;records.setLength(0);
        append("C110 通知实验 0.1.0 / TCP 3333 / 仅接收");
        if(wifi==null){append("未发现唯一匹配网关192.168.1.254的Wi-Fi；请在系统设置中连接相机后再试");return;}
        final int token=++generation;
        stopped=false;start.setEnabled(false);stop.setEnabled(true);mark.setEnabled(true);
        append("已识别相机网关；发起一次TCP连接");
        worker=new Thread(()->listen(wifi,token),"C110-event-only");worker.start();
    }
    private void message(int token,String text){runOnUiThread(()->{if(token==generation)append(text);});}
    private void listen(Network wifi,int token){
        long end=SystemClock.elapsedRealtime()+LIMIT_MS;
        String result="监听结束";
        int total=0;
        EventDecoder decoder=new EventDecoder(s->message(token,s));
        try(Socket s=wifi.getSocketFactory().createSocket()){
            socket=s;
            if(stopped||generation!=token)return;
            s.connect(new InetSocketAddress(HOST,PORT),3000);
            s.setSoTimeout(1000);
            message(token,"TCP 3333 已连接；请先静置10秒，再按实体快门");
            // Only read. No application payload, ping, HTTP, directory access, or reconnect.
            InputStreamReader input=new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8);
            char[] chunk=new char[1024];
            while(!stopped&&generation==token&&SystemClock.elapsedRealtime()<end){
                try{
                    int n=input.read(chunk);
                    if(n<0){result="相机关闭TCP连接";break;}
                    if(n==0)continue;
                    total+=n;
                    message(token,"接收字符数="+n+" 累计="+total);
                    if(total>MAX_CHARS){result="达到接收上限，停止";break;}
                    decoder.feed(chunk,n);
                }catch(SocketTimeoutException timeout){/* Local wait only, sends no payload. */}
            }
            if(SystemClock.elapsedRealtime()>=end)result="达到2分钟上限，自动结束";
            decoder.finish();
        }catch(IOException|RuntimeException e){
            if(!stopped)result="连接/读取异常："+e.getClass().getSimpleName();
        }finally{
            socket=null;
            final String summary=result+"；累计接收字符="+total;
            runOnUiThread(()->{
                worker=null;
                if(generation==token){stopped=true;append(summary);start.setEnabled(true);stop.setEnabled(false);mark.setEnabled(false);}
                else {start.setEnabled(true);stop.setEnabled(false);mark.setEnabled(false);}
            });
        }
    }
    private void stopSession(String why){
        if(stopped)return;
        stopped=true;++generation;append(why);
        stop.setEnabled(false);mark.setEnabled(false);
        Socket s=socket;
        if(s!=null)try{s.close();}catch(IOException ignored){}
        // Start stays disabled until prior socket thread has actually ended.
    }
    @Override protected void onPause(){stopSession("离开实验界面/锁屏，已停止");super.onPause();}
    @Override protected void onDestroy(){stopSession("退出实验");super.onDestroy();}
}
