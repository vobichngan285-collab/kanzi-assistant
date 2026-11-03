import com.vobichngan.kanzi.CrossRowOrientationAudit;
import java.util.*;

/**
 * Synthetic only. Alerts are not text corrections.
 * Includes a deliberate adversarial false positive proving SHADOW-only safety.
 */
public final class CrossRowOrientationAuditSmokeTest {
    private static int checks = 0;
    private static void require(boolean yes, String msg) {
        checks++; if(!yes) throw new AssertionError(msg);
    }
    private static CrossRowOrientationAudit.Box b(int id,float x,float y,float w,float h,float conf) {
        return new CrossRowOrientationAudit.Box(id,
            new float[]{x,y,x+w,y,x+w,y+h,x,y+h},conf);
    }
    private static List<CrossRowOrientationAudit.Box> table() {
        List<CrossRowOrientationAudit.Box> boxes = new ArrayList<>();
        boxes.add(b(0,220,50,140,34,.999f));
        boxes.add(b(1,450,50,80,34,.999f));
        boxes.add(b(2,230,100,140,34,.999f));
        boxes.add(b(3,450,100,80,34,.999f));
        boxes.add(b(4,230,150,140,34,.999f));
        boxes.add(b(5,450,150,80,34,.999f));
        boxes.add(b(15,110,200,38,141,.999f));
        boxes.add(b(16,185,202,40,141,.999f));
        boxes.add(b(17,244,200,40,147,.999f));
        boxes.add(b(18,450,216,70,34,.999f));
        boxes.add(b(20,450,260,70,34,.999f));
        boxes.add(b(23,450,305,70,34,.999f));
        return boxes;
    }
    private static void basic() {
        List<CrossRowOrientationAudit.Box> boxes = table();
        float[] before = boxes.get(6).quad.clone();
        CrossRowOrientationAudit.Audit r = CrossRowOrientationAudit.inspect(boxes,1080,1000);
        require(r.suspects.size()==3,"must identify three cross-row risk candidates");
        require(r.status.equals("SUSPECT_ONLY_NO_TEXT_MUTATION"),"must not claim proven bad line");
        for(int i=0;i<3;i++) {
            CrossRowOrientationAudit.Alert a = r.suspects.get(i);
            require(a.sourceId == 15+i,"wrong source ID");
            require(a.companionIds.length==2,"must require two companions");
            require(a.anchorIds.length >=3,"need independent horizontal anchor column");
        }
        require(Arrays.equals(before,boxes.get(6).quad),"audit mutated original input quad");
    }
    private static void realVerticalAndAmbiguous() {
        // Three genuinely vertical strings away from a horizontal price-column:
        // no overlapping row anchors -> no alert.
        List<CrossRowOrientationAudit.Box> vertical = new ArrayList<>();
        vertical.add(b(10,110,200,40,150,.999f));
        vertical.add(b(11,185,200,40,150,.999f));
        vertical.add(b(12,250,200,40,150,.999f));
        vertical.add(b(1,350,40,90,30,.99f));
        vertical.add(b(2,350,70,90,30,.99f));
        vertical.add(b(3,350,100,90,30,.99f));
        require(CrossRowOrientationAudit.inspect(vertical,1080,1000).suspects.isEmpty(),
            "genuine vertical text with no same-row anchors must not flag");
        // HARD NEGATIVE: legitimate vertical writing next to horizontally aligned
        // prices is indistinguishable by these bounding boxes alone. It DOES
        // trigger a suspicion, proving auto-rotation/deletion would be unsafe.
        List<CrossRowOrientationAudit.Box> adversarial = table();
        require(CrossRowOrientationAudit.inspect(adversarial,1080,1000).suspects.size()==3,
            "hard negative proves possible false positives; never use to auto-fix");
    }
    private static void cautiousAbstain() {
        List<CrossRowOrientationAudit.Box> src=table();
        src.removeIf(b-> b.sourceId == 16);
        require(CrossRowOrientationAudit.inspect(src,1080,1000).suspects.isEmpty(),
            "must not flag without a 3-box vertical cluster");
        src=table();
        src.removeIf(b->b.sourceId == 20);
        require(CrossRowOrientationAudit.inspect(src,1080,1000).suspects.isEmpty(),
            "must not flag without three independent same-column horizontal rows");
        src=table();
        src.add(new CrossRowOrientationAudit.Box(100,
            new float[]{Float.NaN,0,0,0,0,0,0,0},.5f));
        require(CrossRowOrientationAudit.inspect(src,1080,1000).suspects.size()==3,
            "malformed quad must not corrupt other results");
        require(CrossRowOrientationAudit.inspect(Collections.emptyList(),1080,1000)
            .suspects.isEmpty(),"empty input safe");
        List<CrossRowOrientationAudit.Box> many=new ArrayList<>();
        for(int i=0;i<513;i++)many.add(b(i,20,20,20,20,.9f));
        require(CrossRowOrientationAudit.inspect(many,1080,1000)
            .status.equals("CAPACITY_ABSTAIN"),"large input must abstain");
    }
    public static void main(String[] args) {
        basic();realVerticalAndAmbiguous();cautiousAbstain();
        System.out.println("CROSS_ROW_ORIENTATION_SHADOW_TEST=PASS assertions="+checks);
    }
}
