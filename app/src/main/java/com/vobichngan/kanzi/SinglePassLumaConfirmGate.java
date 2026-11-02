package com.vobichngan.kanzi;

import java.util.Locale;

/**
 * Experimental SINGLE-OCR-pass confirmation using follow-up NV21 luminance only.
 *
 * No second ONNX inference, Bitmap, frame copy, queue, handler, audio or Android API.
 * Reference fingerprints contain only <= 1088 bytes of sampled luminance,
 * never a full camera image. All mutation is synchronized for safe callback usage.
 *
 * This proves motion consistency, NOT that the OCR text is semantically correct.
 * Disabled by default and not wired into the production scan path.
 */
public final class SinglePassLumaConfirmGate {
    public enum Action { SKIP, WAIT, READY, DUPLICATE, REJECT }
    public enum Reason {
        DISABLED, INVALID_INPUT, LOW_CONFIDENCE, TOO_LONG, REFERENCE_TOO_OLD,
        FLAT_OR_BLURRY_ROI, ARMED, UNARMED, OUT_OF_ORDER, TOO_EARLY,
        FRAME_GAP, WINDOW_EXPIRED, FRAME_CHANGED, CONFIRMED, ALREADY_READ
    }

    public static final class Config {
        public final boolean enabled;
        public final int maxTextCodepoints;
        public final float minConfidence;
        public final long maxReferenceAgeMs;
        public final long confirmWindowMs;
        public final long minHoldAfterArmMs;
        public final int requiredNewFrames;
        public final long maxFrameGapMs;
        public final double maxGlobalMeanDiff;
        public final double maxLocalMeanDiff;
        public final double maxChangedFraction;
        public final double minLocalContrast;

        public Config(boolean enabled) {
            this(enabled, 48, .78f, 1200, 650, 160, 2, 400, 5.5, 9.0, .09, 3.0);
        }

        public Config(boolean enabled, int maxTextCodepoints, float minConfidence,
                      long maxReferenceAgeMs, long confirmWindowMs,
                      long minHoldAfterArmMs, int requiredNewFrames, long maxFrameGapMs,
                      double maxGlobalMeanDiff, double maxLocalMeanDiff,
                      double maxChangedFraction, double minLocalContrast) {
            if (maxTextCodepoints < 1 || maxTextCodepoints > 256 ||
                !Float.isFinite(minConfidence) || minConfidence < 0 || minConfidence > 1 ||
                maxReferenceAgeMs < 1 || maxReferenceAgeMs > 10000 ||
                confirmWindowMs < 1 || confirmWindowMs > 10000 ||
                minHoldAfterArmMs < 0 || minHoldAfterArmMs > confirmWindowMs ||
                requiredNewFrames < 1 || requiredNewFrames > 5 ||
                maxFrameGapMs < 1 || maxFrameGapMs > confirmWindowMs ||
                !Double.isFinite(maxGlobalMeanDiff) || maxGlobalMeanDiff < 0 ||
                !Double.isFinite(maxLocalMeanDiff) || maxLocalMeanDiff < 0 ||
                !Double.isFinite(maxChangedFraction) || maxChangedFraction < 0 ||
                maxChangedFraction > 1 ||
                !Double.isFinite(minLocalContrast) || minLocalContrast < 0)
                throw new IllegalArgumentException("Invalid luminance gate configuration");
            this.enabled = enabled;
            this.maxTextCodepoints = maxTextCodepoints;
            this.minConfidence = minConfidence;
            this.maxReferenceAgeMs = maxReferenceAgeMs;
            this.confirmWindowMs = confirmWindowMs;
            this.minHoldAfterArmMs = minHoldAfterArmMs;
            this.requiredNewFrames = requiredNewFrames;
            this.maxFrameGapMs = maxFrameGapMs;
            this.maxGlobalMeanDiff = maxGlobalMeanDiff;
            this.maxLocalMeanDiff = maxLocalMeanDiff;
            this.maxChangedFraction = maxChangedFraction;
            this.minLocalContrast = minLocalContrast;
        }
    }

