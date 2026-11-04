package com.vobich.kanzi.craftprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Debug;
import android.os.SystemClock;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * Standalone *detection only* two-model trial. No recognizer, TTS, network,
 * Paddle SDK changes, read-order repair, or fabricated text. On-device CPU.
 *
 * Small: existing pinned PP-OCRv6 Small DBNet (BGR, long side <=1600).
 * CN-Lite: independently trained DBNet from DayBreak-u/chineseocr_lite
 * (RGB, short side 640). Different inputs are deliberate faithful-native
 * settings, NOT an equal-input-resolution architecture comparison.
 *
 * Uses standard min-area rectangles with analytical rectangle offset to
 * approximate polygon clipper dilation. The output is NOT pixel-identical
 * to Python's PyClipper or Paddle SDK; validate against frozen images.
 */
final class DetCompareEngine implements AutoCloseable {
    static final String SMALL_SHA = "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e";
    static final String CNLITE_SHA = "ec31eb2b7daa39c4e0307ebb290045deecd515f4e9656d5d987eb82148aea867";
    static final long SMALL_BYTES = 9880512L;
    static final long CNLITE_BYTES = 3637234L;

    static final class Model implements AutoCloseable {
        final String id;
        final String asset;
        final String expectedSha;
        final long expectedBytes;
        final boolean chinese;
        OrtSession session;
        String input;
        long loadMs;
        Model(String id, String asset, String sha, long size, boolean cn) {
            this.id=id; this.asset=asset; this.expectedSha=sha; this.expectedBytes=size; this.chinese=cn;
        }
        @Override public void close() throws Exception {
            if (session!=null) { session.close(); session=null; }
        }
    }

    static final class Detection {
        final float[] quad; // TL,TR,BR,BL in source decoded Bitmap coordinates
        final double score;
        Detection(float[] quad,double score) { this.quad=quad;this.score=score; }
    }
    static final class Result {
        String model;
        int modelWidth,modelHeight,mapWidth,mapHeight;
        long preprocessMs,forwardMs,postprocessMs,totalMs,pssKb;
        List<Detection> boxes=new ArrayList<>();
    }

    final Model small=new Model("PP-OCRv6 Small DET","small_det.onnx",SMALL_SHA,SMALL_BYTES,false);
    final Model chinese=new Model("ChineseOCR-Lite DET","cnlite_det.onnx",CNLITE_SHA,CNLITE_BYTES,true);
    private final OrtEnvironment env=OrtEnvironment.getEnvironment();
    private final Context ctx;

    DetCompareEngine(Context context) {ctx=context.getApplicationContext();}

