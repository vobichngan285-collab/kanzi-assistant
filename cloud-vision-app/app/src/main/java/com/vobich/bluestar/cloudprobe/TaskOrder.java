package com.vobich.bluestar.cloudprobe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure-Java task ordering. Saves stable IDs, never modifies task definitions. */
final class TaskOrder {
    static final String CREATE = "__create_task__";

    private TaskOrder(){}

    static List<String> reconcile(List<String> preferred,List<String> available){
        LinkedHashSet<String> remaining=new LinkedHashSet<>(available);
        List<String> ordered=new ArrayList<>(remaining.size());
        if(preferred!=null){
            for(String id:preferred){
                if(id!=null&&remaining.remove(id))ordered.add(id);
            }
        }
        ordered.addAll(remaining);
        return ordered;
    }

    static List<String> move(List<String> list,int from,int to){
        if(list==null||from<0||from>=list.size()||to<0||to>=list.size())
            throw new IllegalArgumentException("无效的任务位置");
        List<String> ordered=new ArrayList<>(list);
        if(from!=to)ordered.add(to,ordered.remove(from));
        return ordered;
    }
}
