import com.vobichngan.kanzi.StrictOcrOrderGuard;
import java.util.Arrays;
import java.util.List;

public final class StrictOcrOrderGuardSmokeTest {
    private static void check(boolean b, String message) {
        if (!b) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        List<StrictOcrOrderGuard.Item> raw = Arrays.asList(
            new StrictOcrOrderGuard.Item(7, "净含量250毫升"),
            new StrictOcrOrderGuard.Item(3, "纯牛奶"),
            new StrictOcrOrderGuard.Item(9, "蛋白质3.2克"));
        StrictOcrOrderGuard.Result ok = StrictOcrOrderGuard.apply(raw, Arrays.asList(3,7,9));
        check(ok.accepted, "valid ordering rejected");
        check(ok.exactText().equals("纯牛奶\n净含量250毫升\n蛋白质3.2克"), "text mutated");
        for (List<Integer> bad : Arrays.asList(
                Arrays.asList(3,7,99), Arrays.asList(3,3,9),
                Arrays.asList(3,7), Arrays.asList(3,7,9,9))) {
            StrictOcrOrderGuard.Result result = StrictOcrOrderGuard.apply(raw,bad);
            check(!result.accepted, "invalid IDs accepted");
            check(result.exactText().equals("净含量250毫升\n纯牛奶\n蛋白质3.2克"),
                "fallback failed");
        }
        System.out.println("STRICT_OCR_ORDER_GUARD=PASS");
    }
}
