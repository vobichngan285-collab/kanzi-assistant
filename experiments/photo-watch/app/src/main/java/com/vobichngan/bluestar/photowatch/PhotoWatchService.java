package com.vobichngan.bluestar.photowatch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * User-initiated 30-minute folder-watch experiment.
 * No camera, no Internet permission, no OCR, no photo uploads.
 * A separate bounded worker reads image bytes LOCALLY to test readiness.
 * A foreground service plus short-duration partial wakelock lets users test
 * camera-app switching and screen-off behavior without an always-on daemon.
 */
public final class PhotoWatchService extends Service {
    public static final String ACTION_START = "com.vobichngan.bluestar.photowatch.START";
    public static final String ACTION_STOP = "com.vobichngan.bluestar.photowatch.STOP";
    public static final String PREFS = "photo_watch_experiment";
    public static final String KEY_TREE = "watched_tree_uri";
    public static final String KEY_STATUS = "status";
    public static final String KEY_COUNT = "detected_count";
    public static final String KEY_LAST_NAME = "last_name";
    public static final String KEY_DELAY = "estimated_delay_ms";
    public static final String KEY_LAST_SCAN = "last_scan_ms";
    public static final String KEY_LOCAL_TO_TTS_MS = "scan_to_speech_request_ms";
    public static final String KEY_TTS_REQUEST = "tts_request_status";
    public static final String KEY_DIRECT_STATUS = "direct_file_observer_status";
    public static final String KEY_DIRECT_EVENTS = "direct_file_events";
    public static final String KEY_LAST_SOURCE = "last_detection_source";
    public static final String KEY_TTS_ONSTART_MS = "tts_engine_onstart_delay_ms";
    public static final String KEY_PROBE_FILE = "probe_filename";
    public static final String KEY_PROBE_STATUS = "probe_status";
    public static final String KEY_PROBE_OPEN_MS = "probe_first_open_ms";
    public static final String KEY_PROBE_DECODE_MS = "probe_first_decode_ms";
    public static final String KEY_PROBE_READY_MS = "probe_read_ready_ms";
    public static final String KEY_PROBE_ATTEMPTS = "probe_attempts";
    public static final String KEY_PROBE_SIZE = "probe_size_bytes";
    public static final String KEY_PROBE_DIMENSIONS = "probe_dimensions";
    public static final String KEY_PROBE_MARKER = "probe_marker";
    public static final String KEY_PROBE_SOURCE = "probe_source";

    private static final String CHANNEL_ID = "photo_watch_v1";
    private static final int NOTIFICATION_ID = 2341;
    // MediaStore observer is fast path; 600ms fallback if the provider is silent.
    private static final long SCAN_PERIOD_MS = 600L;
    private static final long MAX_SESSION_MS = 30L * 60L * 1000L;

    public static volatile boolean isRunning = false;

