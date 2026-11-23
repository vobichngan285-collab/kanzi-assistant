package com.vobich.bluestar.cloudprobe;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
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
import java.util.LinkedHashSet;
import java.util.Locale;

/** Canonical single Blue Star settings page shared by Home and live preview.
 *  All legacy voice/camera/USB/cloud/tasks/diagnostic controls are kept here;
 *  HomeActivity no longer implements a second partial settings UI.
 */
final class BlueStarSettingsScreen {
    private static final int BG=0xFF091B32, PANEL=0xFF183553, TEXT=0xFFF3F8FF, SUB=0xFFC3D7EF;
    private final MainActivity app;
    private final FrameLayout host,overlay;
    private LinearLayout content;
    private ScrollView currentScroll;
    private boolean builtinModelsExpanded;
    private TextView c110LiveStatus;
    private final ArrayList<Page> history=new ArrayList<>();
    private boolean closed;
    private static final class Page {
        final String name,param;
        Page(String n,String p){name=n;param=p;}
    }
    BlueStarSettingsScreen(MainActivity activity,FrameLayout root){
        app=activity;host=root;
        overlay=new FrameLayout(app);
        overlay.setBackgroundColor(BG);
        overlay.setClickable(true);overlay.setFocusable(true);
        host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        navigate("home","");
    }
    boolean isClosed(){return closed;}
    void updateC110Status(String message){
        if(closed||c110LiveStatus==null||history.isEmpty()||
            !"c110-camera".equals(history.get(history.size()-1).name))return;
        c110LiveStatus.setText(message==null||message.isEmpty()?
            app.currentC110WifiSettingsStatus():message);
    }
    void openApiKey(){navigate("cloud","");navigate("key","");}
    void openCloudRole(String role){
        if(!CloudProviderPolicy.validRole(role))return;
        navigate("cloud","");navigate("provider-role",role);
    }
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
        if("server-manage".equals(name))builtinModelsExpanded=false;
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
        c110LiveStatus=null;
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
            case "camera":heading="相机";break;
            case "c110-camera":heading="C110 运动相机";break;
            case "usb-picture":heading="USB 画质调节";break;
            case "sources":heading="选择摄像头";break;
            case "advanced":heading="高级与诊断";break;
            case "cloud":heading="云端 AI";break;
            case "provider-role":heading="独立服务选择";break;
            case "provider-edit":heading="编辑云端接口";break;
            case "server-manage":heading="云服务器管理";break;
            case "server-actions":heading="模型管理";break;
            case "model-prompt":heading="模型专属OCR提示词";break;
            case "ocr-speed":heading="文字朗读方式";break;
            case "usage":heading="模型用量统计";break;
            case "usage-clear":heading="清空用量记录";break;
            case "key":heading="API Key";break;
            case "result":heading="上次识别结果";break;
            case "diagnostics":heading="运行诊断";break;
            default:heading="蓝星向导 · 设置";
        }
        TextView title=text(heading,25,TEXT,true);
        title.setPadding(dp(9),0,0,0);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(64),1f));
        page.addView(bar);
        ScrollView scroll=new ScrollView(app);
        currentScroll=scroll;
        scroll.setFillViewport(true);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        content=new LinearLayout(app);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4),dp(12),dp(4),dp(25));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        // The same three blue navigation actions as Home/Preview, not the
        // former settings-only window with no way back to app navigation.
        LinearLayout dock=new LinearLayout(app);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(bg(0xED091D36,22));
        for(String label:new String[]{"⌂\n主页","◎\n预览","⚙\n设置"}){
            final int index=dock.getChildCount();
            Button tab=button(label,index==2?0xFF176BAC:0xFF2C547D);
            tab.setTextSize(16);
            tab.setContentDescription(index==0?"返回主页":
                index==1?"返回或打开摄像头预览":"当前蓝星统一设置");
            tab.setOnClickListener(v->{
                if(index==0)app.openHomeFromUnifiedSettings();
                else if(index==1)app.openPreviewFromUnifiedSettings();
                else if(!"home".equals(p.name)){history.clear();navigate("home","");}
            });
            dock.addView(tab,new LinearLayout.LayoutParams(0,dp(66),1f));
        }
        LinearLayout.LayoutParams dockParams=new LinearLayout.LayoutParams(-1,dp(69));
        dockParams.topMargin=dp(3);
        page.addView(dock,dockParams);
        if(p.name.equals("home"))home();
        else if(p.name.equals("speech"))speech();
        else if(p.name.equals("engines"))engines();
        else if(p.name.equals("tasks"))tasks();
        else if(p.name.equals("preset"))editor(p.param,false);
        else if(p.name.equals("custom"))editor(p.param,true);
        else if(p.name.equals("camera"))camera();
        else if(p.name.equals("c110-camera"))c110Camera();
        else if(p.name.equals("usb-picture"))usbPicture();
        else if(p.name.equals("sources"))sources();
        else if(p.name.equals("advanced"))advanced();
        else if(p.name.equals("cloud"))cloud();
        else if(p.name.equals("provider-role"))providerRole(p.param);
        else if(p.name.equals("provider-edit"))providerEdit(p.param);
        else if(p.name.equals("server-manage"))cloudServers();
        else if(p.name.equals("server-actions"))serverActions(p.param);
        else if(p.name.equals("model-prompt"))modelPrompt(p.param);
        else if(p.name.equals("ocr-speed"))ocrSpeed();
        else if(p.name.equals("usage"))usage();
        else if(p.name.equals("usage-clear"))usageClear();
        else if(p.name.equals("key"))apiKey();
        else if(p.name.equals("result"))result();
        else if(p.name.equals("diagnostics"))diagnostics();
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
        block.setPadding(dp(18),dp(11),dp(18),dp(11));
        block.setBackground(bg(PANEL,15));
        // Single-line setting title. Descriptions are intentionally not rendered.
        TextView title=text(label+"  ›",22,TEXT,true);
        block.addView(title);
        block.setMinimumHeight(dp(66));
        block.setClickable(true);block.setFocusable(true);
        block.setOnClickListener(v->clicked.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(12);
        content.addView(block,lp);
    }
    /** Only server-management rows are compact; no other settings or Home
     * geometry changes. Keep a >=48dp real touch target at 50% text scale. */
    private void compactServerRow(String label,Runnable clicked,Runnable held){
        LinearLayout card=new LinearLayout(app);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16),0,dp(16),0);
        card.setBackground(bg(PANEL,15));
        TextView name=text(label,22,TEXT,true);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name,new LinearLayout.LayoutParams(0,-1,1f));
        // The chevron is an icon, not a label: keep its apparent size.
        TextView arrow=text("›",42,TEXT,false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow,new LinearLayout.LayoutParams(dp(20),-1));
        card.setClickable(true);card.setFocusable(true);
        card.setContentDescription(label);
        card.setOnClickListener(v->clicked.run());
        card.setOnLongClickListener(v->{held.run();return true;});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));
        lp.bottomMargin=dp(6);
        content.addView(card,lp);
    }
    private void compactServerRow(String label,Runnable clicked){
        compactServerRow(label,clicked,clicked);
    }
    private void compactServerHeading(String title){
        TextView h=text(title,21,TEXT,true);
        h.setPadding(dp(8),dp(9),dp(8),dp(9));
        content.addView(h);
    }
    private void compactAddServer(){
        Button add=button("＋ 添加新服务器",0xFF246AA6);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));
        lp.bottomMargin=dp(10);
        content.addView(add,lp);
        add.setOnClickListener(v->new BlueStarDialogChoices.AppCompatBuilder(app)
            .setTitle("选择接口用途")
            .setItems(new String[]{"文字识别 OCR","视觉分析与AI追问","语音识别"},
                (d,i)->navigate("provider-edit",
                    new String[]{CloudProviderStore.OCR,
                        CloudProviderStore.VISION,CloudProviderStore.ASR}[i]+"|"))
            .setNegativeButton("取消",null).show());
    }
    private void rerenderManagementAtSamePosition(){
        final int y=currentScroll==null?0:currentScroll.getScrollY();
        render(history.get(history.size()-1));
        if(currentScroll!=null){
            ScrollView current=currentScroll;
            current.post(()->{if(!closed&&current==currentScroll)current.scrollTo(0,y);});
        }
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
        // Keep toggles concise; the switch state is already visible.
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
        row("相机","手机、USB、C110及画面设置",()->navigate("camera",""));
        row("语音与提示","语音引擎、语速、文字过滤、音效和振动",
            ()->navigate("speech",""));
        row("云端 AI","OCR、视觉模型、API Key和速度",()->navigate("cloud",""));
        row("任务管理","文字识别任务与自定义提示词",()->navigate("tasks",""));
        row("高级与诊断","上次识别、匿名日志和故障排查",
            ()->navigate("advanced",""));
    }
    private void advanced(){
        row("上次识别结果","查看文字并再次朗读",()->navigate("result",""));
        row("运行诊断","查看耗时、错误记录及导出日志",
            ()->navigate("diagnostics",""));
        heading("最近一次录音诊断");
        note(app.lastMicDiagnostic());
    }
    private void speech(){
        SharedPreferences prefs=app.getSharedPreferences("kanzi_settings",Context.MODE_PRIVATE);
        row("选择语音引擎","",()->navigate("engines",""));
        row("跟随语音引擎默认语速 · "+(app.isFollowingSystemSpeechRate()?"开启":"关闭"),
            "保留旧版语速跟随功能",()->{
                app.setFollowSystemSpeechRate(!app.isFollowingSystemSpeechRate());
                render(history.get(history.size()-1));
            });
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
        heading("朗读内容过滤");
        toggle("过滤英文","朗读时跳过英文单词和字母","speech_skip_latin",false);
        toggle("过滤数字","朗读时跳过阿拉伯数字","speech_skip_digits",false);
        toggle("过滤符号","跳过特殊符号，保留停顿","speech_skip_symbols",false);
        toggle("对所有任务生效","默认只过滤「朗读文字」任务","speech_filter_all_tasks",false);
        action("▶ 试听当前语音",()->app.previewSpeech());
        heading("音效与振动");
        toggle("提示音","拍照咔嗒声与语音发送嗖声",
            "shutter_feedback_sound",true);
        toggle("触觉反馈","拍照、开始录音、找到目标时振动",
            "shutter_feedback_vibration",true);
        action("▶ 试听拍照提示音",()->app.previewCaptureFeedback());
        action("▶ 试听语音发送音",()->app.previewVoiceSendFeedback());
        action("▶ 体验录音振动",()->app.previewVoiceStartHaptic());
        row("云服务器管理","",()->navigate("server-manage",""));
    }
    private void engines(){
        List<TextToSpeech.EngineInfo> engines=app.availableTtsEngines();
        row(("手机系统默认语音引擎".equals(app.currentSpeechEngineDescription())?"✓ ":"")
            +"跟随系统默认","",()->{
            app.changeSpeechEngine("");back();
        });
        for(TextToSpeech.EngineInfo engine:engines){
            String id=engine.name,label=engine.label==null?id:engine.label;
            row((id.equals(app.currentSpeechEngineDescription())?"✓ ":"")+label,"",()->{
                app.changeSpeechEngine(id);back();
            });
        }
        if(engines.isEmpty())note("没有发现其他语音引擎，可检查手机系统的语音合成设置。");
    }
    private void tasks(){
        for(TaskCatalog.Task task:TaskCatalog.all(app)){
            String label=(TaskCatalog.isPresetHidden(app,task.id)?"已隐藏 · ":"")+
                (task.custom?"★ ":"")+" "+task.name;
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
        if(!custom&&TaskCatalog.isPresetHidden(app,task.id))
            action("恢复显示到任务宫格",()->{
                TaskCatalog.restoreHiddenPreset(app,task.id);
                app.toastShort("已恢复："+task.name);
                back();
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
    /** This is a settings screen, not a camera video/RTSP preview page.
     *  Existing verified C110 commands are reused by MainActivity. */
    private void c110Camera(){
        // Only actual controls and a short live connection/result status.
        // Shared cloud models stay in the top-level Cloud AI settings.
        c110LiveStatus=text("",17,SUB,false);
        c110LiveStatus.setPadding(dp(12),dp(2),dp(12),dp(12));
        content.addView(c110LiveStatus,new LinearLayout.LayoutParams(-1,-2));
        updateC110Status(app.lastC110SettingsStatus());
        action("立即连接相机 Wi-Fi",()->app.connectC110WifiFromSettings());
        action("搜索 / 更换运动相机",()->app.chooseC110WifiFromSettings());
        row("下次打开自动连接 · "+(app.c110AutoConnectEnabled()?"开启":"关闭"),
            "只在成功验证并记住相机后生效；每次启动尝试一次",()->{
                app.setC110AutoConnectEnabled(!app.c110AutoConnectEnabled());
                render(history.get(history.size()-1));
            });
        row("运动相机高清上传 · "+app.c110PhotoUploadDescription(),
            "只控制上传给千问的JPEG；关闭后不再限制原图像素",()->{
            String[] choices={"原图上传（默认，不限上传尺寸，不重新压缩）",
                "1440P优化上传（手动开启，约370万像素）"};
            new BlueStarDialogChoices.AppCompatBuilder(app)
                .setTitle("运动相机高清上传")
                .setSingleChoiceItems(choices,app.c110PhotoUploadModeIndex(),(dialog,which)->{
                    app.setC110PhotoUploadModeFromSettings(which);
                    dialog.dismiss();
                    render(history.get(history.size()-1));
                }).setNegativeButton("取消",null).show();
        });
        row("机身提示音","开关机与拍照声音",()->{
            new BlueStarDialogChoices.AppCompatBuilder(app).setTitle("C110 机身提示音")
                .setItems(new String[]{"关闭提示音","恢复提示音"},(dialog,which)->
                    app.setC110BeepFromSettings(which==1))
                .setNegativeButton("取消",null).show();
        });
        row("指示灯","立即熄灭、保持开启或延时关闭",()->{
            String[] choices={"立即关闭","保持开启","15秒后关闭",
                "30秒后关闭","60秒后关闭","自定义关闭时间"};
            new BlueStarDialogChoices.AppCompatBuilder(app).setTitle("C110 指示灯")
                .setItems(choices,(dialog,which)->{
                    if(which==5)c110CustomLightDelay();
                    else app.setC110LightFromSettings(
                        new int[]{0,-1,15,30,60}[which]);
                }).setNegativeButton("取消",null).show();
        });
    }
    private void c110CustomLightDelay(){
        EditText seconds=new EditText(app);
        seconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        seconds.setSingleLine(true);
        seconds.setTextColor(TEXT);
        seconds.setTextSize(21);
        seconds.setHint("5～600秒");
        seconds.setHintTextColor(SUB);
        new BlueStarDialogChoices.AppCompatBuilder(app)
            .setTitle("指示灯自动关闭时间")
            .setView(seconds)
            .setNegativeButton("取消",null)
            .setPositiveButton("设置",(dialog,which)->{
                try{
                    int value=Integer.parseInt(seconds.getText().toString().trim());
                    if(value<5||value>600)throw new NumberFormatException();
                    app.setC110LightFromSettings(value);
                }catch(NumberFormatException wrong){
                    app.toastShort("请输入5～600之间的整数；没有下发机身命令");
                }
            }).show();
    }

    private void camera(){
        row("C110 运动相机","机身声音、指示灯和相机Wi-Fi",
            ()->navigate("c110-camera",""));
        row("摄像头 · "+app.sourceDescription(),"",()->navigate("sources",""));
        row("手机拍照模式 · "+app.phonePhotoModeDescription(),
            "快速取预览帧（默认） / 高清真实拍照（仅手机摄像头）",()->{
            String[] choices={"快速模式（预览截帧，默认）",
                "高清模式（约800万像素真实拍照）"};
            new BlueStarDialogChoices.AppCompatBuilder(app)
                .setTitle("手机摄像头拍照模式")
                .setSingleChoiceItems(choices,app.isPhoneHighRes()?1:0,(dialog,which)->{
                    app.setPhonePhotoModeFromSettings(which==1);
                    dialog.dismiss();
                    render(history.get(history.size()-1));
                }).setNegativeButton("取消",null).show();
        });
        row("手机高清上传分辨率 · "+app.phoneHdUploadDescription(),
            "真实800万拍照不变；选择1440P后等比缩图上传千问",()->{
            String[] choices={"原图约800万（可手动选择）",
                "1440P（约370万像素，默认推荐）","1080P（约207万像素）"};
            new BlueStarDialogChoices.AppCompatBuilder(app)
                .setTitle("手机高清上传分辨率")
                .setSingleChoiceItems(choices,app.phoneHdUploadModeIndex(),(dialog,which)->{
                    app.setPhoneHdUploadModeFromSettings(which);
                    dialog.dismiss();
                    render(history.get(history.size()-1));
                }).setNegativeButton("取消",null).show();
        });
        // This existing 720p/1080p setting applies ONLY to NV21 preview frames
        // (phone fast and USB). Real phone JPEG and C110 native JPEG bypass it.
        row("手机/USB预览上传 · "+app.imagePolicyDescription(),
            "仅限预览截帧；手机高清使用上方独立上传档位",()->{
            app.cycleImagePolicy();render(history.get(history.size()-1));
        });
        row("画面旋转 · "+app.rotationDescription(),"",()->{app.adjustCamera(1);render(history.get(history.size()-1));});
        row("水平镜像 · "+app.mirrorDescription(),"",()->{app.adjustCamera(2);render(history.get(history.size()-1));});
        row("垂直翻转 · "+app.flipDescription(),"",()->{app.adjustCamera(3);render(history.get(history.size()-1));});
        row("USB 左右校正 · "+app.usbCorrectionDescription(),"",()->{app.adjustCamera(4);render(history.get(history.size()-1));});
        row("缩放恢复1×","双指捏合和张开缩放画面",()->{app.adjustCamera(5);render(history.get(history.size()-1));});
        row("USB 画质调节","硬件亮度、对比度、饱和度和增益",()->navigate("usb-picture",""));
        row("USB插拔自动切换 · "+(app.isUsbAutoSwitch()?"开启":"关闭"),
            "插上切外置；拔掉切手机后置；手动选择优先",
            ()->{app.setUsbAutoSwitch(!app.isUsbAutoSwitch());
                 render(history.get(history.size()-1));});
        row("插入USB时自动启动 · "+(app.isUsbAutoLaunch()?"开启":"关闭"),
            "Android弹出设备应用选择；首次需授权，锁屏不保证启动",
            ()->{app.setUsbAutoLaunch(!app.isUsbAutoLaunch());
                 render(history.get(history.size()-1));});
    }
    private void usbPicture(){
        heading("外置摄像头硬件画质");
        if(!app.isUsbSelected()){
            note("当前为手机摄像头。请先在摄像头设置中选择 USB，再进入此页。");
            return;
        }
        TextView status=text("正在读取USB设备实际支持的控制项…",17,SUB,false);
        content.addView(status,new LinearLayout.LayoutParams(-1,-2));
        SeekBar[] sliders=new SeekBar[UsbPictureLevels.COUNT];
        TextView[] labels=new TextView[UsbPictureLevels.COUNT];
        for(int k=0;k<UsbPictureLevels.COUNT;k++){
            final int kind=k;
            labels[k]=text(UsbPictureLevels.LABELS[k]+" · 检查中",20,TEXT,true);
            labels[k].setPadding(dp(10),dp(16),dp(10),0);
            content.addView(labels[k],new LinearLayout.LayoutParams(-1,-2));
            SeekBar slider=new SeekBar(app);
            slider.setMax(100);
            slider.setProgress(50);
            slider.setEnabled(false);
            content.addView(slider,new LinearLayout.LayoutParams(-1,dp(58)));
            sliders[k]=slider;
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                    if(fromUser)labels[kind].setText(UsbPictureLevels.LABELS[kind]+
                        " · "+progress+"%（松手应用）");
                }
                @Override public void onStartTrackingTouch(SeekBar bar){}
                @Override public void onStopTrackingTouch(SeekBar bar){
                    final int value=bar.getProgress();
                    bar.setEnabled(false);
                    status.setText("正在发送 UVC 硬件调节请求…");
                    app.setUsbPictureControl(kind,value,(supported,values,message)->{
                        if(closed||history.isEmpty()||
                           !"usb-picture".equals(history.get(history.size()-1).name))return;
                        status.setText(message);
                        bar.setEnabled(true);
                    });
                }
            });
        }
        action("重新读取设备控制项",()->render(history.get(history.size()-1)));
        action("恢复USB画质默认设置",()->new BlueStarDialogChoices.AppCompatBuilder(app)
            .setTitle("恢复USB摄像头硬件画质")
            .setMessage("只恢复该USB设备的亮度、对比度、饱和度和增益，"
                +"不会改变已修复的旋转、左右校正、缩放或云端模型。")
            .setNegativeButton("取消",null)
            .setPositiveButton("恢复默认",(dialog,which)->{
                status.setText("正在恢复硬件默认值…");
                app.resetUsbPictureControls((supported,values,message)->{
                    if(closed||history.isEmpty()||
                       !"usb-picture".equals(history.get(history.size()-1).name))return;
                    app.toastShort(message);
                    render(history.get(history.size()-1));
                });
            }).show());
        app.queryUsbPictureControls((supported,values,message)->{
            if(closed||history.isEmpty()||
               !"usb-picture".equals(history.get(history.size()-1).name))return;
            status.setText(message);
            for(int k=0;k<UsbPictureLevels.COUNT;k++){
                boolean valid=supported!=null&&supported.length>k&&supported[k]&&
                    values!=null&&values.length>k&&values[k]>=0;
                sliders[k].setEnabled(valid);
                if(valid){
                    sliders[k].setProgress(UsbPictureLevels.clamp(values[k]));
                    labels[k].setText(UsbPictureLevels.LABELS[k]+" · "+
                        values[k]+"%");
                }else labels[k].setText(UsbPictureLevels.LABELS[k]+" · 设备不支持");
            }
        });
    }
    private void sources(){
        row("USB 外接摄像头","保留原生UVC直接预览",()->{app.setCameraSourceFromSettings(0);back();});
        row("手机后置摄像头","原生 Camera2",()->{app.setCameraSourceFromSettings(1);back();});
        row("手机前置摄像头","原生 Camera2",()->{app.setCameraSourceFromSettings(2);back();});
    }
    private void cloud(){
        // One source of truth: these rows SHOW the actual route and model,
        // not a hidden legacy selection overruled by custom configuration.
        row("文字识别 OCR · "+CloudProviderStore.activeModelDescription(
            app,CloudProviderStore.OCR,app.ocrModelDescription()),"",
            ()->navigate("provider-role",CloudProviderStore.OCR));
        row("视觉分析与AI追问 · "+CloudProviderStore.activeModelDescription(
            app,CloudProviderStore.VISION,app.visionModelDescription()),"",
            ()->navigate("provider-role",CloudProviderStore.VISION));
        row("语音识别 · "+CloudProviderStore.activeModelDescription(
            app,CloudProviderStore.ASR,"qwen3-asr-flash"),"",
            ()->navigate("provider-role",CloudProviderStore.ASR));
        row("文字朗读方式 · "+(OcrSpeedExperiment.C.equals(app.ocrSpeedDescription())?
                "快速逐行":"兼容全文"),"",()->navigate("ocr-speed",""));
        row("联网智能追问 · "+(app.isWebSearchEnabled()?"开启":"关闭"),
            "",()->{app.setWebSearchEnabled(!app.isWebSearchEnabled());
                 render(history.get(history.size()-1));});
        row("模型用量统计","查看当前真正调用的模型与Token",()->navigate("usage",""));
        row("原有百炼 API Key","仅用于选择百炼时",()->navigate("key",""));
    }
    private static String roleTitle(String role){
        if(CloudProviderStore.OCR.equals(role))return "文字识别 OCR";
        if(CloudProviderStore.ASR.equals(role))return "语音转文字 ASR";
        return "视觉分析与AI追问";
    }
    private static String protocolTitle(String type){
        if(CloudProviderStore.ASR_MULTIPART.equals(type))return "通用音频转写 /audio/transcriptions";
        if(CloudProviderStore.ASR_QWEN.equals(type))return "百炼兼容 Chat 音频输入";
        if(CloudProviderStore.ASR_CHAT_AUDIO.equals(type))return "OpenAI Chat 音频输入";
        if(CloudProviderStore.LAYOUT.equals(type))return "Layout Parsing（专用OCR，非流式）";
        return "OpenAI 兼容 Chat Completions（图文）";
    }
    private void providerRole(String role){
        if(!CloudProviderPolicy.validRole(role)){note("无效云端任务类别");return;}
        String builtin=CloudProviderStore.OCR.equals(role)?app.ocrModelDescription():
            CloudProviderStore.ASR.equals(role)?"qwen3-asr-flash":app.visionModelDescription();
        String selected=CloudProviderStore.selectedId(app,role);
        heading("原有百炼");
        if(CloudProviderStore.ASR.equals(role)){
            row((selected.isEmpty()?"✓ ":"")+"百炼 · qwen3-asr-flash","",()->{
                CloudProviderStore.select(app,role,"");
                render(history.get(history.size()-1));
            });
        }else if(CloudProviderStore.OCR.equals(role)){
            for(int i=0;i<OcrModelCatalog.IDS.length;i++){
                final String id=OcrModelCatalog.IDS[i];
                row((selected.isEmpty()&&id.equals(app.ocrModelDescription())?"✓ ":"")+
                    "百炼 · "+OcrModelCatalog.LABELS[i],"",()->{
                    app.selectOcrModel(id); // Also switches active provider to Bailian.
                    render(history.get(history.size()-1));
                });
            }
        }else{
            for(int i=0;i<VisionModelCatalog.IDS.length;i++){
                final String id=VisionModelCatalog.IDS[i];
                row((selected.isEmpty()&&id.equals(app.visionModelDescription())?"✓ ":"")+
                    "百炼 · "+VisionModelCatalog.LABELS[i],"",()->{
                    app.selectVisionModel(id);
                    render(history.get(history.size()-1));
                });
            }
        }
        heading("自定义服务商");
        int total=0;
        for(CloudProviderStore.Profile profile:CloudProviderStore.list(app)){
            if(!CloudProviderPolicy.accepts(role,profile.protocol))continue;
            total++;
            row((profile.id.equals(selected)?"✓ ":"")+profile.name+" · "+profile.model,"",()->{
                CloudProviderStore.select(app,role,profile.id);
                app.toastShort("当前实际启用："+profile.model);
                render(history.get(history.size()-1));
            });

        }
        if(total==0)note("没有已保存的自定义模型");
    }
    private EditText providerInput(String title,String value,String hint,int inputType){
        EditText input=new EditText(app);
        input.setContentDescription(title);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP,18);
        input.setTextColor(TEXT);input.setHintTextColor(SUB);
        input.setInputType(inputType);
        final String example=title+" · "+hint;
        input.setHint(example);
        // Tap/focus clears the example immediately; empty blur restores it.
        input.setOnFocusChangeListener((v,focused)->{
            if(focused)input.setHint("");
            else if(input.getText().length()==0)input.setHint(example);
        });
        if(value!=null&&!value.isEmpty())input.setText(value);
        content.addView(input,new LinearLayout.LayoutParams(-1,dp(60)));
        return input;
    }
    private void providerEdit(String spec){
        int split=spec==null?-1:spec.indexOf('|');
        if(split<0){note("缺少配置类别");return;}
        String role=spec.substring(0,split),id=spec.substring(split+1);
        if(!CloudProviderPolicy.validRole(role)){note("无效配置类别");return;}
        CloudProviderStore.Profile old=id.isEmpty()?null:CloudProviderStore.find(app,id);
        if(!id.isEmpty()&&old==null){note("配置不存在，请返回重新选择");return;}
        EditText name=providerInput("配置名称",old==null?"":old.name,
            "例如 火山OCR / 千问视觉 / 自选语音",
            InputType.TYPE_CLASS_TEXT);
        EditText url=providerInput("完整 HTTPS API 地址",old==null?"":old.url,
            CloudProviderStore.ASR.equals(role)?
                "https://服务商域名/v1/audio/transcriptions":
                "https://服务商域名/v1/chat/completions",
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText model=providerInput("模型 ID",old==null?"":old.model,
            "填厂商控制台给出的完整模型 ID",InputType.TYPE_CLASS_TEXT);
        EditText key=providerInput("API Key（本机加密）","",
            old==null?"留空自动复用同服务商有效密钥，或粘贴新Key":"留空＝保留原来的 API Key",
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final String[] reuseKeySource={""};
        Button reuseKey=button("复用已有密钥（可选）",PANEL);
        reuseKey.setContentDescription("从相同HTTPS服务商的已保存密钥中选择，不显示密钥内容");
        content.addView(reuseKey,new LinearLayout.LayoutParams(-1,dp(48)));
        reuseKey.setOnClickListener(v->{
            try{
                List<CloudProviderStore.Profile> available=
                    CloudProviderStore.reusableProfiles(
                        app,url.getText().toString(),id);
                if(available.isEmpty()){
                    app.toastShort("没有找到相同HTTPS服务商的有效密钥，请填写API Key");
                    return;
                }
                String[] labels=new String[available.size()];
                for(int i=0;i<available.size();i++)
                    labels[i]=available.get(i).name+" · "+available.get(i).model;
                new BlueStarDialogChoices.AppCompatBuilder(app).setTitle("选择复用的密钥")
                    .setItems(labels,(dialog,index)->{
                        CloudProviderStore.Profile chosen=available.get(index);
                        reuseKeySource[0]=chosen.id;
                        key.setText("");
                        reuseKey.setText("✓ 复用："+chosen.name+" 的密钥");
                    }).setNegativeButton("取消",null).show();
            }catch(Exception error){app.toastShort("无法复用："+error.getMessage());}
        });
        final String[] protocol=new String[]{
            old!=null?old.protocol:(CloudProviderStore.ASR.equals(role)?
                CloudProviderStore.ASR_MULTIPART:CloudProviderStore.CHAT)};
        final Switch streaming=new Switch(app);
        if(CloudProviderStore.ASR.equals(role)||CloudProviderStore.OCR.equals(role)){
            TextView current=text("协议："+protocolTitle(protocol[0])+"  ›",18,TEXT,true);
            current.setPadding(dp(10),dp(14),dp(10),dp(14));
            current.setBackground(bg(PANEL,12));
            current.setClickable(true);current.setFocusable(true);
            content.addView(current,new LinearLayout.LayoutParams(-1,-2));
            current.setOnClickListener(v->{
                final String[] options=CloudProviderStore.ASR.equals(role)?
                    new String[]{CloudProviderStore.ASR_MULTIPART,
                        CloudProviderStore.ASR_QWEN,CloudProviderStore.ASR_CHAT_AUDIO}:
                    new String[]{CloudProviderStore.CHAT,CloudProviderStore.LAYOUT};
                final String[] labels=new String[options.length];
                for(int i=0;i<options.length;i++)labels[i]=protocolTitle(options[i]);
                new BlueStarDialogChoices.AppCompatBuilder(app).setTitle("选择 "+roleTitle(role)+" 协议")
                    .setItems(labels,(dialog,index)->{
                        protocol[0]=options[index];
                        current.setText("协议："+protocolTitle(protocol[0])+"  ›");
                        if(CloudProviderStore.OCR.equals(role)){
                            boolean layout=CloudProviderStore.LAYOUT.equals(protocol[0]);
                            streaming.setVisibility(layout?View.GONE:View.VISIBLE);
                            if(layout)streaming.setChecked(false);
                            else if(old==null)streaming.setChecked(true);
                            if(layout)url.setHint(
                                "https://open.bigmodel.cn/api/paas/v4/layout_parsing");
                            else url.setHint("https://服务商域名/v1/chat/completions");
                        }
                    }).setNegativeButton("取消",null).show();
            });
        }
        if(!CloudProviderStore.ASR.equals(role)){
            streaming.setText("快速流式返回 SSE（支持逐行朗读）");
            streaming.setTextColor(TEXT);
            streaming.setTextSize(TypedValue.COMPLEX_UNIT_SP,18);
            streaming.setChecked(old==null||old.stream);
            streaming.setVisibility(CloudProviderStore.LAYOUT.equals(protocol[0])?
                View.GONE:View.VISIBLE);
            content.addView(streaming);
        }
        action("保存配置",()->{
            try{
                CloudProviderStore.Profile profile=new CloudProviderStore.Profile(
                    id,name.getText().toString(),url.getText().toString(),
                    model.getText().toString(),protocol[0],streaming.isChecked());
                CloudProviderStore.Profile saved=CloudProviderStore.save(
                    app,profile,key.getText().toString(),reuseKeySource[0]);
                key.setText("");
                app.toastShort("已保存："+saved.name);
                back();
            }catch(Exception error){
                app.toastShort("保存失败："+error.getMessage());
            }
        });
    }
    // List user's actual saved profiles FIRST; never infer a duplicate from
    // similar model IDs because two profiles may use different keys/endpoints.
    private void cloudServers(){
        compactServerHeading("我的服务器");
        List<CloudProviderStore.Profile> profiles=CloudProviderStore.list(app);
        if(profiles.isEmpty()){
            TextView empty=text("暂无自定义服务器",18,SUB,false);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(16),0,dp(16),0);
            content.addView(empty,new LinearLayout.LayoutParams(-1,dp(44)));
        }
        for(CloudProviderStore.Profile profile:profiles){
            boolean asr=CloudProviderStore.ASR_MULTIPART.equals(profile.protocol)||
                CloudProviderStore.ASR_QWEN.equals(profile.protocol)||
                CloudProviderStore.ASR_CHAT_AUDIO.equals(profile.protocol);
            // Chat can serve both OCR and vision; use the currently selected
            // role when possible, while showing the saved profile exactly ONCE.
            String role=asr?CloudProviderStore.ASR:
                (profile.id.equals(CloudProviderStore.selectedId(app,CloudProviderStore.OCR))?
                    CloudProviderStore.OCR:
                    profile.id.equals(CloudProviderStore.selectedId(app,CloudProviderStore.VISION))?
                    CloudProviderStore.VISION:CloudProviderStore.OCR);
            String spec="c|"+role+"|"+profile.id;
            compactServerRow(profile.name+" · "+profile.model,
                ()->navigate("server-actions",spec));
        }
        compactAddServer();
        // Built-ins are a collapsed group by default. A model supporting
        // both OCR and Vision gets one management entry and one thinking key.
        LinkedHashSet<String> models=new LinkedHashSet<>();
        for(String id:OcrModelCatalog.IDS)models.add(id);
        for(String id:VisionModelCatalog.IDS)models.add(id);
        models.add("qwen3-asr-flash");
        compactServerRow("原有百炼模型（"+models.size()+"） · "+
            (builtinModelsExpanded?"收起":"展开"),()->
                rerenderBuiltinsToggle());
        if(!builtinModelsExpanded)return;
        for(String model:models){
            String role="qwen3-asr-flash".equals(model)?CloudProviderStore.ASR:
                OcrModelCatalog.indexOf(model)>=0?
                    CloudProviderStore.OCR:CloudProviderStore.VISION;
            String spec="b|"+role+"|"+model;
            compactServerRow("百炼 · "+model,()->navigate("server-actions",spec));
        }
    }
    private void rerenderBuiltinsToggle(){
        builtinModelsExpanded=!builtinModelsExpanded;
        rerenderManagementAtSamePosition();
    }

    // A standard tap and a long press open exactly the SAME accessible menu.
    private void serverActions(String spec){
        String[] args=spec==null?new String[0]:spec.split("\\|",3);
        if(args.length!=3){note("模型参数无效");return;}
        final boolean custom="c".equals(args[0]);
        final String role=args[1],identity=args[2];
        if(!CloudProviderPolicy.validRole(role)){note("无效服务类别");return;}
        final CloudProviderStore.Profile saved=custom?
            CloudProviderStore.find(app,identity):null;
        if(custom&&saved==null){note("服务器已被删除");return;}
        final String model=custom?saved.model:identity;
        final String protocol=custom?saved.protocol:
            (CloudProviderStore.ASR.equals(role)?
                CloudProviderStore.ASR_QWEN:CloudProviderStore.CHAT);
        final String url=custom?saved.url:CloudProviderStore.BAILIAN_URL;
        final String profileId=custom?saved.id:"";
        compactServerHeading(custom?saved.name+" · "+model:"百炼 · "+model);
        if(!custom&&CloudProviderStore.ASR.equals(role)){
            TextView builtin=text("系统内置语音模型，无可修改设置",18,SUB,false);
            builtin.setPadding(dp(16),dp(10),dp(12),dp(10));
            content.addView(builtin);
        }
        if(custom)compactServerRow("修改服务器",()->navigate("provider-edit",role+"|"+profileId));
        String thinkingStyle=CloudThinkingPolicy.style(url,model,protocol);
        if(!CloudThinkingPolicy.NONE.equals(thinkingStyle)){
            boolean thinking=CloudModelOptions.get(app,role,profileId,model).thinking;
            compactServerRow("深度思考 · "+(thinking?"开启":"关闭"),()->{
                CloudModelOptions.saveThinking(app,role,profileId,model,!thinking);
                render(history.get(history.size()-1));
            });
        }
        if(!CloudProviderStore.ASR.equals(role)&&CloudProviderStore.CHAT.equals(protocol))
            compactServerRow("自定义 OCR 提示词",()->
                navigate("model-prompt",(custom?"c":"b")+"|"+CloudProviderStore.OCR+"|"+identity));
        if(!CloudProviderStore.ASR.equals(role))
            compactServerRow("测试连接（少量付费）",()->
                app.testCloudProvider(role,profileId,model));
        if(custom)
            compactServerRow("删除服务器",()->new BlueStarDialogChoices.AppCompatBuilder(app)
                .setTitle("删除 "+saved.name+"？")
                .setMessage("将删除这套配置和加密密钥；若正在使用将切回原有百炼。")
                .setNegativeButton("取消",null)
                .setPositiveButton("确认删除",(d,w)->{
                    CloudProviderStore.delete(app,profileId);
                    app.toastShort("已删除："+saved.name);
                    back();
                }).show());
    }
    private void modelPrompt(String spec){
        String[] args=spec==null?new String[0]:spec.split("\\|",3);
        if(args.length!=3||!CloudProviderStore.OCR.equals(args[1])){
            note("只有文字识别模型可以编辑OCR提示词");return;
        }
        boolean custom="c".equals(args[0]);
        String id=custom?args[2]:"";
        CloudProviderStore.Profile saved=custom?
            CloudProviderStore.find(app,id):null;
        if(custom&&saved==null){note("服务器不存在");return;}
        String model=custom?saved.model:args[2];
        if(custom&&!CloudProviderStore.CHAT.equals(saved.protocol)){
            note("Layout Parsing接口不接收自定义提示词");return;
        }
        EditText input=new EditText(app);
        input.setContentDescription("该模型的OCR提示词");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setGravity(Gravity.TOP);
        input.setTextColor(TEXT);input.setHintTextColor(SUB);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
        input.setMinLines(5);input.setMaxLines(12);
        input.setHint("填写这款模型专用的OCR指令，留空使用原任务提示词");
        input.setText(CloudModelOptions.get(app,args[1],id,model).ocrPrompt);
        content.addView(input,new LinearLayout.LayoutParams(-1,-2));
        action("保存提示词",()->{
            try{
                CloudModelOptions.saveOcrPrompt(app,args[1],id,model,
                    input.getText().toString());
                app.toastShort("提示词已保存");back();
            }catch(Exception e){input.setError(e.getMessage());}
        });
        action("恢复默认",()->{
            CloudModelOptions.saveOcrPrompt(app,args[1],id,model,"");
            app.toastShort("已使用原任务提示词");back();
        });
    }
    private void ocrSpeed(){
        for(String id:new String[]{OcrSpeedExperiment.C,OcrSpeedExperiment.A}){
            final boolean selected=id.equals(app.ocrSpeedDescription());
            row((selected?"✓ ":"")+OcrSpeedExperiment.LABELS[OcrSpeedExperiment.index(id)],"",
                ()->{app.selectOcrSpeedMode(id);render(history.get(history.size()-1));});
        }
    }

    /** Uses exactly the existing opaque BG/PANEL/TEXT/SUB settings palette. */
    private void usageLine(ModelUsageLedger.Line line){
        LinearLayout block=new LinearLayout(app);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(16),dp(13),dp(16),dp(13));
        block.setBackground(bg(PANEL,15));
        block.addView(text(line.model,20,TEXT,true));
        String details;
        if(line.calls==0)details="今日尚未调用";
        else details="调用 "+line.calls+" 次（失败 "+line.failed+" 次）"
            +"\n已报告输入 "+line.input+" Token、输出 "+line.output+" Token"
            +"\n已报告合计 "+(line.input+line.output)+" Token"
            +(line.unknown>0?"；用量未知 "+line.unknown+" 次":"");
        TextView d=text(details,16,SUB,false);
        d.setPadding(0,dp(7),0,0);
        block.addView(d);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(10);
        content.addView(block,lp);
    }

    private void usage(){
        ModelUsageLedger.Snapshot snapshot=app.todayModelUsage();
        if(snapshot==null){note("模型统计暂未初始化");return;}
        heading("当前真正生效的云端模型");
        note("OCR："+CloudProviderStore.activeModelDescription(
            app,CloudProviderStore.OCR,app.ocrModelDescription())+
            "\n视觉："+CloudProviderStore.activeModelDescription(
            app,CloudProviderStore.VISION,app.visionModelDescription())+
            "\nASR："+CloudProviderStore.activeModelDescription(
            app,CloudProviderStore.ASR,"qwen3-asr-flash"));
        heading("今日统计 · "+snapshot.day);
        note("已报告 Token 合计："+(snapshot.input+snapshot.output)
            +"\n输入："+snapshot.input+"  输出："+snapshot.output
            +"\n视觉调用："+snapshot.calls+" 次，失败："+snapshot.failed
            +" 次，用量未返回："+snapshot.unknown+" 次");
        heading("最近一次实际调用");
        if(snapshot.lastRequested.isEmpty())note("本机还没有记录模型调用");
        else note("请求模型："+snapshot.lastRequested
            +"\n服务端模型："+(snapshot.lastReported.isEmpty()?
                "未返回（不能据此确认实际模型）":snapshot.lastReported)
            +"\n调用结果："+("success".equals(snapshot.lastOutcome)?"成功":"失败"));
        heading("各模型今日用量");
        for(ModelUsageLedger.Line line:snapshot.lines)usageLine(line);
        action("刷新统计",()->render(history.get(history.size()-1)));
        row("清空本机模型统计","仅删除本机记录，不影响百炼账户计费",()->navigate("usage-clear",""));
    }
    private void usageClear(){
        heading("请确认是否清空统计记录");
        note("只清空本机统计，不影响百炼账单。");
        action("确认清空本机统计",()->{
            app.clearModelUsage();
            app.toastShort("已请求清空本机模型用量数据");
            back();
        });
        action("取消，保留统计",this::back);
    }

    private void apiKey(){
        EditText key=new EditText(app);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP,20);
        key.setTextColor(Color.WHITE);
        key.setHint("粘贴自己的百炼 API Key");
        key.setHintTextColor(SUB);
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(key,new LinearLayout.LayoutParams(-1,dp(72)));
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
    private void diagnostics(){
        action("查看上次发给 AI 的照片（仅本机）",()->app.previewLastUploadedJpeg());
        action("复制诊断摘要",()->app.copyRuntimeDiagnostics());
        action("导出匿名诊断日志",()->app.exportRuntimeDiagnostics());
        action("刷新诊断记录",()->render(history.get(history.size()-1)));
        action("清空全部诊断记录",()->new BlueStarDialogChoices.AppCompatBuilder(app)
            .setTitle("清空运行诊断")
            .setMessage("确定清除本机保存的全部匿名性能事件？此操作不能撤销。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确定清空",(dialog,which)->{
                app.clearRuntimeDiagnostics();
                render(history.get(history.size()-1));
            }).show());
        heading("最近的诊断记录");
        TextView report=text(app.runtimeDiagnosticSummary(),16,TEXT,false);
        report.setTextIsSelectable(true);
        report.setPadding(dp(12),dp(12),dp(12),dp(12));
        report.setBackground(bg(PANEL,12));
        content.addView(report,new LinearLayout.LayoutParams(-1,-2));
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
