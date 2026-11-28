package com.vobich.bluestar.openocrlab

import android.content.Context
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/** Private persistent plain-text history. No images, URIs, or network transfer.
 * Never silently evict records. Clipboard limit avoids Binder transaction overflow.
 */
internal class ExperimentHistory(context:Context){
 private val file=File(context.filesDir,"cooperative-runs.txt")
 @Synchronized fun append(s:String){file.appendText(s+"\n",Charsets.UTF_8)}
 @Synchronized fun allForClipboard():String?=if(!file.exists())"" else if(file.length()>300_000L)null else file.readText(Charsets.UTF_8)
 @Synchronized fun copyTo(out:OutputStream){if(file.exists())file.inputStream().use{it.copyTo(out)}}
 @Synchronized fun clear(){if(file.exists())check(file.delete())}
 @Synchronized fun size()=file.length()
 companion object{
  fun sampleHash(input:java.io.InputStream):String{
   val md=MessageDigest.getInstance("SHA-256")
   val b=ByteArray(64*1024)
   while(true){val n=input.read(b);if(n<0)break;if(n>0)md.update(b,0,n)}
   return md.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
  }
 }
}
