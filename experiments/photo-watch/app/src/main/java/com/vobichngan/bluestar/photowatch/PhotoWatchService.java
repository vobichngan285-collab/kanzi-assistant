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
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * User-initiated 30-minute folder-watch experiment.
 * No camera, no Internet permission, no OCR, no image byte reads.
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

    private static final String CHANNEL_ID = "photo_watch_v1";
    private static final int NOTIFICATION_ID = 2341;
    private static final long SCAN_PERIOD_MS = 2000L;
    private static final long MAX_SESSION_MS = 30L * 60L * 1000L;

    public static volatile boolean isRunning = false;

    private final Object wakeSignal = new Object();
    private volatile boolean watching = false;
    private Thread worker;
    private ContentObserver contentObserver;
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
                    .putString(KEY_STATUS, "正在建立初始照片列表，旧照片不会播报。")
                    .apply();
            subscribe(treeUri);
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
        try {
            String parentId = DocumentsContract.getTreeDocumentId(tree);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
            contentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { wakeScan(); }
                @Override public void onChange(boolean selfChange, Uri changedUri) { wakeScan(); }
            };
            getContentResolver().registerContentObserver(children, true, contentObserver);
        } catch (RuntimeException ignored) {
            // Some providers don't support change notifications. Poll fallback remains.
            contentObserver = null;
        }
    }

    private void wakeScan() {
        synchronized (wakeSignal) { wakeSignal.notifyAll(); }
    }

    private void scanLoop() {
        final PhotoSnapshotTracker tracker = new PhotoSnapshotTracker();
        final long began = SystemClock.elapsedRealtime();
        try {
            while (watching && SystemClock.elapsedRealtime() - began < MAX_SESSION_MS) {
                List<PhotoSnapshotTracker.Photo> photos = listDirectImages(treeUri);
                long scannedAt = System.currentTimeMillis();
                List<PhotoSnapshotTracker.Photo> fresh = tracker.accept(photos);
                prefs.edit().putLong(KEY_LAST_SCAN, scannedAt).apply();

                if (!watching) break;
                if (!tracker.hasBaseline()) {
                    // tracker.accept always baselines the first valid listing.
                    throw new IllegalStateException("Baseline not ready");
                }
                if (count == 0 && fresh.isEmpty()
                        && "正在建立初始照片列表，旧照片不会播报。".equals(
                                prefs.getString(KEY_STATUS, ""))) {
                    prefs.edit().putString(KEY_STATUS,
                            "正在监听 · 已忽略 " + tracker.baselineCount() +
                            " 张原有照片；请用相机再拍一张。").apply();
                    updateNotification("等待新照片 · 已建立基线");
                }

                if (!fresh.isEmpty()) {
                    count += fresh.size();
                    PhotoSnapshotTracker.Photo latest = fresh.get(fresh.size() - 1);
                    long estimated = latest.modified > 0
                            && scannedAt >= latest.modified
                            && scannedAt - latest.modified < 24L * 60L * 60L * 1000L
                            ? scannedAt - latest.modified : -1;
                    prefs.edit().putInt(KEY_COUNT, count)
                            .putString(KEY_LAST_NAME, latest.name)
                            .putLong(KEY_DELAY, estimated)
                            .putString(KEY_STATUS,
                                    "成功发现 " + fresh.size() + " 张新照片；累计 " + count +
                                    " 张。已等到文件写入稳定。")
                            .apply();
                    updateNotification("已发现 " + count + " 张新照片");
                    if (ttsReady) {
                        String spoken = fresh.size() == 1 ? "发现新照片"
                                : "发现" + fresh.size() + "张新照片";
                        tts.speak(spoken, TextToSpeech.QUEUE_ADD, null,
                                "new_photo_" + scannedAt);
                    }
                }
                synchronized (wakeSignal) { wakeSignal.wait(SCAN_PERIOD_MS); }
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