    private static String hex(File file) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try (InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536]; int len;
            while ((len=in.read(buffer))!=-1) hash.update(buffer,0,len);
        }
        StringBuilder out=new StringBuilder();
        for (byte b:hash.digest()) out.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return out.toString();
    }
    void load() throws Exception {
        load(small);
        try {load(chinese);} catch (Exception error) {small.close();throw error;}
    }
    /** Read-only Chinese detector path; do not load the Small DET baseline into memory. */
    void loadChineseOnly() throws Exception { load(chinese); }
    private void load(Model m) throws Exception {
        if(m.session!=null)return;
        long start=SystemClock.elapsedRealtimeNanos();
        File target=new File(ctx.getFilesDir(),m.asset);
        if(!target.isFile()||target.length()!=m.expectedBytes||!m.expectedSha.equals(hex(target))) {
            File temp=new File(ctx.getFilesDir(),m.asset+".tmp");
            try(InputStream input=ctx.getAssets().open(m.asset);FileOutputStream out=new FileOutputStream(temp)) {
                byte[] buf=new byte[65536];int n;
                while((n=input.read(buf))!=-1)out.write(buf,0,n);
            }
            if(!temp.renameTo(target))throw new IllegalStateException("无法保存模型："+m.id);
        }
        if(target.length()!=m.expectedBytes||!m.expectedSha.equals(hex(target)))
            throw new SecurityException("模型SHA不匹配，禁止运行："+m.id);
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            m.session=env.createSession(target.getAbsolutePath(),options);
            if(m.session.getInputNames().size()!=1) throw new IllegalStateException("模型输入数量异常："+m.id);
            m.input=m.session.getInputNames().iterator().next();
            TensorInfo t=(TensorInfo)m.session.getInputInfo().get(m.input).getInfo();
            if(t.getShape().length!=4||t.getShape()[1]!=3)
                throw new IllegalStateException("模型输入形状异常："+m.id);
        }
        m.loadMs=millis(SystemClock.elapsedRealtimeNanos()-start);
    }
    private static long millis(long delta) {return Math.round(delta/1000000.);}
    private static int multiple32Small(double v) {return Math.max(32,(int)(Math.rint((int)v/32.)*32));}
    private static float clip(double n,double max) {return (float)Math.max(0,Math.min(max,n));}

    Result run(Bitmap original,Model model) throws Exception {
        if(model.session==null)throw new IllegalStateException("先加载模型");
        final int srcW=original.getWidth(),srcH=original.getHeight();
        if(srcW<=0||srcH<=0||srcW>3200||srcH>3200)
            throw new IllegalArgumentException("仅接受解码后最长边≤3200的固定图片");
        long start=SystemClock.elapsedRealtimeNanos();
        int w,h;
        if(model.chinese) {
            // Exactly mirrors ChineseOCR-Lite's original short-side=640 path.
            if(srcH<srcW) {
                h=640; w=Math.max(32,(int)Math.floor(srcW*(640.0/srcH)/32.0)*32);
            } else {
                w=640; h=Math.max(32,(int)Math.floor(srcH*(640.0/srcW)/32.0)*32);
            }
        } else {
            double ratio=Math.min(1.0,1600.0/Math.max(srcW,srcH));
            w=multiple32Small(srcW*ratio); h=multiple32Small(srcH*ratio);
            if(Math.max(w,h)>1600) {
                double again=1600.0/Math.max(w,h);
                w=multiple32Small(w*again); h=multiple32Small(h*again);
            }
        }
        if((long)w*h>4000000L)throw new IllegalArgumentException("模型输入像素超出4MP上限，换一张常见横/竖图");
        Mat rgba=new Mat(),channels=new Mat(),resized=new Mat();
        try {
            Utils.bitmapToMat(original,rgba);
            Imgproc.cvtColor(rgba,channels,model.chinese?Imgproc.COLOR_RGBA2RGB:Imgproc.COLOR_RGBA2BGR);
            Imgproc.resize(channels,resized,new Size(w,h),0,0,Imgproc.INTER_LINEAR);
            byte[] pixels=new byte[w*h*3];
            resized.get(0,0,pixels);
            float[] input=new float[3*w*h];
            int plane=w*h;
            float[] means={.485f,.456f,.406f},stds={.229f,.224f,.225f};
            for(int i=0;i<plane;i++) for(int c=0;c<3;c++) {
                int n=pixels[i*3+c]&255;
                input[c*plane+i]=(n/255f-means[c])/stds[c];
            }
            Result result=new Result();
            result.model=model.id;result.modelWidth=w;result.modelHeight=h;
            long preprocessDone=SystemClock.elapsedRealtimeNanos();
            result.preprocessMs=millis(preprocessDone-start);
            try(OnnxTensor tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,h,w});
                OrtSession.Result outputs=model.session.run(Collections.singletonMap(model.input,tensor))) {
                long forwardDone=SystemClock.elapsedRealtimeNanos();
                result.forwardMs=millis(forwardDone-preprocessDone);
                OnnxTensor probs=(OnnxTensor)outputs.get(0);
                long[] shape=((TensorInfo)probs.getInfo()).getShape();
                if(shape.length!=4||shape[0]!=1||shape[1]!=1)
                    throw new IllegalStateException("输出必须为 NCHW 单通道概率图："+model.id);
                int mh=(int)shape[2],mw=(int)shape[3];
                if(mh<1||mw<1||((long)mh*mw)>6000000)
                    throw new IllegalStateException("输出尺寸异常："+mh+"x"+mw);
                result.mapWidth=mw;result.mapHeight=mh;
                float[] probabilities=new float[mw*mh];
                probs.getFloatBuffer().get(probabilities);
                result.boxes=decode(probabilities,mw,mh,srcW,srcH,model.chinese);
                result.postprocessMs=millis(SystemClock.elapsedRealtimeNanos()-forwardDone);
            }
            result.totalMs=millis(SystemClock.elapsedRealtimeNanos()-start);
            result.pssKb=Debug.getPss();
            return result;
        } finally {
            rgba.release();channels.release();resized.release();
        }
    }

    private static List<Detection> decode(float[] proba,int mw,int mh,int sw,int sh,boolean cn) {
        Mat map=new Mat(mh,mw,CvType.CV_32FC1);
        Mat binaryFloat=new Mat(),binary=new Mat(),hierarchy=new Mat();
        List<MatOfPoint> contours=new ArrayList<>();
        List<Detection> detected=new ArrayList<>();
        try {
            map.put(0,0,proba);
            Imgproc.threshold(map,binaryFloat,cn?.30:.20,255,Imgproc.THRESH_BINARY);
            binaryFloat.convertTo(binary,CvType.CV_8U);
            Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
            int limit=Math.min(contours.size(),cn?1000:3000);
            for(int i=0;i<limit;i++) {
                MatOfPoint contour=contours.get(i);
                if(contour.rows()<3)continue;
                MatOfPoint2f cf=new MatOfPoint2f(contour.toArray());
                RotatedRect raw=Imgproc.minAreaRect(cf);
                cf.release();
                if(Math.min(raw.size.width,raw.size.height)<3)continue;
                Point[] quad=new Point[4];raw.points(quad);
                double score=boxScore(map,cn?contour:rectContour(quad));
                if(score<(cn?.50:.45))continue;
                double area=raw.size.width*raw.size.height;
                double perimeter=2*(raw.size.width+raw.size.height);
                if(perimeter<1e-5)continue;
                double grow=(cn?2.0:1.4)*area/perimeter;
                // PyClipper round-joint offset around a rectangle has the same
                // limiting dimensions; analytical approximation avoids
                // bundling GPL implementation or a third-party clipper library.
                RotatedRect expanded=new RotatedRect(raw.center,
                    new Size(raw.size.width+2*grow,raw.size.height+2*grow),raw.angle);
                if(Math.min(expanded.size.width,expanded.size.height)<5)continue;
                Point[] pts=new Point[4];expanded.points(pts);
                float[] q=new float[8];
                for(int j=0;j<4;j++) {
                    q[2*j]=clip(Math.rint(pts[j].x*sw/mw),sw);
                    q[2*j+1]=clip(Math.rint(pts[j].y*sh/mh),sh);
                }
                float minx=Math.min(Math.min(q[0],q[2]),Math.min(q[4],q[6]));
                float maxx=Math.max(Math.max(q[0],q[2]),Math.max(q[4],q[6]));
                float miny=Math.min(Math.min(q[1],q[3]),Math.min(q[5],q[7]));
                float maxy=Math.max(Math.max(q[1],q[3]),Math.max(q[5],q[7]));
                if(maxx-minx>3&&maxy-miny>3)detected.add(new Detection(q,score));
            }
            return detected;
        } finally {
            for(MatOfPoint contour:contours)contour.release();
            map.release();binaryFloat.release();binary.release();hierarchy.release();
        }
    }
    private static MatOfPoint rectContour(Point[] p) {return new MatOfPoint(p);}
    private static double boxScore(Mat image,MatOfPoint polygon) {
        Point[] pts=polygon.toArray();
        int w=image.cols(),h=image.rows();
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
        double maxX=-1,maxY=-1;
        for(Point p:pts) {minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);
            maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);}
        int x0=Math.max(0,Math.min(w-1,(int)Math.floor(minX)));
        int x1=Math.max(0,Math.min(w-1,(int)Math.ceil(maxX)));
        int y0=Math.max(0,Math.min(h-1,(int)Math.floor(minY)));
        int y1=Math.max(0,Math.min(h-1,(int)Math.ceil(maxY)));
        if(x1<x0||y1<y0)return 0;
        Rect roi=new Rect(x0,y0,x1-x0+1,y1-y0+1);
        Mat mask=Mat.zeros(roi.height,roi.width,CvType.CV_8UC1);
        Mat region=image.submat(roi);
        try {
            Point[] shifted=new Point[pts.length];
            for(int i=0;i<pts.length;i++)
                shifted[i]=new Point(Math.floor(pts[i].x-x0),Math.floor(pts[i].y-y0));
            MatOfPoint poly=new MatOfPoint(shifted);
            try {Imgproc.fillPoly(mask,Collections.singletonList(poly),new org.opencv.core.Scalar(1));}
            finally {poly.release();}
            return Core.mean(region,mask).val[0];
        } finally {mask.release();region.release();}
    }
    @Override public void close() throws Exception {small.close();chinese.close();}
}
