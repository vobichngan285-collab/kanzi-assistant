import com.vobichngan.kanzi.ReadingFocus;
import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.SpeechText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real-world continuity checks, with OCR text and coordinates kept independent. */
public final class ContinuousReadingSmokeTest {
    private static ReadingOrder.Box box(String text,float x,float y,float w,float h,float conf) {
        return new ReadingOrder.Box(text,conf,
            new float[]{x,x+w,x+w,x},new float[]{y,y,y+h,y+h});
    }
    private static void check(boolean ok,String why) {
        if (!ok) throw new AssertionError(why);
    }
    private static int occurrences(String text,String target) {
        int count=0,at=0;
        while((at=text.indexOf(target,at))>=0){count++;at+=target.length();}
        return count;
    }
    private static void before(String text,String a,String b) {
        check(text.contains(a) && text.contains(b) && text.indexOf(a)<text.indexOf(b),
            "broken text flow: "+a+" -> "+b+" in "+text);
    }
    private static String withoutSpaces(String s) {
        return s.replaceAll("[\\s]+","");
    }
    public static void main(String[] args) {
        // Real OCR coordinates approximated directly from the user's v0.9.6
        // 48-box report: left cut-off text, central five lines of dialogue,
        // and a narrow independent right-hand note.
        List<ReadingOrder.Box> diag=new ArrayList<>();
        for(int i=0;i<15;i++)
            diag.add(box("左侧残句("+i+")结束",1,644+i*25,145-i%4*6,22,.89f));
        diag.add(box("“对不起，小姐，”萨姆说，“我在厅里等你，要是她吓着你了。",
            213,810,287,23,.9827f));
        diag.add(box("你就叫一下，我会进来的。”",197,836,121,20,.9872f));
        diag.add(box("“不用了，萨姆，你回到厨房去吧，我一点也不怕。”我倒真是",
            210,849,294,23,.9875f));
        diag.add(box("不怕的，不过我很感兴趣，也很激动。[3]",
            193,874,197,21,.9923f));
        diag.add(box("这也是继续叙述的一整行文字。",209,899,290,22,.971f));
        for(int i=0;i<11;i++)
            diag.add(box("独立注释("+i+")末尾",506,741+i*15,66,12,.82f));
        diag.add(box("很低的孤立内容",336,730,68,12,.13f));
        ReadingFocus.Selection focused=ReadingFocus.choose(diag,720,1280,
            ReadingFocus.Mode.AUTO,Float.NaN);
        check(!focused.isolated && focused.selectedBoxes==diag.size(),
            "automatic mode silently loses detected text");
        String output=focused.text;
        for(ReadingOrder.Box box:diag)
            check(occurrences(output,box.text)==1,
                "OCR box missing or duplicated in output: "+box.text+" IN "+output);
        String a="“对不起，小姐，”萨姆说",b="你就叫一下，我会进来的。”",
            c="“不用了，萨姆，你回到厨房去吧",d="不怕的，不过我很感兴趣";
        before(output,a,b);
        before(output,b,c);
        before(output,c,d);
        String middle=output.substring(output.indexOf(a),output.indexOf(d));
        check(!middle.contains("残句") && !middle.contains("独立注释"),
            "unrelated flows injected midway through a conversation: "+middle);
        check(output.contains("\n\n"),"independent OCR flows need explicit boundaries");
        List<String> speech=SpeechText.chunks(output);
        check(speech.size()>=3,"distinct regions should have utterance boundaries");
        check(withoutSpaces(String.join("",speech)).equals(withoutSpaces(output)),
            "TTS segmentation dropped or invented non-whitespace OCR characters");

        // Short visible line does not authorize discarding it, and no confidence
        // threshold can silently remove punctuation or tiny labels.
        List<ReadingOrder.Box> isolated=Arrays.asList(
            box("有效但短",32,50,75,22,.10f),
            box("￥9.90",201,60,90,24,.21f),
            box("[1]",320,67,30,18,.09f));
        String preserved=ReadingFocus.choose(isolated,640,480,
            ReadingFocus.Mode.AUTO,Float.NaN).text;
        for(ReadingOrder.Box box:isolated)
            check(preserved.contains(box.text),"low-confidence text was discarded: "+box.text);

        // OCR line wraps are SOFT. They must never create a separate TTS
        // utterance for each physical line or truncate the end of a paragraph.
        String soft="第一行连续文字，接下来还没有结束\n第二行继续上句话，接下去\n第三行才结束。";
        List<String> joined=SpeechText.chunks(soft);
        check(joined.size()==1,"physical OCR lines should read fluently: "+joined);
        check(joined.get(0).equals(soft.replace("\n","")),"soft wrap loses content");
        List<String> hard=SpeechText.chunks("第一段尚未完结\n另一行继续\n\n另一条独立文字流");
        check(hard.size()==2 && hard.get(0).equals("第一段尚未完结另一行继续")
            && hard.get(1).equals("另一条独立文字流"),
            "do not combine independent text regions into one spoken segment");
        String english="Please read the complete\ninstructions before use.";
        check(String.join("",SpeechText.chunks(english)).equals(
            "Please read the complete instructions before use."),
            "English wrapping needs a word separator");
        StringBuilder longText=new StringBuilder();
        for(int i=0;i<125;i++)longText.append("完整文字不丢失。");
        List<String> pieces=SpeechText.chunks(longText.toString());
        check(pieces.size()>2,"large paragraph must be split safely");
        for(String piece:pieces)check(piece.length()<=180,"TTS text limit exceeded");
        check(String.join("",pieces).equals(longText.toString()),
            "long continuous text lost a middle or final sentence");
        String emoji="可读文字".repeat(43)+"🧡"+"这是后续文字，不得破坏代理对。";
        check(String.join("",SpeechText.chunks(emoji)).equals(emoji),
            "UTF-16 surrogate pair was split or lost");
        System.out.println("CONTINUOUS_TEXT_INTEGRITY_SMOKE_TEST=PASS");
    }
}
