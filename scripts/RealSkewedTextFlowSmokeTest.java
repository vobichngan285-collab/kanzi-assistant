import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.ReadingFocus;
import com.vobichngan.kanzi.SpeechText;
import java.util.ArrayList;
import java.util.List;

/** Reproduces the user's Nov 2 v0.9.7 OCR quadrilaterals and fragmented text. */
public final class RealSkewedTextFlowSmokeTest {
    private static final List<ReadingOrder.Box> boxes=new ArrayList<>();
    private static void q(String text,float x0,float y0,float x1,float y1,
                           float x2,float y2,float x3,float y3) {
        boxes.add(new ReadingOrder.Box(text,.92f,
            new float[]{x0,x1,x2,x3}, new float[]{y0,y1,y2,y3}));
    }
    private static void check(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }
    private static int occurs(String text,String needle) {
        int n=0,p=0;
        while((p=text.indexOf(needle,p))>=0){n++;p+=needle.length();}
        return n;
    }
    public static void main(String[] args) {
        // Left-hand cut-off stream; it must never intrude into the coherent
        // middle stream, even if many left rows share screen Y with center.
        q("左侧上部",39,537,191,525,192,538,40,550);
        q("左侧错字",49,548,172,540,173,553,50,561);
        q("左侧英格拉姆",31,596,236,591,237,609,32,614);
        q("左侧切开的行A",0,686,173,681,173,696,0,701);
        q("左侧切开的行B",0,702,184,697,185,712,0,717);
        q("左侧切开的行C",0,717,181,713,182,729,0,733);
        q("左侧切开的行D",0,734,182,731,183,746,0,749);
        q("左侧切开的行E",0,750,174,748,174,763,0,764);
        q("左侧切开的行F",0,812,239,822,238,847,0,837);
        q("左侧切开的行G",0,832,235,842,233,865,0,855);
        q("左侧切开的行H",0,850,231,862,229,885,0,873);
        q("左侧切开的行I",0,871,230,884,229,906,0,893);
        q("左侧切开的行J",0,890,227,904,226,927,0,913);
        q("左侧切开的行K",0,933,221,948,220,969,0,955);
        q("左侧切开的行L",0,956,216,971,215,990,0,975);
        // Center main reading stream, split by perspective across individual OCR boxes.
        q("说到这里。",326,586,364,584,364,596,326,598);
        q("中间第一行很长的文字",442,592,570,613,567,627,440,605);
        q("中间第二行很长的文字",451,608,569,627,567,640,449,621);
        q("中间第三行很长的文字",411,616,570,642,568,655,408,629);
        q("面一再露出不安的表情。",412,630,571,657,569,670,409,643);
        q("在这一片混乱",316,659,376,655,377,670,317,674);
        q("之中，我的耳目被眼前的情景所吸引。这时我听",371,655,573,686,571,701,369,670);
        q("“啊，我一定去。”我回",305,738,409,740,409,757,305,754);
        q("国答。我很高兴能有这个意外的机会满足",405,743,579,768,577,782,403,758);
        q("溜出房间，谁也没有看到我",417,762,580,786,578,800,415,777);
        q("人们在一起，围着刚回来",288,774,409,773,409,791,288,792);
        q("依然哆嗦着的三个人",404,779,582,803,580,818,402,794);
        q("“对不起，小姐。”萨姆说。",300,810,422,814,422,830,300,826);
        q("，“我在厅里等你，要是她吓着你了，",418,818,583,840,581,855,416,832);
        q("你就叫一下，我会进来的。",282,830,403,831,403,849,282,848);
        q("不怕的，不过我很感兴趣，也很激动。",277,867,474,879,473,900,276,889);
        // Narrow annotation stream has similar Y but different baselines.
        q("[3] 右边注释A",575,602,636,606,635,618,574,614);
        q("右边注释B",577,611,637,614,636,626,576,623);
        q("右边注释C",577,620,637,624,636,636,576,632);
        q("右边注释D",578,629,638,634,637,646,577,641);
        q("右边注释E",579,639,621,641,621,653,578,651);
        q("[2] 右边注释F",587,754,654,759,653,771,586,767);
        q("右边注释G",588,765,654,770,653,782,587,777);
        q("右边注释H",589,776,656,781,655,793,588,789);
        q("右边注释I",590,787,656,792,655,805,589,799);
        q("右边注释J",591,799,657,804,656,816,590,811);
        q("右边注释K",591,810,656,814,655,827,590,822);
        q("右边注释L",591,821,643,825,642,838,590,834);
        q("[3] 右边注释M",597,901,669,905,668,919,596,914);
        q("右边注释N",599,914,671,918,670,932,598,927);
        q("右边注释O",599,926,636,929,634,944,598,940);
        String got=ReadingFocus.choose(boxes,720,1280,ReadingFocus.Mode.AUTO,Float.NaN).text;
        check(got.contains("在这一片混乱之中，我的耳目被眼前的情景所吸引"),
            "same projected baseline not joined: "+got);
        check(got.contains("“对不起，小姐。”萨姆说。，“我在厅里等你，要是她吓着你了，"),
            "adjacent center halves not rejoined: "+got);
        check(got.indexOf("你就叫一下，我会进来的。")>
              got.indexOf("，“我在厅里等你"),
              "next dialogue line appeared before introduction: "+got);
        String main=got.substring(got.indexOf("在这一片混乱"),got.indexOf("你就叫一下"));
        check(!main.contains("右边注释") && !main.contains("左侧切开的行"),
            "unrelated flow invaded the middle of the prose: "+main);
        for(ReadingOrder.Box b:boxes)
            check(occurs(got,b.text)==1, "missing/duplicated OCR box: "+b.text);
        String tts=String.join("",SpeechText.chunks(got));
        check(got.replaceAll("\\s+","").equals(tts.replaceAll("\\s+","")),
            "speech segmentation dropped a recognized character");
        System.out.println("REAL_SKEWED_TEXT_FLOW_SMOKE_TEST=PASS");
    }
}
