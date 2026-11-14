package com.vobichngan.bluestar.m02slab;

import android.net.Network;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Local-only RTSP OPTIONS/DESCRIBE verifier adapted from E09's documented
 * APP_LIVE AP URL rtsp://192.168.169.1/h264.
 * Uses ONLY the verified glasses AP Network and verified private gateway.
 * This is a read-only network probe; a successful response is not proof
 * frames decoded. Never downloads/stores/streams media to the cloud.
 */
public final class M02sE09RtspProbe {
    private M02sE09RtspProbe() { }

    public static final class Result {
        public final boolean optionsOk;
        public final boolean describeOk;
        public final boolean videoSdp;
        public final long durationMs;
        Result(boolean options,boolean describe,boolean sdp,long ms) {
            optionsOk=options;describeOk=describe;videoSdp=sdp;durationMs=ms;
        }
    }

    public static Result probe(Network net, String gateway) throws Exception {
        if (net == null) throw new IllegalArgumentException("unverified network");
        final String host = ProbePolicy.checkedLocalHost(gateway);
        final String url = "rtsp://" + host + "/h264";
        long t=android.os.SystemClock.elapsedRealtime();
        boolean options=false,describe=false,video=false;
        try (Socket socket=net.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(host,554),3000);
            socket.setSoTimeout(4000);
            OutputStream out=socket.getOutputStream();
            InputStream in=socket.getInputStream();
            out.write(("OPTIONS "+url+" RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: BlueStar-M02S-lab\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String reply=readResponse(in);
            options=reply.startsWith("RTSP/1.0 200")||reply.startsWith("RTSP/2.0 200");
            out.write(("DESCRIBE "+url+" RTSP/1.0\r\nCSeq: 2\r\n"
                    +"Accept: application/sdp\r\nUser-Agent: BlueStar-M02S-lab\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String sdp=readResponse(in);
            describe=sdp.startsWith("RTSP/1.0 200")||sdp.startsWith("RTSP/2.0 200");
            video=describe&&sdp.toLowerCase(Locale.ROOT).contains("m=video");
        }
        return new Result(options,describe,video,
                android.os.SystemClock.elapsedRealtime()-t);
    }

    private static String readResponse(InputStream in) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        int last4=0;
        int headerEnd=-1;
        int contentLength=0;
        while(out.size()<8192){
            int c=in.read();
            if(c<0)break;
            out.write(c);
            last4=(last4<<8)|(c&255);
            if(headerEnd<0&&last4==0x0d0a0d0a){
                headerEnd=out.size();
                String head=new String(out.toByteArray(),StandardCharsets.US_ASCII);
                for(String line:head.split("\\r\\n")){
                    if(line.toLowerCase(Locale.ROOT).startsWith("content-length:")){
                        try{contentLength=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());}
                        catch(NumberFormatException ignored){}
                    }
                }
                contentLength=Math.max(0,Math.min(4096,contentLength));
                if(contentLength==0)break;
            }
            if(headerEnd>=0&&out.size()>=headerEnd+contentLength)break;
        }
        return out.toString(StandardCharsets.US_ASCII.name());
    }
}
