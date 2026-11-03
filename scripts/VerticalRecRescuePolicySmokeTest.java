import com.vobichngan.kanzi.VerticalCropAxisGuard;
import com.vobichngan.kanzi.VerticalRecRescuePolicy;

public final class VerticalRecRescuePolicySmokeTest {
    private static int n=0;
    private static void ok(boolean pass,String reason) {
        n++;if(!pass)throw new AssertionError(reason);
    }
    private static VerticalCropAxisGuard.Inspection risk() {
        return VerticalCropAxisGuard.inspect(new float[]{
            150,100,180,100,130,550,100,550
        });
    }
    private static VerticalCropAxisGuard.Inspection normal() {
        return VerticalCropAxisGuard.inspect(new float[]{
            120,100,150,100,150,550,120,550
        });
    }
    private static void geometryAndBudget() {
        ok(risk().status==VerticalCropAxisGuard.Status.X_SORT_AXIS_RISK,"fixture risk");
        ok(normal().status==VerticalCropAxisGuard.Status.STABLE_X_GROUPS,"fixture normal");
        ok(VerticalRecRescuePolicy.shouldRetry(risk(),"有",0),"one anomalous char retries");
        ok(VerticalRecRescuePolicy.shouldRetry(risk(),"",0),"empty REC text retries");
        ok(VerticalRecRescuePolicy.shouldRetry(risk(),"文字符",1),"3 chars retries");
        ok(!VerticalRecRescuePolicy.shouldRetry(risk(),"文本正常输出",0),"long text keeps original");
        ok(!VerticalRecRescuePolicy.shouldRetry(normal(),"有",0),"real vertical with correct axis not retried");
        ok(!VerticalRecRescuePolicy.shouldRetry(null,"有",0),"null guard safe");
        ok(!VerticalRecRescuePolicy.shouldRetry(risk(),null,0),"null OCR text safe");
        ok(!VerticalRecRescuePolicy.shouldRetry(risk(),"有",2),"strict inference budget");
        ok(!VerticalRecRescuePolicy.shouldRetry(risk(),"有",-1),"negative count invalid");
        ok(VerticalRecRescuePolicy.MAX_EXTRA_REC_INFERENCES_PER_FRAME==2,"bound per frame");
    }
    private static void acceptance() {
        String original="有";
        String recognized="压中暑肌肉酸疼所致风寒痹症都有";
        var d=VerticalRecRescuePolicy.decide(risk(),original,recognized,.92f,0);
        ok(d.accepted,"strong first-person OCR candidate accepted");
        ok(d.selectedText.equals(recognized),"candidate must not be rewritten");
        ok(d.reason.equals("STRONG_OCR_CANDIDATE"),"reason tracked");

        var low=VerticalRecRescuePolicy.decide(risk(),original,recognized,.79f,0);
        ok(!low.accepted && low.selectedText.equals(original),"low-confidence candidate keeps original");
        var shortCandidate=VerticalRecRescuePolicy.decide(risk(),original,"风寒痹症",.99f,0);
        ok(!shortCandidate.accepted && shortCandidate.selectedText.equals(original),
           "4-char candidate insufficient even with 99%");
        ok(!VerticalRecRescuePolicy.decide(risk(),original,null,.99f,0).accepted,
           "null candidate not accepted");
        ok(!VerticalRecRescuePolicy.decide(risk(),original,recognized,Float.NaN,0).accepted,
           "invalid candidate confidence not accepted");
        ok(!VerticalRecRescuePolicy.decide(risk(),original,recognized,Float.POSITIVE_INFINITY,0).accepted,
           "infinite confidence not accepted");
        ok(!VerticalRecRescuePolicy.decide(normal(),original,recognized,.99f,0).accepted,
           "no blind true-vertical correction");
        ok(!VerticalRecRescuePolicy.decide(risk(),"正常完整识别",recognized,.99f,0).accepted,
           "plausible original text always preserved");
        ok(!VerticalRecRescuePolicy.decide(risk(),original,recognized,.99f,2).accepted,
           "additional inference budget cannot be bypassed");
    }
    private static void exactAndUnicode() {
        var d=VerticalRecRescuePolicy.decide(risk(),"原样保留", "新的识别包含很多很多汉字",.95f,0);
        ok(!d.accepted,"input length over threshold never overwritten");
        ok("原样保留".equals(d.selectedText),"original bytes not touched");
        ok(!VerticalRecRescuePolicy.shouldRetry(risk(),"😀😀😀😀",0),"count code points correctly");
        ok(VerticalRecRescuePolicy.shouldRetry(risk(),"😀😀😀",0),"three codepoints, not six UTF16 units");
        ok(!VerticalRecRescuePolicy.decide(risk(),"有","😀😀😀😀",.95f,0).accepted,
           "candidate codepoint count insufficient");
        ok(VerticalRecRescuePolicy.decide(risk(),"有","一二三四五六七八九十",.80f,0).accepted,
           "boundary confidence and Unicode length");
    }
    public static void main(String[] args) {
        geometryAndBudget();acceptance();exactAndUnicode();
        System.out.println("VERTICAL_REC_CONSERVATIVE_POLICY_TEST=PASS assertions="+n);
    }
}
