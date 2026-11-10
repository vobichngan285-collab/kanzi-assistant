package com.vobichngan.bluestar.photowatch;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.DocumentsContract;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local-only read-readiness experiment.
 *
 * Measures from receipt of the first app-observed file arrival event to:
 *  (1) successful SAF open with a nonzero file size;
 *  (2) successful low-memory sampled bitmap decoding;
 *  (3) a conservative "read-ready candidate": complete compressed stream
 *      read, sampled bitmap decode, unchanged file length/mtime on two probes,
 *      and a valid terminal marker for JPEG/PNG.
 *
 * On HEIC/WebP, no universal terminal marker is checked: result is only
 * a heuristic estimate. Nothing is uploaded, retained, or logged from the
 * photo bytes; only timing, filename, dimensions and byte count are reported.
 *
 * The source photo might still change after a successful probe. This is
 * an experiment, NOT an atomic finalization guarantee.
 */
public final class PhotoReadProbe {
    public interface Listener {
        void onUpdate(Result result);
    }

    public static final class Result {
        public final String filename;
        public final String source;
        public final String status;
        public final long firstOpenMs;
        public final long firstDecodedMs;
        public final long readyMs;
        public final long bytes;
        public final int width;
        public final int height;
        public final int attempts;
        public final boolean strictEndMarker;

        Result(String filename, String source, String status,
               long firstOpenMs, long firstDecodedMs, long readyMs,
               long bytes, int width, int height, int attempts,
               boolean strictEndMarker) {
            this.filename = filename;
            this.source = source;
            this.status = status;
            this.firstOpenMs = firstOpenMs;
            this.firstDecodedMs = firstDecodedMs;
            this.readyMs = readyMs;
            this.bytes = bytes;
            this.width = width;
            this.height = height;
            this.attempts = attempts;
            this.strictEndMarker = strictEndMarker;
        }
    }

    private static final long PROBE_TIMEOUT_MS = 10_000L;
    private static final long RETRY_MS = 85L;
    private static final long MAX_PHOTO_BYTES = 80L * 1024L * 1024L;
    private static final int MAX_ACTIVE = 3;

    private final ContentResolver resolver;
    private final Uri tree;
    private final Listener listener;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final Map<String, Boolean> booked = new ConcurrentHashMap<>();
    private final Object slots = new Object();

    public PhotoReadProbe(ContentResolver resolver, Uri tree, Listener listener) {
        this.resolver = resolver;
        this.tree = tree;
        this.listener = listener;
    }

    public boolean offer(String filename, long startElapsed, String source) {
        if (!enabled.get() || !ImageCompletionPolicy.isSupported(filename)) return false;
        if (filename == null || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0) {
            return false;
        }
        String key = filename.toLowerCase(Locale.ROOT);
        synchronized (slots) {
            if (booked.containsKey(key) || booked.size() >= MAX_ACTIVE) return false;
            booked.put(key, true);
        }
        try {
            pool.submit(() -> {
                try {
                    run(filename, startElapsed, source);
                } finally {
                    booked.remove(key);
                }
            });
            return true;
        } catch (RuntimeException e) {
            booked.remove(key);
            return false;
        }
    }

    public void stop() {
        enabled.set(false);
        pool.shutdownNow();
    }

    private static final class Found {
        final Uri uri;
        final long declaredModified;
        Found(Uri uri, long declaredModified) {
            this.uri = uri;
            this.declaredModified = declaredModified;
        }
    }

