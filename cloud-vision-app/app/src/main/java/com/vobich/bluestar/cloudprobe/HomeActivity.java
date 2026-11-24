package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.app.Activity;
import android.net.Network;
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
    private android.widget.Switch listenSwitch;
    private boolean syncingListenSwitch;
    private android.content.SharedPreferences listenPrefs;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listenChanged=(p,k)->{
        if(C110PhysicalPolicy.ENABLE.equals(k))syncListenSwitch();
    };
    private C110DeviceControls homeLightControls;
    private Button lightButton;
    private boolean lightBusy;
    private Boolean lastLightCommand;
    private Network lightNetwork;
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
        homeVoice=new HomeVoiceAssistant(this,this::quickStatusChanged,this::prepareHomeVoiceSpeech,this::voiceOneShotPhoto);
        homeLightControls=new C110DeviceControls(this,this::quickStatusChanged);
        listenPrefs=getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
        listenPrefs.registerOnSharedPreferenceChangeListener(listenChanged);
        cameraStatus=new C110HomeStatus(this,
            ()->lightBusy||quickLaunchPending||(quickShot!=null&&quickShot.isShutterPending())
                ||C110ListenService.cameraIoBusy(),
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
        tile(second,5,"设置",this::openSettings);
        second.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));
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
        listenSwitch=new android.widget.Switch(this);
        listenSwitch.setText("监听");
        listenSwitch.setTextSize(14);
        listenSwitch.setTextColor(WHITE);
        listenSwitch.setSwitchPadding(dp(4));
        listenSwitch.setContentDescription("相机实体快门监听，开启后锁屏也继续朗读");
        listenSwitch.setChecked(listenPrefs.getBoolean(C110PhysicalPolicy.ENABLE,false));
        listenSwitch.setOnCheckedChangeListener((button,checked)->{
            if(syncingListenSwitch)return;
            listenPrefs.edit().putBoolean(C110PhysicalPolicy.ENABLE,checked).apply();
            if(checked)C110ListenService.ensureStarted(this);
            else C110ListenService.stopListening(this);
        });
        header.addView(listenSwitch,new LinearLayout.LayoutParams(-2,dp(44)));
        cameraBattery=text("",17,ACCENT);
        cameraBattery.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        cameraBattery.setPadding(dp(5),0,0,0);
        header.addView(cameraBattery,new LinearLayout.LayoutParams(-2,-2));
        card.addView(header,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout line=new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        cameraConnection=text("Wi-Fi 未连接",15,ACCENT);
        line.addView(cameraConnection,new LinearLayout.LayoutParams(0,-2,1f));
        Button connect=new Button(this);
        connect.setText("快速连接");
        connect.setAllCaps(false);
        connect.setTextSize(16);
        connect.setTextColor(WHITE);
        connect.setMinHeight(0);connect.setMinimumHeight(dp(48));
        connect.setMinWidth(0);connect.setMinimumWidth(0);
        connect.setPadding(dp(7),dp(4),dp(7),dp(4));
        connect.setBackground(bg(NAV_ACTIVE,12));
        connect.setContentDescription("快速连接运动相机 Wi-Fi");
        connect.setOnClickListener(v->connectCamera());
        line.addView(connect,new LinearLayout.LayoutParams(-2,-2));
        lightButton=new Button(this);
        lightButton.setAllCaps(false);
        lightButton.setTextSize(16);lightButton.setTextColor(WHITE);
        lightButton.setMinWidth(0);lightButton.setMinimumWidth(0);
        lightButton.setMinHeight(0);lightButton.setMinimumHeight(dp(48));
        lightButton.setPadding(dp(7),dp(4),dp(7),dp(4));
        lightButton.setBackground(bg(0xFF205582,12));
        Drawable bulb=new LightIcon();
        bulb.setBounds(0,0,dp(18),dp(18));
        lightButton.setCompoundDrawables(bulb,null,null,null);
        lightButton.setCompoundDrawablePadding(dp(4));
        lightButton.setOnClickListener(v->tapLight());
        LinearLayout.LayoutParams lightParams=new LinearLayout.LayoutParams(-2,-2);
        lightParams.leftMargin=dp(6);
        line.addView(lightButton,lightParams);
        updateLightButton();
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
        C110BatteryPolicy.Reading reading=status.connected?status.battery:C110BatteryPolicy.unknown();
        cameraBattery.setContentDescription("运动相机"+(status.connected&&status.reading&&!reading.available?
            "正在读取电量":reading.label));
        Network connected=C110WifiBootstrap.connected(this);
        if(!java.util.Objects.equals(lightNetwork,connected)){
            lightNetwork=connected;lastLightCommand=null;
        }
        updateLightButton();
        Drawable batteryIcon=new BatteryIcon(reading);
        batteryIcon.setBounds(0,0,dp(46),dp(25));
        cameraBattery.setCompoundDrawables(batteryIcon,null,null,null);
        cameraBattery.setCompoundDrawablePadding(0);
    }
    private void updateLightButton(){
        if(lightButton==null)return;
        String label=lightBusy?"处理中":lastLightCommand==null?"灯光":lastLightCommand?"关灯":"开灯";
        lightButton.setText(label);
        lightButton.setContentDescription(lightBusy?"相机灯光命令处理中":
            lastLightCommand==null?"相机指示灯，选择开启或关闭":
            label+"，上一次命令已获相机确认");
        lightButton.setEnabled(!lightBusy);
    }
    private void tapLight(){
        if(lightBusy||!homeResumed||homeLightControls==null)return;
        Network wifi=C110WifiBootstrap.connected(this);
        if(wifi==null){quickStatusChanged("请先连接运动相机 Wi-Fi");return;}
        if(!wifi.equals(lightNetwork)){lightNetwork=wifi;lastLightCommand=null;}
        if(quickLaunchPending||(quickShot!=null&&quickShot.isShutterPending())){
            quickStatusChanged("正在拍照，请稍后操作灯光");return;
        }
        // No status read-back is available. First use chooses an explicit action;
        // later taps reverse only the last acknowledged command on this connection.
        if(lastLightCommand==null){
            new BlueStarDialogChoices.PlatformBuilder(this).setTitle("相机指示灯")
                .setItems(new String[]{"开启灯光","关闭灯光"},(dialog,which)->applyLight(which==0))
                .setNegativeButton("取消",null).show();
        }else applyLight(!lastLightCommand);
    }
    private void applyLight(boolean enabled){
        if(lightBusy||!homeResumed||homeLightControls==null)return;
        final Network target=C110WifiBootstrap.connected(this);
        if(target==null){quickStatusChanged("请先连接运动相机 Wi-Fi");return;}
        if(quickLaunchPending||(quickShot!=null&&quickShot.isShutterPending())){
            quickStatusChanged("正在拍照，请稍后操作灯光");return;
        }
        C110ListenService.beforeManualAction();
        lightBusy=true;updateLightButton();
        homeLightControls.setLightFromHome(enabled,ok->{
            lightBusy=false;
            lastLightCommand=ok&&homeResumed&&target.equals(C110WifiBootstrap.connected(this))?enabled:null;
            lightNetwork=C110WifiBootstrap.connected(this);
            updateLightButton();
        });
    }
    private static final class LightIcon extends Drawable{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c){
            c.save();c.translate(getBounds().left,getBounds().top);
            c.scale(getBounds().width()/24f,getBounds().height()/24f);
            p.setColor(0xFFE7F4FF);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);
            Path bulb=new Path();
            bulb.moveTo(9,17);bulb.lineTo(9,14);
            bulb.cubicTo(2,7,7,3,12,3);bulb.cubicTo(17,3,22,7,15,14);
            bulb.lineTo(15,17);bulb.close();c.drawPath(bulb,p);
            c.drawLine(9,20,15,20,p);c.drawLine(11,22,13,22,p);
            c.restore();
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
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
    /** SJCAM X silhouette and bar count; only the bars have the requested color. */
    private static final class BatteryIcon extends Drawable{
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final C110BatteryPolicy.Reading reading;
        BatteryIcon(C110BatteryPolicy.Reading reading){this.reading=reading;}
        @Override public void draw(Canvas canvas){
            canvas.save();
            canvas.translate(getBounds().left,getBounds().top);
            canvas.scale(getBounds().width()/66f,getBounds().height()/36f);
            p.setStyle(Paint.Style.FILL);p.setColor(0xFF333333);
            canvas.drawRoundRect(new RectF(0,0,60,36),5,5,p);
            canvas.drawRoundRect(new RectF(58,12,66,24),2,2,p);
            if(!reading.available){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xFFB9C4D0);
                canvas.drawRoundRect(new RectF(1,1,59,35),5,5,p);
            }else if(reading.charging){
                p.setColor(0xFF55DDA6);
                Path bolt=new Path();bolt.moveTo(23,7);bolt.lineTo(45,20);
                bolt.lineTo(29,20);bolt.lineTo(32,29);bolt.lineTo(12,16);
                bolt.lineTo(27,16);bolt.close();canvas.drawPath(bolt,p);
            }else if(reading.bars==0){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xFFFF5252);
                canvas.drawRoundRect(new RectF(7,7,53,29),2,2,p);
            }else{
                p.setColor(reading.bars>=3?0xFF55DDA6:reading.bars==2?0xFFFFCB42:0xFFFF5252);
                for(int i=0;i<reading.bars;i++){
                    float x=13+i*9;
                    canvas.drawRoundRect(new RectF(x,10,x+6,26),3,3,p);
                }
            }
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
        C110ListenService.beforeManualAction();
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
        C110ListenService.beforeManualAction();
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
    private void syncListenSwitch(){
        if(listenSwitch==null||listenPrefs==null)return;
        syncingListenSwitch=true;
        listenSwitch.setChecked(listenPrefs.getBoolean(C110PhysicalPolicy.ENABLE,false));
        syncingListenSwitch=false;
    }
    boolean physicalManualBusy(){
        return lightBusy||quickLaunchPending||(quickShot!=null&&quickShot.isShutterPending());
    }
    boolean physicalVoiceBusy(){return homeVoice!=null&&homeVoice.busyForPhysical();}
    void yieldForPhysical(){
        if(homeVoice!=null)homeVoice.yieldSpeechToPhysicalPhoto();
        if(quickShot!=null){quickShot.cancelRecognitionOnly();quickShot.beforeOpenAnotherScreen();}
    }
    void physicalStatus(String message){if(homeResumed)quickStatusChanged(message);}
    private void prepareHomeVoiceSpeech(){
        C110ListenService.beforeVoiceAction();
        if(quickShot!=null){
            quickShot.cancelRecognitionOnly();
            quickShot.beforeOpenAnotherScreen();
        }
    }
    private void prepareOtherScreenSpeech(){
        C110ListenService.beforeManualAction();
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
        C110ListenService.attachHome(this);
        if(listenPrefs.getBoolean(C110PhysicalPolicy.ENABLE,false))C110ListenService.ensureStarted(this);
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
        lastLightCommand=null;lightNetwork=null;
        // The foreground listener owns its lifecycle; screen lock must not stop it.
        if(cameraStatus!=null)cameraStatus.stop();
        if(homeVoice!=null)homeVoice.onPause();
        if(quickShot!=null)quickShot.onPause();
        super.onPause();
    }
    @Override protected void onDestroy(){
        statusHandler.removeCallbacks(hideQuickStatus);
        C110ListenService.detachHome(this);
        if(listenPrefs!=null)listenPrefs.unregisterOnSharedPreferenceChangeListener(listenChanged);
        if(cameraStatus!=null){cameraStatus.shutdown();cameraStatus=null;}
        if(homeLightControls!=null){homeLightControls.shutdown();homeLightControls=null;}
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
