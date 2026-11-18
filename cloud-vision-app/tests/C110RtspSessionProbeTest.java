package com.vobich.bluestar.cloudprobe;

import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class C110RtspSessionProbeTest {
    private static final String VIDEO="v=0\r\nm=video 0 RTP/AVP 96\r\n"
        +"a=rtpmap:96 H264/90000\r\n";
    private static void expect(int code,String url,String body,Map<String,String> headers){
        C110RtspSessionProbe.TrackResult got=C110RtspSessionProbe.videoTrack(body,headers);
        if(got.code!=code)throw new AssertionError("Expected "+code+" got "+got.code);
        if(url!=null&&(got.url==null||!got.url.equals(url)))
            throw new AssertionError("Unexpected normalized URL: "+got.url);
    }
    private static Map<String,String> base(){
        Map<String,String> h=new HashMap<>();
        h.put("content-base","rtsp://192.168.1.254:554/xxx.mp4/");
        return h;
    }
    public static void main(String[] args)throws Exception{
        String known="rtsp://192.168.1.254:554/xxx.mp4";
        Map<String,String> h=base();
        expect(1,known+"/trackID=1",VIDEO+"a=control:trackID=1\r\n",h);
        expect(1,known,"v=0\r\na=control:*\r\n"+VIDEO,h);
        expect(1,known+"/trackID=3","v=0\r\na=control:*\r\n"+VIDEO+"a=control:trackID=3\r\n",h);
        expect(1,known+"/trackID=9","v=0\r\na=control:trackID=9\r\n"+VIDEO,h);
        expect(1,"rtsp://192.168.1.254:554/live/track1",
            VIDEO+"a=control:rtsp://192.168.1.254/live/track1\r\n",h);
        expect(-2,null,VIDEO,h);
        expect(-3,null,"",h);
        expect(-4,null,"v=0\r\nm=audio 0 RTP/AVP 0\r\na=control:track1\r\n",h);
        expect(-5,null,VIDEO+"a=control:rtsp://10.0.0.2:554/stream\r\n",h);
        expect(-5,null,VIDEO+"a=control:../../outside\r\n",h);
        String response="RTSP/1.0 200 OK\r\nCSeq: 1\r\nContent-Type: application/sdp\r\n\r\n"
            +VIDEO+"a=control:trackID=1\r\n";
        C110RtspSessionProbe.Response parsed=C110RtspSessionProbe.read(
            new ByteArrayInputStream(response.getBytes(StandardCharsets.US_ASCII)),
            new Socket(),true);
        expect(1,known+"/trackID=1",parsed.body,h);
        System.out.println("C110 RTSP SDP/track parser regression PASS");
    }
}
