package com.vobich.bluestar.cloudprobe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * A single JPEG target for the SJCAM Zone LYCamera deleteMediaFile API.
 * Confirmed original classes5.dex calls Command.setCmd(4003),
 * Command.setStr(StrUtils.getUTF8String(
 *   cameraURL.replace("http://192.168.1.254/","A:\\").replace("/","\\"))).
 * No directories, cards, batches, wildcard or untrusted query strings.
 */
final class C110DeletePolicy {
    private static final String IP="192.168.1.254";
    private static final Pattern FRESH_JPEG=Pattern.compile(
        "^/DCIM/(?:[A-Za-z0-9_-]+/){0,4}[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*\\.(?i:jpg|jpeg)$");
    private C110DeletePolicy(){}

    static String exactJpegPath(String input){
        if(input==null||!FRESH_JPEG.matcher(input).matches()||
           input.contains("..")||input.contains("%")||
           input.length()>220)return null;
        String canonical=C110PhotoPolicy.path(input,"/DCIM/",IP,false);
        if(canonical==null||!canonical.equals(input))return null;
        return canonical;
    }

    static String oneFileDeleteEndpoint(String exactCameraPath){
        String path=exactJpegPath(exactCameraPath);
        if(path==null)throw new IllegalArgumentException("Only exact /DCIM/ JPEG files may be deleted");
        String nativePath="A:\\"+path.substring(1).replace('/','\\');
        String encoded=URLEncoder.encode(nativePath,StandardCharsets.UTF_8);
        return "/?custom=1&cmd=4003&str="+encoded;
    }
}
