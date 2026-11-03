import com.vobichngan.kanzi.VerticalCropAxisGuard;
import java.util.Arrays;

/** Synthetic regression; never stores or reads user photos/real box coordinates. */
public final class VerticalCropAxisGuardSmokeTest {
    private static int assertions=0;
    static void check(boolean v,String message) {
        assertions++; if(!v)throw new AssertionError(message);
    }
    static float[] quad(float tlx,float trx,float blx,float brx,float top,float bottom) {
        return new float[]{tlx,top,trx,top,brx,bottom,blx,bottom};
    }
    static void testTopShiftRight() {
        float[] source=quad(150,180,100,130,100,550);
        float[] original=source.clone();
        var p=VerticalCropAxisGuard.inspect(source);
        check(p.status==VerticalCropAxisGuard.Status.X_SORT_AXIS_RISK,
              "both top points to right of both bottom points must flag");
        check(p.horizontalSeparation>=20f,"disjoint margin should be positive");
        check(p.verticalToHorizontalRatio>=5,"must be truly tall");
        check(p.topBottomOrdered[0]==150 && p.topBottomOrdered[2]==180,
              "top pair should be sorted left to right");
        check(p.topBottomOrdered[4]==130 && p.topBottomOrdered[6]==100,
              "bottom pair should be sorted in correct corner order");
        check(Arrays.equals(source,original),"inspection must never mutate source");
    }
    static void testTopShiftLeft() {
        var p=VerticalCropAxisGuard.inspect(quad(100,130,150,180,100,550));
        check(p.status==VerticalCropAxisGuard.Status.X_SORT_AXIS_RISK,
              "symmetric reverse skew must also flag");
        check(p.horizontalSeparation>=20f,"symmetric disjoint margin");
    }
    static void testNormalVertical() {
        var p=VerticalCropAxisGuard.inspect(quad(130,160,125,155,100,550));
        check(p.status==VerticalCropAxisGuard.Status.STABLE_X_GROUPS,
              "normal vertical true text must not flag");
        check(p.horizontalSeparation<=0f,"X intervals overlap");
        var near=VerticalCropAxisGuard.inspect(quad(150,180,120,150,100,550));
        check(near.status==VerticalCropAxisGuard.Status.STABLE_X_GROUPS,
              "touching X intervals must abstain");
    }
    static void testNotVertical() {
        var horizontal=VerticalCropAxisGuard.inspect(
            new float[]{10,10,510,10,510,50,10,50});
        check(horizontal.status==VerticalCropAxisGuard.Status.NOT_LONG_VERTICAL,
              "horizontal row must never flag as vertical-axis sorting");
        var shortTall=VerticalCropAxisGuard.inspect(quad(10,20,10,20,10,55));
        check(shortTall.status==VerticalCropAxisGuard.Status.NOT_LONG_VERTICAL,
              "very short candidates abstain");
        // Existing cross-row wrong columns are not necessarily crop-axis bugs;
        // a separate audit handles that distinct detector failure.
        var crossrow=VerticalCropAxisGuard.inspect(quad(130,165,134,169,100,280));
        check(crossrow.status==VerticalCropAxisGuard.Status.STABLE_X_GROUPS,
              "wrong vertical detector box does not imply X-sort axis bug");
    }
    static void testMalformed() {
        check(VerticalCropAxisGuard.inspect(null).status
              ==VerticalCropAxisGuard.Status.INVALID,"null");
        check(VerticalCropAxisGuard.inspect(new float[]{1,2}).status
              ==VerticalCropAxisGuard.Status.INVALID,"length");
        check(VerticalCropAxisGuard.inspect(new float[]{
            Float.NaN,0,1,0,1,100,0,100}).status
              ==VerticalCropAxisGuard.Status.INVALID,"NaN");
        check(VerticalCropAxisGuard.inspect(new float[]{
            0,0,0,0,0,0,0,0}).status
              ==VerticalCropAxisGuard.Status.INVALID,"degenerate");
    }
    static void testOrderInvariance() {
        float[] a=quad(140,165,100,125,100,550);
        float[] shuffled={a[6],a[7],a[2],a[3],a[0],a[1],a[4],a[5]};
        var p=VerticalCropAxisGuard.inspect(a);
        var q=VerticalCropAxisGuard.inspect(shuffled);
        check(p.status==q.status,"corners must not depend on input index order");
        check(Arrays.equals(p.topBottomOrdered,q.topBottomOrdered),
              "order of potential reference points must be deterministic");
    }
    public static void main(String[] args) {
        testTopShiftRight();testTopShiftLeft();testNormalVertical();
        testNotVertical();testMalformed();testOrderInvariance();
        System.out.println("VERTICAL_CROP_AXIS_SHADOW_TEST=PASS assertions="+assertions);
    }
}
