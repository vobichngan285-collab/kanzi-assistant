package com.vobich.bluestar.cloudprobe;

import java.util.function.Consumer;

/** Emit only complete newline-delimited OCR text. Never speculate or repeat a line. */
final class OcrLineChunks {
    private final StringBuilder pending=new StringBuilder();
    void append(String delta,Consumer<String> sink){
        if(delta==null||delta.isEmpty())return;
        for(int i=0;i<delta.length();i++){
            char ch=delta.charAt(i);
            if(ch=='\n'||ch=='\r')flushLine(sink);
            else pending.append(ch);
        }
    }
    void finish(Consumer<String> sink){flushLine(sink);}
    private void flushLine(Consumer<String> sink){
        if(pending.length()==0)return;
        String line=pending.toString().trim();
        pending.setLength(0);
        if(!line.isEmpty())sink.accept(line);
    }
}
