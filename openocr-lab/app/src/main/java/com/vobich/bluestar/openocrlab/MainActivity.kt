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
  label("蓝星 OpenOCR v0.4.0 · 主阅读区 · 离线")
  choose=Button(this).apply{text="选择相册原图";box.addView(this);setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)}}
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;box.addView(this)}
  mode=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("智能主阅读区（默认）","原v0.1.3区域基线","全图完整列裁剪","同图区域对照（新/旧/全图）"));box.addView(this)}
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
 private fun test(){
  val bm=source?:return;val selected=mode.selectedItemPosition;val nt=listOf(4,2,6)[threads.selectedItemPosition]
  busy(true);output.text="";status.text="YOLO 定位中"
  worker.execute{
   try{
    val start=now();if(locator==null)locator=TextLocator(this);val init=now()
    val found=locator!!.locate(bm);val detected=now()
    require(found.boxes.isNotEmpty()){"未检测到文字"}
    val focusStart=now()
    val plan=FocusPlanner.plan(found.boxes.map{q->q.rect.let{FocusPlanner.R(it.left,it.top,it.right,it.bottom,q.score)}},bm.width,bm.height)
    val focusMs=now()-focusStart
    val focus=plan.roi?.let{Rect(floor(it.l).toInt(),floor(it.t).toInt(),ceil(it.r).toInt(),ceil(it.b).toInt())}
    val modes=if(selected==3)listOf(0,1,2)else listOf(selected)
    val load=now();if(reader==null||readerThreads!=nt){reader?.close();reader=null;reader=Reader(this,"rep.onnx",nt);readerThreads=nt}
    val engine=reader!!;val loadMs=now()-load
    runOnUiThread{output.append("Focus v0.4.0 source=${bm.width}x${bm.height} detector_threads=4 rec_threads=$nt detector_init_ms=${init-start} detect_ms=${detected-init} recognizer_load_ms=$loadMs raw_boxes=${found.boxes.size} focus_plan_ms=$focusMs focus_reason=${plan.reason} groups=${plan.groups} best=${plan.best} rival=${plan.rival}\n")}
    for(m in modes){
     val name=listOf("智能主阅读区","原v0.1.3区域","全图")[m]
     val roi=when(m){0->focus;1->found.roi;else->null}
     val boxes=found.boxes.filter{b->roi==null||roi.contains(b.rect.centerX().toInt(),b.rect.centerY().toInt())}
     val outside=found.boxes.filter{it !in boxes}
     val pStart=now();val columns=RowPlanner.plan(boxes,bm.width,bm.height,1.6f,true);val pMs=now()-pStart
     val scale=min(1f,1200f/max(bm.width,bm.height))
     val scaled=Bitmap.createScaledBitmap(bm,(bm.width*scale).toInt(),(bm.height*scale).toInt(),true)
     val shown=scaled.copy(Bitmap.Config.ARGB_8888,true)
     if(scaled!==bm)scaled.recycle()
     Canvas(shown).apply{
      scale(scale,scale);val paint=Paint().apply{style=Paint.Style.STROKE;strokeWidth=3/scale}
      paint.color=Color.GRAY;outside.forEach{drawRect(it.rect,paint)}
      paint.color=Color.YELLOW;boxes.forEach{drawRect(it.rect,paint)}
      if(roi!=null){paint.color=Color.GREEN;drawRect(roi,paint)}
      paint.color=Color.CYAN;columns.rows.flatten().forEach{b->val path=Path();path.moveTo(b.points[0],b.points[1]);for(i in 1..3)path.lineTo(b.points[2*i],b.points[2*i+1]);path.close();drawPath(path,paint)}
     }
     runOnUiThread{preview.setImageBitmap(shown);output.append("\nmode=$m $name roi=$roi selected=${boxes.size} outside=${outside.size} rows=${columns.rows.size} crops=${columns.rows.sumOf{it.size}} duplicates=${columns.removed} plan_ms=$pMs\n"+(if(outside.isNotEmpty())"提示：区域外${outside.size}个检测框未识别，可切换全图模式；灰框为未读区域。\n" else "")+outside.joinToString("\n"){q->"outside det=${q.score} quad=${q.points.contentToString()}"}+"\n")}
     for(round in 1..2){
      val begin=now();var first=-1L;var count=0;var uncertain=0;var blank=0
      val lines=mutableListOf<String>();val notes=mutableListOf<String>()
      for((ri,row) in columns.rows.withIndex()){
       val parts=mutableListOf<String>()
       for((ci,b) in row.withIndex()){
        val ans=engine.read(bm,b,true)
        val oldSuspect=FineRead.suspicious(ans,b.score)
        val suspect=oldSuspect||(m!=1&&(ans.text.isBlank()||ans.confidence<.75f||ans.minConfidence<.65f))
        count++;if(suspect)uncertain++;if(ans.text.isBlank())blank++
        if(m!=1||!oldSuspect)parts.add(if(suspect)"[待核:${ans.text}]"else ans.text.ifBlank{"[未识别]"})
        notes.add("cell row=${ri+1} col=${ci+1} quad=${b.points.contentToString()} det=${b.score} rec=${ans.confidence} min=${ans.minConfidence} suspect=$suspect text=${ans.text}")
       }
       if(parts.isNotEmpty())lines.add(parts.joinToString(" | "));if(first<0)first=now()-begin
      }
      val rec=now()-begin;val total=detected-init+pMs+rec+(if(m==0)focusMs else 0)
      runOnUiThread{output.append("\n$name round=$round detect_ms=${detected-init} first_row_ms=$first rec_ms=$rec total_ms=$total crops=$count suspect=$uncertain blank=$blank pss_kb=${Debug.getPss()}\n"+lines.joinToString("\n")+"\n"+notes.joinToString("\n")+"\n")}
     }
    }
    runOnUiThread{status.text="完成：请复制诊断和识别结果";busy(false)}
   }catch(t:Throwable){runOnUiThread{output.append("\n失败："+t.toString());status.text="失败";busy(false)}}
  }
 }
}
