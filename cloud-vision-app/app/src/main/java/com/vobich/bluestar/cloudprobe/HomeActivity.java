package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Small launcher: never constructs a phone/USB controller or an RTSP player. */
public final class HomeActivity extends Activity {
    // Only the dedicated launcher entry requests an automatic one-shot.
    // Ordinary ACTION_MAIN launching Blue Star never fires the shutter.
    static final String ACTION_QUICK_SHOT=
        "com.vobich.bluestar.cloudprobe.action.QUICK_SHOT";
    private boolean quickLaunchPending;
    // Use the original Blue Star palette already present in LargeSettingsScreen
    // and MainActivity, not the yellow/black reference application's colors.
    private static final int BG=0xFF091B32,ACCENT=0xFFB9DCF8,WHITE=0xFFF3F8FF;
    private static final int NAV_BG=0xED091D36,NAV_NORMAL=0xFF2C547D,NAV_ACTIVE=0xFF176BAC;
    private FrameLayout root;
    private C110VolumeShortcut shortcut;
    private C110QuickShotController quickShot;
    private TextView quickStatusView;
    private String quickStatus="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        quickLaunchPending=isQuickShotIntent(getIntent());
        shortcut=new C110VolumeShortcut(this,this::quickPhoto);
        quickShot=new C110QuickShotController(this,this::quickStatusChanged);
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
        TextView heading=text("蓝星向导",27,WHITE);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        content.addView(heading,new LinearLayout.LayoutParams(-1,dp(74)));
            option(content,"▣","快捷拍照","C110+ 原厂高清快门 → OCR → 朗读",this::quickPhoto);
            quickStatusView=text(quickStatus,15,0xFFC3D7EF);
            quickStatusView.setPadding(dp(12),dp(5),dp(12),dp(8));
            quickStatusView.setVisibility(quickStatus.isEmpty()?View.GONE:View.VISIBLE);
            content.addView(quickStatusView,new LinearLayout.LayoutParams(-1,-2));
            option(content,"▶","C110+ 实时预览","保留 150ms 播放与竖屏裁切",()->
                startActivity(new Intent(this,C110CameraActivity.class)));
            option(content,"▤","朗读任务","选择当前 OCR 或 AI 任务",this::chooseTask);
            option(content,"✦","AI 视觉分析","切换到预览后执行视觉任务",()->{
                selectVisionTask();
                openPreview();
            });
            option(content,"▦","阅读模式","进入原来的书页阅读页面",()->{
                Intent intent=new Intent(this,MainActivity.class);
                intent.putExtra("bluestar_launch_preview",true);
                intent.putExtra("bluestar_launch_reading",true);
                startActivity(intent);
            });
        LinearLayout dock=new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(bg(NAV_BG,23));
        Button h=nav("⌂\n主页",true,()->render());
        Button p=nav("◎\n预览",false,this::openPreview);
        Button settings=nav("⚙\n设置",false,this::openSettings);
        for(Button button:new Button[]{h,p,settings})
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
    private void option(LinearLayout parent,String symbol,String title,
                        String detail,Runnable action){
        LinearLayout item=new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10),dp(14),dp(8),dp(14));
        TextView sign=text(symbol,28,ACCENT);
        sign.setGravity(Gravity.CENTER);
        item.addView(sign,new LinearLayout.LayoutParams(dp(45),dp(69)));
        LinearLayout labels=new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name=text(title,21,WHITE);
        name.setTypeface(null,android.graphics.Typeface.BOLD);
        labels.addView(name);
        TextView sub=text(detail,13,0xFFC3D7EF);
        labels.addView(sub);
        item.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        TextView arrow=text("›",30,WHITE);item.addView(arrow);
        parent.addView(item,new LinearLayout.LayoutParams(-1,dp(100)));
        item.setClickable(true);item.setFocusable(true);
        item.setContentDescription(title+"，"+detail);
        item.setOnClickListener(v->action.run());
        View line=new View(this);line.setBackgroundColor(0xFF265077);
        parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private static boolean isQuickShotIntent(Intent intent){
        return intent!=null&&ACTION_QUICK_SHOT.equals(intent.getAction());
    }
    private void consumeQuickLaunch(){
        if(!quickLaunchPending||quickShot==null||isFinishing())return;
        quickLaunchPending=false; // consume before any async Wi-Fi dialog/onResume
        Intent launched=getIntent();
        if(isQuickShotIntent(launched))launched.setAction(Intent.ACTION_MAIN);
        quickPhoto();
    }
    private void quickPhoto(){
        // No Activity, dialog, camera preview, or experiment screen: the
        // native photo + existing OCR/TTS starts on THIS homepage.
        if(quickShot!=null)quickShot.trigger();
    }
    private void quickStatusChanged(String message){
        quickStatus=message==null?"":message;
        if(quickStatusView!=null){
            quickStatusView.setText(quickStatus);
            quickStatusView.setVisibility(quickStatus.isEmpty()?View.GONE:View.VISIBLE);
        }
    }
    private void openPreview(){
        Intent intent=new Intent(this,MainActivity.class);
        intent.putExtra("bluestar_launch_preview",true);
        startActivity(intent);
    }
    private void openSettings(){
        // Both Home and Preview enter the exact SAME full BlueStarSettingsScreen.
        // No extra Home-only shortcuts/model/key page, no nested legacy menu.
        Intent intent=new Intent(this,MainActivity.class);
        intent.putExtra("bluestar_launch_settings",true);
        startActivity(intent);
    }
    private void chooseTask(){
        List<TaskCatalog.Task> tasks=TaskCatalog.all(this);
        String[] labels=new String[tasks.size()];
        for(int i=0;i<tasks.size();i++)labels[i]=tasks.get(i).name;
        final String current=TaskCatalog.selectedId(this);
        int selected=0;
        for(int i=0;i<tasks.size();i++)if(tasks.get(i).id.equals(current))selected=i;
        new AlertDialog.Builder(this).setTitle("朗读任务")
            .setSingleChoiceItems(labels,selected,(d,i)->{
                TaskCatalog.select(this,tasks.get(i).id);
                d.dismiss();
                Toast.makeText(this,"任务："+tasks.get(i).name,Toast.LENGTH_SHORT).show();
            }).setNegativeButton("取消",null).show();
    }
    private void selectVisionTask(){
        for(TaskCatalog.Task t:TaskCatalog.all(this))
            if(TaskCatalog.VISION.equals(t.engine)){
                TaskCatalog.select(this,t.id);break;
            }
    }
    @Override protected void onResume(){
        super.onResume();
        if(quickShot!=null)quickShot.onResume();
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
        if(quickShot!=null)quickShot.onPause();
        if(shortcut!=null)shortcut.cancel();
        super.onPause();
    }
    @Override protected void onDestroy(){
        if(quickShot!=null){quickShot.shutdown();quickShot=null;}
        super.onDestroy();
    }
    @Override public void onRequestPermissionsResult(int requestCode,
                                      String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(quickShot!=null)quickShot.onPermissionResult(requestCode,results);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(shortcut!=null&&shortcut.dispatch(event))return true;
        return super.dispatchKeyEvent(event);
    }
}
