package com.vobich.bluestar.cloudprobe;
import java.util.*;
public final class C110DeferredPhotoTest {
    static void ok(boolean value){if(!value)throw new AssertionError();}
    static Set<String> set(String... p){return new HashSet<>(Arrays.asList(p));}
    public static void main(String[] args){
        C110PhysicalPolicy policy=new C110PhysicalPolicy();
        C110DeferredPhoto<String> buffer=new C110DeferredPhoto<>();
        policy.arm(set("old"));
        buffer.offer("old_pending");buffer.clear();ok(policy.ready());
        ok(policy.begin(3020,19,0)&&policy.finish(3020,20,200)==200);
        Set<String> first=set("old","first");
        String a=C110PhysicalPolicy.latest(first,policy.history());
        ok("first".equals(a));policy.remember(first);
        buffer.offer(a);ok(buffer.takeIfReady(true)==null);
        ok(policy.begin(3020,19,1000)&&policy.finish(3020,20,1200)==200);
        Set<String> second=set("old","first","second");
        String b=C110PhysicalPolicy.latest(second,policy.history());
        ok("second".equals(b));policy.remember(second);
        buffer.offer(b);ok(buffer.takeIfReady(true)==null);
        ok("second".equals(buffer.takeIfReady(false)));
        ok(buffer.takeIfReady(false)==null);
        ok(C110PhysicalPolicy.latest(second,policy.history())==null);
        buffer.offer("third");buffer.clear();policy.arm(set("old","third"));
        ok(buffer.takeIfReady(false)==null&&policy.ready());
        ok(policy.finish(3020,20,1300)<0);
        ok(policy.begin(3020,19,1400)&&policy.finish(3020,20,1600)==200);
        ok("fourth".equals(C110PhysicalPolicy.latest(set("old","third","fourth"),policy.history())));
        System.out.println("PASS voice deferral, latest-wins, preserved baseline and manual rearm");
    }
}
