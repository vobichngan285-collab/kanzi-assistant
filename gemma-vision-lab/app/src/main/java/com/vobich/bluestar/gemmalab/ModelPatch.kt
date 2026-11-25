package com.vobich.bluestar.gemmalab

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Properties

/** Single streaming pass over the original; never modifies the selected file. */
object ModelPatch {
 private fun hex(b:ByteArray)=b.joinToString(""){"%02x".format(it.toInt() and 255)}
 data class Row(val source:Long,val dest:Long,val size:Long,val asset:String)
 fun apply(input:InputStream, output:File, p:Properties, asset:(String)->InputStream, progress:(Long,Long)->Unit){
  fun number(k:String)=p.getProperty(k).toLong()
  val sourceSize=number("source_size");val resultSize=number("size")
  require(sourceSize==Policy.MODEL_SIZE&&p.getProperty("source_sha256")==Policy.MODEL_SHA){"原厂模型身份错误"}
  require(resultSize>0&&resultSize<4_000_000_000L)
  val count=number("count").toInt();require(count in 1..100)
  val rows=(0 until count).map{i->Row(number("$i.source"),number("$i.dest"),number("$i.size"),p.getProperty("$i.asset"))}
  var end=0L;var sourceEnd=0L
  for(r in rows){
   require(r.size>0&&r.dest>=end&&r.dest<=resultSize-r.size)
   end=r.dest+r.size
   if(r.asset.isEmpty()){require(r.source>=sourceEnd&&r.source<=sourceSize-r.size);sourceEnd=r.source+r.size}
   else require(r.source==-1L&&r.asset in setOf("header.bin","encoder.tflite","adapter.tflite","metadata.bin"))
  }
  require(end==resultSize)
  val sha=MessageDigest.getInstance("SHA-256")
  val buf=ByteArray(1024*1024)
  var pos=0L;var last=0L
  RandomAccessFile(output,"rw").use{out->
   out.setLength(0);out.setLength(resultSize)
   fun consume(n:Long,write:Boolean){
    var left=n
    while(left>0){
     val got=input.read(buf,0,minOf(left,buf.size.toLong()).toInt())
     require(got>0){"原厂模型文件不完整"}
     sha.update(buf,0,got);if(write)out.write(buf,0,got)
     left-=got;pos+=got
     if(pos-last>=64L*1024*1024){last=pos;progress(pos,sourceSize)}
    }
   }
   for(r in rows){
    out.seek(r.dest)
    if(r.asset.isEmpty()){consume(r.source-pos,false);consume(r.size,true)}
    else asset(r.asset).use{a->
     var left=r.size
     while(left>0){val n=a.read(buf,0,minOf(left,buf.size.toLong()).toInt());require(n>0){"内置补丁不完整"};out.write(buf,0,n);left-=n}
     require(a.read()==-1){"内置补丁长度错误"}
    }
   }
   consume(sourceSize-pos,false);require(input.read()==-1){"原厂模型尺寸错误"}
   require(hex(sha.digest())==Policy.MODEL_SHA){"请选择已下载的原厂280模型，SHA不匹配"}
   out.fd.sync()
  }
  val check=MessageDigest.getInstance("SHA-256")
  output.inputStream().buffered().use{f->while(true){val n=f.read(buf);if(n<0)break;check.update(buf,0,n)}}
  require(output.length()==resultSize&&hex(check.digest())==p.getProperty("sha256")){"560重组校验失败"}
 }
}
