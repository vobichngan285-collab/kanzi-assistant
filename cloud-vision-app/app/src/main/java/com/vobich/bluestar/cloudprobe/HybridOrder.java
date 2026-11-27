package com.vobich.bluestar.cloudprobe;

import java.util.*;

/** Strict exact-once grouping. Cloud output never becomes spoken text. */
final class HybridOrder {
    private HybridOrder(){}
    static String restore(String response,List<String> texts){
        if(response==null||texts==null||texts.isEmpty())throw new IllegalArgumentException("empty");
        String[] lines=response.trim().split("\\r?\\n",-1);
        boolean[] used=new boolean[texts.size()];
        int count=0;
        StringBuilder output=new StringBuilder();
        for(String line:lines){
            String clean=line.trim();
            if(!clean.matches("[1-9][0-9]*(\\s*,\\s*[1-9][0-9]*)*"))throw new IllegalArgumentException("format");
            if(output.length()>0)output.append('\n');
            boolean first=true;
            for(String part:clean.split(",")){
                int id;
                try{id=Integer.parseInt(part.trim());}catch(NumberFormatException bad){throw new IllegalArgumentException("id");}
                if(id<1||id>texts.size()||used[id-1])throw new IllegalArgumentException("duplicate or invalid id");
                used[id-1]=true;count++;
                if(!first)output.append(' ');
                output.append(texts.get(id-1));first=false;
            }
        }
        if(count!=texts.size())throw new IllegalArgumentException("missing id");
        return output.toString();
    }
}
