import com.vobichngan.kanzi.OcrPerformanceProfile;
import com.vobichngan.kanzi.VerticalCropAxisGuard;
import com.vobichngan.kanzi.VerticalRecRescuePolicy;

public final class X200OfflineProfileSmokeTest {
    private static int tested;
    private static void ok(boolean b,String reason) {
        tested++;
        if(!b) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        var p0=OcrPerformanceProfile.BASELINE;
        var p1=OcrPerformanceProfile.BALANCED;
        var p2=OcrPerformanceProfile.QUALITY;
        var p3=OcrPerformanceProfile.THROUGHPUT;
        ok(p0.cpuThreads==2 && p0.recBatchSize==1, "baseline must match old app");
        ok(p0.maxShadowRescuesPerDiagnostic==0, "baseline must have zero extra REC");
        ok(p1.cpuThreads==3 && p1.recBatchSize==2, "balanced profile");
        ok(p1.maxShadowRescuesPerDiagnostic==2, "balanced shadow cap");
        ok(p2.cpuThreads==4 && p2.recBatchSize==2, "quality profile");
        ok(p2.maxShadowRescuesPerDiagnostic==4, "quality cap 4");
        ok(p3.cpuThreads==4 && p3.recBatchSize==4, "throughput experimental 4x4");
        ok(p3.maxShadowRescuesPerDiagnostic==4, "throughput shadow cap");
        ok(OcrPerformanceProfile.forId("quality")==p2, "quality resolved");
        ok(OcrPerformanceProfile.forId("balanced")==p1, "balanced resolved");
        ok(OcrPerformanceProfile.forId("throughput")==p3, "throughput resolved");
        ok(OcrPerformanceProfile.forId("unknown")==p0, "unknown fail closed");
        ok(OcrPerformanceProfile.forId(null)==p0, "null fail closed");
        ok(OcrPerformanceProfile.defaultId().equals("baseline"), "explicit baseline");

        float[] q={160,100,190,100,130,570,100,570};
        var evidence=VerticalCropAxisGuard.inspect(q);
        ok(evidence.status==VerticalCropAxisGuard.Status.X_SORT_AXIS_RISK, "risk recognized");
        ok(!VerticalRecRescuePolicy.shouldRetry(evidence,"有",0,0), "baseline zero attempts");
        ok(VerticalRecRescuePolicy.shouldRetry(evidence,"有",0,2), "balanced first allowed");
        ok(!VerticalRecRescuePolicy.shouldRetry(evidence,"有",2,2), "balanced cap");
        ok(VerticalRecRescuePolicy.shouldRetry(evidence,"有",3,4), "quality fourth allowed");
        ok(!VerticalRecRescuePolicy.shouldRetry(evidence,"有",4,4), "quality cap");
        ok(!VerticalRecRescuePolicy.shouldRetry(evidence,"有",0,5), "invalid budget rejected");
        var d=VerticalRecRescuePolicy.decide(evidence,"有","中暑肌肉酸疼风寒痹症",.90f,3,4);
        ok(d.accepted && d.selectedText.equals("中暑肌肉酸疼风寒痹症"),
            "4th attempt recognizes without semantic rewriting");
        ok(!VerticalRecRescuePolicy.decide(evidence,"有","文字",.98f,3,4).accepted,
            "short but high-confidence candidate not substituted");
        ok(!VerticalRecRescuePolicy.decide(evidence,"原文没有问题","中暑肌肉酸疼风寒痹症",.99f,0,4).accepted,
            "plausible original remains invariant");
        ok(!VerticalRecRescuePolicy.decide(evidence,"有","中暑肌肉酸疼风寒痹症",.99f,4,4).accepted,
            "exhausted budget fails closed");
        System.out.println("X200_OFFLINE_PROFILE_POLICY=PASS assertions="+tested);
    }
}
