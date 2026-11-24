package com.vobich.bluestar.cloudprobe;
import java.util.*;
public final class C110PhysicalPolicyTest {
    static Set<String> set(String... p){return new HashSet<>(Arrays.asList(p));}
    static void ok(boolean v){if(!v)throw new AssertionError();}
    public static void main(String[] args){
        C110PhysicalPolicy p=new C110PhysicalPolicy();
        ok(!p.begin(3020,19,0)&&p.finish(3020,20,200)<0);
        p.arm(set("a","b"));
        ok(p.ready()&&p.finish(3020,20,300)<0);
        ok(!p.begin(3020,8,100)&&p.finish(3020,9,200)<0);
        ok(p.begin(3020,19,3246)&&p.finish(3020,20,3466)==220);
        ok(p.finish(3020,20,3467)<0);
        ok(p.begin(3020,19,10984)&&p.finish(3020,20,11181)==197);
        ok(p.begin(3020,19,12000)&&p.finish(3020,20,24000)<0);
        ok(C110PhysicalPolicy.latest(set("a","b"),p.history())==null);
        ok("d".equals(C110PhysicalPolicy.latest(set("a","c","d"),p.history())));
        p.remember(set("a","c","d"));
        ok(C110PhysicalPolicy.latest(set("a","b","c","d"),p.history())==null);
        p.arm(set("old"));ok(p.finish(3020,20,25000)<0);
        ok("new".equals(C110PhysicalPolicy.latest(set("old","new"),p.history())));
        ok(C110JpegPath.exactJpegPath("/DCIM/PHOTO/IMG001.JPG")!=null);
        for(String bad:new String[]{"/DCIM/","/DCIM/../a.jpg","/DCIM/%2e%2e/a.jpg","/DCIM/a.jpg?cmd=4004","/DCIM/a.mp4","/DCIM/*","/DCIM/a\\b.jpg"})
            ok(C110JpegPath.exactJpegPath(bad)==null);
        System.out.println("PASS event pair, no old images, dedupe, strict read-only JPEG path");
    }
}
