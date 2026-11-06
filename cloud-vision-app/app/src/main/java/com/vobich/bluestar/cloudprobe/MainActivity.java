package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.RenderEffect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.TextureView;
import android.widget.Button;
import android.widget.EditText;
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
    private static final String PREF_USB_PREVIEW_VIVID = "usb_preview_vivid_experiment";
    private static final String PREF_TTS_ENGINE = "app_tts_engine_package";
    private static final int REQUEST_PHONE_CAMERA = 4051;

    private FrameLayout previewContainer;
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
    private boolean usbPreviewVivid=true;
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

    /**
     * Exactly the 2.0 full-screen display scaffold: full bleed image,
     * floating dark three-button dock, no separate scrolling camera UI.
     */
    @Override protected View getRootView(LayoutInflater inflater) {
        FrameLayout root = new FrameLayout(this);
        root.setPadding(0,0,0,0);
        root.setBackgroundColor(Color.BLACK);

        previewContainer = new FrameLayout(this);
        previewContainer.setBackgroundColor(Color.BLACK);
        previewContainer.setClipChildren(true);
        previewContainer.setClipToPadding(true);
        root.addView(previewContainer,new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        phoneTexture = new TextureView(this);
        phoneTexture.setOpaque(false);
        phoneTexture.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(l!=ol||t!=ot||r!=or||b!=ob) applyPhonePreviewTransform();
        });
        phoneTexture.setVisibility(View.GONE);
        // Must attach only AFTER CameraActivity.initView() removeAllViews()!
        phonePreviewStatus = new TextView(this);
        phonePreviewStatus.setTextColor(Color.WHITE);
        phonePreviewStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);
        phonePreviewStatus.setGravity(Gravity.CENTER);
        phonePreviewStatus.setPadding(dp(20),dp(10),dp(20),dp(10));
        phonePreviewStatus.setBackgroundColor(0xA9000000);
        phonePreviewStatus.setVisibility(View.GONE);
        phonePreviewStatus.setClickable(false);
        root.addView(phonePreviewStatus,new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(104),Gravity.CENTER));

        // Original 2.0 overlay catches fullscreen taps while leaving video behind it.
        View tapOverlay = new View(this);
        tapOverlay.setContentDescription("点击取图，执行当前任务");
        tapOverlay.setOnClickListener(v->captureOneFrame());
        root.addView(tapOverlay,new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4),dp(4),dp(4),dp(4));
        controls.setBackgroundColor(0xAA151515);
        modeButton = createControlButton("任务：朗读文字");
        controls.addView(modeButton,new LinearLayout.LayoutParams(0,dp(54),1f));
        startButton = createControlButton("点击拍照");
        controls.addView(startButton,new LinearLayout.LayoutParams(0,dp(54),1f));
        settingsButton = createControlButton("设置");
        controls.addView(settingsButton,new LinearLayout.LayoutParams(0,dp(54),1f));
        root.addView(controls,new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(62),Gravity.BOTTOM));
        modeButton.setOnClickListener(v->showTaskSelector());
        startButton.setOnClickListener(v->captureOneFrame());
        settingsButton.setOnClickListener(v->showSettingsDialog());
        return root;
    }

    private int dp(int d) {return Math.round(d*getResources().getDisplayMetrics().density);}

    private Button createControlButton(String label) {
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,12);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(0);
        b.setMinimumHeight(dp(48));
        b.setPadding(dp(2),0,dp(2),0);
        b.setBackgroundTintList(ColorStateList.valueOf(0xFF303030));
        return b;
    }

    private void refreshTask(String id) {
        currentTask=null;
        for (TaskCatalog.Task task:TaskCatalog.all(this))
            if(task.id.equals(id)){currentTask=task;break;}
        if(currentTask==null)currentTask=TaskCatalog.all(this).get(0);
        TaskCatalog.select(this,currentTask.id);
        if(modeButton!=null) {
            String label="任务："+currentTask.name;
            modeButton.setText(label);
            modeButton.setContentDescription(label+"；点击选择或新建任务");
        }
    }

    private void showTaskSelector() {
        List<TaskCatalog.Task> list=TaskCatalog.all(this);
        String[] names=new String[list.size()+1];
        int selected=-1;
        for(int i=0;i<list.size();i++){
            names[i]=list.get(i).name;
            if(currentTask!=null&&currentTask.id.equals(list.get(i).id))selected=i;
        }
        names[list.size()]="＋ 自定义任务";
        new AlertDialog.Builder(this).setTitle("选择任务")
            .setSingleChoiceItems(names,selected,(dialog,which)->{
                dialog.dismiss();
                if(which==list.size()){editTask(null);return;}
                invalidate();
                refreshTask(list.get(which).id);
                if(currentTask.custom) showCustomTaskActions();
                else Toast.makeText(this,"已选择："+currentTask.name,Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消",null).show();
    }

    private void showCustomTaskActions() {
        TaskCatalog.Task task=currentTask;
        new AlertDialog.Builder(this).setTitle(task.name)
            .setItems(new String[]{"执行此任务","编辑任务","删除任务"},
                (dialog,which)->{
                    if(which==1)editTask(task);
                    else if(which==2)new AlertDialog.Builder(this).setTitle("删除自定义任务？")
                        .setNegativeButton("取消",null)
                        .setPositiveButton("删除",(d,b)->{
                            invalidate();TaskCatalog.remove(this,task.id);refreshTask("ocr");
                        }).show();
                }).show();
    }

    private void editTask(TaskCatalog.Task original) {
        EditText name=new EditText(this);
        name.setSingleLine(true);
        name.setHint("任务名称");
        name.setText(original==null?"":original.name);
        EditText instruction=new EditText(this);
        instruction.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        instruction.setMinLines(3);
        instruction.setHint("请输入希望AI看图完成的具体任务");
        instruction.setText(original==null?"":original.instruction);
        LinearLayout form=new LinearLayout(this);
        form.setPadding(dp(18),dp(10),dp(18),dp(3));
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(name);form.addView(instruction);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(original==null?"新建自定义任务":"编辑自定义任务")
            .setView(form).setNegativeButton("取消",null)
            .setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(w->{
                try{
                    TaskCatalog.Task saved=TaskCatalog.upsert(this,
                        original==null?null:original.id,
                        name.getText().toString(),instruction.getText().toString());
                    invalidate();refreshTask(saved.id);dialog.dismiss();
                }catch(IllegalArgumentException error){instruction.setError(error.getMessage());}
            }));
        dialog.show();
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

    private void showSettingsDialog() {
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22),dp(23),dp(22),dp(12));
        panel.setBackgroundColor(0xFF0B0B0D);
        TextView heading=new TextView(this);
        heading.setText("设置");heading.setTextSize(TypedValue.COMPLEX_UNIT_SP,26);
        heading.setTextColor(Color.WHITE);heading.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);panel.addView(heading);

        addSimpleSettingsRow(panel,"语音",ttsEngine.isEmpty()?"跟随手机默认语音":ttsEngine,
            ()->showInstalledTtsSelector());
        addSimpleSettingsRow(panel,"摄像头",usingPhoneCamera
            ?(phoneFrontFacing?"手机前置摄像头":"手机后置摄像头"):"USB 外接摄像头",
            ()->showCameraSourceSelector());
        addSimpleSettingsRow(panel,"任务",currentTask.name,
            ()->{if(settingsDialog!=null)settingsDialog.dismiss();showTaskSelector();});
        addSimpleSettingsRow(panel,"识别服务","云端OCR / 云端VL（本测试版尚未连接）",
            ()->Toast.makeText(this,"此版本只验证拍照和任务操作，未向云端上传任何图片",Toast.LENGTH_LONG).show());
        TextView preview= new TextView(this);
        preview.setText("▶ 试听当前语音");
        preview.setGravity(Gravity.CENTER);preview.setTextSize(18);
        preview.setTextColor(0xFF151515);preview.setTypeface(null,android.graphics.Typeface.BOLD);
        preview.setPadding(dp(12),dp(15),dp(12),dp(15));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF66B4EA);bg.setCornerRadius(dp(12));preview.setBackground(bg);
        preview.setOnClickListener(v->speak("蓝星向导语音试听。"));
        panel.addView(preview);
        TextView note=new TextView(this);
        note.setText("双摄像头恢复测试：无本地OCR，不上传图片，也不产生云端费用。锁屏取帧尚未验证。");
        note.setTextColor(0xFFBFC2C8);note.setPadding(0,dp(20),0,0);panel.addView(note);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(panel);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll)
            .setPositiveButton("完成",(d,w)->{}).create();
        dialog.setOnShowListener(d->{
            if(dialog.getWindow()!=null){
                android.graphics.drawable.GradientDrawable frame=new android.graphics.drawable.GradientDrawable();
                frame.setColor(0xFF0B0B0D);frame.setCornerRadius(dp(18));
                dialog.getWindow().setBackgroundDrawable(frame);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF66B4EA);
                dialog.getWindow().setLayout(
                    getResources().getDisplayMetrics().widthPixels-dp(24),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();settingsDialog=dialog;
        dialog.setOnDismissListener(d->{if(settingsDialog==dialog)settingsDialog=null;});
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
        usbPreviewVivid=prefs.getBoolean(PREF_USB_PREVIEW_VIVID,true);
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
        Toast.makeText(this,"双摄像头云端任务实验版；本版不联网识别",Toast.LENGTH_LONG).show();
    }

    private void initializeSpeech() {
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        ttsReady=false;
        try{
            TextToSpeech.OnInitListener listener=result->ttsReady=result==TextToSpeech.SUCCESS;
            tts=ttsEngine.isEmpty()?new TextToSpeech(this,listener):
                new TextToSpeech(this,listener,ttsEngine);
        }catch(Exception error){Log.w(TAG,"TTS initialization unavailable",error);}
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
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean(PREF_PHONE_CAMERA,phone).apply();
        phoneStartSequence++;
        if(phoneController!=null)phoneController.stop();
        if(phoneTexture!=null)phoneTexture.setVisibility(phone?View.VISIBLE:View.GONE);
        if(cameraTextureView!=null)cameraTextureView.setVisibility(phone?View.INVISIBLE:View.VISIBLE);
        if(phone)startPhoneCamera();
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
        cameraTextureView.setScaleX(1f);
        cameraTextureView.setScaleY(previewVerticalFlip?-1f:1f);
        if(vw>0&&vh>0){
            Matrix matrix=new Matrix();
            if(usbPreviewCorrectMirror^previewMirror)
                matrix.setScale(-1f,1f,vw*.5f,vh*.5f);
            cameraTextureView.setTransform(matrix);
        }
        if(usbPreviewVivid){
            ColorMatrix m=new ColorMatrix();m.setSaturation(1.07f);
            cameraTextureView.setRenderEffect(
                RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(m)));
        }else cameraTextureView.setRenderEffect(null);
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
            handler.post(this::applyPreviewTransform);
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

    private void invalidate(){
        requestVersion.incrementAndGet();
        if(encodingTask!=null){encodingTask.cancel(true);encodingTask=null;}
        if(tts!=null)tts.stop();
    }

    private void captureOneFrame(){
        long now=SystemClock.elapsedRealtime();
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
        int token=requestVersion.get();
        TaskCatalog.Task task=currentTask;
        String source=usingPhoneCamera?(phoneFrontFacing?"手机前置":"手机后置"):"USB 外接";
        encodingTask=encoder.submit(()->{
            try{
                byte[] jpeg=FrameCompressor.to720pJpeg(current.pixels,current.width,current.height);
                if(token!=requestVersion.get())return;
                handler.post(()->{
                    if(token!=requestVersion.get()||!uiActive)return;
                    String message="已取得"+source+"摄像头最新画面，"
                        +current.width+"×"+current.height+"，JPEG "+jpeg.length/1024+"KB，任务："+task.name;
                    Log.i(TAG,"Mock single-frame success: "+source+" "+current.width+"x"+current.height+
                        " compressedBytes="+jpeg.length+" route="+task.engine);
                    Toast.makeText(this,message,Toast.LENGTH_LONG).show();
                    speak("模拟测试，已拍到一张当前照片。暂时没有连接云端人工智能，无法朗读真实文字。");
                });
            }catch(Exception e){
                handler.post(()->{
                    if(token==requestVersion.get()&&uiActive)
                        Toast.makeText(this,"取图压缩失败："+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
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
        invalidate();
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
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        super.onDestroy();
    }
}
