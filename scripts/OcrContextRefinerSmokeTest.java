import com.vobichngan.kanzi.OcrContextRefiner;

public final class OcrContextRefinerSmokeTest {
    static void check(boolean ok,String error) { if(!ok) throw new AssertionError(error); }
    public static void main(String[] args) {
        String cover="悦读疑峰\n为读好书而生\n簡·爱\n[英国]夏洛蒂·勃朗特/著\n黄源深/译" +
            "\nJANE EYRE\n黄源深，英语文学学者，翻译家，" +
            "\n曾任上海题泽家协会副会长。中国\n详协理事、上海市文联理事、上海";
        String corrected=OcrContextRefiner.refine(cover);
        check(corrected.contains("简·爱"),"mixed script title");
        check(corrected.contains("上海翻译家协会副会长"),"anchored institute");
        check(corrected.contains("译协理事"),"anchored organization");
        check(corrected.startsWith("悦读疑峰"),"do not guess publisher branding");
        check(OcrContextRefiner.refine("曾任上海题泽家协会副会长。") .
            contains("题泽家"),"no contextual guessing");
        String food="特仑苏\n高\n3.8g\n125\n净含量:200mL\nCLA";
        check(food.equals(OcrContextRefiner.refine(food)),
            "food ingredients, measurements and figures must be unchanged");
        check("任咖 高点诚".equals(OcrContextRefiner.refine("任咖 高点诚")),
            "unverified personal names must not be hallucinated");
        System.out.println("CONSERVATIVE_OCR_CONTEXT=PASS");
    }
}
