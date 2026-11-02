import com.vobichngan.kanzi.Nv21Rotation;
import com.vobichngan.kanzi.FrameQuality;
import com.vobichngan.kanzi.FrameChangeGate;
import com.vobichngan.kanzi.SpeechText;
import java.util.Arrays;
public class CameraReadingFeatureSmokeTest {
    private static void check(boolean v, String label) { if (!v) throw new AssertionError(label); }
    public static void main(String[] args) {
        byte[] nv21 = new byte[4 * 2 * 3 / 2];
        for (int i = 0; i < nv21.length; i++) nv21[i] = (byte)i;
        byte[] r90 = Nv21Rotation.rotate(nv21, 4, 2, 90);
        check(r90.length == nv21.length && r90[0] == 4 && r90[1] == 0, "90 degree Y orientation");
        byte[] r180 = Nv21Rotation.rotate(nv21, 4, 2, 180);
        check(r180[0] == 7, "180 degree Y orientation");
        byte[] r270 = Nv21Rotation.rotate(nv21, 4, 2, 270);
        check(r270[0] == 3, "270 degree Y orientation");
        check(Arrays.equals(Nv21Rotation.rotate(r90, 2, 4, 270), nv21), "roundtrip sensor conversion");
        byte[] dark = new byte[32 * 24 * 3 / 2];
        check(FrameQuality.assess(dark, 32, 24).dark, "dark warning");
        check(SpeechText.chunks("第一段。\n第二段。").size() == 1, "adjacent OCR lines are one fluent utterance");
        FrameChangeGate gate = new FrameChangeGate();
        check(gate.accept(dark, 32, 24, 1000, true), "first frame");
        check(!gate.accept(dark, 32, 24, 1500, true), "skip duplicate");
        System.out.println("CAMERA_READING_FEATURES=PASS");
    }
}
