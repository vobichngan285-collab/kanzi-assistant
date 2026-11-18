package com.vobich.bluestar.cloudprobe;

import android.net.Network;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single, bounded RTSP TCP SETUP/PLAY probe, only after Media3 playback fails
 * following a user-authorized "C110预览" tap.
 *
 * Uses only the 192.168.1.254:554 URL returned by original SJCAM Zone DEX
 * and the first video track advertised in its live SDP. A disposable session
 * is explicitly torn down. Never logs credentials, SDP, track URLs or RTP.
 */
final class C110RtspSessionProbe {
    private static final String HOST="192.168.1.254";
    private static final String URL="rtsp://192.168.1.254:554/xxx.mp4";
    private static final int LIMIT=16384;
    private static final int CONNECT_MS=1800, READ_MS=1800;
    static final class Report {
        final int track, setup, play, firstRtp;
        Report(int track,int setup,int play,int firstRtp){
            this.track=track;this.setup=setup;this.play=play;this.firstRtp=firstRtp;
        }
    }
    /** Anonymous codes: 1 valid; -2 no control; -3 empty SDP;
     * -4 no video; -5 untrusted control URI. */
    static final class TrackResult {
        final int code; final String url;
        TrackResult(int code,String url){this.code=code;this.url=url;}
    }
    static final class Response {
        final int code;final Map<String,String> headers;final String body;
        Response(int code,Map<String,String> headers,String body){
            this.code=code;this.headers=headers;this.body=body;
        }
    }

    static Report probe(Network wifi){
        if(wifi==null)return new Report(-5,-9,-9,-9);
        int track=0, setup=-9, play=-9, firstRtp=-9;
        try(Socket socket=wifi.getSocketFactory().createSocket()){
            socket.connect(new InetSocketAddress(HOST,554),CONNECT_MS);
            socket.setSoTimeout(READ_MS);
            InputStream input=socket.getInputStream();OutputStream out=socket.getOutputStream();
            send(out,"DESCRIBE",URL,1,"Accept: application/sdp\r\n");
            Response describe=read(input,socket,true);
            if(describe.code!=200)return new Report(-6,describe.code,-9,-9);
            TrackResult selected=videoTrack(describe.body,describe.headers);
            if(selected.code!=1)return new Report(selected.code,-9,-9,-9);
            String trackUrl=selected.url;
            track=1;
            send(out,"SETUP",trackUrl,2,
                "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n");
            Response set=read(input,socket,false);setup=set.code;
            if(set.code!=200)return new Report(track,setup,-9,-9);
            String session=set.headers.get("session");
            if(session==null)return new Report(track,setup,-7,-9);
            session=session.split(";",2)[0].trim();
            if(!session.matches("[A-Za-z0-9._-]{1,128}"))
                return new Report(track,setup,-7,-9);
            try {
                send(out,"PLAY",URL,3,"Session: "+session+"\r\n");
                Response played=read(input,socket,false);play=played.code;
                if(play==200){
                    try{
                        socket.setSoTimeout(1300);
                        int marker=input.read();
                        if(marker=='$'){
                            int channel=input.read();
                            int lenHi=input.read();int lenLo=input.read();
                            firstRtp=(channel>=0&&lenHi>=0&&lenLo>=0)?1:-6;
                        }else if(marker<0)firstRtp=-7;
                        else firstRtp=0; // Received data, not an interleaved RTP marker.
                    }catch(SocketTimeoutException noRtp){firstRtp=-1;}
                }
            }finally{
                // Teardown only this disposable RTSP Session ID, never camera SD files.
                try {send(out,"TEARDOWN",URL,4,"Session: "+session+"\r\n");}
                catch(IOException ignored){}
            }
            return new Report(track,setup,play,firstRtp);
        }catch(SocketTimeoutException timeout){
            return new Report(track,setup==-9?-1:setup,play,firstRtp);
        }catch(Exception problem){
            return new Report(track,setup==-9?-5:setup,play,firstRtp);
        }
    }

    private static void send(OutputStream out,String method,String target,int seq,String extra)
        throws IOException {
        String request=method+" "+target+" RTSP/1.0\r\nCSeq: "+seq+"\r\n"+
            "User-Agent: BlueStar-C110\r\n"+extra+"\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));out.flush();
    }