    private final Object wakeSignal = new Object();
    private boolean scanRequested = false;
    private volatile boolean watching = false;
    private Thread worker;
    private ContentObserver contentObserver;
    private DirectFolderEvents directEvents;
    private volatile PhotoSnapshotTracker photoTracker;
    private PhotoReadProbe photoReadProbe;
    private volatile String lastUtteranceId = "";
    private volatile long lastSpeechRequestElapsed;
    private Uri treeUri;
    private PowerManager.WakeLock wakeLock;
    private TextToSpeech tts;
    private volatile boolean ttsReady;
    private volatile int count;
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createChannel();
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int available = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                ttsReady = available != TextToSpeech.LANG_MISSING_DATA &&
                           available != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                if (id != null && id.equals(lastUtteranceId) && prefs != null) {
                    long delay = SystemClock.elapsedRealtime() - lastSpeechRequestElapsed;
                    prefs.edit().putLong(KEY_TTS_ONSTART_MS, Math.max(0L, delay)).apply();
                }
            }
            @Override public void onDone(String id) { }
            @Override public void onError(String id) {
                if (id != null && id.equals(lastUtteranceId) && prefs != null) {
                    prefs.edit().putString(KEY_TTS_REQUEST, "语音引擎播放失败").apply();
                }
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            prefs.edit().putString(KEY_STATUS, "监听已手动停止。").apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action) || watching) return START_NOT_STICKY;

        String uriString = prefs.getString(KEY_TREE, "");
        if (uriString.isEmpty()) {
            prefs.edit().putString(KEY_STATUS, "请先选择照片文件夹。").apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            treeUri = Uri.parse(uriString);
            // A system-granted persistent URI is required; do not open private
            // app data or try to circumvent Android directory restrictions.
            if (!hasReadGrant(treeUri)) {
                prefs.edit().putString(KEY_STATUS, "文件夹授权已失效，请重新选择。").apply();
                stopSelf();
                return START_NOT_STICKY;
            }

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification("正在建立照片基线"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification("正在建立照片基线"));
            }
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power != null) {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "BlueStarPhotoWatch:30MinuteTest");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(MAX_SESSION_MS);
            }

            watching = true;
            isRunning = true;
            count = 0;
            prefs.edit().putInt(KEY_COUNT, 0)
                    .putString(KEY_LAST_NAME, "暂无")
                    .putLong(KEY_DELAY, -1)
                    .putLong(KEY_LOCAL_TO_TTS_MS, -1)
                    .putLong(KEY_TTS_ONSTART_MS, -1)
                    .putString(KEY_LAST_SOURCE, "尚未触发")
                    .putInt(KEY_DIRECT_EVENTS, 0)
                    .putString(KEY_DIRECT_STATUS, "正在尝试直接监听文件夹")
                    .putString(KEY_TTS_REQUEST, "尚未触发")
                    .putString(KEY_PROBE_FILE, "暂无")
                    .putString(KEY_PROBE_SOURCE, "尚未触发")
                    .putString(KEY_PROBE_STATUS, "尚未触发")
                    .putLong(KEY_PROBE_OPEN_MS, -1)
                    .putLong(KEY_PROBE_DECODE_MS, -1)
                    .putLong(KEY_PROBE_READY_MS, -1)
                    .putLong(KEY_PROBE_SIZE, 0)
                    .putInt(KEY_PROBE_ATTEMPTS, 0)
                    .putString(KEY_PROBE_DIMENSIONS, "未知")
                    .putString(KEY_PROBE_MARKER, "尚未判断")
                    .putString(KEY_STATUS, "正在建立初始照片列表，旧照片不会播报。")
                    .apply();
            photoTracker = new PhotoSnapshotTracker();
            photoReadProbe = new PhotoReadProbe(getContentResolver(),
                    treeUri, this::onPhotoProbeResult);
            subscribe(treeUri);
            directEvents = DirectFolderEvents.start(treeUri, this::onDirectPhotoEvent);
            prefs.edit().putString(KEY_DIRECT_STATUS, directEvents == null
                    ? "直连不可用：使用照片通知与600ms备用扫描"
                    : "文件系统监听已注册，尚待收到真实事件").apply();
            worker = new Thread(this::scanLoop, "BlueStarPhotoFolderMonitor");
            worker.start();
        } catch (Exception failure) {
            prefs.edit().putString(KEY_STATUS,
                    "启动失败：" + failure.getClass().getSimpleName()).apply();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private boolean hasReadGrant(Uri target) {
        for (android.content.UriPermission permission :
                getContentResolver().getPersistedUriPermissions()) {
            if (permission.isReadPermission() && permission.getUri().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "新照片监听实验", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("明确启动的30分钟照片文件夹监听实验");
            NotificationManager mgr = (NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    private Notification notification(String message) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 20, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, PhotoWatchService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 21, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("蓝星照片监听实验")
                .setContentText(message)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "停止", stopPending)
                .build();
    }

    private void updateNotification(String message) {
        NotificationManager mgr = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        if (mgr != null) mgr.notify(NOTIFICATION_ID, notification(message));
    }

    private void subscribe(Uri tree) {
        contentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { wakeScan(); }
            @Override public void onChange(boolean selfChange, Uri changedUri) { wakeScan(); }
        };
        // The folder's DocumentsProvider may not emit change events on vivo.
        // MediaStore updates are an independent way to wake the *same* SAF
        // folder query. No broad photo-reading permission is requested.
        try {
            String parentId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
            getContentResolver().registerContentObserver(children, true, contentObserver);
        } catch (RuntimeException ignored) {
            // Another observer plus bounded polling remain available.
        }
        try {
            getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true, contentObserver);
        } catch (RuntimeException ignored) {
            // Some Android variants deny image collection notifications.
        }
    }

    private void wakeScan() {
        synchronized (wakeSignal) {
            scanRequested = true;
            wakeSignal.notifyAll();
        }
    }

    /**
     * Native filesystem event path. Never performs SAF/MediaStore queries and
     * never opens image bytes before the speech cue. The tracker suppresses
     * repeated MODIFY/CLOSE_WRITE or later SAF results for the same filename.
     */
    private void onDirectPhotoEvent(String filename, long fileEventElapsed) {
        PhotoSnapshotTracker state = photoTracker;
        if (!watching || state == null || !state.acceptPhysicalEvent(filename)) return;
        announcePhotos(Collections.singletonList(
                        new PhotoSnapshotTracker.Photo("fs:" + filename, filename, -1, 0)),
                "文件系统 FileObserver（直达）", fileEventElapsed);
        schedulePhotoProbe(filename, fileEventElapsed, "FileObserver 原生文件事件");
        prefs.edit().putInt(KEY_DIRECT_EVENTS,
                        prefs.getInt(KEY_DIRECT_EVENTS, 0) + 1)
                .putString(KEY_DIRECT_STATUS, "已收到真实文件系统事件").apply();
        // Let the regular SAF scan catch up for UI and metadata. Speech does
        // NOT wait for that scan, so this is not an extra intermediary.
        wakeScan();
    }

    private void schedulePhotoProbe(String filename, long startedElapsed, String source) {
        PhotoReadProbe probe = photoReadProbe;
        if (!watching || probe == null) return;
        if (!ImageCompletionPolicy.isSupported(filename)) return;
        // The most recently announced photo is the one displayed by the UI.
        prefs.edit().putString(KEY_PROBE_FILE, filename)
                .putString(KEY_PROBE_SOURCE, source)
                .putString(KEY_PROBE_STATUS, "排队进行本地读取实验")
                .putLong(KEY_PROBE_OPEN_MS, -1)
                .putLong(KEY_PROBE_DECODE_MS, -1)
                .putLong(KEY_PROBE_READY_MS, -1)
                .putLong(KEY_PROBE_SIZE, 0)
                .putInt(KEY_PROBE_ATTEMPTS, 0)
                .putString(KEY_PROBE_DIMENSIONS, "未知")
                .putString(KEY_PROBE_MARKER, "尚未判断").apply();
        if (!probe.offer(filename, startedElapsed, source)) {
            prefs.edit().putString(KEY_PROBE_STATUS,
                    "当前已有同名图片测试或同时测试数量已满").apply();
        }
    }

    private void onPhotoProbeResult(PhotoReadProbe.Result result) {
        if (!watching || prefs == null) return;
        if (!result.filename.equals(prefs.getString(KEY_PROBE_FILE, ""))) return;
        prefs.edit()
                .putString(KEY_PROBE_STATUS, result.status)
                .putString(KEY_PROBE_SOURCE, result.source)
                .putLong(KEY_PROBE_OPEN_MS, result.firstOpenMs)
                .putLong(KEY_PROBE_DECODE_MS, result.firstDecodedMs)
                .putLong(KEY_PROBE_READY_MS, result.readyMs)
                .putLong(KEY_PROBE_SIZE, result.bytes)
                .putInt(KEY_PROBE_ATTEMPTS, result.attempts)
                .putString(KEY_PROBE_DIMENSIONS,
                        result.width > 0 ? result.width + "×" + result.height : "未知")
                .putString(KEY_PROBE_MARKER,
                        result.strictEndMarker ? "需要 JPEG/PNG 结束标志" :
                                "此格式没有可靠末尾标志，结果仅为估计")
                .apply();
    }

    private synchronized void announcePhotos(List<PhotoSnapshotTracker.Photo> fresh,
                                              String source, long startedElapsed) {
        if (!watching || fresh.isEmpty()) return;
        PhotoSnapshotTracker.Photo latest = fresh.get(fresh.size() - 1);
        long wallNow = System.currentTimeMillis();
        long timeSinceModified = latest.modified > 0
                && wallNow >= latest.modified
                && wallNow - latest.modified < 24L * 60L * 60L * 1000L
                ? wallNow - latest.modified : -1;

        // Submit speech BEFORE Preferences, notifications or metadata scans.
        // TTS.onStart only reports engine progress, not actual speaker audio.
        String spoken = fresh.size() == 1 ? "发现新照片"
                : "发现" + fresh.size() + "张新照片";
        String voiceStatus = "系统中文语音尚未就绪";
        long requestElapsed = SystemClock.elapsedRealtime();
        long eventToRequest = Math.max(0L, requestElapsed - startedElapsed);
        prefs.edit().putLong(KEY_TTS_ONSTART_MS, -1).apply();
        if (ttsReady && tts != null) {
            lastUtteranceId = "new_photo_" + requestElapsed;
            lastSpeechRequestElapsed = requestElapsed;
            int result = tts.speak(spoken, TextToSpeech.QUEUE_FLUSH,
                    null, lastUtteranceId);
            voiceStatus = result == TextToSpeech.SUCCESS
                    ? "已提交给语音引擎" : "语音请求失败";
        }
        count += fresh.size();
        prefs.edit().putInt(KEY_COUNT, count)
                .putString(KEY_LAST_NAME, latest.name)
                .putLong(KEY_DELAY, timeSinceModified)
                .putLong(KEY_LOCAL_TO_TTS_MS, eventToRequest)
                .putString(KEY_TTS_REQUEST, voiceStatus)
                .putString(KEY_LAST_SOURCE, source)
                .putString(KEY_STATUS,
                        "发现新照片 " + fresh.size() + " 张，累计 " + count +
                        " 张。提示仅代表文件出现，图片可能仍在写入。")
                .apply();
        updateNotification("已发现 " + count + " 张新照片");
    }

    private void scanLoop() {
        final PhotoSnapshotTracker tracker = photoTracker;
        final long began = SystemClock.elapsedRealtime();
        try {
            while (watching && SystemClock.elapsedRealtime() - began < MAX_SESSION_MS) {
                long scanStartedElapsed = SystemClock.elapsedRealtime();
                List<PhotoSnapshotTracker.Photo> photos = listDirectImages(treeUri);
                long scannedAt = System.currentTimeMillis();
                List<PhotoSnapshotTracker.Photo> fresh = tracker.accept(photos);
                prefs.edit().putLong(KEY_LAST_SCAN, scannedAt).apply();

                if (!watching) break;
                if (!tracker.hasBaseline()) {
                    throw new IllegalStateException("Baseline not ready");
                }
                if (count == 0 && fresh.isEmpty()
                        && "正在建立初始照片列表，旧照片不会播报。".equals(
                                prefs.getString(KEY_STATUS, ""))) {
                    prefs.edit().putString(KEY_STATUS,
                            "正在监听 · 已忽略 " + tracker.baselineCount() +
                            " 张原有照片；可以用相机拍一张。").apply();
                    updateNotification("等待新照片 · 已建立基线");
                }
                if (!fresh.isEmpty()) {
                    announcePhotos(fresh, "SAF 目录扫描 / 系统通知", scanStartedElapsed);
                    for (PhotoSnapshotTracker.Photo photo : fresh) {
                        schedulePhotoProbe(photo.name, scanStartedElapsed,
                                "SAF 目录发现（非文件系统首次事件）");
                    }
                }
                synchronized (wakeSignal) {
                    if (!scanRequested) wakeSignal.wait(SCAN_PERIOD_MS);
                    scanRequested = false;
                }
            }
            if (watching) {
                prefs.edit().putString(KEY_STATUS,
                        "30分钟实验时段结束，已自动停止；可手动再次启动。").apply();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            prefs.edit().putString(KEY_STATUS,
                    "文件夹读取失败：" + failure.getClass().getSimpleName() +
                    "。请重新选择可访问文件夹。").apply();
        } finally {
            stopSelf();
        }
    }

    /**
     * Query the selected SAF folder only (no subfolder recursion).
     * Metadata only, no image decoding, no photo copies.
     */
    private List<PhotoSnapshotTracker.Photo> listDirectImages(Uri tree) {
        String parentId = DocumentsContract.getTreeDocumentId(tree);
        Uri childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        List<PhotoSnapshotTracker.Photo> files = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(childUri, projection,
                null, null, null)) {
            if (cursor == null) throw new IllegalStateException("Provider returned no cursor");
            int id = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int name = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mime = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int size = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            int modified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            if (id < 0 || name < 0) throw new IllegalStateException("Missing document metadata");
            while (cursor.moveToNext()) {
                if (cursor.isNull(id) || cursor.isNull(name)) continue;
                String photoName = cursor.getString(name);
                String photoMime = mime < 0 || cursor.isNull(mime) ? "" : cursor.getString(mime);
                if (!isPhoto(photoName, photoMime)) continue;
                long length = size >= 0 && !cursor.isNull(size) ? cursor.getLong(size) : 0L;
                long timestamp = modified >= 0 && !cursor.isNull(modified)
                        ? cursor.getLong(modified) : 0L;
                files.add(new PhotoSnapshotTracker.Photo(
                        cursor.getString(id), photoName, length, timestamp));
            }
        }
        return files;
    }

    private static boolean isPhoto(String name, String mime) {
        if (mime != null && mime.startsWith("image/")) return true;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".png") || lower.endsWith(".heic") ||
               lower.endsWith(".heif") || lower.endsWith(".webp") ||
               lower.endsWith(".bmp");
    }

    @Override public void onDestroy() {
        watching = false;
        isRunning = false;
        wakeScan();
        if (worker != null) worker.interrupt();
        if (photoReadProbe != null) {
            photoReadProbe.stop();
            photoReadProbe = null;
        }
        if (directEvents != null) {
            try { directEvents.stop(); } catch (RuntimeException ignored) { }
            directEvents = null;
        }
        if (contentObserver != null) {
            try { getContentResolver().unregisterContentObserver(contentObserver); }
            catch (RuntimeException ignored) { }
            contentObserver = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (prefs != null) {
            String last = prefs.getString(KEY_STATUS, "");
            if (!last.contains("失败") && !last.contains("已停止") &&
                    !last.contains("时段结束")) {
                prefs.edit().putString(KEY_STATUS, "已停止监听。").apply();
            }
        }
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
