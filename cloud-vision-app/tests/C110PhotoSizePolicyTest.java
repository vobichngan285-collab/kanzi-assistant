package com.vobich.bluestar.c110photosize;

import java.util.Set;

final class C110PhotoSizePolicyTest {
    static void eq(Object a,Object b){
        if(a==null?b!=null:!a.equals(b))
            throw new AssertionError("expected "+b+", got "+a);
    }
    static void check(boolean val,String reason){
        if(!val)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        eq(PhotoSizePolicy.path("IMG_0001.JPG","/DCIM/PHOTO/",false),
            "/DCIM/PHOTO/IMG_0001.JPG");
        eq(PhotoSizePolicy.path("http://192.168.1.254/DCIM/PHOTO/IMG_0002.JPG",
            "/DCIM/",false),"/DCIM/PHOTO/IMG_0002.JPG");
        for(String bad:new String[]{
            "/DCIM/PHOTO/../IMG.JPG","/PRIVATE/IMG.JPG","/DCIM/IMG.JPG?cmd=4003",
            "https://host.example/DCIM/PHOTO/A.JPG","//evil.example/DCIM/PHOTO/A.JPG",
            "/DCIM/PHOTO/A.MP4","/DCIM/PHOTO/","/DCIM/PHOTO/B.JPG#erase",
            "/DCIM/PHOTO/%2e%2e/B.JPG?x"})
            check(PhotoSizePolicy.path(bad,"/DCIM/",false)==null,bad);
        String html="<a href=\"IMG_0001.JPG\">jpg</a>" +
            "<a href=\"/DCIM/PHOTO/IMG_0002.JPG\">other</a>";
        Set<String> photos=PhotoSizePolicy.jpegPaths(html,"/DCIM/PHOTO/");
        check(photos.contains("/DCIM/PHOTO/IMG_0001.JPG"),"relative JPEG");
        check(photos.contains("/DCIM/PHOTO/IMG_0002.JPG"),"absolute JPEG");
        eq(PhotoSizePolicy.integerTag("<Status>0</Status>","Status"),0);
        eq(PhotoSizePolicy.integerTag("<Value>5</Value>","Value"),5);
        check(PhotoSizePolicy.integerTag("<Status>xx</Status>","Status")==null,
            "unknown code");
        byte[] jpg=new byte[300];
        jpg[0]=(byte)0xff;jpg[1]=(byte)0xd8;jpg[2]=(byte)0xff;
        jpg[298]=(byte)0xff;jpg[299]=(byte)0xd9;
        check(PhotoSizePolicy.validJpeg(jpg),"expected SOI/EOI");
        jpg[299]=0;
        check(!PhotoSizePolicy.validJpeg(jpg),"reject incomplete JPEG");
        System.out.println("PASS C110 photo-size lab: SD new JPEG parsing and integrity, no photo scaling");
    }
}
