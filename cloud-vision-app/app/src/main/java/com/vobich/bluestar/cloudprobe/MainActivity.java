package com.vobich.bluestar.cloudprobe;

import android.Manifest;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.AudioAttributes;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.view.TextureView;
import android.view.PixelCopy;
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
import com.jiangdg.ausbc.camera.CameraUVC;
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
    private static final String PREF_USB_AUTO_SWITCH="usb_camera_auto_switch_v1";
    private static final String PREF_USB_AUTO_LAUNCH="usb_camera_auto_launch_v1";
    private static final String USB_ATTACH_ALIAS="com.vobich.bluestar.cloudprobe.UsbAttachEntry";
    private static final String PREF_FRONT_CAMERA = "phone_camera_front";
    private static final String PREF_PHONE_HD_STILL = "phone_true_hd_still_v1";
    private static final String PREF_PHONE_HD_UPLOAD_MODE = "phone_hd_upload_mode_trial_v1";
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
    private static final String PREF_OCR_MODEL = "cloud_ocr_model_v1";
    private static final String PREF_FINDING_TARGET = "continuous_find_target";
    private static final int REQUEST_PHONE_CAMERA = 4051;
    private static final int REQUEST_VOICE_MIC = 4052;
    private static final int REQUEST_DIAG_EXPORT = 4053;
    private static final int REQUEST_READING_EXPORT = 4054;
    private static final int REQUEST_C110_PAGE = 4055;
    private static final long USB_MAX_FRAME_AGE_MS=800L; // UVC raw frames are capped near 5Hz

    private boolean settingsOnly;
    private FrameLayout previewContainer,rootFrame;
    private TextView resultChip;
    private C110WifiBootstrap c110WifiBootstrap;
    private C110DeviceControls settingsC110DeviceControls;
    private String settingsC110Status="";
    private C110QuickShotController quickShot;
    // Real one-shot 1001 OEM photo path remains active after removing the unused video player.
    private final C110WifiCamera c110Camera=new C110WifiCamera();
    private volatile android.net.Network lastSessionCloudNetwork;
    private View tapOverlay;
    private ScaleGestureDetector zoomDetector;
    private boolean touchMulti;
    private float downX,downY,zoom=1f;
    private long downAtMs,lastPinchMs,lastZoomSentMs;
    private final Runnable delayedPhoneZoom=this::applyQueuedPhoneZoom;
    private boolean feedbackSound=true,feedbackVibration=true,followSystemSpeechRate=true;
    private float speechRate=1f;
    private final VoiceRecorder voiceRecorder=new VoiceRecorder();
    private boolean voiceListening;
    private volatile int pendingPhoneFrameToken=-1;
    private volatile int pendingPhoneStillToken=-1;
    private boolean phoneHighRes;
    private int phoneHdUploadMode=PhoneStillUploadPolicy.ORIGINAL;
    private long phoneCaptureTapAtMs;
    private RuntimeDiagnostics diagnostics;
    private ModelUsageLedger modelUsage;
    private final ExecutorService modelTestExecutor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean modelTestBusy=new AtomicBoolean(false);
    private boolean usbFirstFrameRecorded;
    private boolean phoneFirstFrameRecorded;
    private final AtomicInteger voiceSequence=new AtomicInteger();
    private BlueStarSettingsScreen largeSettings;
    private SoundPool clickPool;
    private int metalClickSample,sendWhooshSample;
    private final java.util.Set<Integer> soundLoaded=
        java.util.Collections.synchronizedSet(new java.util.HashSet<Integer>());
    private String findingTarget="";
    private String lastMicDiagnostic="尚未完成录音";
    private final VoicePressState voicePress=new VoicePressState();
    private byte[] lastSessionJpeg;
    // Only the exact JPEG used for the most recent cloud call, kept in RAM.
    // Never written to RuntimeDiagnostics, preferences, storage or extra upload.
    private volatile byte[] lastSubmittedJpeg;
    private String lastSessionText="",lastSessionTask="";
    // A voice capture is one-shot; never mutate or persist the selected task.
    private TaskCatalog.Task pendingPhoneCaptureTask;
    private final ArrayList<String> followupTurns=new ArrayList<>();
    private AspectRatioTextureView cameraTextureView;
    private TextureView phoneTexture;
    private TextView phonePreviewStatus;
    private Button modeButton, startButton, cameraEntryButton;
    private volatile ReadingScreen readingScreen;
    private ReadingLibrary readingLibrary;
    private ReadingNarrationQueue readingNarration;
    private final AtomicBoolean readingAwaitingFrame=new AtomicBoolean(false);
    // True only while a user-visible camera preview is active; shutter stays manual.
    private volatile boolean readingPreviewActive;
    private volatile boolean readingProcessing;
    private volatile long readingCaptureStartedAtMs;
    private volatile int readingCaptureToken;
    private int readingSpeechToken;
    private String readingExportBookId="";
    private InternalCameraController phoneController;
    private boolean usingPhoneCamera, phoneFrontFacing, phonePreviewReceived;
    private int phoneStartSequence, phoneRawWidth=1280, phoneRawHeight=720;
    private int phoneSensorNaturalRotation=90, phoneOcrRotation;
    private int previewRotation;
    private boolean previewMirror, previewVerticalFlip, usbPreviewCorrectMirror=true;

    private boolean previewCallbackAdded;
    private volatile boolean usbForeground;
    private boolean usbStopPending, usbReopenWanted;
    private boolean manualPhoneUntilUsbDetach;
    private int manualPhoneUsbDeviceId=-1;
    private String activeUsbDeviceName;
    // RC16: system USB attach intent and AUSBC permission callback can arrive
    // before onResume; debounce a one-shot physical-device reconciliation.
    private final Runnable usbAttachReconcile=this::reconcileAttachedUsb;
    private final UsbRollingFps usbRollingFps=new UsbRollingFps();
    private long usbLastSampleAtMs,lastUsbFirstFrameMs=-1;
    // RC14: all observation is counter-only. No snapshots, text or extra USB frames.
    private volatile boolean usbKnownOpened;
    private final AtomicLong usbTextureCount=new AtomicLong();
    private volatile long usbTextureLastAtMs;
    private final UsbStreamHealth usbStreamHealth=new UsbStreamHealth();
    private UsbStreamHealth.Sample usbLastSample;
    private int usbLastHealthState=-1,usbHealthSampleBudget;
    private long usbResumeAtMs,usbStopRequestAtMs,usbOpenRequestAtMs;
    private final Runnable usbHealthTicker=this::sampleUsbHealth;
    private final ExecutorService usbPictureExecutor=Executors.newSingleThreadExecutor();
    private final AtomicInteger usbPictureGeneration=new AtomicInteger();
    private long usbOpenedAtMs, usbRecoveryAtMs, phoneStartAtMs;
    private volatile Frame latestPhoneFrame, latestUsbFrame;
    private final AtomicLong usbFrameCount = new AtomicLong();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService encoder = Executors.newSingleThreadExecutor();
    // Camera/JPEG work and old network waits must never share a single queue.
    private final ExecutorService photoCloudExecutor = Executors.newFixedThreadPool(2);
    private final Object photoCloudLock = new Object();
    private java.util.concurrent.Future<?> photoCloudTask;
    private CloudVisionClient.Cancellation activePhotoCloud;
    private boolean c110NativeShutterBusy;
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
    private String visionModel=VisionModelCatalog.DEFAULT;
    private String ocrModel=OcrModelCatalog.DEFAULT;
    // RC21: verified C is default OCR playback; A remains the user-selectable compatibility fallback.
    private volatile String ocrSpeedMode=OcrSpeedExperiment.DEFAULT;
    private volatile boolean webSearchEnabled=SearchRoutePolicy.DEFAULT_ENABLED;
    private String lastCloudResult="";

    private static final class Frame {
        final byte[] pixels;
        final int width, height;
        final long atMs;
        final byte[] remoteJpeg; // C110 original JPEG, already fully validated in RAM
        final android.net.Network cloudNetwork; // Cellular only, never camera Wi-Fi
        final boolean c110LiveFrame; // Distinguish PixelCopy from original JPEG
        final boolean phoneStillJpeg; // Real Camera2 TEMPLATE_STILL_CAPTURE
        Frame(byte[] pixels, int width, int height, long atMs) {
            this(pixels,width,height,atMs,null,null,false,false);
        }
        Frame(byte[] pixels,int width,int height,long atMs,
              byte[] remoteJpeg,android.net.Network cloudNetwork) {
            this(pixels,width,height,atMs,remoteJpeg,cloudNetwork,false,false);
        }
        Frame(byte[] pixels,int width,int height,long atMs,
              byte[] remoteJpeg,android.net.Network cloudNetwork,
              boolean c110LiveFrame) {
            this(pixels,width,height,atMs,remoteJpeg,cloudNetwork,c110LiveFrame,false);
        }
        Frame(byte[] stillJpeg,int width,int height,long atMs,boolean phoneStill) {
            this(null,width,height,atMs,stillJpeg,null,false,phoneStill);
        }
        private Frame(byte[] pixels,int width,int height,long atMs,
              byte[] remoteJpeg,android.net.Network cloudNetwork,
              boolean c110LiveFrame,boolean phoneStill) {
            this.pixels=pixels;this.width=width;this.height=height;this.atMs=atMs;
            this.remoteJpeg=remoteJpeg;this.cloudNetwork=cloudNetwork;
            this.c110LiveFrame=c110LiveFrame;this.phoneStillJpeg=phoneStill;
        }
    }

    private final IPreviewDataCallBack previewCallback = new IPreviewDataCallBack() {
        @Override public void onPreviewData(byte[] frame, int w, int h, DataFormat format) {
            if (!uiActive || usingPhoneCamera || format != DataFormat.NV21) return;
            updateFrame(frame,w,h,false);
        }
    };

    private void updateFrame(byte[] bytes,int w,int h,boolean phone) {
        if(bytes==null||w<2||h<2||(w&1)!=0||(h&1)!=0||
            (long)bytes.length!=(long)w*h*3L/2L)return;
        // Pinned AUSBC CameraUVC.frameCallBack creates a fresh ByteArray on
        // every NV21 delivery and never mutates it. Camera2 does the same.
        // Keep that owned buffer directly: no duplicate 1920x1080 allocation
        // for each photo-cache update. Native preview remains direct TextureView.
        Frame frame=new Frame(bytes,w,h,SystemClock.elapsedRealtime());
        if(phone){
            handler.post(()->{
                int token=pendingPhoneFrameToken;
                if(token<0||token!=requestVersion.get()||!usingPhoneCamera||!uiActive)return;
                pendingPhoneFrameToken=-1;
                TaskCatalog.Task task=pendingPhoneCaptureTask;
                pendingPhoneCaptureTask=null;
                if(readingScreen!=null){
                    pendingPhoneFrameToken=-1;
                    if(readingAwaitingFrame.compareAndSet(true,false))
                        beginReadingCapturedFrame(frame,token);
                    return;
                }
                beginCapturedFrame(frame,phoneCaptureTapAtMs,token,task);
            });
        }else{
            latestUsbFrame=frame;
            if(readingScreen!=null&&!usingPhoneCamera&&readingPreviewActive&&
                readingAwaitingFrame.get()&&frame.atMs>=readingCaptureStartedAtMs){
                final int token=readingCaptureToken;
                if(readingAwaitingFrame.compareAndSet(true,false))
                    handler.post(()->beginReadingCapturedFrame(frame,token));
            }
            usbFrameCount.incrementAndGet();
            if(!usbFirstFrameRecorded){
                usbFirstFrameRecorded=true;
                lastUsbFirstFrameMs=usbOpenedAtMs>0?
                    Math.max(0,frame.atMs-usbOpenedAtMs):-1;
                if(diagnostics!=null)diagnostics.event("camera","usb_first_frame_ms",
                    lastUsbFirstFrameMs,"ok");
            }
        }
    }

    /** Full screen camera + one-tap capture/pinch + accessible dock. */
    @Override protected View getRootView(LayoutInflater inflater){
        FrameLayout root=new FrameLayout(this);
        rootFrame=root;
        // Android 15/16 enforce edge-to-edge: reserve status/navigation bar areas.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top=insets.getSystemWindowInsetTop();
            int bottom=insets.getSystemWindowInsetBottom();
            if(view.getPaddingTop()!=top||view.getPaddingBottom()!=bottom)
                view.setPadding(0,top,0,bottom);
            return insets;
        });
        root.requestApplyInsets();
        root.setBackgroundColor(settingsOnly?0xFF091B32:Color.BLACK);
        previewContainer=new FrameLayout(this);
        previewContainer.setBackgroundColor(Color.BLACK);
        if(settingsOnly)previewContainer.setVisibility(View.GONE);
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
        tapOverlay.setContentDescription("点击当前画面识别文字；双指缩放普通摄像头画面");
        tapOverlay.setFocusable(true);
        tapOverlay.setOnClickListener(v->{
            captureOneFrame();
        });
        zoomDetector=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){
                touchMulti=true;return true;
            }
            @Override public boolean onScale(ScaleGestureDetector d){
                setCameraZoom(zoom*d.getScaleFactor());return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d){
                lastPinchMs=SystemClock.elapsedRealtime();
                saveCameraProfile();
                handler.removeCallbacks(delayedPhoneZoom);
                if(usingPhoneCamera)handler.post(delayedPhoneZoom);
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

        resultChip=new TextView(this);
        resultChip.setGravity(Gravity.CENTER);
        resultChip.setTextColor(Color.WHITE);
        resultChip.setTextSize(TypedValue.COMPLEX_UNIT_SP,13);
        resultChip.setText("");
        resultChip.setVisibility(View.GONE);
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
        modeButton=createControlButton("⌂\n主页",0xFF2C547D);
        startButton=createControlButton("◎\n预览",0xFF176BAC);
        cameraEntryButton=createControlButton("🎙\n按住AI",0xFF2C547D);
        for(Button btt:new Button[]{modeButton,startButton,cameraEntryButton}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(75),1f);
            lp.setMargins(dp(3),0,dp(3),0);dock.addView(btt,lp);
        }
        root.addView(dock,new FrameLayout.LayoutParams(-1,dp(89),Gravity.BOTTOM));
        modeButton.setContentDescription("返回蓝星向导主页");
        startButton.setContentDescription("预览当前摄像头，点击可切换手机、USB或C110");
        cameraEntryButton.setContentDescription("按住AI说话，松开发送，追问上一张手机或C110照片");
        modeButton.setOnClickListener(v->finish());
        startButton.setOnClickListener(v->showUnifiedModeSelector());
        // Same proven MainActivity VoicePressState/VoiceRecorder/ASR/TTS flow
        // previously attached to the TOP microphone. This only moves its touch
        // target to the bottom-right button; no second recorder or AI API.
        cameraEntryButton.setOnTouchListener((v,event)->handleVoiceButtonTouch(event));
        cameraEntryButton.setOnClickListener(v->toastShort("请按住AI说话，松开发送"));
        return root;
    }

    /** Three camera sources, one bottom dock. The proven C110 VLC remains isolated. */
    private void showUnifiedModeSelector(){
        if(readingScreen!=null){toastShort("请先退出阅读页面");return;}
        String[] modes={"手机摄像头","C110 运动相机","USB 摄像头"};
        new BlueStarDialogChoices.AppCompatBuilder(this).setTitle("选择模式")
            .setItems(modes,(dialog,which)->{
                if(which==1){
                    // OEM hotspot picker belongs to the foreground C110
                    // Activity; never show its dialog behind a new Activity.
                    startActivityForResult(new Intent(this,C110CameraActivity.class),
                        REQUEST_C110_PAGE);
                }else if(which==0){
                    setCameraSourceFromSettings(phoneFrontFacing?2:1);
                }else{
                    setCameraSourceFromSettings(0);
                }
            }).setNegativeButton("取消",null).show();
    }

    /** Return from the proven separate video player to an existing app feature. */
    private void handleC110PageResult(int resultCode,Intent data){
        if(resultCode!=RESULT_OK||data==null)return;
        refreshTask(TaskCatalog.selectedId(this));
        String action=data.getStringExtra("c110_action");
        if(action==null)return;
        // Run after CameraActivity.onResume; the shutter is invoked exactly once.
        handler.postDelayed(()->{
            if(!uiActive)return;
            switch(action){
                case "photo":captureC110OneShot();break;
                case "reading":openReadingMode();break;
                case "mode_phone":setCameraSourceFromSettings(phoneFrontFacing?2:1);break;
                case "mode_usb":setCameraSourceFromSettings(0);break;
                case "tasks":startActivity(new Intent(this,TaskDesktopActivity.class));break;
                case "settings":openLargeSettings();break;
                case "ai":
                    toastShort("按住底部 AI 对话说话，松开发送");
                    break;
                default:break;
            }
        },250L);
    }

    private GradientDrawable roundRect(int color,int radius){
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;
    }
    private int dp(int d){return Math.round(d*getResources().getDisplayMetrics().density);}

    private Button createControlButton(String label,int background){
        Button button=new Button(this);
        button.setAllCaps(false);
        android.text.SpannableString styled=new android.text.SpannableString(label);
        int lineEnd=label.indexOf('\n');
        if(lineEnd>0)styled.setSpan(new android.text.style.RelativeSizeSpan(1.6f),
            0,lineEnd,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(styled);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP,16);
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
        if(modeButton!=null)modeButton.setContentDescription("返回主页；当前任务："+currentTask.name);
    }

    private void setFindingTarget(String target){
        FindingTargetStore.set(this,target);
        findingTarget=FindingTargetStore.current(this);
    }

    private final Runnable hideStatus=()->{
        if(resultChip!=null)resultChip.setVisibility(View.GONE);
    };
    /** Only preserve a numeric HTTP status, never response bodies or user text. */
    private static long diagnosticHttpStatus(Throwable error){
        if(error==null||error.getMessage()==null)return -1L;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
            "HTTP\\s*(\\d{3})").matcher(error.getMessage());
        if(!m.find())return -1L;
        try{return Long.parseLong(m.group(1));}catch(NumberFormatException ignored){return -1L;}
    }

    private void setResultStatus(String text){
        if(resultChip==null)return;
        handler.removeCallbacks(hideStatus);
        if(text==null||text.trim().isEmpty()){resultChip.setVisibility(View.GONE);return;}
        resultChip.setText(text);
        resultChip.setVisibility(View.VISIBLE);
        handler.postDelayed(hideStatus,4500L);
    }

    private void openLargeSettings(){
        if(largeSettings!=null&&!largeSettings.isClosed())return;
        largeSettings=new BlueStarSettingsScreen(this,rootFrame);
    }
    void openCloudServiceSettings(String role){
        openLargeSettings();
        if(largeSettings!=null&&!largeSettings.isClosed())
            largeSettings.openCloudRole(role);
    }
    void settingsPageClosed(){
        largeSettings=null;
        // Home -> Settings is settings-only, never reveal the camera preview
        // if the user closes Settings. Return to the actual HomeActivity.
        if(settingsOnly)finish();
    }
    /** C110 hardware settings are operated directly in unified Settings.
     *  Never start C110CameraActivity/VLC merely to change LED or beep. */
    String currentC110WifiSettingsStatus(){
        return C110WifiBootstrap.connected(this)!=null?
            "C110 Wi-Fi已验证连接，可以直接控制机身":
            "尚未连接C110 Wi-Fi；请先点击连接";
    }
    String lastC110SettingsStatus(){return settingsC110Status;}
    private void setC110SettingsStatus(String value){
        if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()){
            handler.post(()->setC110SettingsStatus(value));return;
        }
        settingsC110Status=value==null?"":value;
        setResultStatus(settingsC110Status);
        if(largeSettings!=null&&!largeSettings.isClosed())
            largeSettings.updateC110Status(settingsC110Status);
    }
    void connectC110WifiFromSettings(){
        if(C110WifiBootstrap.connected(this)!=null){
            setC110SettingsStatus("C110 Wi-Fi已验证连接，可直接设置机身");
            return;
        }
        setC110SettingsStatus("正在申请连接C110相机Wi-Fi；按系统提示确认");
        if(c110WifiBootstrap!=null)c110WifiBootstrap.requestForCameraMode();
    }
    void setC110BeepFromSettings(boolean enabled){
        if(settingsC110DeviceControls!=null)
            settingsC110DeviceControls.setSoundFromSettings(enabled);
    }
    void setC110LightFromSettings(int seconds){
        if(settingsC110DeviceControls!=null)
            settingsC110DeviceControls.setLightPolicyFromSettings(seconds);
    }
    void openHomeFromUnifiedSettings(){
        if(largeSettings!=null&&!largeSettings.isClosed())largeSettings.close();
        if(!settingsOnly)finish();
    }
    void openPreviewFromUnifiedSettings(){
        if(settingsOnly){
            // Settings-only is already the task's top MainActivity. Because
            // MainActivity is singleTop, startActivity(MainActivity)+finish()
            // reuses and then destroys THIS activity, sending users Home.
            // Activate its existing camera view in-place instead.
            settingsOnly=false;
            Intent launch=getIntent();
            if(launch!=null)launch.removeExtra("bluestar_launch_settings");
            if(rootFrame!=null)rootFrame.setBackgroundColor(Color.BLACK);
            if(previewContainer!=null)previewContainer.setVisibility(View.VISIBLE);
            if(largeSettings!=null&&!largeSettings.isClosed())largeSettings.close();
            usbForeground=true;
            handler.post(this::resumeForegroundPreview);
        }else if(largeSettings!=null&&!largeSettings.isClosed())largeSettings.close();
    }
    @Override public void onBackPressed(){
        if(largeSettings!=null&&!largeSettings.isClosed()){largeSettings.back();return;}
        if(readingScreen!=null){closeReadingMode();return;}
        if(settingsOnly){finish();return;}
        super.onBackPressed();
    }

    private void shortcutPhoto(){
        if(settingsOnly||(largeSettings!=null&&!largeSettings.isClosed())){
            toastShort("设置页面不执行拍照");
            return;
        }
        if(!uiActive||readingScreen!=null){toastShort("请先退出阅读模式");return;}
        // Connected: preserve the existing real 1001 + livestream resume path.
        // Not yet connected: retain the SAME one-tap shutter across the
        // original Android Wi-Fi consent, without opening a second page.
        if(C110WifiBootstrap.connected(this)!=null){
            captureC110OneShot();
        }else{
            if(quickShot==null){
                quickShot=new C110QuickShotController(this,this::setResultStatus);
                quickShot.onResume();
            }
            quickShot.trigger();
        }
    }
    void toastShort(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
    List<TextToSpeech.EngineInfo> availableTtsEngines(){
        return tts==null?new ArrayList<>():tts.getEngines();
    }
    String currentSpeechEngineDescription(){
        return ttsEngine==null||ttsEngine.isEmpty()?"手机系统默认语音引擎":ttsEngine;
    }
    void changeSpeechEngine(String id){
        ttsEngine=id==null?"":id;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(PREF_TTS_ENGINE,ttsEngine).apply();
        initializeSpeech();
    }
    boolean isFollowingSystemSpeechRate(){return followSystemSpeechRate;}
    void setFollowSystemSpeechRate(boolean enabled){
        followSystemSpeechRate=enabled;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean(PREF_SPEECH_DEFAULT,enabled).apply();
        if(tts!=null&&ttsReady)tts.setSpeechRate(enabled?1f:speechRate);
    }
    void applySpeechSpeed(float value){
        speechRate=Math.min(3f,Math.max(.5f,value));
        followSystemSpeechRate=false;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putFloat(PREF_SPEECH_RATE,speechRate)
            .putBoolean(PREF_SPEECH_DEFAULT,false).apply();
        if(tts!=null&&ttsReady)tts.setSpeechRate(speechRate);
    }
    void previewSpeech(){speakUnfiltered("蓝星向导，当前语音和语速试听。");}
    void previewLastRecognizedText(){speak(lastCloudResult);}
    String lastRecognizedText(){return lastCloudResult;}
    void refreshSelectedTask(String id){refreshTask(id);}
    String runtimeDiagnosticSummary(){
        String basic=diagnostics==null?"暂无记录":diagnostics.summary();
        if(usingPhoneCamera)return basic+
            "\n当前使用手机摄像头；USB预览FPS不适用，不展示历史USB快照。\n";
        if(!usbForeground)return basic+"\nUSB处于后台，当前帧率不可测。\n";
        UsbStreamHealth.Sample last=usbLastSample;
        long age=usbLastSampleAtMs<=0?-1:
            Math.max(0,SystemClock.elapsedRealtime()-usbLastSampleAtMs);
        if(last==null||age>4500L)return basic+
            "\nUSB流诊断：没有足够新的采样，请保持USB预览几秒后再刷新。\n";
        UsbRollingFps.Stats window=usbRollingFps.stats(SystemClock.elapsedRealtime());
        return basic+"\nUSB预览流快照（更新时间距今 "+age+"ms）\n"+
            "状态码 "+last.state+"（0启动中/1正常/2双路停/3原始帧停/4预览停）\n"+
            "原始NV21回调约 "+(last.rawFps10/10f)+"次/秒（此回调人为限约5Hz）\n"+
            "TextureView新缓冲当前约 "+(last.previewFps10/10f)+"次/秒\n"+
            (window.count==0?"最近10秒有效样本：暂无（初始化中）\n":
                "最近10秒有效样本 "+window.count+
                " 组；平均约 "+(window.meanFps10/10f)+
                " 次/秒；最低约 "+(window.minFps10/10f)+
                " 次/秒；最高约 "+(window.maxFps10/10f)+" 次/秒\n")+
            "最近一次USB打开至首NV21帧 "+lastUsbFirstFrameMs+"ms（-1为尚未取得）\n"+
            "NV21最近帧距今 "+last.rawAgeMs+"ms；预览新缓冲距今 "+
            last.previewAgeMs+"ms\n"+
            "注意：TextureView新缓冲提交次数不保证每次是不同画面，也不是面板上屏帧率。\n";
    }
    private void sampleUsbHealth(){
        if(!usbForeground||usingPhoneCamera)return;
        long now=SystemClock.elapsedRealtime();
        Frame frame=latestUsbFrame;
        UsbStreamHealth.Sample sample=usbStreamHealth.sample(now,usbOpenedAtMs,
            usbFrameCount.get(),usbTextureCount.get(),frame==null?0:frame.atMs,
            usbTextureLastAtMs,usbKnownOpened);
        usbLastSample=sample;
        usbLastSampleAtMs=now;
        usbRollingFps.add(now,sample.previewFps10,sample.elapsedMs,
            usbKnownOpened&&sample.state!=UsbStreamHealth.OPENING);
        boolean changed=sample.state!=usbLastHealthState;
        if(diagnostics!=null&&(usbHealthSampleBudget>0||
           (changed&&sample.state!=UsbStreamHealth.OPENING))){
            diagnostics.event("usb","raw_fps_x10",sample.rawFps10,"ok");
            diagnostics.event("usb","view_fps_x10",sample.previewFps10,"ok");
            diagnostics.event("usb","raw_age_ms",sample.rawAgeMs,"ok");
            diagnostics.event("usb","view_age_ms",sample.previewAgeMs,"ok");
            diagnostics.event("usb","health_state",sample.state,
                sample.state==UsbStreamHealth.HEALTHY?"ok":
                sample.state==UsbStreamHealth.OPENING?"starting":"stalled");
        }
        if(usbHealthSampleBudget>0)usbHealthSampleBudget--;
        usbLastHealthState=sample.state;
        handler.postDelayed(usbHealthTicker,2000L);
    }
    private void resetUsbFpsState(){
        handler.removeCallbacks(usbHealthTicker);
        usbStreamHealth.reset();
        usbRollingFps.reset();
        usbLastSample=null;
        usbLastSampleAtMs=0;
        usbLastHealthState=-1;
        usbHealthSampleBudget=3;
    }
    private void startUsbFpsMonitoring(){
        resetUsbFpsState();
        if(usbForeground&&!usingPhoneCamera)
            handler.postDelayed(usbHealthTicker,2000L);
    }
    @Override protected void onUsbSurfaceAvailableForDiagnostics(){
        if(diagnostics!=null)diagnostics.event("usb","surface",1,"available");
    }
    @Override protected void onUsbSurfaceDestroyedForDiagnostics(){
        usbPictureGeneration.incrementAndGet();
        if(diagnostics!=null)diagnostics.event("usb","surface",0,"destroyed");
    }
    @Override protected void onUsbTextureUpdatedForDiagnostics(){
        usbTextureLastAtMs=SystemClock.elapsedRealtime();
        usbTextureCount.incrementAndGet();
    }
    void previewLastUploadedJpeg(){
        byte[] jpeg=lastSubmittedJpeg;
        if(jpeg==null||jpeg.length==0){
            toastShort("暂无本次运行已提交的照片，请先拍照识别");
            return;
        }
        Bitmap bitmap;
        try{
            bitmap=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
        }catch(RuntimeException error){
            toastShort("无法解码上次发送的照片");return;
        }
        if(bitmap==null){toastShort("无法解码上次发送的照片");return;}
        ImageView view=new ImageView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setMaxHeight(getResources().getDisplayMetrics().heightPixels*3/4);
        view.setImageBitmap(bitmap);
        AlertDialog dialog=new BlueStarDialogChoices.AppCompatBuilder(this)
            .setTitle("上次实际发送给 AI 的 JPEG")
            .setMessage("仅本机临时查看；不保存、不再上传、不计入匿名日志。可核对方向、镜像和文字。")
            .setView(view)
            .setPositiveButton("关闭",null).create();
        dialog.setOnDismissListener(d->{
            view.setImageDrawable(null);
            bitmap.recycle();
        });
        dialog.show();
    }
    void copyRuntimeDiagnostics(){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard==null){toastShort("剪贴板暂不可用");return;}
        clipboard.setPrimaryClip(ClipData.newPlainText("蓝星向导运行诊断",runtimeDiagnosticSummary()));
        toastShort("已复制匿名运行诊断摘要");
    }
    void exportRuntimeDiagnostics(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"BlueStar_Running_Diagnostics.txt");
        startActivityForResult(intent,REQUEST_DIAG_EXPORT);
    }
    void clearRuntimeDiagnostics(){
        if(diagnostics!=null)diagnostics.clear();
        toastShort("本机诊断记录已清空");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_C110_PAGE){handleC110PageResult(resultCode,data);return;}
        if(requestCode==REQUEST_READING_EXPORT){
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
                final Uri target=data.getData();
                final String id=readingExportBookId;
                encoder.submit(()->{
                    try(OutputStream out=getContentResolver().openOutputStream(target)){
                        if(out==null)throw new Exception("无法创建文件");
                        out.write(readingLibrary.export(id).getBytes(StandardCharsets.UTF_8));
                        handler.post(()->toastShort("已导出书籍文字 TXT"));
                    }catch(Exception ex){
                        handler.post(()->toastShort("导出失败："+ex.getMessage()));
                    }
                });
            }
            readingExportBookId="";
            return;
        }
        if(requestCode!=REQUEST_DIAG_EXPORT||resultCode!=RESULT_OK||data==null)return;
        Uri uri=data.getData();
        if(uri==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(uri)){
            if(out==null)throw new Exception("无法创建导出文件");
            out.write(runtimeDiagnosticSummary().getBytes(StandardCharsets.UTF_8));
            toastShort("匿名诊断日志已保存");
        }catch(Exception e){toastShort("导出失败，请重新选择位置");}
    }
    String sourceDescription(){return usingPhoneCamera?
        (phoneFrontFacing?"手机前置摄像头":"手机后置摄像头"):"USB 外接摄像头";}
    String rotationDescription(){return previewRotation+"度";}
    String mirrorDescription(){return previewMirror?"已开启":"已关闭";}
    String flipDescription(){return previewVerticalFlip?"已开启":"已关闭";}
    String usbCorrectionDescription(){return usbPreviewCorrectMirror?"已开启":"已关闭";}
    boolean isUsbAutoSwitch(){return getSharedPreferences(PREFS,MODE_PRIVATE)
        .getBoolean(PREF_USB_AUTO_SWITCH,true);}
    boolean isUsbAutoLaunch(){return getSharedPreferences(PREFS,MODE_PRIVATE)
        .getBoolean(PREF_USB_AUTO_LAUNCH,false);}
    void setUsbAutoSwitch(boolean enable){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean(PREF_USB_AUTO_SWITCH,enable).apply();
        if(!settingsOnly&&enable&&!usingPhoneCamera&&!hasConnectedUsbVideoDevice())
            switchCameraSource(true);
    }
    void setUsbAutoLaunch(boolean enable){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean(PREF_USB_AUTO_LAUNCH,enable).apply();
        updateUsbAttachAlias(enable);
    }
    private void updateUsbAttachAlias(boolean enable){
        try{
            ComponentName entry=new ComponentName(this,USB_ATTACH_ALIAS);
            PackageManager manager=getPackageManager();
            int desired=enable?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if(manager.getComponentEnabledSetting(entry)!=desired)
                manager.setComponentEnabledSetting(entry,desired,PackageManager.DONT_KILL_APP);
        }catch(RuntimeException err){
            Log.w(TAG,"USB system attach alias unavailable",err);
            toastShort("无法修改系统USB启动关联，可手动打开应用");
        }
    }
    private static boolean isUsbVideoDevice(UsbDevice device){
        if(device==null)return false;
        if(device.getVendorId()==0xEBA4&&device.getProductId()==0x6579)return true;
        for(int i=0;i<device.getInterfaceCount();i++)
            if(device.getInterface(i).getInterfaceClass()==UsbConstants.USB_CLASS_VIDEO)
                return true;
        return device.getDeviceClass()==UsbConstants.USB_CLASS_VIDEO;
    }
    private UsbDevice attachedUsbVideoDevice(){
        UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);
        if(manager==null)return null;
        UsbDevice first=null;
        for(UsbDevice dev:manager.getDeviceList().values()){
            if(!isUsbVideoDevice(dev))continue;
            // Reuse the existing known camera when more than one USB device exists.
            if(activeUsbDeviceName!=null&&activeUsbDeviceName.equals(dev.getDeviceName()))
                return dev;
            if(dev.getVendorId()==0xEBA4&&dev.getProductId()==0x6579)
                return dev;
            if(first==null)first=dev;
        }
        return first;
    }
    private boolean hasConnectedUsbVideoDevice(){
        return attachedUsbVideoDevice()!=null;
    }
    private void selectManualPhoneOverride(boolean phone){
        UsbDevice present=phone?attachedUsbVideoDevice():null;
        manualPhoneUntilUsbDetach=present!=null;
        manualPhoneUsbDeviceId=present==null?-1:present.getDeviceId();
    }
    private boolean sameOverriddenUsbStillPresent(){
        if(!manualPhoneUntilUsbDetach)return false;
        UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);
        if(manager==null)return false;
        java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();
        for(UsbDevice dev:manager.getDeviceList().values())
            if(isUsbVideoDevice(dev))ids.add(dev.getDeviceId());
        int[] connected=new int[ids.size()];
        for(int i=0;i<ids.size();i++)connected[i]=ids.get(i);
        return UsbAutoSwitchPolicy.keepManualOverrideForPhysicalDevice(
            manualPhoneUntilUsbDetach,manualPhoneUsbDeviceId,connected);
    }
    private void queueUsbAttachReconcile(){
        handler.removeCallbacks(usbAttachReconcile);
        handler.postDelayed(usbAttachReconcile,180L);
    }
    private void reconcileAttachedUsb(){
        if(!uiActive||!usbForeground||readingScreen!=null)return;
        UsbDevice device=attachedUsbVideoDevice();
        if(manualPhoneUntilUsbDetach&&!sameOverriddenUsbStillPresent()){
            manualPhoneUntilUsbDetach=false;
            manualPhoneUsbDeviceId=-1;
        }
        boolean shouldSwitch=UsbAutoSwitchPolicy.useUsbOnConnect(
            isUsbAutoSwitch(),usbForeground,device!=null,usingPhoneCamera,
            manualPhoneUntilUsbDetach);
        if(diagnostics!=null)diagnostics.event("usb","attach_reconcile",
            shouldSwitch?1:0,device==null?"no_device":
            manualPhoneUntilUsbDetach?"manual":"checked");
        if(!shouldSwitch)return;
        // This covers initial Activity auto-launch and repeated onNewIntent, where
        // AUSBC onConnect may have completed before onResume or never fired while
        // the hidden TextureView was inactive. Making USB visible lets the
        // EXISTING AUSBC TextureView listener register and request UVC permission.
        saveCameraProfile();
        activeUsbDeviceName=device.getDeviceName();
        switchCameraSource(false);
        // switchCameraSource queues one normal resumeUsb; onConnect will handle
        // USB permission later if AUSBC's currentCamera is not yet available.
    }
    void adjustCamera(int index){
        if(index==1)previewRotation=(previewRotation+90)%360;
        else if(index==2)previewMirror=!previewMirror;
        else if(index==3)previewVerticalFlip=!previewVerticalFlip;
        else if(index==4)usbPreviewCorrectMirror=!usbPreviewCorrectMirror;
        else if(index==5)setCameraZoom(1f);
        saveCameraProfile();applyPreviewTransform();applyPhonePreviewTransform();
    }
    String phonePhotoModeDescription(){
        return phoneHighRes?"高清静态拍照":"快速预览截帧";
    }
    boolean isPhoneHighRes(){return phoneHighRes;}
    int phoneHdUploadModeIndex(){return phoneHdUploadMode;}
    String phoneHdUploadDescription(){
        switch(phoneHdUploadMode){
            case PhoneStillUploadPolicy.P1440:return "1440P";
            case PhoneStillUploadPolicy.P1080:return "1080P";
            default:return "原图约800万";
        }
    }
    void setPhoneHdUploadModeFromSettings(int choice){
        phoneHdUploadMode=PhoneStillUploadPolicy.normalize(choice);
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putInt(PREF_PHONE_HD_UPLOAD_MODE,phoneHdUploadMode).apply();
        // Only the JPEG upload is affected. No camera restart.
    }
    void setPhonePhotoModeFromSettings(boolean enabled){
        if(phoneHighRes==enabled)return;
        if(cloudBusy){toastShort("正在处理照片，请完成后再切换拍照模式");return;}
        phoneHighRes=enabled;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean(PREF_PHONE_HD_STILL,enabled).apply();
        // Only Camera2 is restarted. USB and C110 state machines are untouched.
        if(usingPhoneCamera&&uiActive&&!settingsOnly&&phoneController!=null)
            startPhoneCamera();
    }

    void setCameraSourceFromSettings(int index){
        if(index<0||index>2)return;
        boolean phone=index!=0,front=index==2;
        if(settingsOnly){
            // Unified settings opened from Home does NOT start Camera2/UVC.
            // Store preferred source now; initialize actual camera only when
            // user explicitly selects the Preview tab.
            usingPhoneCamera=phone;phoneFrontFacing=front;
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putBoolean(PREF_PHONE_CAMERA,phone)
                .putBoolean(PREF_FRONT_CAMERA,front).apply();
            toastShort("已保存摄像头选择，进入预览后生效");
            return;
        }
        selectManualPhoneOverride(phone);
        if(phone==usingPhoneCamera&&(!phone||front==phoneFrontFacing))return;
        saveCameraProfile();
        if(phone){
            phoneFrontFacing=front;
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putBoolean(PREF_FRONT_CAMERA,front).apply();
        }
        switchCameraSource(phone);
    }
    String visionModelDescription(){return visionModel;}
    String ocrModelDescription(){return ocrModel;}
    String ocrSpeedDescription(){return ocrSpeedMode;}
    boolean selectOcrSpeedMode(String mode){
        if(!(OcrSpeedExperiment.A.equals(mode)||OcrSpeedExperiment.C.equals(mode)))return false;
        ocrSpeedMode=mode;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString(OcrSpeedExperiment.PREF,mode).apply();
        return true;
    }
    boolean selectOcrModel(String id){
        if(OcrModelCatalog.indexOf(id)<0)return false;
        // The old model picker must also switch the ACTIVE cloud route,
        // otherwise the old checkmark lies while custom GLM continues to run.
        CloudProviderStore.select(this,CloudProviderStore.OCR,"");
        ocrModel=id;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString(PREF_OCR_MODEL,id).apply();
        return true;
    }
    boolean isWebSearchEnabled(){return webSearchEnabled;}
    void setWebSearchEnabled(boolean enabled){
        webSearchEnabled=enabled;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putBoolean(SearchRoutePolicy.PREF,enabled).apply();
    }
    boolean selectVisionModel(String id){
        if(VisionModelCatalog.indexOf(id)<0)return false;
        CloudProviderStore.select(this,CloudProviderStore.VISION,"");
        visionModel=id;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString(PREF_VISION_MODEL,id).apply();
        return true;
    }
    ModelUsageLedger.Snapshot todayModelUsage(){
        return modelUsage==null?null:modelUsage.today();
    }
    void clearModelUsage(){
        if(modelUsage!=null)modelUsage.clearAsync();
    }

    /** Explicit, paid micro-image test of an independently saved OCR or Vision
     * profile. Does NOT activate it; failing tests leave every selection intact. */
    void testCloudProvider(String role,String profileId){
        testCloudProvider(role,profileId,null);
    }
    /** Exact model test; does NOT switch the active cloud profile or model. */
    void testCloudProvider(String role,String profileId,String modelOverride){
        if(CloudProviderStore.ASR.equals(role)){
            toastShort("语音识别请在主页按住AI说一句话进行真实测试");return;
        }
        if(!uiActive||cloudBusy||voiceListening||voiceRecorder.isBusy()){
            toastShort("请先结束当前拍照或录音任务");return;
        }
        if(!modelTestBusy.compareAndSet(false,true)){
            toastShort("已有模型测试正在进行");return;
        }
        modelTestExecutor.execute(()->{
            Bitmap bitmap=null;
            try{
                String builtin=modelOverride==null?
                    (CloudProviderStore.OCR.equals(role)?ocrModel:visionModel):modelOverride;
                CloudProviderStore.Config choice=CloudProviderStore.resolveFor(
                    this,role,profileId,builtin);
                // 64x64 is too tiny to reliably validate a real OCR service.
                // The clearer 512x512 glyph makes correctness meaningful.
                bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.RGB_565);
                Canvas canvas=new Canvas(bitmap);
                canvas.drawColor(0xFFFFFFFF);
                Paint paint=new Paint();
                paint.setColor(0xFF101010);paint.setTextSize(320);paint.setAntiAlias(true);
                canvas.drawText("A",140,393,paint);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG,75,bytes);
                TaskCatalog.Task probe=new TaskCatalog.Task("model-probe","接口测试",
                    "这张测试图上是哪个英文字母？只返回字母本身。",
                    CloudProviderStore.OCR.equals(role)?TaskCatalog.OCR:TaskCatalog.VISION,
                    false,"✦");
                CloudVisionClient.Result result=CloudVisionClient.recognize(
                    bytes.toByteArray(),choice,probe,null,OcrSpeedExperiment.A,null);
                // HTTP 200 or a generic description does NOT mean OCR worked.
                // Only a correct reading of the known glyph is a pass.
                if(!CloudProviderPolicy.probeRecognizedA(result.text))
                    throw new Exception("已收到接口响应，但没有正确识别出测试字母A");
                if(modelUsage!=null)modelUsage.recordAsync(result.model,result.serverModel,
                    result.promptTokens,result.completionTokens,true);
                handler.post(()->{if(uiActive)toastShort(
                    "连接成功："+choice.name+" · "+result.model);});
            }catch(Exception e){
                final String error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                handler.post(()->{if(uiActive)toastShort("接口测试失败："+error);});
            }finally{
                if(bitmap!=null)bitmap.recycle();
                modelTestBusy.set(false);
            }
        });
    }

    /** One explicit tiny synthetic image request; never silently retry using Plus. */
    void testSelectedVisionModel(){
        if(!uiActive){toastShort("请在前台测试模型");return;}
        if(cloudBusy||voiceListening||voiceRecorder.isBusy()){
            toastShort("请先完成当前拍照或语音任务");return;
        }
        if(!modelTestBusy.compareAndSet(false,true)){
            toastShort("已有模型连接测试在进行");return;
        }
        final String chosen=visionModel;
        setResultStatus("正在测试 "+chosen+"（会消耗少量 Token）");
        modelTestExecutor.execute(()->{
            try{
                String key=CloudKeyStore.load(this);
                if(key.isEmpty())throw new Exception("请先配置百炼 API Key");
                Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.RGB_565);
                try{
                    Canvas canvas=new Canvas(bitmap);
                    canvas.drawColor(0xFFFFFFFF);
                    Paint p=new Paint();
                    p.setColor(0xFF101010);p.setTextSize(28);p.setAntiAlias(true);
                    canvas.drawText("A",22,43,p);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG,75,bytes);
                    byte[] jpeg=bytes.toByteArray();
                    TaskCatalog.Task probe=new TaskCatalog.Task(
                        "model-probe","视觉连接测试",
                        "这是一张测试图片。请只用一句简短中文确认你能看到图片。",
                        TaskCatalog.VISION,false,"✦");
                    CloudVisionClient.Result result=CloudVisionClient.recognize(
                        jpeg,key,probe,chosen,null);
                    if(modelUsage!=null)modelUsage.recordAsync(chosen,result.serverModel,
                        result.promptTokens,result.completionTokens,true);
                    handler.post(()->{
                        if(!uiActive)return;
                        setResultStatus("模型连接成功："+chosen);
                        toastShort("连接成功："+chosen+"（已计入本机用量）");
                    });
                }finally{bitmap.recycle();}
            }catch(Exception error){
                if(modelUsage!=null)modelUsage.recordAsync(chosen,"",-1,-1,false);
                final String message=error.getMessage()==null?
                    error.getClass().getSimpleName():error.getMessage();
                handler.post(()->{
                    if(!uiActive)return;
                    setResultStatus("模型测试失败："+chosen);
                    toastShort("连接失败："+chosen+"；"+message);
                });
            }finally{modelTestBusy.set(false);}
        });
    }

    String imagePolicyDescription(){
        return "auto".equals(imagePolicy)?"自动720p／必要时1080p":
            ("720".equals(imagePolicy)?"固定720p":"固定1080p");
    }
    void cycleImagePolicy(){
        imagePolicy="auto".equals(imagePolicy)?"720":("720".equals(imagePolicy)?"1080":"auto");
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(PREF_IMAGE_POLICY,imagePolicy).apply();
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

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(BlueStarFontScale.wrap(base));
    }
    @Override public void onCreate(Bundle state) {
        Intent launch=getIntent();
        settingsOnly=launch!=null&&launch.getBooleanExtra("bluestar_launch_settings",false);
        super.onCreate(state);
        diagnostics=new RuntimeDiagnostics(this);
        c110WifiBootstrap=new C110WifiBootstrap(this,this::setC110SettingsStatus);
        settingsC110DeviceControls=new C110DeviceControls(this,this::setC110SettingsStatus);
        modelUsage=new ModelUsageLedger(this);
        SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        // Home-first experiment: USB attachment must not launch preview unasked.
        updateUsbAttachAlias(prefs.getBoolean(PREF_USB_AUTO_LAUNCH,false));
        usingPhoneCamera=(!settingsOnly&&launch!=null&&
            launch.getBooleanExtra("bluestar_launch_preview",false))?
            !hasConnectedUsbVideoDevice():prefs.getBoolean(PREF_PHONE_CAMERA,false);
        phoneFrontFacing=prefs.getBoolean(PREF_FRONT_CAMERA,false);
        phoneHighRes=prefs.getBoolean(PREF_PHONE_HD_STILL,false);
        phoneHdUploadMode=PhoneStillUploadPolicy.normalize(
            prefs.getInt(PREF_PHONE_HD_UPLOAD_MODE,PhoneStillUploadPolicy.ORIGINAL));
        previewRotation=prefs.getInt(PREF_PREVIEW_ROTATION,0);
        previewMirror=prefs.getBoolean(PREF_PREVIEW_MIRROR,false);
        previewVerticalFlip=prefs.getBoolean(PREF_PREVIEW_FLIP,false);
        usbPreviewCorrectMirror=prefs.getBoolean(PREF_USB_PREVIEW_CORRECT_MIRROR,true);
        feedbackSound=prefs.getBoolean(PREF_SOUND,true);
        feedbackVibration=prefs.getBoolean(PREF_HAPTIC,true);
        initMetalClick();
        speechRate=prefs.getFloat(PREF_SPEECH_RATE,1f);
        followSystemSpeechRate=prefs.getBoolean(PREF_SPEECH_DEFAULT,true);
        loadCameraProfile();
        imagePolicy=prefs.getString(PREF_IMAGE_POLICY,"auto");
        String storedModel=prefs.getString(PREF_VISION_MODEL,VisionModelCatalog.DEFAULT);
        visionModel=VisionModelCatalog.normalize(storedModel);
        if(!visionModel.equals(storedModel))
            prefs.edit().putString(PREF_VISION_MODEL,visionModel).apply();
        String storedOcrModel=prefs.getString(PREF_OCR_MODEL,OcrModelCatalog.DEFAULT);
        ocrModel=OcrModelCatalog.normalize(storedOcrModel);
        if(!ocrModel.equals(storedOcrModel))
            prefs.edit().putString(PREF_OCR_MODEL,ocrModel).apply();
        ocrSpeedMode=OcrSpeedExperiment.normalize(
            prefs.getString(OcrSpeedExperiment.PREF,OcrSpeedExperiment.DEFAULT));
        webSearchEnabled=prefs.getBoolean(SearchRoutePolicy.PREF,
            SearchRoutePolicy.DEFAULT_ENABLED);
        findingTarget=FindingTargetStore.current(this);
        ttsEngine=prefs.getString(PREF_TTS_ENGINE,"");
        if(ttsEngine==null)ttsEngine="";
        refreshTask(TaskCatalog.selectedId(this));
        attachPhonePreviewAfterUsbInit();
        applyPreviewTransform();
        phoneController=new InternalCameraController(this,phoneTexture,new InternalCameraController.Listener(){
            @Override public boolean shouldReceiveFrame() {
                return uiActive && usingPhoneCamera &&
                    pendingPhoneFrameToken==requestVersion.get()&&
                    (readingScreen==null||readingAwaitingFrame.get());
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
                    // Starting the camera only starts a preview. Never capture from this
                    // callback: the book reader must wait for the user's tap.
                    phonePreviewReceived=true;
                    if(!phoneFirstFrameRecorded){
                        phoneFirstFrameRecorded=true;
                        if(diagnostics!=null)diagnostics.event("camera","phone_preview_ready_ms",
                            phoneStartAtMs>0?
                                Math.max(0,SystemClock.elapsedRealtime()-phoneStartAtMs):-1,"ok");
                    }
                    if(phonePreviewStatus!=null)phonePreviewStatus.setVisibility(View.GONE);
                });
            }
            @Override public void onError(String message){
                runOnUiThread(()->{
                    if(usingPhoneCamera){
                        if(readingScreen!=null&&readingPreviewActive)
                            failReadingCapture("手机相机启动失败："+message);
                        else showPhonePreviewStatus("手机摄像头启动失败："+message,true);
                    }
                });
            }
        });
        initializeSpeech();
        if(usingPhoneCamera){
            phoneTexture.setVisibility(View.VISIBLE);
            if(cameraTextureView!=null)cameraTextureView.setVisibility(View.INVISIBLE);
        }
        setResultStatus("");
        // A USB-started activity may be created before AUSBC grants permission;
        // wait for its actual onConnect hook instead of opening blindly.
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(readingScreen==null&&intent!=null&&UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())){
            if(diagnostics!=null)diagnostics.event("usb","intent_attach",1,"ok");
            queueUsbAttachReconcile();
        }
        // USBMonitor still owns permission, UVC opening and physical detach.
        // The system intent only schedules a check; no second receiver is added.
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
                @Override public void onDone(String utteranceId){
                    handler.post(()->{
                        if(readingScreen==null||readingNarration==null)return;
                        String prefix="reading-"+readingSpeechToken+"-";
                        if(utteranceId==null||!utteranceId.startsWith(prefix))return;
                        try{readingNarration.onDone(Integer.parseInt(
                            utteranceId.substring(prefix.length())));}
                        catch(NumberFormatException ignored){}
                    });
                }
                @Override public void onError(String utteranceId){
                    if(diagnostics!=null)diagnostics.event("tts","engine_error",0,"failed");
                }
                @Override public void onAudioAvailable(String utteranceId,byte[] audio){
                    if(audio!=null&&audio.length>0)recordTtsProgress(utteranceId,true);
                }
            });
        }catch(Exception error){Log.w(TAG,"TTS initialization unavailable",error);}
    }

    private void recordTtsProgress(String id,boolean audio){
        int token=speechRequestToken;
        if(id==null||(!id.equals("vision-"+token+"-0")&&
            !id.equals("follow-"+token+"-0"))||
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
        if(diagnostics!=null)diagnostics.event("tts",
            audio?"first_audio_callback_ms":"onstart_ms",ms,"callback_only");
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

    private String filterForSpeech(String message,TaskCatalog.Task task){
        if(message==null)return "";
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        boolean all=p.getBoolean("speech_filter_all_tasks",false);
        if(!all&&(task==null||!TaskCatalog.OCR.equals(task.engine)))return message;
        return SpeechFilter.apply(message,
            p.getBoolean("speech_skip_latin",false),
            p.getBoolean("speech_skip_digits",false),
            p.getBoolean("speech_skip_symbols",false));
    }
    private void speakUnfiltered(String message){
        if(message==null||message.trim().isEmpty())return;
        if(tts!=null&&ttsReady)
            tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,
                "bluestar-"+System.nanoTime());
        else toastShort("本地语音引擎尚未就绪");
    }
    private void speak(String message){
        String text=filterForSpeech(message,currentTask);
        if(text.trim().isEmpty()){
            toastShort("过滤后没有可朗读内容，请调整过滤开关");
            return;
        }
        speakUnfiltered(text);
    }
    /** Photo-related speech always uses a traceable first utterance ID, including OCR. */
    private void speakForCapture(String message,int token,boolean filtered){
        speakForCapture(message,token,filtered,currentTask);
    }
    private void speakForCapture(String message,int token,boolean filtered,
                                 TaskCatalog.Task actualTask){
        String text=filtered?filterForSpeech(message,actualTask):message;
        if(text==null||text.trim().isEmpty()){
            if(filtered)toastShort("没有可朗读内容，请调整过滤设置");
            return;
        }
        if(tts==null||!ttsReady){
            if(diagnostics!=null)diagnostics.event("tts","engine",0,"unavailable");
            toastShort("本地TTS未就绪，请检查已安装语音引擎");
            return;
        }
        int result=tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,
            "vision-"+token+"-0");
        if(result!=TextToSpeech.SUCCESS&&diagnostics!=null)
            diagnostics.event("tts","enqueue",0,"failed");
    }

    /** Preserve camera/source switching and attachment order from 2.0. */
    private void switchCameraSource(boolean phone) {
        boolean wasPhone=usingPhoneCamera;
        if(phone&&!wasPhone){
            // Stop only the old selected USB stream. Its asynchronous CLOSED
            // callback must not resurrect UVC behind the phone preview.
            stopUsb();
            usbReopenWanted=false;
        }
        invalidate();
        pendingPhoneFrameToken=-1;
        if(phoneController!=null)phoneController.cancelFreshFrame();
        usbFirstFrameRecorded=false;
        phoneFirstFrameRecorded=false;
        usingPhoneCamera=phone;
        latestPhoneFrame=null;
        latestUsbFrame=null;
        resetUsbFpsState();
        if(diagnostics!=null){
            diagnostics.begin(phone?"phone_switch":"usb_switch");
            diagnostics.event("usb","selected_source",phone?1:0,"ok");
        }
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
        }else{
            phonePreviewReceived=false;
            if(phonePreviewStatus!=null)phonePreviewStatus.setVisibility(View.GONE);
            applyPreviewTransform();
            usbReopenWanted=true;
            startUsbFpsMonitoring();
            handler.post(this::resumeUsb);
        }
        String message=phone?(phoneFrontFacing?"已切换手机前置摄像头":"已切换手机后置摄像头"):
            "已切换USB外接摄像头";
        if(rootFrame!=null)rootFrame.announceForAccessibility(message);
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
        if(!usingPhoneCamera||phoneController==null||phoneTexture==null||!uiActive||
            (readingScreen!=null&&!readingPreviewActive))return;
        if(!phoneController.hasPermission()){
            showPhonePreviewStatus("请允许蓝星向导使用手机摄像头",false);
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_PHONE_CAMERA);
            return;
        }
        phoneTexture.setVisibility(View.VISIBLE);
        phoneTexture.bringToFront();
        if(cameraTextureView!=null)cameraTextureView.setVisibility(View.INVISIBLE);
        phonePreviewReceived=false;
        phoneFirstFrameRecorded=false;
        phoneStartAtMs=SystemClock.elapsedRealtime();
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
                            phoneController.start(phoneFrontFacing,phoneHighRes);
                    }
                    @Override public void onViewDetachedFromWindow(View v){}
                });
                return;
            }
            phoneController.start(phoneFrontFacing,phoneHighRes);
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==C110WifiBootstrap.PERMISSION_REQUEST){
            if(c110WifiBootstrap!=null)
                c110WifiBootstrap.onPermissionResult(results.length>0&&
                    results[0]==PackageManager.PERMISSION_GRANTED);
            return;
        }
        if(requestCode==C110WifiBootstrap.SCAN_PERMISSION_REQUEST){
            if(c110WifiBootstrap!=null)
                c110WifiBootstrap.onScanPermissionResult(this,results.length>0&&
                    results[0]==PackageManager.PERMISSION_GRANTED);
            return;
        }
        if(requestCode==REQUEST_PHONE_CAMERA&&c110WifiBootstrap!=null)
            c110WifiBootstrap.startAtLaunch();
        if(requestCode==REQUEST_VOICE_MIC){
            voicePress.interrupt();
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)
                toastShort("麦克风已授权，请重新按住 AI 对话再说话");
            else toastShort("需要麦克风权限才能按住语音对话");
            return;
        }
        if(requestCode!=REQUEST_PHONE_CAMERA)return;
        if(readingScreen!=null&&!readingPreviewActive)return;
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
        } else {
            if(readingScreen!=null&&readingPreviewActive)
                failReadingCapture("未获得相机权限，无法显示预览");
            else Toast.makeText(this,"请允许相机权限后使用摄像头",Toast.LENGTH_LONG).show();
        }
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
        UsbFrameOrientation.Plan orientation=UsbFrameOrientation.forSettings(
            previewRotation,previewMirror,previewVerticalFlip,usbPreviewCorrectMirror);
        boolean quarterTurn=orientation.quarterTurn;
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
        cameraTextureView.setRotation(orientation.rotation);
        int vw=cameraTextureView.getWidth(),vh=cameraTextureView.getHeight();
        cameraTextureView.setPivotX(vw/2f);cameraTextureView.setPivotY(vh/2f);
        cameraTextureView.setScaleX(zoom);
        // Android view-local Y becomes display X after a 90/270 turn.
        cameraTextureView.setScaleY(zoom*(orientation.previewLocalFlip?-1f:1f));
        if(vw>0&&vh>0){
            Matrix matrix=new Matrix();
            if(orientation.previewLocalMirror)
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

    private void applyQueuedPhoneZoom(){
        if(usingPhoneCamera&&phoneController!=null)phoneController.setZoom(zoom);
        lastZoomSentMs=SystemClock.elapsedRealtime();
    }

    private void setCameraZoom(float next){
        float limit=3f;
        if(usingPhoneCamera&&phoneController!=null)
            limit=Math.max(1f,Math.min(3f,phoneController.zoomLimit()));
        float applied=Math.max(1f,Math.min(limit,next));
        if(Math.abs(applied-zoom)<.01f)return;
        zoom=applied;
        if(usingPhoneCamera&&phoneController!=null){
            long now=SystemClock.elapsedRealtime();
            if(now-lastZoomSentMs>60L){
                phoneController.setZoom(zoom);lastZoomSentMs=now;
            }else{
                handler.removeCallbacks(delayedPhoneZoom);
                handler.postDelayed(delayedPhoneZoom,75L);
            }
        }else applyPreviewTransform();
    }

    @Override protected UsbDevice getDefaultCamera(){
        UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);
        if(manager==null)return null;
        Map<String,UsbDevice> devices=manager.getDeviceList();
        for(UsbDevice device:devices.values())
            if(device.getVendorId()==0xEBA4&&device.getProductId()==0x6579)return device;
        return null;
    }

    @Override protected void onUsbDeviceAttachedForApp(UsbDevice device){
        if(settingsOnly)return;
        boolean video=isUsbVideoDevice(device);
        if(diagnostics!=null)diagnostics.event("usb","device_attached",
            video?1:0,"ok");
        if(video&&readingScreen==null)queueUsbAttachReconcile();
    }
    @Override protected void onUsbDeviceConnectedForApp(UsbDevice device){
        if(!isUsbVideoDevice(device)||settingsOnly)return;
        if(readingScreen!=null){
            // During manual reading preview, AUSBC onConnect owns the initial
            // USB open. Never let the queued resumeUsb race a second native open.
            if(!usingPhoneCamera&&readingPreviewActive)usbReopenWanted=false;
            return;
        }
        if(diagnostics!=null)diagnostics.event("usb","device_connected",1,"ok");
        if(UsbAutoSwitchPolicy.useUsbOnConnect(isUsbAutoSwitch(),usbForeground,
           true,usingPhoneCamera,manualPhoneUntilUsbDetach)){
            saveCameraProfile();
            activeUsbDeviceName=device.getDeviceName();
            switchCameraSource(false);
            // The AUSBC onConnect below owns this initial openCamera().
            // Suppress our queued resumeUsb(): duplicate native open is unsafe.
            usbReopenWanted=false;
        }else if(!usingPhoneCamera){
            activeUsbDeviceName=device.getDeviceName();
            // AUSBC onConnect will open this camera; pending onResume must not.
            usbReopenWanted=false;
        }
    }
    @Override protected boolean shouldAutoOpenUsbCameraForApp(UsbDevice device){
        return !settingsOnly&&isUsbVideoDevice(device)&&UsbAutoSwitchPolicy.allowNativeUsbOpen(
            usbForeground,!usingPhoneCamera);
    }
    @Override protected void onUsbDeviceDetachedForApp(UsbDevice device){
        if(!isUsbVideoDevice(device))return;
        if(readingScreen!=null){
            if(!usingPhoneCamera&&readingPreviewActive)
                handler.post(()->failReadingCapture("USB摄像头已断开，请重新连接"));
            return;
        }
        if(manualPhoneUntilUsbDetach&&device.getDeviceId()==manualPhoneUsbDeviceId){
            manualPhoneUntilUsbDetach=false;
            manualPhoneUsbDeviceId=-1;
        }
        boolean same=activeUsbDeviceName==null||
            activeUsbDeviceName.equals(device.getDeviceName());
        if(diagnostics!=null)diagnostics.event("usb","device_detached",same?1:0,"ok");
        if(UsbAutoSwitchPolicy.usePhoneOnDetach(isUsbAutoSwitch(),
            !usingPhoneCamera,same)){
            usbKnownOpened=false;
            usbStopPending=false;usbReopenWanted=false;
            activeUsbDeviceName=null;
            saveCameraProfile();
            switchCameraSource(true);
        }else if(same){
            activeUsbDeviceName=null;
        }
    }

    @Override public void onCameraState(MultiCameraClient.ICamera self,
                                        ICameraStateCallBack.State code,String msg){
        if(code==ICameraStateCallBack.State.OPENED){
            if(readingScreen!=null&&!readingPreviewActive&&!readingAwaitingFrame.get())
                handler.post(this::suspendReadingCamera);
            usbOpenedAtMs=SystemClock.elapsedRealtime();
            usbKnownOpened=true;
            startUsbFpsMonitoring();
            lastUsbFirstFrameMs=-1;
            usbFirstFrameRecorded=false;
            usbStopPending=false;usbReopenWanted=false;
            if(diagnostics!=null){
                diagnostics.event("usb","opened",1,"ok");
                diagnostics.event("usb","open_elapsed_ms",
                    usbOpenRequestAtMs>0?Math.max(0,usbOpenedAtMs-usbOpenRequestAtMs):-1,"ok");
            }
            int generation=usbPictureGeneration.incrementAndGet();
            handler.postDelayed(()->restoreUsbPictureControls(generation),700L);
            if(!previewCallbackAdded){
                addPreviewDataCallBack(previewCallback);
                previewCallbackAdded=true;
            }
            handler.post(()->{
                if(!usingPhoneCamera){loadCameraProfile();applyPreviewTransform();}
            });
        }else if(code==ICameraStateCallBack.State.CLOSED){
            usbKnownOpened=false;
            usbPictureGeneration.incrementAndGet();
            if(diagnostics!=null)diagnostics.event("usb","close_elapsed_ms",
                usbStopRequestAtMs>0?Math.max(0,SystemClock.elapsedRealtime()-usbStopRequestAtMs):-1,
                "closed");
            previewCallbackAdded=false;
            latestUsbFrame=null;
            if(usbStopPending){
                usbStopPending=false;
                if(usbForeground&&usbReopenWanted)
                    handler.post(this::resumeUsb);
            }
        }else if(code==ICameraStateCallBack.State.ERROR){
            usbKnownOpened=false;
            usbPictureGeneration.incrementAndGet();
            if(diagnostics!=null)diagnostics.event("usb","state",0,"failed");
            Log.w(TAG,"UVC open error: "+msg);
            if(readingScreen!=null&&readingPreviewActive&&!usingPhoneCamera)
                handler.post(()->failReadingCapture("USB摄像头启动失败，请重新预览"));
            runOnUiThread(()->Toast.makeText(this,"USB打开失败："+msg,Toast.LENGTH_LONG).show());
        }
    }

    private void stopUsb(){
        if(usbStopPending||!isCameraOpened())return;
        MultiCameraClient.ICamera camera=getCurrentCamera();
        if(camera==null)return;
        usbStopPending=true;
        usbReopenWanted=true;
        usbStopRequestAtMs=SystemClock.elapsedRealtime();
        if(diagnostics!=null)diagnostics.event("usb","close_requested",0,"ok");
        camera.closeCamera();
    }
    private void resumeUsb(){
        if(!usbForeground||!usbReopenWanted||usbStopPending||usingPhoneCamera||
           (readingScreen!=null&&!readingPreviewActive&&!readingAwaitingFrame.get())||
           cameraTextureView==null||!cameraTextureView.isAvailable()||isCameraOpened())return;
        MultiCameraClient.ICamera camera=getCurrentCamera();
        if(camera==null||camera.getUsbDevice()==null)return;
        UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);
        if(manager==null||!manager.hasPermission(camera.getUsbDevice()))return;
        usbReopenWanted=false;
        usbOpenRequestAtMs=SystemClock.elapsedRealtime();
        if(diagnostics!=null)diagnostics.event("usb","open_requested",
            usbResumeAtMs>0?usbOpenRequestAtMs-usbResumeAtMs:0,"ok");
        camera.openCamera(cameraTextureView,getCameraRequest());
    }


    interface UsbPictureCallback {
        void done(boolean[] supported,int[] values,String message);
    }
    boolean isUsbSelected(){return !usingPhoneCamera;}
    private String usbPictureProfile(UsbDevice dev){
        if(dev==null)return "usb_pic_unknown_";
        return "usb_pic_"+dev.getVendorId()+"_"+dev.getProductId()+"_";
    }
    private CameraUVC currentUsbControlCamera(){
        if(!usbForeground||usingPhoneCamera||!usbKnownOpened)return null;
        MultiCameraClient.ICamera cam=getCurrentCamera();
        if(!(cam instanceof CameraUVC)||!cam.isCameraOpened())return null;
        return (CameraUVC)cam;
    }
    private void deliverUsbPicture(UsbPictureCallback cb,boolean[] support,
                                   int[] values,String status){
        if(cb!=null)handler.post(()->cb.done(support,values,status));
    }
    private boolean isUsbControlUsable(CameraUVC camera,int kind){
        try{return UsbPictureHardware.supported(camera,kind);}
        catch(Exception failure){Log.w(TAG,"USB picture capability unavailable",failure);return false;}
    }
    void queryUsbPictureControls(UsbPictureCallback callback){
        usbPictureExecutor.execute(()->{
            boolean[] support=new boolean[UsbPictureLevels.COUNT];
            int[] values=new int[UsbPictureLevels.COUNT];
            java.util.Arrays.fill(values,-1);
            CameraUVC cam=currentUsbControlCamera();
            if(cam==null){
                deliverUsbPicture(callback,support,values,
                    "USB摄像头尚未打开，请选择USB并保持预览运行");
                return;
            }
            for(int kind=0;kind<UsbPictureLevels.COUNT;kind++){
                if(!isUsbControlUsable(cam,kind))continue;
                try{
                    int value=UsbPictureHardware.read(cam,kind);
                    if(value<0)continue;
                    support[kind]=true;values[kind]=value;
                }catch(Exception failure){
                    Log.w(TAG,"USB picture read failed for "+kind,failure);
                }
            }
            deliverUsbPicture(callback,support,values,
                "只显示设备确实支持的控制项。修改直接作用于UVC硬件画面。");
        });
    }
    void setUsbPictureControl(int kind,int value,UsbPictureCallback callback){
        if(!UsbPictureLevels.valid(kind))return;
        int requested=UsbPictureLevels.clamp(value);
        int generation=usbPictureGeneration.get();
        usbPictureExecutor.execute(()->{
            CameraUVC cam=currentUsbControlCamera();
            if(cam==null||generation!=usbPictureGeneration.get()){
                deliverUsbPicture(callback,null,null,"摄像头正在关闭或重连，没有更改");
                return;
            }
            if(!isUsbControlUsable(cam,kind)){
                deliverUsbPicture(callback,null,null,"该USB设备不支持这个硬件调节项目");
                return;
            }
            try{
                UsbPictureHardware.write(cam,kind,requested);
                int readback=UsbPictureHardware.read(cam,kind);
                if(generation!=usbPictureGeneration.get())return;
                if(readback<0||Math.abs(readback-requested)>4){
                    deliverUsbPicture(callback,null,null,
                        "相机未确认新数值，可能不支持；请以实时画面为准");
                    return;
                }
                String pref=usbPictureProfile(cam.getUsbDevice());
                getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                    .putInt(pref+UsbPictureLevels.key(kind),readback).apply();
                if(diagnostics!=null){
                    diagnostics.event("usb","picture_kind",kind,"ok");
                    diagnostics.event("usb","picture_level",readback,"ok");
                }
                deliverUsbPicture(callback,null,null,
                    UsbPictureLevels.LABELS[kind]+"已设为 "+readback+"%");
            }catch(Exception failed){
                Log.w(TAG,"USB hardware picture control failed",failed);
                deliverUsbPicture(callback,null,null,"硬件控制失败；未修改保存设置");
            }
        });
    }
    void resetUsbPictureControls(UsbPictureCallback callback){
        int generation=usbPictureGeneration.get();
        usbPictureExecutor.execute(()->{
            CameraUVC cam=currentUsbControlCamera();
            if(cam==null||generation!=usbPictureGeneration.get()){
                deliverUsbPicture(callback,null,null,"请先打开USB摄像头再恢复默认");
                return;
            }
            int success=0;
            try{
                for(int kind=0;kind<UsbPictureLevels.COUNT;kind++){
                    if(!isUsbControlUsable(cam,kind))continue;
                    UsbPictureHardware.reset(cam,kind);success++;
                }
                String prefix=usbPictureProfile(cam.getUsbDevice());
                SharedPreferences.Editor edit=getSharedPreferences(PREFS,MODE_PRIVATE).edit();
                for(int kind=0;kind<UsbPictureLevels.COUNT;kind++)
                    edit.remove(prefix+UsbPictureLevels.key(kind));
                edit.apply();
                if(diagnostics!=null)diagnostics.event("usb","picture_reset_count",success,"ok");
                deliverUsbPicture(callback,null,null,
                    "已请求恢复 "+success+" 项设备默认值；请重新读取确认");
            }catch(Exception failure){
                deliverUsbPicture(callback,null,null,"恢复默认失败，请重试");
            }
        });
    }
    private void restoreUsbPictureControls(int generation){
        usbPictureExecutor.execute(()->{
            CameraUVC cam=currentUsbControlCamera();
            if(cam==null||generation!=usbPictureGeneration.get())return;
            String prefix=usbPictureProfile(cam.getUsbDevice());
            SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
            int restored=0;
            for(int kind=0;kind<UsbPictureLevels.COUNT;kind++){
                String key=prefix+UsbPictureLevels.key(kind);
                if(!prefs.contains(key)||generation!=usbPictureGeneration.get())continue;
                if(!isUsbControlUsable(cam,kind))continue;
                try{
                    UsbPictureHardware.write(cam,kind,prefs.getInt(key,50));
                    restored++;
                }catch(Exception failed){Log.w(TAG,"USB picture restore failed",failed);}
            }
            if(diagnostics!=null&&restored>0)
                diagnostics.event("usb","picture_restore_count",restored,"ok");
        });
    }

    /** Short, high-transient original PCM assets, on the normal media volume path. */
    private void initMetalClick(){
        try{
            AudioAttributes attributes=new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            clickPool=new SoundPool.Builder().setMaxStreams(2)
                .setAudioAttributes(attributes).build();
            clickPool.setOnLoadCompleteListener((pool,id,status)->{
                if(status==0)soundLoaded.add(id);
                else Log.w(TAG,"Feedback sound loading failed: sampleId="+id);
            });
            metalClickSample=clickPool.load(this,R.raw.c_metal_clack_ding,1);
            sendWhooshSample=clickPool.load(this,R.raw.voice_send_whoosh,1);
        }catch(RuntimeException error){
            Log.w(TAG,"Feedback sound loading failed",error);
        }
    }

    private void playSound(int sample){
        if(clickPool!=null&&sample>0&&soundLoaded.contains(sample)){
            int stream=clickPool.play(sample,1f,1f,2,0,1f);
            if(stream==0)Log.w(TAG,"SoundPool declined sound sample "+sample);
        }else Log.w(TAG,"Sound not yet loaded: sampleId="+sample);
    }
    private void signalCapture(){
        SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        feedbackSound=prefs.getBoolean(PREF_SOUND,true);
        feedbackVibration=prefs.getBoolean(PREF_HAPTIC,true);
        if(feedbackSound)playSound(metalClickSample);
        if(feedbackVibration)vibrateClick(false);
    }
    void previewCaptureFeedback(){signalCapture();}

    private void vibrateClick(boolean strong){
        if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(PREF_HAPTIC,true))return;
        try{
            Vibrator vib=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(vib==null||!vib.hasVibrator())return;
            if(android.os.Build.VERSION.SDK_INT>=29)
                vib.vibrate(VibrationEffect.createPredefined(strong?
                    VibrationEffect.EFFECT_HEAVY_CLICK:VibrationEffect.EFFECT_CLICK));
            else vib.vibrate(VibrationEffect.createOneShot(strong?24:12,
                    strong?235:210));
        }catch(RuntimeException error){
            Log.w(TAG,"Haptic click unavailable",error);
        }
    }
    private void vibrationForVoiceStart(){vibrateClick(true);}
    private void vibrationForTargetFound(){
        if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(PREF_HAPTIC,true))return;
        try{
            Vibrator vib=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(vib!=null&&vib.hasVibrator())
                vib.vibrate(VibrationEffect.createWaveform(new long[]{0,20,80,20},-1));
        }catch(RuntimeException ignored){}
    }
    void previewVoiceSendFeedback(){playVoiceSendSound();}
    void previewVoiceStartHaptic(){vibrationForVoiceStart();}
    String lastMicDiagnostic(){return lastMicDiagnostic;}
    private void playVoiceSendSound(){
        // Only called after a complete, valid user-released WAV has been captured.
        if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(PREF_SOUND,true))
            playSound(sendWhooshSample);
    }

    /** One gesture for all intentions: press records, release sends, swipe up cancels. */
    private boolean handleVoiceButtonTouch(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                voicePress.down(startVoiceHold());
                return true;
            case MotionEvent.ACTION_MOVE:
                if(voicePress.move(event.getY(),-dp(100)))
                    setResultStatus("松开取消录音");
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                if(voicePress.interrupt()==VoicePressState.Decision.CANCEL){
                    voiceRecorder.cancel();
                    voiceSequence.incrementAndGet();
                    voiceListening=false;
                    setResultStatus("录音已取消");
                }
                return true;
            case MotionEvent.ACTION_UP:
                VoicePressState.Decision result=voicePress.up();
                if(result==VoicePressState.Decision.CANCEL){
                    voiceRecorder.cancel();
                    voiceSequence.incrementAndGet();
                    voiceListening=false;
                    setResultStatus("录音已取消，不发送");
                }else if(result==VoicePressState.Decision.SEND){
                    voiceRecorder.finish();
                    setResultStatus("已松手，正在整理录音…");
                }
                return true;
            default:return true;
        }
    }

    /** The target search command DOES NOT require a previously captured photo. */
    private boolean startVoiceHold(){
        if(!uiActive)return false;
        if(cloudBusy){
            toastShort("AI正在处理上一条消息，请稍后再按住");return false;
        }
        if(voiceRecorder.isBusy()||voiceListening){
            toastShort("麦克风仍在录音");return false;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_VOICE_MIC);
            return false;
        }
        if(tts!=null)tts.stop();
        voiceListening=true;
        if(diagnostics!=null)diagnostics.event("voice","hold_started",0,"ok");
        final int sequence=voiceSequence.incrementAndGet();
        setResultStatus("正在连接麦克风…按住说话，松开发送");
        boolean started=voiceRecorder.begin(this,new VoiceRecorder.Listener(){
            @Override public void onReady(String mic){
                handler.post(()->{
                    if(sequence!=voiceSequence.get()||!uiActive)return;
                    lastMicDiagnostic="正在录音；当前麦克风："+mic;
                    if(voicePress.isHeld())setResultStatus("正在录音，松开发送");
                });
            }
            @Override public void onFinished(byte[] wav,int durationMs,int peak,int rms,String mic){
                handler.post(()->{
                    if(sequence!=voiceSequence.get()||!uiActive)return;
                    voiceListening=false;
                    cloudBusy=true;
                    lastMicDiagnostic="输入："+mic+"；录音："+durationMs+"毫秒；峰值："+peak+"；均方根："+rms;
                    if(diagnostics!=null){
                        diagnostics.event("voice","duration_ms",durationMs,"ok");
                        diagnostics.event("voice","peak",peak,peak>0?"ok":"silent");
                        diagnostics.event("voice","rms",rms,"ok");
                    }
                    // No arbitrary RMS gate. Quiet recordings are sent to the ASR itself.
                    Log.i(TAG,"Voice microphone="+mic+" durationMs="+durationMs+
                        " peak="+peak+" rms="+rms+" wavBytes="+wav.length);
                    setResultStatus("语音已采集，正在提交识别…");
                    final int photoToken=requestVersion.get();
                    encodingTask=encoder.submit(()->{
                        try{
                            CloudProviderStore.Config asrService=CloudProviderStore.resolve(
                                MainActivity.this,CloudProviderStore.ASR,"qwen3-asr-flash");
                            handler.post(()->{
                                if(uiActive&&sequence==voiceSequence.get()&&
                                    photoToken==requestVersion.get())
                                    playVoiceSendSound();
                            });
                            long asrAt=SystemClock.elapsedRealtime();
                            String transcript=CloudAsrClient.recognize(wav,asrService,null);
                            // ASR currently exposes no reliable Token usage: record call only.
                            if(modelUsage!=null)modelUsage.recordAsync(
                                asrService.model,"",-1,-1,true);
                            if(diagnostics!=null)diagnostics.event("asr","response_ms",
                                SystemClock.elapsedRealtime()-asrAt,"ok");
                            handler.post(()->{
                                if(!uiActive||sequence!=voiceSequence.get()||
                                    photoToken!=requestVersion.get())return;
                                cloudBusy=false;
                                routeSpokenRequest(transcript);
                            });
                        }catch(Exception error){
                            if(modelUsage!=null)modelUsage.recordAsync(
                                "qwen3-asr-flash","",-1,-1,false);
                            handler.post(()->{
                                if(!uiActive||sequence!=voiceSequence.get()||
                                    photoToken!=requestVersion.get())return;
                                cloudBusy=false;
                                if(diagnostics!=null){
                                    diagnostics.event("asr","request",0,"failed");
                                    diagnostics.event("asr","http_status",
                                        diagnosticHttpStatus(error),"failed");
                                }
                                setResultStatus("语音上传或识别失败，请重试");
                                toastShort("语音识别失败："+error.getMessage());
                                speakUnfiltered("语音识别失败，请再试一次。");
                            });
                        }
                    });
                });
            }
            @Override public void onFailure(String reason){
                handler.post(()->{
                    if(sequence!=voiceSequence.get()||!uiActive)return;
                    voiceListening=false;
                    lastMicDiagnostic="录音失败："+reason;
                    if(diagnostics!=null)diagnostics.event("voice","record",0,"failed");
                    setResultStatus("录音失败："+reason);
                    toastShort(reason);
                });
            }
        });
        if(!started){
            voiceListening=false;
            toastShort("麦克风仍在处理上一段语音");
            return false;
        }
        // Haptic fires at the first accepted press, not after ACTION_UP.
        vibrationForVoiceStart();
        return true;
    }

    /** One conversation button: intent determines photo question vs ongoing target search. */
    private void routeSpokenRequest(String transcript){
        findingTarget=FindingTargetStore.current(this);
        VoiceAction action=VoiceAction.classify(transcript,findingTarget);
        if(action.type==VoiceAction.Type.STOP_FINDING){
            if(findingTarget.isEmpty()){
                setResultStatus("目前没有正在寻找的目标");
                speakUnfiltered("目前没有正在寻找的目标。");
            }else{
                setFindingTarget("");
                setResultStatus("已停止寻找，可以正常拍照识别");
                speakUnfiltered("好的，已停止寻找。");
            }
            return;
        }
        if(action.type==VoiceAction.Type.START_FINDING){
            setFindingTarget(action.target);
            setResultStatus("已开始寻找："+findingTarget+" · 拍照自动检查");
            speakUnfiltered("好的，正在帮你找"+findingTarget+"。继续走路时，点击画面拍照，我会逐张检查。");
            return;
        }
        if(action.type==VoiceAction.Type.OBSERVE_SCENE){
            TaskCatalog.Task observe=new TaskCatalog.Task("voice-scene","观察场景",
                "用户刚才说："+(transcript.length()>180?transcript.substring(0,180):transcript)
                +"。请只根据眼前真实可见的画面直接回应，"
                +"先说最重要的内容，尽量简短；看不清不要猜测。",
                TaskCatalog.VISION,false,"◉");
            captureOneFrame(observe);
            return;
        }
        if(action.type==VoiceAction.Type.READ_TEXT){
            TaskCatalog.Task read=new TaskCatalog.Task("voice-read","朗读文字",
                TaskCatalog.defaultInstruction(TaskCatalog.OCR),
                TaskCatalog.OCR,false,"▤");
            captureOneFrame(read);
            return;
        }
        if(action.type==VoiceAction.Type.REVIEW_IMAGE&&lastSessionJpeg==null){
            setResultStatus("没有可重新查看的照片，请先拍照");
            speakUnfiltered("还没有可重新查看的照片，请先拍照。");
            return;
        }
        // General spoken questions can use the same one-shot text-only path,
        // even before any photo has been taken. Never invent an unseen photo.
        askAboutCurrentImage(transcript,action.type==VoiceAction.Type.REVIEW_IMAGE);
    }

    private void showTextQuestionFallback(String reason){
        EditText question=new EditText(this);
        question.setSingleLine(false);
        question.setTextColor(Color.WHITE);
        question.setHint("请输入关于上一张照片的问题");
        LinearLayout content=new LinearLayout(this);
        content.setPadding(dp(22),dp(10),dp(22),dp(4));
        content.addView(question);
        new BlueStarDialogChoices.AppCompatBuilder(this).setTitle("AI对话")
            .setMessage(reason).setView(content).setNegativeButton("取消",null)
            .setPositiveButton("发送",(d,w)->{
                String text=question.getText().toString().trim();
                if(!text.isEmpty())routeSpokenRequest(text);
            }).show();
    }

    private void askAboutCurrentImage(String question,boolean recheckImage){
        if(cloudBusy||(recheckImage&&(lastSessionJpeg==null||
            lastSessionText==null||lastSessionText.isEmpty())))return;
        final boolean search=webSearchEnabled;
        final boolean hasContext=lastSessionText!=null&&!lastSessionText.isEmpty();
        final byte[] jpeg=recheckImage?lastSessionJpeg:null;
        final android.net.Network followupCloudNetwork=lastSessionCloudNetwork;
        final String observed=lastSessionText;
        final String taskName=lastSessionTask;
        final String prompt=recheckImage?
            FollowupPrompt.buildForImage(taskName,observed,followupTurns,question,search):
            (hasContext?FollowupPrompt.build(taskName,observed,followupTurns,question,search):
                FollowupPrompt.buildStandalone(followupTurns,question,search));
        final TaskCatalog.Task ask=recheckImage?new TaskCatalog.Task(
            "user-followup","重新看图",prompt,TaskCatalog.VISION,true,"✦"):null;
        invalidate();
        final int token=requestVersion.get();
        cloudBusy=true;
        speechRequestToken=token;
        speechCaptureAtMs=SystemClock.elapsedRealtime();
        ttsEngineStartMs=-1;
        ttsAudioCallbackMs=-1;
        setResultStatus(recheckImage?"正在重新看图…":
            (hasContext?"正在回答追问…":"正在AI对话…"));
        // Both photographed and text-only conversations use the user's
        // selected visual model. "联网智能追问" allows, but never forces, search.
        final String selectedModel=visionModel;
        final java.util.concurrent.atomic.AtomicInteger pieces=new java.util.concurrent.atomic.AtomicInteger();
        encodingTask=encoder.submit(()->{
            try{
                CloudProviderStore.Config service=CloudProviderStore.resolve(
                    this,CloudProviderStore.VISION,selectedModel);
                CloudVisionClient.PartListener speaker=part->handler.post(()->{
                        if(token!=requestVersion.get()||!uiActive||tts==null||!ttsReady)return;
                        String spoken=filterForSpeech(part,null);
                        if(spoken.trim().isEmpty())return;
                        int partIndex=pieces.getAndIncrement();
                        tts.speak(spoken,partIndex==0?TextToSpeech.QUEUE_FLUSH:
                            TextToSpeech.QUEUE_ADD,null,"follow-"+token+"-"+partIndex);
                    });
                CloudVisionClient.Result result=recheckImage?
                    CloudVisionClient.recognize(jpeg,service,ask,speaker,search,
                        followupCloudNetwork):
                    CloudVisionClient.recognizeText(prompt,service,speaker,search,
                        followupCloudNetwork);
                if(modelUsage!=null)modelUsage.recordAsync(service.model,result.serverModel,
                    result.promptTokens,result.completionTokens,true);
                handler.post(()->{
                    if(!uiActive||token!=requestVersion.get())return;
                    cloudBusy=false;
                    setResultStatus(recheckImage?"重新看图已完成":
                        (hasContext?"追问已完成":"AI对话已完成"));
                    lastCloudResult=result.text;
                    if(pieces.get()==0)speakForCapture(result.text,token,false);
                    if(diagnostics!=null){
                        diagnostics.event("followup","network_ms",result.networkMs,"ok");
                        diagnostics.event("followup","first_text_ms",result.firstTextMs,"ok");
                    }
                    followupTurns.add("问："+question+"，答："+result.text);
                    while(followupTurns.size()>3)followupTurns.remove(0);
                });
            }catch(Exception ex){
                if(modelUsage!=null)modelUsage.recordAsync(selectedModel,"",-1,-1,false);
                handler.post(()->{
                    if(!uiActive||token!=requestVersion.get())return;
                    cloudBusy=false;
                    if(diagnostics!=null){
                        diagnostics.event("followup","network",0,"failed");
                        diagnostics.event("followup","http_status",
                            diagnosticHttpStatus(ex),"failed");
                    }
                    setResultStatus("AI对话失败");
                    Toast.makeText(this,"追问失败："+ex.getMessage(),Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Reading operates independently from the short-text task grid; photos are never saved. */
    private void openReadingMode(){
        if(readingScreen!=null)return;
        try{
            if(readingLibrary==null)readingLibrary=new ReadingLibrary(this);
            invalidate();
            readingNarration=new ReadingNarrationQueue(new ReadingNarrationQueue.Sink(){
                @Override public boolean speak(String text,int index,boolean flush){
                    if(tts==null||!ttsReady)return false;
                    return tts.speak(text,flush?TextToSpeech.QUEUE_FLUSH:
                        TextToSpeech.QUEUE_ADD,null,"reading-"+readingSpeechToken+"-"+index)
                        ==TextToSpeech.SUCCESS;
                }
                @Override public void stop(){if(tts!=null)tts.stop();}
            });
            readingScreen=new ReadingScreen(this,rootFrame,readingLibrary,
                new ReadingScreen.Listener(){
                    @Override public void enterPreview(String id){enterReadingPreview(id);}
                    @Override public void takePhoto(String id){takeReadingPhoto(id);}
                    @Override public void cancelCapture(){cancelReadingCapture();}
                    @Override public void closeReading(){closeReadingMode();}
                    @Override public void stopReading(){stopReadingSession();}
                    @Override public void replay(String text){replayReadingText(text);}
                    @Override public void togglePause(){toggleReadingPause();}
                    @Override public void exportBook(String id){exportReadingBook(id);}
                    @Override public String lastBookId(){
                        return getSharedPreferences(PREFS,MODE_PRIVATE)
                            .getString("reader_last_book_v1","");
                    }
                    @Override public void selectedBook(String id){
                        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                            .putString("reader_last_book_v1",id).apply();
                    }
                });
            readingScreen.open();
            suspendReadingCamera();
            // RC25: Reading now opens only the minimal two-choice landing page.
            // Camera is activated only by New Reading or Continue inside a book.
        }catch(Exception error){
            Log.w(TAG,"Reader launch failed",error);
            readingScreen=null;
            toastShort("无法打开书架："+error.getMessage());
        }
    }
    private void suspendReadingCamera(){
        if(readingScreen==null)return;
        readingPreviewActive=false;
        // Stop the actual native UVC/Camera2 device, not merely hide a preview.
        usbForeground=false;
        usbReopenWanted=false;
        phoneStartSequence++;
        if(phoneController!=null)phoneController.stop();
        latestPhoneFrame=null;
        stopUsb();
        usbReopenWanted=false;
        latestUsbFrame=null;
    }
    private void stopReadingSession(){
        readingAwaitingFrame.set(false);
        readingPreviewActive=false;
        readingProcessing=false;
        pendingPhoneFrameToken=-1;
        invalidate();
        readingSpeechToken++;
        if(readingNarration!=null)readingNarration.reset();
        suspendReadingCamera();
    }
    private void closeReadingMode(){
        if(readingScreen==null)return;
        stopReadingSession();
        readingScreen.dispose();
        readingScreen=null;
        readingNarration=null;
        // Restore only the previously selected camera; do not switch sources.
        if(!uiActive)return;
        usbForeground=true;
        if(usingPhoneCamera)startPhoneCamera();
        else{
            usbReopenWanted=true;
            handler.post(this::resumeUsb);
        }
    }
    private void exportReadingBook(String id){
        if(readingLibrary==null)return;
        try{
            ReadingLibrary.Book b=readingLibrary.get(id);
            if(b.pages.isEmpty()){toastShort("这本书还没有可导出的文字");return;}
            readingExportBookId=id;
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType("text/plain");
            String title=b.title.replaceAll("[\\\\/:*?\\\"<>|]","_");
            save.putExtra(Intent.EXTRA_TITLE,title+".txt");
            startActivityForResult(save,REQUEST_READING_EXPORT);
        }catch(Exception e){toastShort("导出准备失败："+e.getMessage());}
    }
    private void replayReadingText(String text){
        if(readingScreen==null||readingNarration==null||text==null||text.isEmpty())return;
        readingSpeechToken++;
        readingNarration.reset();
        readingNarration.addText(text);
        readingScreen.setPaused(false);
        if(tts==null||!ttsReady)toastShort("语音引擎尚未就绪，文字已保存在书架");
    }
    private void toggleReadingPause(){
        if(readingScreen==null||readingNarration==null)return;
        if(readingNarration.isPaused())readingNarration.resume();
        else readingNarration.pause();
        readingScreen.setPaused(readingNarration.isPaused());
    }
    private void cancelReadingCapture(){
        if(readingScreen==null)return;
        stopReadingSession();
        readingScreen.cancelled();
    }
    private void failReadingCapture(String message){
        if(readingScreen==null||
            (!readingAwaitingFrame.getAndSet(false)&&!readingPreviewActive))return;
        readingProcessing=false;
        pendingPhoneFrameToken=-1;
        if(phoneController!=null)phoneController.cancelFreshFrame();
        suspendReadingCamera();
        readingScreen.failure(message);
        if(diagnostics!=null)diagnostics.event("reader","fresh_frame",0,"failed");
    }
    /** Next Page opens only the live view; no capture or model request here. */
    private void enterReadingPreview(String bookId){
        if(readingScreen==null||readingAwaitingFrame.get()||readingProcessing)return;
        try{
            readingLibrary.get(bookId);
            stopReadingSession();
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString("reader_last_book_v1",bookId).apply();
            readingPreviewActive=true;
            readingScreen.showPreview();
            if(usingPhoneCamera){
                startPhoneCamera();
            }else{
                usbForeground=true;
                usbReopenWanted=true;
                handler.post(this::resumeUsb);
            }
            if(diagnostics!=null)
                diagnostics.event("reader","preview_started",1,"ok");
        }catch(Exception e){
            suspendReadingCamera();
            if(readingScreen!=null)readingScreen.failure("摄像头预览无法启动："+e.getMessage());
        }
    }
    /** A real user click on the preview, NEVER an automatic timeout or onOpened. */
    private void takeReadingPhoto(String bookId){
        if(readingScreen==null||!readingScreen.isPreview()||
            !readingPreviewActive||readingAwaitingFrame.get()||readingProcessing)return;
        if(usingPhoneCamera){
            if(phoneController==null||!phonePreviewReceived){
                toastShort("手机摄像头尚未准备好，请对准画面后重试");return;
            }
        }else if(!usbKnownOpened||!isCameraOpened()){
            toastShort("USB预览尚未准备好，请对准画面后重试");return;
        }
        long at=SystemClock.elapsedRealtime();
        final int token=requestVersion.get();
        readingCaptureToken=token;
        readingCaptureStartedAtMs=at;
        readingAwaitingFrame.set(true);
        readingScreen.showCapturing();
        if(diagnostics!=null){
            diagnostics.begin(usingPhoneCamera?"reader_phone":"reader_usb");
            diagnostics.event("reader","manual_shutter",1,"ok");
        }
        if(usingPhoneCamera){
            pendingPhoneFrameToken=token;
            phoneController.requestFreshFrame();
        }
        // USB acquires the next native NV21 callback AFTER the user's click.
        handler.postDelayed(()->{
            if(readingScreen!=null&&readingCaptureToken==token&&
                token==requestVersion.get()&&readingAwaitingFrame.get())
                failReadingCapture("拍照超时，未取得新画面，请再试一次");
        },4500L);
    }
    /** Reader shutter acknowledgement: always a tangible vibration on a valid frame. */
    private void readerShutterFeedback(){
        try{
            Vibrator vib=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(vib!=null&&vib.hasVibrator()){
                if(android.os.Build.VERSION.SDK_INT>=26)
                    vib.vibrate(VibrationEffect.createOneShot(50,
                        VibrationEffect.DEFAULT_AMPLITUDE));
                else vib.vibrate(50);
            }
        }catch(RuntimeException e){Log.w(TAG,"Reader shutter vibration unavailable",e);}
        if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(PREF_SOUND,true))
            playSound(metalClickSample);
    }
    private void beginReadingCapturedFrame(Frame frame,int token){
        if(readingScreen==null||token!=requestVersion.get()||!uiActive||
            !readingPreviewActive)return;
        readingAwaitingFrame.set(false);
        pendingPhoneFrameToken=-1;
        readerShutterFeedback();
        readingProcessing=true;
        final String bookId=readingScreen.bookId();
        final String model=OcrModelCatalog.normalize(ocrModel);
        final TaskCatalog.Task bookTask=new TaskCatalog.Task(
            ReadingRules.TASK_ID,"完整书页",ReadingRules.PROMPT,TaskCatalog.OCR,false,"▤");
        final UsbFrameOrientation.Plan usb=usingPhoneCamera?null:
            UsbFrameOrientation.forSettings(previewRotation,previewMirror,
                previewVerticalFlip,usbPreviewCorrectMirror);
        final int rotation=usingPhoneCamera?previewRotation:usb.rotation;
        final boolean mirror=usingPhoneCamera?previewMirror:usb.displayMirror;
        final boolean flip=usingPhoneCamera?previewVerticalFlip:usb.displayFlip;
        final float photoZoom=usingPhoneCamera?1f:zoom;
        if(diagnostics!=null){
            diagnostics.event("reader","raw_width_px",frame.width,"ok");
            diagnostics.event("reader","raw_height_px",frame.height,"ok");
        }
        suspendReadingCamera();
        readingScreen.showRecognizing();
        readingSpeechToken++;
        if(readingNarration!=null)readingNarration.reset();
        encodingTask=encoder.submit(()->{
            boolean cloudCallRecorded=false;
            try{
                FrameCompressor.EncodedJpeg photo=FrameCompressor.encodeAdjusted(
                    frame.pixels,frame.width,frame.height,1080,rotation,mirror,flip,photoZoom);
                if(token!=requestVersion.get())return;
                CloudProviderStore.Config service=CloudProviderStore.resolve(
                    this,CloudProviderStore.OCR,model);
                CloudVisionClient.Result result=CloudVisionClient.recognize(
                    photo.jpeg,service,bookTask,
                    part->handler.post(()->{
                        if(readingScreen==null||token!=requestVersion.get()||
                            !bookId.equals(readingScreen.bookId()))return;
                        // The OCR model can return (x,y,w,h,angle) instead of words.
                        // Never read or show pure bounding-box coordinate rows.
                        if(ReadingRules.coordinateLine(part))return;
                        if(readingNarration!=null)readingNarration.addLine(part);
                        readingScreen.appendLine(part);
                    }),OcrSpeedExperiment.C,null);
                if(modelUsage!=null){
                    modelUsage.recordAsync(result.model,result.serverModel,
                        result.promptTokens,result.completionTokens,true);
                    cloudCallRecorded=true;
                }
                if(token!=requestVersion.get()||readingScreen==null||
                    !bookId.equals(readingScreen.bookId()))return;
                if(!ReadingRules.usable(result.text)||
                    ReadingRules.coordinatesOnly(result.text))
                    throw new Exception("模型返回了坐标或无效书页文字，本页未保存");
                ReadingLibrary.Page saved=readingLibrary.add(bookId,result.text);
                if(diagnostics!=null){
                    diagnostics.event("reader","network_ms",result.networkMs,"ok");
                    diagnostics.event("reader","first_line_ms",result.firstLineReadyMs,"ok");
                    diagnostics.event("reader","saved_page",saved.number,"ok");
                }
                handler.post(()->{
                    if(readingScreen==null||token!=requestVersion.get()||
                        !bookId.equals(readingScreen.bookId()))return;
                    readingProcessing=false;
                    readingScreen.saved(saved);
                    if(readingNarration!=null)
                        readingScreen.setPaused(readingNarration.isPaused());
                });
            }catch(Exception ex){
                // A successful billed cloud call must not be misreported as a failed
                // request merely because local storage subsequently failed.
                if(!cloudCallRecorded&&modelUsage!=null)
                    modelUsage.recordAsync(model,"",-1,-1,false);
                handler.post(()->{
                    if(readingScreen==null||token!=requestVersion.get())return;
                    readingProcessing=false;
                    readingSpeechToken++;
                    if(readingNarration!=null)readingNarration.reset();
                    readingScreen.failure("本页未保存："+(ex.getMessage()==null?
                        ex.getClass().getSimpleName():ex.getMessage()));
                    if(diagnostics!=null)diagnostics.event("reader","ocr",0,"failed");
                });
            }
        });
    }

    private void invalidate(){
        requestVersion.incrementAndGet();
        synchronized(photoCloudLock){
            if(activePhotoCloud!=null){activePhotoCloud.cancel();activePhotoCloud=null;}
            if(photoCloudTask!=null){photoCloudTask.cancel(true);photoCloudTask=null;}
        }
        pendingPhoneFrameToken=-1;
        pendingPhoneStillToken=-1;
        pendingPhoneCaptureTask=null;
        if(phoneController!=null){
            phoneController.cancelFreshFrame();
            phoneController.cancelPendingStill();
        }
        cloudBusy=false;
        // Never interrupt a real C110 1001 shutter/JPEG download in flight.
        if(encodingTask!=null&&!c110NativeShutterBusy){
            encodingTask.cancel(true);encodingTask=null;
        }
        if(tts!=null)tts.stop();
    }

    /** USB retains its last owned NV21; Camera2 only converts a new frame after tap. */
    private void captureOneFrame(){captureOneFrame(null);}
    private void captureOneFrame(TaskCatalog.Task oneShotTask){
        boolean fromVoice=oneShotTask!=null;
        if(voiceListening||voiceRecorder.isBusy()){
            toastShort("正在录音，请松手后再拍照");return;
        }
        // Never confuse a physical exposure with a safely replaceable cloud job.
        if(c110NativeShutterBusy||pendingPhoneStillToken>=0||
                pendingPhoneFrameToken>=0){
            toastShort("当前照片尚未取得，请完成拍摄后再拍下一张");return;
        }
        long now=SystemClock.elapsedRealtime();
        if(now-lastPinchMs<550L||!uiActive)return;
        if(now-lastTapMs<350L){
            toastShort("已忽略连续误触");
            if(fromVoice)speakUnfiltered("拍照间隔太短，请再说一次。");
            return;
        }
        Frame usbShot=usingPhoneCamera?null:latestUsbFrame;
        long usbFrameAge=usbShot==null?-1L:Math.max(0L,now-usbShot.atMs);
        if(!usingPhoneCamera&&(usbShot==null||usbFrameAge>USB_MAX_FRAME_AGE_MS)){
            toastShort("USB没有最新画面，不会使用旧照片");
            if(fromVoice)speakUnfiltered("外接摄像头还没有新画面，请稍后再试。");
            if(diagnostics!=null)diagnostics.event("camera","usb_stale_frame",
                usbFrameAge,"failed");
            return;
        }
        if(usingPhoneCamera&&phoneController==null){
            toastShort("手机摄像头尚未就绪");
            if(fromVoice)speakUnfiltered("手机摄像头还没有准备好。");
            return;
        }
        lastTapMs=now;
        invalidate();
        final int token=requestVersion.get();
        cloudBusy=true;
        speechRequestToken=token;
        speechCaptureAtMs=now;
        ttsEngineStartMs=-1;
        ttsAudioCallbackMs=-1;
        if(diagnostics!=null){
            diagnostics.begin(usingPhoneCamera?
                (phoneFrontFacing?"phone_front":"phone_back"):"usb");
            diagnostics.event("camera","tap",0,"ok");
        }
        if(usingPhoneCamera&&phoneHighRes){
            // Opt-in Camera2 true JPEG still; original preview pipeline is untouched.
            pendingPhoneStillToken=token;
            setResultStatus("正在拍摄手机高清照片…");
            phoneController.requestStillPhoto(new InternalCameraController.StillListener(){
                @Override public void onJpeg(byte[] jpeg,int width,int height,long atMs){
                    handler.post(()->{
                        if(!uiActive||token!=requestVersion.get()||
                           pendingPhoneStillToken!=token||!usingPhoneCamera)return;
                        pendingPhoneStillToken=-1;
                        if(diagnostics!=null)
                            diagnostics.event("camera","phone_still_ms",
                                Math.max(0,atMs-now),"ok");
                        beginCapturedFrame(new Frame(jpeg,width,height,atMs,true),
                            now,token,oneShotTask);
                    });
                }
                @Override public void onFailure(String reason){
                    handler.post(()->{
                        if(!uiActive||token!=requestVersion.get()||
                           pendingPhoneStillToken!=token)return;
                        pendingPhoneStillToken=-1;
                        cloudBusy=false;
                        if(diagnostics!=null)
                            diagnostics.event("camera","phone_still",0,"failed");
                        setResultStatus("手机高清拍照失败");
                        toastShort(reason);
                    });
                }
            });
            handler.postDelayed(()->{
                if(!uiActive||token!=requestVersion.get()||
                    pendingPhoneStillToken!=token)return;
                pendingPhoneStillToken=-1;
                phoneController.cancelPendingStill();
                cloudBusy=false;
                if(diagnostics!=null)diagnostics.event("camera","phone_still_ms",
                    SystemClock.elapsedRealtime()-now,"timeout");
                setResultStatus("手机高清拍照超时，请检查相机后重试");
            },8000L);
        }else if(usingPhoneCamera){
            pendingPhoneFrameToken=token;
            pendingPhoneCaptureTask=oneShotTask;
            phoneCaptureTapAtMs=now;
            setResultStatus("正在获取最新手机画面…");
            phoneController.requestFreshFrame();
            handler.postDelayed(()->{
                if(!uiActive||token!=requestVersion.get()||
                    pendingPhoneFrameToken!=token)return;
                pendingPhoneFrameToken=-1;
                pendingPhoneCaptureTask=null;
                phoneController.cancelFreshFrame();
                cloudBusy=false;
                if(diagnostics!=null)diagnostics.event("camera","fresh_frame_ms",
                    SystemClock.elapsedRealtime()-now,"timeout");
                setResultStatus("手机摄像头未提供新照片，请检查预览后重试");
                if(fromVoice)speakUnfiltered("手机摄像头没有获取到新照片，请稍后再试。");
            },3000L);
        }else beginCapturedFrame(usbShot,now,token,oneShotTask);
    }

    /** User-triggered C110 high-resolution photo -> existing OCR/vision/TTS pipeline. */
    private void captureC110OneShot(){
        if(!uiActive||readingScreen!=null){
            toastShort("请先退出阅读页面，再使用C110高清拍照");return;
        }
        // Home Wi-Fi can remain the internet default while C110 is requested
        // as a secondary local-only Wi-Fi. Never issue 1001 before a camera
        // Network has been discovered/verified.
        if(C110WifiBootstrap.connected(this)==null){
            if(c110WifiBootstrap!=null)c110WifiBootstrap.requestForCameraMode();
            setResultStatus("正在申请连接C110。允许系统连接后再点高清拍照");
            toastShort("请允许Android系统连接C110热点，然后再拍一张");
            return;
        }
        if(voiceListening||voiceRecorder.isBusy()||c110NativeShutterBusy){
            toastShort("正在录音或C110仍在执行真实快门，请稍后拍照");return;
        }
        long now=SystemClock.elapsedRealtime();
        if(now-lastTapMs<350L){toastShort("拍照间隔太短");return;}
        lastTapMs=now;
        // RTSP playback may hold the firmware in streaming mode.
        // Close the player and explicitly re-enter photo mode for this tap,
        // then resume live preview only after complete original JPEG download.
        // MainActivity no longer owns a second, unreachable C110 video player.
        // The separate C110 page keeps its proven video/photo state machine.
        final float highPhotoZoomAtTap=1f;
        invalidate();
        final int token=requestVersion.get();
        cloudBusy=true;
        speechRequestToken=token;
        speechCaptureAtMs=now;
        ttsEngineStartMs=-1;ttsAudioCallbackMs=-1;
        if(diagnostics!=null){
            diagnostics.begin("c110_photo");
            diagnostics.event("c110","tap",1,"ok");
        }
        setResultStatus("C110：正在取高清照片…");
        c110NativeShutterBusy=true;
        encodingTask=encoder.submit(()->{
            try{
                // The C110 shutter is NEVER gated on cloud key or mobile data.
                // Transition from any previous video-mode session to photo mode
                // before reading the baseline, then send exactly ONE 1001.
                C110WifiCamera.Photo photo=c110Camera.shootAfterLivePreview(this);
                if(Thread.currentThread().isInterrupted())return;
                // Native SD JPEG is only cropped when C110 video was digitally zoomed.
                // At 1x C110ZoomFrames returns the untouched source byte[].
                C110ZoomFrames.JpegResult scaled=
                    C110ZoomFrames.cropNativeJpeg(
                        photo.jpeg,photo.width,photo.height,highPhotoZoomAtTap);
                android.net.Network cellular=null;
                String cloudIssue=null;
                try{
                    cellular=C110WifiCamera.requireCellular(this);
                    String photoRole=findingTarget.isEmpty()&&currentTask!=null&&
                        TaskCatalog.OCR.equals(currentTask.engine)?
                        CloudProviderStore.OCR:CloudProviderStore.VISION;
                    if(!CloudProviderStore.hasKey(this,photoRole))
                        cloudIssue="未配置当前云端服务商 API Key，高清照片已拍摄但未识别";
                }catch(Exception noCloud){
                    cloudIssue="手机移动数据不可用或密钥无法读取，高清照片已拍摄但未云端识别";
                }
                final android.net.Network cloudNetwork=cellular;
                final String issue=cloudIssue;
                handler.post(()->{
                    c110NativeShutterBusy=false;
                    if(!uiActive||token!=requestVersion.get())return;
                    if(diagnostics!=null){
                        diagnostics.event("c110","mode_ms",photo.modeMs,"ok");
                        diagnostics.event("c110","shutter_ms",photo.shutterMs,"ok");
                        diagnostics.event("c110","discover_ms",photo.discoverMs,"ok");
                        diagnostics.event("c110","jpeg_ms",photo.downloadMs,"ok");
                        diagnostics.event("c110","photo_total_ms",photo.totalMs,"ok");
                        diagnostics.event("c110","zoom_x10",
                            Math.round(highPhotoZoomAtTap*10f),"ok");
                        diagnostics.event("c110","native_width_px",photo.width,"ok");
                        diagnostics.event("c110","native_height_px",photo.height,"ok");
                        diagnostics.event("c110","wifi_alive",photo.wifiStillPresent?1:0,
                            photo.wifiStillPresent?"ok":"lost");
                        diagnostics.event("c110","cloud_ready",issue==null?1:0,
                            issue==null?"ok":"unavailable");
                    }
                    if(issue==null){
                        Frame remote=new Frame(null,scaled.width,scaled.height,
                            SystemClock.elapsedRealtime(),scaled.jpeg,cloudNetwork);
                        beginCapturedFrame(remote,now,token,null);
                    }else{
                        cloudBusy=false;
                        // The OEM still photo succeeded even though cloud was
                        // unavailable: user still deserves exactly one local ding.
                        // The online branch already calls signalCapture inside
                        // beginCapturedFrame(), so do NOT duplicate it there.
                        signalCapture();
                        setResultStatus("C110高清原图已拍摄 "+photo.width+"×"+photo.height);
                        toastShort(issue);
                    }
                });
            }catch(Exception ex){
                handler.post(()->{
                    c110NativeShutterBusy=false;
                    if(token!=requestVersion.get()||!uiActive)return;
                    cloudBusy=false;
                    if(diagnostics!=null)diagnostics.event("c110","photo",0,"failed");
                    String message=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                    setResultStatus("C110高清快门或取图失败");
                    new BlueStarDialogChoices.AppCompatBuilder(this).setTitle("C110高清拍照失败（不会自动重拍）")
                        .setMessage(message).setPositiveButton("知道了",null).show();
                });
            }
        });
    }

    private void beginCapturedFrame(Frame current,long tapAtMs,int token,
                                    TaskCatalog.Task oneShotTask){
        if(!uiActive||token!=requestVersion.get())return;
        // Do not reuse prior image as the new frame; only clear the previous
        // image session once a genuinely new frame is acquired.
        lastSessionJpeg=null;
        lastSubmittedJpeg=null;
        lastSessionText="";
        lastSessionTask="";
        lastCloudResult="";
        lastSessionCloudNetwork=null;
        followupTurns.clear();
        signalCapture();
        cloudBusy=true;
        setResultStatus("已拍照 · 正在云端识别…");
        if(diagnostics!=null){
            if(current.phoneStillJpeg){
                diagnostics.event("camera","phone_still_jpeg",1,"ok");
                diagnostics.event("camera","phone_hd_upload_mode",phoneHdUploadMode,"ok");
            }
            else if(current.remoteJpeg!=null)
                diagnostics.event("camera","c110_input",current.c110LiveFrame?2:1,"ok");
            else if(usingPhoneCamera)
                diagnostics.event("camera","fresh_frame_ms",
                    Math.max(0,current.atMs-tapAtMs),"ok");
            else diagnostics.event("camera","usb_frame_age_ms",
                Math.max(0,tapAtMs-current.atMs),"ok");
            diagnostics.event("camera","raw_width_px",current.width,"ok");
            diagnostics.event("camera","raw_height_px",current.height,"ok");
        }
        final int phoneHdUploadSnapshot=phoneHdUploadMode;
        final TaskCatalog.Task task=oneShotTask==null?currentTask:oneShotTask;
        // A one-shot voice observation should not be hijacked by an ongoing search.
        final String targetToFind=oneShotTask==null?FindingTargetStore.current(this):"";
        final boolean phoneStill=current.phoneStillJpeg;
        final boolean c110Source=current.remoteJpeg!=null&&!phoneStill;
        final boolean directJpeg=current.remoteJpeg!=null;
        final String source=phoneStill?
            (phoneFrontFacing?"手机前置高清":"手机后置高清"):c110Source?
            (current.c110LiveFrame?"C110实时取帧":"C110高清原图"):(usingPhoneCamera?
            (phoneFrontFacing?"手机前置":"手机后置"):"USB 外接");
        final String policy=imagePolicy;
        // A "find target" voice intent remains a visual task even if the
        // currently selected desktop preset happens to be 朗读文字.
        final String model=OcrModelCatalog.forTask(
            targetToFind.isEmpty()&&TaskCatalog.OCR.equals(task.engine),
            visionModel,ocrModel);
        // Snapshot OCR playback policy at capture time; menu/scene/target/photo-followups
        // are NEVER affected by OCR line streaming.
        final String thisOcrMode=TaskCatalog.OCR.equals(task.engine)?
            ocrSpeedMode:OcrSpeedExperiment.A;
        if(diagnostics!=null&&TaskCatalog.OCR.equals(task.engine))
            diagnostics.event("ocr","speed_mode",
                OcrSpeedExperiment.diagnosticValue(thisOcrMode),"ok");
        final UsbFrameOrientation.Plan usbOrientation=usingPhoneCamera||directJpeg?null:
            UsbFrameOrientation.forSettings(previewRotation,previewMirror,
                previewVerticalFlip,usbPreviewCorrectMirror);
        final int photoRotation=c110Source?0:
            (usingPhoneCamera?previewRotation:usbOrientation.rotation);
        final int stillRotation=phoneStill?
            (phoneOcrRotation+photoRotation+360)%360:0;
        // USB controls are in FINAL display coordinates; preview maps them back
        // to local TextureView axes, NV21/JPEG applies them AFTER rotation.
        final boolean photoMirror=c110Source?false:
            (usingPhoneCamera?previewMirror:usbOrientation.displayMirror);
        final boolean photoFlip=c110Source?false:
            (usingPhoneCamera?previewVerticalFlip:usbOrientation.displayFlip);
        final float photoZoom=c110Source?1f:(usingPhoneCamera?1f:zoom);
        final CloudVisionClient.Cancellation photoCancel=new CloudVisionClient.Cancellation();
        synchronized(photoCloudLock){activePhotoCloud=photoCancel;}
        encodingTask=encoder.submit(()->{
            long takenAt=SystemClock.elapsedRealtime();
            try{
                FrameCompressor.Plan plan=directJpeg?null:FrameCompressor.choose(
                    current.pixels,current.width,current.height,policy);
                FrameCompressor.EncodedJpeg encoded=directJpeg?null:FrameCompressor.encodeAdjusted(
                    current.pixels,current.width,current.height,plan.limit,
                    photoRotation,photoMirror,photoFlip,photoZoom);
                // C110 stays byte-for-byte original. Phone still JPEG is
                // normalized in RAM; never enlarged from the preview stream.
                PhoneStillJpeg.Result still=phoneStill?PhoneStillJpeg.prepare(
                    current.remoteJpeg,stillRotation,photoMirror,photoFlip,
                    phoneHdUploadSnapshot):null;
                byte[] jpeg=c110Source?current.remoteJpeg:
                    phoneStill?still.jpeg:encoded.jpeg;
                long encodingMs=SystemClock.elapsedRealtime()-takenAt;
                if(token!=requestVersion.get())return;
                if(diagnostics!=null){
                    diagnostics.event("jpeg","encode_ms",encodingMs,"ok");
                    diagnostics.event("jpeg","bytes",jpeg.length,"ok");
                    diagnostics.event("jpeg","width_px",c110Source?current.width:
                        phoneStill?still.width:encoded.width,"ok");
                    diagnostics.event("jpeg","height_px",c110Source?current.height:
                        phoneStill?still.height:encoded.height,"ok");
                    diagnostics.event("jpeg","rotation_deg",photoRotation,"ok");
                    diagnostics.event("jpeg","mirror_x",photoMirror?1:0,"ok");
                    diagnostics.event("jpeg","flip_y",photoFlip?1:0,"ok");
                }
                final String role=!targetToFind.isEmpty()?
                    CloudProviderStore.VISION:
                    (TaskCatalog.OCR.equals(task.engine)?
                        CloudProviderStore.OCR:CloudProviderStore.VISION);
                final CloudProviderStore.Config service;
                try{service=CloudProviderStore.resolve(this,role,model);}
                catch(IllegalArgumentException missing){
                    handler.post(()->{
                        if(token!=requestVersion.get()||!uiActive)return;
                        cloudBusy=false;
                        if(diagnostics!=null)diagnostics.event("cloud","key",0,"missing");
                        openLargeSettings();
                        if(largeSettings!=null)largeSettings.openCloudRole(role);
                        toastShort(missing.getMessage());
                    });
                    return;
                }
                // The encoder is free as soon as the JPEG is ready. A stale
                // HTTP call cannot hold this queue (or a C110 native shutter).
                synchronized(photoCloudLock){
                    if(token!=requestVersion.get()||photoCancel.isCancelled())return;
                    photoCloudTask=photoCloudExecutor.submit(()->{
                        try{
                            photoCancel.check();
                // Store only the original byte[] being submitted, in volatile memory.
                // Diagnostic export remains numeric and never exports the photo.
                lastSubmittedJpeg=jpeg;
                RecentPhotoSession.captured(jpeg,current.cloudNetwork,task.name);
                if(!targetToFind.isEmpty()){
                    long cloudAt=SystemClock.elapsedRealtime();
                    TargetVisionClient.Result inspection=TargetVisionClient.inspect(
                        jpeg,targetToFind,service,current.cloudNetwork,photoCancel);
                    if(modelUsage!=null)modelUsage.recordAsync(service.model,inspection.serverModel,
                        inspection.promptTokens,inspection.completionTokens,true);
                    if(diagnostics!=null)diagnostics.event("cloud","target_ms",
                        SystemClock.elapsedRealtime()-cloudAt,"ok");
                    handler.post(()->{
                        if(token!=requestVersion.get()||!uiActive)return;
                        cloudBusy=false;
                        lastSessionJpeg=jpeg;
                        lastSessionCloudNetwork=current.cloudNetwork;
                        lastSessionText=inspection.message;
                        RecentPhotoSession.analyzed(jpeg,inspection.message);
                        lastSessionTask="寻找"+targetToFind;
                        lastCloudResult=inspection.message;
                        if(!targetToFind.equals(FindingTargetStore.current(this))){
                            setResultStatus("寻找目标已更改，本次旧照片结果已取消");
                            return;
                        }
                        if(inspection.confirmed()&&
                            FindingTargetStore.clearIfCurrent(this,targetToFind)){
                            findingTarget="";
                            setResultStatus("已经找到目标 · 自动结束寻找");
                            vibrationForTargetFound();
                        }else setResultStatus("寻找："+targetToFind+" · "+inspection.decision);
                        speakForCapture(inspection.message,token,false);
                    });
                    return;
                }
                java.util.concurrent.atomic.AtomicInteger delivered=
                    new java.util.concurrent.atomic.AtomicInteger();
                CloudVisionClient.Result result=CloudVisionClient.recognize(jpeg,service,task,
                    part->handler.post(()->{
                        if(token!=requestVersion.get()||!uiActive||tts==null||!ttsReady)return;
                        String spoken=filterForSpeech(part,task);
                        if(spoken.trim().isEmpty())return;
                        int index=delivered.get();
                        int mode=index==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD;
                        if(tts.speak(spoken,mode,null,"vision-"+token+"-"+index)
                            ==TextToSpeech.SUCCESS)delivered.incrementAndGet();
                    }),thisOcrMode,current.cloudNetwork,photoCancel);
                if(modelUsage!=null)modelUsage.recordAsync(result.model,result.serverModel,
                    result.promptTokens,result.completionTokens,true);
                long overallMs=SystemClock.elapsedRealtime()-tapAtMs;
                handler.post(()->{
                    if(token!=requestVersion.get()||!uiActive)return;
                    cloudBusy=false;
                    lastSessionJpeg=jpeg;
                    lastSessionCloudNetwork=current.cloudNetwork;
                    lastSessionText=result.text;
                    RecentPhotoSession.analyzed(jpeg,result.text);
                    lastSessionTask=task.name;
                    lastCloudResult=result.text;
                    if(diagnostics!=null){
                        diagnostics.event("cloud","upload_ms",result.uploadMs,"ok");
                        diagnostics.event("cloud","network_ms",result.networkMs,"ok");
                        if(TaskCatalog.OCR.equals(task.engine)){
                            diagnostics.event("cloud","response_headers_ms",
                                result.responseHeadersMs,"ok");
                            diagnostics.event("cloud","first_text_ms",result.firstTextMs,"ok");
                            diagnostics.event("cloud","first_line_ready_ms",
                                result.firstLineReadyMs,"ok");
                        }else diagnostics.event("cloud","first_text_ms",result.firstTextMs,"ok");
                        diagnostics.event("cloud","first_speakable_ms",result.firstSpeakableMs,"ok");
                        if(service.custom&&CloudProviderPolicy.zhipuFastVisualOcr(
                                service.url,result.model)){
                            // Numeric-only performance data: never store OCR text.
                            diagnostics.event("cloud","output_chars",result.text.length(),"ok");
                            if(result.firstTextMs>=0)
                                diagnostics.event("cloud","after_first_text_ms",
                                    Math.max(0,result.networkMs-result.firstTextMs),"ok");
                        }
                        diagnostics.event("capture","total_ms",overallMs,"ok");
                    }
                    Log.i(TAG,"Cloud timing: route="+task.engine+" model="+result.model+
                        " capture="+source+" raw="+current.width+"x"+current.height+
                        " imageLimit="+(directJpeg?current.width:plan.limit)+" jpegBytes="+jpeg.length+
                        " jpegMs="+encodingMs+" uploadMs="+result.uploadMs+
                        " requestMs="+result.networkMs+
                        " firstTextMs="+result.firstTextMs+
                        " firstSpeakableMs="+result.firstSpeakableMs);
                    setResultStatus("已识别："+task.name+" · 可按住AI对话追问");
                    // C already enqueues completed OCR lines. Do not
                    // replay the whole OCR answer, which would duplicate speech.
                    // A/B always speak the entire returned text exactly once.
                    if((TaskCatalog.OCR.equals(task.engine)&&
                            !OcrSpeedExperiment.earlySpeak(thisOcrMode))||
                        delivered.get()==0)
                        speakForCapture(result.text,token,true,task);
                });
            }catch(OcrResponseGate.NoReadableTextException noText){
                if(modelUsage!=null)modelUsage.recordAsync(
                    model,noText.serverModel,
                    noText.inputTokens,noText.outputTokens,true);
                // Valid HTTP response with no readable characters. Do not block the camera
                // behind an "error" dialog; give one brief accessible spoken outcome.
                handler.post(()->{
                    if(token!=requestVersion.get()||!uiActive)return;
                    cloudBusy=false;
                    if(diagnostics!=null){
                        diagnostics.event("cloud","empty_result",0,"no_text");
                        diagnostics.event("capture","total_ms",
                            SystemClock.elapsedRealtime()-tapAtMs,"no_text");
                    }
                    setResultStatus("未识别到文字");
                    speakForCapture("未识别到文字，请调整拍摄角度再试。",token,false);
                });
            }catch(Exception error){
                String attempted=model;
                if(modelUsage!=null)modelUsage.recordAsync(attempted,"",-1,-1,false);
                handler.post(()->{
                    if(token!=requestVersion.get()||!uiActive)return;
                    cloudBusy=false;
                    if(diagnostics!=null){
                        diagnostics.event("cloud","request",0,"failed");
                        diagnostics.event("cloud","http_status",
                            diagnosticHttpStatus(error),"failed");
                        diagnostics.event("cloud","elapsed_before_error_ms",
                            SystemClock.elapsedRealtime()-tapAtMs,"failed");
                    }
                    setResultStatus("云端识别失败，请重试");
                    new BlueStarDialogChoices.AppCompatBuilder(this).setTitle("云端识别失败")
                        .setMessage(error.getMessage()==null?
                            error.getClass().getSimpleName():error.getMessage())
                        .setPositiveButton("知道了",null).show();
                });
            }
                    });
                }
            }catch(Exception encodeError){
                handler.post(()->{
                    if(token!=requestVersion.get()||!uiActive)return;
                    cloudBusy=false;
                    if(diagnostics!=null)diagnostics.event("jpeg","prepare",0,"failed");
                    setResultStatus("照片处理失败，请重新拍照");
                    toastShort("无法处理当前照片："+encodeError.getClass().getSimpleName());
                });
            }
        });
    }

    @Override protected void onResume(){
        super.onResume();
        // C110 page and photo lab can change these shared model settings.
        // Refresh cached homepage labels after returning, without changing
        // camera mode, task selection or any cloud request.
        SharedPreferences currentModels=getSharedPreferences(PREFS,MODE_PRIVATE);
        ocrModel=OcrModelCatalog.normalize(
            currentModels.getString(PREF_OCR_MODEL,OcrModelCatalog.DEFAULT));
        visionModel=VisionModelCatalog.normalize(
            currentModels.getString(PREF_VISION_MODEL,VisionModelCatalog.DEFAULT));
        findingTarget=FindingTargetStore.current(this);
        uiActive=true;usbForeground=!settingsOnly;
        if(quickShot!=null)quickShot.onResume();
        if(settingsOnly){
            handler.post(this::openLargeSettings);
            return;
        }
        if(getIntent()!=null&&getIntent().getBooleanExtra("bluestar_launch_reading",false)){
            getIntent().removeExtra("bluestar_launch_reading");
            handler.postDelayed(()->{if(uiActive)openReadingMode();},600L);
        }
        if(readingScreen!=null){
            readingAwaitingFrame.set(false);
            suspendReadingCamera();
            return;
        }
        resumeForegroundPreview();
    }
    /** Common camera-resume path for normal Home->Preview and Settings->Preview.
     *  Never creates a second MainActivity or changes the original C110/TTS path. */
    private void resumeForegroundPreview(){
        if(settingsOnly||!uiActive||isFinishing())return;
        usbForeground=true;
        // Handles permission/connect having arrived before onResume on cold USB launch.
        if(!usingPhoneCamera)usbReopenWanted=true;
        usbResumeAtMs=SystemClock.elapsedRealtime();
        startUsbFpsMonitoring();
        if(!usingPhoneCamera&&diagnostics!=null){
            diagnostics.begin("usb_resume");
            diagnostics.event("usb","resume",0,"ok");
        }
        boolean fallback=false;
        if(isUsbAutoSwitch()&&!usingPhoneCamera&&!hasConnectedUsbVideoDevice()){
            saveCameraProfile();
            switchCameraSource(true);
            fallback=true;
        }else handler.post(this::resumeUsb);
        // Cold USB launch often dispatches onConnect before onResume while
        // usbForeground=false. A later onResume must reconcile the already
        // attached physical device, even when no fresh onConnect arrives.
        if(usingPhoneCamera&&hasConnectedUsbVideoDevice())queueUsbAttachReconcile();
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_PHONE_CAMERA);
        else {
            if(usingPhoneCamera&&!fallback)startPhoneCamera();
            if(c110WifiBootstrap!=null)c110WifiBootstrap.startAtLaunch();
        }
    }
    @Override protected void onPause(){
        if(quickShot!=null)quickShot.onPause();
        if(!usingPhoneCamera&&diagnostics!=null){
            diagnostics.event("usb","pause",0,"ok");
            Frame frame=latestUsbFrame;
            diagnostics.event("usb","raw_age_ms",frame==null?-1:
                Math.max(0,SystemClock.elapsedRealtime()-frame.atMs),"ok");
            diagnostics.event("usb","view_age_ms",usbTextureLastAtMs<=0?-1:
                Math.max(0,SystemClock.elapsedRealtime()-usbTextureLastAtMs),"ok");
        }
        if(readingScreen!=null&&readingScreen.isPreview()){
            readingPreviewActive=false;
            readingScreen.cancelled();
        }else if(readingScreen!=null&&(readingAwaitingFrame.get()||readingProcessing)){
            readingAwaitingFrame.set(false);
            readingPreviewActive=false;
            readingProcessing=false;
            readingScreen.failure("应用切到后台，本页未保存，请重新拍照");
        }
        handler.removeCallbacks(usbHealthTicker);
        handler.removeCallbacks(usbAttachReconcile);
        usbLastSample=null;usbLastSampleAtMs=0;usbRollingFps.reset();
        usbPictureGeneration.incrementAndGet();
        uiActive=false;usbForeground=false;
        voiceSequence.incrementAndGet();voiceRecorder.cancel();
        voiceListening=false;voicePress.interrupt();
        pendingPhoneFrameToken=-1;
        pendingPhoneCaptureTask=null;
        if(phoneController!=null)phoneController.cancelFreshFrame();
        invalidate();
        cloudBusy=false;
        saveCameraProfile();
        handler.removeCallbacks(delayedPhoneZoom);
        stopUsb();
        phoneStartSequence++;
        if(phoneController!=null)phoneController.stop();
        latestPhoneFrame=null;
        super.onPause();
    }
    @Override protected void onDestroy(){
        if(quickShot!=null){quickShot.shutdown();quickShot=null;}
        if(settingsC110DeviceControls!=null){
            settingsC110DeviceControls.shutdown();settingsC110DeviceControls=null;
        }
        if(c110WifiBootstrap!=null){c110WifiBootstrap.shutdown();c110WifiBootstrap=null;}
        invalidate();
        if(readingScreen!=null){readingScreen.dispose();readingScreen=null;}
        if(phoneController!=null)phoneController.stop();
        encoder.shutdownNow();
        photoCloudExecutor.shutdownNow();
        modelTestExecutor.shutdownNow();
        usbPictureExecutor.shutdownNow();
        handler.removeCallbacks(usbHealthTicker);
        handler.removeCallbacks(usbAttachReconcile);
        if(modelUsage!=null)modelUsage.shutdown();
        voiceSequence.incrementAndGet();voiceRecorder.cancel();
        lastSessionJpeg=null;
        lastSubmittedJpeg=null;
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        if(clickPool!=null){clickPool.release();clickPool=null;}
        handler.removeCallbacks(hideStatus);
        super.onDestroy();
    }
}
