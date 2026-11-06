package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.TextureView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.base.CameraActivity;
import com.jiangdg.ausbc.camera.bean.CameraRequest;
import com.jiangdg.ausbc.camera.bean.PreviewSize;
import com.jiangdg.ausbc.callback.ICameraStateCallBack;
import com.jiangdg.ausbc.callback.IPreviewDataCallBack;
import com.jiangdg.ausbc.widget.AspectRatioTextureView;
import com.jiangdg.ausbc.widget.IAspectRatio;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 2.0-layout-preserving dual-camera cloud task probe. 
 * Reuses native UVC/TextureView + the original independent Camera2 controller,
 * with no OCR inference, no NETWORK permission and no photo persistence.
 */
public final class MainActivity extends CameraActivity {
    private static final String TAG = "BlueStarDualCloud";
    private static final String PREFS = "kanzi_settings";
    private static final String PREF_PHONE_CAMERA = "phone_camera_source";
    private static final String PREF_FRONT_CAMERA = "phone_camera_front";
    private static final String PREF_PREVIEW_ROTATION = "preview_rotation";
    private static final String PREF_PREVIEW_MIRROR = "preview_mirror";
    private static final String PREF_PREVIEW_FLIP = "preview_flip";
    private static final String PREF_USB_PREVIEW_CORRECT_MIRROR = "usb_preview_correct_left_right";
    private static final String PREF_SOUND = "shutter_feedback_sound";
    private static final String PREF_HAPTIC = "shutter_feedback_vibration";
    private static final String PREF_SPEECH_RATE = "local_tts_rate";
    private static final String PREF_SPEECH_DEFAULT = "local_tts_follow_default";
    private static final String PREF_TTS_ENGINE = "app_tts_engine_package";
    private static final String PREF_IMAGE_POLICY = "cloud_capture_quality";
    private static final String PREF_VISION_MODEL = "cloud_vision_model";
    private static final int REQUEST_PHONE_CAMERA = 4051;
    private static final int REQUEST_VOICE_MIC = 4052;

    private FrameLayout previewContainer,rootFrame;
    private TextView currentTaskChip,zoomChip,resultChip;
    private View tapOverlay;
    private ScaleGestureDetector zoomDetector;
    private boolean touchMulti;
    private float downX,downY,zoom=1f;
    private long downAtMs,lastPinchMs;
    private boolean feedbackSound=true,feedbackVibration=true,followSystemSpeechRate=true;
    private float speechRate=1f;
    private SpeechRecognizer speechRecognizer;
    private boolean voiceListening;
    private byte[] lastSessionJpeg;
    private String lastSessionText="",lastSessionTask="";
    private final ArrayList<String> followupTurns=new ArrayList<>();
    private AspectRatioTextureView cameraTextureView;
    private TextureView phoneTexture;
    private TextView phonePreviewStatus;
    private Button modeButton, startButton, settingsButton;
    private AlertDialog settingsDialog;
    private InternalCameraController phoneController;
    private boolean usingPhoneCamera, phoneFrontFacing, phonePreviewReceived;
    private int phoneStartSequence, phoneRawWidth=1280, phoneRawHeight=720;
    private int phoneSensorNaturalRotation=90, phoneOcrRotation;
    private int previewRotation;
    private boolean previewMirror, previewVerticalFlip, usbPreviewCorrectMirror=true;

    private boolean previewCallbackAdded;
    private boolean usbForeground, usbStopPending, usbReopenWanted;
    private long usbOpenedAtMs, usbRecoveryAtMs;
    private volatile Frame latestPhoneFrame, latestUsbFrame;
    private final AtomicLong usbFrameCount = new AtomicLong();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService encoder = Executors.newSingleThreadExecutor();
    private final AtomicInteger requestVersion = new AtomicInteger();
    private long lastTapMs;
    private Future<?> encodingTask;
    private TextToSpeech tts;
    private volatile boolean ttsReady;
    private String ttsEngine = "";
    private TaskCatalog.Task currentTask;
    private boolean uiActive;
    private boolean cloudBusy;
    private volatile int speechRequestToken=-1;
    private volatile long speechCaptureAtMs=-1;
    private volatile long ttsEngineStartMs=-1;
    private volatile long ttsAudioCallbackMs=-1;
    private AlertDialog cloudResultDialog;
    private String cloudResultInfo="",cloudResultText="";
    private int cloudResultToken=-1;
    private String imagePolicy="auto";
    private String visionModel="qwen3-vl-plus";
    private String lastCloudResult="";

    private static final class Frame {
        final byte[] pixels;
        final int width, height;
        final long atMs;
        Frame(byte[] pixels, int width, int height, long atMs) {
            this.pixels=pixels; this.width=width; this.height=height; this.atMs=atMs;
        }
    }

    private final IPreviewDataCallBack previewCallback = new IPreviewDataCallBack() {
        @Override public void onPreviewData(byte[] frame, int w, int h, DataFormat format) {
            if (!uiActive || usingPhoneCamera || format != DataFormat.NV21) return;
            updateFrame(frame,w,h,false);
        }
    };

    private void updateFrame(byte[] bytes,int w,int h,boolean phone) {
        if (bytes==null || w<2 || h<2 || (w&1)!=0 || (h&1)!=0 ||
            (long)bytes.length != (long)w*h*3L/2L) return;
        Frame frame = new Frame(bytes,w,h,SystemClock.elapsedRealtime());
        if(phone) latestPhoneFrame=frame;
        else { latestUsbFrame=frame; usbFrameCount.incrementAndGet(); }
    }

    /** Full screen camera + one-tap capture/pinch + accessible dock. */
    @Override protected View getRootView(LayoutInflater inflater){
        FrameLayout root=new FrameLayout(this);
        rootFrame=root;
        root.setBackgroundColor(Color.BLACK);
        previewContainer=new FrameLayout(this);
        previewContainer.setBackgroundColor(Color.BLACK);
        previewContainer.setClipChildren(true);
        root.addView(previewContainer,new FrameLayout.LayoutParams(-1,-1));

        phoneTexture=new TextureView(this);
        phoneTexture.setOpaque(false);
        phoneTexture.addOnLayoutChangeListener((v,l,t,r,bl,ol,ot,or,ob)->{
            if(l!=ol||t!=ot||r!=or||bl!=ob)applyPhonePreviewTransform();
        });
        phoneTexture.setVisibility(View.GONE);
        phonePreviewStatus=new TextView(this);
        phonePreviewStatus.setTextColor(Color.WHITE);
        phonePreviewStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);
        phonePreviewStatus.setGravity(Gravity.CENTER);
        phonePreviewStatus.setPadding(dp(18),dp(12),dp(18),dp(12));
        phonePreviewStatus.setBackgroundColor(0xB2101B31);
        phonePreviewStatus.setVisibility(View.GONE);
        root.addView(phonePreviewStatus,new FrameLayout.LayoutParams(-1,dp(104),Gravity.CENTER));

