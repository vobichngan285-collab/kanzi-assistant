package com.vobich.bluestar.npulab

import android.content.*
import android.os.*
import android.os.Process
import java.util.ArrayDeque

/** Main process owns watchdog and logs; native runtime only runs in :inference. */
class LabController(private val context:Context,private val log:(String)->Unit,private val status:(String)->Unit,private val answer:(String)->Unit,private val finished:()->Unit){
    private val handler=Handler(Looper.getMainLooper())
    private val queue=ArrayDeque<String>()
    private var model="";private var image="";private var ref=""
    private var current:Slot?=null
    private var disposed=false
    private var active=false
    private inner class Slot(val mode:String){
        var remote:Messenger?=null
        var binder:IBinder?=null
        var pid=0
        var bound=false
        var ending=false
        var phase="connect"
        var run=0
        var start=SystemClock.elapsedRealtime()
        var last=0L
        val death=IBinder.DeathRecipient{handler.post{gone(this)}}
        val receiver=Messenger(Handler(Looper.getMainLooper()){m->
            if(current===this && !ending){
                when(m.what){
                    LabProtocol.HELLO->{pid=m.data.getInt("pid");phase="load";start=SystemClock.elapsedRealtime();line("phase=worker_ready pid="+pid)}
                    LabProtocol.STAGE->{phase=m.data.getString("stage","unknown");run=m.data.getInt("run");start=SystemClock.elapsedRealtime();last=0L;line("phase="+phase+"_begin run="+run);status(mode+" · "+phase+" · "+run)}
                    LabProtocol.ROW->log(m.data.getString("line",""))
                    LabProtocol.RESULT->answer(m.data.getString("text",""))
                    LabProtocol.DONE->{line("phase=mode_complete");end(this)}
                    LabProtocol.FAILED->{line("phase=failed stage="+phase+" "+m.data.getString("line",""));end(this)}
                }
            }
            true
        })
        val connection=object:ServiceConnection{
            override fun onServiceConnected(name:ComponentName,b:IBinder){
                if(current!==this@Slot||ending)return
                binder=b;remote=Messenger(b)
                try{
                    b.linkToDeath(death,0)
                    remote!!.send(Message.obtain(null,LabProtocol.START).apply{
                        replyTo=receiver
                        data=Bundle().apply{putString("mode",mode);putString("model",model);putString("image",image);putString("ref",ref.take(4096))}
                    })
                }catch(_:RemoteException){gone(this@Slot)}
            }
            override fun onServiceDisconnected(name:ComponentName){gone(this@Slot)}
            override fun onBindingDied(name:ComponentName){gone(this@Slot)}
            override fun onNullBinding(name:ComponentName){line("phase=null_binding");end(this@Slot)}
        }
        fun line(s:String)=log("v="+MainActivity.V+" requested="+mode+" "+s)
    }
    private val tick=object:Runnable{
        override fun run(){
            if(!active||disposed)return
            val s=current
            if(s!=null&&!s.ending){
                val elapsed=SystemClock.elapsedRealtime()-s.start
                if(elapsed>=LabProtocol.deadlineMs(s.phase)){
                    s.line("phase=timeout stage="+s.phase+" run="+s.run+" elapsed_ms="+elapsed)
                    end(s)
                }else if(elapsed>=10000 && elapsed-s.last>=10000){
                    s.last=elapsed;s.line("phase=heartbeat stage="+s.phase+" run="+s.run+" elapsed_ms="+elapsed)
                    status(s.mode+" · "+s.phase+" · "+(elapsed/1000)+"秒")
                }
            }
            if(active&&!disposed)handler.postDelayed(this,1000)
        }
    }
    fun start(modes:List<String>,model:String,image:String,ref:String){
        if(active||disposed)return
        require(modes.isNotEmpty() && modes.all{it in LabProtocol.modes})
        this.model=model;this.image=image;this.ref=ref;queue.addAll(modes);active=true
        next();handler.removeCallbacks(tick);handler.postDelayed(tick,1000)
    }
    private fun next(){
        if(disposed)return
        if(queue.isEmpty()){active=false;status("实验结束，请复制数值日志");finished();return}
        val s=Slot(queue.removeFirst());current=s
        status(s.mode+" · 启动隔离进程")
        try{
            s.bound=context.bindService(Intent(context,LabService::class.java),s.connection,Context.BIND_AUTO_CREATE)
            if(!s.bound){s.line("phase=bind_failed");gone(s)}
        }catch(t:Throwable){s.line("phase=bind_failed error="+t.javaClass.simpleName);gone(s)}
    }
    /** Ask worker main looper to self-terminate even if native worker is stuck.
     * If binder death is not confirmed, stop batch instead of starting concurrent engines. */
    private fun end(s:Slot){
        if(current!==s||s.ending)return
        s.ending=true
        val b=s.binder
        if(b==null||!b.isBinderAlive){
            // No worker handshake: do not assume it can be reused safely.
            if(b==null){queue.clear();s.line("phase=worker_not_connected batch_stopped=1")}
            gone(s);return
        }
        try{s.remote?.send(Message.obtain(null,LabProtocol.STOP))}catch(_:RemoteException){gone(s);return}
        handler.postDelayed({
            if(current===s && s.ending){
                if(s.binder?.isBinderAlive!=true){gone(s)}
                else{
                    // PID only obtained from this explicit same-UID service.
                    val pid=s.pid
                    if(pid>0 && pid!=Process.myPid()){
                        s.line("phase=force_stop_worker")
                        runCatching{Process.killProcess(pid)}
                    }
                    handler.postDelayed({
                        if(current===s){
                            if(s.binder?.isBinderAlive!=true)gone(s)
                            else{queue.clear();s.line("phase=worker_exit_unconfirmed batch_stopped=1");cleanup(s);current=null;active=false;status("无法确认实验进程退出，请重开软件");finished()}
                        }
                    },3000)
                }
            }
        },3000)
    }
    private fun cleanup(s:Slot){
        runCatching{s.binder?.unlinkToDeath(s.death,0)}
        if(s.bound){s.bound=false;runCatching{context.unbindService(s.connection)}}
    }
    private fun gone(s:Slot){
        if(current!==s)return
        if(!s.ending)s.line("phase=worker_disconnected stage="+s.phase+" run="+s.run)
        cleanup(s);current=null
        if(!disposed)handler.postDelayed({if(current==null&&active)next()},500)
    }
    fun stop(){
        if(!active)return
        queue.clear();current?.let{it.line("phase=user_stop stage="+it.phase);end(it)}
    }
    fun dispose(){
        disposed=true;queue.clear();active=false;handler.removeCallbacks(tick)
        current?.let{s->runCatching{s.remote?.send(Message.obtain(null,LabProtocol.STOP))};cleanup(s)}
        current=null
    }
}
