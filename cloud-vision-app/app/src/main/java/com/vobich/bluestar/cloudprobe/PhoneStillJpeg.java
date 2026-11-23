package com.vobich.bluestar.cloudprobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import java.io.ByteArrayOutputStream;

/**
 * Normalize Camera2's real JPEG still into the same upright coordinates as
 * the existing phone NV21 capture path. Never writes to disk or the gallery.
 * Camera2 requests JPEG_ORIENTATION=0; we apply sensor/display orientation here.
 */
final class PhoneStillJpeg {
    // One source of truth for both native Camera2 still selection and upload cap.
    static final long MAX_UPLOAD_PIXELS=PhoneStillUploadPolicy.ORIGINAL_MAX_PIXELS;
    private PhoneStillJpeg(){}

    static final class Result {
        final byte[] jpeg;
        final int width,height;
        Result(byte[] data,int w,int h){jpeg=data;width=w;height=h;}
    }

    static Result prepare(byte[] original,int clockwiseRotation,
                          boolean mirrorX,boolean flipY,int uploadMode) {
        if(original==null||original.length<4)throw new IllegalArgumentException("高清JPEG为空");
        int angle=((clockwiseRotation%360)+360)%360;
        if(angle%90!=0)throw new IllegalArgumentException("无法处理的高清拍照旋转角度");
        Bitmap source=BitmapFactory.decodeByteArray(original,0,original.length);
        if(source==null)throw new IllegalArgumentException("无法解码手机高清照片");
        Bitmap finalImage=source;
        try{
            int w=source.getWidth(),h=source.getHeight();
            double scale=PhoneStillUploadPolicy.scaleFor(w,h,uploadMode);
            boolean change=angle!=0||mirrorX||flipY||scale<0.999;
            if(!change)return new Result(original,w,h);
            Matrix transform=new Matrix();
            transform.postRotate(angle);
            transform.postScale((float)(mirrorX?-scale:scale),
                                (float)(flipY?-scale:scale));
            finalImage=Bitmap.createBitmap(source,0,0,w,h,transform,true);
            ByteArrayOutputStream output=new ByteArrayOutputStream(
                Math.max(65536,Math.min(original.length,4000000)));
            if(!finalImage.compress(Bitmap.CompressFormat.JPEG,90,output))
                throw new IllegalStateException("手机高清JPEG编码失败");
            return new Result(output.toByteArray(),
                finalImage.getWidth(),finalImage.getHeight());
        }finally{
            if(finalImage!=source)finalImage.recycle();
            source.recycle();
        }
    }
}
