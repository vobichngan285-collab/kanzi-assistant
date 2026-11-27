package com.vobich.bluestar.openocrlab;
import java.util.*;
/** Pure event gate and snapshot history. Only a paired 19/20 event authorizes acquisition. */
final class C110PhysicalPolicy {
    static final String ENABLE="c110_physical_watch";
    private boolean armed;
    private long started=-1;
    private final Set<String> seen=new HashSet<>();
    void arm(Set<String> paths){seen.clear();seen.addAll(paths);armed=true;started=-1;}
    boolean ready(){return armed;}
    boolean begin(int cmd,int status,long now){
        if(!armed||cmd!=3020||status!=19)return false;
        started=now;return true;
    }
    int finish(int cmd,int status,long now){
        if(!armed||cmd!=3020||status!=20||started<0)return -1;
        long gap=now-started;started=-1;
        return gap>=0&&gap<=10000?(int)gap:-1;
    }
    Set<String> history(){return new HashSet<>(seen);}
    void remember(Set<String> paths){seen.addAll(paths);}
    static String latest(Set<String> paths,Set<String> history){
        String best=null;
        for(String path:paths)if(!history.contains(path)&&(best==null||path.compareTo(best)>0))best=path;
        return best;
    }
}
