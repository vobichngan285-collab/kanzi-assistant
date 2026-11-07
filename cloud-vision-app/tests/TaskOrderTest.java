package com.vobich.bluestar.cloudprobe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TaskOrderTest {
    private static void check(boolean ok,String info){if(!ok)throw new AssertionError(info);}
    private static void equal(List<String> actual,String... expected){
        check(actual.equals(Arrays.asList(expected)),"order mismatch: "+actual);
    }
    private static List<String> ids(String... a){return Arrays.asList(a);}
    public static void main(String[] args){
        final String CREATE=TaskOrder.CREATE;
        List<String> base=ids("ocr","menu","hospital","shop","product",CREATE);
        equal(TaskOrder.reconcile(null,base),"ocr","menu","hospital","shop","product",CREATE);
        equal(TaskOrder.move(base,0,4),"menu","hospital","shop","product","ocr",CREATE);
        equal(TaskOrder.move(base,5,0),CREATE,"ocr","menu","hospital","shop","product");
        equal(TaskOrder.move(base,1,1),"ocr","menu","hospital","shop","product",CREATE);
        equal(TaskOrder.move(base,4,0),"product","ocr","menu","hospital","shop",CREATE);
        // Stale deleted custom tasks never reappear. Every available ID appears once.
        equal(TaskOrder.reconcile(ids("user-deleted","shop","shop",CREATE,"ocr"),base),
            "shop",CREATE,"ocr","menu","hospital","product");
        // User custom tasks and creation entry may be placed anywhere.
        equal(TaskOrder.reconcile(ids("user-1",CREATE,"hospital"),ids("ocr","hospital",CREATE,"user-1","user-2")),
            "user-1",CREATE,"hospital","ocr","user-2");
        // Across a 9-cell page: move from first to page two and back.
        List<String> many=new ArrayList<>();
        for(int i=0;i<14;i++)many.add("user-"+i);
        List<String> shifted=TaskOrder.move(many,0,12);
        check(shifted.get(12).equals("user-0")&&shifted.get(0).equals("user-1"),
            "drag across pages");
        check(TaskOrder.move(shifted,12,0).equals(many),"return across pages");
        // Moves do not mutate the catalog or its definitions.
        equal(base,"ocr","menu","hospital","shop","product",CREATE);
        boolean rejected=false;
        try{TaskOrder.move(base,-1,0);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"reject invalid source");
        rejected=false;
        try{TaskOrder.move(base,0,6);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"reject invalid destination");
        System.out.println("TASK_ORDER_DRAG_RECONCILIATION_TESTS=PASS");
    }
}