    public static final class Decision {
        public final Action action;
        public final Reason reason;
        public final String text;
        public final int newFrames;
        private Decision(Action action, Reason reason, String text, int newFrames) {
            this.action = action;
            this.reason = reason;
            this.text = text;
            this.newFrames = newFrames;
        }
        private static Decision of(Action action, Reason reason) {
            return new Decision(action, reason, "", 0);
        }
        private static Decision waiting(Reason reason, int count) {
            return new Decision(Action.WAIT, reason, "", count);
        }
    }

    // Fixed sampling sizes ensure bounded CPU cost independent of 720p/1080p.
    private static final int GLOBAL_COLS = 32;
    private static final int GLOBAL_ROWS = 18;
    private static final int LOCAL_COLS = 32;
    private static final int LOCAL_ROWS = 16;
    private static final int SAMPLE_COUNT = GLOBAL_COLS * GLOBAL_ROWS + LOCAL_COLS * LOCAL_ROWS;

    private final Config config;
    private final byte[] reference = new byte[SAMPLE_COUNT];
    private String pendingText = "";
    private String pendingNormalized = "";
    private String lastSpokenNormalized = "";
    private int srcWidth;
    private int srcHeight;
    private int roiLeft;
    private int roiTop;
    private int roiRight;
    private int roiBottom;
    private long armedAtMs = -1;
    private long acceptedFrameSequence = -1;
    private long lastFrameMs = -1;
    private int newFrameCount;

    public SinglePassLumaConfirmGate() { this(new Config(false)); }
    public SinglePassLumaConfirmGate(Config config) {
        if (config == null) throw new IllegalArgumentException("config");
        this.config = config;
    }

    /**
     * Reference data must be the precise NV21 frame supplied to original OCR.
     * ROI is in that raw frame's pixel coordinates, not rotated display pixels.
     * Time arguments must be from the same monotonic clock domain.
     */
    public synchronized Decision arm(String text, float confidence, byte[] ocrNv21,
                                     int width, int height,
                                     int left, int top, int right, int bottom,
                                     long sourceSequence, long sourceCapturedMs,
                                     long armedNowMs) {
        clearPending();
        if (!config.enabled) return Decision.of(Action.SKIP, Reason.DISABLED);
        if (!validFrame(ocrNv21,width,height) || sourceSequence < 0 ||
            sourceCapturedMs < 0 || armedNowMs < sourceCapturedMs ||
            left < 0 || top < 0 || right > width || bottom > height ||
            right - left < 12 || bottom - top < 12 || armedNowMs < 0)
            return Decision.of(Action.REJECT, Reason.INVALID_INPUT);
        if (armedNowMs - sourceCapturedMs > config.maxReferenceAgeMs)
            return Decision.of(Action.REJECT, Reason.REFERENCE_TOO_OLD);
        if (!Float.isFinite(confidence) || confidence < config.minConfidence || confidence > 1)
            return Decision.of(Action.REJECT, Reason.LOW_CONFIDENCE);
        String normalized = normalize(text);
        if (normalized.length() == 0 ||
            normalized.codePointCount(0,normalized.length()) > config.maxTextCodepoints)
            return Decision.of(Action.REJECT, Reason.TOO_LONG);
        int areaWidth = right - left, areaHeight = bottom - top;
        // Do not run this gate on nearly uniform text crops with inadequate texture.
        fillReference(ocrNv21, width, height, left, top, areaWidth, areaHeight);
        double contrast = localContrast();
        if (contrast < config.minLocalContrast)
            return Decision.of(Action.REJECT, Reason.FLAT_OR_BLURRY_ROI);
        pendingText = text.trim();
        pendingNormalized = normalized;
        srcWidth = width;
        srcHeight = height;
        roiLeft = left;
        roiTop = top;
        roiRight = right;
        roiBottom = bottom;
        armedAtMs = armedNowMs;
        lastFrameMs = armedNowMs;
        acceptedFrameSequence = sourceSequence;
        newFrameCount = 0;
        return Decision.waiting(Reason.ARMED, 0);
    }

