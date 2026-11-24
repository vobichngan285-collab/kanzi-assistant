package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Small launcher: never constructs a phone/USB controller or an RTSP player. */
public final class HomeActivity extends Activity {
    // Only the dedicated launcher entry requests an automatic one-shot.
    // Ordinary ACTION_MAIN launching Blue Star never fires the shutter.
    static final String ACTION_QUICK_SHOT=
        "com.vobich.bluestar.cloudprobe.action.QUICK_SHOT";
    private boolean quickLaunchPending,homeResumed;
    // Use the original Blue Star palette already present in LargeSettingsScreen
    // and MainActivity, not the yellow/black reference application's colors.
    private static final int BG=0xFF091B32,ACCENT=0xFFB9DCF8,WHITE=0xFFF3F8FF;
    private static final int NAV_BG=0xED091D36,NAV_NORMAL=0xFF2C547D,NAV_ACTIVE=0xFF176BAC;
    private FrameLayout root;
    private C110QuickShotController quickShot;
    private C110WifiBootstrap cameraWifi;
    private HomeVoiceAssistant homeVoice;
    private TextView quickStatusView,cameraConnection,cameraBattery;
    private C110HomeStatus cameraStatus;
    private final Handler statusHandler=new Handler(Looper.getMainLooper());
    private final Runnable hideQuickStatus=()->{
        quickStatus="";
        if(quickStatusView!=null)quickStatusView.setVisibility(View.GONE);
    };
    private String quickStatus="";

