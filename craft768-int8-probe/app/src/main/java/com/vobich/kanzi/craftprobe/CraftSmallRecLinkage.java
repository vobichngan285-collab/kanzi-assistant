package com.vobich.kanzi.craftprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Debug;
import android.os.SystemClock;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * EXPERIMENTAL ONLY — CRAFT768 INT8 box -> PP-OCRv6 Small REC crop inference.
 *
 * Isolated phone laboratory. Not referenced by production 看字助手 or its TTS.
 * No model/character file is committed. The separate CI stage compiles this
 * class without generating an APK and tests full text on Linux with the
 * original same models. If a future, separately-approved test APK is built,
 * model+dictionary must be embedded and checked before calling load().
 */
final class CraftSmallRecLinkage implements AutoCloseable {
    static final String MODEL_SHA =
        "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634";
    static final long MODEL_BYTES = 21_159_378L;
    static final int REC_HEIGHT = 48;
    static final int MAX_WIDTH = 3200;
    static final int MAX_BOXES = 150;

    static final class Line implements SmallRecCtc.Positioned {
        final int originalBoxIndex;
        final String text;
        final float confidence;
        final float x0, y0, y1;
        final long recMs;
        Line(int originalBoxIndex, String text, float confidence,
             float x0, float y0, float y1, long recMs) {
            this.originalBoxIndex=originalBoxIndex;
            this.text=text;
            this.confidence=confidence;
            this.x0=x0;
            this.y0=y0;
            this.y1=y1;
            this.recMs=recMs;
        }
        @Override public float left() { return x0; }
        @Override public float top() { return y0; }
        @Override public float bottom() { return y1; }
    }

    static final class Report {
        final String text;
        final int offeredBoxes, attemptedCrops, recognizedBoxes;
        final int skippedBoxes;
        final long cropAndRecognitionMs;
        final long modelInferenceMs;
        final long recModelLoadMs;
        final long pssAfterKb;
        final List<Line> lines;

        Report(String text, int offeredBoxes, int attemptedCrops,
               int recognizedBoxes, int skippedBoxes,
               long cropAndRecognitionMs, long modelInferenceMs,
               long recModelLoadMs, long pssAfterKb, List<Line> lines) {
            this.text=text;
            this.offeredBoxes=offeredBoxes;
            this.attemptedCrops=attemptedCrops;
            this.recognizedBoxes=recognizedBoxes;
            this.skippedBoxes=skippedBoxes;
            this.cropAndRecognitionMs=cropAndRecognitionMs;
            this.modelInferenceMs=modelInferenceMs;
            this.recModelLoadMs=recModelLoadMs;
            this.pssAfterKb=pssAfterKb;
            this.lines=lines;
        }
    }

    private final Context context;
    private final OrtEnvironment environment;
    private OrtSession recSession;
    private String inputName;
    private List<String> characters = Collections.emptyList();
    private long loadMs;

    CraftSmallRecLinkage(Context context, OrtEnvironment environment) {
        this.context = context.getApplicationContext();
        this.environment = environment;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try (InputStream input=new FileInputStream(file)) {
            byte[] buffer=new byte[64*1024];
            int count;
            while ((count=input.read(buffer))!=-1)
                digest.update(buffer,0,count);
        }
        StringBuilder hex=new StringBuilder();
        for(byte v:digest.digest())
            hex.append(String.format(Locale.ROOT,"%02x",v&255));
        return hex.toString();
    }

    private File localRecModel() throws Exception {
        File file = new File(context.getFilesDir(),"craft_lab_small_rec.onnx");
        if (!file.isFile() || file.length()!=MODEL_BYTES || !MODEL_SHA.equals(sha256(file))) {
            File tmp = new File(context.getFilesDir(),"craft_lab_small_rec.onnx.tmp");
            try(InputStream input=context.getAssets().open("small_rec.onnx");
                OutputStream output=new FileOutputStream(tmp)) {
                byte[] buffer=new byte[64*1024];
                int count;
                while((count=input.read(buffer))!=-1)
                    output.write(buffer,0,count);
            }
            if(file.exists() && !file.delete())
                throw new IllegalStateException("Cannot replace old Small REC model");
            if(!tmp.renameTo(file))
                throw new IllegalStateException("Cannot install lab Small REC model");
        }
        if(file.length()!=MODEL_BYTES || !MODEL_SHA.equals(sha256(file)))
            throw new SecurityException("Small REC model checksum invalid");
        return file;
    }