    private Found findChild(String filename) {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return null;
            int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int modifiedColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            if (idColumn < 0 || nameColumn < 0) return null;
            while (cursor.moveToNext()) {
                if (cursor.isNull(nameColumn) || cursor.isNull(idColumn)) continue;
                if (!filename.equals(cursor.getString(nameColumn))) continue;
                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(
                        tree, cursor.getString(idColumn));
                long modified = modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)
                        ? cursor.getLong(modifiedColumn) : 0;
                return new Found(documentUri, modified);
            }
        }
        return null;
    }

    private static final class Sample {
        long bytes;
        long modified;
        boolean terminalMarker;
        boolean decoded;
        int width;
        int height;
    }

    private Sample sample(Found found, String filename) throws IOException {
        Sample result = new Sample();
        result.modified = found.declaredModified;

        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(found.uri, "r")) {
            if (pfd == null) throw new IOException("Document open returned null");
            try (FileInputStream stream = new FileInputStream(pfd.getFileDescriptor())) {
                FileChannel channel = stream.getChannel();
                long length = channel.size();
                result.bytes = length;
                if (length <= 0 || length > MAX_PHOTO_BYTES) {
                    return result;
                }

                int count = (int) Math.min(12L, length);
                byte[] tail = new byte[count];
                ByteBuffer buffer = ByteBuffer.wrap(tail);
                long offset = length - count;
                while (buffer.hasRemaining()) {
                    int n = channel.read(buffer, offset);
                    if (n <= 0) throw new IOException("Unable to read image end bytes");
                    offset += n;
                }
                result.terminalMarker = ImageCompletionPolicy.hasValidEndMarker(
                        filename, tail);
            }
        }

        // The compressed file may have a valid header long before it is finished.
        // Decode only a *sampled bitmap* (max ~1280 px), not a 50MP original.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(found.uri)) {
            if (in == null) throw new IOException("Could not open photo stream");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return result;
        int sampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sampleSize > 1280) {
            sampleSize *= 2;
        }
        BitmapFactory.Options sample = new BitmapFactory.Options();
        sample.inSampleSize = sampleSize;
        sample.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap decoded;
        try (InputStream in = resolver.openInputStream(found.uri)) {
            if (in == null) throw new IOException("Could not reopen photo stream");
            decoded = BitmapFactory.decodeStream(in, null, sample);
        }
        if (decoded != null) {
            result.decoded = true;
            result.width = bounds.outWidth;
            result.height = bounds.outHeight;
            decoded.recycle();
        }
        return result;
    }

    /**
     * Stream the ENTIRE compressed photo to nowhere, not to RAM and not to a
     * network endpoint, ensuring every reported byte can be opened/read.
     */
    private boolean readAllBytes(Uri uri, long expectedLength) {
        if (expectedLength <= 0 || expectedLength > MAX_PHOTO_BYTES) return false;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return false;
            byte[] buffer = new byte[64 * 1024];
            long count = 0;
            int n;
            while (enabled.get() && (n = in.read(buffer)) != -1) {
                count += n;
                if (count > expectedLength) return false;
            }
            return enabled.get() && count == expectedLength;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private void report(String filename, String source, String state,
                        long opened, long decoded, long ready,
                        long bytes, int width, int height, int attempts,
                        boolean strictMarker) {
        if (enabled.get()) {
            listener.onUpdate(new Result(filename, source, state,
                    opened, decoded, ready, bytes, width, height, attempts, strictMarker));
        }
    }

    private void run(String filename, long eventElapsed, String source) {
        long begin = Math.max(0, eventElapsed);
        long firstOpen = -1;
        long firstDecode = -1;
        long completed = -1;
        int attempt = 0;
        long lastBytes = -1;
        long lastModified = -1;
        long lastDecodedSize = -1;
        boolean lastDecoded = false;
        boolean previouslyMarker = false;
        long lastObservedAt = 0;
        int width = 0, height = 0;
        String error = "尚未从系统文件夹提供程序找到该照片";
        boolean strict = ImageCompletionPolicy.hasReliableEndMarker(filename);

        report(filename, source, "开始等待照片可读取", firstOpen, firstDecode,
                completed, 0, 0, 0, 0, strict);

        while (enabled.get() && SystemClock.elapsedRealtime() - begin < PROBE_TIMEOUT_MS) {
            attempt++;
            try {
                Found found = findChild(filename);
                if (found != null) {
                    Sample current = sample(found, filename);
                    if (current.bytes > 0 && firstOpen < 0) {
                        firstOpen = SystemClock.elapsedRealtime() - begin;
                        report(filename, source, "文件已打开，正在测试解码",
                                firstOpen, firstDecode, -1, current.bytes,
                                width, height, attempt, strict);
                    }
                    if (current.bytes > MAX_PHOTO_BYTES) {
                        error = "图片大于80MB，超出实验安全上限";
                        break;
                    }
                    if (current.decoded) {
                        lastDecoded = true;
                        lastDecodedSize = current.bytes;
                        width = current.width;
                        height = current.height;
                        if (firstDecode < 0) {
                            firstDecode = SystemClock.elapsedRealtime() - begin;
                            report(filename, source, "首次成功抽样解码",
                                    firstOpen, firstDecode, -1, current.bytes,
                                    width, height, attempt, strict);
                        }
                    }
                    long currentAt = SystemClock.elapsedRealtime();
                    boolean stable = ImageCompletionPolicy.stable(
                            lastBytes, current.bytes, lastModified, current.modified)
                            && (currentAt - lastObservedAt) >= 60;
                    if (stable && current.terminalMarker && previouslyMarker
                            && current.decoded && lastDecoded
                            && current.bytes == lastDecodedSize) {
                        if (readAllBytes(found.uri, current.bytes)) {
                            completed = SystemClock.elapsedRealtime() - begin;
                            report(filename, source,
                                    strict ? "JPEG/PNG结束标志、抽样解码、稳定与完整字节读取均通过"
                                           : "抽样解码、稳定与完整字节读取通过（格式结束标志未验证）",
                                    firstOpen, firstDecode, completed, current.bytes,
                                    width, height, attempt, strict);
                            return;
                        }
                        error = "图片发生变化或压缩字节尚未全部可读";
                    } else if (!current.terminalMarker) {
                        error = "等待图片结束标志";
                    } else if (!current.decoded) {
                        error = "等待图片能够正常解码";
                    } else {
                        error = "等待文件大小连续两次保持稳定";
                    }
                    lastBytes = current.bytes;
                    lastModified = current.modified;
                    lastObservedAt = currentAt;
                    previouslyMarker = current.terminalMarker;
                    lastDecoded = current.decoded;
                    lastDecodedSize = current.bytes;
                } else {
                    error = "照片尚未在已授权目录中可见";
                }
            } catch (Exception problem) {
                // Temporary import/rename races are normal. Do not crash the
                // successful direct file-event voice notification.
                error = "尚不能读取：" + problem.getClass().getSimpleName();
            }
            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (enabled.get()) {
            report(filename, source, "10秒实验超时：" + error, firstOpen,
                    firstDecode, -1, lastBytes, width, height, attempt, strict);
        }
    }
}
