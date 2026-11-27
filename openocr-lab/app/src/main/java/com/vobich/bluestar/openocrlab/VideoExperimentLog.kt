package com.vobich.bluestar.openocrlab
import android.content.Context
/** Bounded local diagnostic history; never saves image content, recognized text or keys. */
object VideoExperimentLog {
 private const val LIMIT=140000
 @Synchronized fun add(context:Context,line:String){
  val prefs=context.getSharedPreferences("video_experiment_log",Context.MODE_PRIVATE)
  val current=prefs.getString("history","")?:""
  val all=current+android.os.SystemClock.elapsedRealtime()+" "+line+"\n"
  val cut=if(all.length<=LIMIT)all else all.substring(all.indexOf('\n',all.length-LIMIT).let{if(it<0)all.length-LIMIT else it+1})
  prefs.edit().putString("history",cut).apply()
 }
 @Synchronized fun read(context:Context)=context.getSharedPreferences("video_experiment_log",Context.MODE_PRIVATE).getString("history","")?:""
 @Synchronized fun clear(context:Context){context.getSharedPreferences("video_experiment_log",Context.MODE_PRIVATE).edit().remove("history").apply()}
}
