package com.vobich.bluestar.openocrlab
import android.app.Activity
import android.content.*
import android.graphics.*
import android.os.*
import android.widget.*
import android.view.View
import kotlin.math.*
import java.util.concurrent.Executors

class MainActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private lateinit var status:TextView;private lateinit var output:TextView;private lateinit var preview:ImageView
 private lateinit var choose:Button;private lateinit var run:Button;private lateinit var mode:Spinner
 private var source:Bitmap?=null;private var locator:TextLocator?=null
 private fun now()=SystemClock.elapsedRealtime()
 override fun onCreate(state:Bundle?){super.onCreate(state)
  val scroll=ScrollView(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,48,24,32)}
  scroll.addView(box);setContentView(scroll)
  fun label(t:String)=TextView(this).apply{text=t;textSize=18f;box.addView(this)}
  label("蓝星 OpenOCR v0.1.0 · 完全离线")
  choose=Button(this).apply{text="选择相册原图";box.addView(this);setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)}}
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;box.addView(this)}
  mode=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("双模型对照（默认）","仅 RepSVTR","仅 SVTRv2"));box.addView(this)}
  run=Button(this).apply{text="开始识别 / 同图复测";box.addView(this);setOnClickListener{test()}}
  status=label("请选择图片")
  Button(this).apply{text="复制结果及诊断";box.addView(this);setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OpenOCR",output.text));Toast.makeText(this@MainActivity,"已复制",Toast.LENGTH_SHORT).show()}}
  output=label("");output.setTextIsSelectable(true)
 }
 override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req!=9||res!=RESULT_OK)return;val uri=data?.data?:return
  busy(true);worker.execute{
   try{val bm=ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,uri)){d,i,_->
    require(i.size.width.toLong()*i.size.height<=16_000_000L){"图片超过1600万像素"};d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE}
    runOnUiThread{preview.setImageBitmap(bm);source?.recycle();source=bm;status.text="原图 "+bm.width+"×"+bm.height;busy(false)}
   }catch(t:Throwable){runOnUiThread{status.text=t.toString();busy(false)}}
  }
 }
 private fun busy(v:Boolean){choose.isEnabled=!v;run.isEnabled=!v;mode.isEnabled=!v}
 private fun test(){val bm=source?:return;val selected=mode.selectedItemPosition;busy(true);output.text="";status.text="YOLO 定位中"
  worker.execute{
   try{
    val start=now();if(locator==null)locator=TextLocator(this);val init=now()
    val found=locator!!.locate(bm);val detected=now()
    val roi=found.roi
    val boxes=found.boxes.filter{b->roi==null||roi.contains(b.rect.centerX().toInt(),b.rect.centerY().toInt())}
    require(boxes.isNotEmpty()){"未检测到文字"}
    // Same row grouping and crop geometry for both recognizers; never merge distant columns vertically.
    val rows=mutableListOf<MutableList<TextLocator.Box>>()
    for(b in boxes.sortedBy{it.rect.centerY()}){
     val r=b.rect
     val row=rows.lastOrNull()?.takeIf{rr->
      val c=rr.map{it.rect.centerY()}.average()
      abs(r.centerY()-c)<=max(4.0,min(r.height().toDouble(),rr.map{it.rect.height()}.average())*.55)
     }
     if(row==null)rows.add(mutableListOf(b))else row.add(b)
    }
    val ordered=rows.map{it.sortedBy{b->b.rect.left}}
    val scale=min(1f,1200f/max(bm.width,bm.height));val shown=Bitmap.createScaledBitmap(bm,(bm.width*scale).toInt(),(bm.height*scale).toInt(),true).copy(Bitmap.Config.ARGB_8888,true)
    Canvas(shown).apply{scale(scale,scale);val paint=Paint().apply{color=Color.YELLOW;style=Paint.Style.STROKE;strokeWidth=3/scale};boxes.forEach{drawRect(it.rect,paint)};if(roi!=null){paint.color=Color.GREEN;drawRect(roi,paint)}}
    runOnUiThread{preview.setImageBitmap(shown);output.text="source=${bm.width}x${bm.height} detector_init_ms=${init-start} detect_ms=${detected-init} boxes=${found.boxes.size} selected=${boxes.size} rows=${rows.size} roi=$roi\n"}
    val models=when(selected){1->listOf("RepSVTR" to "rep.onnx");2->listOf("SVTRv2" to "svtr.onnx");else->listOf("RepSVTR" to "rep.onnx","SVTRv2" to "svtr.onnx")}
    for((name,asset) in models){
     runOnUiThread{status.text="$name 识别中"}
     val t=now();Reader(this,asset).use{reader->
      val loaded=now()
      // Two consecutive runs, same detector boxes and same reader session.
      repeat(2){round->
       val begin=now();var first=-1L;var count=0;var sum=0f;val lines=mutableListOf<String>()
       for(row in ordered){val parts=mutableListOf<String>()
        for(b in row){val a=reader.read(bm,b);count++;sum+=a.confidence;parts.add(a.text.ifBlank{"[未识别]"});}
        lines.add(parts.joinToString(" "));if(first<0)first=now()-begin
       }
       val total=now()-begin
       runOnUiThread{output.append("\n$name 第${round+1}轮 load_ms=${if(round==0)loaded-t else 0} first_row_ms=$first rec_ms=$total det_plus_rec_ms=${detected-init+total} boxes=$count mean_conf=${sum/count}\n"+lines.joinToString("\n")+"\n")}
      }
     }
    }
    runOnUiThread{status.text="完成：请复制诊断和识别结果";busy(false)}
   }catch(t:Throwable){runOnUiThread{output.append("\n失败："+t.toString());status.text="失败";busy(false)}}
  }
 }
}
