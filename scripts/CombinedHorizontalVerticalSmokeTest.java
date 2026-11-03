import com.vobichngan.kanzi.*;
import java.util.*;

/** Synthetic ONLY. This doesn't prove distinguishing true vertical vs cross-row. */
public final class CombinedHorizontalVerticalSmokeTest {
    static int checks=0;
    static void ok(boolean v,String reason) {
        checks++;if(!v)throw new AssertionError(reason);
    }
    static CrossRowOrientationAudit.Box box(int id,float x,float y,float w,float h,float confidence) {
        return new CrossRowOrientationAudit.Box(id,
            new float[]{x,y,x+w,y,x+w,y+h,x,y+h},confidence);
    }
    static List<CrossRowOrientationAudit.Box> table() {
        var b=new ArrayList<CrossRowOrientationAudit.Box>();
        // 3 detected narrow/vertical columns crossing 4 actual horizontal rows.
        b.add(box(15,457,646,39,139,.999f));
        b.add(box(16,550,657,45,129,.999f));
        b.add(box(17,601,653,42,168,.999f));
        b.add(box(18,782,651,78,33,.99f));
        b.add(box(20,796,694,66,32,.99f));
        b.add(box(23,797,735,65,35,.99f));
        // Additional ordinary text anchors to mirror actual frame horizontal height.
        b.add(box(0,120,95,160,32,.99f));
        b.add(box(1,150,145,170,35,.99f));
        b.add(box(2,150,195,170,32,.99f));
        return b;
    }
    public static void main(String[] args) {
        var source=table();float[] copy=source.get(0).quad.clone();
        var proposal=HorizontalCrossRowProposal.propose(source,1080,1920,4);
        ok(proposal.status.equals("SHADOW_ONLY_GEOMETRIC_ROW_HYPOTHESIS"),"proposed");
        ok(proposal.xyxy.length==4,"4 horizontal candidate rows");
        ok(Arrays.equals(proposal.suspectIds,new int[]{15,16,17}),"exact suspect ids");
        ok(proposal.anchorIds.length>=3,"multiple independent anchors");
        ok(proposal.xyxy[0][0]==449f,"left crop from actual geometry");
        ok(proposal.xyxy[0][2]==651f,"right crop from actual geometry");
        ok(proposal.xyxy[0][1]>=645&&proposal.xyxy[0][1]<=651,"row y0");
        ok(proposal.xyxy[3][3]>=812&&proposal.xyxy[3][3]<=824,"fourth inferred row");
        ok(Arrays.equals(source.get(0).quad,copy),"no image/source mutation");

        var limited=HorizontalCrossRowProposal.propose(source,1080,1920,3);
        ok(limited.xyxy.length==3,"low budget must skip speculative fourth");
        var tiny=HorizontalCrossRowProposal.propose(source,1080,1920,2);
        ok(tiny.xyxy.length==0,"cannot certify full row without 3");
        var missing=new ArrayList<>(source);missing.removeIf(b->b.sourceId==20);
        ok(HorizontalCrossRowProposal.propose(missing,1080,1920,4).xyxy.length==0,
           "two price anchors insufficient");
        var m2=new ArrayList<>(source);m2.removeIf(b->b.sourceId==16);
        ok(HorizontalCrossRowProposal.propose(m2,1080,1920,4).xyxy.length==0,
           "two bad narrow boxes insufficient");
        ok(HorizontalCrossRowProposal.propose(source,1080,1920,0).xyxy.length==0,
           "zero inference budget abstains");
        ok(HorizontalCrossRowProposal.propose(null,1080,1920,4).xyxy.length==0,
           "invalid input abstains");

        // KNOWN HARD NEGATIVE: 3 genuine vertical columns next to price anchors
        // generate the same *visual* candidate boxes. This is exactly why
        // proposals NEVER overwrite baseline text or live TTS automatically.
        var genuineVerticalWithPrices=table();
        ok(HorizontalCrossRowProposal.propose(genuineVerticalWithPrices,1080,1920,4)
           .xyxy.length==4,"hard negative remains ambiguous; shadow only");
        System.out.println("X200_COMBINED_SHADOW_GEOMETRY=PASS assertions="+checks);
    }
}
