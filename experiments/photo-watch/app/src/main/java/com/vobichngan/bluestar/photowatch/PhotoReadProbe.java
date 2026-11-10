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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local-only image-read timing lab. Does not change or block FileObserver
 * -> TTS.speak and never transmits or saves a user's photo.
 *
 * Tries a known authorized Android ExternalStorageProvider child URI first
 * (avoids a potentially slow full SAF directory listing). Falls back to
 * listing the selected SAF directory if that shortcut is unsupported.
 *
 * A stable decoded, fully readable file is an "OCR-ready candidate". This
 * cannot guarantee a camera app won't rewrite the same file later.
 *
 * JPEG's EOI (FF D9) can precede appended Motion Photo video: requiring the
 * LAST two bytes to be FF D9 was an incorrect v0.1.3 assumption.
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
        public final String accessPath;
        public final String markerInfo;

        Result(String filename, String source, String status,
               long firstOpenMs, long firstDecodedMs, long readyMs,
               long bytes, int width, int height, int attempts,
               boolean strictEndMarker, String accessPath, String markerInfo) {
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
            this.accessPath = accessPath;
            this.markerInfo = markerInfo;
        }
    }

    private static final long PROBE_TIMEOUT_MS = 10_000L;
    private static final long RETRY_MS = 85L;
    private static final long FALLBACK_RETRY_MS = 350L;
    private static final long MAX_PHOTO_BYTES = 80L * 1024L * 1024L;
    private static final int MAX_ACTIVE = 3;
    private static final String EXTERNAL_STORAGE_AUTHORITY =
            "com.android.externalstorage.documents";

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
        if (!enabled.get() || !ImageCompletionPolicy.isSafePhotoLeaf(filename)) return false;
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
        final String accessPath;
        Found(Uri uri, long declaredModified, String accessPath) {
            this.uri = uri;
            this.declaredModified = declaredModified;
            this.accessPath = accessPath;
        }
    }

    /**
     * Android's built-in ExternalStorageProvider uses path-shaped document
     * IDs for primary shared storage. This optimization is ONLY tried for
     * the EXACT previously authorized Camera directory, with a safe single
     * filename. It grants no access beyond the user-selected tree.
     *
     * Other providers/phones may reject this URI: fall back to SAF listing.
     */
    private Found knownChildUri(String filename) {
        if (!EXTERNAL_STORAGE_AUTHORITY.equals(tree.getAuthority())
                || !ImageCompletionPolicy.isSafePhotoLeaf(filename)) {
            return null;
        }
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        if (!"primary:DCIM/Camera".equalsIgnoreCase(treeId)) return null;
        String childId = treeId + "/" + filename;
        Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId);
        // 0 means mtime unknown; two stable byte counts are still required.
        return new Found(uri, 0, "授权目录子文件 URI（无需列举目录）");
    }

    private Found findChildByListing(String filename) {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor c = resolver.query(children, projection, null, null, null)) {
            if (c == null) return null;
            int id = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int name = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int modified = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            if (id < 0 || name < 0) return null;
            while (c.moveToNext()) {
                if (c.isNull(id) || c.isNull(name)) continue;
                if (!filename.equals(c.getString(name))) continue;
                Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(id));
                long time = modified < 0 || c.isNull(modified) ? 0 : c.getLong(modified);
                return new Found(uri, time, "SAF 目录列表（备用方式）");
            }
        }
        return null;
    }

    private static final class Sample {
        long bytes;
        long firstOpenElapsed;
        long modified;
        boolean tailMarker;
        boolean decoded;
        int width;
        int height;
    }

    private Sample sample(Found found, String filename) throws IOException {
        Sample sample = new Sample();
        sample.modified = found.declaredModified;
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(found.uri, "r")) {
            if (pfd == null) throw new IOException("Photo document open returned null");
            // The FileChannel size is used because provider mtime or statSize
            // may be unavailable under the Android scoped-storage model.
            try (FileInputStream stream = new FileInputStream(pfd.getFileDescriptor())) {
                FileChannel channel = stream.getChannel();
                long size = channel.size();
                sample.bytes = size;
                if (size > 0) sample.firstOpenElapsed = SystemClock.elapsedRealtime();
                if (size <= 0 || size > MAX_PHOTO_BYTES) return sample;
                int count = (int) Math.min(12L, size);
                byte[] tail = new byte[count];
                ByteBuffer bytes = ByteBuffer.wrap(tail);
                long at = size - count;
                while (bytes.hasRemaining()) {
                    int n = channel.read(bytes, at);
                    if (n <= 0) throw new IOException("Cannot read image tail");
                    at += n;
                }
                sample.tailMarker = ImageCompletionPolicy.hasValidEndMarker(filename, tail);
            }
        }
        BitmapFactory.Options header = new BitmapFactory.Options();
        header.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(found.uri)) {
            if (in == null) throw new IOException("Photo header stream unavailable");
            BitmapFactory.decodeStream(in, null, header);
        }
        if (header.outWidth <= 0 || header.outHeight <= 0) return sample;
        int divisor = 1;
        while (Math.max(header.outWidth, header.outHeight) / divisor > 1280) divisor *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = divisor;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = resolver.openInputStream(found.uri)) {
            if (in == null) throw new IOException("Photo decoding stream unavailable");
            Bitmap decoded = BitmapFactory.decodeStream(in, null, opts);
            if (decoded != null) {
                sample.decoded = true;
                sample.width = header.outWidth;
                sample.height = header.outHeight;
                decoded.recycle();
            }
        }
        return sample;
    }

    private static final class StreamResult {
        long bytesRead;
        boolean allBytesReadable;
        boolean jpegEoiSeen;
    }

    /**
     * Stream compressed bytes through a small reusable buffer, never into an
     * in-memory full photo copy. Also scans for JPEG FF D9 appearing ANYWHERE
     * (not a full JPEG parser); the actual marker may precede appended MP4.
     */
    private StreamResult readAllBytes(Uri uri, long expectedBytes) {
        StreamResult result = new StreamResult();
        if (expectedBytes <= 0 || expectedBytes > MAX_PHOTO_BYTES) return result;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return result;
            byte[] buffer = new byte[64 * 1024];
            int last = -1;
            int n;
            while (enabled.get() && (n = in.read(buffer)) >= 0) {
                for (int k = 0; k < n; k++) {
                    int next = buffer[k] & 0xff;
                    if (last == 0xff && next == 0xd9) result.jpegEoiSeen = true;
                    last = next;
                }
                result.bytesRead += n;
                if (result.bytesRead > expectedBytes) return result;
            }
            result.allBytesReadable = enabled.get() && result.bytesRead == expectedBytes;
        } catch (IOException | RuntimeException ignored) {
            result.allBytesReadable = false;
        }
        return result;
    }

    /** Re-check length after the full byte read; writer may still append. */
    private boolean sizeStillMatches(Uri uri, long expectedBytes) {
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd == null) return false;
            long size = pfd.getStatSize();
            if (size >= 0) return size == expectedBytes;
            try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
                return in.getChannel().size() == expectedBytes;
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private void report(String filename, String source, String status,
                        long openMs, long decodeMs, long readyMs,
                        long bytes, int width, int height, int tries,
                        boolean strictMarker, String route, String markerInfo) {
        if (enabled.get()) listener.onUpdate(new Result(filename, source, status,
                openMs, decodeMs, readyMs, bytes, width, height, tries,
                strictMarker, route, markerInfo));
    }

    private void run(String filename, long eventElapsed, String source) {
        long start = eventElapsed;
        long firstOpen = -1, firstDecode = -1, completed = -1;
        long priorBytes = -1, priorModified = -1, priorAt = 0;
        boolean priorDecoded = false;
        int width = 0, height = 0, tries = 0;
        long latestBytes = 0;
        boolean strict = ImageCompletionPolicy.hasReliableEndMarker(filename);
        String markerInfo = "尚未读取结束标志";
        String route = "等待授权照片文件";
        String reason = "照片尚未出现在已授权的目录中";
        Found direct = knownChildUri(filename);
        Found listing = null;
        long lastListingAt = 0;
        report(filename, source, "正在等待新照片可打开", -1, -1, -1,
                0, 0, 0, 0, strict, route, markerInfo);

        while (enabled.get() && SystemClock.elapsedRealtime() - start < PROBE_TIMEOUT_MS) {
            tries++;
            Sample now = null;
            Found used = null;
            // Fast attempt: no full directory scan, no image read before
            // the unchanged TTS cue in PhotoWatchService.
            if (direct != null) {
                try {
                    now = sample(direct, filename);
                    used = direct;
                } catch (IOException | RuntimeException ignored) {
                    reason = "已授权子文件 URI 暂时不可打开";
                }
            }
            // Fallback only when fast direct open has failed. Avoid blocking
            // every 85ms on a potentially slow DocumentsProvider query.
            if (now == null) {
                long time = SystemClock.elapsedRealtime();
                if (direct == null || time - start >= 250) {
                    if (listing == null || time - lastListingAt >= FALLBACK_RETRY_MS) {
                        try {
                            listing = findChildByListing(filename);
                        } catch (RuntimeException ignored) {
                            listing = null;
                        }
                        lastListingAt = SystemClock.elapsedRealtime();
                    }
                    if (listing != null) {
                        try {
                            now = sample(listing, filename);
                            used = listing;
                        } catch (IOException | RuntimeException ignored) {
                            reason = "目录已找到照片，但尚未可以打开";
                        }
                    }
                }
            }

            if (now != null && used != null) {
                latestBytes = now.bytes;
                route = used.accessPath;
                if (now.bytes > MAX_PHOTO_BYTES) {
                    reason = "照片超过实验80MB的上限";
                    break;
                }
                if (now.bytes > 0 && firstOpen < 0) {
                    firstOpen = now.firstOpenElapsed - start;
                    report(filename, source, "首次成功打开文件", firstOpen,
                            firstDecode, -1, now.bytes, width, height,
                            tries, strict, route, markerInfo);
                }
                if (now.decoded) {
                    width = now.width;
                    height = now.height;
                    if (firstDecode < 0) {
                        firstDecode = SystemClock.elapsedRealtime() - start;
                        report(filename, source, "首次成功抽样解码", firstOpen,
                                firstDecode, -1, now.bytes, width, height,
                                tries, strict, route, markerInfo);
                    }
                }
                long nowAt = SystemClock.elapsedRealtime();
                boolean stable = ImageCompletionPolicy.stable(
                        priorBytes, now.bytes, priorModified, now.modified)
                        && nowAt - priorAt >= 60L;
                boolean isPng = filename.toLowerCase(Locale.ROOT).endsWith(".png");
                boolean isJpeg = filename.toLowerCase(Locale.ROOT).endsWith(".jpg")
                        || filename.toLowerCase(Locale.ROOT).endsWith(".jpeg");

                if (isJpeg) {
                    markerInfo = now.tailMarker
                            ? "JPEG文件最后2字节是 FF D9"
                            : "JPEG末尾不是 FF D9：可能包含动态照片附加数据";
                } else if (isPng) {
                    markerInfo = now.tailMarker ? "PNG结尾 IEND 正常"
                            : "PNG结尾 IEND 尚未出现";
                } else {
                    markerInfo = "本格式不检查统一的结尾标志";
                }

                if (stable && now.decoded && priorDecoded && now.bytes > 0
                        && (!isPng || now.tailMarker)) {
                    StreamResult full = readAllBytes(used.uri, now.bytes);
                    boolean jpegValid = !isJpeg || full.jpegEoiSeen;
                    boolean stableAfterRead = full.allBytesReadable
                            && sizeStillMatches(used.uri, now.bytes);
                    boolean ready = ImageCompletionPolicy.isReadReadyCandidate(
                            stable, now.decoded, stableAfterRead) && jpegValid;
                    if (ready) {
                        completed = SystemClock.elapsedRealtime() - start;
                        if (isJpeg) {
                            markerInfo = full.jpegEoiSeen
                                    ? (now.tailMarker
                                    ? "JPEG EOI 位于末尾"
                                    : "JPEG内发现 EOI，后面可能附带动态照片数据")
                                    : "未找到 JPEG EOI";
                        }
                        report(filename, source,
                                "本地可读候选：解码成功、大小稳定、完整字节读完"
                                + "（未来上传仍需保证文件不再被修改）",
                                firstOpen, firstDecode, completed,
                                now.bytes, width, height, tries, strict, route, markerInfo);
                        return;
                    }
                    if (isJpeg && !jpegValid) reason = "JPEG文件内部尚未发现 EOI";
                    else if (!stableAfterRead) reason = "完整字节读取前后文件大小仍在变化";
                } else if (!now.decoded) {
                    reason = "等待完整图片可以抽样解码";
                } else if (isPng && !now.tailMarker) {
                    reason = "等待 PNG IEND 结束标志";
                } else {
                    reason = "等待文件大小在两轮探测中保持稳定";
                }
                priorBytes = now.bytes;
                priorModified = now.modified;
                priorAt = nowAt;
                priorDecoded = now.decoded;
            } else {
                reason = "照片尚未在 SAF 授权范围内可打开";
            }

            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        report(filename, source, "10秒实验结束：" + reason, firstOpen,
                firstDecode, -1, latestBytes, width, height,
                tries, strict, route, markerInfo);
    }
}
