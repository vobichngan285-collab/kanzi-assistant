package com.vobich.kanzi.craftprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import org.json.JSONArray;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * ONLY recognition of regions supplied by ChineseOCR-Lite DET. No invented
 * words, no spelling correction, no language model and no TTS. The exact
 * original PP-OCRv6 Small REC model and its own dictionary are used with
 * CTC greedy blank/repeat decoding.
 *
 * Crop/normalize/CTC mirrors PaddleOCR v3.7.0 Android SDK as patched for
 * BGR by the original 看字助手 build. No default "read what seems likely".
 *
 * This independent research application does NOT alter the installed App.
 */
final class SmallRecAfterChineseDet implements AutoCloseable {
    static final String MODEL_ASSET = "small_rec.onnx";
    static final String CHARS_ASSET = "small_rec_chars.json";
    static final String EXPECTED_SHA = "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634";
    static final int HEIGHT = 48, MAX_WIDTH = 3200, MAX_LINES = 100;

    static final class Line {
        int detIndex;
        float[] quad;
        String rawText;
        float confidence;
        int cropWidth,cropHeight,recWidth;
        long preprocessMs,forwardMs,postprocessMs;
    }
    static final class Run {
        long detectionMs,recognitionMs,totalMs;
        int detected,eligible,omitted,empty,failed;
        ArrayList<Line> lines=new ArrayList<>();
    }

    private final Context app;
    private final OrtEnvironment env=OrtEnvironment.getEnvironment();
    private OrtSession rec;
    private String input;
    private List<String> characters;
    long loadMs;

    SmallRecAfterChineseDet(Context context) {app=context.getApplicationContext();}

