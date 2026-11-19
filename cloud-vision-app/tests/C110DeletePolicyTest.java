package com.vobich.bluestar.cloudprobe;

final class C110DeletePolicyTest {
    private static void eq(String a,String b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
    private static void accept(boolean b){if(!b)throw new AssertionError("expected safe path");}
    private static void reject(String p){
        if(C110DeletePolicy.exactJpegPath(p)!=null)
            throw new AssertionError("untrusted delete path accepted: "+p);
        boolean threw=false;
        try{C110DeletePolicy.oneFileDeleteEndpoint(p);}
        catch(IllegalArgumentException expected){threw=true;}
        if(!threw)throw new AssertionError("untrusted delete endpoint accepted");
    }
    public static void main(String[] ignored){
        String image="/DCIM/PHOTO/IMG_0031.JPG";
        accept(image.equals(C110DeletePolicy.exactJpegPath(image)));
        eq(C110DeletePolicy.oneFileDeleteEndpoint(image),
            "/?custom=1&cmd=4003&str=A%3A%5CDCIM%5CPHOTO%5CIMG_0031.JPG");
        eq(C110DeletePolicy.oneFileDeleteEndpoint("/DCIM/IMG_0101.jpeg"),
            "/?custom=1&cmd=4003&str=A%3A%5CDCIM%5CIMG_0101.jpeg");
        for(String path:new String[]{
            "",null,"/","/DCIM/","/DCIM/PHOTO/","/DCIM/PHOTO/IMG.JPG/...",
            "/DCIM/PHOTO/../IMG.JPG","/DCIM/PHOTO/%2e%2e/X.JPG",
            "/DCIM/PHOTO/a.jpg?all=true","/DCIM/PHOTO/a.jpg#x",
            "/DCIM/PHOTO/a.jpg&cmd=4004","//DCIM/PHOTO/a.jpg",
            "http://wrong.example/DCIM/PHOTO/a.jpg",
            "http://192.168.1.254/DCIM/PHOTO/a.jpg",
            "/PRIVATE/IMG.JPG","/DCIM/PHOTO/C110.mp4","/DCIM/PHOTO/000.txt",
            "/DCIM/PHOTO/.jpg","/DCIM/PHOTO/IMG*123.jpg","/DCIM/PHOTO/A\\B.JPG",
            "/DCIM/PHOTO/a.jpg;drop","/DCIM/PHOTO/a.jpg%00",
            "/DCIM/PHOTO/IMG.jpg/../../OTHER.jpg"
        })reject(path);
        System.out.println("PASS: C110 OEM one-JPEG deletion path policy / no directory, injected URL or other file types");
    }
}
