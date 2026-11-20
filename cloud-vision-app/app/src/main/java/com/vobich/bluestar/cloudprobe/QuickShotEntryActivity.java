package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * Distinct LAUNCHER icon for accessibility "open app" pickers that enumerate
 * Android launcher activities. Reuses the real Home/one-shot pipeline.
 * NO camera/OCR/TTS logic lives here and there is no diagnostics page.
 *
 * If the screen reader only enumerates packages (not launcher activities),
 * use the tiny companion trigger app built from the same original repository.
 */
public final class QuickShotEntryActivity extends Activity {
    private boolean forwarded;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        TextView brief=new TextView(this);
        brief.setBackgroundColor(0xFF091B32);
        brief.setTextColor(0xFFF3F8FF);
        brief.setText("正在打开蓝星一键拍照…");
        brief.setTextSize(18);
        brief.setGravity(android.view.Gravity.CENTER);
        setContentView(brief);
    }
    @Override protected void onResume(){
        super.onResume();
        if(forwarded)return;
        // Dispatch only AFTER this entry has become the user-visible Activity.
        // This is a direct consequence of a user launcher/reader gesture, not a
        // background service or a new accessibility key interception service.
        getWindow().getDecorView().post(()->{
            if(forwarded||isFinishing())return;
            forwarded=true;
            Intent shot=new Intent(this,HomeActivity.class);
            shot.setAction(HomeActivity.ACTION_QUICK_SHOT);
            shot.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
                          Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(shot);
            finish();
        });
    }
}
