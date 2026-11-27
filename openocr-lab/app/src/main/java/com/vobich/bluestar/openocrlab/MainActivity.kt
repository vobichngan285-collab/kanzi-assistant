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
 private var source:Bitmap?=null
 private fun now()=SystemClock.elapsedRealtime()
 private fun busy(v:Boolean){choose.isEnabled=!v;run.isEnabled=!v;threads.isEnabled=!v;order.isEnabled=!v}
 private fun note(s:String){runOnUiThread{if(!isDestroyed)output.append(s+"\n")}}
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  val scroll=ScrollView(this)
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,48,24,32)}
  scroll.addView(layout);setContentView(scroll)
  fun label(s:String)=TextView(this).apply{text=s;textSize=16f;layout.addView(this)}
  label("蓝星 OCR 识别竞技场 v0.2.0")
  label("固定 DB 1600 · 同一四边形 · 每模型两轮 · 原始识别结果")
  choose=Button(this).apply{text="选择相册原图";layout.addView(this);setOnClickListener{
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)
  }}
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;layout.addView(this)}
  threads=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("CPU 4线程","CPU 6线程","CPU 2线程"));layout.addView(this)}
  order=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("RepSVTR → OpenOCRv2 CTC","OpenOCRv2 CTC → RepSVTR"));layout.addView(this)}
  run=Button(this).apply{text="运行双模型对照";layout.addView(this);setOnClickListener{test()}}
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
  val models=listOf("RepSVTR" to "rep.onnx","OpenOCRv2 CTC" to "openocrv2_ctc.onnx").let{if(order.selectedItemPosition==0)it else it.reversed()}
  busy(true);output.text="";status.text="固定检测框计算中"
  worker.execute{
   val started=now()
   try{
    note("Rec Arena v0.2.0 source=${bm.width}x${bm.height} CPU=$nt DB=1600 order=${models.map{it.first}} pss_start_kib=${Debug.getPss()}")
    val boxes=DbLocator(this,nt).use{db->
     val init=now()-started;val t=now()
     val result=OfficialGeometry.order(db.locate(bm,1600,true))
     note(db.lastDiagnostic+" detector_init_ms=$init detector_ms=${now()-t}")
     result
    }
    require(boxes.isNotEmpty()){"未检测到文字"}
    note("fixed_boxes=${boxes.size} pss_after_db_close_kib=${Debug.getPss()}")
    note(boxes.mapIndexed{i,b->"id=${i+1} det=${b.score} quad=${b.points.contentToString()}"}.joinToString("\n"))
    val rgba=Mat();val bgr=Mat()
    try{
     val convertStart=now()
     Utils.bitmapToMat(bm,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);rgba.release()
     note("shared_bgr_ms=${now()-convertStart}")
     val answers=linkedMapOf<String,List<Reader.Answer>>()
     for((name,asset) in models){
      runOnUiThread{if(!isDestroyed)status.text="正在测试 $name"}
      try{
       val loadStart=now()
       fun runPasses(shape:String,batch:Int,recognize:(Reader.Timing,()->Unit)->List<Reader.Answer>){
        note("$name load_ms=${now()-loadStart} shape=$shape batch=$batch pss_loaded_kib=${Debug.getPss()}")
        for(pass in 1..2){
         val timing=Reader.Timing()
         val begin=now();var first=-1L
         val result=recognize(timing){if(first<0)first=now()-begin}
         check(result.size==boxes.size)
         val elapsed=now()-begin
         note("$name pass=$pass rec_ms=$elapsed first_batch_ms=$first prep_ms=${timing.prepNs/1_000_000.0} tensor_ms=${timing.tensorNs/1_000_000.0} infer_ms=${timing.inferNs/1_000_000.0} decode_ms=${timing.decodeNs/1_000_000.0} blank=${result.count{it.text.isBlank()}} below_0.5=${result.count{it.confidence<.5f}} pss_kib=${Debug.getPss()}")
         note(result.mapIndexed{i,a->"$name pass=$pass id=${i+1} width=${a.width} rec=${a.confidence} min=${a.minConfidence} text=${a.text}"}.joinToString("\n"))
         if(pass==2)answers[name]=result
        }
       }
       if(asset=="rep.onnx"){
        Reader(this,asset,nt).use{engine->
         runPasses(engine.inputShape,engine.officialBatchSize){timing,onBatch->
          val result=ArrayList<Reader.Answer>(boxes.size)
          for(chunk in boxes.chunked(engine.officialBatchSize)){
           result.addAll(engine.readOfficial(bgr,chunk,timing));onBatch()
          }
          result
         }
        }
       }else{
        V2Reader(this,nt).use{engine->
         note("OpenOCRv2 CTC shape buckets, full-page preprocessing before first inference; batch cap=6")
         runPasses(engine.inputShape,6){timing,onBatch->engine.read(bgr,boxes,timing,onBatch)}
         note("OpenOCRv2 CTC input_shapes="+engine.lastShapes.joinToString(","))
        }
       }
      }catch(t:Exception){note("$name FAILED ${t.stackTraceToString()}")}
      note("$name session_closed pss_kib=${Debug.getPss()}")
     }
     val a=answers["RepSVTR"];val b=answers["OpenOCRv2 CTC"]
     if(a!=null&&b!=null){
      note("DIFF warm_pass; 不同输出不代表谁正确")
      var diff=0
      for(i in boxes.indices)if(a[i].text!=b[i].text){
       diff++;note("id=${i+1} RepSVTR=${a[i].text} | OpenOCRv2 CTC=${b[i].text}")
      }
      note("different=$diff/${boxes.size}")
     }else note("对照不完整：至少一个模型未完成")
     note("arena_wall_ms=${now()-started} pss_end_kib=${Debug.getPss()} (PSS snapshots, not peak)")
     runOnUiThread{if(!isDestroyed)status.text=if(a!=null&&b!=null)"双模型对照完成" else "对照未完整完成"}
    }finally{rgba.release();bgr.release()}
   }catch(t:Throwable){note("FAILED ${t.stackTraceToString()}");runOnUiThread{if(!isDestroyed)status.text="运行失败"}}
   finally{runOnUiThread{if(!isDestroyed)busy(false)}}
  }
 }
}
