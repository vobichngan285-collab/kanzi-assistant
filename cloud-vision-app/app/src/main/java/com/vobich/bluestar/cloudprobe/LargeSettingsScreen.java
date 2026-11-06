package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every settings subsection is a full-height, large-text page, never a tiny system dialog. */
final class LargeSettingsScreen {
    private static final int BG=0xFF091B32, PANEL=0xFF183553, TEXT=0xFFF3F8FF, SUB=0xFFC3D7EF;
    private final MainActivity app;
    private final FrameLayout host,overlay;
    private LinearLayout content;
    private final ArrayList<Page> history=new ArrayList<>();
    private boolean closed;
    private static final class Page {
        final String name,param;
        Page(String n,String p){name=n;param=p;}
    }
    LargeSettingsScreen(MainActivity activity,FrameLayout root){
        app=activity;host=root;
        overlay=new FrameLayout(app);
        overlay.setBackgroundColor(BG);
        overlay.setClickable(true);overlay.setFocusable(true);
        host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        navigate("home","");
    }
    boolean isClosed(){return closed;}
    void close(){
        if(closed)return;
        closed=true;
        host.removeView(overlay);
        app.settingsPageClosed();
    }
    void back(){
        if(history.size()<=1){close();return;}
        history.remove(history.size()-1);
        Page p=history.get(history.size()-1);
        render(p);
    }
    private void navigate(String name,String param){
        history.add(new Page(name,param));
        render(history.get(history.size()-1));
    }
    private int dp(int x){return Math.round(x*app.getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String value,int sp,int color,boolean bold){
        TextView t=new TextView(app);
        t.setText(value);t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private void render(Page p){
        if(closed)return;
        overlay.removeAllViews();
        LinearLayout page=new LinearLayout(app);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12),dp(12),dp(12),dp(12));
        overlay.addView(page,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout bar=new LinearLayout(app);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹",0xFF265077);
        back.setTextSize(30);
        bar.addView(back,new LinearLayout.LayoutParams(dp(58),dp(62)));
        back.setContentDescription("返回上一级");
        back.setOnClickListener(v->back());
        String heading;
        switch(p.name){
            case "speech":heading="语音与朗读";break;
            case "engines":heading="选择语音引擎";break;
            case "tasks":heading="任务管理";break;
            case "preset":heading="编辑预设提示词";break;
            case "custom":heading="编辑自定义任务";break;
            case "camera":heading="摄像头设置";break;
            case "sources":heading="选择摄像头";break;
            case "feedback":heading="提示音与振动";break;
            case "cloud":heading="云端 AI";break;
            case "key":heading="API Key";break;
            case "result":heading="上次识别结果";break;
            default:heading="蓝星向导 · 设置";
        }
        TextView title=text(heading,25,TEXT,true);
        title.setPadding(dp(9),0,0,0);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(64),1f));
        page.addView(bar);
        ScrollView scroll=new ScrollView(app);
        scroll.setFillViewport(true);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        content=new LinearLayout(app);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4),dp(12),dp(4),dp(25));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        if(p.name.equals("home"))home();
        else if(p.name.equals("speech"))speech();
        else if(p.name.equals("engines"))engines();
        else if(p.name.equals("tasks"))tasks();
        else if(p.name.equals("preset"))editor(p.param,false);
        else if(p.name.equals("custom"))editor(p.param,true);
        else if(p.name.equals("camera"))camera();
        else if(p.name.equals("sources"))sources();
        else if(p.name.equals("feedback"))feedback();
        else if(p.name.equals("cloud"))cloud();
        else if(p.name.equals("key"))apiKey();
        else if(p.name.equals("result"))result();
    }
    private Button button(String value,int color){
        Button b=new Button(app);
        b.setAllCaps(false);
        b.setText(value);b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bg(color,15));
        b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(8),0,dp(8),0);
        return b;
    }
    private void row(String label,String description,Runnable clicked){
        LinearLayout block=new LinearLayout(app);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(18),dp(15),dp(18),dp(15));
        block.setBackground(bg(PANEL,15));
        TextView title=text(label+"  ›",22,TEXT,true);
        block.addView(title);
        if(description!=null&&!description.isEmpty()){
            TextView subtitle=text(description,17,SUB,false);
            subtitle.setPadding(0,dp(5),0,0);
            block.addView(subtitle);
        }
        block.setMinimumHeight(dp(78));
        block.setClickable(true);block.setFocusable(true);
        block.setOnClickListener(v->clicked.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(12);
        content.addView(block,lp);
    }
    private void heading(String value){
        TextView h=text(value,21,TEXT,true);
        h.setPadding(dp(8),dp(13),dp(8),dp(10));
        content.addView(h);
    }
    private void note(String value){
        TextView note=text(value,17,SUB,false);
        note.setPadding(dp(12),dp(4),dp(12),dp(17));
        content.addView(note);
    }
    private void action(String label,Runnable task){
        Button button=button(label,0xFF246AA6);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(66));
        lp.bottomMargin=dp(14);
        content.addView(button,lp);button.setOnClickListener(v->task.run());
    }
    private void toggle(String label,String desc,String key,boolean defaultValue){
        SharedPreferences prefs=app.getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
        LinearLayout line=new LinearLayout(app);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setBackground(bg(PANEL,14));
        line.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout labels=new LinearLayout(app);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title=text(label,21,TEXT,true);labels.addView(title);
        TextView hint=text(desc,16,SUB,false);labels.addView(hint);
        line.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        Switch control=new Switch(app);
        control.setChecked(prefs.getBoolean(key,defaultValue));
        line.addView(control);
        control.setOnCheckedChangeListener((v,isChecked)->
            prefs.edit().putBoolean(key,isChecked).apply());
        line.setOnClickListener(v->control.setChecked(!control.isChecked()));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(10);
        content.addView(line,lp);
    }
    private void home(){
        row("语音与朗读","本地语音、语速、过滤英文／数字／符号",()->navigate("speech",""));
        row("任务管理","修改五个预设提示词、新增自定义任务",()->navigate("tasks",""));
        row("摄像头设置","前后摄像头、USB、方向与镜像、缩放",()->navigate("camera",""));
        row("提示音与振动","金属咔嗒音、清脆点击振动",()->navigate("feedback",""));
        row("云端 AI","识别模型、图片清晰度、API Key",()->navigate("cloud",""));
        row("上次识别结果","查看完整内容与再次朗读",()->navigate("result",""));
        note("所有设置自动保存。关闭设置后，点击摄像头画面即可拍照识别。");
    }
    private void speech(){
        SharedPreferences prefs=app.getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
        row("选择语音引擎",app.currentSpeechEngineDescription(),()->navigate("engines",""));
        heading("朗读速度");
        float rate=prefs.getFloat("local_tts_rate",1f);
        TextView value=text(String.format(Locale.CHINA,"%.2f×",rate),23,TEXT,true);
        value.setGravity(Gravity.CENTER);
        content.addView(value);
        SeekBar slider=new SeekBar(app);
        slider.setMax(50);
        slider.setProgress(Math.round((rate-.5f)*20f));
        content.addView(slider,new LinearLayout.LayoutParams(-1,dp(67)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
                if(!user)return;
                float speed=.5f+.05f*progress;
                value.setText(String.format(Locale.CHINA,"%.2f×",speed));
                app.applySpeechSpeed(speed);
            }
            @Override public void onStartTrackingTouch(SeekBar v){}
            @Override public void onStopTrackingTouch(SeekBar v){}
        });
        note("0.50× 慢　　　　　　　　　3.00× 快");
        heading("朗读内容过滤");
        toggle("过滤英文","朗读时跳过英文单词和字母","speech_skip_latin",false);
        toggle("过滤数字","朗读时跳过阿拉伯数字","speech_skip_digits",false);
        toggle("过滤符号","跳过特殊符号，保留停顿","speech_skip_symbols",false);
        toggle("对所有任务生效","默认只过滤「朗读文字」任务","speech_filter_all_tasks",false);
        note("过滤只改变听到的内容，原始识别文字和 AI 追问仍保留全部信息。");
        action("▶ 试听当前语音",()->app.previewSpeech());
    }
    private void engines(){
        List<TextToSpeech.EngineInfo> engines=app.availableTtsEngines();
        row("跟随手机系统默认","自动使用手机当前已配置的语音引擎",()->{
            app.changeSpeechEngine("");back();
        });
        for(TextToSpeech.EngineInfo engine:engines){
            String id=engine.name,label=engine.label==null?id:engine.label;
            row(label,"选择此语音引擎",()->{
                app.changeSpeechEngine(id);back();
            });
        }
        if(engines.isEmpty())note("没有发现其他语音引擎，可检查手机系统的语音合成设置。");
    }
    private void tasks(){
        for(TaskCatalog.Task task:TaskCatalog.all(app)){
            String label=(task.custom?"★ ":"")+" "+task.name;
            row(label,task.custom?"自定义任务 · 可编辑名称、图标、提示词":
                "系统预设 · 可修改AI提示词、恢复默认",
                ()->navigate(task.custom?"custom":"preset",task.id));
        }
        action("＋ 新建自定义任务",()->navigate("custom",""));
    }
    private TaskCatalog.Task findTask(String id){
        for(TaskCatalog.Task t:TaskCatalog.all(app))if(t.id.equals(id))return t;
        return null;
    }
    private void editor(String id,boolean custom){
        TaskCatalog.Task task=findTask(id);
        if(!id.isEmpty()&&task==null){note("此任务已不存在");return;}
        if(!custom&&task==null){note("未找到预设任务");return;}
        TextView nameTitle=text("任务名称",20,TEXT,true);
        content.addView(nameTitle);
        EditText name=new EditText(app);
        name.setTextColor(TEXT);name.setTextSize(20);
        name.setText(task==null?"":task.name);
        name.setEnabled(custom);
        if(!custom)name.setBackgroundTintList(ColorStateList.valueOf(0xFF3B5977));
        content.addView(name,new LinearLayout.LayoutParams(-1,dp(65)));
        TextView promptTitle=text("AI 提示词",21,TEXT,true);
        promptTitle.setPadding(0,dp(17),0,dp(7));
        content.addView(promptTitle);
        EditText prompt=new EditText(app);
        prompt.setTextColor(TEXT);prompt.setHintTextColor(0xFF9AB9D5);
        prompt.setTextSize(TypedValue.COMPLEX_UNIT_SP,21);
        prompt.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        prompt.setGravity(Gravity.TOP);
        prompt.setMinLines(6);prompt.setMaxLines(12);
        prompt.setHint("输入你希望AI按照什么规则识别图片");
        prompt.setText(task==null?"":task.instruction);
        prompt.setBackground(bg(PANEL,12));
        prompt.setPadding(dp(14),dp(14),dp(14),dp(14));
        content.addView(prompt,new LinearLayout.LayoutParams(-1,-2));
        final String[] icon={task==null?"✦":task.icon};
        if(custom){
            heading("任务图标");
            String[] palette=TaskCatalog.iconPalette();
            Button iconButton=button("点击切换图标："+icon[0],PANEL);
            iconButton.setContentDescription("选择任务图标，当前"+icon[0]);
            LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(-1,dp(68));
            iconLp.bottomMargin=dp(13);content.addView(iconButton,iconLp);
            iconButton.setOnClickListener(v->{
                int current=0;for(int k=0;k<palette.length;k++)if(palette[k].equals(icon[0]))current=k;
                icon[0]=palette[(current+1)%palette.length];
                iconButton.setText("点击切换图标："+icon[0]);
                iconButton.setContentDescription("选择任务图标，当前"+icon[0]);
            });
        }
        note("只修改指令，不改变 OCR 与视觉 AI 的模型分工。");
        action("保存修改",()->{
            try{
                if(custom){
                    TaskCatalog.Task saved=TaskCatalog.upsert(app,
                        task==null?null:task.id,name.getText().toString(),
                        prompt.getText().toString(),icon[0]);
                    app.refreshSelectedTask(saved.id);
                }else{
                    TaskCatalog.updatePreset(app,task.id,prompt.getText().toString());
                    app.refreshSelectedTask(TaskCatalog.selectedId(app));
                }
                app.toastShort("任务已保存");
                back();
            }catch(IllegalArgumentException e){prompt.setError(e.getMessage());}
        });
        if(!custom)action("恢复系统默认提示词",()->{
            TaskCatalog.restorePreset(app,task.id);
            app.refreshSelectedTask(TaskCatalog.selectedId(app));
            app.toastShort("已恢复系统默认提示词");
            back();
        });
        else if(task!=null)action("删除自定义任务",()->{
            TaskCatalog.remove(app,task.id);
            app.refreshSelectedTask(TaskCatalog.selectedId(app));
            app.toastShort("已删除自定义任务");
            back();
        });
    }
    private void camera(){
        row("选择摄像头",app.sourceDescription(),()->navigate("sources",""));
        row("画面旋转90°","当前："+app.rotationDescription(),()->{app.adjustCamera(1);render(history.get(history.size()-1));});
        row("水平镜像",app.mirrorDescription(),()->{app.adjustCamera(2);render(history.get(history.size()-1));});
        row("垂直翻转",app.flipDescription(),()->{app.adjustCamera(3);render(history.get(history.size()-1));});
        row("USB 左右校正",app.usbCorrectionDescription(),()->{app.adjustCamera(4);render(history.get(history.size()-1));});
        row("缩放恢复1×","双指捏合和张开缩放画面",()->{app.adjustCamera(5);render(history.get(history.size()-1));});
        note("不同手机镜头和 USB 摄像头分别记住方向。相机预览与上传给AI的图片必须一致。");
    }
    private void sources(){
        row("USB 外接摄像头","保留原生UVC直接预览",()->{app.setCameraSourceFromSettings(0);back();});
        row("手机后置摄像头","原生 Camera2",()->{app.setCameraSourceFromSettings(1);back();});
        row("手机前置摄像头","原生 Camera2",()->{app.setCameraSourceFromSettings(2);back();});
    }
    private void feedback(){
        toggle("拍照金属提示音","短促、清亮的金属开盖咔嗒声","shutter_feedback_sound",true);
        toggle("拍照清脆振动","系统预设短点击效果，非持续嗡嗡声","shutter_feedback_vibration",true);
        action("▶ 试听声音与振动",()->app.previewCaptureFeedback());
        note("两项开关互相独立，点击拍照时尽量同步反馈。");
    }
    private void cloud(){
        row("视觉模型",app.visionModelDescription(),()->{
            app.toggleVisionModel();render(history.get(history.size()-1));
        });
        row("图片分辨率",app.imagePolicyDescription(),()->{
            app.cycleImagePolicy();render(history.get(history.size()-1));
        });
        row("API Key","本机加密保存，点击进入",()->navigate("key",""));
        note("视觉AI和语音识别会上传照片或语音到百炼服务器并可能消耗Token。使用前请在百炼确认用完即停。");
    }
    private void apiKey(){
        EditText key=new EditText(app);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
        key.setTextColor(Color.WHITE);
        key.setHint("粘贴自己的百炼 API Key");
        key.setHintTextColor(SUB);
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(key,new LinearLayout.LayoutParams(-1,dp(72)));
        note("密钥只加密保存在此手机的 Android Keystore 中，不会写进安装包。");
        action("加密保存 API Key",()->{
            try{
                CloudKeyStore.save(app,key.getText().toString());
                key.setText("");
                app.toastShort("密钥已保存");back();
            }catch(Exception error){app.toastShort("密钥保存失败："+error.getClass().getSimpleName());}
        });
        action("清除已保存密钥",()->{
            CloudKeyStore.clear(app);
            app.toastShort("密钥已清除");back();
        });
    }
    private void result(){
        String result=app.lastRecognizedText();
        if(result==null||result.isEmpty())note("尚无识别结果");
        else{
            TextView large=text(result,21,TEXT,false);
            large.setTextIsSelectable(true);
            large.setPadding(dp(13),dp(15),dp(13),dp(15));
            large.setBackground(bg(PANEL,12));
            content.addView(large);
            action("▶ 再次朗读",()->app.previewLastRecognizedText());
        }
    }
}