    synchronized long load() throws Exception {
        if(recSession!=null) return loadMs;
        // Use JSON, not line-splitting: real character dictionary can include
        // whitespace/punctuation and exact Unicode characters.
        JSONArray dict;
        try(InputStream input=context.getAssets().open("small_rec_chars.json")) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] bytes=new byte[16*1024];
            int n;
            while((n=input.read(bytes))!=-1)out.write(bytes,0,n);
            dict=new JSONArray(out.toString("UTF-8"));
        }
        ArrayList<String> list=new ArrayList<>(dict.length());
        for(int i=0;i<dict.length();++i) list.add(dict.getString(i));
        if(list.size()<1000) throw new IllegalStateException("Small REC character dictionary incomplete");
        File modelFile=localRecModel();
        long start=SystemClock.elapsedRealtimeNanos();
        try(OrtSession.SessionOptions opts=new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(2);
            opts.setInterOpNumThreads(1);
            recSession=environment.createSession(modelFile.getAbsolutePath(),opts);
        }
        if(recSession.getInputNames().size()!=1) {
            close();throw new IllegalStateException("Unexpected Small REC inputs");
        }
        inputName=recSession.getInputNames().iterator().next();
        TensorInfo meta=(TensorInfo)recSession.getInputInfo().get(inputName).getInfo();
        long[] shape=meta.getShape();
        if(shape.length!=4 || shape[1]!=3 || shape[2]!=REC_HEIGHT) {
            close();throw new IllegalStateException("Unexpected Small REC input shape");
        }
        characters=list;
        loadMs=(SystemClock.elapsedRealtimeNanos()-start)/1_000_000L;
        return loadMs;
    }

    private static float[] orderedQuad(float[] coords) {
        float[][] p=new float[4][2];
        for(int i=0;i<4;++i) {
            p[i][0]=coords[i*2];p[i][1]=coords[i*2+1];
        }
        java.util.Arrays.sort(p,(a,b)->Float.compare(a[0],b[0]));
        float[] tl=p[0][1]<=p[1][1]?p[0]:p[1];
        float[] bl=p[0][1]<=p[1][1]?p[1]:p[0];
        float[] tr=p[2][1]<=p[3][1]?p[2]:p[3];
        float[] br=p[2][1]<=p[3][1]?p[3]:p[2];
        return new float[]{
            tl[0],tl[1],tr[0],tr[1],br[0],br[1],bl[0],bl[1]
        };
    }

    private static double length(float x,float y,float xx,float yy) {
        return Math.hypot(x-xx,y-yy);
    }

    private static Bitmap crop(Bitmap photo,CraftBoxDecoder.Box box) {
        float[] q=orderedQuad(box.quad);
        int width=(int)Math.max(length(q[0],q[1],q[2],q[3]),
                                length(q[6],q[7],q[4],q[5]));
        int height=(int)Math.max(length(q[0],q[1],q[6],q[7]),
                                 length(q[2],q[3],q[4],q[5]));
        if(width<4||height<4||width>3200||height>3200)
            return null;
        float[] to = new float[]{0,0,width,0,width,height,0,height};
        Matrix warp=new Matrix();
        if(!warp.setPolyToPoly(q,0,to,0,4)) return null;
        Bitmap rectified=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(rectified);
        canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(photo,warp,paint);
        if(height/(float)Math.max(1,width)>=1.5f) {
            Matrix rotate=new Matrix();
            rotate.postRotate(-90f);
            Bitmap turned=Bitmap.createBitmap(rectified,0,0,width,height,rotate,true);
            if(turned!=rectified)rectified.recycle();
            return turned;
        }
        return rectified;
    }

    private SmallRecCtc.Result recognizeOne(Bitmap crop,long[] modelTime) throws Exception {
        int w=crop.getWidth(),h=crop.getHeight();
        int destW=Math.max(1,Math.min(MAX_WIDTH,
            (int)Math.ceil(REC_HEIGHT*w/(double)Math.max(1,h))));
        Bitmap scaled=Bitmap.createScaledBitmap(crop,destW,REC_HEIGHT,true);
        int plane=REC_HEIGHT*destW;
        int[] pixels=new int[plane];
        try {
            scaled.getPixels(pixels,0,destW,0,0,destW,REC_HEIGHT);
        } finally { if(scaled!=crop) scaled.recycle(); }
        float[] values=new float[3*plane];
        for(int p=0;p<plane;p++) {
            int pix=pixels[p];
            values[p]=((pix>>>16)&255)/127.5f-1f;
            values[plane+p]=((pix>>>8)&255)/127.5f-1f;
            values[2*plane+p]=(pix&255)/127.5f-1f;
        }
        try(OnnxTensor tensor=OnnxTensor.createTensor(environment,
                FloatBuffer.wrap(values),new long[]{1,3,REC_HEIGHT,destW})) {
            long started=SystemClock.elapsedRealtimeNanos();
            try(OrtSession.Result outs=recSession.run(
                    Collections.singletonMap(inputName,tensor))) {
                OnnxTensor result=(OnnxTensor)outs.get(0);
                long[] dims=((TensorInfo)result.getInfo()).getShape();
                SmallRecCtc.Result decoded=SmallRecCtc.decode(
                    result.getFloatBuffer(),dims,characters);
                modelTime[0]+=(SystemClock.elapsedRealtimeNanos()-started)/1_000_000L;
                return decoded;
            }
        }
    }

    synchronized Report recognize(Bitmap bitmap,List<CraftBoxDecoder.Box> boxes) throws Exception {
        if(recSession==null) throw new IllegalStateException("Load Small REC first");
        long started=SystemClock.elapsedRealtimeNanos();
        long[] modelTime={0L};
        List<Line> result=new ArrayList<>();
        int accepted=0,skipped=0;
        for(int i=0;i<boxes.size()&&i<MAX_BOXES;i++) {
            CraftBoxDecoder.Box b=boxes.get(i);
            Bitmap crop=crop(bitmap,b);
            if(crop==null) {skipped++;continue;}
            try {
                long before=SystemClock.elapsedRealtimeNanos();
                SmallRecCtc.Result text=recognizeOne(crop,modelTime);
                long elapsed=(SystemClock.elapsedRealtimeNanos()-before)/1_000_000L;
                accepted++;
                if(!text.text.trim().isEmpty()) {
                    float left=Float.POSITIVE_INFINITY;
                    float top=Float.POSITIVE_INFINITY;
                    float bottom=Float.NEGATIVE_INFINITY;
                    for(int j=0;j<4;j++) {
                        left=Math.min(left,b.quad[j*2]);
                        top=Math.min(top,b.quad[j*2+1]);
                        bottom=Math.max(bottom,b.quad[j*2+1]);
                    }
                    result.add(new Line(i,text.text,text.confidence,left,top,bottom,elapsed));
                }
            } finally {crop.recycle();}
        }
        List<Line> reading=SmallRecCtc.topToBottom(result);
        StringBuilder fullText=new StringBuilder();
        for(Line line:reading) {
            if(fullText.length()>0)fullText.append('\n');
            fullText.append(line.text);
        }
        long time=(SystemClock.elapsedRealtimeNanos()-started)/1_000_000L;
        return new Report(fullText.toString(),boxes.size(),
            accepted,reading.size(),skipped,time,modelTime[0],
            loadMs,Debug.getPss(),reading);
    }

    @Override public synchronized void close() {
        if(recSession!=null) {
            recSession.close();
            recSession=null;
        }
    }
}
