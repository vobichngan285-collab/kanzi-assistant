package com.vobich.bluestar.openocrlab
import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
class DbLocator(context:Context,threads:Int):AutoCloseable {
 private val env=OrtEnvironment.getEnvironment()
 private val session:OrtSession
 init{
  check(OpenCVLoader.initLocal()){"OpenCV unavailable"}
  val file=java.io.File(context.cacheDir,"repvit-db.onnx")
  if(!file.exists()){val tmp=java.io.File(context.cacheDir,"repvit-db.tmp");context.assets.open("repvit-db.onnx").use{src->tmp.outputStream().use{src.copyTo(it)}};check(tmp.renameTo(file))}
  val opts=OrtSession.SessionOptions()
  try{opts.setIntraOpNumThreads(threads);session=env.createSession(file.absolutePath,opts)}finally{opts.close()}
 }
 fun locate(bm:Bitmap):List<TextLocator.Box>{
  val shape=(session.inputInfo.values.first().info as TensorInfo).shape
  val ratio=min(1f,960f/max(bm.width,bm.height))
  val w=if(shape[3]>0)shape[3].toInt()else max(32,(bm.width*ratio/32).roundToInt()*32)
  val h=if(shape[2]>0)shape[2].toInt()else max(32,(bm.height*ratio/32).roundToInt()*32)
  val resized=Bitmap.createScaledBitmap(bm,w,h,true);val pixels=IntArray(w*h)
  try{resized.getPixels(pixels,0,w,0,0,w,h)}finally{if(resized!==bm)resized.recycle()}
  val buf=ByteBuffer.allocateDirect(3*w*h*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  val mean=floatArrayOf(.485f,.456f,.406f);val std=floatArrayOf(.229f,.224f,.225f)
  for(c in 0..2)for(v in pixels)buf.put((((v shr (c*8)) and 255)/255f-mean[c])/std[c])
  buf.rewind()
  OnnxTensor.createTensor(env,buf,longArrayOf(1,3,h.toLong(),w.toLong())).use{input->
   session.run(mapOf(session.inputNames.single() to input)).use{res->
    val out=res[0] as OnnxTensor;val s=out.info.shape
    require(s.size==4&&s[0]==1L&&s[1]>=1)
    val mh=s[2].toInt();val mw=s[3].toInt();val prob=FloatArray(mh*mw);out.floatBuffer.get(prob)
    return boxes(prob,mw,mh,bm.width,bm.height)
   }
  }
 }
 private fun boxes(prob:FloatArray,w:Int,h:Int,ow:Int,oh:Int):List<TextLocator.Box>{
  val mask=Mat(h,w,CvType.CV_8UC1);val map=Mat(h,w,CvType.CV_32FC1);val hierarchy=Mat()
  val contours=mutableListOf<MatOfPoint>();val result=mutableListOf<TextLocator.Box>()
  try{
   mask.put(0,0,ByteArray(prob.size){if(prob[it]>.3f)255.toByte()else 0})
   map.put(0,0,prob)
   Imgproc.findContours(mask,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE)
   for(c in contours.take(1000)){
    val pts=MatOfPoint2f(*c.toArray())
    try{
     val rr=Imgproc.minAreaRect(pts);val rw=rr.size.width;val rh=rr.size.height
     if(min(rw,rh)<3)continue
     val roi=Imgproc.boundingRect(c);if(roi.width<1||roi.height<1)continue
     val local=MatOfPoint(*c.toArray().map{Point(it.x-roi.x,it.y-roi.y)}.toTypedArray())
     val m=Mat.zeros(roi.height,roi.width,CvType.CV_8UC1);val sub=map.submat(roi)
     val score=try{Imgproc.fillPoly(m,listOf(local),Scalar(1.0));Core.mean(sub,m).`val`[0].toFloat()}finally{local.release();m.release();sub.release()}
     if(score<.6f)continue
     // For a rectangular contour, the min-area rectangle of a rounded
     // outward offset has the same orientation and adds 2d to both sides.
     // Continuous rectangle approximation, not byte-identical pyclipper.
     val d=rw*rh*1.5/(2*(rw+rh))
     val ex=RotatedRect(rr.center,Size(rw+2*d,rh+2*d),rr.angle)
     if(min(ex.size.width,ex.size.height)<5)continue
     val q=Array(4){Point()};ex.points(q)
     val left=q.sortedBy{it.x}.take(2).sortedBy{it.y}
     val right=q.sortedBy{it.x}.takeLast(2).sortedBy{it.y}
     val ordered=listOf(left[0],right[0],right[1],left[1])
     result.add(TextLocator.Box(FloatArray(8){i->if(i%2==0)(ordered[i/2].x*ow/w).toFloat().coerceIn(0f,ow.toFloat())else(ordered[i/2].y*oh/h).toFloat().coerceIn(0f,oh.toFloat())},score))
    }finally{pts.release()}
   }
  }finally{contours.forEach{it.release()};mask.release();map.release();hierarchy.release()}
  return result
 }
 override fun close(){session.close()}
}
