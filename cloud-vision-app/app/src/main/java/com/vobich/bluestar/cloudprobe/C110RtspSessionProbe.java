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
    private static final class Response {
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
            Response describe=read(input);
            if(describe.code!=200)return new Report(-6,describe.code,-9,-9);
            String trackUrl=videoTrack(describe);
            if(trackUrl==null)return new Report(-2,-9,-9,-9);
            track=1;
            send(out,"SETUP",trackUrl,2,
                "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n");
            Response set=read(input);setup=set.code;
            if(set.code!=200)return new Report(track,setup,-9,-9);
            String session=set.headers.get("session");
            if(session==null)return new Report(track,setup,-7,-9);
            session=session.split(";",2)[0].trim();
            if(!session.matches("[A-Za-z0-9._-]{1,128}"))
                return new Report(track,setup,-7,-9);
            try {
                send(out,"PLAY",URL,3,"Session: "+session+"\r\n");
                Response played=read(input);play=played.code;
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

    private static Response read(InputStream input)throws IOException {
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

    /** Resolve only the VIDEO media's first a=control to the original camera. */
    private static String videoTrack(Response r){
        String control=null;boolean inVideo=false;
        for(String raw:r.body.split("\\r?\\n")){
            String row=raw.trim();
            if(row.startsWith("m="))inVideo=row.toLowerCase(Locale.ROOT).startsWith("m=video");
            if(inVideo&&row.startsWith("a=control:")&&control==null){
                String candidate=row.substring("a=control:".length()).trim();
                if(!candidate.equals("*")&&!candidate.isEmpty())control=candidate;
            }
        }
        if(control==null)return null;
        try{
            String base=r.headers.get("content-base");
            if(base==null||base.isEmpty())base=URL.endsWith("/")?URL:URL+"/";
            URI resolved;
            if(control.startsWith("rtsp://"))resolved=URI.create(control);
            else if(control.startsWith("/"))resolved=URI.create("rtsp://"+HOST+":554"+control);
            else resolved=URI.create(base.endsWith("/")?base:base+"/").resolve(control);
            if(!"rtsp".equalsIgnoreCase(resolved.getScheme())||
                !HOST.equals(resolved.getHost())||resolved.getPort()!=554||
                resolved.getRawUserInfo()!=null||resolved.getQuery()!=null||
                resolved.getFragment()!=null||resolved.getRawPath().contains(".."))
                return null;
            return resolved.toString();
        }catch(Exception e){return null;}
    }
}
