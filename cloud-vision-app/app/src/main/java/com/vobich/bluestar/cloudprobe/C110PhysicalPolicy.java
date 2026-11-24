package com.vobich.bluestar.cloudprobe;

import java.util.*;
/** Pure session delta policy: initial/reconnected photos never become tasks. */
final class C110PhysicalPolicy {
    static final String ENABLE="c110_physical_watch", INTERVAL="c110_physical_interval",
        DELETE="c110_physical_delete";
    static final int[] INTERVALS={100,200,300,500};
    static int interval(int raw){
        for(int v:INTERVALS)if(raw==v)return v;
        return 300;
    }
    static final class Delta {
        final String latest;
        final boolean unique;
        final int count;
        Delta(String latest,int count){this.latest=latest;this.count=count;unique=count==1;}
    }
    private final Set<String> seen=new HashSet<>();
    private Set<String> baseline;
    private boolean ready;
    void reset(){seen.clear();baseline=null;ready=false;}
    boolean ready(){return ready;}
    Delta observe(Set<String> paths){
        if(!ready){
            if(baseline==null){baseline=new HashSet<>(paths);return new Delta(null,0);}
            // Two complete snapshots establish history. No upload while arming.
            seen.addAll(baseline);seen.addAll(paths);baseline=null;ready=true;
            return new Delta(null,0);
        }
        ArrayList<String> added=new ArrayList<>();
        for(String path:paths)if(!seen.contains(path))added.add(path);
        seen.addAll(paths);
        Collections.sort(added);
        return new Delta(added.isEmpty()?null:added.get(added.size()-1),added.size());
    }
    // Only a verified successful single-file deletion makes this path reusable.
    void deleted(String path){if(ready)seen.remove(path);}
}
