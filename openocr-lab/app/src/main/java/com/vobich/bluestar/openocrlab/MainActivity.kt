package com.vobich.bluestar.openocrlab

import android.app.Activity
import android.content.*
import android.graphics.*
import android.os.*
import android.widget.*
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors

/** Independent recognizer A/B. No routing, filtering, corrections, TTS or network. */
class MainActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private lateinit var status:TextView
 private lateinit var output:TextView
 private lateinit var preview:ImageView
 private lateinit var choose:Button
 private lateinit var run:Button
 private lateinit var threads:Spinner
 private lateinit var order:Spinner
 private lateinit var backend:Spinner
 private var source:Bitmap?=null
 private fun now()=SystemClock.elapsedRealtime()
 private fun busy(v:Boolean){choose.isEnabled=!v;run.isEnabled=!v;threads.isEnabled=!v;order.isEnabled=!v;backend.isEnabled=!v}
 private fun note(s:String){runOnUiThread{if(!isDestroyed)output.append(s+"\n")}}
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  val scroll=ScrollView(this)
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,48,24,32)}
  scroll.addView(layout);setContentView(scroll)
  fun label(s:String)=TextView(this).apply{text=s;textSize=16f;layout.addView(this)}
  label("蓝星 OCR GPU 竞技场 v0.1.0")
  label("固定DB1600 · 同一输入张量重放 · ORT CPU / MNN CPU / 单个GPU后端 · 实验版，非正式替换")
  choose=Button(this).apply{text="选择相册原图";layout.addView(this);setOnClickListener{
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)
  }}
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;layout.addView(this)}
  threads=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("CPU 4线程","CPU 6线程","CPU 2线程"));layout.addView(this)}
  order=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("OpenOCRv2 CTC","RepSVTR"));layout.addView(this)}
  backend=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("OpenCL","Vulkan","仅CPU对照"));layout.addView(this)}
  run=Button(this).apply{text="运行后端对照";layout.addView(this);setOnClickListener{test()}}
  status=label("请选择原图")
  Button(this).apply{text="复制完整结果";layout.addView(this);setOnClickListener{
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OCR Arena",output.text))
   Toast.makeText(this@MainActivity,"已复制",Toast.LENGTH_SHORT).show()
  }}
  output=label("");output.setTextIsSelectable(true)
 }
 override fun onActivityResult(req:Int,res:Int,data:Intent?){
  super.onActivityResult(req,res,data)
  if(req!=9||res!=RESULT_OK)return
  val uri=data?.data?:return
  busy(true)
  worker.execute{
   try{
    val bm=ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,uri)){d,i,_->
     require(i.size.width.toLong()*i.size.height<=16_000_000L){"图片超过1600万像素"}
     d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
    }
    runOnUiThread{
     if(isDestroyed){bm.recycle()}else{
      preview.setImageBitmap(bm);source?.recycle();source=bm;output.text=""
      status.text="原图 "+bm.width+"×"+bm.height;busy(false)
     }
    }
   }catch(t:Throwable){runOnUiThread{if(!isDestroyed){status.text=t.toString();busy(false)}}}
  }
 }
 override fun onDestroy(){worker.shutdown();super.onDestroy()}
 private fun test(){
  val bm=source?:return
  val nt=listOf(4,6,2)[threads.selectedItemPosition]
  val model=if(order.selectedItemPosition==0)"v2" else "rep"
  val target=listOf(3,7,0)[backend.selectedItemPosition]
  busy(true);output.text="";status.text="固定检测框计算中"
  worker.execute{
   try{
    note("GPU Arena v0.1.0 model=$model CPU=$nt target=$target source=${bm.width}x${bm.height} DB=1600 device=${Build.MANUFACTURER}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT} pss_start_kib=${Debug.getPss()}")
    val boxes=DbLocator(this,nt).use{db->
     val t=now();val result=OfficialGeometry.order(db.locate(bm,1600,true))
     note(db.lastDiagnostic+" detector_ms=${now()-t}");result
    }
    require(boxes.isNotEmpty()){"未检测到文字"}
    note("fixed_boxes=${boxes.size}")
    val rgba=Mat();val bgr=Mat()
    try{
     Utils.bitmapToMat(bm,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);rgba.release()
     val replay=GpuReplay(this,model,nt,::note)
     replay.use{
      val captureStart=now()
      val timing=Reader.Timing()
      if(model=="rep"){
       Reader(this,"rep.onnx",nt).use{engine->engine.readOfficial(bgr,boxes,timing,replay::capture)}
      }else{
       V2Reader(this,nt).use{engine->engine.read(bgr,boxes,timing,{},replay::capture)}
      }
      note("capture_wall_ms=${now()-captureStart} prep_ms=${timing.prepNs/1e6} (includes separate preparation session; not replay timing)")
      replay.checkCoverage(boxes.size)
      runOnUiThread{if(!isDestroyed)status.text="运行同输入后端对照"}
      replay.run(target)
     }
     runOnUiThread{if(!isDestroyed)status.text="对照结束，检查日志中的失败和GPU执行证据"}
    }finally{rgba.release();bgr.release()}
   }catch(t:Throwable){note("FAILED ${t.stackTraceToString()}");runOnUiThread{if(!isDestroyed)status.text="运行失败"}}
   finally{runOnUiThread{if(!isDestroyed)busy(false)}}
  }
 }
}
