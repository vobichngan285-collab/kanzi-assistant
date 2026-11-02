import com.vobichngan.kanzi.LayoutFrameScheduler;

public final class LayoutFrameSchedulerSmokeTest {
    private static void expect(LayoutFrameScheduler.Action expected, LayoutFrameScheduler.Action actual) {
        if(expected!=actual)throw new AssertionError("expected "+expected+" got "+actual);
    }
    public static void main(String[] args) {
        LayoutFrameScheduler s=new LayoutFrameScheduler(200,1500,.03f);
        expect(LayoutFrameScheduler.Action.ANALYZE,s.decide(1000,0,false));
        expect(LayoutFrameScheduler.Action.GEOMETRY_FALLBACK,s.decide(1010,0,false));
        s.completed(1100,true);
        expect(LayoutFrameScheduler.Action.REUSE,s.decide(1200,.01f,false));
        expect(LayoutFrameScheduler.Action.GEOMETRY_FALLBACK,s.decide(1250,.1f,false));
        expect(LayoutFrameScheduler.Action.ANALYZE,s.decide(1400,.1f,false));
        s.completed(1450,true);
        expect(LayoutFrameScheduler.Action.REUSE,s.decide(1500,0,false));
        expect(LayoutFrameScheduler.Action.ANALYZE,s.decide(3000,0,false));
        s.completed(3010,false);
        expect(LayoutFrameScheduler.Action.GEOMETRY_FALLBACK,s.decide(3100,0,false));
        expect(LayoutFrameScheduler.Action.ANALYZE,s.decide(3300,0,false));
        s.completed(3350,true);
        expect(LayoutFrameScheduler.Action.GEOMETRY_FALLBACK,s.decide(3400,Float.NaN,false));
        s.invalidate();
        expect(LayoutFrameScheduler.Action.ANALYZE,s.decide(3500,0,false));
        System.out.println("LAYOUT_FRAME_SCHEDULER=PASS");
    }
}
