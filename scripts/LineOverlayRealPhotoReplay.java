import com.vobichngan.kanzi.LineOverlayPlanner;
import com.vobichngan.kanzi.ReadingOrder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Isolated stdin/stdout geometry adapter; NEVER invoked by the Android app. */
public final class LineOverlayRealPhotoReplay {
    private static void emit(String photo, List<ReadingOrder.Box> original) {
        LineOverlayPlanner.Plan plan = LineOverlayPlanner.plan(original);
        int seen = 0;
        boolean[] covered = new boolean[original.size()];
        for (LineOverlayPlanner.Line line : plan.lines) {
            StringBuilder row = new StringBuilder("ROW\t").append(photo)
                .append('\t').append(line.merged ? "1" : "0")
                .append('\t').append(line.drawable ? "1" : "0")
                .append('\t');
            for (int j = 0; j < line.sourceIds.length; j++) {
                int id = line.sourceIds[j];
                if (id < 0 || id >= original.size() || covered[id])
                    throw new IllegalStateException("Source box lost or duplicated: " + id);
                covered[id] = true;
                seen++;
                if (j != 0) row.append(',');
                row.append(id);
            }
            row.append('\t');
            if (line.outline.length != 8)
                throw new IllegalStateException("Invalid result quad length");
            for (int j = 0; j < 8; j++) {
                if (j != 0) row.append(',');
                row.append(line.outline[j]);
            }
            System.out.println(row);
        }
        if (seen != original.size()) throw new IllegalStateException("Missing source boxes");
        System.out.println("DONE\t" + photo + "\t" + original.size()
            + "\t" + plan.lines.size() + "\t" + plan.mergedLineCount);
    }
    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(
            System.in, StandardCharsets.UTF_8));
        String photo = null;
        List<ReadingOrder.Box> original = new ArrayList<>();
        String record;
        while ((record = in.readLine()) != null) {
            String[] f = record.split("\t", -1);
            if ("PHOTO".equals(f[0]) && f.length == 2) {
                if (photo != null) throw new IllegalArgumentException("Nested photo");
                photo = f[1];
                original.clear();
            } else if ("BOX".equals(f[0]) && f.length == 11 && photo != null) {
                if (Integer.parseInt(f[1]) != original.size())
                    throw new IllegalArgumentException("Non-sequential source index");
                float[] x = new float[4], y = new float[4];
                for (int j = 0; j < 4; j++) {
                    x[j] = Float.parseFloat(f[3 + j * 2]);
                    y[j] = Float.parseFloat(f[4 + j * 2]);
                }
                original.add(new ReadingOrder.Box(
                    "1".equals(f[2]) ? "可识别文本" : "", 0.9f, x, y));
            } else if ("END".equals(f[0]) && f.length == 1 && photo != null) {
                emit(photo, original);
                photo = null;
                original.clear();
            } else {
                throw new IllegalArgumentException("Malformed or out-of-order input");
            }
        }
        if (photo != null) throw new IllegalArgumentException("Unclosed photo");
    }
}