    private static String digest(File target)throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(target)) {
            byte[] b=new byte[65536];int n;
            while((n=in.read(b))!=-1)d.update(b,0,n);
        }
        StringBuilder s=new StringBuilder();
        for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return s.toString();
    }
    void load() throws Exception {
        if(rec!=null)return;
        long start=SystemClock.elapsedRealtimeNanos();
        String content;
        try(InputStream in=app.getAssets().open(CHARS_ASSET)) {
            java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();
            byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);
            content=new String(b.toByteArray(),StandardCharsets.UTF_8);
        }
        JSONArray chars=new JSONArray(content);
        if(chars.length()<5000||chars.length()>30000)throw new IllegalStateException("Small REC 字典长度异常："+chars.length());
        ArrayList<String> words=new ArrayList<>(chars.length());
        for(int i=0;i<chars.length();i++) {
            String text=chars.getString(i);
            if(text.isEmpty())throw new IllegalStateException("Small REC 字典存在空字符："+i);
            words.add(text);
        }
        File model=new File(app.getFilesDir(),MODEL_ASSET);
        if(!model.isFile()||!EXPECTED_SHA.equals(digest(model))) {
            File tmp=new File(app.getFilesDir(),MODEL_ASSET+".tmp");
            try(InputStream in=app.getAssets().open(MODEL_ASSET);
                FileOutputStream out=new FileOutputStream(tmp)) {
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }
            if(!tmp.renameTo(model))throw new IllegalStateException("REC 模型无法保存到应用私有目录");
        }
        if(!EXPECTED_SHA.equals(digest(model)))throw new SecurityException("Small REC 模型 SHA 不匹配");
        try(OrtSession.SessionOptions opts=new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(2);opts.setInterOpNumThreads(1);
            rec=env.createSession(model.getAbsolutePath(),opts);
        }
        if(rec.getInputNames().size()!=1)throw new IllegalStateException("REC 输入数量不正确");
        input=rec.getInputNames().iterator().next();
        TensorInfo shape=(TensorInfo)rec.getInputInfo().get(input).getInfo();
        if(shape.getShape().length!=4||shape.getShape()[1]!=3)
            throw new IllegalStateException("REC 必须是 NCHW RGB/BGR 模型");
        characters=words;
        loadMs=Math.round((SystemClock.elapsedRealtimeNanos()-start)/1e6);
    }

    private static long ms(long since) {
        return Math.round((SystemClock.elapsedRealtimeNanos()-since)/1e6);
    }
    private static double top(DetCompareEngine.Detection b) {
        return Math.min(Math.min(b.quad[1],b.quad[3]),Math.min(b.quad[5],b.quad[7]));
    }
    private static double left(DetCompareEngine.Detection b) {
        return Math.min(Math.min(b.quad[0],b.quad[2]),Math.min(b.quad[4],b.quad[6]));
    }
    private static class Numbered {
        int index;DetCompareEngine.Detection box;
        Numbered(int index,DetCompareEngine.Detection box){this.index=index;this.box=box;}
    }

    Run read(Bitmap image,DetCompareEngine.Result regions) throws Exception {
        if(rec==null)throw new IllegalStateException("先加载 REC");
        long totalStart=SystemClock.elapsedRealtimeNanos();
        ArrayList<Numbered> boxes=new ArrayList<>();
        for(int i=0;i<regions.boxes.size();i++)boxes.add(new Numbered(i,regions.boxes.get(i)));
        boxes.sort(Comparator.comparingDouble((Numbered b)->top(b.box)).thenComparingDouble(b->left(b.box)));
        Run answer=new Run();
        answer.detected=boxes.size();
        answer.eligible=Math.min(MAX_LINES,boxes.size());
        answer.omitted=boxes.size()-answer.eligible;
        Mat rgba=new Mat(),bgr=new Mat();
        try {
            Utils.bitmapToMat(image,rgba);
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            for(int i=0;i<answer.eligible;i++) {
                Numbered b=boxes.get(i);
                Mat crop=null;
                try {
                    crop=cropFromQuad(bgr,b.box.quad);
                    Line line=recognize(crop);
                    line.detIndex=b.index;
                    line.quad=b.box.quad.clone();
                    if(line.rawText.isEmpty())answer.empty++;
                    answer.lines.add(line);
                } catch(Exception e) {
                    // Never silently synthesize a missing word. Stop and
                    // surface a precise model/crop problem to the operator.
                    answer.failed++;
                    throw new IllegalStateException("第"+(i+1)+"行裁切/识别失败，框编号"+b.index+"："+e.getMessage(),e);
                } finally {if(crop!=null)crop.release();}
            }
        } finally {rgba.release();bgr.release();}
        answer.recognitionMs=ms(totalStart);
        answer.totalMs=regions.totalMs+answer.recognitionMs;
        answer.detectionMs=regions.totalMs;
        return answer;
    }

    /**
     * Rebuild a minAreaRect from the model-detected quad just as PaddleOCR
     * QuadTextCrop does. The detector is free to return rotated boxes.
     * This affects only crop geometry, never inserts or edits recognized text.
     */
    private static Mat cropFromQuad(Mat src,float[] q) {
        if(q.length!=8)throw new IllegalArgumentException("检测框需要4个点");
        Point[] pts=new Point[4];
        for(int i=0;i<4;i++)pts[i]=new Point(q[2*i],q[2*i+1]);
        MatOfPoint2f original=new MatOfPoint2f(pts);
        RotatedRect rect=Imgproc.minAreaRect(original);
        original.release();
        Point[] corners=new Point[4];rect.points(corners);
        Arrays.sort(corners,Comparator.comparingDouble(p->p.x));
        Point tl,bl,tr,br;
        if(corners[0].y<=corners[1].y){tl=corners[0];bl=corners[1];}
        else {tl=corners[1];bl=corners[0];}
        if(corners[2].y<=corners[3].y){tr=corners[2];br=corners[3];}
        else {tr=corners[3];br=corners[2];}
        int w=(int)Math.max(Math.hypot(tr.x-tl.x,tr.y-tl.y),Math.hypot(br.x-bl.x,br.y-bl.y));
        int h=(int)Math.max(Math.hypot(bl.x-tl.x,bl.y-tl.y),Math.hypot(br.x-tr.x,br.y-tr.y));
        if(w<4||h<4||w>3200||h>3200)throw new IllegalArgumentException("异常四边形裁切尺寸："+w+"x"+h);
        MatOfPoint2f from=new MatOfPoint2f(tl,tr,br,bl);
        MatOfPoint2f to=new MatOfPoint2f(new Point(0,0),new Point(w,0),new Point(w,h),new Point(0,h));
        Mat transform=Imgproc.getPerspectiveTransform(from,to);
        from.release();to.release();
        Mat result=new Mat();
        try {
            Imgproc.warpPerspective(src,result,transform,new Size(w,h),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE,
                new org.opencv.core.Scalar(0,0,0));
        } finally {transform.release();}
        if((double)result.rows()/result.cols()>=1.5) {
            Mat rotated=new Mat();
            Core.rotate(result,rotated,Core.ROTATE_90_COUNTERCLOCKWISE);
            result.release();return rotated;
        }
        return result;
    }

    private Line recognize(Mat crop) throws Exception {
        long a=SystemClock.elapsedRealtimeNanos();
        int h=crop.rows(),w=crop.cols();
        int targetW=Math.max(1,Math.min(MAX_WIDTH,(int)Math.ceil(HEIGHT*(double)w/h)));
        Mat resized=new Mat();
        float[] tensor;
        try {
            Imgproc.resize(crop,resized,new Size(targetW,HEIGHT),0,0,Imgproc.INTER_LINEAR);
            byte[] pixels=new byte[targetW*HEIGHT*3];
            resized.get(0,0,pixels); // original Paddle Small REC inference.yml: BGR
            int plane=targetW*HEIGHT;
            tensor=new float[3*plane];
            for(int i=0;i<plane;i++)for(int ch=0;ch<3;ch++)
                tensor[ch*plane+i]=((pixels[i*3+ch]&255)/127.5f)-1f;
        } finally {resized.release();}
        Line line=new Line();
        line.cropWidth=w;line.cropHeight=h;line.recWidth=targetW;
        line.preprocessMs=ms(a);
        long b=SystemClock.elapsedRealtimeNanos();
        try(OnnxTensor t=OnnxTensor.createTensor(env,FloatBuffer.wrap(tensor),new long[]{1,3,HEIGHT,targetW});
            OrtSession.Result output=rec.run(java.util.Collections.singletonMap(input,t))) {
            line.forwardMs=ms(b);
            long c=SystemClock.elapsedRealtimeNanos();
            OnnxTensor decoded=(OnnxTensor)output.get(0);
            long[] shape=((TensorInfo)decoded.getInfo()).getShape();
            if(shape.length!=3||shape[0]!=1||shape[1]<1||shape[2]!=characters.size()+1)
                throw new IllegalStateException("CTC字符数与YAML字典不符: output="+Arrays.toString(shape)+
                    " dictionary="+characters.size());
            int steps=(int)shape[1],classes=(int)shape[2];
            if((long)steps*classes>60000000L)throw new IllegalStateException("CTC输出过大");
            FloatBuffer scores=decoded.getFloatBuffer().duplicate();scores.rewind();
            StringBuilder out=new StringBuilder();
            double confidence=0;int retained=0,previous=-1;
            for(int step=0;step<steps;step++) {
                int best=0;float value=-Float.MAX_VALUE;
                for(int clazz=0;clazz<classes;clazz++) {
                    float score=scores.get();
                    if(!Float.isFinite(score))throw new IllegalStateException("CTC输出包含非有限值");
                    if(score>value){value=score;best=clazz;}
                }
                if(best!=0 && best!=previous) {
                    out.append(characters.get(best-1));
                    confidence+=value;retained++;
                }
                previous=best; // A blank resets duplicate suppression.
            }
            line.rawText=out.toString();
            line.confidence=retained==0?0:(float)(confidence/retained);
            line.postprocessMs=ms(c);
        }
        return line;
    }

    @Override public void close() throws Exception {
        if(rec!=null){rec.close();rec=null;}
    }
}
