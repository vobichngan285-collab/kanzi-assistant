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
 private var source:Bitmap?=null;private var locator:TextLocator?=null;private var reader:Reader?=null;private var readerThreads=0
 private lateinit var threads:Spinner
 private fun now()=SystemClock.elapsedRealtime()
 override fun onCreate(state:Bundle?){super.onCreate(state)
  val scroll=ScrollView(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,48,24,32)}
  scroll.addView(box);setContentView(scroll)
  fun label(t:String)=TextView(this).apply{text=t;textSize=18f;box.addView(this)}
  label("蓝星 OpenOCR v0.1.2 · 完全离线")
  choose=Button(this).apply{text="选择相册原图";box.addView(this);setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)}}
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;box.addView(this)}
  mode=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("精细识别对照（默认）","仅精细识别","仅v0.1.1基线","CPU 2/4/6线程对照"));box.addView(this)}
  threads=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("CPU 4线程（默认）","CPU 2线程","CPU 6线程"));box.addView(this)}
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
 private fun busy(v:Boolean){choose.isEnabled=!v;run.isEnabled=!v;mode.isEnabled=!v;threads.isEnabled=!v}
 private fun test(){val bm=source?:return;val selected=mode.selectedItemPosition;val chosenThreads=listOf(4,2,6)[threads.selectedItemPosition];busy(true);output.text="";status.text="YOLO 定位中"
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
    val planStart=now();val optimized=RowPlanner.plan(boxes,bm.width,bm.height);val planMs=now()-planStart
    // Narrower merge gap is experimental: preserve separated columns, keep old plan for comparison.
    val fineStart=now();val fine=RowPlanner.plan(boxes,bm.width,bm.height,1.6f);val fineMs=now()-fineStart
    val scale=min(1f,1200f/max(bm.width,bm.height));val shown=Bitmap.createScaledBitmap(bm,(bm.width*scale).toInt(),(bm.height*scale).toInt(),true).copy(Bitmap.Config.ARGB_8888,true)
    Canvas(shown).apply{scale(scale,scale);val paint=Paint().apply{color=Color.YELLOW;style=Paint.Style.STROKE;strokeWidth=3/scale};boxes.forEach{drawRect(it.rect,paint)};if(roi!=null){paint.color=Color.GREEN;drawRect(roi,paint)};paint.color=Color.CYAN;optimized.rows.flatten().forEach{b->val path=Path();path.moveTo(b.points[0],b.points[1]);for(i in 1..3)path.lineTo(b.points[2*i],b.points[2*i+1]);path.close();drawPath(path,paint)}}
    runOnUiThread{preview.setImageBitmap(shown);output.text="source=${bm.width}x${bm.height} detector_init_ms=${init-start} detect_ms=${detected-init} boxes=${found.boxes.size} selected=${boxes.size} rows=${rows.size} roi=$roi\n"}
    runOnUiThread{output.append("new_rows=${optimized.rows.size} new_crops=${optimized.rows.sumOf{it.size}} duplicates=${optimized.removed} plan_ms=$planMs\n")}
    runOnUiThread{output.append("fine_rows=${fine.rows.size} fine_crops=${fine.rows.sumOf{it.size}} fine_plan_ms=$fineMs\n")}
    val cases=when(selected){1->listOf(true,true);2->listOf(false,false);3->listOf(true,true);else->listOf(false,true,true,false)}
    val threadCases=if(selected==3)listOf(2,4,6)else listOf(chosenThreads)
    for(nt in threadCases){
     val t=now()
     if(reader==null||readerThreads!=nt){reader?.close();reader=null;reader=Reader(this,"rep.onnx",nt);readerThreads=nt}
     val engine=reader!!;val loaded=now()
     runOnUiThread{output.append("RepSVTR threads=$nt load_ms=${loaded-t}\n")}
     for((round,isFine) in cases.withIndex()){
      val name=if(isFine)"精细识别" else "v0.1.1基线"
      val linesIn=if(isFine)fine.rows else optimized.rows
      runOnUiThread{status.text="$name CPU$nt 识别中"}
      val begin=now();var first=-1L;var count=0;var sum=0f;var retries=0;var recovered=0;var uncertain=0
      val lines=mutableListOf<String>();val notes=mutableListOf<String>()
      for((ri,row) in linesIn.withIndex()){
       val parts=mutableListOf<String>()
       for((ci,b) in row.withIndex()){
        var ans=engine.read(bm,b,isFine);val original=ans
        if(isFine&&retries<6&&FineRead.retryNeeded(ans)){
         // Expand modestly in local coordinates, do not cross adjacent crop bounding boxes.
         val expanded=FineRead.expand(b,1.18f,1.08f,bm.width,bm.height)
         val er=expanded.rect
         val overlaps=linesIn.flatten().any{other->other!==b&&android.graphics.RectF.intersects(er,other.rect)&&!android.graphics.RectF.intersects(b.rect,other.rect)}
         if(!overlaps){val alt=engine.read(bm,expanded,true);retries++
          if(FineRead.accept(ans,alt)){ans=alt;recovered++}
          notes.add("retry row=${ri+1} crop=${ci+1} ${original.text}(${original.confidence}) -> ${alt.text}(${alt.confidence}) accepted=${ans===alt}")
         }
        }
        count++;sum+=ans.confidence
        val suspect=isFine&&FineRead.suspicious(ans,b.score)
        if(suspect){uncertain++;notes.add("疑似装饰 row=${ri+1} crop=${ci+1} text=${ans.text} det=${b.score} rec=${ans.confidence}")}
        // Flag suspicious text rather than delete potentially valid short labels.
        parts.add(if(suspect)"[待核:${ans.text}]" else ans.text.ifBlank{"[未识别]"})
       }
       lines.add(parts.joinToString(if(isFine)"  |  " else " "));if(first<0)first=now()-begin
      }
      val total=now()-begin
      val layoutMs=if(isFine)fineMs else planMs
      runOnUiThread{output.append("\n$name 第${round+1}轮 threads=$nt first_row_ms=$first rec_ms=$total det_plus_rec_ms=${detected-init+layoutMs+total} crops=$count mean_conf=${sum/count} retries=$retries accepted=$recovered suspicious=$uncertain\n"+lines.joinToString("\n")+"\n"+notes.joinToString("\n")+"\n")}
     }
    }
    runOnUiThread{status.text="完成：请复制诊断和识别结果";busy(false)}
   }catch(t:Throwable){runOnUiThread{output.append("\n失败："+t.toString());status.text="失败";busy(false)}}
  }
 }
}