    /**
     * Only run on NEW raw camera frames when isArmed() == true.
     * This runs no OCR. Frame buffers are read synchronously, never retained.
     */
    public synchronized Decision observe(byte[] nv21, int width, int height,
                                         long sequence, long nowMs) {
        if (!config.enabled) return Decision.of(Action.SKIP, Reason.DISABLED);
        if (armedAtMs < 0) return Decision.of(Action.SKIP, Reason.UNARMED);
        if (!validFrame(nv21,width,height) || width != srcWidth ||
            height != srcHeight || nowMs < 0)
            return reject(Reason.INVALID_INPUT);
        if (nowMs < lastFrameMs) return reject(Reason.INVALID_INPUT);
        if (sequence <= acceptedFrameSequence)
            return Decision.waiting(Reason.OUT_OF_ORDER, newFrameCount);
        // Time-window expiry and burst gaps are fail-closed.
        if (nowMs - armedAtMs > config.confirmWindowMs)
            return reject(Reason.WINDOW_EXPIRED);
        if (nowMs - lastFrameMs > config.maxFrameGapMs)
            return reject(Reason.FRAME_GAP);
        if (!sameImage(nv21,width,height))
            return reject(Reason.FRAME_CHANGED);

        acceptedFrameSequence = sequence;
        lastFrameMs = nowMs;
        newFrameCount++;
        if (newFrameCount < config.requiredNewFrames ||
            nowMs - armedAtMs < config.minHoldAfterArmMs)
            return Decision.waiting(Reason.TOO_EARLY, newFrameCount);

        String result = pendingText;
        boolean alreadyRead = pendingNormalized.equals(lastSpokenNormalized);
        if (!alreadyRead) lastSpokenNormalized = pendingNormalized;
        int count = newFrameCount;
        clearPending();
        return new Decision(alreadyRead ? Action.DUPLICATE : Action.READY,
                alreadyRead ? Reason.ALREADY_READ : Reason.CONFIRMED,
                alreadyRead ? "" : result, count);
    }

    /** Stop/restart or external trusted new-scene transition. */
    public synchronized void reset() {
        clearPending();
        lastSpokenNormalized = "";
    }

    /** Cancel pending text without allowing previously read text to be repeated. */
    public synchronized void cancel() { clearPending(); }

    public synchronized boolean isArmed() { return armedAtMs >= 0; }

    private Decision reject(Reason reason) {
        clearPending();
        return Decision.of(Action.REJECT, reason);
    }

    private void clearPending() {
        pendingText = "";
        pendingNormalized = "";
        armedAtMs = -1;
        acceptedFrameSequence = -1;
        lastFrameMs = -1;
        newFrameCount = 0;
    }

    private static boolean validFrame(byte[] data,int width,int height) {
        return data != null && width >= 16 && height >= 16 &&
            (long) width * height <= data.length;
    }

    private static int coord(int start, int len, int index, int bins) {
        return start + (int)(((long)(2*index+1)*len) / (2*bins));
    }
    private static int y(byte[] frame,int width,int x,int row) {
        return frame[row * width + x] & 0xff;
    }

    private void fillReference(byte[] frame,int width,int height,
                               int left,int top,int w,int h) {
        int pos = 0;
        for (int r=0;r<GLOBAL_ROWS;r++) {
            int yy=coord(0,height,r,GLOBAL_ROWS);
            for (int c=0;c<GLOBAL_COLS;c++)
                reference[pos++]=frame[yy*width+coord(0,width,c,GLOBAL_COLS)];
        }
        for (int r=0;r<LOCAL_ROWS;r++) {
            int yy=coord(top,h,r,LOCAL_ROWS);
            for (int c=0;c<LOCAL_COLS;c++)
                reference[pos++]=frame[yy*width+coord(left,w,c,LOCAL_COLS)];
        }
    }

