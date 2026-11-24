package com.vobich.bluestar.cloudprobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Zoom >1x crops the same center region the VLC view displays, BEFORE cloud upload.
 * 1x preserves the exact camera JPEG unchanged. The SD original is never modified.
 */
final class C110ZoomFrames {
    static final class JpegResult {
        final byte[] jpeg;
        final int width,height;
        JpegResult(byte[] jpeg,int width,int height){
            this.jpeg=jpeg;this.width=width;this.height=height;
        }
    }

    /** Exact source coordinates of the CURRENT preview visible area.
     *  The same central zoom/portrait cover as VLC's UI-only scale is applied
     *  BEFORE PixelCopy. No intermediate downscaled full-screen bitmap. */
    static C110ZoomGeometry.Region visiblePreviewRegion(int sourceWidth,int sourceHeight,
                           float zoom,boolean portraitCover,float viewportAspect){
        return portraitCover?
            C110ZoomGeometry.centeredCover(sourceWidth,sourceHeight,zoom,viewportAspect):
            C110ZoomGeometry.centered(sourceWidth,sourceHeight,zoom);
    }

    static Bitmap cropPreviewBitmap(Bitmap original,float zoom){
        return cropPreviewBitmap(original,zoom,false,1f);
    }

    static Bitmap cropPreviewBitmap(Bitmap original,float zoom,
                                    boolean portraitCover,float viewportAspect){
        if(original==null||original.isRecycled())
            throw new IllegalArgumentException("C110 live bitmap missing");
        C110ZoomGeometry.Region region=visiblePreviewRegion(
            original.getWidth(),original.getHeight(),zoom,portraitCover,viewportAspect);
        if(region.full(original.getWidth(),original.getHeight()))return original;
        return Bitmap.createBitmap(original,
            region.left,region.top,region.width,region.height);
    }

    static JpegResult cropNativeJpeg(byte[] source,int sourceWidth,int sourceHeight,
                                     float zoom) throws IOException {
        return cropNativeJpeg(source,sourceWidth,sourceHeight,zoom,false,1f);
    }

    static JpegResult cropNativeJpeg(byte[] source,int sourceWidth,int sourceHeight,
                                     float zoom,boolean portraitCover,
                                     float viewportAspect) throws IOException {
        if(source==null||source.length<256||sourceWidth<2||sourceHeight<2)
            throw new IOException("C110原图不完整，禁止提交OCR");
        C110ZoomGeometry.Region region=portraitCover?
            C110ZoomGeometry.centeredCover(sourceWidth,sourceHeight,zoom,viewportAspect):
            C110ZoomGeometry.centered(sourceWidth,sourceHeight,zoom);
        if(region.full(sourceWidth,sourceHeight))
            return new JpegResult(source,sourceWidth,sourceHeight);
        if(!C110ZoomGeometry.photoLooksLikeLandscapeVideo(sourceWidth,sourceHeight))
            throw new IOException("原图画幅不是16:9，无法安全匹配预览缩放区域；已取消识别");
        // Decode only the ROI: avoid allocating/re-encoding a 20MP full image.
        BitmapRegionDecoder decoder=null;
        Bitmap subsection=null;
        try{
            decoder=BitmapRegionDecoder.newInstance(source,0,source.length,false);
            if(decoder==null)throw new IOException("相机JPEG无法区域解码");
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            subsection=decoder.decodeRegion(new Rect(region.left,region.top,
                region.left+region.width,region.top+region.height),options);
            if(subsection==null)
                throw new IOException("相机JPEG区域裁剪失败");
            try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
                if(!subsection.compress(Bitmap.CompressFormat.JPEG,92,out))
                    throw new IOException("相机JPEG区域编码失败");
                byte[] jpeg=out.toByteArray();
                if(jpeg.length<256)throw new IOException("缩放区域JPEG无效");
                return new JpegResult(jpeg,subsection.getWidth(),subsection.getHeight());
            }
        }catch(RuntimeException ex){
            throw new IOException("C110高清原图缩放失败，不使用未放大的整图代替",ex);
        }finally{
            if(subsection!=null&&!subsection.isRecycled())subsection.recycle();
            if(decoder!=null)decoder.recycle();
        }
    }

    /** Only post-download and post-zoom-crop cloud JPEG. Original byte[] is untouched. */
    static JpegResult limitForCloud(byte[] jpeg,int width,int height,int mode)
            throws IOException {
        if(jpeg==null||jpeg.length<256||width<2||height<2)
            throw new IOException("C110上云JPEG无效");
        double scale=C110PhotoUploadPolicy.scaleFor(width,height,mode);
        if(scale>=0.999)return new JpegResult(jpeg,width,height);
        int outW=Math.max(2,(int)Math.round(width*scale));
        int outH=Math.max(2,(int)Math.round(height*scale));
        Bitmap source=null,result=null;
        try{
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            // 5888x3312 JPEG can be decoded 2x sampled before 2560x1440 export.
            if(width>=2*outW&&height>=2*outH)options.inSampleSize=2;
            source=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);
            if(source==null)throw new IOException("C110 1440P解码失败");
            if(source.getWidth()<outW||source.getHeight()<outH)
                throw new IOException("C110 1440P不能插值放大");
            result=Bitmap.createScaledBitmap(source,outW,outH,true);
            try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
                if(!result.compress(Bitmap.CompressFormat.JPEG,90,out))
                    throw new IOException("C110 1440P编码失败");
                byte[] bytes=out.toByteArray();
                if(bytes.length<256)throw new IOException("C110 1440P数据无效");
                return new JpegResult(bytes,result.getWidth(),result.getHeight());
            }
        }catch(RuntimeException error){
            throw new IOException("C110缩图失败，不改用未缩原图",error);
        }finally{
            if(result!=null&&result!=source)result.recycle();
            if(source!=null)source.recycle();
        }
    }

    private C110ZoomFrames(){}
}
