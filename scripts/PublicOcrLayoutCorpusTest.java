import com.vobichngan.kanzi.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real online-public 100-image oracle-annotation benchmark, source:
 * https://huggingface.co/datasets/sdcb/simdpaddleocr-dataset-v1
 * License Apache-2.0. Pixel JPEG SHA256 checks are performed by exporter.
 *
 * Only evaluates layout/target selection with ground-truth boxes/strings.
 * The Android OCR model is NOT invoked here; that is a separate benchmark.
 */
public final class PublicOcrLayoutCorpusTest {
    private static Map<Integer,Integer> chars(String s) {
        Map<Integer,Integer> m=new HashMap<>();
        if(s==null)return m;
        s.codePoints().forEach(c->{
            if(Character.isWhitespace(c)||Character.isSpaceChar(c))return;
            m.merge(c,1,Integer::sum);
        });
        return m;
    }
    private static boolean subset(String spoken,List<ReadingOrder.Box> originals) {
        Map<Integer,Integer> have=new HashMap<>();
        for(var b:originals)
            for(var e:chars(b.text).entrySet())
                have.merge(e.getKey(),e.getValue(),Integer::sum);
        for(var e:chars(spoken).entrySet())
            if(e.getValue()>have.getOrDefault(e.getKey(),0))return false;
        return true;
    }
    private static int tested=0,boxes=0,selected=0,full=0,vertical=0,overlapErrors=0;
    private static int oracleRotated=0,detectedVertical=0;
    private static void run(String id,int width,int height,
        List<ReadingOrder.Box> lines,List<Integer> orientations) {
        tested++;boxes+=lines.size();
        if(lines.size()!=orientations.size())throw new AssertionError("missing line annotation");
        boolean rotated=false;
        for(int a:orientations)if(Math.abs(a)==90)rotated=true;
        if(rotated)oracleRotated++;
        var regions=TextRegionPartition.partition(lines);
        Set<Integer> ids=new HashSet<>();
        for(var r:regions)
            for(int i:r.ids)if(!ids.add(i))throw new AssertionError(id+": duplicate source index");
        if(ids.size()!=lines.size())overlapErrors++;
        var focus=new CenterTargetTracker();
        var out=ReadingStrategyRouter.arrange(lines,width,height,true,true,focus);
        if(out.centerSelected)selected++;else full++;
        if("MULTI_VERTICAL".equals(out.strategy)){vertical++;if(rotated)detectedVertical++;}
        if(!subset(out.text,lines))throw new AssertionError(id+": routing invented or duplicated OCR characters");
        if(out.text.isBlank())throw new AssertionError(id+": unexpectedly blank output");
        if(out.selectedBoxes>out.totalBoxes)throw new AssertionError(id+": invalid origin count");
    }
    public static void main(String[]args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("annotation TSV argument missing");
        String id=null;
        int w=0,h=0;
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        List<Integer> orientation=new ArrayList<>();
        try(BufferedReader rd=new BufferedReader(new FileReader(args[0],StandardCharsets.UTF_8))){
            String row;
            while((row=rd.readLine())!=null){
                String[] parts=row.split("\t",-1);
                if(parts[0].equals("S")) {
                    if(id!=null)throw new AssertionError("missing prior E");
                    id=parts[1];w=Integer.parseInt(parts[2]);h=Integer.parseInt(parts[3]);
                    boxes.clear();orientation.clear();
                } else if(parts[0].equals("B")) {
                    if(id==null||parts.length!=7)throw new AssertionError("malformed B record");
                    float x0=Float.parseFloat(parts[1]),y0=Float.parseFloat(parts[2]);
                    float x1=Float.parseFloat(parts[3]),y1=Float.parseFloat(parts[4]);
                    int a=Integer.parseInt(parts[5]);
                    String text=new String(Base64.getDecoder().decode(parts[6]),StandardCharsets.UTF_8);
                    boxes.add(new ReadingOrder.Box(text,.99f,
                        new float[]{x0,x1,x1,x0},new float[]{y0,y0,y1,y1}));
                    orientation.add(a);
                } else if(parts[0].equals("E")) {
                    if(id==null||boxes.isEmpty())throw new AssertionError("empty public image");
                    run(id,w,h,new ArrayList<>(boxes),new ArrayList<>(orientation));
                    id=null;
                } else throw new AssertionError("unknown export record");
            }
        }
        if(tested!=100||boxes<1000||overlapErrors>0)
            throw new AssertionError("incomplete public dataset images="+tested+" boxes="+boxes+" dropped="+overlapErrors);
        System.out.println("PUBLIC_ONLINE_LAYOUT=PASS images="+tested+
            " gt_lines="+boxes+" image_with_90deg_lines="+oracleRotated+
            " center_focused="+selected+" full_page="+full+
            " multi_vertical="+vertical+" multi_vertical_on_rotated="+detectedVertical+
            " all_original_ids_preserved=true no_char_invention=true");
    }
}
