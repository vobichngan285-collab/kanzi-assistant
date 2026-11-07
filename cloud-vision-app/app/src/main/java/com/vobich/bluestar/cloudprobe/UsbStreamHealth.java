package com.vobich.bluestar.cloudprobe;

/** Pure Java USB preview health classification; no photos and no per-frame logs. */
final class UsbStreamHealth {
    static final int OPENING=0,HEALTHY=1,BOTH_STALLED=2,RAW_STALLED=3,PREVIEW_STALLED=4;
    static final class Sample {
        final int state,rawFps10,previewFps10;
        final long rawAgeMs,previewAgeMs,elapsedMs;
        Sample(int state,int rawFps10,int previewFps10,long rawAgeMs,
               long previewAgeMs,long elapsedMs){
            this.state=state;this.rawFps10=rawFps10;this.previewFps10=previewFps10;
            this.rawAgeMs=rawAgeMs;this.previewAgeMs=previewAgeMs;this.elapsedMs=elapsedMs;
        }
    }
    private long previousAt=-1,previousRaw,previousPreview;
    void reset(){previousAt=-1;previousRaw=previousPreview=0;}
    Sample sample(long now,long openedAt,long totalRaw,long totalPreview,
                  long lastRawAt,long lastPreviewAt,boolean opened){
        long elapsed=previousAt<0?0:Math.max(1,now-previousAt);
        long rawDelta=previousAt<0?0:Math.max(0,totalRaw-previousRaw);
        long viewDelta=previousAt<0?0:Math.max(0,totalPreview-previousPreview);
        int rawFps=previousAt<0?0:(int)Math.min(10000,rawDelta*10000L/elapsed);
        int viewFps=previousAt<0?0:(int)Math.min(10000,viewDelta*10000L/elapsed);
        long rawAge=lastRawAt<=0?-1:Math.max(0,now-lastRawAt);
        long viewAge=lastPreviewAt<=0?-1:Math.max(0,now-lastPreviewAt);
        // 5Hz NV21 sampling is sufficient; inspect age rather than assume 30fps.
        boolean warmed=opened&&openedAt>0&&now-openedAt>=3500L;
        boolean rawStale=rawAge<0||rawAge>=1800L;
        boolean viewStale=viewAge<0||viewAge>=1800L;
        int state=!warmed?OPENING:rawStale&&viewStale?BOTH_STALLED:
            rawStale?RAW_STALLED:viewStale?PREVIEW_STALLED:HEALTHY;
        previousAt=now;previousRaw=totalRaw;previousPreview=totalPreview;
        return new Sample(state,rawFps,viewFps,rawAge,viewAge,elapsed);
    }
}
