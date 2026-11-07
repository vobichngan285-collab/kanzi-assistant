package com.vobich.bluestar.cloudprobe;

import java.util.ArrayList;
import java.util.List;
public final class ReadingNarrationQueueTest {
    static final class Mock implements ReadingNarrationQueue.Sink{
        final List<String> said=new ArrayList<>();
        int stopped;
        public boolean speak(String text,int i,boolean flush){
            said.add(i+":"+text+":"+flush);return true;
        }
        public void stop(){stopped++;}
    }
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        Mock m=new Mock();
        ReadingNarrationQueue q=new ReadingNarrationQueue(m);
        q.addLine(" 第一行 ");q.addLine("第二行");
        check(m.said.size()==2&&m.said.get(0).equals("0:第一行:true"),"ordered line TTS");
        q.onDone(0);
        q.pause();
        q.addLine("第三行");
        check(m.said.size()==2,"pause does not enqueue");
        q.resume();
        check(m.said.size()==4&&m.said.get(2).equals("1:第二行:false")
            &&m.said.get(3).equals("2:第三行:false"),"resume at last completed line");
        q.onDone(1);q.onDone(2);
        check(q.completedLines()==3,"all completed");
        q.reset();q.addText("新一页\n结束");
        check(m.said.get(4).equals("0:新一页:true"),"new page flushes");
        q.addLine("  ");check(q.totalLines()==2,"skip blank");
        System.out.println("RC22_READING_SPEECH_QUEUE=PASS");
    }
}