    static Response read(InputStream input,Socket socket,boolean describe)throws IOException {
        String first=line(input,256);
        Matcher status=Pattern.compile("^RTSP/1\\.0\\s+([1-5]\\d\\d)\\b")
            .matcher(first);
        if(!status.find())return new Response(-6,new HashMap<>(),"");
        Map<String,String> headers=new HashMap<>();
        for(int i=0;i<48;i++){
            String line=line(input,768);
            if(line.isEmpty())break;
            int split=line.indexOf(':');
            if(split>0)headers.put(line.substring(0,split).trim().toLowerCase(Locale.ROOT),
                line.substring(split+1).trim());
            if(i==47)throw new IOException("Too many RTSP headers");
        }
        int size=0;
        String length=headers.get("content-length");
        if(length!=null){
            try{size=Integer.parseInt(length);}catch(NumberFormatException e){
                throw new IOException("Invalid content length");
            }
            if(size<0||size>LIMIT)throw new IOException("Oversized RTSP response");
        }
        // Bounded fallback for cameras omitting DESCRIBE Content-Length.
        if(length==null&&describe){
            ByteArrayOutputStream body=new ByteArrayOutputStream();
            byte[] chunk=new byte[1024];
            socket.setSoTimeout(400);
            try{
                while(body.size()<LIMIT){
                    int n;
                    try{n=input.read(chunk,0,Math.min(chunk.length,LIMIT-body.size()));}
                    catch(SocketTimeoutException idle){break;}
                    if(n<0)break;
                    if(n>0)body.write(chunk,0,n);
                }
            }finally{socket.setSoTimeout(READ_MS);}
            return new Response(Integer.parseInt(status.group(1)),headers,
                new String(body.toByteArray(),StandardCharsets.US_ASCII));
        }
        byte[] body=new byte[size];int offset=0;
        while(offset<size){
            int n=input.read(body,offset,size-offset);
            if(n<0)throw new IOException("Truncated RTSP body");
            offset+=n;
        }
        return new Response(Integer.parseInt(status.group(1)),headers,
            new String(body,StandardCharsets.US_ASCII));
    }

    private static String line(InputStream in,int max)throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        while(output.size()<max){
            int c=in.read();
            if(c<0)throw new IOException("RTSP connection closed");
            if(c=='\n')return output.toString("US-ASCII");
            if(c!='\r')output.write(c);
        }
        throw new IOException("RTSP header line too long");
    }

    /** Resolve first VIDEO control, falling back only to session control. */
    static TrackResult videoTrack(String sdp,Map<String,String> headers){
        if(sdp==null||sdp.trim().isEmpty())return new TrackResult(-3,null);
        String sessionControl=null,mediaControl=null;
        boolean mediaSeen=false,inVideo=false,hasVideo=false;
        for(String raw:sdp.split("\\r?\\n")){
            String row=raw.trim();
            if(row.startsWith("m=")){
                if(inVideo)break;
                mediaSeen=true;
                inVideo=row.toLowerCase(Locale.ROOT).startsWith("m=video");
                if(inVideo)hasVideo=true;
                continue;
            }
            if(row.regionMatches(true,0,"a=control:",0,10)){
                String value=row.substring(10).trim();
                if(value.isEmpty())continue;
                if(!mediaSeen&&sessionControl==null)sessionControl=value;
                else if(inVideo&&mediaControl==null)mediaControl=value;
            }
        }
        if(!hasVideo)return new TrackResult(-4,null);
        String control=mediaControl!=null?mediaControl:sessionControl;
        if(control==null)return new TrackResult(-2,null);
        // SDP a=control:* is aggregate: use only the known presentation URL.
        if("*".equals(control))return new TrackResult(1,URL);
        try{
            if(control.contains(".."))return new TrackResult(-5,null);
            String base=headers.get("content-base");
            if(base==null||base.isEmpty())base=headers.get("content-location");
            if(base==null||base.isEmpty())base=URL;
            URI resolved;
            if(control.regionMatches(true,0,"rtsp://",0,7))
                resolved=URI.create(control);
            else if(control.startsWith("/"))
                resolved=URI.create("rtsp://"+HOST+":554"+control);
            else {
                URI parent=URI.create(base.endsWith("/")?base:base+"/");
                resolved=parent.resolve(control);
            }
            if(!"rtsp".equalsIgnoreCase(resolved.getScheme())||
                !HOST.equals(resolved.getHost())||
                (resolved.getPort()!=-1&&resolved.getPort()!=554)||
                resolved.getRawUserInfo()!=null||resolved.getRawQuery()!=null||
                resolved.getRawFragment()!=null||resolved.getPath()==null||
                resolved.getPath().contains(".."))
                return new TrackResult(-5,null);
            URI normalized=new URI("rtsp",null,HOST,554,resolved.getPath(),null,null);
            return new TrackResult(1,normalized.toString());
        }catch(Exception invalid){return new TrackResult(-5,null);}
    }
}
