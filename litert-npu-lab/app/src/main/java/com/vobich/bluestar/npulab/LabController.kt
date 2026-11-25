package com.vobich.bluestar.npulab

import android.content.*
import android.os.*
import android.os.Process
import java.util.ArrayDeque

/** Main process owns watchdog and logs; native runtime only runs in :inference. */
class LabController(private val context:Context,private val log:(String)->Unit,private val status:(String)->Unit,private val answer:(String)->Unit,private val finished:()->Unit){
    private val handler=Handler(Looper.getMainLooper())
    private val queue=ArrayDeque<String>()
    private var model="";private var image="";private var ref="";private var strategy="compare"
    private var current:Slot?=null
    private var disposed=false
    private var active=false
    private var blocked=false
    private var keepResident=false
    private var batch=0L
    private var block=0
    private inner class Slot(val mode:String){
        var remote:Messenger?=null
        var binder:IBinder?=null
        var pid=0
        var bound=false
        var ending=false
        var resident=false
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
                    LabProtocol.DONE->{line("phase=mode_complete");if(keepResident&&queue.isEmpty()){resident=true;phase="idle";active=false;line("phase=resident_idle");status(mode+" · 实验结束，模型已常驻");finished()}else end(this)}
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
                    sendStart(this@Slot)
                }catch(_:RemoteException){gone(this@Slot)}
            }
            override fun onServiceDisconnected(name:ComponentName){gone(this@Slot)}
            override fun onBindingDied(name:ComponentName){gone(this@Slot)}
            override fun onNullBinding(name:ComponentName){line("phase=null_binding");end(this@Slot)}
        }
        fun line(s:String)=log("v="+MainActivity.V+" requested="+mode+" "+s)
    }
    private fun sendStart(s:Slot){
        s.resident=false;s.phase="load";s.run=0;s.start=SystemClock.elapsedRealtime();s.last=0
        try{
            requireNotNull(s.remote).send(Message.obtain(null,LabProtocol.START).apply{
                replyTo=s.receiver
                data=Bundle().apply{putString("mode",s.mode);putString("model",model);putString("image",image);putString("ref",ref.take(4096));putString("strategy",strategy);putLong("batch",batch);putInt("block",block)}
            })
        }catch(t:Throwable){s.line("phase=start_failed error="+t.javaClass.simpleName);end(s)}
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
    fun start(modes:List<String>,model:String,image:String,ref:String,strategy:String){
        if(disposed||active){finished();return}
        if(blocked){status("无法确认旧进程退出，请关闭软件重开");finished();return}
        require(modes.isNotEmpty() && modes.all{it in LabProtocol.modes})
        require(strategy in LabProtocol.strategies)
        this.model=model;this.image=image;this.ref=ref;this.strategy=strategy
        batch=SystemClock.elapsedRealtime();block=0;keepResident=modes.size==1
        queue.addAll(modes);active=true
        log("v="+MainActivity.V+" phase=batch_begin batch="+batch+" order="+modes.joinToString(",")+" strategy="+strategy)
        val old=current
        if(old!=null){
            if(!old.ending&&old.resident&&keepResident&&old.mode==modes.first()&&old.binder?.isBinderAlive==true){
                queue.removeFirst();block=1;old.line("phase=resident_reuse batch="+batch);sendStart(old)
            }else if(!old.ending)end(old)
        }else next()
        handler.removeCallbacks(tick);handler.postDelayed(tick,1000)
    }
    private fun next(){
        if(disposed)return
        if(queue.isEmpty()){active=false;status("实验结束，请复制数值日志");finished();return}
        block++;val s=Slot(queue.removeFirst());current=s
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
                            else{queue.clear();s.line("phase=worker_exit_unconfirmed batch_stopped=1");cleanup(s);current=null;blocked=true;val wasActive=active;active=false;status("无法确认实验进程退出，请重开软件");if(wasActive)finished()}
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
        if(!disposed){if(active)handler.postDelayed({if(current==null&&active)next()},500) else status("模型已释放") }
    }
    fun stop(){
        queue.clear()
        val s=current
        if(s!=null){s.line("phase=user_stop stage="+s.phase);end(s)}
        else if(active){active=false;status("实验已停止");finished()}
    }
    fun dispose(){
        disposed=true;queue.clear();active=false;handler.removeCallbacks(tick)
        current?.let{s->runCatching{s.remote?.send(Message.obtain(null,LabProtocol.STOP))};cleanup(s)}
        current=null
    }
}
