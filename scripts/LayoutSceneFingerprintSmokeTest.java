import com.vobichngan.kanzi.LayoutSceneFingerprint;
import java.util.Arrays;

public final class LayoutSceneFingerprintSmokeTest {
    private static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    private static byte[] frame(int w,int h) {
        byte[] pixels=new byte[w*h*3/2];
        Arrays.fill(pixels,(byte)220);
        for (int y=10;y<h-10;y+=20) for(int x=5;x<w/2;x++)
            pixels[y*w+x]=(byte)30;
        return pixels;
    }
    public static void main(String[] args) {
        int w=320,h=240;
        byte[] src=frame(w,h);
        LayoutSceneFingerprint a=LayoutSceneFingerprint.fromNv21(src,w,h);
        check(a.isStationaryComparedWith(LayoutSceneFingerprint.fromNv21(src.clone(),w,h)),
            "identical images must match");
        byte[] mild=src.clone();
        for(int i=0;i<w*h;i++)mild[i]=(byte)Math.min(255,(mild[i]&0xff)+1);
        check(a.isStationaryComparedWith(LayoutSceneFingerprint.fromNv21(mild,w,h)),
            "small global luma noise must match");
        // Text strokes can change away from the one former center pixel.
        // The strengthened 3x3 sampling must catch a local dark stroke.
        byte[] local=src.clone();
        for(int y=1;y<=3;y++)for(int x=1;x<=3;x++)local[y*w+x]=(byte)20;
        check(!a.isStationaryComparedWith(LayoutSceneFingerprint.fromNv21(local,w,h)),
            "local text/graphic change between old sample centers must invalidate cache");
        // A single luma sample flipping strongly must not hide behind the
        // low average difference across a mostly unchanged page.
        byte[] tiny=src.clone();
        tiny[1*w+1]=(byte)0;
        check(!a.isStationaryComparedWith(LayoutSceneFingerprint.fromNv21(tiny,w,h)),
            "strong local stroke change must invalidate cache");
        byte[] replaced=new byte[src.length];
        Arrays.fill(replaced,(byte)120);
        check(!a.isStationaryComparedWith(LayoutSceneFingerprint.fromNv21(replaced,w,h)),
            "new scene must invalidate cache");
        check(!a.isStationaryComparedWith(LayoutSceneFingerprint.fromNv21(frame(640,480),640,480)),
            "different dimensions must invalidate cache");
        System.out.println("LAYOUT_SCENE_FINGERPRINT=PASS");
    }
}
