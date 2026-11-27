package com.vobich.bluestar.openocrlab
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import java.lang.ref.WeakReference
import ai.onnxruntime.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
class DbLocator(context:Context,threads:Int):AutoCloseable {
 private val env=OrtEnvironment.getEnvironment()
 private val session:OrtSession
 private data class Probability(val source:WeakReference<Bitmap>,val generation:Int,val longSide:Int,val official:Boolean,val w:Int,val h:Int,val values:FloatArray,val diagnostic:String,val preMs:Long,val inferMs:Long)
 private var cached:Probability?=null
 fun clearProbability(){cached=null}
 var lastDiagnostic:String=""
  private set
 init{
  check(OpenCVLoader.initLocal()){"OpenCV unavailable"}
  val file=java.io.File(context.cacheDir,"repvit-db.onnx")
  if(!file.exists()){val tmp=java.io.File(context.cacheDir,"repvit-db.tmp");context.assets.open("repvit-db.onnx").use{src->tmp.outputStream().use{src.copyTo(it)}};check(tmp.renameTo(file))}
  val opts=OrtSession.SessionOptions()
  try{opts.setIntraOpNumThreads(threads);session=env.createSession(file.absolutePath,opts)}finally{opts.close()}
 }
 fun locate(bm:Bitmap,longSide:Int=960,official:Boolean=true):List<TextLocator.Box>{
  require(longSide in listOf(960,1280,1600))
  lastDiagnostic=""
  val old=cached
  val hit=old!=null&&old.source.get()===bm&&old.generation==bm.generationId&&old.longSide==longSide&&old.official==official
  if(hit&&old!=null){
   lastDiagnostic=old.diagnostic+" map_cache=hit cached_pre_ms="+old.preMs+" cached_infer_ms="+old.inferMs+" preprocess_ms=0 inference_ms=0"
   val t=SystemClock.elapsedRealtime()
   val boxes=boxes(old.values,old.w,old.h,bm.width,bm.height,official)
   lastDiagnostic+=" postprocess_ms="+(SystemClock.elapsedRealtime()-t)
   return boxes
  }
  cached=null
  val started=SystemClock.elapsedRealtime()
  val shape=(session.inputInfo.values.first().info as TensorInfo).shape
  val padded=official&&bm.width+bm.height<64
  val sw=if(padded)max(32,bm.width)else bm.width;val sh=if(padded)max(32,bm.height)else bm.height
  val ratio=min(1f,longSide.toFloat()/max(sw,sh))
  val officialRatio=min(1.0,longSide.toDouble()/max(sw,sh))
  val w=if(shape[3]>0)shape[3].toInt()else if(official)OfficialGeometry.dimension(sw,officialRatio)else max(32,(bm.width*ratio/32).roundToInt()*32)
  val h=if(shape[2]>0)shape[2].toInt()else if(official)OfficialGeometry.dimension(sh,officialRatio)else max(32,(bm.height*ratio/32).roundToInt()*32)
  lastDiagnostic="DB requested_long_side=$longSide input=${w}x${h} model_shape=${shape.contentToString()} dynamic_h=${shape[2]<=0} dynamic_w=${shape[3]<=0}"
  val buf=ByteBuffer.allocateDirect(3*w*h*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  val mean=floatArrayOf(.485f,.456f,.406f);val std=floatArrayOf(.229f,.224f,.225f)
  if(official){
   val rgba=Mat();val bgr=Mat();val pad=Mat();val resized=Mat()
   try{
    Utils.bitmapToMat(bm,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);rgba.release()
    val input=if(padded){Core.copyMakeBorder(bgr,pad,0,sh-bm.height,0,sw-bm.width,Core.BORDER_CONSTANT,Scalar(0.0,0.0,0.0));pad}else bgr
    Imgproc.resize(input,resized,Size(w.toDouble(),h.toDouble()),0.0,0.0,Imgproc.INTER_LINEAR)
    val bytes=ByteArray(w*h*3);resized.get(0,0,bytes)
    for(c in 0..2)for(i in 0 until w*h)buf.put(((bytes[3*i+c].toInt() and 255)*(1f/255f)-mean[c])/std[c])
   }finally{rgba.release();bgr.release();pad.release();resized.release()}
  }else{
   val resized=Bitmap.createScaledBitmap(bm,w,h,true);val pixels=IntArray(w*h)
   try{resized.getPixels(pixels,0,w,0,0,w,h)}finally{if(resized!==bm)resized.recycle()}
   for(c in 0..2)for(v in pixels)buf.put((((v shr (c*8)) and 255)/255f-mean[c])/std[c])
  }
  buf.rewind()
  val preMs=SystemClock.elapsedRealtime()-started
  val inferStart=SystemClock.elapsedRealtime()
  OnnxTensor.createTensor(env,buf,longArrayOf(1,3,h.toLong(),w.toLong())).use{input->
   session.run(mapOf(session.inputNames.single() to input)).use{res->
    val out=res[0] as OnnxTensor;val s=out.info.shape
    require(s.size==4&&s[0]==1L&&s[1]>=1)
    val mh=s[2].toInt();val mw=s[3].toInt();val prob=FloatArray(mh*mw);out.floatBuffer.get(prob)
    lastDiagnostic+=" prob_map=${mw}x${mh}"
    val inferMs=SystemClock.elapsedRealtime()-inferStart
    cached=Probability(WeakReference(bm),bm.generationId,longSide,official,mw,mh,prob,lastDiagnostic,preMs,inferMs)
    lastDiagnostic+=" map_cache=miss preprocess_ms=$preMs inference_ms=$inferMs"
    val t=SystemClock.elapsedRealtime()
    val result=boxes(prob,mw,mh,bm.width,bm.height,official)
    lastDiagnostic+=" postprocess_ms="+(SystemClock.elapsedRealtime()-t)
    return result
   }
  }
 }
 private fun boxes(prob:FloatArray,w:Int,h:Int,ow:Int,oh:Int,official:Boolean):List<TextLocator.Box>{
  val mask=Mat(h,w,CvType.CV_8UC1);val map=Mat(h,w,CvType.CV_32FC1);val hierarchy=Mat()
  val contours=mutableListOf<MatOfPoint>();val result=mutableListOf<TextLocator.Box>()
  var shortRejected=0;var invalidRejected=0;var scoreRejected=0;var expandedRejected=0
  val shortHistogram=IntArray(6);val acceptedHistogram=IntArray(6)
  try{
   mask.put(0,0,ByteArray(prob.size){if(prob[it]>.3f)255.toByte()else 0})
   map.put(0,0,prob)
   Imgproc.findContours(mask,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE)
   for(c in contours.take(1000)){
    val pts=MatOfPoint2f(*c.toArray())
    try{
     val rr=Imgproc.minAreaRect(pts);val rw=rr.size.width;val rh=rr.size.height
     val short=min(rw,rh);val bucket=short.toInt().coerceIn(0,5);shortHistogram[bucket]++
     if(short<3){shortRejected++;continue}
     val roi=Imgproc.boundingRect(c);if(roi.width<1||roi.height<1){invalidRejected++;continue}
     val local=MatOfPoint(*c.toArray().map{Point(it.x-roi.x,it.y-roi.y)}.toTypedArray())
     val m=Mat.zeros(roi.height,roi.width,CvType.CV_8UC1);val sub=map.submat(roi)
     val score=try{Imgproc.fillPoly(m,listOf(local),Scalar(1.0));Core.mean(sub,m).`val`[0].toFloat()}finally{local.release();m.release();sub.release()}
     if(score<.6f){scoreRejected++;continue}
     // For a rectangular contour, the min-area rectangle of a rounded
     // outward offset has the same orientation and adds 2d to both sides.
     // Continuous rectangle approximation, not byte-identical pyclipper.
     val ex=if(official){
      val corners=Array(4){Point()};rr.points(corners)
      val sorted=order(corners)
      val paths=OfficialGeometry.unclip(DoubleArray(8){i->if(i%2==0)sorted[i/2].x else sorted[i/2].y})
      if(paths.size!=1||paths[0].size<6){expandedRejected++;continue}
      val path=paths[0];val expanded=MatOfPoint2f(*Array(path.size/2){i->Point(path[2*i],path[2*i+1])})
      try{Imgproc.minAreaRect(expanded)}finally{expanded.release()}
     }else{
      val d=rw*rh*1.5/(2*(rw+rh));RotatedRect(rr.center,Size(rw+2*d,rh+2*d),rr.angle)
     }
     if(min(ex.size.width,ex.size.height)<5){expandedRejected++;continue}
     val q=Array(4){Point()};ex.points(q)
     val ordered=if(official)order(q)else {
      val left=q.sortedBy{it.x}.take(2).sortedBy{it.y};val right=q.sortedBy{it.x}.takeLast(2).sortedBy{it.y}
      listOf(left[0],right[0],right[1],left[1])
     }
     acceptedHistogram[bucket]++
     result.add(TextLocator.Box(FloatArray(8){i->if(official){if(i%2==0)OfficialGeometry.map(ordered[i/2].x,ow,w)else OfficialGeometry.map(ordered[i/2].y,oh,h)}else if(i%2==0)(ordered[i/2].x*ow/w).toFloat().coerceIn(0f,ow.toFloat())else(ordered[i/2].y*oh/h).toFloat().coerceIn(0f,oh.toFloat())},score))
    }finally{pts.release()}
   }
  }finally{contours.forEach{it.release()};mask.release();map.release();hierarchy.release()}
  lastDiagnostic+=" contours=${contours.size} examined=${min(contours.size,1000)} cap_skipped=${max(0,contours.size-1000)} rejected_short=$shortRejected rejected_invalid=$invalidRejected rejected_score=$scoreRejected rejected_expanded=$expandedRejected accepted=${result.size} mask_threshold=0.3 box_threshold=0.6 profile=${if(official)"official_port" else "verified_region"} min_short=3 min_expanded=5 unclip=1.5 short_hist_0_1_2_3_4_5plus=${shortHistogram.contentToString()} accepted_hist=${acceptedHistogram.contentToString()}"
  return result
 }
 private fun order(q:Array<Point>):List<Point>{
  val p=q.sortedBy{it.x};val l=if(p[1].y>p[0].y)listOf(p[0],p[1])else listOf(p[1],p[0])
  val r=if(p[3].y>p[2].y)listOf(p[2],p[3])else listOf(p[3],p[2])
  return listOf(l[0],r[0],r[1],l[1])
 }
 override fun close(){clearProbability();session.close()}
}
