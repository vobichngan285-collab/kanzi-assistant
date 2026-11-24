package com.vobich.bluestar.cloudprobe;
import java.util.*;
public final class C110PhysicalPolicyTest {
    static Set<String> set(String... p){return new HashSet<>(Arrays.asList(p));}
    static void ok(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        C110PhysicalPolicy p=new C110PhysicalPolicy();
        ok(C110PhysicalPolicy.interval(100)==100&&C110PhysicalPolicy.interval(200)==200&&
           C110PhysicalPolicy.interval(300)==300&&C110PhysicalPolicy.interval(500)==500&&
           C110PhysicalPolicy.interval(1)==300);
        ok(p.observe(set("a")).latest==null&&!p.ready());
        ok(p.observe(set("a","b")).latest==null&&p.ready());
        ok(p.observe(set("a","b")).latest==null);
        C110PhysicalPolicy.Delta d=p.observe(set("a","b","c"));
        ok(d.unique&&"c".equals(d.latest));
        ok(p.observe(set("a","b","c")).latest==null);
        d=p.observe(set("a","b","c","d","e"));
        ok(!d.unique&&d.count==2&&"e".equals(d.latest));
        ok(p.observe(set("a")).latest==null);
        ok(p.observe(set("a","b","c","d","e")).latest==null);
        p.deleted("c");
        ok("c".equals(p.observe(set("a","b","c","d","e")).latest));
        p.reset();ok(p.observe(set()).latest==null);
        ok(p.observe(set("old")).latest==null);
        ok(p.observe(set("old","new")).unique);
        ok(C110DeletePolicy.exactJpegPath("/DCIM/PHOTO/IMG001.JPG")!=null);
        for(String bad:new String[]{"/DCIM/","/DCIM/../a.jpg","/DCIM/%2e%2e/a.jpg",
                "/DCIM/a.jpg?cmd=4004","/DCIM/a.mp4","/DCIM/*","/DCIM/a\\b.jpg"})
            ok(C110DeletePolicy.exactJpegPath(bad)==null);
        ok(C110DeletePolicy.oneFileDeleteEndpoint("/DCIM/PHOTO/IMG001.JPG").equals(
            "/?custom=1&cmd=4003&str=A%3A%5CDCIM%5CPHOTO%5CIMG001.JPG"));
        System.out.println("PASS physical session baseline, no duplicates, latest and exact single-file deletion");
    }
}