        tapOverlay=new View(this);
        tapOverlay.setContentDescription("点击拍照识别；双指缩放摄像头画面");
        tapOverlay.setFocusable(true);
        tapOverlay.setOnClickListener(v->captureOneFrame());
        zoomDetector=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){
                touchMulti=true;return true;
            }
            @Override public boolean onScale(ScaleGestureDetector d){
                setCameraZoom(zoom*d.getScaleFactor());return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d){
                lastPinchMs=SystemClock.elapsedRealtime();
            }
        });
        tapOverlay.setOnTouchListener((v,e)->{
            if(e.getPointerCount()>1)touchMulti=true;
            zoomDetector.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    touchMulti=false;downX=e.getX();downY=e.getY();
                    downAtMs=SystemClock.elapsedRealtime();return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    touchMulti=true;return true;
                case MotionEvent.ACTION_UP:
                    long now=SystemClock.elapsedRealtime();
                    if(!touchMulti&&now-lastPinchMs>550L&&now-downAtMs<750L&&
                       Math.abs(e.getX()-downX)<dp(20)&&Math.abs(e.getY()-downY)<dp(20))
                        v.performClick();
                    return true;
                default:return true;
            }
        });
        root.addView(tapOverlay,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout title=new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12),dp(5),dp(12),dp(5));
        ImageView brand=new ImageView(this);
        brand.setImageResource(R.drawable.bluestar_icon);
        title.addView(brand,new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView appTitle=new TextView(this);
        appTitle.setText("蓝星向导");
        appTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP,19);
        appTitle.setPadding(dp(6),0,dp(6),0);
        title.addView(appTitle,new LinearLayout.LayoutParams(0,dp(50),1f));
        zoomChip=new TextView(this);
        zoomChip.setText("1.0×");
        zoomChip.setGravity(Gravity.CENTER);
        zoomChip.setTextColor(Color.WHITE);
        zoomChip.setBackground(roundRect(0xDD204365,15));
        zoomChip.setPadding(dp(12),dp(9),dp(12),dp(9));
        title.addView(zoomChip);
        root.addView(title,new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));

        currentTaskChip=new TextView(this);
        currentTaskChip.setTextColor(0xFFECF6FF);
        currentTaskChip.setTextSize(TypedValue.COMPLEX_UNIT_SP,15);
        currentTaskChip.setPadding(dp(14),dp(7),dp(14),dp(7));
        currentTaskChip.setBackground(roundRect(0xC9153359,14));
        FrameLayout.LayoutParams chip=new FrameLayout.LayoutParams(-2,dp(37),Gravity.TOP|Gravity.LEFT);
        chip.topMargin=dp(64);chip.leftMargin=dp(12);
        root.addView(currentTaskChip,chip);

        resultChip=new TextView(this);
        resultChip.setGravity(Gravity.CENTER);
        resultChip.setTextColor(Color.WHITE);
        resultChip.setTextSize(TypedValue.COMPLEX_UNIT_SP,13);
        resultChip.setText("点击画面识别");
        resultChip.setPadding(dp(10),dp(6),dp(10),dp(6));
        resultChip.setBackground(roundRect(0xCE163758,13));
        FrameLayout.LayoutParams stat=new FrameLayout.LayoutParams(-2,dp(36),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        stat.bottomMargin=dp(99);
        root.addView(resultChip,stat);

        LinearLayout dock=new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(7),dp(5),dp(7),dp(7));
        dock.setBackground(roundRect(0xED091D36,20));
        modeButton=createControlButton("▦\n任务",0xFF2C547D);
        startButton=createControlButton("●\nAI对话",0xFF176BAC);
        settingsButton=createControlButton("⚙\n设置",0xFF2C547D);
        for(Button btt:new Button[]{modeButton,startButton,settingsButton}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(75),1f);
            lp.setMargins(dp(3),0,dp(3),0);dock.addView(btt,lp);
        }
        root.addView(dock,new FrameLayout.LayoutParams(-1,dp(89),Gravity.BOTTOM));
        modeButton.setContentDescription("任务选择");
        startButton.setContentDescription("AI对话，追问刚才照片");
        settingsButton.setContentDescription("设置");
        modeButton.setOnClickListener(v->showTaskSelector());
        startButton.setOnClickListener(v->startVoiceFollowup());
        settingsButton.setOnClickListener(v->showSettingsDialog());
        return root;
    }

    private GradientDrawable roundRect(int color,int radius){
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;
    }
    private int dp(int d){return Math.round(d*getResources().getDisplayMetrics().density);}

    private Button createControlButton(String label,int background){
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4),0,dp(4),0);
        button.setBackground(roundRect(background,15));
        return button;
    }

    private void refreshTask(String id){
        currentTask=null;
        List<TaskCatalog.Task> tasks=TaskCatalog.all(this);
        for(TaskCatalog.Task t:tasks)if(t.id.equals(id)){currentTask=t;break;}
        if(currentTask==null)currentTask=tasks.get(0);
        TaskCatalog.select(this,currentTask.id);
        if(currentTaskChip!=null)currentTaskChip.setText("任务："+currentTask.name);
        if(modeButton!=null)modeButton.setContentDescription("任务选择，当前："+currentTask.name);
    }

    private void showTaskSelector(){
        TaskDesktopOverlay.show(this,TaskCatalog.all(this),currentTask.id,
            new TaskDesktopOverlay.Listener(){
                @Override public void selected(TaskCatalog.Task task){
                    invalidate();refreshTask(task.id);
                    setResultStatus("已选择："+task.name);
                }
                @Override public void createTask(){editTask(null);}
            });
    }

    private void editTask(TaskCatalog.Task original){
        boolean existing=original!=null;
        String[] icon={existing?original.icon:"✦"};
        EditText name=new EditText(this);
        name.setSingleLine(true);name.setTextColor(Color.WHITE);
        name.setHint("任务名称");name.setText(existing?original.name:"");
        EditText prompt=new EditText(this);
        prompt.setTextColor(Color.WHITE);
        prompt.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        prompt.setMinLines(4);prompt.setHint("请输入AI任务指令");
        prompt.setText(existing?original.instruction:"");
        Button symbol=new Button(this);
        symbol.setAllCaps(false);symbol.setText("选择图标："+icon[0]);
        symbol.setOnClickListener(v->{
            String[] symbols=TaskCatalog.iconPalette();
            new AlertDialog.Builder(this).setTitle("选择任务图标")
                .setItems(symbols,(d,which)->{
                    icon[0]=symbols[which];symbol.setText("选择图标："+icon[0]);
                }).show();
        });
        LinearLayout form=new LinearLayout(this);
        form.setPadding(dp(20),dp(8),dp(20),dp(8));
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(name);form.addView(prompt);form.addView(symbol);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(existing?"编辑自定义任务":"新建自定义任务")
            .setView(form).setNegativeButton("取消",null)
            .setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(w->{
                try{
                    TaskCatalog.Task saved=TaskCatalog.upsert(this,
                        existing?original.id:null,name.getText().toString(),
                        prompt.getText().toString(),icon[0]);
                    invalidate();refreshTask(saved.id);dialog.dismiss();
                }catch(IllegalArgumentException error){prompt.setError(error.getMessage());}
            }));
        dialog.show();
    }

    private void showTaskManagement(){
        List<TaskCatalog.Task> tasks=TaskCatalog.all(this);
        String[] names=new String[tasks.size()+1];
        for(int i=0;i<tasks.size();i++)
            names[i]=(tasks.get(i).custom?"✦ ":"预设 · ")+tasks.get(i).name;
        names[tasks.size()]="＋ 新建自定义任务";
        new AlertDialog.Builder(this).setTitle("任务管理 · 编辑AI提示词")
            .setItems(names,(d,index)->{
                if(index==tasks.size()){editTask(null);return;}
                TaskCatalog.Task task=tasks.get(index);
                if(task.custom)showCustomTaskEditActions(task);
                else editPresetTask(task);
            }).setNegativeButton("关闭",null).show();
    }
    private void showCustomTaskEditActions(TaskCatalog.Task task){
        new AlertDialog.Builder(this).setTitle(task.name)
            .setItems(new String[]{"编辑任务、图标及提示词","删除任务"},(d,index)->{
                if(index==0)editTask(task);
                else new AlertDialog.Builder(this).setTitle("确认删除："+task.name)
                    .setNegativeButton("取消",null)
                    .setPositiveButton("删除",(yes,which)->{
                        TaskCatalog.remove(this,task.id);
                        refreshTask(TaskCatalog.selectedId(this));
                    }).show();
            }).show();
    }
    private void editPresetTask(TaskCatalog.Task task){
        EditText prompt=new EditText(this);
        prompt.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        prompt.setTextColor(Color.WHITE);
        prompt.setMinLines(4);prompt.setText(task.instruction);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(8),dp(20),dp(8));panel.addView(prompt);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("编辑："+task.name)
            .setMessage("保存后下次识别将使用这里的AI指令。")
            .setView(panel)
            .setNeutralButton("恢复默认",(d,w)->{
                TaskCatalog.restorePreset(this,task.id);
                refreshTask(currentTask.id);
                Toast.makeText(this,"已恢复："+task.name,Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(w->{
                try{
                    TaskCatalog.updatePreset(this,task.id,prompt.getText().toString());
                    refreshTask(currentTask.id);dialog.dismiss();
                    Toast.makeText(this,"提示词已保存",Toast.LENGTH_SHORT).show();
                }catch(IllegalArgumentException e){prompt.setError(e.getMessage());}
            }));
        dialog.show();
    }

    private void setResultStatus(String text){
        if(resultChip!=null)resultChip.setText(text);
    }

    private void addSimpleSettingsRow(LinearLayout panel,String label,String detail,Runnable clicked) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(15),dp(14),dp(15),dp(14));
        TextView title=new TextView(this);
        title.setText(label+"   ›");title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP,19);
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        row.addView(title);
        TextView description=new TextView(this);
        description.setText(detail);description.setTextColor(0xFFD2D3D7);
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP,15);
        description.setPadding(0,dp(4),0,0);
        row.addView(description);
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(0xFF202124);background.setCornerRadius(dp(11));
        row.setBackground(background);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin=dp(10);panel.addView(row,lp);
        row.setContentDescription(label+"："+detail+"；双击选择");
        row.setClickable(true);row.setFocusable(true);
        row.setOnClickListener(v->clicked.run());
    }

    private void showSettingsDialog(){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18),dp(20),dp(18),dp(12));
        panel.setBackgroundColor(0xFF0B1B30);
        TextView heading=new TextView(this);
        heading.setText("蓝星向导 · 设置");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP,25);
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading);
        addSimpleSettingsRow(panel,"语音与语速","本地TTS引擎、0.5–3倍语速、试听",
            ()->showSpeechSettings());
        addSimpleSettingsRow(panel,"任务管理","编辑五个预设提示词、新增与修改自定义任务",
            ()->{if(settingsDialog!=null)settingsDialog.dismiss();showTaskManagement();});
        addSimpleSettingsRow(panel,"摄像头设置","镜头来源、独立方向记忆、镜像、缩放",
            ()->showCameraOptions());
        addSimpleSettingsRow(panel,"提示音与振动","拍照叮叮提示和短振动，均可单独关闭",
            ()->showFeedbackSettings());
        addSimpleSettingsRow(panel,"云端AI","模型、分辨率、API Key及用完即停提醒",
            ()->showCloudOptions());
        addSimpleSettingsRow(panel,"上次识别结果","查看全文或再次朗读",
            ()->showLastResultDialog());
        TextView footer=new TextView(this);
        footer.setText("识别需要联网并可能消耗百炼Token；公开发布需服务端密钥与限额保护。");
        footer.setTextColor(0xFFD0E1F3);
        footer.setPadding(0,dp(9),0,0);panel.addView(footer);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(panel);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setView(scroll).setPositiveButton("完成",null).create();
        dialog.show();settingsDialog=dialog;
        if(dialog.getWindow()!=null)
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(20),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setOnDismissListener(d->{if(settingsDialog==dialog)settingsDialog=null;});
    }

    private void showSpeechSettings(){
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20),dp(12),dp(20),dp(12));
        Switch follow=new Switch(this);
        follow.setText("跟随语音引擎默认语速");
        follow.setTextColor(Color.WHITE);
        follow.setChecked(followSystemSpeechRate);
        form.addView(follow);
        TextView speed=new TextView(this);
        speed.setTextColor(Color.WHITE);speed.setTextSize(17);
        speed.setText("本地语速："+String.format(Locale.CHINA,"%.2f",speechRate)+"×");
        form.addView(speed);
        SeekBar seek=new SeekBar(this);seek.setMax(50);
        seek.setProgress(Math.round((speechRate-.5f)*20f));
        seek.setEnabled(!followSystemSpeechRate);
        form.addView(seek);
        follow.setOnCheckedChangeListener((view,isChecked)->{
            followSystemSpeechRate=isChecked;
            seek.setEnabled(!isChecked);
            persistSpeechRate();
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                if(!fromUser)return;
                speechRate=.5f+value*.05f;
                speed.setText("本地语速："+String.format(Locale.CHINA,"%.2f",speechRate)+"×");
                persistSpeechRate();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        new AlertDialog.Builder(this).setTitle("本地语音")
            .setView(form)
            .setNeutralButton("选择语音引擎",(d,w)->showInstalledTtsSelector())
            .setNegativeButton("关闭",null)
            .setPositiveButton("试听",(d,w)->speak("蓝星向导，当前语速试听。")).show();
    }
    private void persistSpeechRate(){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putFloat(PREF_SPEECH_RATE,speechRate)
            .putBoolean(PREF_SPEECH_DEFAULT,followSystemSpeechRate).apply();
        if(tts!=null&&ttsReady)
            tts.setSpeechRate(followSystemSpeechRate?1f:speechRate);
    }

    private void showFeedbackSettings(){
        String[] titles={"拍照叮叮提示音","拍照短振动"};
        boolean[] choices={feedbackSound,feedbackVibration};
        new AlertDialog.Builder(this).setTitle("提示与振动")
            .setMultiChoiceItems(titles,choices,(d,which,checked)->{
                if(which==0)feedbackSound=checked;else feedbackVibration=checked;
                getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                    .putBoolean(PREF_SOUND,feedbackSound)
                    .putBoolean(PREF_HAPTIC,feedbackVibration).apply();
            }).setPositiveButton("完成",null).show();
    }

    private void showCameraOptions(){
        String[] names={"切换摄像头","旋转画面（当前："+previewRotation+"°）",
            "水平镜像（"+(previewMirror?"开":"关")+"）",
            "垂直翻转（"+(previewVerticalFlip?"开":"关")+"）",
            "USB左右校正（"+(usbPreviewCorrectMirror?"开":"关")+"）",
            "缩放恢复1×"};
        new AlertDialog.Builder(this).setTitle("摄像头设置 · 各镜头分别记忆")
            .setItems(names,(d,i)->{
                if(i==0){showCameraSourceSelector();return;}
                if(i==1)previewRotation=(previewRotation+90)%360;
                else if(i==2)previewMirror=!previewMirror;
                else if(i==3)previewVerticalFlip=!previewVerticalFlip;
                else if(i==4)usbPreviewCorrectMirror=!usbPreviewCorrectMirror;
                else if(i==5)setCameraZoom(1f);
                saveCameraProfile();
                applyPreviewTransform();applyPhonePreviewTransform();
                setResultStatus("摄像头画面设置已保存");
                showCameraOptions();
            }).setNegativeButton("完成",null).show();
    }

    private void showCloudOptions(){
        String[] menu={"选择视觉AI模型（当前："+visionModel+"）",
            "选择照片分辨率","配置或清除 API Key","用完即停说明"};
        new AlertDialog.Builder(this).setTitle("云端 AI")
            .setItems(menu,(d,i)->{
                if(i==0)showVisionModelSelector();
                else if(i==1)showImagePolicySelector();
                else if(i==2)showCloudKeyDialog();
                else new AlertDialog.Builder(this).setTitle("费用保护")
                    .setMessage("务必在阿里云百炼对各模型确认免费额度并开启「用完即停」。所有照片仅在你主动点击时上传。")
                    .setPositiveButton("知道了",null).show();
            }).setNegativeButton("关闭",null).show();
    }

    private void showLastResultDialog(){
        if(lastCloudResult==null||lastCloudResult.isEmpty()){
            Toast.makeText(this,"尚无识别结果",Toast.LENGTH_SHORT).show();return;
        }
        new AlertDialog.Builder(this).setTitle("上次识别结果")
            .setMessage(lastCloudResult)
            .setPositiveButton("朗读",(d,w)->speak(lastCloudResult))
            .setNegativeButton("关闭",null).show();
    }

    /** Identical selection labels and saved preferences as 2.0. */
    private void showCameraSourceSelector() {
        final String[] choices={"USB 外接摄像头","手机后置摄像头","手机前置摄像头"};
        final int selected=usingPhoneCamera?(phoneFrontFacing?2:1):0;
        new AlertDialog.Builder(this).setTitle("摄像头")
            .setSingleChoiceItems(choices,selected,(dialog,which)->{
                dialog.dismiss();
                boolean phone=which!=0;
                boolean front=which==2;
                if(phone==usingPhoneCamera&&(!phone||front==phoneFrontFacing))return;
                if(settingsDialog!=null)settingsDialog.dismiss();
                saveCameraProfile();
                if(phone){
                    phoneFrontFacing=front;
                    getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                        .putBoolean(PREF_FRONT_CAMERA,front).apply();
                }
                switchCameraSource(phone);
            }).setNegativeButton("取消",null).show();
    }

    private void showInstalledTtsSelector() {
        if(tts==null){Toast.makeText(this,"语音引擎未初始化",Toast.LENGTH_SHORT).show();return;}
        List<TextToSpeech.EngineInfo> engines=tts.getEngines();
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        java.util.ArrayList<String> ids=new java.util.ArrayList<>();
        labels.add("跟随手机系统默认");ids.add("");
        for(TextToSpeech.EngineInfo e:engines){
            if(e==null||e.name==null)continue;
            labels.add(e.label==null?e.name:e.label);ids.add(e.name);
        }
        new AlertDialog.Builder(this).setTitle("选择本机已安装的语音引擎")
            .setSingleChoiceItems(labels.toArray(new String[0]),Math.max(0,ids.indexOf(ttsEngine)),
                (dialog,which)->{
                    dialog.dismiss();
                    ttsEngine=ids.get(which);
                    getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                        .putString(PREF_TTS_ENGINE,ttsEngine).apply();
                    initializeSpeech();
                    if(settingsDialog!=null)settingsDialog.dismiss();
                }).setNegativeButton("取消",null).show();
    }

    @Override protected IAspectRatio getCameraView() {
        cameraTextureView=new AspectRatioTextureView(this);
        cameraTextureView.setOnClickListener(v->captureOneFrame());
        cameraTextureView.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->applyPreviewTransform());
        return cameraTextureView;
    }
    @Override protected ViewGroup getCameraViewContainer() {return previewContainer;}
    @Override protected int getGravity(){return Gravity.CENTER;}
    @Override protected CameraRequest getCameraRequest(){
        return new CameraRequest.Builder()
            .setPreviewWidth(1920).setPreviewHeight(1080)
            .setRenderMode(CameraRequest.RenderMode.NORMAL)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setRawPreviewData(true).setAspectRatioShow(true).create();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        usingPhoneCamera=prefs.getBoolean(PREF_PHONE_CAMERA,false);
        phoneFrontFacing=prefs.getBoolean(PREF_FRONT_CAMERA,false);
        previewRotation=prefs.getInt(PREF_PREVIEW_ROTATION,0);
        previewMirror=prefs.getBoolean(PREF_PREVIEW_MIRROR,false);
        previewVerticalFlip=prefs.getBoolean(PREF_PREVIEW_FLIP,false);
        usbPreviewCorrectMirror=prefs.getBoolean(PREF_USB_PREVIEW_CORRECT_MIRROR,true);
        feedbackSound=prefs.getBoolean(PREF_SOUND,true);
        feedbackVibration=prefs.getBoolean(PREF_HAPTIC,true);
        speechRate=prefs.getFloat(PREF_SPEECH_RATE,1f);
        followSystemSpeechRate=prefs.getBoolean(PREF_SPEECH_DEFAULT,true);
        loadCameraProfile();
        imagePolicy=prefs.getString(PREF_IMAGE_POLICY,"auto");
        visionModel=prefs.getString(PREF_VISION_MODEL,"qwen3-vl-plus");
        ttsEngine=prefs.getString(PREF_TTS_ENGINE,"");
        if(ttsEngine==null)ttsEngine="";
        refreshTask(TaskCatalog.selectedId(this));
        attachPhonePreviewAfterUsbInit();
        applyPreviewTransform();
        phoneController=new InternalCameraController(this,phoneTexture,new InternalCameraController.Listener(){
            @Override public boolean shouldReceiveFrame() {
                return uiActive && usingPhoneCamera &&
                    (latestPhoneFrame==null ||
                     SystemClock.elapsedRealtime()-latestPhoneFrame.atMs>=220L);
            }
            @Override public void onFrame(byte[] nv21,int w,int h){
                if(usingPhoneCamera)updateFrame(nv21,w,h,true);
            }
            @Override public void onOpened(int rawW,int rawH,int sensor,int correction) {
                phoneRawWidth=rawW;phoneRawHeight=rawH;
                phoneSensorNaturalRotation=sensor;phoneOcrRotation=correction;
                runOnUiThread(this::applyFromCallback);
            }
            private void applyFromCallback(){applyPhonePreviewTransform();}
            @Override public void onPreviewStarted(){
                runOnUiThread(()->{
                    if(!usingPhoneCamera)return;
                    if(phoneController!=null)phoneController.setZoom(zoom);
                    phonePreviewReceived=true;
                    if(phonePreviewStatus!=null)phonePreviewStatus.setVisibility(View.GONE);
                });
            }
            @Override public void onError(String message){
                runOnUiThread(()->{
                    if(usingPhoneCamera)showPhonePreviewStatus("手机摄像头启动失败："+message,true);
                });
            }
        });
        initializeSpeech();
        if(usingPhoneCamera){
            phoneTexture.setVisibility(View.VISIBLE);
            if(cameraTextureView!=null)cameraTextureView.setVisibility(View.INVISIBLE);
        }
        setResultStatus("点击画面识别 · 点击AI对话追问");
    }

    private void showImagePolicySelector(){
        String[] names={"自动：清晰720p／低细节原生1080p（推荐）",
            "固定720p（速度对照）","固定1080p（清晰度对照）"};
        String[] values={"auto","720","1080"};
        int selected="720".equals(imagePolicy)?1:("1080".equals(imagePolicy)?2:0);
        new AlertDialog.Builder(this).setTitle("图片分辨率实验")
            .setMessage("自动档只在原始相机画面足够大且细节较少时使用1080p，不放大小图，不会重复收费请求。")
            .setSingleChoiceItems(names,selected,(d,i)->{
                imagePolicy=values[i];getSharedPreferences(PREFS,MODE_PRIVATE)
                    .edit().putString(PREF_IMAGE_POLICY,imagePolicy).apply();
                d.dismiss();
                if(settingsDialog!=null)settingsDialog.dismiss();
                Toast.makeText(this,"已选："+names[i],Toast.LENGTH_SHORT).show();
            }).setNegativeButton("取消",null).show();
    }

    private void showVisionModelSelector(){
        String[] names={"qwen3-vl-plus（稳定优先）","qwen3-vl-flash（速度A/B实验）"};
        int selected="qwen3-vl-flash".equals(visionModel)?1:0;
        new AlertDialog.Builder(this).setTitle("视觉模型")
            .setMessage("切换为flash前，请确认百炼免费额度页面有该模型的额度且已开启用完即停；否则可能产生费用。OCR仍固定使用qwen-vl-ocr。")
            .setSingleChoiceItems(names,selected,(d,i)->{
                visionModel=i==1?"qwen3-vl-flash":"qwen3-vl-plus";
                getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                    .putString(PREF_VISION_MODEL,visionModel).apply();
                d.dismiss();
                if(settingsDialog!=null)settingsDialog.dismiss();
                Toast.makeText(this,"视觉模型："+visionModel,Toast.LENGTH_SHORT).show();
            }).setNegativeButton("取消",null).show();
    }

    private void showCloudKeyDialog() {
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setHint("粘贴百炼普通按量付费 API Key");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout layout=new LinearLayout(this);
        layout.setPadding(dp(20),dp(10),dp(20),0);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(input);
        new AlertDialog.Builder(this).setTitle("配置云端 API Key（北京地域）")
            .setMessage("密钥由 Android Keystore 加密后仅保存在本机；不会写入APK或GitHub。请勿截图密钥。")
            .setView(layout).setNegativeButton("取消",null)
            .setNeutralButton("清除已保存密钥",(d,w)->{
                CloudKeyStore.clear(this);
                Toast.makeText(this,"已清除本机API Key",Toast.LENGTH_SHORT).show();
            })
            .setPositiveButton("加密保存",(d,w)->{
                try {
                    CloudKeyStore.save(this,input.getText().toString());
                    input.setText("");
                    Toast.makeText(this,"API Key已安全保存",Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Toast.makeText(this,"密钥保存失败："+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private void initializeSpeech() {
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        ttsReady=false;
        try{
            TextToSpeech.OnInitListener listener=result->{
                ttsReady=result==TextToSpeech.SUCCESS;
                if(ttsReady&&tts!=null)
                    tts.setSpeechRate(followSystemSpeechRate?1f:speechRate);
            };
            tts=ttsEngine.isEmpty()?new TextToSpeech(this,listener):
                new TextToSpeech(this,listener,ttsEngine);
            // Neither callback proves the speaker has audibly played sound.
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String utteranceId){
                    recordTtsProgress(utteranceId,false);
                }
                @Override public void onDone(String utteranceId){}
                @Override public void onError(String utteranceId){}
                @Override public void onAudioAvailable(String utteranceId,byte[] audio){
                    if(audio!=null&&audio.length>0)recordTtsProgress(utteranceId,true);
                }
            });
        }catch(Exception error){Log.w(TAG,"TTS initialization unavailable",error);}
    }

    private void recordTtsProgress(String id,boolean audio){
        int token=speechRequestToken;
        if(id==null||!id.equals("vision-"+token+"-0")||
            token!=requestVersion.get()||speechCaptureAtMs<0)return;
        long ms=SystemClock.elapsedRealtime()-speechCaptureAtMs;
        if(audio){
            if(ttsAudioCallbackMs>=0)return;
            ttsAudioCallbackMs=ms;
        }else{
            if(ttsEngineStartMs>=0)return;
            ttsEngineStartMs=ms;
        }
        Log.i(TAG,"TTS timing: "+(audio?"firstAudioCallbackMs=":"engineOnStartMs=")+ms);
        handler.post(this::refreshCloudResultTtsStatus);
    }

    private String ttsTimingText(){
        return "TTS引擎启动 "+
            (ttsEngineStartMs<0?"未报告":(ttsEngineStartMs+"毫秒"))+"；"+
            "音频数据回调 "+(ttsAudioCallbackMs<0?"引擎未提供":(ttsAudioCallbackMs+"毫秒"))+
            "\n（TTS回调不等于扬声器真正发声）";
    }

    private void refreshCloudResultTtsStatus(){
        if(cloudResultDialog!=null&&cloudResultDialog.isShowing()&&
            cloudResultToken==requestVersion.get()&&
            currentTask!=null&&!TaskCatalog.OCR.equals(currentTask.engine))
            cloudResultDialog.setMessage(cloudResultInfo+
                ttsTimingText()+"\n\n"+cloudResultText);
    }

    private void speak(String message) {
        if(tts!=null&&ttsReady)
            tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"cloud-probe-"+System.nanoTime());
        else Toast.makeText(this,"本机语音尚未就绪："+message,Toast.LENGTH_LONG).show();
    }

    /** Preserve camera/source switching and attachment order from 2.0. */
    private void switchCameraSource(boolean phone) {
        invalidate();
        usingPhoneCamera=phone;
        latestPhoneFrame=null;
        latestUsbFrame=null;
        loadCameraProfile();
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean(PREF_PHONE_CAMERA,phone).apply();
        phoneStartSequence++;
        if(phoneController!=null)phoneController.stop();
        if(phoneTexture!=null)phoneTexture.setVisibility(phone?View.VISIBLE:View.GONE);
        if(cameraTextureView!=null)cameraTextureView.setVisibility(phone?View.INVISIBLE:View.VISIBLE);
        if(phone){
            if(phoneController!=null)phoneController.setZoom(zoom);
            startPhoneCamera();
        }
        else{
            phonePreviewReceived=false;
            if(phonePreviewStatus!=null)phonePreviewStatus.setVisibility(View.GONE);
            applyPreviewTransform();
            handler.post(this::resumeUsb);
        }
        String message=phone?(phoneFrontFacing?"已切换手机前置摄像头":"已切换手机后置摄像头"):
            "已切换USB外接摄像头";
        if(settingsButton!=null)settingsButton.announceForAccessibility(message);
    }

    private void attachPhonePreviewAfterUsbInit() {
        if(previewContainer==null||phoneTexture==null)return;
        if(phoneTexture.getParent() instanceof ViewGroup)
            ((ViewGroup)phoneTexture.getParent()).removeView(phoneTexture);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT,Gravity.CENTER);
        previewContainer.addView(phoneTexture,params);
        phoneTexture.setVisibility(View.GONE);
    }

    private void showPhonePreviewStatus(String message,boolean speakError) {
        if(!usingPhoneCamera||phonePreviewStatus==null)return;
        phonePreviewStatus.setText(message);
        phonePreviewStatus.setVisibility(View.VISIBLE);
        if(speakError) {
            phonePreviewStatus.announceForAccessibility(message);
            Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        }
    }

    private void startPhoneCamera() {
        if(!usingPhoneCamera||phoneController==null||phoneTexture==null||!uiActive)return;
        if(!phoneController.hasPermission()){
            showPhonePreviewStatus("请允许蓝星向导使用手机摄像头",false);
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_PHONE_CAMERA);
            return;
        }
        phoneTexture.setVisibility(View.VISIBLE);
        phoneTexture.bringToFront();
        if(cameraTextureView!=null)cameraTextureView.setVisibility(View.INVISIBLE);
        phonePreviewReceived=false;
        showPhonePreviewStatus(phoneFrontFacing?"正在打开手机前置摄像头…":"正在打开手机后置摄像头…",false);
        final int sequence=++phoneStartSequence;
        handler.postDelayed(()->{
            if(sequence!=phoneStartSequence||!usingPhoneCamera||phonePreviewReceived)return;
            String detail=phoneTexture.getParent()==null?"预览控件未挂载":
                !phoneTexture.isAttachedToWindow()?"预览控件尚未连接到窗口":
                !phoneTexture.isAvailable()?"预览SurfaceTexture未就绪":"摄像头没有产生预览画面";
            showPhonePreviewStatus("手机摄像头启动超时："+detail,true);
        },5500L);
        phoneTexture.post(()->{
            if(!usingPhoneCamera||phoneController==null||sequence!=phoneStartSequence)return;
            if(phoneTexture.getParent()!=previewContainer){
                attachPhonePreviewAfterUsbInit();
                phoneTexture.setVisibility(View.VISIBLE);
            }
            if(!phoneTexture.isAttachedToWindow()){
                phoneTexture.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                    @Override public void onViewAttachedToWindow(View v){
                        v.removeOnAttachStateChangeListener(this);
                        if(sequence==phoneStartSequence&&usingPhoneCamera)
                            phoneController.start(phoneFrontFacing);
                    }
                    @Override public void onViewDetachedFromWindow(View v){}
                });
                return;
            }
            phoneController.start(phoneFrontFacing);
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQUEST_VOICE_MIC){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)
                beginVoiceListening();
            else Toast.makeText(this,"请授予麦克风权限，才能语音追问",Toast.LENGTH_LONG).show();
            return;
        }
        if(requestCode!=REQUEST_PHONE_CAMERA)return;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED) {
            if(usingPhoneCamera) startPhoneCamera();
            else {
                // USB permission may have arrived before CAMERA runtime permission.
                MultiCameraClient.ICamera device=getCurrentCamera();
                if(device!=null && cameraTextureView!=null && cameraTextureView.isAvailable()
                    && !isCameraOpened()) {
                    UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);
                    if(manager!=null && manager.hasPermission(device.getUsbDevice()))
                        device.openCamera(cameraTextureView,getCameraRequest());
                }
            }
        } else Toast.makeText(this,"请允许相机权限后使用摄像头",Toast.LENGTH_LONG).show();
    }

    private void applyPhonePreviewTransform(){
        if(phoneTexture==null||!usingPhoneCamera)return;
        int viewW=phoneTexture.getWidth(),viewH=phoneTexture.getHeight();
        if(viewW<=0||viewH<=0||phoneRawWidth<=0||phoneRawHeight<=0)return;
        int displayDegrees=getWindowManager().getDefaultDisplay().getRotation()*90;
        int angle=PhonePreviewGeometry.rotation(previewRotation,-displayDegrees);
        PhonePreviewGeometry.Layout fit=PhonePreviewGeometry.camera2Preview(
            viewW,viewH,phoneRawWidth,phoneRawHeight,
            phoneSensorNaturalRotation,angle,previewMirror,previewVerticalFlip);
        Matrix transform=new Matrix();
        transform.setValues(fit.textureMatrix);
        phoneTexture.setTransform(transform);
    }

    private void applyPreviewTransform(){
        if(cameraTextureView==null||previewContainer==null)return;
        int cw=previewContainer.getWidth(),ch=previewContainer.getHeight();
        if(cw<=0||ch<=0)return;
        boolean quarterTurn=previewRotation==90||previewRotation==270;
        PreviewSize actual=previewCallbackAdded?getCurrentPreviewSize():null;
        float aspect=actual!=null&&actual.getHeight()>0?
            actual.getWidth()/(float)actual.getHeight():16f/9f;
        float wantedAspect=quarterTurn?1f/aspect:aspect;
        float shownW=Math.min(cw,ch*wantedAspect);
        float shownH=shownW/wantedAspect;
        int wantedW=Math.max(1,Math.round(quarterTurn?shownH:shownW));
        int wantedH=Math.max(1,Math.round(quarterTurn?shownW:shownH));
        ViewGroup.LayoutParams raw=cameraTextureView.getLayoutParams();
        if(raw instanceof FrameLayout.LayoutParams){
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)raw;
            if(lp.width!=wantedW||lp.height!=wantedH||lp.gravity!=Gravity.CENTER){
                lp.width=wantedW;lp.height=wantedH;lp.gravity=Gravity.CENTER;
                cameraTextureView.setLayoutParams(lp);
            }
        }
        cameraTextureView.setRotation(previewRotation);
        int vw=cameraTextureView.getWidth(),vh=cameraTextureView.getHeight();
        cameraTextureView.setPivotX(vw/2f);cameraTextureView.setPivotY(vh/2f);
        cameraTextureView.setScaleX(zoom);
        cameraTextureView.setScaleY(zoom*(previewVerticalFlip?-1f:1f));
        if(vw>0&&vh>0){
            Matrix matrix=new Matrix();
            if(usbPreviewCorrectMirror^previewMirror)
                matrix.setScale(-1f,1f,vw*.5f,vh*.5f);
            cameraTextureView.setTransform(matrix);
        }
        // No USB vivid/saturation effects in official edition.
    }

    /**
     * Profiles are isolated by built-in front/back and USB VID/PID.
     * The test UVC model EBA4:6579 is known to be mounted sideways: default 90°.
     * Users can recalibrate any device from the settings menu.
     */
    private String cameraProfilePrefix(){
        if(usingPhoneCamera)return phoneFrontFacing?"camera_phone_front_":"camera_phone_back_";
        UsbDevice device=null;
        MultiCameraClient.ICamera current=getCurrentCamera();
        if(current!=null)device=current.getUsbDevice();
        if(device==null)device=getDefaultCamera();
        if(device==null){
            UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);
            if(manager!=null&&!manager.getDeviceList().isEmpty())
                device=manager.getDeviceList().values().iterator().next();
        }
        if(device==null)return "camera_usb_default_";
        return "camera_usb_"+device.getVendorId()+"_"+device.getProductId()+"_";
    }

    private void loadCameraProfile(){
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        String id=cameraProfilePrefix();
        boolean knownUsb=id.equals("camera_usb_60324_25977_"); // EBA4:6579
        int def=knownUsb?90:0;
        // Legacy single rotation applies to USB only when no profile exists.
        if(!p.contains(id+"rotation")&&!usingPhoneCamera&&p.contains(PREF_PREVIEW_ROTATION))
            def=p.getInt(PREF_PREVIEW_ROTATION,def);
        previewRotation=p.getInt(id+"rotation",def);
        previewMirror=p.getBoolean(id+"mirror",false);
        previewVerticalFlip=p.getBoolean(id+"flip",false);
        usbPreviewCorrectMirror=p.getBoolean(id+"usbCorrectMirror",true);
        zoom=Math.max(1f,Math.min(3f,p.getFloat(id+"zoom",1f)));
        if(zoomChip!=null)zoomChip.setText(String.format(Locale.CHINA,"%.1f×",zoom));
    }

    private void saveCameraProfile(){
        String prefix=cameraProfilePrefix();
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putInt(prefix+"rotation",previewRotation)
            .putBoolean(prefix+"mirror",previewMirror)
            .putBoolean(prefix+"flip",previewVerticalFlip)
            .putBoolean(prefix+"usbCorrectMirror",usbPreviewCorrectMirror)
            .putFloat(prefix+"zoom",zoom).apply();
    }

    private void setCameraZoom(float next){
        float limit=3f;
        if(usingPhoneCamera&&phoneController!=null)
            limit=Math.max(1f,Math.min(3f,phoneController.zoomLimit()));
        float applied=Math.max(1f,Math.min(limit,next));
        if(Math.abs(applied-zoom)<.01f)return;
        zoom=applied;
        if(zoomChip!=null)zoomChip.setText(String.format(Locale.CHINA,"%.1f×",zoom));
        if(usingPhoneCamera&&phoneController!=null)phoneController.setZoom(zoom);
        else applyPreviewTransform();
        saveCameraProfile();
    }

    @Override protected UsbDevice getDefaultCamera(){
        UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);
        if(manager==null)return null;
        Map<String,UsbDevice> devices=manager.getDeviceList();
        for(UsbDevice device:devices.values())
            if(device.getVendorId()==0xEBA4&&device.getProductId()==0x6579)return device;
        return null;
    }

    @Override public void onCameraState(MultiCameraClient.ICamera self,
                                        ICameraStateCallBack.State code,String msg){
        if(code==ICameraStateCallBack.State.OPENED){
            usbOpenedAtMs=SystemClock.elapsedRealtime();
            usbStopPending=false;usbReopenWanted=false;
            if(!previewCallbackAdded){
                addPreviewDataCallBack(previewCallback);
                previewCallbackAdded=true;
            }
            handler.post(()->{
                if(!usingPhoneCamera){loadCameraProfile();applyPreviewTransform();}
            });
        }else if(code==ICameraStateCallBack.State.CLOSED){
            previewCallbackAdded=false;
            latestUsbFrame=null;
            if(usbStopPending){
                usbStopPending=false;
                if(usbForeground&&usbReopenWanted)
                    handler.post(this::resumeUsb);
            }
        }else if(code==ICameraStateCallBack.State.ERROR){
            Log.w(TAG,"UVC open error: "+msg);
            runOnUiThread(()->Toast.makeText(this,"USB打开失败："+msg,Toast.LENGTH_LONG).show());
        }
    }

    private void stopUsb(){
        if(usbStopPending||!isCameraOpened())return;
        MultiCameraClient.ICamera camera=getCurrentCamera();
        if(camera==null)return;
        usbStopPending=true;
        usbReopenWanted=true;
        camera.closeCamera();
    }
    private void resumeUsb(){
        if(!usbForeground||!usbReopenWanted||usbStopPending||usingPhoneCamera||
           cameraTextureView==null||!cameraTextureView.isAvailable()||isCameraOpened())return;
        MultiCameraClient.ICamera camera=getCurrentCamera();
        if(camera==null||camera.getUsbDevice()==null)return;
        UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);
        if(manager==null||!manager.hasPermission(camera.getUsbDevice()))return;
        usbReopenWanted=false;
        camera.openCamera(cameraTextureView,getCameraRequest());
    }

    private void signalCapture(){
        if(feedbackSound){
            try{
                ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,60);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP,75);
                handler.postDelayed(()->{
                    try{tone.startTone(ToneGenerator.TONE_PROP_BEEP,80);}
                    finally{handler.postDelayed(tone::release,130);}
                },135L);
            }catch(RuntimeException error){Log.w(TAG,"Capture tone unavailable");}
        }
        if(feedbackVibration){
            try{
                Vibrator vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
                if(vibrator!=null&&vibrator.hasVibrator())
                    vibrator.vibrate(VibrationEffect.createOneShot(45,VibrationEffect.DEFAULT_AMPLITUDE));
            }catch(RuntimeException error){Log.w(TAG,"Capture vibration unavailable");}
        }
    }

    /** One tap initiates native Android ASR; microphone is never left listening. */
    private void startVoiceFollowup(){
        if(cloudBusy){Toast.makeText(this,"请等当前AI回答完成",Toast.LENGTH_SHORT).show();return;}
        if(lastSessionJpeg==null||lastSessionText.isEmpty()){
            speak("请先点击画面拍摄并识别一张照片。");return;
        }
        if(voiceListening){
            if(speechRecognizer!=null)speechRecognizer.cancel();
            voiceListening=false;setResultStatus("已取消录音");return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_VOICE_MIC);
            return;
        }
        beginVoiceListening();
    }

    private void beginVoiceListening(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){
            showTextQuestionFallback("未发现手机语音识别服务，可输入问题继续追问。");
            return;
        }
        if(tts!=null)tts.stop();
        try{
            if(speechRecognizer!=null){speechRecognizer.destroy();speechRecognizer=null;}
            speechRecognizer=SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle params){
                    setResultStatus("正在听你说话…");
                }
                @Override public void onBeginningOfSpeech(){setResultStatus("正在收音…");}
                @Override public void onRmsChanged(float rmsdB){}
                @Override public void onBufferReceived(byte[] buffer){}
                @Override public void onEndOfSpeech(){setResultStatus("正在转换语音…");}
                @Override public void onError(int error){
                    voiceListening=false;
                    setResultStatus("语音识别未成功，请重试");
                    if(error==SpeechRecognizer.ERROR_CLIENT||
                       error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS||
                       error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                        showTextQuestionFallback("语音识别暂不可用，可手动输入问题。");
                }
                @Override public void onResults(Bundle results){
                    voiceListening=false;
                    ArrayList<String> answers=results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                    String question=answers==null||answers.isEmpty()?"":answers.get(0).trim();
                    if(question.isEmpty()){
                        setResultStatus("未听清，请再点AI对话");return;
                    }
                    askAboutCurrentImage(question);
                }
                @Override public void onPartialResults(Bundle partial){}
                @Override public void onEvent(int eventType,Bundle params){}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            voiceListening=true;
            setResultStatus("请说话，结束后自动发送");
            speechRecognizer.startListening(intent);
        }catch(RuntimeException error){
            voiceListening=false;
            setResultStatus("无法启动语音识别");
            showTextQuestionFallback("手机语音服务启动失败，可以输入问题。");
        }
    }

    private void showTextQuestionFallback(String reason){
        EditText question=new EditText(this);
        question.setSingleLine(false);
        question.setTextColor(Color.WHITE);
        question.setHint("请输入关于上一张照片的问题");
        LinearLayout content=new LinearLayout(this);
        content.setPadding(dp(22),dp(10),dp(22),dp(4));
        content.addView(question);
        new AlertDialog.Builder(this).setTitle("AI对话")
            .setMessage(reason).setView(content).setNegativeButton("取消",null)
            .setPositiveButton("发送",(d,w)->{
                String text=question.getText().toString().trim();
                if(!text.isEmpty())askAboutCurrentImage(text);
            }).show();
    }

    private void askAboutCurrentImage(String question){
        if(cloudBusy||lastSessionJpeg==null||lastSessionText.isEmpty())return;
        // Always include the actual image: earlier short summary may omit the
        // details needed for follow-up. The cloud does not remember prior calls.
        final byte[] jpeg=lastSessionJpeg;
        final String observed=lastSessionText;
        final String taskName=lastSessionTask;
        final StringBuilder prompt=new StringBuilder();
        prompt.append("你正在回答用户对上一张照片的追问。照片之前的任务：")
            .append(taskName).append("。此前识别结果：")
            .append(observed.length()>2500?observed.substring(0,2500):observed)
            .append("。只依据照片中的真实内容回答，不猜测看不清的信息。");
        for(String prior:followupTurns)prompt.append("此前对话：").append(prior).append("。");
        prompt.append("用户最新问题：").append(question)
            .append("。请直接给出清晰的中文回答，不要Markdown。");
        final TaskCatalog.Task ask=new TaskCatalog.Task(
            "user-followup","图片追问",prompt.toString(),TaskCatalog.VISION,true,"✦");
        invalidate();
        final int token=requestVersion.get();
        cloudBusy=true;
        setResultStatus("AI正在回答图片追问…");
        final java.util.concurrent.atomic.AtomicInteger pieces=new java.util.concurrent.atomic.AtomicInteger();
        encodingTask=encoder.submit(()->{
            try{
                String key=CloudKeyStore.load(this);
                if(key.isEmpty())throw new Exception("请先在设置中配置API Key");
                CloudVisionClient.Result result=CloudVisionClient.recognize(
                    jpeg,key,ask,visionModel,part->handler.post(()->{
                        if(token!=requestVersion.get()||!uiActive||tts==null||!ttsReady)return;
                        int partIndex=pieces.getAndIncrement();
                        tts.speak(part,partIndex==0?TextToSpeech.QUEUE_FLUSH:
                            TextToSpeech.QUEUE_ADD,null,"follow-"+token+"-"+partIndex);
                    }));
                handler.post(()->{
                    if(!uiActive||token!=requestVersion.get())return;
                    cloudBusy=false;
                    setResultStatus("AI追问已回答 · 再点可继续提问");
                    lastCloudResult=result.text;
                    followupTurns.add("问："+question+"，答："+result.text);
                    while(followupTurns.size()>3)followupTurns.remove(0);
                });
            }catch(Exception ex){
                handler.post(()->{
                    if(!uiActive||token!=requestVersion.get())return;
                    cloudBusy=false;
                    setResultStatus("AI对话失败");
                    Toast.makeText(this,"追问失败："+ex.getMessage(),Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void invalidate(){
        requestVersion.incrementAndGet();
        cloudBusy=false;
        if(encodingTask!=null){encodingTask.cancel(true);encodingTask=null;}
        if(tts!=null)tts.stop();
    }

    private void captureOneFrame(){
        if(cloudBusy){Toast.makeText(this,"正在识别，请稍候",Toast.LENGTH_SHORT).show();return;}
        long now=SystemClock.elapsedRealtime();
        if(now-lastPinchMs<550L)return;
        if(!uiActive)return;
        if(now-lastTapMs<1500L){
            Toast.makeText(this,"已忽略连续误触",Toast.LENGTH_SHORT).show();return;
        }
        Frame current=usingPhoneCamera?latestPhoneFrame:latestUsbFrame;
        if(current==null||now-current.atMs>1400L){
            Toast.makeText(this,"没有最新摄像头画面，不会使用旧照片",Toast.LENGTH_LONG).show();
            return;
        }
        lastTapMs=now;
        invalidate();
        lastSessionJpeg=null;lastSessionText="";lastSessionTask="";
        lastCloudResult="";followupTurns.clear();
        signalCapture();
        setResultStatus("已拍照 · 正在云端识别…");
        cloudBusy=true;
        int token=requestVersion.get();
        speechRequestToken=token;
        speechCaptureAtMs=now;
        ttsEngineStartMs=-1;
        ttsAudioCallbackMs=-1;
        TaskCatalog.Task task=currentTask;
        String source=usingPhoneCamera?(phoneFrontFacing?"手机前置":"手机后置"):"USB 外接";
        String policy=imagePolicy;
        String model=visionModel;
        int photoRotation=previewRotation;
        boolean photoMirror=usingPhoneCamera?previewMirror:(usbPreviewCorrectMirror^previewMirror);
        boolean photoFlip=previewVerticalFlip;
        float photoZoom=usingPhoneCamera?1f:zoom;
        encodingTask=encoder.submit(()->{
            long takenAt=SystemClock.elapsedRealtime();
            try{
                FrameCompressor.Plan plan=FrameCompressor.choose(
                    current.pixels,current.width,current.height,policy);
                byte[] jpeg=FrameCompressor.encodeAdjustedJpeg(
                    current.pixels,current.width,current.height,plan.limit,
                    photoRotation,photoMirror,photoFlip,photoZoom);
                long compressedAt=SystemClock.elapsedRealtime();
                long encodingMs=compressedAt-takenAt;
                if(token!=requestVersion.get())return;
                String key=CloudKeyStore.load(this);
                if(key.isEmpty()){
                    handler.post(()->{
                        if(token!=requestVersion.get()||!uiActive)return;
                        cloudBusy=false;
                        showCloudKeyDialog();
                    });
                    return;
                }

                java.util.concurrent.atomic.AtomicInteger segments=new java.util.concurrent.atomic.AtomicInteger(0);
                CloudVisionClient.Result result=CloudVisionClient.recognize(jpeg,key,task,model,
                    part->handler.post(()->{
                        if(token!=requestVersion.get()||!uiActive||tts==null||!ttsReady)return;
                        int partIndex=segments.getAndIncrement();
                        int mode=partIndex==0?
                            TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD;
                        tts.speak(part,mode,null,"vision-"+token+"-"+partIndex);
                    }));
                long overallMs=SystemClock.elapsedRealtime()-takenAt;
                handler.post(()->{
                    if(token!=requestVersion.get()||!uiActive)return;
                    cloudBusy=false;
                    lastCloudResult=result.text;
                    Log.i(TAG,"Cloud timing: route="+task.engine+" model="+result.model+
                        " capture="+source+" raw="+current.width+"x"+current.height+
                        " imageLimit="+plan.limit+" jpegBytes="+jpeg.length+
                        " sharpnessEdgeRatio="+plan.edgeRatio+" jpegMs="+encodingMs+
                        " requestMs="+result.networkMs+" firstTextMs="+result.firstTextMs+
                        " firstSpeakableMs="+result.firstSpeakableMs+
                        " promptTokens="+result.promptTokens+
                        " completionTokens="+result.completionTokens);
                    String first=result.firstSpeakableMs>=0?
                        ("首句就绪 "+result.firstSpeakableMs+"毫秒；"):"";
                    String diagnostic="本机编码 "+encodingMs+"毫秒；"+
                        first+"完整响应 "+result.networkMs+"毫秒；"+
                        "总计 "+overallMs+"毫秒\n"+
                        "模型 "+result.model+"；发送 "+jpeg.length/1024+"KB，"+
                        (plan.limit==1920?"1080p":"720p")+"（"+plan.decision+"）"+
                        (result.completionTokens>=0?"；输出Token "+result.completionTokens:"")+
                        "\n\n";
                    // Never show a modal dialog over the camera after recognition:
                    // a single click on the middle AI button can follow up immediately.
                    lastSessionJpeg=jpeg;
                    lastSessionText=result.text;
                    lastSessionTask=task.name;
                    lastCloudResult=result.text;
                    setResultStatus("已识别："+task.name+" · 点击AI对话可追问");
                    if(TaskCatalog.OCR.equals(task.engine))speak(result.text);
                });
            }catch(Exception e){
                handler.post(()->{
                    if(token==requestVersion.get()&&uiActive){
                        cloudBusy=false;
                        setResultStatus("识别失败，请重试");
                        new AlertDialog.Builder(this).setTitle("云端识别失败")
                            .setMessage(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())
                            .setPositiveButton("知道了",null).show();
                    }
                });
            }
        });
    }

    @Override protected void onResume(){
        super.onResume();
        uiActive=true;usbForeground=true;
        handler.post(this::resumeUsb);
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_PHONE_CAMERA);
        else if(usingPhoneCamera)startPhoneCamera();
    }
    @Override protected void onPause(){
        uiActive=false;usbForeground=false;
        if(speechRecognizer!=null){speechRecognizer.cancel();voiceListening=false;}
        invalidate();
        cloudBusy=false;
        stopUsb();
        phoneStartSequence++;
        if(phoneController!=null)phoneController.stop();
        latestPhoneFrame=null;
        super.onPause();
    }
    @Override protected void onDestroy(){
        invalidate();
        if(phoneController!=null)phoneController.stop();
        encoder.shutdownNow();
        if(speechRecognizer!=null){speechRecognizer.destroy();speechRecognizer=null;}
        lastSessionJpeg=null;
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        super.onDestroy();
    }
}
