package com.vobich.bluestar.cloudprobe;

import android.net.Network;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only, one-pass protocol diagnosis for the exact 554 and 8192 addresses
 * seen in the SJCAM Zone DEX. Never logs camera payloads or probes unknown hosts.
 * A socket opening does NOT prove that the candidate video URL exists.
 */
final class C110PreviewProbe {
    static final int TIMEOUT_MS=1400;
    static final String IP="192.168.1.254";
    static final String RTSP_URL="rtsp://192.168.1.254:554/xxx.mp4";

    static final class Report {
        final int port554, options554, describe554, port8192, http8192;
        Report(int a,int b,int c,int d,int e){
            port554=a;options554=b;describe554=c;port8192=d;http8192=e;
        }
    }

    static Report inspect(Network wifi){
        int a=probePort(wifi,554);
        int b=a==1?requestRtsp(wifi,"OPTIONS"):-9;
        int c=a==1?requestRtsp(wifi,"DESCRIBE"):-9;
        int d=probePort(wifi,8192);
        int e=d==1?probeHttp(wifi):-9;
        return new Report(a,b,c,d,e);
    }

    // 1=open, -1=timeout, -2=refused, -3=unreachable, -4=security, -5=other.
    private static int failure(Exception ex){
        if(ex instanceof SocketTimeoutException)return -1;
        if(ex instanceof ConnectException)return -2;
        if(ex instanceof NoRouteToHostException)return -3;
        if(ex instanceof SecurityException)return -4;
        return -5;
    }

    static int probePort(Network network,int port){
        try(Socket s=network.getSocketFactory().createSocket()){
            s.connect(new InetSocketAddress(IP,port),TIMEOUT_MS);
            return 1;
        }catch(Exception e){return failure(e);}
    }

    // RTSP OPTIONS and DESCRIBE are read-only and never start RTP media.
    private static int requestRtsp(Network network,String method){
        if(!"OPTIONS".equals(method)&&!"DESCRIBE".equals(method))return -8;
        try(Socket s=network.getSocketFactory().createSocket()){
            s.connect(new InetSocketAddress(IP,554),TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            String message=method+" "+RTSP_URL+" RTSP/1.0\r\nCSeq: 1\r\n"+
                ("DESCRIBE".equals(method)?"Accept: application/sdp\r\n":"")+
                "User-Agent: BlueStarC110-Diagnostics\r\n\r\n";
            OutputStream out=s.getOutputStream();
            out.write(message.getBytes(StandardCharsets.US_ASCII));out.flush();
            String line=readFirstLine(s.getInputStream());
            Matcher m=Pattern.compile("^RTSP/1\\.0\\s+(\\d{3})").matcher(line);
            if(!m.find())return -6;
            return Integer.parseInt(m.group(1));
        }catch(Exception e){return failure(e);}
    }

    // May be a raw stream rather than HTTP: no attempt to decode or read image data.
    private static int probeHttp(Network network){
        try(Socket s=network.getSocketFactory().createSocket()){
            s.connect(new InetSocketAddress(IP,8192),TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            OutputStream out=s.getOutputStream();
            out.write(("GET / HTTP/1.1\r\nHost: "+IP+":8192\r\n"+
                "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String line=readFirstLine(s.getInputStream());
            Matcher m=Pattern.compile("^HTTP/1\\.[01]\\s+(\\d{3})").matcher(line);
            if(!m.find())return -6;
            return Integer.parseInt(m.group(1));
        }catch(Exception e){return failure(e);}
    }

    private static String readFirstLine(InputStream in)throws IOException {
        byte[] line=new byte[160];int count=0;
        while(count<line.length){
            int value=in.read();
            if(value<0||value==10)break;
            if(value!=13)line[count++]=(byte)value;
        }
        return new String(line,0,count,StandardCharsets.US_ASCII);
    }

    /** Stable anonymous error reason, not free-form server or exception text. */
    static int playbackKind(Throwable error){
        int depth=0;
        while(error!=null&&depth++<8){
            if(error instanceof SocketTimeoutException)return 1;
            if(error instanceof ConnectException)return 2;
            if(error instanceof NoRouteToHostException)return 3;
            if(error instanceof SecurityException)return 4;
            String name=error.getClass().getName();
            if(name.contains("Rtsp"))return 5;
            if(name.contains("UnknownHost"))return 6;
            if(error instanceof IOException)return 7;
            error=error.getCause();
        }
        return 9;
    }
}
