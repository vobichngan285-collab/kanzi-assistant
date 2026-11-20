package com.vobich.bluestar.boyuebridge;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Tiny accessibility-friendly LAUNCHER-only bridge.
 *
 * The working Blue Star C110, OEM photo, OCR, key, and voice code is in :app,
 * not duplicated or called across application sandboxes.
 * No CAMERA/AUDIO/NETWORK/ACCESSIBILITY permissions are requested.
 *
 * The external Boyue reader launches THIS app as a foreground user gesture;
 * this Activity then opens the actual Home in explicit one-shot mode.
 */
public final class LaunchActivity extends Activity {
    private static final String TARGET_PACKAGE=
        "com.vobich.bluestar.c110plusquick159";
    private static final String TARGET_ACTIVITY=
        "com.vobich.bluestar.cloudprobe.HomeActivity";
    private static final String ACTION_QUICK_SHOT=
        "com.vobich.bluestar.cloudprobe.action.QUICK_SHOT";
    private boolean forwarded;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        TextView brief=new TextView(this);
        brief.setGravity(Gravity.CENTER);
        brief.setText("正在执行蓝星一键拍照…");
        brief.setContentDescription("正在启动蓝星向导拍照识别");
        brief.setTextSize(18);
        brief.setTextColor(0xFFF3F8FF);
        brief.setBackgroundColor(0xFF091B32);
        setContentView(brief);
        getWindow().setStatusBarColor(0xFF091B32);
        getWindow().setNavigationBarColor(0xFF091B32);
    }

    @Override protected void onResume(){
        super.onResume();
        // When resuming an already focused launcher, deliver exactly once.
        if(hasWindowFocus())
            getWindow().getDecorView().post(this::launchOnce);
    }

    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused)getWindow().getDecorView().post(this::launchOnce);
    }

    private void launchOnce(){
        if(forwarded||isFinishing()||!hasWindowFocus())return;
        forwarded=true;
        Intent shot=new Intent(ACTION_QUICK_SHOT);
        shot.setComponent(new ComponentName(TARGET_PACKAGE,TARGET_ACTIVITY));
        shot.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
                      Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{
            startActivity(shot);
        }catch(ActivityNotFoundException|SecurityException error){
            Toast.makeText(this,
                "请先安装蓝星向导 v1.0.59 主程序",Toast.LENGTH_LONG).show();
        }finally{
            finish();
        }
    }
}
