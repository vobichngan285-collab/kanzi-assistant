package com.vobichngan.kanzi;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Passive USB health observations. No camera, USB, GL, OCR or TTS control calls.
 * Frame callback records only two atomic writes; do not allocate image buffers here.
 * Timebase: System.nanoTime / 1_000_000 on all threads.
 *
 * GL "submitted" means the render loop attempted eglSwapBuffers; it does NOT
 * prove that the TextureView physically displayed a new camera image.
 */
public final class UsbCameraHealthMonitor {
    private static final int MAX_LINES = 72;
    private static final long STALL_MS = 2500L;
    private final AtomicLong rawFrames = new AtomicLong();
    private volatile long lastRawAtMs;
    private volatile int lastWidth, lastHeight;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private long openedAtMs, lastSampleAtMs, lastRawCount, lastGlCount;
    private long firstGlCount, firstGlFailureCount;
    private boolean opened;
    private String lastClassification = "";
    private String lastSummary = "等待USB连接";
    private String lastConnection = "尚未打开";

    public static long nowMs() { return System.nanoTime() / 1_000_000L; }
    public long lastRawFrameMs() { return lastRawAtMs; }
    public long totalRawFrames() { return rawFrames.get(); }

    /** Called directly on native UVC callback. Constant time and no allocation. */
    public void onRawNv21(int width, int height) {
        onRawNv21At(width,height,nowMs());
    }

    // Package-private deterministic clock hook for regression tests only.
    void onRawNv21At(int width, int height, long timeMs) {
        lastWidth = width;
        lastHeight = height;
        lastRawAtMs = timeMs;
        rawFrames.incrementAndGet();
    }

    public synchronized void onOpened(long now, long glCounter, long glErrors) {
        opened = true;
        openedAtMs = now;
        lastSampleAtMs = now;
        lastRawCount = rawFrames.get();
        lastGlCount = glCounter;
        firstGlCount = glCounter;
        firstGlFailureCount = glErrors;
        lastConnection = "OPENED";
        lastClassification = "";
        append(now,"STATE OPENED: USB session established (not proof of live frames)");
    }

    public synchronized void onClosed(long now) {
        opened = false;
        lastConnection = "CLOSED";
        lastClassification = "";
        append(now,"STATE CLOSED");
    }

    public synchronized void onError(long now, String message) {
        opened = false;
        lastConnection = "ERROR";
        lastClassification = "";
        append(now, "STATE ERROR "+sanitize(message));
    }

    public synchronized void onEvent(long now, String name) {
        append(now,"EVENT "+sanitize(name));
    }

    public synchronized String sample(long now, long glCounter, long lastGlAtMs,
        long eglErrors, boolean ocrBusy, boolean surfaceReady, boolean usbSelected) {
        if(!usbSelected) return "手机摄像头模式（USB监测暂停）";
        long rawCount = rawFrames.get();
        long delta = Math.max(100L,now-lastSampleAtMs);
        double rawFps = (rawCount-lastRawCount)*1000.0/delta;
        double glFps = (glCounter-lastGlCount)*1000.0/delta;
        lastSampleAtMs = now;
        lastRawCount = rawCount;
        lastGlCount = glCounter;

        long rawAge = lastRawAtMs <= openedAtMs ? Math.max(0,now-openedAtMs) : now-lastRawAtMs;
        long glAge = lastGlAtMs <= openedAtMs ? Math.max(0,now-openedAtMs) : now-lastGlAtMs;
        long eglDelta = Math.max(0,eglErrors-firstGlFailureCount);
        String diagnosis;
        if (!opened) {
            diagnosis = "USB "+lastConnection+"；尚不能判定断流";
        } else if(now-openedAtMs < 3500L) {
            diagnosis = "USB预览启动观察中";
        } else if(rawAge > STALL_MS && glAge > STALL_MS) {
            diagnosis = "USB原始帧与GL提交均停滞";
        } else if(rawAge > STALL_MS) {
            diagnosis = "原始NV21回调停滞，GL仍有提交";
        } else if(glAge > STALL_MS) {
            diagnosis = "原始NV21仍到达，但GL提交停滞";
        } else if(eglDelta > 0) {
            diagnosis = "EGL交换缓冲出现错误";
        } else if(!surfaceReady) {
            diagnosis = "USB TextureView表面未就绪";
        } else {
            diagnosis = "帧与GL提交有活动（不保证屏幕真的更新）";
        }
        String summary = String.format(Locale.US,
            "USB原始 %.1f帧/秒｜GL提交 %.1f次/秒｜OCR %s\n原始距今 %dms｜GL距今 %dms｜EGL错误 %d｜%dx%d\n%s",
            Math.max(0,rawFps),Math.max(0,glFps),ocrBusy?"忙":"空闲",
            rawAge,glAge,eglDelta,lastWidth,lastHeight,diagnosis);
        lastSummary = summary;
        // Event history is bounded to ~one minute; no images, text or model output.
        append(now,String.format(Locale.US,
            "RAW=%.1f GL=%.1f raw_age=%dms gl_age=%dms egl_errors=%d surface=%s OCR=%s %s",
            rawFps,glFps,rawAge,glAge,eglDelta,surfaceReady,ocrBusy,diagnosis));
        if(!diagnosis.equals(lastClassification)) {
            append(now,"CHANGE "+diagnosis);
            lastClassification = diagnosis;
        }
        return summary;
    }

    private static String sanitize(String x) {
        if(x == null) return "";
        return x.replace('\n',' ').replace('\r',' ').substring(0,Math.min(180,x.length()));
    }

    private void append(long now, String message) {
        long seconds=now/1000;
        lines.addLast("T+"+seconds+"s "+message);
        while(lines.size()>MAX_LINES) lines.removeFirst();
    }

    public synchronized String dump() {
        StringBuilder out = new StringBuilder(
            "Blue Star Guide 1.0.3 USB PIPELINE TRACE\n"
            +"Only frame counters, EGL and camera state. No photos, OCR text or audio.\n"
            +"GL submitted != screen visibly refreshed.\n\n");
        out.append("Last: ").append(lastSummary).append("\n\n");
        for(String line:lines) out.append(line).append('\n');
        return out.toString();
    }

    public synchronized String lastClassification() { return lastClassification; }
}
