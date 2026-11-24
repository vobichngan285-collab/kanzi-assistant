package com.vobich.bluestar.cloudprobe;

import java.util.regex.Pattern;

/** Strict read-only JPEG path validation. */
final class C110JpegPath {
    private static final String IP="192.168.1.254";
    private static final Pattern FRESH_JPEG=Pattern.compile(
        "^/DCIM/(?:[A-Za-z0-9_-]+/){0,4}[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*\\.(?i:jpg|jpeg)$");
    private C110JpegPath(){}

    static String exactJpegPath(String input){
        if(input==null||!FRESH_JPEG.matcher(input).matches()||
           input.contains("..")||input.contains("%")||
           input.length()>220)return null;
        String canonical=C110PhotoPolicy.path(input,"/DCIM/",IP,false);
        if(canonical==null||!canonical.equals(input))return null;
        return canonical;
    }

}
