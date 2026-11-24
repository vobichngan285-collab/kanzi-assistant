package com.vobich.bluestar.cloudprobe;
import java.util.*;
public final class C110DeferredPhotoTest {
    static void ok(boolean value){if(!value)throw new AssertionError();}
    static Set<String> set(String... p){return new HashSet<>(Arrays.asList(p));}
    public static void main(String[] args){
        C110PhysicalPolicy policy=new C110PhysicalPolicy();
        C110DeferredPhoto<String> buffer=new C110DeferredPhoto<>();
        policy.observe(set("old"));policy.observe(set("old"));
        ok(policy.ready());
        // Starting ASR clears the previous pending task, not the historical baseline.
        buffer.offer("old_pending");buffer.clear();
        ok(policy.ready());
        C110PhysicalPolicy.Delta first=policy.observe(set("old","first"));
        ok(first.unique&&"first".equals(first.latest));
        buffer.offer(first.latest);
        ok(buffer.takeIfReady(true)==null);
        C110PhysicalPolicy.Delta second=policy.observe(set("old","first","second"));
        ok(second.unique&&"second".equals(second.latest));
        buffer.offer(second.latest);
        ok(buffer.takeIfReady(true)==null);
        // Once target has been committed on the main thread, exactly latest dispatches once.
        ok("second".equals(buffer.takeIfReady(false)));
        ok(buffer.takeIfReady(false)==null);
        ok(policy.observe(set("old","first","second")).latest==null);
        buffer.offer("third");buffer.clear();policy.reset();
        ok(buffer.takeIfReady(false)==null&&!policy.ready());
        ok(policy.observe(set("old","third")).latest==null);
        ok(policy.observe(set("old","third")).latest==null);
        ok("fourth".equals(policy.observe(set("old","third","fourth")).latest));
        System.out.println("PASS voice deferral, latest-wins, preserved baseline and manual reset");
    }
}