    private double localContrast() {
        long gradient=0;
        for (int r=0;r<LOCAL_ROWS;r++)
            for (int c=1;c<LOCAL_COLS;c++) {
                int index=GLOBAL_COLS*GLOBAL_ROWS + r*LOCAL_COLS+c;
                gradient += Math.abs((reference[index] & 0xff) -
                        (reference[index-1] & 0xff));
            }
        return gradient / (double) (LOCAL_ROWS*(LOCAL_COLS-1));
    }

    /**
     * Keep exact comparison as the fast path. Only when it fails, search for a
     * common global+ROI translation within +-2 luma pixels. No full-frame
     * image transform or extra OCR pass. The SAME shift must fit both regions.
     *
     * WARNING: allowing small translations improves hand-jitter tolerance but
     * does NOT establish whether two visually similar words are identical.
     */
    private boolean sameImage(byte[] frame, int width, int height) {
        if (matchAtOffset(frame, width, height, 0, 0)) return true;
        for (int radius=1;radius<=2;radius++) {
            for (int dy=-radius;dy<=radius;dy++) {
                for (int dx=-radius;dx<=radius;dx++) {
                    if (Math.max(Math.abs(dx),Math.abs(dy))!=radius) continue;
                    if (matchAtOffset(frame,width,height,dx,dy)) return true;
                }
            }
        }
        return false;
    }

    private boolean matchAtOffset(byte[] frame,int width,int height,
                                  int shiftX,int shiftY) {
        long total=0;
        int changed=0;
        int offset=0;
        final double maxGlobalTotal=config.maxGlobalMeanDiff*GLOBAL_COLS*GLOBAL_ROWS;
        final double maxGlobalChanged=config.maxChangedFraction*GLOBAL_COLS*GLOBAL_ROWS;
        for (int r=0;r<GLOBAL_ROWS;r++) {
            int yy=coord(0,height,r,GLOBAL_ROWS)+shiftY;
            if(yy<0 || yy>=height) return false;
            for (int c=0;c<GLOBAL_COLS;c++) {
                int xx=coord(0,width,c,GLOBAL_COLS)+shiftX;
                if(xx<0 || xx>=width) return false;
                int diff=Math.abs((frame[yy*width+xx]&0xff)-(reference[offset++]&0xff));
                total+=diff;
                if(diff>=20) changed++;
                if(total>maxGlobalTotal || changed>maxGlobalChanged) return false;
            }
        }
        total=0;
        changed=0;
        int w=roiRight-roiLeft,h=roiBottom-roiTop;
        final double maxLocalTotal=config.maxLocalMeanDiff*LOCAL_COLS*LOCAL_ROWS;
        final double maxLocalChanged=config.maxChangedFraction*LOCAL_COLS*LOCAL_ROWS;
        for (int r=0;r<LOCAL_ROWS;r++) {
            int yy=coord(roiTop,h,r,LOCAL_ROWS)+shiftY;
            if(yy<0 || yy>=height) return false;
            for (int c=0;c<LOCAL_COLS;c++) {
                int xx=coord(roiLeft,w,c,LOCAL_COLS)+shiftX;
                if(xx<0 || xx>=width) return false;
                int diff=Math.abs((frame[yy*width+xx]&0xff)-(reference[offset++]&0xff));
                total+=diff;
                if(diff>=20) changed++;
                if(total>maxLocalTotal || changed>maxLocalChanged) return false;
            }
        }
        return true;
    }

    private static String normalize(String source) {
        if (source == null) return "";
        StringBuilder sb=new StringBuilder(Math.min(source.length(),128));
        boolean ws=false;
        for (int i=0;i<source.length();) {
            int cp=source.codePointAt(i); i+=Character.charCount(cp);
            if (Character.isISOControl(cp)) continue;
            if (Character.isWhitespace(cp)) {
                if (sb.length()>0&&!ws) sb.append(' ');
                ws=true;
            } else {
                sb.appendCodePoint(cp);
                ws=false;
            }
            if (sb.length()>512) return "";
        }
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }
}
