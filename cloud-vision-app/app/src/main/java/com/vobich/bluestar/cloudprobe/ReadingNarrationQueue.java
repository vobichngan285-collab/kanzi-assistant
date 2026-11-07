package com.vobich.bluestar.cloudprobe;

import java.util.ArrayList;
import java.util.List;

/** UI-thread owned TTS queue; resumes at the last completed full line, never guesses a word. */
final class ReadingNarrationQueue {
    interface Sink{
        boolean speak(String text,int index,boolean flush);
        void stop();
    }
    private final Sink sink;
    private final List<String> lines=new ArrayList<>();
    private int completed,queued;
    private boolean paused;
    ReadingNarrationQueue(Sink sink){this.sink=sink;}
    void reset(){
        sink.stop();lines.clear();completed=queued=0;paused=false;
    }
    void addLine(String source){
        if(source==null||source.trim().isEmpty())return;
        lines.add(source.trim());
        drain();
    }
    void addText(String text){
        if(text==null)return;
        String[] parts=text.split("\\r?\\n");
        for(String part:parts)addLine(part);
    }
    void pause(){
        if(paused)return;
        paused=true;sink.stop();queued=completed;
    }
    void resume(){
        if(!paused)return;
        paused=false;drain();
    }
    void onDone(int index){
        if(paused||index<completed||index>=queued)return;
        // Android TTS executes a queue serially; completed index is monotonic.
        completed=index+1;
    }
    void stop(){reset();}
    boolean isPaused(){return paused;}
    int totalLines(){return lines.size();}
    int completedLines(){return completed;}
    private void drain(){
        if(paused)return;
        while(queued<lines.size()){
            if(!sink.speak(lines.get(queued),queued,queued==0))break;
            queued++;
        }
    }
}
