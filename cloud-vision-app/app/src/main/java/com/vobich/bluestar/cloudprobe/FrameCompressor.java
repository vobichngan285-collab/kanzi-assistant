package com.vobich.bluestar.cloudprobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import java.io.ByteArrayOutputStream;

/** Capture-only NV21 encoder. The preview and camera controllers never call this class. */
final class FrameCompressor {
    static final int LONG_720 = 1280;   // landscape 1280x720; portrait 720x1280
    static final int LONG_1080 = 1920; // landscape 1920x1080; portrait 1080x1920

    static final class Plan {
        final int limit;
        final double edgeRatio;
        final String decision;
        Plan(int limit,double edgeRatio,String decision){
            this.limit=limit;this.edgeRatio=edgeRatio;this.decision=decision;
        }
    }

    /**
     * Cheap heuristic on native-resolution luma. This is NOT a blur detector or OCR:
     * it only decides whether extra native pixels may preserve fine details.
     * The threshold needs phone/scene A/B calibration and can be overridden.
     */
    static Plan choose(byte[] nv21,int width,int height,String policy) {
        validate(nv21,width,height);
        if("720".equals(policy))return new Plan(LONG_720,-1,"手动720p");
        if("1080".equals(policy))return new Plan(LONG_1080,-1,"手动1080p");
        if(Math.max(width,height)<1600)return new Plan(LONG_720,-1,"源图小于1080p");
        // Compare narrow and broad luminance gradients. Flat/dark backgrounds
        // are not assumed blurry; preserve 1080 only for recoverable fine detail.
        int step=Math.max(3,Math.min(width,height)/220);
        int x0=width/12,x1=width-x0-4,y0=height/12,y1=height-y0-4;
        int fine=0,broad=0,total=0;
        long sum=0,squares=0;
        for(int y=y0;y<y1;y+=step){
            int row=y*width;
            for(int x=x0;x<x1;x+=step){
                int v=nv21[row+x]&255;
                int d1=Math.max(Math.abs(v-(nv21[row+x+1]&255)),
                    Math.abs(v-(nv21[row+width+x]&255)));
                int d4=Math.max(Math.abs(v-(nv21[row+x+4]&255)),
                    Math.abs(v-(nv21[row+4*width+x]&255)));
                if(d1>=22)fine++;
                if(d4>=25)broad++;
                sum+=v;squares+=(long)v*v;total++;
            }
        }
        double fineRatio=total==0?0:(double)fine/total;
        double broadRatio=total==0?0:(double)broad/total;
        double mean=total==0?0:(double)sum/total;
        double variance=total==0?0:Math.max(0,(double)squares/total-mean*mean);
        int limit=ImageQualityPolicy.pick(Math.max(width,height),policy,
            fineRatio,broadRatio,mean,variance);
        String reason=limit==LONG_1080?"有可见柔和细节，保留原生1080p":
            "清晰或低纹理场景，优先720p";
        return new Plan(limit,fineRatio,reason);
    }

    static byte[] encodeJpeg(byte[] nv21,int width,int height,int longEdge) {
        validate(nv21,width,height);
        int limit=Math.min(LONG_1080,Math.max(LONG_720,longEdge));
        ByteArrayOutputStream original=new ByteArrayOutputStream();
        boolean ok=new YuvImage(nv21,ImageFormat.NV21,width,height,null)
            .compressToJpeg(new Rect(0,0,width,height),83,original);
        if(!ok)throw new IllegalStateException("相机JPEG编码失败");
        int longest=Math.max(width,height);
        if(longest<=limit)return original.toByteArray(); // never invent extra pixels
        Bitmap decoded=BitmapFactory.decodeByteArray(original.toByteArray(),0,original.size());
        if(decoded==null)throw new IllegalStateException("JPEG解析失败");
        int newW=Math.max(2,(int)Math.round(width*(double)limit/longest));
        int newH=Math.max(2,(int)Math.round(height*(double)limit/longest));
        Bitmap scaled=Bitmap.createScaledBitmap(decoded,newW,newH,true);
        ByteArrayOutputStream small=new ByteArrayOutputStream();
        try{
            if(!scaled.compress(Bitmap.CompressFormat.JPEG,80,small))
                throw new IllegalStateException("照片缩放失败");
            return small.toByteArray();
        }finally{
            if(scaled!=decoded)scaled.recycle();
            decoded.recycle();
        }
    }

    static byte[] to720pJpeg(byte[] nv21,int width,int height) {
        return encodeJpeg(nv21,width,height,LONG_720);
    }

    private static void validate(byte[] nv21,int width,int height){
        if(nv21==null||width<2||height<2||(width&1)!=0||(height&1)!=0||
            (long)nv21.length!=(long)width*height*3L/2L)
            throw new IllegalArgumentException("无效的摄像头NV21帧");
    }
}
