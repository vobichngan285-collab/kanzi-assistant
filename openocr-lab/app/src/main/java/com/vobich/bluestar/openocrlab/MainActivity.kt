package com.vobich.bluestar.openocrlab
import android.app.Activity
import android.content.*
import android.graphics.*
import android.os.*
import android.widget.*
import kotlin.math.*
import java.util.concurrent.Executors
class MainActivity:Activity(){
 private val worker=Executors.newSingleThreadExecutor()
 private val names=listOf("YOLO26n-Text","YOLO26s-Text","YOLO26m-Text","YOLO26l-Text","YOLO26x-Text","OpenOCR RepViT-DB")
 private val assets=listOf("yolo26n-text.onnx","yolo26s-text.onnx","yolo26m-text.onnx","yolo26l-text.onnx","yolo26x-text.onnx")
 private lateinit var model:Spinner;private lateinit var threads:Spinner;private lateinit var scan:Spinner;private lateinit var layout:Spinner;private lateinit var viewModel:Spinner
 private lateinit var preview:ImageView;private lateinit var output:TextView;private lateinit var status:TextView
 private lateinit var choose:Button;private lateinit var single:Button;private lateinit var all:Button
 private var source:Bitmap?=null;private var reader:Reader?=null;private var readerThreads=0
 private var inputDescription="";private var detector:AutoCloseable?=null;private var key="";private var detect:((Bitmap)->List<TextLocator.Box>)?=null
 private val previews=mutableMapOf<Int,Bitmap>()
 private fun now()=SystemClock.elapsedRealtime()
 override fun onCreate(state:Bundle?){super.onCreate(state)
  val scroll=ScrollView(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,48,24,32)};scroll.addView(box);setContentView(scroll)
  fun label(t:String)=TextView(this).apply{text=t;textSize=17f;box.addView(this)}
  fun spinner(values:List<String>)=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,values);box.addView(this)}
  fun button(t:String,action:()->Unit)=Button(this).apply{text=t;box.addView(this);setOnClickListener{action()}}
  label("蓝星 OpenOCR Arena v0.2.0 · 六模型 · 离线")
  choose=button("选择相册原图"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},9)}
  model=spinner(names);threads=spinner(listOf("CPU 4线程","CPU 2线程","CPU 6线程"))
  scan=spinner(listOf("全图单次检测（默认）","全图＋四块高清检测"))
  layout=spinner(listOf("原始框识别（默认）","v0.1.4混合排版","v0.1.5完整竖列","横排行列裁剪"))
  single=button("单模型测试"){test(false)};all=button("六模型一键擂台"){test(true)}
  status=label("请选择图片")
  label("画框预览：黄色原始框 / 青色横排 / 紫色竖排")
  viewModel=spinner(names)
  preview=ImageView(this).apply{adjustViewBounds=true;maxHeight=650;box.addView(this)}
  viewModel.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){preview.setImageBitmap(previews[position]?:source)}
  }
  button("复制全部诊断"){(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Arena",output.text))}
  output=label("");output.setTextIsSelectable(true)
 }
 private fun busy(v:Boolean){listOf(choose,single,all,model,threads,scan,layout,viewModel).forEach{it.isEnabled=!v}}
 override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req!=9||res!=RESULT_OK)return;val uri=data?.data?:return;busy(true)
  worker.execute{try{val bm=ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,uri)){d,i,_->require(i.size.width.toLong()*i.size.height<=16_000_000L);d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE}
   runOnUiThread{preview.setImageBitmap(bm);previews.values.forEach{it.recycle()};previews.clear();source?.recycle();source=bm;status.text="原图 ${bm.width}×${bm.height}";busy(false)}
  }catch(t:Throwable){runOnUiThread{status.text=t.toString();busy(false)}}}
 }
 private fun releaseDetector(){detector?.close();detector=null;detect=null;key=""}
 private fun loadDetector(id:Int,nt:Int){
  if(key=="$id/$nt"&&detect!=null)return
  releaseDetector()
  if(id==5){val d=DbLocator(this,nt);detector=d;inputDescription="DB native (max960 if dynamic)";detect={d.locate(it)}}
  else{val d=TextLocator(this,assets[id],nt);detector=d;inputDescription="${d.inputSide}x${d.inputSide}";detect={d.locate(it).boxes}}
  key="$id/$nt"
 }
 private fun test(arena:Boolean){
  val bm=source?:return;val ids=if(arena)names.indices.toList()else listOf(model.selectedItemPosition)
  val nt=listOf(4,2,6)[threads.selectedItemPosition];val tiled=scan.selectedItemPosition==1;val kind=layout.selectedItemPosition
  busy(true);output.text="Arena v0.2.0 source=${bm.width}x${bm.height} threads=$nt tiles=$tiled layout=$kind\n"
  worker.execute{
   try{
    val load=now();if(reader==null||readerThreads!=nt){reader?.close();reader=null;reader=Reader(this,"rep.onnx",nt);readerThreads=nt}
    val rec=reader!!;runOnUiThread{output.append("recognizer_load_ms=${now()-load}\n")}
    for(id in ids){
     runOnUiThread{status.text="测试 ${names[id]}"}
     try{
      if(arena)releaseDetector()
      val init=now();loadDetector(id,nt);val initMs=now()-init
      // Two full runs with the same resident detector; first and warm reported separately.
      repeat(2){round->
       val start=now();val initial=detect!!(bm);val global=now()-start
       val tileStart=now();val raw=initial.toMutableList()
       if(tiled)for(y in 0..1)for(x in 0..1){
        val l=if(x==0)0 else (bm.width*.40f).toInt();val t=if(y==0)0 else (bm.height*.40f).toInt()
        val r=if(x==0)(bm.width*.60f).toInt()else bm.width;val b=if(y==0)(bm.height*.60f).toInt()else bm.height
        val tile=Bitmap.createBitmap(bm,l,t,r-l,b-t)
        try{raw.addAll(detect!!(tile).map{q->TextLocator.Box(q.points.mapIndexed{i,v->v+if(i%2==0)l else t}.toFloatArray(),q.score)})}finally{if(tile!==bm)tile.recycle()}
       }
       val tileMs=now()-tileStart;val planStart=now();val boxes=MixedLayout.dedup(raw)
       val blocks=when(kind){
        1->MixedLayout.plan(boxes,bm.width,bm.height)
        2->MixedLayout.completePlan(boxes,bm.width,bm.height)
        3->RowPlanner.plan(boxes,bm.width,bm.height,1.6f,true).rows.map{MixedLayout.Block(it,false)}
        else->boxes.sortedWith(compareBy<TextLocator.Box>{it.rect.top}.thenBy{it.rect.left}).map{MixedLayout.Block(listOf(it),it.rect.height()>it.rect.width()*2.2f)}
       }
       val planMs=now()-planStart
       val begin=now();val lines=mutableListOf<String>();val details=mutableListOf<String>();var first=-1L;var n=0
       for((i,block) in blocks.withIndex()){
        val parts=mutableListOf<String>()
        for(q in block.boxes){
         val ans=if(block.vertical)MixedLayout.verticalRead(bm,q,rec,kind==2)else rec.read(bm,q,kind!=0)
         val text=ans.text.trim();val suspect=FineRead.suspicious(ans,q.score)||text.isBlank()||ans.confidence<.4f
         parts.add(if(suspect)"[待核:$text]"else text)
         n++;details.add("cell=$i vertical=${block.vertical} quad=${q.points.contentToString()} det=${q.score} rec=${ans.confidence} min=${ans.minConfidence} suspect=$suspect text=$text")
        }
        lines.add(parts.joinToString(" | "));if(first<0)first=now()-begin
       }
       val recMs=now()-begin
       val mem=android.os.Debug.getPss()
       val scale=min(1f,1000f/max(bm.width,bm.height));val temp=Bitmap.createScaledBitmap(bm,(bm.width*scale).toInt().coerceAtLeast(1),(bm.height*scale).toInt().coerceAtLeast(1),true)
       val shown=temp.copy(Bitmap.Config.ARGB_8888,true);if(temp!==bm)temp.recycle()
       Canvas(shown).apply{scale(scale,scale);val paint=Paint().apply{style=Paint.Style.STROKE;strokeWidth=3/scale}
        fun draw(q:TextLocator.Box){val path=Path();path.moveTo(q.points[0],q.points[1]);for(i in 1..3)path.lineTo(q.points[i*2],q.points[i*2+1]);path.close();drawPath(path,paint)}
        paint.color=Color.YELLOW;raw.forEach{draw(it)}
        blocks.forEach{bl->paint.color=if(bl.vertical)Color.MAGENTA else Color.CYAN;bl.boxes.forEach{draw(it)}}
       }
       val report="\n${names[id]} round=${round+1} input=$inputDescription detector_load_ms=${if(round==0)initMs else 0} global_ms=$global tile_ms=$tileMs raw_boxes=${raw.size} unique_boxes=${boxes.size} blocks=${blocks.size} crops=$n plan_ms=$planMs rec_ms=$recMs first_ms=$first total_ms=${global+tileMs+planMs+recMs} pss_kb=$mem\n"+lines.joinToString("\n")+"\n"+raw.mapIndexed{i,q->"raw=$i det=${q.score} quad=${q.points.contentToString()}"}.joinToString("\n")+"\n"+details.joinToString("\n")+"\n"
       runOnUiThread{preview.setImageBitmap(shown);previews.put(id,shown)?.recycle();viewModel.setSelection(id);output.append(report)}
      }
     }catch(t:Throwable){releaseDetector();runOnUiThread{output.append("\n${names[id]} FAILED: $t\n")}}
     finally{if(arena)releaseDetector()}
    }
    runOnUiThread{status.text="完成：复制全部诊断";busy(false)}
   }catch(t:Throwable){runOnUiThread{status.text=t.toString();busy(false)}}
  }
 }
}
