package com.vobichngan.kanzi;

/**
 * Very cheap, on-device scene-change tracker for LIVE TEXT scanning only.
 *
 * Camera frames remain on the camera thread. No bitmap allocations, OCR, AI,
 * or text changes happen here. A scene identity changes after two sufficiently
 * different luma samples (to avoid a single exposure glitch). Sampling a
 * 24x16 grid about 10 times/sec costs far less than running OCR every frame.
 *
 * The generation token prevents an OCR result captured from an old camera
 * target from interrupting speech for a newer target.
 */
public final class RealtimeSceneTracker {
    private static final int COLS = 24;
    private static final int ROWS = 16;
    private static final long SAMPLE_INTERVAL_MS = 90L;
    private byte[] anchor;
    private int anchorWidth, anchorHeight;
    private long generation;
    private long lastSampleAt = Long.MIN_VALUE;
    private int changedSamples;

    public synchronized void reset() {
        anchor = null;
        anchorWidth = 0;
        anchorHeight = 0;
        changedSamples = 0;
        lastSampleAt = Long.MIN_VALUE;
        generation++;
    }

    public synchronized long generation() { return generation; }

    public synchronized long observe(byte[] nv21,int width,int height,long nowMs) {
        if (nv21 == null || width < 2 || height < 2 ||
            nv21.length < (long) width * height) return generation;
        if (anchor != null && anchorWidth == width && anchorHeight == height &&
            nowMs - lastSampleAt >= 0 && nowMs - lastSampleAt < SAMPLE_INTERVAL_MS)
            return generation;
        byte[] next = new byte[COLS * ROWS];
        int p=0;
        for(int y=0;y<ROWS;y++) {
            int sy=(y*(height-1))/(ROWS-1);
            for(int x=0;x<COLS;x++) {
                int sx=(x*(width-1))/(COLS-1);
                next[p++]=nv21[sy*width+sx];
            }
        }
        lastSampleAt=nowMs;
        if(anchor==null || anchorWidth!=width || anchorHeight!=height) {
            anchor=next;
            anchorWidth=width;
            anchorHeight=height;
            changedSamples=0;
            generation++;
            return generation;
        }
        int changed=0;
        long total=0;
        for(int i=0;i<next.length;i++) {
            int difference=Math.abs((next[i]&255)-(anchor[i]&255));
            total+=difference;
            if(difference>=23) changed++;
        }
        double mean=total/(double)next.length;
        double fraction=changed/(double)next.length;
        // Large scene changes, not ordinary camera noise. Every condition is
        // calibrated as a conservative trigger, NOT a recognition-quality score.
        boolean different=(mean>=11.0 && fraction>=.14) ||
            (mean>=17.0 && fraction>=.07) || mean>=27.0;
        if(!different) {
            changedSamples=0;
        } else if(++changedSamples>=2) {
            anchor=next;
            changedSamples=0;
            generation++;
        }
        return generation;
    }
}
