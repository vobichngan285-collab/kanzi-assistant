package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Small launcher: never constructs a phone/USB controller or an RTSP player. */
public final class HomeActivity extends Activity {
    private static final int BG=0xFF07090D,GOLD=0xFFF5C543,WHITE=0xFFF7F7F7;
    private FrameLayout root;
    private boolean settingsTab;
    private C110VolumeShortcut shortcut;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        shortcut=new C110VolumeShortcut(this,this::quickPhoto);
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
        TextView heading=text(settingsTab?"设置":"蓝星向导",27,WHITE);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        content.addView(heading,new LinearLayout.LayoutParams(-1,dp(74)));
        if(settingsTab){
            option(content,"🎛","快捷拍照按键",
                C110VolumeShortcut.LABELS[index(C110VolumeShortcut.selected(this))],
                this::chooseShortcut);
            option(content,"▤","文字朗读 OCR 模型",
                OcrModelCatalog.label(ocrModel()),this::chooseOcrModel);
            option(content,"✦","视觉任务模型",
                visionModel(),this::chooseVisionModel);
            option(content,"🔑","云端 API Key","配置或清除本机密钥",this::editApiKey);
            option(content,"⚙","更多设置","原有语音、任务、相机及诊断设置",()->{
                Intent intent=new Intent(this,MainActivity.class);
                intent.putExtra("bluestar_launch_settings",true);
                startActivity(intent);
            });
        }else{
            option(content,"▣","快捷拍照","C110+ 原厂高清快门 → OCR → 朗读",this::quickPhoto);
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
        }
        LinearLayout dock=new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(bg(0xFF191B1E,23));
        Button h=nav("⌂\n主页",!settingsTab,()->{settingsTab=false;render();});
        Button p=nav("◎\n预览",false,this::openPreview);
        Button settings=nav("⚙\n设置",settingsTab,()->{settingsTab=true;render();});
        for(Button button:new Button[]{h,p,settings})
            dock.addView(button,new LinearLayout.LayoutParams(0,dp(66),1f));
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,dp(70),Gravity.BOTTOM);
        params.setMargins(dp(12),0,dp(12),dp(6));
        root.addView(dock,params);
    }
    private Button nav(String name,boolean selected,Runnable action){
        Button button=new Button(this);
        button.setAllCaps(false);button.setText(name);button.setTextSize(16);
        button.setTextColor(selected?GOLD:WHITE);
        button.setBackground(bg(selected?0xFF333334:0xFF191B1E,20));
        button.setOnClickListener(v->action.run());return button;
    }
    private void option(LinearLayout parent,String symbol,String title,
                        String detail,Runnable action){
        LinearLayout item=new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10),dp(14),dp(8),dp(14));
        TextView sign=text(symbol,28,GOLD);
        sign.setGravity(Gravity.CENTER);
        item.addView(sign,new LinearLayout.LayoutParams(dp(45),dp(69)));
        LinearLayout labels=new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name=text(title,21,WHITE);
        name.setTypeface(null,android.graphics.Typeface.BOLD);
        labels.addView(name);
        TextView sub=text(detail,13,0xFFA8AFB8);
        labels.addView(sub);
        item.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        TextView arrow=text("›",30,WHITE);item.addView(arrow);
        parent.addView(item,new LinearLayout.LayoutParams(-1,dp(100)));
        item.setClickable(true);item.setFocusable(true);
        item.setContentDescription(title+"，"+detail);
        item.setOnClickListener(v->action.run());
        View line=new View(this);line.setBackgroundColor(0xFF343434);
        parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void quickPhoto(){
        startActivity(new Intent(this,C110QuickPhotoActivity.class));
    }
    private void openPreview(){
        Intent intent=new Intent(this,MainActivity.class);
        intent.putExtra("bluestar_launch_preview",true);
        startActivity(intent);
    }
    private int index(String value){
        for(int i=0;i<C110VolumeShortcut.VALUES.length;i++)
            if(C110VolumeShortcut.VALUES[i].equals(value))return i;
        return 0;
    }
    private void chooseShortcut(){
        String current=C110VolumeShortcut.selected(this);
        new AlertDialog.Builder(this).setTitle("仅控制 C110+ 高清拍照")
            .setSingleChoiceItems(C110VolumeShortcut.LABELS,index(current),(d,i)->{
                shortcut.cancel();
                C110VolumeShortcut.select(this,C110VolumeShortcut.VALUES[i]);
                d.dismiss();render();
            }).setNegativeButton("取消",null).show();
    }
    private String ocrModel(){
        return OcrModelCatalog.normalize(getSharedPreferences("kanzi_settings",MODE_PRIVATE)
            .getString("cloud_ocr_model_v1",OcrModelCatalog.DEFAULT));
    }
    private String visionModel(){
        return VisionModelCatalog.normalize(getSharedPreferences("kanzi_settings",MODE_PRIVATE)
            .getString("cloud_vision_model",VisionModelCatalog.DEFAULT));
    }
    private void chooseOcrModel(){
        new AlertDialog.Builder(this).setTitle("主页和 C110+ 共用 OCR 模型")
            .setSingleChoiceItems(OcrModelCatalog.LABELS,
                Math.max(0,OcrModelCatalog.indexOf(ocrModel())),(d,i)->{
                    getSharedPreferences("kanzi_settings",MODE_PRIVATE).edit()
                        .putString("cloud_ocr_model_v1",OcrModelCatalog.IDS[i]).apply();
                    d.dismiss();render();
                }).setNegativeButton("取消",null).show();
    }
    private void chooseVisionModel(){
        new AlertDialog.Builder(this).setTitle("视觉分析模型")
            .setSingleChoiceItems(VisionModelCatalog.LABELS,
                Math.max(0,VisionModelCatalog.indexOf(visionModel())),(d,i)->{
                    getSharedPreferences("kanzi_settings",MODE_PRIVATE).edit()
                        .putString("cloud_vision_model",VisionModelCatalog.IDS[i]).apply();
                    d.dismiss();render();
                }).setNegativeButton("取消",null).show();
    }
    private void editApiKey(){
        EditText edit=new EditText(this);
        edit.setSingleLine(true);edit.setHint("粘贴百炼 API Key");
        edit.setInputType(InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("云端密钥（只存手机）")
            .setView(edit).setPositiveButton("保存",(d,w)->{
                try{CloudKeyStore.save(this,edit.getText().toString());
                    Toast.makeText(this,"密钥已保存",Toast.LENGTH_SHORT).show();}
                catch(Exception ex){Toast.makeText(this,"保存失败："+ex.getMessage(),
                    Toast.LENGTH_LONG).show();}
            }).setNeutralButton("清除",(d,w)->CloudKeyStore.clear(this))
            .setNegativeButton("取消",null).show();
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
    @Override protected void onResume(){super.onResume();if(root!=null)render();}
    @Override protected void onPause(){if(shortcut!=null)shortcut.cancel();super.onPause();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(shortcut!=null&&shortcut.dispatch(event))return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){
        if(settingsTab){settingsTab=false;render();return;}
        super.onBackPressed();
    }
}
