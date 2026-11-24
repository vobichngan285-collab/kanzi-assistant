package com.vobich.bluestar.cloudprobe;

import android.app.Activity;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Foreground-only, non-blocking Home status. No extra Wi-Fi lease, shutter, or camera writes. */
final class C110HomeStatus {
    static final class State {
        final boolean connected,reading;
        final C110BatteryPolicy.Reading battery;
        State(boolean connected,boolean reading,C110BatteryPolicy.Reading battery){
            this.connected=connected;this.reading=reading;this.battery=battery;
        }
    }
    private static final long CHECK_MS=3000, BATTERY_MS=60000;
    private final Activity owner;
    private final BooleanSupplier shotBusy;
    private final Consumer<State> updates;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private boolean active,closed,inFlight;
    private Network network;
    private long nextReadAt,lastReadAt=-60000;
    private int generation;
    private C110BatteryPolicy.Reading battery=C110BatteryPolicy.unknown();
    private State state=new State(false,false,battery);
    private final Runnable tick=this::poll;

    C110HomeStatus(Activity owner,BooleanSupplier shotBusy,Consumer<State> updates){
        this.owner=owner;this.shotBusy=shotBusy;this.updates=updates;
    }
    State state(){return state;}
    void start(){
        if(closed)return;
        active=true;nextReadAt=0;
        main.removeCallbacks(tick);
        main.post(tick);
    }
    void stop(){
        active=false;generation++;main.removeCallbacks(tick);
        network=null;battery=C110BatteryPolicy.unknown();
        state=new State(false,false,battery);
    }
    void shutdown(){stop();closed=true;io.shutdownNow();}
    void refresh(){
        if(!active||closed)return;
        if(SystemClock.elapsedRealtime()-lastReadAt>=10000)nextReadAt=0;
        main.removeCallbacks(tick);main.post(tick);
    }
    private Network connected(){
        try{return C110WifiBootstrap.connected(owner);}
        catch(RuntimeException ex){return null;}
    }
    private void publish(boolean reading){
        state=new State(network!=null,reading,battery);
        updates.accept(state);
    }
    private void poll(){
        if(!active||closed)return;
        Network current=connected();
        if(!java.util.Objects.equals(current,network)){
            generation++;network=current;nextReadAt=0;
            battery=C110BatteryPolicy.unknown();
            publish(false);
        }
        long now=SystemClock.elapsedRealtime();
        if(network!=null&&!inFlight&&!shotBusy.getAsBoolean()&&now>=nextReadAt){
            final Network target=network;
            final int ticket=generation;
            inFlight=true;lastReadAt=now;nextReadAt=now+BATTERY_MS;
            publish(true);
            try{
                io.execute(()->{
                    C110BatteryPolicy.Reading result=read(target);
                    main.post(()->{
                        inFlight=false;
                        if(closed||!active||ticket!=generation)return;
                        if(!target.equals(connected())){
                            generation++;network=null;battery=C110BatteryPolicy.unknown();
                            publish(false);return;
                        }
                        battery=result;publish(false);
                    });
                });
            }catch(RuntimeException e){inFlight=false;publish(false);}
        }
        main.postDelayed(tick,CHECK_MS);
    }
    private static C110BatteryPolicy.Reading read(Network network){
        HttpURLConnection conn=null;
        try{
            // The existing connected camera Network, no process binding or redirects.
            conn=(HttpURLConnection)network.openConnection(new URL(
                "http://192.168.1.254/?custom=1&cmd=3019"));
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(1200);conn.setReadTimeout(1200);
            conn.setUseCaches(false);
            if(conn.getResponseCode()!=200)return C110BatteryPolicy.unknown();
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] chunk=new byte[512];int n;
                long deadline=SystemClock.elapsedRealtime()+1800;
                while((n=in.read(chunk))!=-1){
                    if(out.size()+n>8192||SystemClock.elapsedRealtime()>deadline)
                        return C110BatteryPolicy.unknown();
                    out.write(chunk,0,n);
                }
                return C110BatteryPolicy.parse(out.toString(StandardCharsets.UTF_8.name()));
            }
        }catch(Exception e){return C110BatteryPolicy.unknown();}
        finally{if(conn!=null)try{conn.disconnect();}catch(RuntimeException ignored){}}
    }
}