    @Override protected void attachBaseContext(Context base){
        super.attachBaseContext(BlueStarFontScale.wrap(base));
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        quickLaunchPending=isQuickShotIntent(getIntent());
        // The ONE network lease belongs to Home, not transient Settings/VLC.
        cameraWifi=new C110WifiBootstrap(this,this::quickStatusChanged);
        quickShot=new C110QuickShotController(this,this::quickStatusChanged);
        homeVoice=new HomeVoiceAssistant(this,this::quickStatusChanged,this::prepareOtherScreenSpeech,this::voiceOneShotPhoto);
        cameraStatus=new C110HomeStatus(this,
            ()->quickLaunchPending||(quickShot!=null&&quickShot.isShutterPending()),
            this::showCameraStatus);
        root=new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();
            view.setPadding(0,top,0,bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        render();
    }
    private int dp(int px){return Math.round(px*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable x=new GradientDrawable();
        x.setColor(color);x.setCornerRadius(dp(radius));return x;
    }
    private TextView text(String value,int size,int color){
        TextView x=new TextView(this);
        x.setText(value);x.setTextColor(color);x.setTextSize(size);return x;
    }
    private void render(){
        root.removeAllViews();
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(16),dp(18),dp(12));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        FrameLayout.LayoutParams s=new FrameLayout.LayoutParams(-1,-1);
        s.bottomMargin=dp(76);
        root.addView(scroll,s);
        // No title bar, banners or experimental features. The camera card
        // is the only status area; the six original actions are a 3 x 2 grid.
        addCameraCard(content);
        LinearLayout first=tileRow(content);
        tile(first,0,"快捷拍照",this::quickPhoto);
        tile(first,1,"实时预览",()->{
            prepareOtherScreenSpeech();
            startActivity(new Intent(this,C110CameraActivity.class));
        });
        tile(first,2,"朗读任务",this::chooseTask);
        LinearLayout second=tileRow(content);
        tile(second,3,"阅读模式",this::openReading);
        tile(second,4,"连接相机",this::connectCamera);
        tile(second,5,"设置",this::openSettings);
        if(cameraStatus!=null)showCameraStatus(cameraStatus.state());
        LinearLayout dock=new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(bg(NAV_BG,23));
        Button h=nav("⌂\n主页",true,()->render());
        Button p=nav("◎\n预览",false,this::openPreview);
        Button talk=nav("🎙\n按住AI",false,()->
            Toast.makeText(this,"请按住AI说话，松开发送",Toast.LENGTH_SHORT).show());
        talk.setContentDescription("按住AI对话，松开发送，针对最近一次手机或C110拍摄的照片回答问题");
        talk.setOnTouchListener((v,event)->homeVoice.handleTouch(v,event));
        for(Button button:new Button[]{h,p,talk})
            dock.addView(button,new LinearLayout.LayoutParams(0,dp(66),1f));
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,dp(70),Gravity.BOTTOM);
        params.setMargins(dp(12),0,dp(12),dp(6));
        root.addView(dock,params);
    }
    private Button nav(String name,boolean selected,Runnable action){
        Button button=new Button(this);
        button.setAllCaps(false);button.setText(name);button.setTextSize(16);
        button.setTextColor(WHITE);
        button.setBackground(bg(selected?NAV_ACTIVE:NAV_NORMAL,20));
        button.setOnClickListener(v->action.run());return button;
    }
    private void addCameraCard(LinearLayout content){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(10));
        card.setBackground(bg(0xFF142E49,16));
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);
        cardParams.bottomMargin=dp(26);
        content.addView(card,cardParams);
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("C110 运动相机",20,WHITE);
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        cameraBattery=text("电量未知",17,ACCENT);
        cameraBattery.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        cameraBattery.setPadding(dp(5),0,0,0);
        header.addView(cameraBattery,new LinearLayout.LayoutParams(-2,-2));
        card.addView(header,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout line=new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        cameraConnection=text("Wi-Fi 未连接",16,ACCENT);
        line.addView(cameraConnection,new LinearLayout.LayoutParams(0,-2,1f));
        Button connect=new Button(this);
        connect.setText("快速连接");
        connect.setAllCaps(false);
        connect.setTextSize(16);
        connect.setTextColor(WHITE);
        connect.setMinHeight(0);connect.setMinimumHeight(dp(48));
        connect.setPadding(dp(12),dp(4),dp(12),dp(4));
        connect.setBackground(bg(NAV_ACTIVE,12));
        connect.setContentDescription("快速连接运动相机 Wi-Fi");
        connect.setOnClickListener(v->connectCamera());
        line.addView(connect,new LinearLayout.LayoutParams(-2,-2));
        card.addView(line,new LinearLayout.LayoutParams(-1,-2));
        quickStatusView=text(quickStatus,15,ACCENT);
        quickStatusView.setVisibility(quickStatus.isEmpty()?View.GONE:View.VISIBLE);
        card.addView(quickStatusView,new LinearLayout.LayoutParams(-1,-2));
    }
    private void showCameraStatus(C110HomeStatus.State status){
        if(cameraConnection==null||cameraBattery==null)return;
        String connection=status.connected?"Wi-Fi 已连接":"Wi-Fi 未连接";
        if(!connection.contentEquals(cameraConnection.getText())){
            cameraConnection.setText(connection);
            cameraConnection.setTextColor(status.connected?0xFF55DDA6:ACCENT);
        }
        String battery=status.connected?
            (status.reading&&!status.battery.available?"读取电量…":status.battery.label):"电量未知";
        if(!battery.contentEquals(cameraBattery.getText()))cameraBattery.setText(battery);
        String raw=status.battery.raw;
        cameraBattery.setContentDescription("运动相机"+battery+
            (status.connected&&!raw.isEmpty()?"，设备返回值 "+raw:""));
        int batteryColor=ACCENT;
        if(status.connected&&status.battery.available){
            batteryColor=("2".equals(raw)||"3".equals(raw)||"4".equals(raw))?
                0xFFFFC278:0xFF55DDA6;
        }
        cameraBattery.setTextColor(batteryColor);
        Drawable batteryIcon=new BatteryIcon(batteryColor);
        batteryIcon.setBounds(0,0,dp(21),dp(14));
        cameraBattery.setCompoundDrawables(batteryIcon,null,null,null);
        cameraBattery.setCompoundDrawablePadding(dp(5));
    }
    private void connectCamera(){
        if(cameraWifi==null)return;
        if(!C110WifiBootstrap.requestFromCameraPage(this,false))
            cameraWifi.requestForCameraMode();
        if(cameraStatus!=null)cameraStatus.refresh();
    }
    private LinearLayout tileRow(LinearLayout content){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(16);
        content.addView(row,params);
        return row;
    }
    private void tile(LinearLayout row,int icon,String title,Runnable action){
        // One real native Button per tile. Its label is a single accessible
        // click target, not a separately focusable decorative icon.
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(19);
        button.setTextColor(WHITE);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);button.setMinimumWidth(0);
        button.setMinHeight(0);button.setMinimumHeight(dp(104));
        button.setPadding(dp(3),dp(9),dp(3),dp(10));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3359B5FF),
            bg(Color.TRANSPARENT,12),bg(Color.WHITE,12)));
        Drawable glyph=new TileIcon(icon);
        glyph.setBounds(0,0,dp(50),dp(50));
        button.setCompoundDrawables(null,glyph,null,null);
        button.setCompoundDrawablePadding(dp(8));
        button.setContentDescription(title);
        button.setOnClickListener(v->action.run());
        row.addView(button,new LinearLayout.LayoutParams(0,-2,1f));
    }
    /** Symbol only; do not invent a fill percentage for the protocol's enum. */
    private static final class BatteryIcon extends Drawable{
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final int color;
        BatteryIcon(int color){this.color=color;}
        @Override public void draw(Canvas canvas){
            canvas.save();
            canvas.translate(getBounds().left,getBounds().top);
            canvas.scale(getBounds().width()/21f,getBounds().height()/14f);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setColor(color);
            canvas.drawRoundRect(new RectF(1,2,17,12),2,2,p);
            p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(new RectF(18,5,20,9),1,1,p);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    /** Flat 50dp launcher icon, independent of emoji/fonts and text size. */
    private static final class TileIcon extends Drawable{
        final int kind;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        TileIcon(int kind){this.kind=kind;}
        @Override public void draw(Canvas c){
            c.save();c.translate(getBounds().left,getBounds().top);
            c.scale(getBounds().width()/50f,getBounds().height()/50f);
            p.setStyle(Paint.Style.FILL);p.setColor(0xFF205582);
            c.drawRoundRect(new RectF(0,0,50,50),12,12,p);
            p.setColor(0xFFE7F4FF);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.6f);p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            Path path=new Path();
            switch(kind){
                case 0:
                    path.moveTo(12,19);path.lineTo(19,19);path.lineTo(21,15);
                    path.lineTo(29,15);path.lineTo(31,19);path.lineTo(38,19);
                    path.lineTo(38,35);path.lineTo(12,35);path.close();
                    c.drawPath(path,p);c.drawCircle(25,26.5f,5.5f,p);break;
                case 1:
                    c.drawRoundRect(new RectF(11,14,39,36),4,4,p);
                    path.moveTo(22,19);path.lineTo(31,25);path.lineTo(22,31);
                    path.close();c.drawPath(path,p);break;
                case 2:
                    for(int y=16;y<=34;y+=9){
                        c.drawCircle(15,y,1,p);c.drawLine(21,y,36,y,p);
                    }break;
                case 3:
                    path.moveTo(25,18);path.cubicTo(19,13,13,15,11,16);
                    path.lineTo(11,34);path.cubicTo(17,31,21,32,25,36);
                    path.cubicTo(29,32,33,31,39,34);path.lineTo(39,16);
                    path.cubicTo(33,14,29,15,25,18);path.close();
                    c.drawPath(path,p);c.drawLine(25,18,25,36,p);break;
                case 4:
                    c.drawArc(new RectF(8,14,42,48),-139,98,false,p);
                    c.drawArc(new RectF(15,21,35,41),-139,98,false,p);
                    c.drawArc(new RectF(21,27,29,35),-139,98,false,p);
                    p.setStyle(Paint.Style.FILL);c.drawCircle(25,35,2,p);break;
                default:
                    for(int i=0;i<8;i++){
                        double angle=i*Math.PI/4;
                        float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
                        c.drawLine(25+dx*12,25+dy*12,25+dx*16,25+dy*16,p);
                    }
                    c.drawCircle(25,25,12,p);c.drawCircle(25,25,5,p);break;
            }
            c.restore();
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static boolean isQuickShotIntent(Intent intent){
        return intent!=null&&ACTION_QUICK_SHOT.equals(intent.getAction());
    }
    private void consumeQuickLaunch(){
        if(!quickLaunchPending||!homeResumed||quickShot==null||isFinishing())return;
        quickLaunchPending=false; // consume before any async Wi-Fi dialog/onResume
        Intent launched=getIntent();
        if(isQuickShotIntent(launched))launched.setAction(Intent.ACTION_MAIN);
        // Preserve explicitly requested legacy external one-shot shortcut.
        if(quickShot!=null)quickShot.trigger();
    }
    private void quickPhoto(){
        // Only a user tap opens the dedicated two-source Quick Photo panel.
        // No camera opens or shutter fires until its source button is tapped.
        prepareOtherScreenSpeech();
        startActivity(new Intent(this,QuickPhotoActivity.class));
    }
    /** Explicit voice read/observe changes only the current C110 shot. */
    private void voiceOneShotPhoto(TaskCatalog.Task oneShot){
        if(quickShot!=null)quickShot.trigger(oneShot);
    }
    private void quickStatusChanged(String message){
        quickStatus=message==null?"":message;
        statusHandler.removeCallbacks(hideQuickStatus);
        if(!quickStatus.isEmpty())statusHandler.postDelayed(hideQuickStatus,8000L);
        if(quickStatusView!=null){
            quickStatusView.setText(quickStatus);
            quickStatusView.setVisibility(quickStatus.isEmpty()?View.GONE:View.VISIBLE);
        }
    }
    private void prepareOtherScreenSpeech(){
        if(quickShot!=null)quickShot.beforeOpenAnotherScreen();
    }
    private void openPreview(){
        prepareOtherScreenSpeech();
        Intent intent=new Intent(this,MainActivity.class);
        intent.putExtra("bluestar_launch_preview",true);
        startActivity(intent);
    }
    private void openSettings(){
        prepareOtherScreenSpeech();
        // Both Home and Preview enter the exact SAME full BlueStarSettingsScreen.
        // No extra Home-only shortcuts/model/key page, no nested legacy menu.
        Intent intent=new Intent(this,MainActivity.class);
        intent.putExtra("bluestar_launch_settings",true);
        startActivity(intent);
    }
    private void openReading(){
        prepareOtherScreenSpeech();
        Intent intent=new Intent(this,MainActivity.class);
        intent.putExtra("bluestar_launch_preview",true);
        intent.putExtra("bluestar_launch_reading",true);
        startActivity(intent);
    }
    private void chooseTask(){
        // Open a REAL opaque screen instead of stacking a dimmed task dialog
        // on top of the Home menu. No unnecessary reading/settings shortcut.
        prepareOtherScreenSpeech();
        startActivity(new Intent(this,TaskDesktopActivity.class));
    }
    @Override protected void onResume(){
        super.onResume();
        if(quickShot!=null)quickShot.onResume();
        homeResumed=true;
        if(homeVoice!=null)homeVoice.onResume();
        if(cameraWifi!=null)cameraWifi.startAtLaunch();
        if(cameraStatus!=null)cameraStatus.start();
        if(root!=null){
            render();
            // Wait until Home is resumed and the real user-requested camera
            // session can start; exactly once per explicit quick-launch intent.
            if(quickLaunchPending)root.post(this::consumeQuickLaunch);
        }
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(isQuickShotIntent(intent)){
            quickLaunchPending=true;
            if(root!=null&&hasWindowFocus())root.post(this::consumeQuickLaunch);
        }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus&&quickLaunchPending&&root!=null)
            root.post(this::consumeQuickLaunch);
    }
    @Override protected void onPause(){
        homeResumed=false;
        if(cameraStatus!=null)cameraStatus.stop();
        if(homeVoice!=null)homeVoice.onPause();
        if(quickShot!=null)quickShot.onPause();
        super.onPause();
    }
    @Override protected void onDestroy(){
        statusHandler.removeCallbacks(hideQuickStatus);
        if(cameraStatus!=null){cameraStatus.shutdown();cameraStatus=null;}
        if(homeVoice!=null){homeVoice.shutdown();homeVoice=null;}
        if(quickShot!=null){quickShot.shutdown();quickShot=null;}
        if(cameraWifi!=null){cameraWifi.shutdown();cameraWifi=null;}
        super.onDestroy();
    }
    @Override public void onRequestPermissionsResult(int requestCode,
                                      String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(quickShot!=null)quickShot.onPermissionResult(requestCode,results);
        if(requestCode==HomeVoiceAssistant.MICROPHONE_PERMISSION)
            Toast.makeText(this,"请再次按住AI按钮说话",Toast.LENGTH_SHORT).show();
    }
}
